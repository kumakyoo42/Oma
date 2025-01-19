package de.kumakyoo.oma;

import java.io.*;
import java.nio.file.*;
import java.util.*;

public class Reunify
{
    /*
     * Data flow of temporary files is rather complicate,
     * see following diagram:
     *
     * As of 6. 2. 2025
     *
     *                       OSM file (xml, o5m, pbf)
     * 1. readFile()            /  /   |   \    \
     *                      tmp1  w    r  nodes  ways
     *
     *   tmp1 (final output) : nodes with tags
     *   w                   : ways with tags
     *   r                   : relations with at least one member
     *   nodes               : nodes that do not fit into memory (only id and geo)
     *   ways                : ways (only id and geo)
     *
     *
     *                           w    r   ways    nodes
     * 2. updateNodes()          |*   |*   |*     -----
     *                           w    r   ways
     *
     *   Replaces node ids with nodes from nodes file.
     *   Will repeat until all nodes have been processed.
     *
     *
     *                           w       w
     * 3. addWays()              |       -
     *                         tmp1
     *
     *   Content of w is appended to tmp1.
     *
     *
     *                           r      ways
     * 4. updateWays()           |*     ----
     *                           r
     *
     *   Replaces way ids with ways from ways file.
     *   Will repeat until all ways have been processed.
     *
     *
     *                                  r                 r
     * 5. convertRelations()          / | \               -
     *                            tmp1  a  collections
     *
     *   Splits relations into ways, areas and collections.
     *   Ways are immediately appended to tmp1.
     *
     *
     *                           a        a
     * 6. addAreas()             |        -
     *                         tmp1
     *
     *    Content of a is appended to tmp1.
     *
     *
     *                              collections
     * 7. updateCollections()            |*
     *                              collections
     *
     *    Replaces members of type relation by their content.
     *    Will repeat until all relations have been processed.
     *
     *
     *                              collections   collections
     * 8. addCollections()               |        -----------
     *                                 tmp1
     *
     *    Appends the collections to tmp1.
     *    Formatting of collections is slightly altered.
     *
     */

    // Marker for IDs: Instead of nodes that cannot be replaced the
    // ID is saved. To distinguish the IDs from nodes, ID_MARKER is
    // added to IDs. This leads to a value, interpreted as
    // coordinates would produce an illegal latitude of more than
    // 213°. IDs won't reach this size in the foreseable future - the
    // database would have to increase its size a billion times.
    public static long ID_MARKER = 0x7f00000000000000L;

    private Path infile;
    private Path outfile;
    private Path nodes;
    private Path ways;
    private Path collections;
    private Path wtmp;
    private Path atmp;
    private Path rtmp;

    private OmaOutputStream nos;
    private OmaOutputStream wos;
    private OmaOutputStream cos;
    private OmaOutputStream out;
    private OmaOutputStream wout;
    private OmaOutputStream aout;
    private OmaOutputStream rout;

    private long exp_nodes;
    private long exp_ways;
    private long exp_rels;

    // Intermediate memory storage of nodes and ways. Using three/two
    // arrays, because this needs less memory than using one array
    // with some object as elements. Also uses less memory than using
    // a HashMap, which would allow for a faster lookup. (Due to the
    // inner workings of HashMaps they tend to produce memory errors
    // anyway.)
    private long[] nodes_id;
    private int[] nodes_lon;
    private int[] nodes_lat;
    private int nodes_c;

    private long[] ways_id;
    private byte[][] ways_data;
    private int ways_c;

    private long[] colls_id;
    private boolean[] colls_used;
    private Relation[] colls_data;
    private int colls_c;

    private long missing_nodes;
    private long missing_ways;
    private long missing_colls;

    private boolean all_nodes_read;
    private boolean all_ways_read;

    private long colls_updated;

    private long nc,wc,rc,nc_used,wc_used,rc_used;

    private boolean bounds = false;

    private long minlon,maxlon,minlat,maxlat;

    public Reunify(Path infile, Path outfile)
    {
        this.infile = infile;
        this.outfile = outfile;
    }

    public OmaOutputStream process() throws IOException
    {
        out = OmaOutputStream.init(outfile,true);

        initMemory();
        readFile();

        updateNodes();
        releaseNodes();
        addWays();

        allocateMemoryForWays();
        updateWays();
        releaseWays();

        convertRelations();
        addAreas();

        if (Oma.collections)
        {
            allocateMemoryForCollections();
            updateCollections();
            releaseCollections();
            addCollections();
        }

        out.close();

        return out;
    }

    private void initMemory() throws IOException
    {
        if (Oma.verbose>=2)
            System.out.println("  Initializing memory...");

        estimateElementCounts(Files.size(infile), Tools.isO5M(infile), Tools.isPBF(infile));
        allocateMemoryForNodes();

        if (Oma.verbose>=2)
            System.out.println("    Initializing memory was successful.");
    }

    private void estimateElementCounts(long filesize, boolean o5m, boolean pbf)
    {
        // The numbers here are somewhat fancy, as the number of nodes
        // increases more over time than the other two. Maybe, we should assume that
        // the file is made up only of nodes.
        if (o5m)
        {
            exp_nodes = (long)(filesize/15);
            exp_ways = (long)(filesize/90);
            exp_rels = (long)(filesize/7300);
        }
        else if (pbf)
        {
            exp_nodes = (long)(filesize/7);
            exp_ways = (long)(filesize/40);
            exp_rels = (long)(filesize/3600);
        }
        else
        {
            exp_nodes = (long)(filesize/125);
            exp_ways = (long)(filesize/760);
            exp_rels = (long)(filesize/61000);
        }

        if (Oma.verbose>=3)
        {
            System.out.println("      Filesize: "+Tools.humanReadable(filesize));
            System.out.println("      Expected nodes: "+Tools.humanReadable(exp_nodes));
            System.out.println("      Expected ways: "+Tools.humanReadable(exp_ways));
            System.out.println("      Expected relations: "+Tools.humanReadable(exp_rels));
        }
    }

    private void allocateMemoryForNodes()
    {
        long available = Tools.memavail();
        long useable = (available-Oma.memlimit)/10*9;
        if (useable<100000) useable = available/5*4;

        if (Oma.verbose>=3)
        {
            System.out.println("      Available memory: "+Tools.humanReadable(available));
            System.out.println("      Useable for nodes: "+Tools.humanReadable(useable));
        }

        long wish = Math.min(exp_nodes/5*6+5,useable/16+1);
        int max_nodes = wish>Integer.MAX_VALUE-10?Integer.MAX_VALUE-10:(int)wish;

        while (true)
            try
            {
                if (Oma.verbose>=3)
                    System.out.println("      Trying to allocate "+max_nodes+" nodes...");
                nodes_id = new long[max_nodes];
                nodes_lon = new int[max_nodes];
                nodes_lat = new int[max_nodes];
                break;
            }
            catch (OutOfMemoryError e)
            {
                if (max_nodes<2)
                {
                    System.err.println("There seems to be almost no available memory. Giving up.");
                    System.exit(-1);
                }

                releaseNodes();
                max_nodes /= 2;

                if (Oma.verbose>=3)
                    System.out.println("      Didn't work. Halving amount. Keep fingers crossed...");
            }

        if (Oma.verbose>=3)
            System.out.println("      Allocation was successfull.");
    }

    private void allocateMemoryForWays()
    {
        long available = Tools.memavail();
        long useable = (available-Oma.memlimit)/10*9;
        if (useable<100000) useable = available/5*4;

        if (Oma.verbose>=3)
        {
            System.out.println("      Available memory: "+Tools.humanReadable(available));
            System.out.println("      Useable for ways: "+Tools.humanReadable(useable));
        }

        long wish = Math.min(exp_ways/5*6+5,useable/90+1);
        int max_ways = wish>Integer.MAX_VALUE-10?Integer.MAX_VALUE-10:(int)wish;

        while (true)
            try
            {
                if (Oma.verbose>=3)
                    System.out.println("      Trying to allocate "+max_ways+" ways...");
                ways_id = new long[max_ways];
                ways_data = new byte[max_ways][];
                break;
            }
            catch (OutOfMemoryError e)
            {
                if (max_ways<2)
                {
                    System.err.println("There seems to be almost no available memory. Giving up.");
                    System.exit(-1);
                }

                releaseWays();
                max_ways /= 2;

                if (Oma.verbose>=3)
                    System.out.println("      Didn't work. Halving amount. Keep fingers crossed...");
            }

        if (Oma.verbose>=3)
            System.out.println("      Allocation was successfull.");
    }

    private void allocateMemoryForCollections()
    {
        long available = Tools.memavail();
        long useable = (available-Oma.memlimit)/10*9;
        if (useable<100000) useable = available/5*4;

        if (Oma.verbose>=3)
        {
            System.out.println("      Available memory: "+Tools.humanReadable(available));
            System.out.println("      Useable for collections: "+Tools.humanReadable(useable));
        }

        long wish = Math.min(exp_rels/5*6+5,useable/10000+1);
        int max_colls = wish>Integer.MAX_VALUE-10?Integer.MAX_VALUE-10:(int)wish;

        while (true)
            try
            {
                if (Oma.verbose>=3)
                    System.out.println("      Trying to allocate "+max_colls+" collections...");
                colls_id = new long[max_colls];
                colls_used = new boolean[max_colls];
                colls_data = new Relation[max_colls];
                break;
            }
            catch (OutOfMemoryError e)
            {
                if (max_colls<2)
                {
                    System.err.println("There seems to be almost no available memory. Giving up.");
                    System.exit(-1);
                }

                releaseCollections();
                max_colls /= 2;

                if (Oma.verbose>=3)
                    System.out.println("      Didn't work. Halving amount. Keep fingers crossed...");
            }

        if (Oma.verbose>=3)
            System.out.println("      Allocation was successfull.");
    }

    private void releaseNodes()
    {
        nodes_id = null;
        nodes_lon = nodes_lat = null;
        Tools.gc();
    }

    private void releaseWays()
    {
        ways_id = null;
        ways_data = null;
        Tools.gc();
    }

    private void releaseCollections()
    {
        colls_id = null;
        colls_used = null;
        colls_data = null;
        Tools.gc();
    }

    //////////////////////////////////////////////////////////////////

    private void readFile() throws IOException
    {
        if (Oma.verbose>=2)
            System.out.println("  Reading file '"+infile+"'...");

        OSMReader r = OSMReader.getReader(infile);

        wtmp = Tools.tmpFile("w");
        wout = OmaOutputStream.init(wtmp);
        rtmp = Tools.tmpFile("r");
        rout = OmaOutputStream.init(rtmp);

        while (true)
        {
            Element el = r.next();
            if (el==null) break;

            if (el instanceof OSMNode)
                processNode((OSMNode)el);
            else if (el instanceof OSMWay)
                processWay((OSMWay)el);
            else if (el instanceof OSMRelation)
                processRelation((OSMRelation)el);
            else if (el instanceof Bounds)
                processBounds((Bounds)el);
        }

        if (!all_nodes_read) endNodes();
        if (!all_ways_read) endWays();
        endRelations();

        wout.close();
        rout.close();
        r.close();

        if (Oma.verbose>=2)
        {
            System.out.println("    "+Tools.humanReadable(nc)+" nodes, "+Tools.humanReadable(wc)+" ways and "+Tools.humanReadable(rc)+" relations read.");
            System.out.println("    "+ElementWithID.discardedTags()+" tags discarded.");
        }
    }

    private void processBounds(Bounds b) throws IOException
    {
        out.writeByte('B');
        b.write(out);
    }

    private void processNode(OSMNode n) throws IOException
    {
        nc++;
        if (!Oma.silent && nc%100000==0)
            System.err.printf("Step 1: reading nodes: ~%.1f%%        \r",100.0/exp_nodes*nc);

        if (nodes_c<nodes_id.length)
        {
            nodes_id[nodes_c] = n.id;
            nodes_lon[nodes_c] = n.lon;
            nodes_lat[nodes_c] = n.lat;
            nodes_c++;
        }
        else
        {
            if (nos==null)
                initTmpNodes();
            nos.writeLong(n.id);
            nos.writeInt(n.lon);
            nos.writeInt(n.lat);
        }

        if (n.tags.size()==0) return;

        nc_used++;

        out.writeByte('N');
        if (Oma.preserve_id)
            out.writeLong(n.id);
        if (Oma.preserve_version)
            out.writeInt(n.version);
        if (Oma.preserve_timestamp)
            out.writeLong(n.timestamp);
        if (Oma.preserve_changeset)
            out.writeLong(n.changeset);
        if (Oma.preserve_user)
        {
            out.writeInt(n.uid);
            out.writeUTF(n.user);
        }

        out.writeInt(n.lon);
        out.writeInt(n.lat);

        out.writeInt(n.tags.size());
        for (var tag:n.tags.entrySet())
        {
            out.writeUTF(tag.getKey());
            out.writeUTF(tag.getValue());
        }
    }

    private void processWay(OSMWay w) throws IOException
    {
        wc++;
        if (!Oma.silent && wc%10000==0)
            System.err.printf("Step 1: reading ways: ~%.1f%%        \r",100.0/exp_ways*wc);

        if (!all_nodes_read) endNodes();

        if (wos==null) initTmpWays();

        wos.writeLong(w.id);
        wos.writeInt(w.nds.size());
        for (int i=0;i<w.nds.size();i++)
            writeNodeLocation(wos,w.nds.get(i));

        if (w.tags.size()==0) return;

        wc_used++;

        wout.writeByte('W');
        if (Oma.preserve_id)
            wout.writeLong(w.id);
        if (Oma.preserve_version)
            wout.writeInt(w.version);
        if (Oma.preserve_timestamp)
            wout.writeLong(w.timestamp);
        if (Oma.preserve_changeset)
            wout.writeLong(w.changeset);
        if (Oma.preserve_user)
        {
            wout.writeInt(w.uid);
            wout.writeUTF(w.user);
        }

        wout.writeInt(w.nds.size());
        for (int i=0;i<w.nds.size();i++)
            writeNodeLocation(wout,w.nds.get(i));

        wout.writeInt(w.tags.size());
        for (var tag:w.tags.entrySet())
        {
            wout.writeUTF(tag.getKey());
            wout.writeUTF(tag.getValue());
        }
    }

    private void processRelation(OSMRelation r) throws IOException
    {
        rc++;
        if (!Oma.silent && rc%1000==0)
            System.err.printf("Step 1: reading relations: ~%.1f%%        \r",100.0/exp_rels*rc);

        if (!all_nodes_read) endNodes();
        if (!all_ways_read) endWays();

        if (r.tags.size()<=1) return;

        rc_used++;

        rout.writeByte('C');
        rout.writeLong(r.id);
        if (Oma.preserve_version)
            rout.writeInt(r.version);
        if (Oma.preserve_timestamp)
            rout.writeLong(r.timestamp);
        if (Oma.preserve_changeset)
            rout.writeLong(r.changeset);
        if (Oma.preserve_user)
        {
            rout.writeInt(r.uid);
            rout.writeUTF(r.user);
        }

        int naz = 0;
        int waz = 0;
        int raz = 0;
        for (Member m:r.members)
        {
            if ("node".equals(m.type))
                naz++;
            if ("way".equals(m.type))
                waz++;
            if ("relation".equals(m.type))
                raz++;
        }

        // Relations need to be saved first. We need that later...
        rout.writeInt(raz);
        for (Member m:r.members)
            if ("relation".equals(m.type))
            {
                rout.writeUTF(m.role);
                rout.writeLong(m.ref);
            }

        rout.writeInt(naz);
        for (Member m:r.members)
            if ("node".equals(m.type))
            {
                rout.writeUTF(m.role);
                writeNodeLocation(rout,m.ref);
            }

        rout.writeInt(waz);
        for (Member m:r.members)
            if ("way".equals(m.type))
            {
                rout.writeUTF(m.role);
                rout.writeByte('w');
                rout.writeLong(m.ref);
                missing_ways++;
            }

        rout.writeInt(r.tags.size());
        for (var tag:r.tags.entrySet())
        {
            rout.writeUTF(tag.getKey());
            rout.writeUTF(tag.getValue());
        }
    }

    private void initTmpNodes() throws IOException
    {
        nodes = Tools.tmpFile("nodes");
        nos = OmaOutputStream.init(nodes);
    }

    private void initTmpWays() throws IOException
    {
        ways = Tools.tmpFile("ways");
        wos = OmaOutputStream.init(ways,true);
    }

    private void endNodes() throws IOException
    {
        if (Oma.verbose>=3)
            System.out.println("      "+Tools.humanReadable(nc)+" nodes read, "+Tools.humanReadable(nc_used)+" nodes used.");

        all_nodes_read = true;

        if (nos==null) return;
        nos.close();

        if (Oma.verbose>=3)
            System.out.println("      "+Tools.humanReadable(nos.fileSize()/16)+" nodes temporarily saved.");
    }

    private void endWays() throws IOException
    {
        if (Oma.verbose>=3)
            System.out.println("      "+Tools.humanReadable(wc)+" ways read, "+Tools.humanReadable(wc_used)+" ways used.");

        all_ways_read = true;

        if (wos==null) return;
        wos.close();
    }

    private void endRelations()
    {
        if (Oma.verbose>=3)
            System.out.println("      "+Tools.humanReadable(rc)+" relations read, "+Tools.humanReadable(rc_used)+" relations used.");
    }

    // Searching node, using binary search. If node is not found,
    // writing marked id instead.
    private void writeNodeLocation(OmaOutputStream s, long id) throws IOException
    {
        int pos = Arrays.binarySearch(nodes_id,0,nodes_c,id);
        if (pos>=0)
        {
            s.writeInt(nodes_lon[pos]);
            s.writeInt(nodes_lat[pos]);
        }
        else
        {
            s.writeLong(ID_MARKER+id);
            missing_nodes++;
        }
    }

    //////////////////////////////////////////////////////////////////

    private void updateNodes() throws IOException
    {
        if (nodes==null) return;

        if (Oma.verbose>=2)
            System.out.println("  Updating missing nodes...");
        if (Oma.verbose>=3)
            System.out.println("      Nodes missing: "+Tools.humanReadable(missing_nodes)+".");

        if (missing_nodes>0)
            addMissingNodes();

        if (Oma.verbose>=2)
            System.out.println("    All nodes updated.");
    }

    private void addMissingNodes() throws IOException
    {
        long node_count = nos.fileSize()/16;
        long passes = node_count/nodes_id.length;
        if (node_count%nodes_id.length>0) passes++;

        if (Oma.verbose>=3)
        {
            System.out.println("      "+Tools.humanReadable(node_count)+" nodes saved.");
            System.out.println("      "+passes+" passes needed.");
        }

        OmaInputStream nis = OmaInputStream.init(nos);
        for (int pass=0;pass<passes;pass++)
        {
            if (!Oma.silent)
                System.err.printf("Step 1: reading new nodes                                  \r");

            readTmpNodes(nis);

            if (!Oma.silent)
                System.err.printf("                                                           \r");

            if (Oma.verbose>=3)
                System.out.println("      Pass "+(pass+1)+": "+nodes_c+" nodes read.");

            updateNodesOfUsedWays();
            updateNodesOfSavedWays();
            updateNodesOfRelationFile();
        }
        nis.release();
    }

    private void readTmpNodes(OmaInputStream nis) throws IOException
    {
        try
        {
            for (nodes_c=0;nodes_c<nodes_id.length;nodes_c++)
            {
                nodes_id[nodes_c] = nis.readLong();
                nodes_lon[nodes_c] = nis.readInt();
                nodes_lat[nodes_c] = nis.readInt();
            }
        }
        catch (EOFException e)
        {
            nis.release();
        }
    }

    private void updateNodesOfUsedWays() throws IOException
    {
        if (!Oma.silent)
            System.err.print("Step 1: preparing update of used ways                           \r");

        long fs = wout.fileSize();
        wout.move(Tools.tmpFile("original"));

        OmaInputStream in = OmaInputStream.init(wout);
        wout = OmaOutputStream.init(wtmp,true);

        byte type = 0;
        int c = 0;
        while (true)
        {
            if (!Oma.silent && ++c%100000==0)
            {
                long sz = wout.fileSize();
                System.err.printf("Step 1: updating nodes of used ways: %.1f%%        \r",100.0/fs*sz);
            }

            try {
                type = in.readByte();
            } catch (EOFException e) { break; }

            copyWayReplacingIDs(in);
        }
        if (!Oma.silent)
            System.err.print("Step 1:                                                                     \r");

        in.release();
        wout.close();
    }

    private void updateNodesOfSavedWays() throws IOException
    {
        if (!Oma.silent)
            System.err.print("Step 1: preparing update of saved ways                          \r");

        long fs = wos.fileSize();
        wos.move(Tools.tmpFile("original"));

        OmaInputStream in = OmaInputStream.init(wos);
        wos = OmaOutputStream.init(ways,true);

        int c = 0;
        while (true)
        {
            if (!Oma.silent && ++c%100000==0)
            {
                long sz = wos.fileSize();
                System.err.printf("Step 1: updating nodes of saved ways: %.1f%%        \r",100.0/fs*sz);
            }

            try {
                wos.writeLong(in.readLong());
            } catch (EOFException e) { break; }

            copyArrayOfNodesReplacingIDs(in,wos,false);
        }
        if (!Oma.silent)
            System.err.print("Step 1:                                                                     \r");

        in.release();
        wos.close();
    }

    private void updateNodesOfRelationFile() throws IOException
    {
        if (!Oma.silent)
            System.err.print("Step 1: preparing update of relation file                       \r");

        long fs = rout.fileSize();
        rout.move(Tools.tmpFile("original"));

        OmaInputStream in = OmaInputStream.init(rout);
        rout = OmaOutputStream.init(rtmp,true);

        byte type = 0;
        int c = 0;
        while (true)
        {
            if (!Oma.silent && ++c%100000==0)
            {
                long sz = rout.fileSize();
                System.err.printf("Step 1: updating nodes of relation file: %.1f%%        \r",100.0/fs*sz);
            }

            try {
                type = in.readByte();
            } catch (EOFException e) { break; }

            copyCollectionReplacingIDs(in);
        }
        if (!Oma.silent)
            System.err.print("Step 1:                                                                     \r");

        in.release();
        rout.close();
    }

    private void copyWayReplacingIDs(OmaInputStream in) throws IOException
    {
        wout.writeByte('W');
        copyMetaData(wout, in, false);
        copyArrayOfNodesReplacingIDs(in,wout,false);
        copyTags(wout,in);
    }

    private void copyCollectionReplacingIDs(OmaInputStream in) throws IOException
    {
        rout.writeByte('C');
        copyMetaData(rout, in, true);

        int raz = in.readInt();
        rout.writeInt(raz);
        for (int i=0;i<raz;i++)
        {
            rout.writeUTF(in.readUTF());
            rout.writeLong(in.readLong());
        }

        copyArrayOfNodesReplacingIDs(in,rout,true);

        int waz = in.readInt();
        rout.writeInt(waz);
        for (int i=0;i<waz;i++)
        {
            rout.writeUTF(in.readUTF());
            rout.writeByte(in.readByte());
            rout.writeLong(in.readLong());
        }

        copyTags(rout,in);
    }

    private void copyArrayOfNodesReplacingIDs(OmaInputStream in, OmaOutputStream out, boolean role) throws IOException
    {
        int naz = in.readInt();
        out.writeInt(naz);
        for (int i=0;i<naz;i++)
        {
            if (role) out.writeUTF(in.readUTF());
            long id = in.readLong();
            if (id>=ID_MARKER)
            {
                int pos = Arrays.binarySearch(nodes_id,0,nodes_c,id-ID_MARKER);
                if (pos>=0)
                {
                    out.writeInt(nodes_lon[pos]);
                    out.writeInt(nodes_lat[pos]);
                    continue;
                }
            }
            out.writeLong(id);
        }
    }

    //////////////////////////////////////////////////////////////////

    private void updateWays() throws IOException
    {
        if (ways==null) return;

        if (Oma.verbose>=2)
            System.out.println("  Updating missing ways...");
        if (Oma.verbose>=3)
            System.out.println("      Ways missing: "+Tools.humanReadable(missing_ways)+".");

        if (missing_ways>0)
            addMissingWays();
        else
            wos.release();

        if (Oma.verbose>=2)
            System.out.println("    All ways updated.");
    }

    private void addMissingWays() throws IOException
    {
        OmaInputStream wis = OmaInputStream.init(wos);
        int pass = 0;
        while (true)
        {
            if (!Oma.silent)
                System.err.printf("Step 1: reading new ways                                   \r");

            pass++;
            boolean finished = readTmpWays(wis);

            if (!Oma.silent)
                System.err.printf("                                                           \r");

            if (Oma.verbose>=3)
                System.out.println("      Pass "+pass+": "+ways_c+" ways read.");

            updateWaysOfOutfile();

            if (finished) break;
        }
        wis.release();
    }

    private boolean readTmpWays(OmaInputStream wis) throws IOException
    {
        ways_c = 0;
        for (int i=0;i<ways_data.length;i++)
            ways_data[i] = null;
        Tools.gc();

        try
        {
            while (true)
            {
                long id = wis.readLong();
                int az = wis.readInt();
                ByteArrayOutputStream baos = new ByteArrayOutputStream(4+8*az);
                new DataOutputStream(baos).writeInt(az);
                byte[] b = new byte[8*az];
                wis.readFully(b);
                baos.write(b);

                ways_id[ways_c] = id;
                ways_data[ways_c] = baos.toByteArray();
                ways_c++;

                if (ways_c==ways_id.length || Tools.memavail()<Oma.memlimit)
                    return false;
            }
        }
        catch (EOFException e)
        {
            wis.release();
            return true;
        }
    }

    private void updateWaysOfOutfile() throws IOException
    {
        long fs = rout.fileSize();

        Path original = Tools.tmpFile("original");
        rout.move(original);

        OmaInputStream in = OmaInputStream.init(rout);
        rout = OmaOutputStream.init(rtmp,true);

        byte type = 0;
        int c = 0;
        while (true)
        {
            if (!Oma.silent && ++c%10000==0)
            {
                long sz = rout.fileSize();
                // to be improved
                System.err.printf("Step 1: updating ways of relation file: %.1f%%        \r",100.0/fs*sz);
            }

            try {
                type = in.readByte();
            } catch (EOFException e) { break; }

            switch (type)
            {
                case 'C' -> copyCollectionReplacingWays(in);
                default ->
                {
                    System.err.println("Unknown type: "+type);
                    System.exit(-1);
                }
            }
        }
        if (!Oma.silent)
            System.err.print("Step 1:                                                                      \r");

        in.release();
        rout.close();
    }

    //////////////////////////////////////////////////////////////////

    private void copyCollectionReplacingWays(OmaInputStream in) throws IOException
    {
        rout.writeByte('C');
        copyMetaData(rout, in,true);

        int raz = in.readInt();
        rout.writeInt(raz);
        for (int i=0;i<raz;i++)
        {
            rout.writeUTF(in.readUTF());
            rout.writeLong(in.readLong());
        }

        int naz = in.readInt();
        rout.writeInt(naz);
        for (int i=0;i<naz;i++)
        {
            rout.writeUTF(in.readUTF());
            rout.writeLong(in.readLong());
        }

        copyArrayOfWaysReplacingWays(in,rout);

        copyTags(rout,in);
    }

    private void copyArrayOfWaysReplacingWays(OmaInputStream in, OmaOutputStream out) throws IOException
    {
        int waz = in.readInt();
        out.writeInt(waz);
        for (int i=0;i<waz;i++)
        {
            out.writeUTF(in.readUTF());
            if (in.readByte()=='w')
            {
                long id = in.readLong();
                int pos = Arrays.binarySearch(ways_id,0,ways_c,id);
                if (pos>=0)
                {
                    out.writeByte('W');
                    out.write(ways_data[pos]);
                }
                else
                {
                    // not yet found
                    out.writeByte('w');
                    out.writeLong(id);
                }
            }
            else
            {
                out.writeByte('W');
                int naz = in.readInt();
                out.writeInt(naz);
                for (int j=0;j<naz;j++)
                    out.writeLong(in.readLong());
            }
        }
    }

    //////////////////////////////////////////////////////////////////

    public void convertRelations() throws IOException
    {
        if (Oma.verbose>=2)
            System.out.println("  Converting relations...");

        long fs = rout.fileSize();

        OmaInputStream in = OmaInputStream.init(rout);

        atmp = Tools.tmpFile("a");
        aout = OmaOutputStream.init(atmp);

        if (Oma.collections)
        {
            collections = Tools.tmpFile("collections");
            cos = OmaOutputStream.init(collections,true);
        }

        byte type = 0;
        int c = 0;
        while (true)
        {
            if (!Oma.silent && ++c%10000==0)
            {
                /*
                long sz = cos.fileSize();
                System.err.printf("Step 1: converting relations: %.1f%%        \r",100.0/fs*sz);
                 */
            }

            try {
                type = in.readByte();
            } catch (EOFException e) { break; }

            convertRelation(in);
        }
        if (!Oma.silent)
            System.err.print("Step 1:                                                                      \r");

        in.release();
        if (Oma.collections)
            cos.close();
        aout.close();
    }

    private void convertRelation(OmaInputStream in) throws IOException
    {
        Relation r = new Relation(in,true);
        r.writeDirectElements(out,aout);

        if (!Oma.collections) return;

        if (!r.empty())
        {
            missing_colls++;

            for (int i=0;i<r.relrole.length;i++)
                if (r.relrole[i]!=null)
                    r.relrole[i]="";

            r.write(cos,false);
        }
    }

    private void copyBoundingBox(OmaInputStream in) throws IOException
    {
        out.writeByte('B');
        out.writeLong(in.readLong());
        out.writeLong(in.readLong());
    }

    private void copyNode(OmaInputStream in) throws IOException
    {
        out.writeByte('N');
        copyMetaData(out, in, false);

        out.writeLong(in.readLong());

        copyTags(out,in);
    }

    private void copyWay(OmaInputStream in) throws IOException
    {
        out.writeByte('W');
        copyMetaData(out, in, false);

        int naz = in.readInt();
        out.writeInt(naz);
        for (int i=0;i<naz;i++)
            out.writeLong(in.readLong());

        copyTags(out,in);
    }

    private void copyTags(OmaOutputStream out, OmaInputStream in) throws IOException
    {
        int taz = in.readInt();
        out.writeInt(taz);
        for (int i=0;i<2*taz;i++)
            out.writeUTF(in.readUTF());
    }

    private void copyMetaData(OmaOutputStream out, OmaInputStream in, boolean preserve_id) throws IOException
    {
        if (preserve_id || Oma.preserve_id)
            out.writeLong(in.readLong());
        if (Oma.preserve_version)
            out.writeInt(in.readInt());
        if (Oma.preserve_timestamp)
            out.writeLong(in.readLong());
        if (Oma.preserve_changeset)
            out.writeLong(in.readLong());
        if (Oma.preserve_user)
        {
            out.writeInt(in.readInt());
            out.writeUTF(in.readUTF());
        }
    }

    //////////////////////////////////////////////////////////////////

    public void updateCollections() throws IOException
    {
        if (Oma.verbose>=2)
            System.out.println("  Updating collections, adding missing relations...");
        if (Oma.verbose>=3)
            System.out.println("      Collections to update: "+Tools.humanReadable(missing_colls)+".");

        colls_updated = 0;
        addMissingRelations();

        if (Oma.verbose>=2)
            System.out.println("    All collections updated.");
    }

    private void addMissingRelations() throws IOException
    {
        int pass = 0;
        while (true)
        {
            if (!Oma.silent)
                System.err.printf("Step 1: reading new collections                            \r");

            pass++;
            boolean finished = readCollections();

            if (!Oma.silent)
                System.err.printf("                                                           \r");

            if (Oma.verbose>=3)
                System.out.println("      Pass "+pass+": "+colls_c+" collections read.");

            updateRelationsOfCollections();
            colls_updated += colls_c;

            if (finished) break;
        }
    }

    private boolean readCollections() throws IOException
    {
        colls_c = 0;
        for (int i=0;i<colls_data.length;i++)
            colls_data[i] = null;
        Tools.gc();

        OmaInputStream in = OmaInputStream.init(cos);
        for (int i=0;i<colls_updated;i++)
            new Relation(in,true);
        Tools.gc();

        while (true)
        {
            colls_data[colls_c] = new Relation(in,true);
            colls_id[colls_c] = colls_data[colls_c].id;
            colls_c++;

            if (colls_updated+colls_c==missing_colls)
            {
                in.close();
                return true;
            }

            if (colls_c==colls_id.length || Tools.memavail()<Oma.memlimit)
            {
                in.close();
                return false;
            }
        }
    }

    private void updateRelationsOfCollections() throws IOException
    {
        Path original = Tools.tmpFile("original");
        cos.move(original);

        OmaInputStream in = OmaInputStream.init(cos);
        cos = OmaOutputStream.init(collections,true);

        for (long i=0;i<missing_colls;i++)
        {
            if (!Oma.silent && i%1000==0)
                System.err.printf("Step 1: updating collections: %.1f%%        \r",100.0/missing_colls*i);

            List<Long> relid = new ArrayList<>();

            for (int j=0;j<colls_id.length;j++)
                colls_used[j] = false;

            copyMetaData(cos,in,true);

            int raz = in.readInt();
            for (int j=0;j<raz;j++)
            {
                in.readUTF();
                addRelid(in.readLong(),relid);
            }

            int mraz = 0;
            for (long id: relid)
            {
                int pos = Arrays.binarySearch(colls_id,0,colls_c,id);
                if (pos<0)
                    mraz++;
            }
            cos.writeInt(mraz);
            for (long id: relid)
            {
                int pos = Arrays.binarySearch(colls_id,0,colls_c,id);
                if (pos<0)
                {
                    cos.writeUTF("");
                    cos.writeLong(id);
                }
            }

            int mnaz = 0;
            for (long id: relid)
            {
                int pos = Arrays.binarySearch(colls_id,0,colls_c,id);
                if (pos>=0)
                    mnaz += colls_data[pos].node.length;
            }
            int naz = in.readInt();
            cos.writeInt(naz+mnaz);
            for (int j=0;j<naz;j++)
            {
                cos.writeUTF(in.readUTF());
                cos.writeLong(in.readLong());
            }
            for (long id: relid)
            {
                int pos = Arrays.binarySearch(colls_id,0,colls_c,id);
                if (pos>=0)
                    for (int j=0;j<colls_data[pos].node.length;j++)
                    {
                        cos.writeUTF(colls_data[pos].noderole[j]);
                        cos.writeInt(colls_data[pos].node[j].lon);
                        cos.writeInt(colls_data[pos].node[j].lat);
                    }
            }

            int mwaz = 0;
            for (long id: relid)
            {
                int pos = Arrays.binarySearch(colls_id,0,colls_c,id);
                if (pos>=0)
                    mwaz += colls_data[pos].way.length;
            }
            int waz = in.readInt();
            cos.writeInt(waz+mwaz);
            for (int j=0;j<waz;j++)
            {
                cos.writeUTF(in.readUTF());
                cos.writeByte(in.readByte());
                int wnaz = in.readInt();
                cos.writeInt(wnaz);
                for (int k=0;k<wnaz;k++)
                    cos.writeLong(in.readLong());
            }
            for (long id: relid)
            {
                int pos = Arrays.binarySearch(colls_id,0,colls_c,id);
                if (pos>=0)
                    for (int j=0;j<colls_data[pos].way.length;j++)
                    {
                        cos.writeUTF(colls_data[pos].wayrole[j]);
                        cos.writeByte('W');
                        cos.writeInt(colls_data[pos].way[j].lon.length);
                        for (int k=0;k<colls_data[pos].way[j].lon.length;k++)
                        {
                            cos.writeInt(colls_data[pos].way[j].lon[k]);
                            cos.writeInt(colls_data[pos].way[j].lat[k]);
                        }
                    }
            }

            copyTags(cos,in);
        }
        if (!Oma.silent)
            System.err.print("Step 1:                                                                      \r");

        in.release();
        cos.close();
    }

    private void addRelid(long id, List<Long> relid)
    {
        int pos = Arrays.binarySearch(colls_id,0,colls_c,id);
        if (pos<0 || !colls_used[pos])
        {
            relid.add(id);
            if (pos>=0)
            {
                colls_used[pos] = true;
                for (long id2: colls_data[pos].relid)
                    addRelid(id2,relid);
            }
        }
    }

    //////////////////////////////////////////////////////////////////

    private void addWays() throws IOException
    {
        wout.close();
        OmaInputStream in = OmaInputStream.init(wout);
        out.copyFrom(in,wout.fileSize());
        in.release();
    }

    private void addAreas() throws IOException
    {
        OmaInputStream in = OmaInputStream.init(aout);
        out.copyFrom(in,aout.fileSize());
        in.release();
    }

    private void addCollections() throws IOException
    {
        OmaInputStream in = OmaInputStream.init(cos);
        for (int i=0;i<missing_colls;i++)
        {
            Relation r = new Relation(in,true);
            r.write(out,true);
        }
        in.release();
    }
}

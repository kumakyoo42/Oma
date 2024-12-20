package de.kumakyoo.oma;

import java.io.*;
import java.nio.file.*;
import java.util.*;

public class Reunify
{
    // Marker for IDs: Instead of nodes that cannot be replaced the
    // ID is saved. To distinguish the IDs from nodes, ID_MARKER is
    // added to IDs. This leads to a value, interpreted as
    // coordinates would produce an illegal latitude of more than
    // 213°. IDs won't reach this sice in the foreseable future - the
    // database would have to increase its size a billion times.
    public static long ID_MARKER = 0x7f00000000000000L;

    private Path infile;
    private Path outfile;
    private Path nodes;
    private Path ways;

    private OmaOutputStream nos;
    private OmaOutputStream wos;
    private OmaOutputStream out;

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

    private long missing_nodes;
    private long missing_ways;

    private boolean all_nodes_read;
    private boolean all_ways_read;

    private long node_end;
    private long way_end;

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
        initMemory();
        readFile();
        updateNodes();
        releaseNodes();
        allocateMemoryForWays();
        updateWays();
        releaseWays();
        convertMultipolygonsToAreas();
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

        long wish = Math.min(exp_ways/5*6+5,useable/90+1);  // 90 is current average mem usage
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

    //////////////////////////////////////////////////////////////////

    private void readFile() throws IOException
    {
        if (Oma.verbose>=2)
            System.out.println("  Reading file '"+infile+"'...");

        OSMReader r = OSMReader.getReader(infile);
        out = OmaOutputStream.init(outfile,true);

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

        out.close();
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
            System.err.printf("Step 1: nodes: ~%.1f%%        \r",100.0/exp_nodes*nc);

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
        out.writeInt(n.tags.size());
        out.writeInt(n.lon);
        out.writeInt(n.lat);
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
            System.err.printf("Step 1: ways: ~%.1f%%        \r",100.0/exp_ways*wc);

        if (!all_nodes_read) endNodes();

        if (wos==null) initTmpWays();

        wos.writeLong(w.id);
        wos.writeInt(w.nds.size());
        for (int i=0;i<w.nds.size();i++)
            writeNodeLocation(wos,w.nds.get(i));

        if (w.tags.size()==0) return;

        wc_used++;

        out.writeByte('W');
        if (Oma.preserve_id)
            out.writeLong(w.id);
        if (Oma.preserve_version)
            out.writeInt(w.version);
        if (Oma.preserve_timestamp)
            out.writeLong(w.timestamp);
        if (Oma.preserve_changeset)
            out.writeLong(w.changeset);
        if (Oma.preserve_user)
        {
            out.writeInt(w.uid);
            out.writeUTF(w.user);
        }
        out.writeInt(w.nds.size());
        out.writeInt(w.tags.size());
        for (int i=0;i<w.nds.size();i++)
            writeNodeLocation(out,w.nds.get(i));
        for (var tag:w.tags.entrySet())
        {
            out.writeUTF(tag.getKey());
            out.writeUTF(tag.getValue());
        }
    }

    private void processRelation(OSMRelation r) throws IOException
    {
        rc++;
        if (!Oma.silent && rc%1000==0)
            System.err.printf("Step 1: relations: ~%.1f%%        \r",100.0/exp_rels*rc);

        if (!all_nodes_read) endNodes();
        if (!all_ways_read) endWays();

        if (r.tags.size()<=1) return;

        if (!"multipolygon".equals(r.tags.get("type")) && !"boundary".equals(r.tags.get("type"))) return;

        rc_used++;

        out.writeByte('M');
        if (Oma.preserve_id)
            out.writeLong(r.id);
        if (Oma.preserve_version)
            out.writeInt(r.version);
        if (Oma.preserve_timestamp)
            out.writeLong(r.timestamp);
        if (Oma.preserve_changeset)
            out.writeLong(r.changeset);
        if (Oma.preserve_user)
        {
            out.writeInt(r.uid);
            out.writeUTF(r.user);
        }
        out.writeInt(r.tags.size());
        for (var tag:r.tags.entrySet())
        {
            out.writeUTF(tag.getKey());
            out.writeUTF(tag.getValue());
        }
        int maz = 0;
        for (Member m:r.members)
            if ("way".equals(m.type) && ("outer".equals(m.role) || "inner".equals(m.role)))
                maz++;
        out.writeInt(maz);
        for (Member m:r.members)
            if ("way".equals(m.type) && ("outer".equals(m.role) || "inner".equals(m.role)))
            {
                out.writeByte("outer".equals(m.role)?'o':'i');
                out.writeLong(m.ref);
                missing_ways++;
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
        node_end = out.getPosition();

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
        way_end = out.getPosition();

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
            readTmpNodes(nis);

            if (Oma.verbose>=3)
                System.out.println("      Pass "+(pass+1)+": "+nodes_c+" nodes read.");

            updateNodesOfOutfile();
            updateNodesOfWayfile();
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

    private void updateNodesOfOutfile() throws IOException
    {
        long fs = out.fileSize();

        Path original = Tools.tmpFile("original");
        out.move(original);

        OmaInputStream in = OmaInputStream.init(original);
        out = OmaOutputStream.init(outfile,true);
        if (!Oma.silent)
            System.err.print("Step 1: copying          \r");
        out.copyFrom(in,node_end);

        byte type = 0;
        int c = 0;
        while (true)
        {
            if (!Oma.silent && ++c%100000==0)
            {
                long sz = out.fileSize();
                System.err.printf("Step 1: updating nodes of outfile: %.1f%%        \r",100.0/fs*sz);
            }

            try {
                type = in.readByte();
            } catch (EOFException e) { break; }

            out.writeByte(type);
            switch (type)
            {
            case 'B' ->
                {
                    out.writeLong(in.readLong());
                    out.writeLong(in.readLong());
                }
            case 'N' ->
                {
                    if (Oma.preserve_id)
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
                    int az = in.readInt();
                    out.writeInt(az);
                    out.writeLong(in.readLong());
                    for (int i=0;i<2*az;i++)
                        out.writeUTF(in.readUTF());
                }
            case 'W' ->
                {
                    if (Oma.preserve_id)
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
                    int naz = in.readInt();
                    out.writeInt(naz);
                    int taz = in.readInt();
                    out.writeInt(taz);
                    for (int i=0;i<naz;i++)
                    {
                        long id = in.readLong();
                        if (id>=ID_MARKER)
                        {
                            int pos = Arrays.binarySearch(nodes_id,0,nodes_c,id-ID_MARKER);
                            if (pos>=0)
                            {
                                out.writeInt(nodes_lon[pos]);
                                out.writeInt(nodes_lat[pos]);
                            }
                            else
                                out.writeLong(id);
                        }
                        else
                            out.writeLong(id);
                    }
                    for (int i=0;i<2*taz;i++)
                        out.writeUTF(in.readUTF());
                }
            case 'M' ->
                {
                    if (Oma.preserve_id)
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
                    int taz = in.readInt();
                    out.writeInt(taz);
                    for (int i=0;i<2*taz;i++)
                        out.writeUTF(in.readUTF());
                    int maz = in.readInt();
                    out.writeInt(maz);
                    for (int i=0;i<maz;i++)
                    {
                        out.writeByte(in.readByte());
                        out.writeLong(in.readLong());
                    }
                }
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
        out.close();
    }

    private void updateNodesOfWayfile() throws IOException
    {
        long fs = out.fileSize();

        Path original = Tools.tmpFile("original");
        wos.move(original);

        OmaInputStream in = OmaInputStream.init(wos);
        wos = OmaOutputStream.init(ways,true);

        byte type = 0;
        int c = 0;
        while (true)
        {
            if (!Oma.silent && ++c%100000==0)
            {
                long sz = wos.fileSize();
                System.err.printf("Step 1: updating nodes of wayfile: %.1f%%        \r",100.0/fs*sz);
            }

            try {
                wos.writeLong(in.readLong());
            } catch (EOFException e) { break; }

            int naz = in.readInt();
            wos.writeInt(naz);
            for (int i=0;i<naz;i++)
            {
                long id = in.readLong();
                if (id>=ID_MARKER)
                {
                    int pos = Arrays.binarySearch(nodes_id,0,nodes_c,id-ID_MARKER);
                    if (pos>=0)
                    {
                        wos.writeInt(nodes_lon[pos]);
                        wos.writeInt(nodes_lat[pos]);
                    }
                    else
                        wos.writeLong(id);
                }
                else
                    wos.writeLong(id);
            }
        }
        if (!Oma.silent)
            System.err.print("Step 1:                                                                      \r");

        in.release();
        wos.close();
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
            pass++;
            boolean finished = readTmpWays(wis);

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
        long fs = out.fileSize();

        Path original = Tools.tmpFile("original");
        out.move(original);

        OmaInputStream in = OmaInputStream.init(out);
        out = OmaOutputStream.init(outfile,true);
        if (!Oma.silent)
            System.err.print("Step 1: copying          \r");
        out.copyFrom(in,way_end);

        byte type = 0;
        int c = 0;
        while (true)
        {
            if (!Oma.silent && ++c%100000==0)
            {
                long sz = out.fileSize();
                System.err.printf("Step 1: updating ways of outfile: %.1f%%        \r",100.0/fs*sz);
            }

            try {
                type = in.readByte();
            } catch (EOFException e) { break; }

            out.writeByte(type);
            switch (type)
            {
            case 'B' ->
                {
                    out.writeLong(in.readLong());
                    out.writeLong(in.readLong());
                }
            case 'N' ->
                {
                    if (Oma.preserve_id)
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
                    int az = in.readInt();
                    out.writeInt(az);
                    out.writeLong(in.readLong());
                    for (int i=0;i<2*az;i++)
                        out.writeUTF(in.readUTF());
                }
            case 'W' ->
                {
                    if (Oma.preserve_id)
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
                    int naz = in.readInt();
                    out.writeInt(naz);
                    int taz = in.readInt();
                    out.writeInt(taz);
                    for (int i=0;i<naz;i++)
                        out.writeLong(in.readLong());
                    for (int i=0;i<2*taz;i++)
                        out.writeUTF(in.readUTF());
                }
            case 'M' ->
                {
                    if (Oma.preserve_id)
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
                    int taz = in.readInt();
                    out.writeInt(taz);
                    for (int i=0;i<2*taz;i++)
                        out.writeUTF(in.readUTF());
                    int maz = in.readInt();
                    out.writeInt(maz);
                    for (int i=0;i<maz;i++)
                    {
                        byte role = in.readByte();
                        if (role=='o' || role=='i')
                        {
                            long id = in.readLong();
                            int pos = Arrays.binarySearch(ways_id,0,ways_c,id);
                            if (pos>=0)
                            {
                                out.writeByte(role-'a'+'A');
                                out.write(ways_data[pos]);
                            }
                            else
                            {
                                // not yet found
                                out.writeByte(role);
                                out.writeLong(id);
                            }
                        }
                        else
                        {
                            out.writeByte(role);
                            int naz = in.readInt();
                            out.writeInt(naz);
                            for (int j=0;j<naz;j++)
                                out.writeLong(in.readLong());
                        }
                    }
                }
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
        out.close();
    }

    //////////////////////////////////////////////////////////////////

    public void convertMultipolygonsToAreas() throws IOException
    {
        if (Oma.verbose>=2)
            System.out.println("  Converting multipolygons to areas...");

        long fs = out.fileSize();

        Path original = Tools.tmpFile("original");
        out.move(original);

        OmaInputStream in = OmaInputStream.init(out);
        out = OmaOutputStream.init(outfile,true);
        if (!Oma.silent)
            System.err.print("Step 1: copying          \r");
        out.copyFrom(in,way_end);

        long multi = 0;
        long area = 0;

        byte type = 0;
        int c = 0;
        while (true)
        {
            if (!Oma.silent && ++c%100000==0)
            {
                long sz = out.fileSize();
                System.err.printf("Step 1: converting multipolygons: %.1f%%        \r",100.0/fs*sz);
            }

            try {
                type = in.readByte();
            } catch (EOFException e) { break; }

            switch (type)
            {
            case 'B' ->
                {
                    out.writeByte(type);
                    out.writeLong(in.readLong());
                    out.writeLong(in.readLong());
                }
            case 'N' ->
                {
                    out.writeByte(type);
                    if (Oma.preserve_id)
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
                    int az = in.readInt();
                    out.writeInt(az);
                    out.writeLong(in.readLong());
                    for (int i=0;i<2*az;i++)
                        out.writeUTF(in.readUTF());
                }
            case 'W' ->
                {
                    out.writeByte(type);
                    if (Oma.preserve_id)
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
                    int naz = in.readInt();
                    out.writeInt(naz);
                    int taz = in.readInt();
                    out.writeInt(taz);
                    for (int i=0;i<naz;i++)
                        out.writeLong(in.readLong());
                    for (int i=0;i<2*taz;i++)
                        out.writeUTF(in.readUTF());
                }
            case 'M' ->
                {
                    long id = Oma.preserve_id?in.readLong():0;
                    int version = Oma.preserve_version?in.readInt():0;
                    long timestamp = Oma.preserve_timestamp?in.readLong():0;
                    long changeset = Oma.preserve_changeset?in.readLong():0;
                    int uid = Oma.preserve_user?in.readInt():0;
                    String user = Oma.preserve_user?in.readUTF():"";

                    int taz = in.readInt();
                    Map<String, String> tags = new HashMap<>();

                    for (int i=0;i<taz;i++)
                        tags.put(in.readUTF(),in.readUTF());

                    Multipolygon mp = new Multipolygon(id,version,timestamp,changeset,uid,user,tags);

                    boolean incomplete = false;

                    int maz = in.readInt();
                    for (int i=0;i<maz;i++)
                    {
                        byte role = in.readByte();
                        if (role=='i' || role=='o')
                        {
                            incomplete = true;
                            in.readLong();
                            continue;
                        }
                        int naz = in.readInt();
                        int[] lon = new int[naz];
                        int[] lat = new int[naz];
                        for (int j=0;j<naz;j++)
                        {
                            lon[j] = in.readInt();
                            lat[j] = in.readInt();
                        }
                        mp.add(lon,lat,role=='I');
                    }

                    if (incomplete) break;

                    mp.createRings();
                    if (mp.sortRings())
                    {
                        for (Area a:mp.areas)
                        {
                            out.writeByte('A');
                            if (Oma.preserve_id)
                                out.writeLong(id);
                            if (Oma.preserve_version)
                                out.writeInt(version);
                            if (Oma.preserve_timestamp)
                                out.writeLong(timestamp);
                            if (Oma.preserve_changeset)
                                out.writeLong(changeset);
                            if (Oma.preserve_user)
                            {
                                out.writeInt(uid);
                                out.writeUTF(user);
                            }
                            out.writeInt(a.lon.length-1);
                            for (int i=0;i<a.lon.length-1;i++)
                            {
                                out.writeInt(a.lon[i]);
                                out.writeInt(a.lat[i]);
                            }
                            out.writeInt(a.h_lon.length);
                            for (int j=0;j<a.h_lon.length;j++)
                            {
                                out.writeInt(a.h_lon[j].length-1);
                                for (int i=0;i<a.h_lon[j].length-1;i++)
                                {
                                    out.writeInt(a.h_lon[j][i]);
                                    out.writeInt(a.h_lat[j][i]);
                                }
                            }
                            out.writeInt(taz-1);
                            for (String key:mp.tags.keySet())
                            {
                                if ("type".equals(key)) continue;
                                out.writeUTF(key);
                                out.writeUTF(mp.tags.get(key));
                            }
                            area++;
                        }
                        multi++;
                    }
                }
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
        out.close();

        if (Oma.verbose>=2)
            System.out.println("    "+multi+" multipolygons converted to "+area+" areas.");
    }
}

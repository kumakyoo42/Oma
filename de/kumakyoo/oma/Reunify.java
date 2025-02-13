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
    // 213°. IDs won't reach this size in the foreseable future - the
    // database would have to increase its size a billion times.
    public static long ID_MARKER = 0x7f00000000000000L;

    private Path infile;
    private Path outfile;

    private Path ntmp;
    private Path wtmp;
    private Path rwtmp;
    private Path ratmp;
    private Path rctmp;

    private Path nodes;

    private OmaOutputStream nos;

    private OmaOutputStream out;
    private OmaOutputStream nout;
    private OmaOutputStream wout;
    private OmaOutputStream aout;
    private OmaOutputStream rwout;
    private OmaOutputStream raout;
    private OmaOutputStream rcout;

    private long nc,wc,rc,rac,rwc,rcc;

    private boolean all_nodes_read;
    private boolean all_ways_read;

    private long[] ids;

    private int[] nodes_lon;
    private int[] nodes_lat;
    private int nodes_c;

    private byte[][] ways_data;
    private int ways_c;

    private Map<Long,List<Member>> members;

    private long missing_nodes;
    private long missing_ways;

    private Bounds bounding_box;

    public Reunify(Path infile, Path outfile)
    {
        this.infile = infile;
        this.outfile = outfile;
    }

    public OmaOutputStream process() throws IOException
    {
        allocateMemory("nodes");
        readFile();
        updateNodes();
        releaseMemory();

        allocateMemory("ways");
        updateWays();
        releaseMemory();

        out = OmaOutputStream.init(outfile,true);
        out.writeByte('B');
        bounding_box.write(out);
        addMembers();
        out.close();

        return out;
    }

    private void allocateMemory(String type)
    {
        long available = Tools.memavail();
        long useable = (available-Oma.memlimit)/10*9;
        if (useable<100000) useable = available/5*4;

        if (Oma.verbose>=3)
        {
            System.out.println("      Available memory: "+Tools.humanReadable(available));
            System.out.println("      Useable: "+Tools.humanReadable(useable));
        }

        long wish =
            switch (type)
            {
                case "nodes" -> useable/16+1;
                case "ways" -> useable/90+1;
                case "relations" -> useable/10000+1;
                default -> -1;
            };

        if ("relations".equals(type)) wish = 900; // xxx

        int max = wish>Integer.MAX_VALUE-10?Integer.MAX_VALUE-10:(int)wish;

        while (true)
            try
            {
                if (Oma.verbose>=3)
                    System.out.println("      Trying to allocate "+max+" "+type+"...");

                ids = new long[max];

                switch (type)
                {
                    case "nodes" ->
                    {
                        nodes_lon = new int[max];
                        nodes_lat = new int[max];
                    }
                    case "ways" -> ways_data = new byte[max][];
                }
                break;
            }
            catch (OutOfMemoryError e)
            {
                if (max<2)
                {
                    System.err.println("There seems to be almost no available memory. Giving up.");
                    System.exit(-1);
                }

                releaseMemory();
                max /= 2;

                if (Oma.verbose>=3)
                    System.out.println("      Didn't work. Halving amount. Keep fingers crossed...");
            }

        if (Oma.verbose>=3)
            System.out.println("      Allocation was successful.");
    }

    private void releaseMemory()
    {
        ids = null;
        nodes_lon = nodes_lat = null;
        ways_data = null;
        Tools.gc();
    }

    //////////////////////////////////////////////////////////////////

    private void readFile() throws IOException
    {
        if (Oma.verbose>=2)
            System.out.println("  Reading file '"+infile+"'...");

        OSMReader r = OSMReader.getReader(infile);

        ntmp = Tools.tmpFile("n");
        nout = OmaOutputStream.init(ntmp);
        wtmp = Tools.tmpFile("w");
        wout = OmaOutputStream.init(wtmp);
        rwtmp = Tools.tmpFile("rw");
        rwout = OmaOutputStream.init(rwtmp);
        ratmp = Tools.tmpFile("ra");
        raout = OmaOutputStream.init(ratmp);
        rctmp = Tools.tmpFile("rc");
        rcout = OmaOutputStream.init(rctmp);

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

        nout.close();
        wout.close();
        rwout.close();
        raout.close();
        rcout.close();

        r.close();

        if (Oma.verbose>=2)
        {
            System.out.println("    "+Tools.humanReadable(nc)+" nodes, "+Tools.humanReadable(wc)+" ways and "+Tools.humanReadable(rc)+" relations read.");
            System.out.println("    "+ElementWithID.discardedTags()+" tags discarded.");
        }
    }

    private void processBounds(Bounds b) throws IOException
    {
        bounding_box = b;
    }

    private void processNode(OSMNode n) throws IOException
    {
        nc++;
        if (!Oma.silent && nc%100000==0)
            System.err.print("Step 1: reading nodes: "+Tools.humanReadable(nc)+"        \r");

        if (nodes_c<ids.length)
        {
            ids[nodes_c] = n.id;
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

        writeMeta(nout,n);

        nout.writeInt(n.lon);
        nout.writeInt(n.lat);

        writeTags(nout,n);
    }

    private void processWay(OSMWay w) throws IOException
    {
        wc++;
        if (!Oma.silent && wc%10000==0)
            System.err.print("Step 1: reading ways: "+Tools.humanReadable(wc)+"       \r");

        if (!all_nodes_read) endNodes();

        writeMeta(wout,w);

        wout.writeSmallInt(w.nds.size());
        for (int i=0;i<w.nds.size();i++)
            writeNodeLocation(wout,w.nds.get(i));

        writeTags(wout,w);
    }

    private void processRelation(OSMRelation r) throws IOException
    {
        rc++;
        if (!Oma.silent && rc%1000==0)
            System.err.print("Step 1: reading relations: "+Tools.humanReadable(rc)+"        \r");

        if (!all_nodes_read) endNodes();
        if (!all_ways_read) endWays();

        String type = r.tags.get("type");

        if ("multipolygon".equals(type) || "boundary".equals(type))
        {
            rac++;
            writeMeta(raout,r);

            int maz = 0;
            for (OSMMember m:r.members)
                if ("way".equals(m.type) && ("outer".equals(m.role) || "inner".equals(m.role)))
                    maz++;

            raout.writeSmallInt(maz);
            for (OSMMember m:r.members)
                if ("way".equals(m.type) && ("outer".equals(m.role) || "inner".equals(m.role)))
                {
                    raout.writeString(m.role);
                    raout.writeByte('w');
                    raout.writeLong(m.ref);
                    m.ref = -1;
                    missing_ways++;
                }

            writeTags(raout,r);
        }

        if ("restriction".equals(type) || "destination_sign".equals(type))
        {
            rwc++;
            writeMeta(rwout,r);

            int maz = 0;
            for (OSMMember m:r.members)
                if ("way".equals(m.type) && ("from".equals(m.role) || "to".equals(m.role) || "via".equals(m.role) || "intersection".equals(m.role)))
                    maz++;
            for (OSMMember m:r.members)
                if ("node".equals(m.type) && ("via".equals(m.role) || "intersection".equals(m.role)))
                    maz++;

            rwout.writeSmallInt(maz);
            for (OSMMember m:r.members)
                if ("way".equals(m.type) && ("from".equals(m.role) || "to".equals(m.role) || "via".equals(m.role) || "intersection".equals(m.role)))
                {
                    rwout.writeString(m.role);
                    rwout.writeByte('w');
                    rwout.writeLong(m.ref);
                    m.ref = -1;
                    missing_ways++;
                }
            for (OSMMember m:r.members)
                if ("node".equals(m.type) && ("via".equals(m.role) || "intersection".equals(m.role)))
                {
                    rwout.writeString(m.role);
                    rwout.writeByte('n');
                    writeNodeLocation(rwout,m.ref);
                    m.ref = -1;
                }

            writeTags(rwout,r);
        }

        int raz = 0;
        for (OSMMember m:r.members)
            if (m.ref!=-1)
                raz++;

        if (raz==0) return;

        rcc++;
        writeMeta(rcout,r);

        rcout.writeSmallInt(raz);
        for (OSMMember m:r.members)
            if (m.ref!=-1)
            {
                rcout.writeString(m.role);
                rcout.writeByte(m.type.charAt(0));
                rcout.writeLong(m.ref);
            }

        writeTags(rcout,r);
    }

    private void writeMeta(OmaOutputStream out, ElementWithID e) throws IOException
    {
        out.writeLong(e.id);
        if (Oma.preserve_version)
            out.writeSmallInt(e.version);
        if (Oma.preserve_timestamp)
            out.writeLong(e.timestamp);
        if (Oma.preserve_changeset)
            out.writeLong(e.changeset);
        if (Oma.preserve_user)
        {
            out.writeInt(e.uid);
            out.writeString(e.user);
        }
    }

    private void writeTags(OmaOutputStream out, ElementWithID e) throws IOException
    {
        out.writeSmallInt(e.tags.size());
        for (var tag:e.tags.entrySet())
        {
            out.writeString(tag.getKey());
            out.writeString(tag.getValue());
        }
    }

    private void initTmpNodes() throws IOException
    {
        nodes = Tools.tmpFile("nodes");
        nos = OmaOutputStream.init(nodes);
    }

    private void endNodes() throws IOException
    {
        if (Oma.verbose>=3)
        {
            System.err.print("                                                                              \r");
            System.out.println("      "+Tools.humanReadable(nc)+" nodes read.");
        }

        all_nodes_read = true;

        if (nos==null) return;
        nos.close();

        if (Oma.verbose>=3)
            System.out.println("      "+Tools.humanReadable(nos.fileSize()/16)+" nodes temporarily saved.");
    }

    private void endWays() throws IOException
    {
        if (Oma.verbose>=3)
        {
            System.err.print("                                                                              \r");
            System.out.println("      "+Tools.humanReadable(wc)+" ways read.");
        }

        all_ways_read = true;
    }

    private void endRelations()
    {
        if (Oma.verbose>=3)
        {
            System.err.print("                                                                              \r");
            System.out.println("      "+Tools.humanReadable(rc)+" relations read ("+rwc+" ways, "+rac+" areas, "+rcc+" collections).");
        }
    }

    // Searching node, using binary search. If node is not found,
    // writing marked id instead.
    private void writeNodeLocation(OmaOutputStream s, long id) throws IOException
    {
        int pos = Arrays.binarySearch(ids,0,nodes_c,id);
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
        long passes = node_count/ids.length;
        if (node_count%ids.length>0) passes++;

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

            updateNodesOfWays();
            updateNodesOfRelationWays();
            updateNodesOfRelationAreas();
        }
        nis.release();
    }

    private void readTmpNodes(OmaInputStream nis) throws IOException
    {
        try
        {
            for (nodes_c=0;nodes_c<ids.length;nodes_c++)
            {
                ids[nodes_c] = nis.readLong();
                nodes_lon[nodes_c] = nis.readInt();
                nodes_lat[nodes_c] = nis.readInt();
            }
        }
        catch (EOFException e)
        {
            nis.release();
        }
    }

    private void updateNodesOfWays() throws IOException
    {
        if (!Oma.silent)
            System.err.print("Step 1: preparing update of ways                           \r");

        wout.move(Tools.tmpFile("original"));

        OmaInputStream in = OmaInputStream.init(wout);
        wout = OmaOutputStream.init(wtmp,true);

        for (int c=0;c<wc;c++)
        {
            if (!Oma.silent && c%100000==0)
                System.err.printf("Step 1: updating nodes of ways: %.1f%%        \r",100.0/wc*c);

            copyMetaData(wout, in);
            copyArrayOfNodesReplacingIDs(in,wout);
            copyTags(wout,in);
        }
        if (!Oma.silent)
            System.err.print("Step 1:                                                                     \r");

        in.release();
        wout.close();
    }

    private void updateNodesOfRelationWays() throws IOException
    {
        if (!Oma.silent)
            System.err.print("Step 1: preparing update of ways from relations                   \r");

        rwout.move(Tools.tmpFile("original"));

        OmaInputStream in = OmaInputStream.init(rwout);
        rwout = OmaOutputStream.init(rwtmp,true);

        for (int c=0;c<rwc;c++)
        {
            if (!Oma.silent && c%100000==0)
                System.err.printf("Step 1: updating nodes of ways from relations: %.1f%%        \r",100.0/rwc*c);

            copyMetaData(rwout, in);
            copyMemberReplacingIDs(in, rwout);
            copyTags(rwout,in);
        }
        if (!Oma.silent)
            System.err.print("Step 1:                                                                     \r");

        in.release();
        rwout.close();
    }

    private void updateNodesOfRelationAreas() throws IOException
    {
        if (!Oma.silent)
            System.err.print("Step 1: preparing update of areas from relations                   \r");

        raout.move(Tools.tmpFile("original"));

        OmaInputStream in = OmaInputStream.init(raout);
        raout = OmaOutputStream.init(ratmp,true);

        for (int c=0;c<rac;c++)
        {
            if (!Oma.silent && c%100000==0)
                System.err.printf("Step 1: updating nodes of areas from relations: %.1f%%        \r",100.0/rac*c);

            copyMetaData(raout, in);
            copyMemberReplacingIDs(in, raout);
            copyTags(raout,in);
        }
        if (!Oma.silent)
            System.err.print("Step 1:                                                                     \r");

        in.release();
        raout.close();
    }

    private void copyArrayOfNodesReplacingIDs(OmaInputStream in, OmaOutputStream out) throws IOException
    {
        int naz = in.readSmallInt();
        out.writeSmallInt(naz);
        for (int i=0;i<naz;i++)
            copyReplacingNodeID(in,out);
    }

    private void copyMemberReplacingIDs(OmaInputStream in, OmaOutputStream out) throws IOException
    {
        int maz = in.readSmallInt();
        out.writeSmallInt(maz);
        for (int i=0;i<maz;i++)
        {
            out.writeString(in.readString());
            byte type = in.readByte();
            switch (type)
            {
            case 'w':
                out.writeByte('w');
                out.writeLong(in.readLong());
                break;
            case 'n':
                out.writeByte('n');
                copyReplacingNodeID(in,out);
                break;
            default:
                System.err.println("unknown type: "+(char)type);
                System.exit(-1);
            }
        }
    }

    private void copyReplacingNodeID(OmaInputStream in, OmaOutputStream out) throws IOException
    {
        long id = in.readLong();
        if (id>=ID_MARKER)
        {
            int pos = Arrays.binarySearch(ids,0,nodes_c,id-ID_MARKER);
            if (pos>=0)
            {
                out.writeInt(nodes_lon[pos]);
                out.writeInt(nodes_lat[pos]);
                return;
            }
        }
        out.writeLong(id);
    }

    //////////////////////////////////////////////////////////////////

    private void updateWays() throws IOException
    {
        if (Oma.verbose>=2)
            System.out.println("  Updating missing ways...");
        if (Oma.verbose>=3)
            System.out.println("      Ways missing: "+Tools.humanReadable(missing_ways)+".");

        if (missing_ways>0)
            addMissingWays();

        if (Oma.verbose>=2)
            System.out.println("    All ways updated.");
    }

    private void addMissingWays() throws IOException
    {
        OmaInputStream wis = OmaInputStream.init(wout);
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

            updateWaysOfRelationWays();
            updateWaysOfRelationAreas();

            if (finished) break;
        }
        wis.close();
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
                if (Oma.preserve_version)
                    wis.readSmallInt();
                if (Oma.preserve_timestamp)
                    wis.readLong();
                if (Oma.preserve_changeset)
                    wis.readLong();
                if (Oma.preserve_user)
                {
                    wis.readInt();
                    wis.readString();
                }

                int az = wis.readSmallInt();
                ByteArrayOutputStream baos = new ByteArrayOutputStream(4+8*az);
                new OmaOutputStream(baos).writeSmallInt(az);
                byte[] b = new byte[8*az];
                wis.readFully(b);
                baos.write(b);

                ids[ways_c] = id;
                ways_data[ways_c] = baos.toByteArray();
                ways_c++;

                az = wis.readSmallInt();
                for (int i=0;i<2*az;i++)
                    wis.readString();

                if (ways_c==ids.length || Tools.memavail()<Oma.memlimit)
                    return false;
            }
        }
        catch (EOFException e) {}

        return true;
    }

    private void updateWaysOfRelationWays() throws IOException
    {
        Path original = Tools.tmpFile("original");
        rwout.move(original);

        OmaInputStream in = OmaInputStream.init(rwout);
        rwout = OmaOutputStream.init(rwtmp,true);

        for (int i=0;i<rwc;i++)
        {
            if (!Oma.silent && i%10000==0)
                System.err.printf("Step 1: updating ways of way relations: %.1f%%        \r",100.0/rwc*i);

            copyMetaData(rwout, in);
            copyMemberReplacingWays(in, rwout);
            copyTags(rwout,in);
        }
        if (!Oma.silent)
            System.err.print("Step 1:                                                                      \r");

        in.release();
        rwout.close();
    }

    private void updateWaysOfRelationAreas() throws IOException
    {
        Path original = Tools.tmpFile("original");
        raout.move(original);

        OmaInputStream in = OmaInputStream.init(raout);
        raout = OmaOutputStream.init(ratmp,true);

        for (int i=0;i<rac;i++)
        {
            if (!Oma.silent && i%10000==0)
                System.err.printf("Step 1: updating ways of area relations: %.1f%%        \r",100.0/rac*i);

            copyMetaData(raout, in);
            copyMemberReplacingWays(in, raout);
            copyTags(raout,in);
        }
        if (!Oma.silent)
            System.err.print("Step 1:                                                                      \r");

        in.release();
        raout.close();
    }

    //////////////////////////////////////////////////////////////////

    private void copyMemberReplacingWays(OmaInputStream in, OmaOutputStream out) throws IOException
    {
        int maz = in.readSmallInt();
        out.writeSmallInt(maz);
        for (int i=0;i<maz;i++)
        {
            out.writeString(in.readString());
            byte type = in.readByte();
            switch (type)
            {
            case 'w':
                long id = in.readLong();
                int pos = Arrays.binarySearch(ids,0,ways_c,id);
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
                break;
            case 'W':
                out.writeByte('W');
                int naz = in.readSmallInt();
                out.writeSmallInt(naz);
                for (int j=0;j<naz;j++)
                    out.writeLong(in.readLong());
                break;
            case 'n':
                out.writeByte('n');
                out.writeLong(in.readLong());
                break;
            default:
                System.err.println("unknown type: "+(char)type);
                System.exit(-1);
            }
        }
    }

    private void copyMetaData(OmaOutputStream out, OmaInputStream in) throws IOException
    {
        out.writeLong(in.readLong());
        if (Oma.preserve_version)
            out.writeSmallInt(in.readSmallInt());
        if (Oma.preserve_timestamp)
            out.writeLong(in.readLong());
        if (Oma.preserve_changeset)
            out.writeLong(in.readLong());
        if (Oma.preserve_user)
        {
            out.writeInt(in.readInt());
            out.writeString(in.readString());
        }
    }

    private void copyTags(OmaOutputStream out, OmaInputStream in) throws IOException
    {
        int taz = in.readSmallInt();
        out.writeSmallInt(taz);
        for (int i=0;i<2*taz;i++)
            out.writeString(in.readString());
    }

    //////////////////////////////////////////////////////////////////

    private void addMembers() throws IOException
    {
        if (Oma.verbose>=2)
            System.out.println("  Adding members...");

        readMembers('n');
        addNodes();
        readMembers('w');
        addWays();
        readMembers('r');
        addRelationWays();
        addRelationAreas();
        addCollections();

        if (Oma.verbose>=2)
            System.out.println("    All members added.");
    }

    private void readMembers(char type) throws IOException
    {
        members = new HashMap<>();

        OmaInputStream in = OmaInputStream.init(rcout);

        for (int j=0;j<rcc;j++)
        {
            long rid = in.readLong();
            if (Oma.preserve_version)
                in.readSmallInt();
            if (Oma.preserve_timestamp)
                in.readLong();
            if (Oma.preserve_changeset)
                in.readLong();
            if (Oma.preserve_user)
            {
                in.readInt();
                in.readString();
            }

            int maz = in.readSmallInt();
            for (int i=0;i<maz;i++)
            {
                String role = in.readString();
                byte mtype = in.readByte();
                long id = in.readLong();

                if (type==mtype)
                {
                    List<Member> m = members.get(id);
                    if (m==null)
                        m = new ArrayList<>();
                    m.add(new Member(rid,role,i));
                    members.put(id,m);
                }
            }

            int taz = in.readSmallInt();
            for (int i=0;i<2*taz;i++)
                in.readString();
        }
        in.close();
    }

    private void addNodes() throws IOException
    {
        OmaInputStream in = OmaInputStream.init(nout);

        for (int i=0;i<nc;i++)
        {
            if (!Oma.silent && i%10000==0)
                System.err.printf("Step 1: adding members: %.1f%% (nodes)       \r",100.0/(nc+wc+rwc+rac+rcc)*i);

            long id = in.readLong();
            List<Member> mlist = members.get(id);
            int version = Oma.preserve_version?in.readSmallInt():0;
            long timestamp = Oma.preserve_timestamp?in.readLong():0;
            long changeset = Oma.preserve_changeset?in.readLong():0;
            int uid = Oma.preserve_user?in.readInt():0;
            String user = Oma.preserve_user?in.readString():null;

            long lonlat = in.readLong();

            int taz = in.readSmallInt();

            if (taz==0 && mlist==null) continue;

            out.writeByte('N');
            if (Oma.preserve_id)
                out.writeLong(id);
            if (Oma.preserve_version)
                out.writeSmallInt(version);
            if (Oma.preserve_timestamp)
                out.writeLong(timestamp);
            if (Oma.preserve_changeset)
                out.writeLong(changeset);
            if (Oma.preserve_user)
            {
                out.writeInt(uid);
                out.writeString(user);
            }

            out.writeLong(lonlat);

            out.writeSmallInt(taz);
            for (int j=0;j<2*taz;j++)
                out.writeString(in.readString());

            if (mlist==null)
                out.writeSmallInt(0);
            else
            {
                out.writeSmallInt(mlist.size());
                for (Member m:mlist)
                {
                    out.writeLong(m.id);
                    out.writeString(m.role);
                    out.writeSmallInt(m.nr);
                }
            }
        }
        if (!Oma.silent)
            System.err.print("Step 1:                                                                      \r");

        in.release();
    }

    private void addWays() throws IOException
    {
        OmaInputStream in = OmaInputStream.init(wout);

        for (int i=0;i<wc;i++)
        {
            if (!Oma.silent && i%10000==0)
                System.err.printf("Step 1: adding members: %.1f%% (ways)       \r",100.0/(nc+wc+rwc+rac+rcc)*(nc+i));

            long id = in.readLong();
            List<Member> mlist = members.get(id);
            int version = Oma.preserve_version?in.readSmallInt():0;
            long timestamp = Oma.preserve_timestamp?in.readLong():0;
            long changeset = Oma.preserve_changeset?in.readLong():0;
            int uid = Oma.preserve_user?in.readInt():0;
            String user = Oma.preserve_user?in.readString():null;

            int naz = in.readSmallInt();
            long[] lonlat = new long[naz];
            for (int j=0;j<naz;j++)
                lonlat[j] = in.readLong();

            int taz = in.readSmallInt();

            if (taz==0 && mlist==null) continue;

            out.writeByte('W');
            if (Oma.preserve_id)
                out.writeLong(id);
            if (Oma.preserve_version)
                out.writeSmallInt(version);
            if (Oma.preserve_timestamp)
                out.writeLong(timestamp);
            if (Oma.preserve_changeset)
                out.writeLong(changeset);
            if (Oma.preserve_user)
            {
                out.writeInt(uid);
                out.writeString(user);
            }

            out.writeSmallInt(naz);
            for (int j=0;j<naz;j++)
                out.writeLong(lonlat[j]);

            out.writeSmallInt(taz);
            for (int j=0;j<2*taz;j++)
                out.writeString(in.readString());

            if (mlist==null)
                out.writeSmallInt(0);
            else
            {
                out.writeSmallInt(mlist.size());
                for (Member m:mlist)
                {
                    out.writeLong(m.id);
                    out.writeString(m.role);
                    out.writeSmallInt(m.nr);
                }
            }
        }
        if (!Oma.silent)
            System.err.print("Step 1:                                                                      \r");

        in.release();
    }

    private void addRelationWays() throws IOException
    {
        OmaInputStream in = OmaInputStream.init(rwout);

        for (int i=0;i<rwc;i++)
        {
            if (!Oma.silent && i%10000==0)
                System.err.printf("Step 1: adding members: %.1f%% (ways)       \r",100.0/(nc+wc+rwc+rac+rcc)*(nc+wc+i));

            long id = in.readLong();
            List<Member> mlist = members.get(id);
            int version = Oma.preserve_version?in.readSmallInt():0;
            long timestamp = Oma.preserve_timestamp?in.readLong():0;
            long changeset = Oma.preserve_changeset?in.readLong():0;
            int uid = Oma.preserve_user?in.readInt():0;
            String user = Oma.preserve_user?in.readString():null;

            FromTo f = new FromTo();
            int maz = in.readSmallInt();
            for (int j=0;j<maz;j++)
            {
                String role = in.readString();
                byte type = in.readByte();
                if (type=='W')
                {
                    Way w = new Way();
                    int naz = in.readSmallInt();
                    int[] lon = new int[naz];
                    int[] lat = new int[naz];
                    for (int l=0;l<naz;l++)
                    {
                        lon[l] = in.readInt();
                        lat[l] = in.readInt();
                    }
                    w.lon = lon;
                    w.lat = lat;

                    if ("from".equals(role))
                        f.addFrom(w);
                    else if ("to".equals(role))
                        f.addTo(w);
                    else
                        f.addVia(w);
                    continue;
                }
                if (type=='n')
                {
                    Node n = new Node();
                    n.lon = in.readInt();
                    n.lat = in.readInt();
                    f.addVia(n);
                    continue;
                }
                in.readLong();
            }


            int taz = in.readSmallInt();
            String[] tags = new String[2*taz];
            for (int j=0;j<2*taz;j++)
                tags[j] = in.readString();

            f.createWays();

            for (Way w:f.ways)
            {
                out.writeByte('W');
                if (Oma.preserve_id)
                    out.writeLong(id);
                if (Oma.preserve_version)
                    out.writeSmallInt(version);
                if (Oma.preserve_timestamp)
                    out.writeLong(timestamp);
                if (Oma.preserve_changeset)
                    out.writeLong(changeset);
                if (Oma.preserve_user)
                {
                    out.writeInt(uid);
                    out.writeString(user);
                }

                out.writeSmallInt(w.lon.length);
                for (int j=0;j<w.lon.length;j++)
                {
                    out.writeInt(w.lon[j]);
                    out.writeInt(w.lat[j]);
                }

                out.writeSmallInt(taz);
                for (int j=0;j<2*taz;j++)
                    out.writeString(tags[j]);

                if (mlist==null)
                    out.writeSmallInt(0);
                else
                {
                    out.writeSmallInt(mlist.size());
                    for (Member m:mlist)
                    {
                        out.writeLong(m.id);
                        out.writeString(m.role);
                        out.writeSmallInt(m.nr);
                    }
                }
            }
        }
        if (!Oma.silent)
            System.err.print("Step 1:                                                                      \r");

        in.release();
    }

    private void addRelationAreas() throws IOException
    {
        OmaInputStream in = OmaInputStream.init(raout);

        for (int i=0;i<rac;i++)
        {
            if (!Oma.silent && i%10000==0)
                System.err.printf("Step 1: adding members: %.1f%% (areas)       \r",100.0/(nc+wc+rwc+rac+rcc)*(nc+wc+rwc+i));

            long id = in.readLong();
            List<Member> mlist = members.get(id);
            int version = Oma.preserve_version?in.readSmallInt():0;
            long timestamp = Oma.preserve_timestamp?in.readLong():0;
            long changeset = Oma.preserve_changeset?in.readLong():0;
            int uid = Oma.preserve_user?in.readInt():0;
            String user = Oma.preserve_user?in.readString():null;

            Multipolygon mp = new Multipolygon();
            int maz = in.readSmallInt();
            for (int j=0;j<maz;j++)
            {
                String role = in.readString();
                byte type = in.readByte();
                if (type=='W')
                {
                    int naz = in.readSmallInt();
                    int[] lon = new int[naz];
                    int[] lat = new int[naz];
                    for (int l=0;l<naz;l++)
                    {
                        lon[l] = in.readInt();
                        lat[l] = in.readInt();
                    }

                    mp.add(lon,lat,"inner".equals(role));
                    continue;
                }
                in.readLong();
            }

            int taz = in.readSmallInt();
            String[] tags = new String[2*taz];
            for (int j=0;j<2*taz;j++)
                tags[j] = in.readString();

            mp.createRings();
            mp.sortRings();

            for (Area a:mp.areas)
            {
                out.writeByte('A');
                if (Oma.preserve_id)
                    out.writeLong(id);
                if (Oma.preserve_version)
                    out.writeSmallInt(version);
                if (Oma.preserve_timestamp)
                    out.writeLong(timestamp);
                if (Oma.preserve_changeset)
                    out.writeLong(changeset);
                if (Oma.preserve_user)
                {
                    out.writeInt(uid);
                    out.writeString(user);
                }

                out.writeSmallInt(a.lon.length-1);
                for (int j=0;j<a.lon.length-1;j++)
                {
                    out.writeInt(a.lon[j]);
                    out.writeInt(a.lat[j]);
                }
                out.writeSmallInt(a.h_lon.length);
                for (int j=0;j<a.h_lon.length;j++)
                {
                    out.writeSmallInt(a.h_lon[j].length-1);
                    for (int k=0;k<a.h_lon[j].length-1;k++)
                    {
                        out.writeInt(a.h_lon[j][k]);
                        out.writeInt(a.h_lat[j][k]);
                    }
                }

                out.writeSmallInt(taz);
                for (int j=0;j<2*taz;j++)
                    out.writeString(tags[j]);

                if (mlist==null)
                    out.writeSmallInt(0);
                else
                {
                    out.writeSmallInt(mlist.size());
                    for (Member m:mlist)
                    {
                        out.writeLong(m.id);
                        out.writeString(m.role);
                        out.writeSmallInt(m.nr);
                    }
                }
            }
        }
        if (!Oma.silent)
            System.err.print("Step 1:                                                                      \r");

        in.release();
    }

    private void addCollections() throws IOException
    {
        OmaInputStream in = OmaInputStream.init(rcout);

        for (int i=0;i<rcc;i++)
        {
            if (!Oma.silent && i%10000==0)
                System.err.printf("Step 1: adding members: %.1f%% (collections)       \r",100.0/(nc+wc+rwc+rac+rcc)*(nc+wc+rwc+rac+i));

            long id = in.readLong();
            List<Member> mlist = members.get(id);
            int version = Oma.preserve_version?in.readSmallInt():0;
            long timestamp = Oma.preserve_timestamp?in.readLong():0;
            long changeset = Oma.preserve_changeset?in.readLong():0;
            int uid = Oma.preserve_user?in.readInt():0;
            String user = Oma.preserve_user?in.readString():null;

            int maz = in.readSmallInt();
            for (int j=0;j<maz;j++)
            {
                in.readString();
                in.readByte();
                in.readLong();
            }

            int taz = in.readSmallInt();
            String[] tags = new String[2*taz];
            for (int j=0;j<2*taz;j++)
                tags[j] = in.readString();

            out.writeByte('C');
            out.writeLong(id);
            if (Oma.preserve_version)
                out.writeSmallInt(version);
            if (Oma.preserve_timestamp)
                out.writeLong(timestamp);
            if (Oma.preserve_changeset)
                out.writeLong(changeset);
            if (Oma.preserve_user)
            {
                out.writeInt(uid);
                out.writeString(user);
            }

            Bounds.getNoBounds().write(out);

            out.writeSmallInt(taz);
            for (int j=0;j<2*taz;j++)
                out.writeString(tags[j]);

            if (mlist==null)
                out.writeSmallInt(0);
            else
            {
                out.writeSmallInt(mlist.size());
                for (Member m:mlist)
                {
                    out.writeLong(m.id);
                    out.writeString(m.role);
                    out.writeSmallInt(m.nr);
                }
            }
        }
        if (!Oma.silent)
            System.err.print("Step 1:                                                                      \r");

        in.release();
    }
}

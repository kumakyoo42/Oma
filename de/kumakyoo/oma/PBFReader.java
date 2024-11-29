package de.kumakyoo.oma;

import java.util.*;
import java.io.*;
import java.nio.file.*;
import java.util.zip.*;
import java.nio.charset.StandardCharsets;

public class PBFReader extends PackedIntegerReader
{
    private DataInputStream din;
    private PrimitiveBlock pb = null;

    public PBFReader(Path filename) throws IOException
    {
        din = Tools.getInStream(filename);
    }

    public void close() throws IOException
    {
        din.close();
    }

    public Element next() throws IOException
    {
        int len = 0;
        while (true)
        {
            if (pb!=null)
            {
                Element e = pb.next();
                if (e!=null) return e;
                pb = null;
            }

            try {
                len = din.readInt(); // sic!
            } catch (EOFException e) { return null; }

            BlobHeader bh = new BlobHeader(new DataInputStream(new ByteArrayInputStream(readBytes(din,len))));
            Blob b = new Blob(new DataInputStream(new ByteArrayInputStream(readBytes(din,bh.datasize))));
            if ("OSMHeader".equals(bh.type))
            {
                Bounds bounds = new HeaderBlock(b.din).getBounds();
                if (bounds!=null) return bounds;
            }
            else if ("OSMData".equals(bh.type))
                pb = new PrimitiveBlock(b.din);
            else
                throw new IOException("unknown PBF type: "+bh.type);
        }
    }

    //////////////////////////////////////////////////////////////////

    abstract class Message
    {
        DataInputStream in;

        public Message(DataInputStream in) throws IOException
        {
            this.in = in;

            if (this instanceof PrimitiveGroup) return;

            while (true)
                try {
                    readData((int)u(in)>>3);
                } catch (EOFException ex) { break; }
        }

        abstract public void readData(int nr) throws IOException;
    }

    class BlobHeader extends Message
    {
        String type;
        int datasize;

        public BlobHeader(DataInputStream in) throws IOException
        {
            super(in);
        }

        public void readData(int nr) throws IOException
        {
            switch (nr)
            {
                case 1 -> type = readString(in);
                case 3 -> datasize = (int)u(in);
                default -> throw new IOException("unknown PBF BlobHeader element: "+nr);
            }
        }
    }

    class Blob extends Message
    {
        DataInputStream din;

        public Blob(DataInputStream in) throws IOException
        {
            super(in);
        }

        public void readData(int nr) throws IOException
        {
            switch (nr)
            {
                case 2 -> u(in);
                case 3 -> din = new DataInputStream(new BufferedInputStream(new InflaterInputStream(new ByteArrayInputStream(readBytes(in)))));
                default -> throw new IOException("unknown PBF Blob element: "+nr);
            }
        }
    }

    class HeaderBlock extends Message
    {
        private Bounds bounds;

        public HeaderBlock(DataInputStream in) throws IOException
        {
            super(in);
        }

        public void readData(int nr) throws IOException
        {
            switch (nr)
            {
                case 1 -> bounds = new HeaderBBox(new DataInputStream(new ByteArrayInputStream(readBytes(in)))).getBounds();
                case 4 ->
                {
                    String feature = readString(in);
                    if (!"OsmSchema-V0.6".equals(feature) && !"DenseNodes".equals(feature))
                        throw new IOException("unknown required PBF feature: "+feature);
                }
                case 5,16,17,34 -> readString(in);
                case 32,33 -> u(in);
                default -> throw new IOException("unknown PBF HeaderBlock element: "+nr);
            }
        }

        public Bounds getBounds()
        {
            return bounds;
        }
    }

    class HeaderBBox extends Message
    {
        private long left,right,top,bottom;

        public HeaderBBox(DataInputStream in) throws IOException
        {
            super(in);
        }

        public void readData(int nr) throws IOException
        {
            switch (nr)
            {
                case 1 -> left = s(in);
                case 2 -> right = s(in);
                case 3 -> top = s(in);
                case 4 -> bottom = s(in);
                default -> throw new IOException("unknown PBF HeaderBBox element: "+nr);
            }
        }

        public Bounds getBounds()
        {
            return new Bounds((int)(left/100),(int)(bottom/100),(int)(right/100),(int)(top/100));
        }
    }

    class PrimitiveBlock extends Message
    {
        private StringTable st;
        private PrimitiveGroup pg;

        public PrimitiveBlock(DataInputStream in) throws IOException
        {
            super(in);
        }

        public void readData(int nr) throws IOException
        {
            switch (nr)
            {
                case 1 -> st = new StringTable(new DataInputStream(new ByteArrayInputStream(readBytes(in))));
                case 2 -> pg = new PrimitiveGroup(new DataInputStream(new ByteArrayInputStream(readBytes(in))));
                default -> throw new IOException("unknown PBF PrimitiveBlock element: "+nr);
            }
        }

        public Element next() throws IOException
        {
            return pg.next(st.getTable());
        }
    }

    class PrimitiveGroup extends Message
    {
        static final String[] TYPES = {"node","way","relation"};

        DenseNodes dn;
        Way way;
        Relation rel;

        public PrimitiveGroup(DataInputStream in) throws IOException
        {
            super(in);
        }

        public void readData(int nr) throws IOException
        {
            switch (nr)
            {
                case 2 -> dn = new DenseNodes(new DataInputStream(new ByteArrayInputStream(readBytes(in))));
                case 3 -> way = new Way(new DataInputStream(new ByteArrayInputStream(readBytes(in))));
                case 4 -> rel = new Relation(new DataInputStream(new ByteArrayInputStream(readBytes(in))));
                default -> throw new IOException("unknown PBF PrimitiveGroup element: "+nr);
            }
        }

        public Element next(List<String> st) throws IOException
        {
            if (dn==null && way==null && rel==null)
                try {
                    readData((int)u(in)>>3);
                } catch (EOFException ex) {}

            if (dn!=null)
            {
                Element e = dn.next(st);
                if (e==null)
                    dn = null;
                return e;
            }

            if (way!=null)
            {
                Map<String, String> t = new HashMap<>();
                if (way.keys!=null)
                {
                    try {
                        while (true)
                            t.put(st.get((int)u(way.keys)),st.get((int)u(way.values)));
                    } catch (EOFException e) {}
                }

                long delta = 0;
                List<Long> nds = new ArrayList<>();
                while (true)
                {
                    try {
                        delta += s(way.refs);
                    } catch (EOFException e) { break; }
                    nds.add(delta);
                }

                Element e = new OSMWay(way.id,way.info.version,way.info.ts,way.info.cs,way.info.uid,st.get(way.info.user),nds,t);
                way = null;
                return e;
            }

            if (rel!=null)
            {
                Map<String, String> t = new HashMap<>();
                if (rel.keys!=null)
                {
                    try {
                        while (true)
                            t.put(st.get((int)u(rel.keys)),st.get((int)u(rel.values)));
                    } catch (EOFException e) {}
                }

                long delta = 0;
                List<Member> members = new ArrayList<>();
                try {
                    while (true)
                    {
                        String role = st.get((int)u(rel.roles));
                        delta += s(rel.members);
                        String type = TYPES[(int)u(rel.types)];

                        members.add(new Member(type,delta,role));
                    }
                } catch (EOFException e) {}

                Element e = new OSMRelation(rel.id,rel.info.version,rel.info.ts,rel.info.cs,rel.info.uid,st.get(rel.info.user),members,t);
                rel = null;
                return e;

            }

            return null;
        }
    }

    class DenseNodes extends Message
    {
        DataInputStream ids;
        DataInputStream lat;
        DataInputStream lon;
        DataInputStream tags;

        long lastid = 0;
        long lastlat = 0;
        long lastlon = 0;

        DenseInfo di;

        public DenseNodes(DataInputStream in) throws IOException
        {
            super(in);
        }

        public void readData(int nr) throws IOException
        {
            switch (nr)
            {
                case 1 -> ids = new DataInputStream(new ByteArrayInputStream(readBytes(in)));
                case 5 -> di = new DenseInfo(new DataInputStream(new ByteArrayInputStream(readBytes(in))));
                case 8 -> lat = new DataInputStream(new ByteArrayInputStream(readBytes(in)));
                case 9 -> lon = new DataInputStream(new ByteArrayInputStream(readBytes(in)));
                case 10 -> tags = new DataInputStream(new ByteArrayInputStream(readBytes(in)));
                default -> throw new IOException("unknown PBF DenseNodes element: "+nr);
            }
        }

        public Element next(List<String> st) throws IOException
        {
            try {
                lastid += s(ids);
            } catch (EOFException e) { return null; }
            lastlat += s(lat);
            lastlon += s(lon);

            Map<String, String> t = new HashMap<>();
            try {
                while (true)
                {
                    long index = u(tags);
                    if (index==0) break;
                    String key = st.get((int)index);
                    index = u(tags);
                    String value = st.get((int)index);
                    t.put(key,value);
                }
            } catch (EOFException e) {}

            return new OSMNode(lastid,(int)di.nextVersion(),di.nextTS(),di.nextCS(),(int)di.nextUID(),st.get((int)di.nextUser()),(int)lastlon,(int)lastlat,t);
        }
    }

    class DenseInfo extends Message
    {
        DataInputStream version;
        DataInputStream ts;
        DataInputStream cs;
        DataInputStream uid;
        DataInputStream user;

        long lastversion = 0;
        long lastts = 0;
        long lastcs = 0;
        long lastuid = 0;
        long lastuser = 0;

        public DenseInfo(DataInputStream in) throws IOException
        {
            super(in);
        }

        public void readData(int nr) throws IOException
        {
            switch (nr)
            {
                case 1 -> version = new DataInputStream(new ByteArrayInputStream(readBytes(in)));
                case 2 -> ts = new DataInputStream(new ByteArrayInputStream(readBytes(in)));
                case 3 -> cs = new DataInputStream(new ByteArrayInputStream(readBytes(in)));
                case 4 -> uid = new DataInputStream(new ByteArrayInputStream(readBytes(in)));
                case 5 -> user = new DataInputStream(new ByteArrayInputStream(readBytes(in)));
                default -> throw new IOException("unknown PBF DenseInfo element: "+nr);
            }
        }

        public long nextVersion() throws IOException
        {
            return u(version);
        }

        public long nextTS() throws IOException
        {
            return lastts += s(ts);
        }

        public long nextCS() throws IOException
        {
            return lastcs += s(cs);
        }

        public long nextUID() throws IOException
        {
            return lastuid += s(uid);
        }

        public long nextUser() throws IOException
        {
            return lastuser += s(user);
        }
    }

    class Way extends Message
    {
        long id;
        Info info;
        DataInputStream keys;
        DataInputStream values;
        DataInputStream refs;

        public Way(DataInputStream in) throws IOException
        {
            super(in);
        }

        public void readData(int nr) throws IOException
        {
            switch (nr)
            {
                case 1 -> id = u(in);
                case 2 -> keys = new DataInputStream(new ByteArrayInputStream(readBytes(in)));
                case 3 -> values = new DataInputStream(new ByteArrayInputStream(readBytes(in)));
                case 4 -> info = new Info(new DataInputStream(new ByteArrayInputStream(readBytes(in))));
                case 8 -> refs = new DataInputStream(new ByteArrayInputStream(readBytes(in)));
                default -> throw new IOException("unknown PBF Way element: "+nr);
            }
        }
    }

    class Relation extends Message
    {
        long id;
        Info info;
        DataInputStream keys;
        DataInputStream values;
        DataInputStream roles;
        DataInputStream members;
        DataInputStream types;

        public Relation(DataInputStream in) throws IOException
        {
            super(in);
        }

        public void readData(int nr) throws IOException
        {
            switch (nr)
            {
                case 1 -> id = u(in);
                case 2 -> keys = new DataInputStream(new ByteArrayInputStream(readBytes(in)));
                case 3 -> values = new DataInputStream(new ByteArrayInputStream(readBytes(in)));
                case 4 -> info = new Info(new DataInputStream(new ByteArrayInputStream(readBytes(in))));
                case 8 -> roles = new DataInputStream(new ByteArrayInputStream(readBytes(in)));
                case 9 -> members = new DataInputStream(new ByteArrayInputStream(readBytes(in)));
                case 10 -> types = new DataInputStream(new ByteArrayInputStream(readBytes(in)));
                default -> throw new IOException("unknown PBF Relation element: "+nr);
            }
        }
    }

    class Info extends Message
    {
        int version;
        long ts;
        long cs;
        int uid;
        int user;

        public Info(DataInputStream in) throws IOException
        {
            super(in);
        }

        public void readData(int nr) throws IOException
        {
            switch (nr)
            {
                case 1 -> version = (int)u(in);
                case 2 -> ts = u(in);
                case 3 -> cs = u(in);
                case 4 -> uid = (int)u(in);
                case 5 -> user = (int)u(in);
                default -> throw new IOException("unknown PBF Info element: "+nr);
            }
        }
    }

    class StringTable extends Message
    {
        private List<String> table;

        public StringTable(DataInputStream in) throws IOException
        {
            super(in);
        }

        public void readData(int nr) throws IOException
        {
            if (table==null) table = new ArrayList<>();
            switch (nr)
            {
                case 1 -> table.add(readString(in));
                default -> throw new IOException("unknown PBF StringTable element: "+nr);
            }
        }

        public List<String> getTable()
        {
            return table;
        }
    }

    //////////////////////////////////////////////////////////////////

    private String readString(DataInputStream in) throws IOException
    {
        int len = (int)u(in);
        byte[] data = new byte[len];
        in.readFully(data);
        return new String(data,StandardCharsets.UTF_8);
    }

    private byte[] readBytes(DataInputStream in) throws IOException
    {
        return readBytes(in,(int)u(in));
    }

    private byte[] readBytes(DataInputStream in, int len) throws IOException
    {
        byte[] data = new byte[len];
        in.readFully(data);
        return data;
    }
}

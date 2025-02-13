package de.kumakyoo.oma;

import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.io.DataInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.EOFException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

public class O5MReader extends PackedIntegerReader
{
    private static final String[] type = {"node","way","relation"};

    private DataInputStream din;
    private DataInputStream in;

    private long id = 0;
    private long timestamp = 0;
    private long changeset = 0;
    private long lon = 0;
    private long lat = 0;
    private int version = 0;
    private int uid = 0;
    private String user = "";
    private long[] refid = new long[3];

    private String[][] table = new String[15000][2];
    private int tab_index = 0;

    private int lastsize;

    public O5MReader(Path filename) throws IOException
    {
        din = Tools.getInStream(filename);
    }

    public void close() throws IOException
    {
        din.close();
    }

    public int nextElement() throws IOException
    {
        int eid = din.readUnsignedByte();
        if (eid<0xf0)
        {
            in = din;
            byte[] data = new byte[(int)u(in)];
            in.readFully(data);
            in = new DataInputStream(new ByteArrayInputStream(data));
        }

        return eid;
    }

    public Element next() throws IOException
    {
        int eid = 0;
        while (true)
        {
            try {
                eid = nextElement();
            } catch (EOFException ex) { return null; }

            switch (eid)
            {
            case 0x10:
                return node();
            case 0x11:
                return way();
            case 0x12:
                return relation();
            case 0xdb:
                return new Bounds((int)s(in),(int)s(in),(int)s(in),(int)s(in));
            case 0xff:
                reset();
                break;
            default:
                // ignore all other elements
                break;
            }
        }
    }

    private void reset()
    {
        id = 0;
        timestamp = 0;
        changeset = 0;
        lon = 0;
        lat = 0;
        for (int i=0;i<3;i++)
            refid[i] = 0;
    }

    private OSMNode node() throws IOException
    {
        basicInfo();

        lon += s(in);
        lat += s(in);

        return new OSMNode(id,version,timestamp,changeset,uid,user,(int)lon,(int)lat,tags());
    }

    private OSMWay way() throws IOException
    {
        basicInfo();

        DataInputStream tmp = in;
        in = getChunk((int)u(in));

        List<Long> nds = new ArrayList<>();
        while (true)
        {
            try {
                refid[0] += s(in);
                nds.add(refid[0]);
            } catch (EOFException ex) { break; }
        }

        in = tmp;
        return new OSMWay(id,version,timestamp,changeset,uid,user,nds,tags());
    }

    private OSMRelation relation() throws IOException
    {
        basicInfo();

        DataInputStream tmp = in;
        in = getChunk((int)u(in));

        List<OSMMember> members = new ArrayList<>();
        while (true)
        {
            try {
                long delta = s(in);
                String[] pair = getPair(true,true);
                int t = pair[0].charAt(0)-'0';
                refid[t] += delta;

                members.add(new OSMMember(type[t],refid[t],pair[1]));
            } catch (EOFException ex) { break; }
        }

        in = tmp;
        return new OSMRelation(id,version,timestamp,changeset,uid,user,members,tags());
    }

    //////////////////////////////////////////////////////////////////

    private void basicInfo() throws IOException
    {
        id += s(in);
        version = (int)u(in);
        if (version == 0)
        {
            timestamp = 0;
            changeset = 0;
            uid = 0;
            user = "";
            return;
        }

        timestamp += s(in);
        if (timestamp == 0)
        {
            changeset = 0;
            uid = 0;
            user = "";
            return;
        }

        changeset += s(in);
        String[] s = getPair(true,false);
        uid = Integer.valueOf(s[0]);
        user = s[1];
    }

    public DataInputStream getChunk(int size) throws IOException
    {
        byte[] data = new byte[size];
        in.readFully(data);
        return new DataInputStream(new ByteArrayInputStream(data));
    }

    public Map<String, String> tags() throws IOException
    {
        Map<String, String> tags = new HashMap<>();

        while (true)
        {
            try {
                String[] tmp = getPair(false,false);
                tags.put(tmp[0],tmp[1]);
            } catch (EOFException e) { break; }
        }

        return tags;
    }

    //////////////////////////////////////////////////////////////////

    private String[] getPair(boolean integer,boolean nozero) throws IOException
    {
        // faster than call to u()...
        int index = in.readUnsignedByte();
        if (index!=0)
        {
            if (index>=0x80)
                index = index - 0x80 + (in.readUnsignedByte()<<7);
            return table[(tab_index+15000-(int)index)%15000];
        }

        String[] ret = new String[2];
        int len0 = 1;
        if (integer)
        {
            if (nozero)
                ret[0] = ""+(char)in.readUnsignedByte();
            else
            {
                long value = u(in);
                ret[0] = ""+value;
                len0 = getLength(value);
                if (value!=0)
                    in.readUnsignedByte(); // zero separating uid and name
            }
        }
        else
        {
            ret[0] = str();
            len0 = lastsize;
        }
        ret[1] = str();

        if (len0+lastsize<=250)
        {
            table[tab_index] = ret;
            tab_index = (tab_index+1)%15000;
        }
        return ret;
    }

    private String str() throws IOException
    {
        List<Byte> b = new ArrayList<>();
        while (true)
        {
            byte next = in.readByte();
            if (next==0) break;
            b.add(next);
        }

        lastsize = b.size();
        byte[] b2 = new byte[b.size()];
        for (int i=0;i<b.size();i++)
            b2[i] = b.get(i);

        return new String(b2,StandardCharsets.UTF_8);
    }

    private int getLength(long val)
    {
        if (val<=127L) return 1;
        if (val<=16383L) return 2;
        if (val<=2097151L) return 3;
        if (val<=268435455L) return 4;
        if (val<=34359738367L) return 5;
        if (val<=4398046511103L) return 6;
        if (val<=562949953421311L) return 7;
        if (val<=72057594037927935L) return 8;
        return 9;
    }
}

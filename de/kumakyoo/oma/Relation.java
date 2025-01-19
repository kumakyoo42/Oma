package de.kumakyoo.oma;

import java.util.*;
import java.io.*;

public class Relation extends ElementWithID
{
    public String[] noderole;
    public Node[] node;

    public String[] wayrole;
    public Way[] way;

    public String[] relrole;
    public long[] relid;

    public Relation(OmaInputStream in, boolean preserve_id) throws IOException
    {
        id = (preserve_id||Oma.preserve_id)?in.readLong():0;
        version = Oma.preserve_version?in.readInt():0;
        timestamp = Oma.preserve_timestamp?in.readLong():0;
        changeset = Oma.preserve_changeset?in.readLong():0;
        uid = Oma.preserve_user?in.readInt():0;
        user = Oma.preserve_user?in.readUTF():"";

        int raz = in.readInt();
        relrole = new String[raz];
        relid = new long[raz];
        for (int i=0;i<raz;i++)
        {
            relrole[i] = in.readUTF();
            relid[i] = in.readLong();
        }

        int naz = in.readInt();
        noderole = new String[naz];
        node = new Node[naz];
        for (int i=0;i<naz;i++)
        {
            noderole[i] = in.readUTF();
            node[i] = new Node();
            node[i].lon = in.readInt();
            node[i].lat = in.readInt();
            if (((long)node[i].lon)<<32+node[i].lat>=Reunify.ID_MARKER)
                node[i] = null;
        }

        int waz = in.readInt();
        wayrole = new String[waz];
        way = new Way[waz];
        for (int i=0;i<waz;i++)
        {
            wayrole[i] = in.readUTF();
            if (preserve_id && in.readByte()=='w')
                in.readLong(); // skip ID
            else
            {
                way[i] = new Way();
                int az = in.readInt();
                way[i].lon = new int[az];
                way[i].lat = new int[az];
                for (int j=0;j<az;j++)
                {
                    way[i].lon[j] = in.readInt();
                    way[i].lat[j] = in.readInt();
                }
            }
        }

        int taz = in.readInt();
        tags = new HashMap<>();

        for (int i=0;i<taz;i++)
            tags.put(in.readUTF(),in.readUTF());
    }

    public void writeDirectElements(OmaOutputStream out, OmaOutputStream aout) throws IOException
    {
        String type = tags.get("type");

        if ("multipolygon".equals(type) || "boundary".equals(type))
            writeAreas(aout);

        if ("restriction".equals(type) || "destination_sign".equals(type))
            writeFromToWays(out);
    }

    private void writeAreas(OmaOutputStream out) throws IOException
    {
        Multipolygon mp = new Multipolygon();

        for (int i=0;i<way.length;i++)
            if (way[i]!=null && ("inner".equals(wayrole[i]) || "outer".equals(wayrole[i])))
            {
                mp.add(way[i].lon,way[i].lat,"inner".equals(wayrole[i]));
                way[i] = null;
            }

        mp.createRings();
        mp.sortRings();

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

            out.writeInt(tags.size());
            for (String key:tags.keySet())
            {
                out.writeUTF(key);
                out.writeUTF(tags.get(key));
            }
        }
    }

    private void writeFromToWays(OmaOutputStream out) throws IOException
    {
        FromTo f = new FromTo();

        for (int i=0;i<way.length;i++)
        {
            if (way[i]!=null && "from".equals(wayrole[i]))
            {
                f.addFrom(way[i]);
                way[i] = null;
            }
            if (way[i]!=null && "to".equals(wayrole[i]))
            {
                f.addTo(way[i]);
                way[i] = null;
            }
            if (way[i]!=null && ("via".equals(wayrole[i]) || "intersection".equals(wayrole[i])))
            {
                f.addVia(way[i]);
                way[i] = null;
            }
        }

        for (int i=0;i<node.length;i++)
            if (node[i]!=null && ("via".equals(noderole[i]) || "intersection".equals(noderole[i])))
            {
                f.addVia(node[i]);
                node[i] = null;
            }

        f.createWays();

        for (Way w:f.ways)
        {
            out.writeByte('W');
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

            out.writeInt(w.lon.length);
            for (int i=0;i<w.lon.length;i++)
            {
                out.writeInt(w.lon[i]);
                out.writeInt(w.lat[i]);
            }

            out.writeInt(tags.size());
            for (String key:tags.keySet())
            {
                out.writeUTF(key);
                out.writeUTF(tags.get(key));
            }
        }
    }

    public boolean empty()
    {
        for (Node n:node)
            if (n!=null)
                return false;

        for (Way w:way)
            if (w!=null)
                return false;

        return relid.length==0;
    }

    public void write(OmaOutputStream out, boolean fin) throws IOException
    {
        if (fin) out.writeByte('C');

        if (!fin || Oma.preserve_id)
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

        if (!fin)
        {
            out.writeInt(relid.length);
            for (int i=0;i<relid.length;i++)
            {
                out.writeUTF(relrole[i]);
                out.writeLong(relid[i]);
            }
        }

        int naz = 0;
        for (Node n:node)
            if (n!=null)
                naz++;

        out.writeInt(naz);
        for (int i=0;i<node.length;i++)
            if (node[i]!=null)
            {
                out.writeUTF(noderole[i]);
                out.writeInt(node[i].lon);
                out.writeInt(node[i].lat);
            }

        int waz = 0;
        for (Way w:way)
            if (w!=null)
                waz++;

        out.writeInt(waz);
        for (int i=0;i<way.length;i++)
            if (way[i]!=null)
            {
                out.writeUTF(wayrole[i]);
                if (!fin)
                    out.writeByte('W');
                out.writeInt(way[i].lon.length);
                for (int j=0;j<way[i].lon.length;j++)
                {
                    out.writeInt(way[i].lon[j]);
                    out.writeInt(way[i].lat[j]);
                }
            }

        if (fin)
            out.writeInt(0); // aaz

        out.writeInt(tags.size());
        for (String key:tags.keySet())
        {
            out.writeUTF(key);
            out.writeUTF(tags.get(key));
        }
    }
}

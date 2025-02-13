package de.kumakyoo.oma;

import java.util.*;
import java.io.*;

public class Relation extends ElementWithID
{
    public List<OSMMember> members;
    public List<Map<String, String>> ctags;

    public Relation(OmaInputStream in, boolean preserve_id) throws IOException
    {
        id = (preserve_id||Oma.preserve_id)?in.readLong():0;
        version = Oma.preserve_version?in.readSmallInt():0;
        timestamp = Oma.preserve_timestamp?in.readLong():0;
        changeset = Oma.preserve_changeset?in.readLong():0;
        uid = Oma.preserve_user?in.readInt():0;
        user = Oma.preserve_user?in.readString():"";

        int maz = in.readSmallInt();
        members = new ArrayList<>();
        for (int i=0;i<maz;i++)
        {
            String role = in.readString();
            byte type = in.readByte();
            long ref = in.readLong();
            members.add(new OSMMember((char)type,ref,role));
        }

        int taz = in.readSmallInt();
        tags = new HashMap<>();
        for (int i=0;i<taz;i++)
            tags.put(in.readString(),in.readString());

        int caz = in.readSmallInt();
        ctags = new ArrayList<>();
        for (int j=0;j<caz;j++)
        {
            Map<String, String> ctag = new HashMap<>();
            int ctaz = in.readSmallInt();
            for (int i=0;i<ctaz;i++)
                ctag.put(in.readString(),in.readString());
            ctags.add(ctag);
        }
    }

    /*
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
     */

    public void write(OmaOutputStream out, boolean preserve_id) throws IOException
    {
        if (preserve_id || Oma.preserve_id)
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

        out.writeSmallInt(members.size());
        for (OSMMember m:members)
        {
            out.writeString(m.role);
            out.writeByte(m.type.charAt(0));
            out.writeLong(m.ref);
        }

        out.writeSmallInt(tags.size());
        for (String key:tags.keySet())
        {
            out.writeString(key);
            out.writeString(tags.get(key));
        }

        out.writeSmallInt(ctags.size());
        for (Map<String, String> ctag:ctags)
        {
            out.writeSmallInt(ctag.size());
            for (String key:ctag.keySet())
            {
                out.writeString(key);
                out.writeString(ctag.get(key));
            }
        }
    }
}

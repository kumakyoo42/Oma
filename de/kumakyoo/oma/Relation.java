package de.kumakyoo.oma;

import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.io.IOException;

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

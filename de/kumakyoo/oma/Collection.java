package de.kumakyoo.oma;

import java.io.*;

public class Collection extends ElementWithID
{
    public Collection(OmaInputStream in, int features) throws IOException
    {
        in.readSmallInt();
        readTags(in);
        readMembers(in);
        readMetaData(in,features|2);
    }

    public void writeGeo(OmaOutputStream out) throws IOException
    {
        out.writeSmallInt(0);
    }

    public void writeMetaData(OmaOutputStream out, int features) throws IOException
    {
        super.writeMetaData(out,features|2);
    }
}

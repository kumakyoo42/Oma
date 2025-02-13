package de.kumakyoo.oma;

import java.io.*;

public class Collection extends ElementWithID
{
    Bounds b;

    public Collection(OmaInputStream in, int features) throws IOException
    {
        b = new Bounds(in);

        readTags(in);
        readMembers(in);
        readMetaData(in,features|2);
    }

    public void writeGeo(OmaOutputStream out) throws IOException
    {
        b.write(out);
    }

    public void writeMetaData(OmaOutputStream out, int features) throws IOException
    {
        super.writeMetaData(out,features|2);
    }
}

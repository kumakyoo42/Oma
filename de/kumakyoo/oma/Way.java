package de.kumakyoo.oma;

import java.io.IOException;

public class Way extends ElementWithID
{
    int[] lon;
    int[] lat;

    public Way()
    {
    }

    public Way(OmaInputStream in, int features) throws IOException
    {
        int az = in.readSmallInt();
        lon = new int[az];
        lat = new int[az];
        for (int k=0;k<az;k++)
        {
            lon[k] = in.readDeltaX();
            lat[k] = in.readDeltaY();
        }
        readTags(in);
        readMembers(in);
        readMetaData(in,features);
    }

    public static Way readGeo(OmaInputStream in) throws IOException
    {
        Way w = new Way();
        int az = in.readSmallInt();
        w.lon = new int[az];
        w.lat = new int[az];
        for (int k=0;k<az;k++)
        {
            w.lon[k] = in.readDeltaX();
            w.lat[k] = in.readDeltaY();
        }
        return w;
    }

    public void writeGeo(OmaOutputStream out) throws IOException
    {
        out.writeSmallInt(lon.length);
        for (int k=0;k<lon.length;k++)
        {
            out.writeDeltaX(lon[k]);
            out.writeDeltaY(lat[k]);
        }
    }

    public boolean isClosed()
    {
        return lon.length>=3 && lon[0]==lon[lon.length-1] && lat[0]==lat[lat.length-1];
    }
}

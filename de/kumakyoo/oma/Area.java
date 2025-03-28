package de.kumakyoo.oma;

import java.io.IOException;

public class Area extends Way
{
    int[][] h_lon;
    int[][] h_lat;

    public Area()
    {
    }

    public Area(OmaInputStream in, int features) throws IOException
    {
        int az = in.readSmallInt();
        lon = new int[az];
        lat = new int[az];
        for (int k=0;k<az;k++)
        {
            lon[k] = in.readDeltaX();
            lat[k] = in.readDeltaY();
        }
        az = in.readSmallInt();
        h_lon = new int[az][];
        h_lat = new int[az][];
        for (int k=0;k<az;k++)
        {
            int az2 = in.readSmallInt();
            h_lon[k] = new int[az2];
            h_lat[k] = new int[az2];
            for (int i=0;i<az2;i++)
            {
                h_lon[k][i] = in.readDeltaX();
                h_lat[k][i] = in.readDeltaY();
            }
        }
        readTags(in);
        readMembers(in);
        readMetaData(in,features);
    }

    public Area(Way w) throws IOException
    {
        this.id = w.id;
        this.version = w.version;
        this.timestamp = w.timestamp;
        this.changeset = w.changeset;
        this.uid = w.uid;
        this.user = w.user;
        this.tags = w.tags;
        this.members = w.members;
        this.lon = new int[w.lon.length-1];
        this.lat = new int[w.lat.length-1];
        System.arraycopy(w.lon,0,this.lon,0,w.lon.length-1);
        System.arraycopy(w.lat,0,this.lat,0,w.lat.length-1);
        h_lon = new int[0][];
        h_lat = new int[0][];
    }

    public static Area readGeo(OmaInputStream in) throws IOException
    {
        Area a = new Area();

        int az = in.readSmallInt();
        a.lon = new int[az];
        a.lat = new int[az];
        for (int k=0;k<az;k++)
        {
            a.lon[k] = in.readDeltaX();
            a.lat[k] = in.readDeltaY();
        }
        az = in.readSmallInt();
        a.h_lon = new int[az][];
        a.h_lat = new int[az][];
        for (int k=0;k<az;k++)
        {
            int az2 = in.readSmallInt();
            a.h_lon[k] = new int[az2];
            a.h_lat[k] = new int[az2];
            for (int i=0;i<az2;i++)
            {
                a.h_lon[k][i] = in.readDeltaX();
                a.h_lat[k][i] = in.readDeltaY();
            }
        }
        return a;
    }

    public void writeGeo(OmaOutputStream out) throws IOException
    {
        sortRings();

        out.writeSmallInt(lon.length);
        for (int k=0;k<lon.length;k++)
        {
            out.writeDeltaX(lon[k]);
            out.writeDeltaY(lat[k]);
        }
        out.writeSmallInt(h_lon.length);
        for (int k=0;k<h_lon.length;k++)
        {
            out.writeSmallInt(h_lon[k].length);
            for (int i=0;i<h_lon[k].length;i++)
            {
                out.writeDeltaX(h_lon[k][i]);
                out.writeDeltaY(h_lat[k][i]);
            }
        }
    }

    private void sortRings()
    {
        if (!isClockWise(lon,lat))
        {
            reverse(lon);
            reverse(lat);
        }

        for (int i=0;i<h_lon.length;i++)
            if (isClockWise(h_lon[i],h_lat[i]))
            {
                reverse(h_lon[i]);
                reverse(h_lat[i]);
            }
    }

    private boolean isClockWise(int[] lon, int[] lat)
    {
        long sum = 0;
        for (int i=0;i<lon.length;i++)
            sum += (lon[(i+1)%lon.length]-lon[i])*(lat[(i+1)%lon.length]+lat[i]);

        return sum>=0;
    }

    private void reverse(int[] h)
    {
        for (int i=0;i<h.length/2;i++)
        {
            int tmp = h[i];
            h[i] = h[h.length-i-1];
            h[h.length-i-1] = tmp;
        }
    }
}

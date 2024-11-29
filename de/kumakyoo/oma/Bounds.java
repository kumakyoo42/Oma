package de.kumakyoo.oma;

import java.io.*;

public class Bounds extends Element
{
    private int minlon;
    private int minlat;
    private int maxlon;
    private int maxlat;

    public Bounds(int minlon, int minlat, int maxlon, int maxlat)
    {
        this.minlon = minlon;
        this.minlat = minlat;
        this.maxlon = maxlon;
        this.maxlat = maxlat;
    }

    public Bounds(DataInputStream in) throws IOException
    {
        this(in.readInt(),in.readInt(),in.readInt(),in.readInt());
    }

    public void write(DataOutputStream out) throws IOException
    {
        out.writeInt(minlon);
        out.writeInt(minlat);
        out.writeInt(maxlon);
        out.writeInt(maxlat);
    }

    public static Bounds getWholeWorld()
    {
        return new Bounds(-1800000000,-900000000,1800000000,900000000);
    }

    public static Bounds getNoBounds()
    {
        return new Bounds(Integer.MAX_VALUE,Integer.MAX_VALUE,Integer.MAX_VALUE,Integer.MAX_VALUE);
    }

    public boolean contains(int lon, int lat)
    {
        return lon>=minlon && lon<=maxlon && lat>=minlat && lat<=maxlat;
    }

    public boolean contains(int[] lon, int[] lat)
    {
        for (int i=0;i<lon.length;i++)
            if (!contains(lon[i],lat[i])) return false;
        return true;
    }

    public boolean contains(int[] lon, int[] lat, int[][] hlon, int[][] hlat)
    {
        for (int i=0;i<lon.length;i++)
            if (!contains(lon[i],lat[i])) return false;

        for (int i=0;i<hlon.length;i++)
            for (int j=0;j<hlon[i].length;j++)
                if (!contains(hlon[i][j],hlat[i][j])) return false;

        return true;
    }

    public boolean intersects(Bounds b)
    {
        return b.maxlon>=minlon && b.minlon<=maxlon && b.maxlat>=minlat && b.minlat<=maxlat;
    }

    public String toString()
    {
        return (minlon/1e7)+","+(minlat/1e7)+","+(maxlon/1e7)+","+(maxlat/1e7);
    }
}

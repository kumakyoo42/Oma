package de.kumakyoo.oma;

public class Point
{
    int lon;
    int lat;

    public Point(int lon, int lat)
    {
        this.lon = lon;
        this.lat = lat;
    }

    public boolean equals(Point p)
    {
        return p.lon==lon && p.lat==lat;
    }

    public boolean inside(Point[] p)
    {
        boolean inside = false;

        for (int i=0;i<p.length-1;i++)
        {
            long x1 = p[i].lon;
            long y1 = p[i].lat;
            long x2 = p[i+1].lon;
            long y2 = p[i+1].lat;
            if (y1==y2) continue;
            if ((y1<=lat) != (lat<y2)) continue;
            if (x1 + (x2-x1)*(lat-y1)/(y2-y1)<lon)
                inside = !inside;
        }

        return inside;
    }
}

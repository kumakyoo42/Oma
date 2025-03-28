package de.kumakyoo.oma;

import java.util.List;
import java.util.ArrayList;
import java.util.Collections;

public class Multipolygon
{
    List<Point[]> inner;
    List<Point[]> outer;

    List<Point[]> inner_rings;
    List<Point[]> outer_rings;

    List<Area> areas;

    private boolean[] used;
    private int[] result;
    private Point[] start;
    private Point[] end;

    public Multipolygon()
    {
        inner = new ArrayList<>();
        outer = new ArrayList<>();
    }

    public void add(int[] lon, int[] lat, boolean inner)
    {
        Point[] p = new Point[lon.length];
        for (int i=0;i<lon.length;i++)
            p[i] = new Point(lon[i],lat[i]);

        if (inner)
            this.inner.add(p);
        else
            this.outer.add(p);
    }

    public void createRings()
    {
        outer_rings = createRings(outer);
        inner_rings = createRings(inner);
    }

    private List<Point[]> createRings(List<Point[]> l)
    {
        used = new boolean[l.size()];
        result = new int[l.size()];

        start = new Point[l.size()];
        end = new Point[l.size()];

        for (int i=0;i<l.size();i++)
        {
            Point[] tmp = l.get(i);
            if (tmp==null || tmp.length<1) return null;
            start[i] = tmp[0];
            end[i] = tmp[tmp.length-1];
        }

        if (!createRings(0,null,null)) return null;

        List<Point[]> ret = new ArrayList<>();

        List<Point> next = null;
        for (int i=0;i<l.size();i++)
        {
            if (next==null)
                next = new ArrayList<>();
            Point[] tmp = l.get(Math.abs(result[i]));
            for (int j=0;j<tmp.length;j++)
                next.add(result[i]>=0?tmp[j]:tmp[tmp.length-1-j]);
            if (next.get(0).equals(next.get(next.size()-1)))
            {
                Point[] p = new Point[next.size()];
                next.toArray(p);
                ret.add(p);
                next = null;
            }
        }

        return ret;
    }

    private boolean createRings(int nr, Point s, Point e)
    {
        if (s==null)
        {
            for (int i=0;i<used.length;i++)
            {
                if (used[i]) continue;

                used[i] = true;
                result[nr] = i;
                return createRings(nr+1,start[i],end[i]);
            }
            return true;
        }

        if (s.equals(e)) return createRings(nr,null,null);

        for (int i=0;i<used.length;i++)
        {
            if (used[i]) continue;

            if (start[i].equals(e))
            {
                used[i] = true;
                result[nr] = i;
                if (createRings(nr+1,s,end[i])) return true;
                used[i] = false;
            }

            if (end[i].equals(e))
            {
                used[i] = true;
                result[nr] = -i;
                if (createRings(nr+1,s,start[i])) return true;
                used[i] = false;
            }
        }

        return false;
    }

    public void sortRings()
    {
        areas = new ArrayList<>();

        if (outer_rings==null) return;

        Collections.sort(outer_rings,(a,b) -> inside(a,b)?-1:inside(b,a)?1:0);

        if (inner_rings==null) inner_rings = new ArrayList<>();
        used = new boolean[inner_rings.size()];

        for (Point[] ring:outer_rings)
        {
            Area a = new Area();
            a.lon = new int[ring.length];
            a.lat = new int[ring.length];
            for (int i=0;i<ring.length;i++)
            {
                a.lon[i] = ring[i].lon;
                a.lat[i] = ring[i].lat;
            }

            List<Point[]> inner = new ArrayList<>();

            for (int i=0;i<inner_rings.size();i++)
            {
                if (used[i]) continue;

                if (inside(inner_rings.get(i),ring))
                {
                    inner.add(inner_rings.get(i));
                    used[i] = true;
                }
            }

            a.h_lon = new int[inner.size()][];
            a.h_lat = new int[inner.size()][];
            for (int i=0;i<inner.size();i++)
            {
                Point[] p = inner.get(i);

                a.h_lon[i] = new int[p.length];
                a.h_lat[i] = new int[p.length];
                for (int j=0;j<p.length;j++)
                {
                    a.h_lon[i][j] = p[j].lon;
                    a.h_lat[i][j] = p[j].lat;
                }
            }

            areas.add(a);
        }
    }

    // If one point is inside, we assume, that everything is inside, to
    // avoid problems with numerical calculations
    private boolean inside(Point[] a, Point[] b)
    {
        for (Point p:a)
            if (p.inside(b)) return true;
        return false;
    }
}

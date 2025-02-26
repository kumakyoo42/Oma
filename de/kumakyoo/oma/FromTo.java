package de.kumakyoo.oma;

import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;

public class FromTo
{
    List<Way> ways;

    List<Point[]> from;
    List<Point[]> via;
    List<Point[]> to;

    public FromTo()
    {
        from = new ArrayList<>();
        via = new ArrayList<>();
        to = new ArrayList<>();
    }

    public void addFrom(Way w)
    {
        Point[] p = new Point[w.lon.length];
        for (int i=0;i<w.lon.length;i++)
            p[i] = new Point(w.lon[i],w.lat[i]);

        from.add(p);
    }

    public void addVia(Way w)
    {
        Point[] p = new Point[w.lon.length];
        for (int i=0;i<w.lon.length;i++)
            p[i] = new Point(w.lon[i],w.lat[i]);

        via.add(p);
    }

    public void addVia(Node n)
    {
        Point[] p = new Point[1];
        p[0] = new Point(n.lon,n.lat);

        via.add(p);
    }

    public void addTo(Way w)
    {
        Point[] p = new Point[w.lon.length];
        for (int i=0;i<w.lon.length;i++)
            p[i] = new Point(w.lon[i],w.lat[i]);

        to.add(p);
    }

    public void createWays()
    {
        ways = new ArrayList<>();

        if (to.isEmpty()) return;

        if (via.isEmpty())
        {
            Point k1 = to.get(0)[0];
            Point k2 = to.get(0)[to.get(0).length-1];

            boolean first = true;
            boolean last = true;
            for (int i=1;i<to.size();i++)
            {
                if (first && !k1.equals(to.get(i)[0]) && !k1.equals(to.get(i)[to.get(i).length-1]))
                    first = false;
                if (last && !k2.equals(to.get(i)[0]) && !k2.equals(to.get(i)[to.get(i).length-1]))
                    last = false;
            }

            for (int i=0;i<from.size();i++)
            {
                if (first && !k1.equals(from.get(i)[0]) && !k1.equals(from.get(i)[from.get(i).length-1]))
                    first = false;
                if (last && !k2.equals(from.get(i)[0]) && !k2.equals(from.get(i)[from.get(i).length-1]))
                    last = false;
            }

            if (first)
            {
                Point[] p = new Point[1];
                p[0] = k1;
                via.add(p);
            }
            else if (last)
            {
                Point[] p = new Point[1];
                p[0] = k2;
                via.add(p);
            }
            else
                return;
        }

        List<Point> viasorted = sortVia();
        if (viasorted==null) return;

        for (Point[] t:to)
        {
            List<Point> way = new ArrayList<>(viasorted);

            if (way.get(0).equals(t[0]) || way.get(0).equals(t[t.length-1]))
                Collections.reverse(way);
            if (way.get(way.size()-1).equals(t[t.length-1]))
                Collections.reverse(Arrays.asList(t));

            if (!way.get(way.size()-1).equals(t[0])) continue;
            way.add(t[1]);

            if (from.isEmpty())
            {
                Way w = new Way();
                w.lon = new int[way.size()+1];
                w.lat = new int[way.size()+1];
                w.lon[0] = way.get(0).lon;
                w.lat[0] = way.get(0).lat;
                for (int i=0;i<way.size();i++)
                {
                    w.lon[i+1] = way.get(i).lon;
                    w.lat[i+1] = way.get(i).lat;
                }
                ways.add(w);
            }
            else
            {
                for (Point[] f:from)
                {
                    if (way.get(way.size()-1).equals(f[0]) || way.get(way.size()-1).equals(f[f.length-1]))
                        Collections.reverse(way);
                    if (way.get(0).equals(f[0]))
                        Collections.reverse(Arrays.asList(f));

                    if (!way.get(0).equals(f[f.length-1])) continue;

                    Way w = new Way();
                    w.lon = new int[way.size()+1];
                    w.lat = new int[way.size()+1];
                    w.lon[0] = f[f.length-2].lon;
                    w.lat[0] = f[f.length-2].lat;
                    for (int i=0;i<way.size();i++)
                    {
                        w.lon[i+1] = way.get(i).lon;
                        w.lat[i+1] = way.get(i).lat;
                    }
                    ways.add(w);
                }
            }
        }
    }

    private List<Point> sortVia()
    {
        List<Point> w = new ArrayList<>();

        boolean[] used = new boolean[via.size()];

        Point[] h = via.get(0);
        for (Point p:h)
            w.add(p);
        used[0] = true;

        if (via.size()==1) return w;

        Point last = w.get(w.size()-1);

        boolean reversed = false;
        while (true)
        {
            boolean changed = false;
            for (int k=0;k<via.size();k++)
                if (!used[k])
                {
                    if (last.equals(via.get(k)[0]))
                    {
                        for (int i=1;i<via.get(k).length;i++)
                            w.add(via.get(k)[i]);

                        last = w.get(w.size()-1);

                        used[k] = true;
                        changed = true;
                    }
                    else if (last.equals(via.get(k)[via.get(k).length-1]))
                    {
                        for (int i=via.get(k).length-2;i>=0;i--)
                            w.add(via.get(k)[i]);

                        last = w.get(w.size()-1);

                        used[k] = true;
                        changed = true;
                    }
                }

            if (changed) continue;
            if (reversed) break;

            Collections.reverse(w);
            last = w.get(w.size()-1);
            reversed = true;
        }

        for (int k=0;k<via.size();k++)
            if (!used[k]) return null;

        return w;
    }
}

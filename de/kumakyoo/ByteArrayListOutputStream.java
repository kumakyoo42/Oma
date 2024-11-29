package de.kumakyoo;

import java.io.*;
import java.util.*;

public class ByteArrayListOutputStream extends OutputStream
{
    static final int MAX_ARRAY = 1000000;

    private List<byte[]> list;

    private long pos;
    private long max;

    public ByteArrayListOutputStream()
    {
        list = new ArrayList<>();
        pos = max = 0;
    }

    public ByteArrayListInputStream getBalis()
    {
        return new ByteArrayListInputStream(list,max);
    }

    public void write(int val)
    {
        while (pos/MAX_ARRAY>=list.size())
            list.add(new byte[MAX_ARRAY]);
        list.get((int)(pos/MAX_ARRAY))[(int)(pos%MAX_ARRAY)] = (byte)(val<128?val:val-256);
        pos++;
        if (pos>max) max = pos;
    }

    public void writeTo(OutputStream s) throws IOException
    {
        pos = 0;
        while (MAX_ARRAY*(pos+1)<max)
        {
            s.write(list.get((int)pos));
            pos++;
        }
        if (max>MAX_ARRAY*pos)
            s.write(list.get((int)pos),0,(int)(max-MAX_ARRAY*pos));
    }

    public long getPosition()
    {
        return pos;
    }

    public void setPosition(long p)
    {
        pos = p;
        if (pos>max) max = pos;
    }

    public long getSize()
    {
        return max;
    }
}

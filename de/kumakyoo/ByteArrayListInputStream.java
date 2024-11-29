package de.kumakyoo;

import java.io.*;
import java.util.*;

public class ByteArrayListInputStream extends InputStream
{
    static final int MAX_ARRAY = ByteArrayListOutputStream.MAX_ARRAY;

    List<byte[]> list;
    long max;
    long pos;

    public ByteArrayListInputStream(List<byte[]> list, long max)
    {
        this.list = list;
        this.max = max;
        pos = 0;
    }

    public int read()
    {
        if (pos>=max) return -1;

        int erg = list.get((int)(pos/MAX_ARRAY))[(int)(pos%MAX_ARRAY)];
        if (erg<0) erg += 256;
        pos++;
        return erg;
    }

    public long getPosition()
    {
        return pos;
    }

    public void setPosition(long p)
    {
        pos = p;
    }
}

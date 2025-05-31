package de.kumakyoo.oma;

import java.io.InputStream;
import java.util.List;

public class ByteArrayListInputStream extends InputStream
{
    static final int MAX_ARRAY = ByteArrayListOutputStream.MAX_ARRAY;

    List<byte[]> list;
    long max;
    long pos;
    int id;

    public ByteArrayListInputStream(List<byte[]> list, long max, int id)
    {
        this.list = list;
        this.max = max;
        this.id = id;
        pos = 0;
    }

    public void release()
    {
        ByteArrayListOutputStream.freeArrays(id);
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

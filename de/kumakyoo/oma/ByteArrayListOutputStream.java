package de.kumakyoo.oma;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

public class ByteArrayListOutputStream extends OutputStream
{
    static final int MAX_ARRAY = 1000000;

    static byte[][] store = null;
    static int[] store_id;
    static int nextid = 1;

    static void malloc(int n)
    {
        if (store!=null) mfree();
        store = new byte[n][MAX_ARRAY];
        store_id = new int[n];
    }

    static void mfree()
    {
        store = null;
        store_id = null;
    }

    static byte[] getArray(int id)
    {
        if (store_id==null) return null;
        for (int i=0;i<store_id.length;i++)
            if (store_id[i]==0)
            {
                store_id[i] = id;
                return store[i];
            }
        return null;
    }

    static void freeArrays(int id)
    {
        for (int i=0;i<store_id.length;i++)
            if (store_id[i]==id)
                store_id[i] = 0;
    }

    static int getFreeStores()
    {
        int count = 0;
        for (int i=0;i<store_id.length;i++)
            if (store_id[i]==0)
                count++;
        return count;
    }

    private List<byte[]> list;

    private long pos;
    private long max;
    private int id;

    public ByteArrayListOutputStream()
    {
        list = new ArrayList<>();
        pos = max = 0;
        id = nextid;
        nextid++;
    }

    public ByteArrayListInputStream getBalis()
    {
        return new ByteArrayListInputStream(list,max,id);
    }

    public void release()
    {
        freeArrays(id);
    }

    public void write(int val)
    {
        while (pos/MAX_ARRAY>=list.size())
            list.add(getArray(id));
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

    public long getAvailableMemory()
    {
        return ((long)getFreeStores())*MAX_ARRAY+(MAX_ARRAY-pos%MAX_ARRAY)%MAX_ARRAY;
    }
}

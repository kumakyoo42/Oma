package de.kumakyoo.oma;

import java.io.IOException;
import java.io.DataInputStream;

abstract public class PackedIntegerReader extends OSMReader
{
    protected long u(DataInputStream in) throws IOException
    {
        long val = 0;
        long fak = 1;

        while (true)
        {
            int next = in.readUnsignedByte();

            val += (next&0x7f)*fak;
            if (next<0x80) break;
            fak *= 0x80;
        }
        return val;
    }

    protected long s(DataInputStream in) throws IOException
    {
        long val = u(in);
        boolean sign = (val&0x01)==0x01;

        val >>= 1;
        return sign?(-val-1):val;
    }
}

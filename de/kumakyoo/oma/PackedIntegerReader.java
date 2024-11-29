package de.kumakyoo.oma;

import java.io.*;

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
        long val = 0;
        long fak = 1;
        boolean first = true;
        boolean sign = false;

        while (true)
        {
            int next = in.readUnsignedByte();

            if (first)
            {
                sign = (next&0x01)==0x01;
                next = (next&0x80) + ((next&0x7f)>>1);
            }

            val += (next&0x7f)*fak;
            if (next<0x80) break;

            fak *= first?0x40:0x80;

            first = false;
        }
        return sign?(-val-1):val;
    }
}

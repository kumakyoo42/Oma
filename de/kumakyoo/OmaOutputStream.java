package de.kumakyoo;

import java.io.*;
import java.nio.file.*;
import java.nio.channels.*;
import java.util.*;

public class OmaOutputStream extends DataOutputStream
{
    PositionOutputStream out;

    private int lastx;
    private int lasty;

    // Due to java restrictions it is not possible to make this
    // a normal constructor. We use static init methods instead.
    public static OmaOutputStream init(Path filename) throws IOException
    {
        return init(filename,false);
    }

    public static OmaOutputStream init(Path filename, boolean toDisk) throws IOException
    {
        PositionOutputStream out = new PositionOutputStream(filename,toDisk);
        OmaOutputStream s = new OmaOutputStream(out);

        s.out = out;
        return s;
    }

    public OmaOutputStream(OutputStream s)
    {
        super(s);
        resetDelta();
    }

    public void release() throws IOException
    {
        out.release();
    }

    //////////////////////////////////////////////////////////////////

    public void writeSmallInt(int value) throws IOException
    {
        if (value<255)
            writeByte(value);
        else
        {
            writeByte(255);
            if (value<65535)
                writeShort(value);
            else
            {
                writeShort(65535);
                writeInt(value);
            }
        }
    }

    public void writeString(String s) throws IOException
    {
        byte[] bytes = s.getBytes("UTF-8");
        writeSmallInt(bytes.length);
        write(bytes,0,bytes.length);
    }

    public void writeDeltaX(int val) throws IOException
    {
        lastx = delta(lastx,val);
    }

    public void writeDeltaY(int val) throws IOException
    {
        lasty = delta(lasty,val);
    }

    public void resetDelta()
    {
        lastx = lasty = 0;
    }

    public int delta(int last, int val) throws IOException
    {
        int delta = val-last;
        if (delta>=-32767 && delta<=32767)
            writeShort(delta);
        else
        {
            writeShort(-32768);
            writeInt(val);
        }

        return val;
    }

    //////////////////////////////////////////////////////////////////

    public long getPosition() throws IOException
    {
        return out.getPosition();
    }

    public void setPosition(long pos) throws IOException
    {
        out.setPosition(pos);
    }

    public void copyFrom(OmaInputStream in, long size) throws IOException
    {
        out.copyFrom(in,size);
    }

    //////////////////////////////////////////////////////////////////

    public long fileSize() throws IOException
    {
        return out.fileSize();
    }

    public void move(Path neu) throws IOException
    {
        out.move(neu);
    }
}

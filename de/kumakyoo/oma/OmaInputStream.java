package de.kumakyoo.oma;

import java.io.IOException;
import java.io.InputStream;
import java.io.DataInputStream;
import java.nio.file.Path;

public class OmaInputStream extends DataInputStream
{
    PositionInputStream in;

    private int lastx;
    private int lasty;

    // Due to java restrictions it is not possible to make this
    // a normal constructor. We use static init methods instead.
    public static OmaInputStream init(OmaOutputStream out) throws IOException
    {
        PositionInputStream in = new PositionInputStream(out.getStream());
        OmaInputStream s = new OmaInputStream(in);

        s.in = in;
        return s;
    }

    public static OmaInputStream init(Path filename) throws IOException
    {
        PositionInputStream in = new PositionInputStream(filename);
        OmaInputStream s = new OmaInputStream(in);

        s.in = in;
        return s;
    }

    public OmaInputStream(InputStream in) throws IOException
    {
        super(in);
        resetDelta();
    }

    public void release() throws IOException
    {
        in.release();
    }

    public void close() throws IOException
    {
        in.close();
    }

    //////////////////////////////////////////////////////////////////

    public int readSmallInt() throws IOException
    {
        int val = readUnsignedByte();
        if (val<255) return val;
        val = readUnsignedShort();
        if (val<65535) return val;
        return readInt();
    }

    public String readString() throws IOException
    {
        int len = readSmallInt();
        byte[] b = new byte[len];
        readFully(b,0,len);
        return new String(b,"UTF-8");
    }

    public void resetDelta()
    {
        lastx = lasty = 0;
    }

    public int readDeltaX() throws IOException
    {
        lastx = delta(lastx);
        return lastx;
    }

    public int readDeltaY() throws IOException
    {
        lasty = delta(lasty);
        return lasty;
    }

    private int delta(int last) throws IOException
    {
        int delta = readShort();
        return delta==-32768?readInt():(last+delta);
    }

    //////////////////////////////////////////////////////////////////

    public long getPosition() throws IOException
    {
        return in.getPosition();
    }

    public void setPosition(long pos) throws IOException
    {
        in.setPosition(pos);
    }
}

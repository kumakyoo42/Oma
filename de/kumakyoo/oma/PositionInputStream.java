package de.kumakyoo.oma;

import java.io.*;
import java.nio.file.*;
import java.nio.channels.*;
import java.util.*;

public class PositionInputStream extends InputStream
{
    private Path filename;
    private boolean fromDisk;

    private FileInputStream fis;
    private FileChannel fc;

    private InputStream in;
    private ByteArrayListInputStream balis;

    public PositionInputStream(PositionOutputStream out) throws IOException
    {
        this.filename = out.filename;
        if (out.toDisk)
            init(filename);
        else
        {
            in = balis = out.getBalis();
            fromDisk = false;
        }
    }

    public PositionInputStream(Path filename) throws IOException
    {
        this.filename = filename;
        init(filename);
    }

    private void init(Path filename) throws IOException
    {
        fis = new FileInputStream(filename.toString());
        fc = fis.getChannel();
        in = new BufferedInputStream(fis);
        fromDisk = true;
    }

    public void release() throws IOException
    {
        if (filename==null) return;

        if (Oma.verbose>=4)
            System.out.println("        Releasing '"+filename+"'.");

        in.close();
        balis = null;
        if (fromDisk)
            Files.delete(filename);
        filename = null;
        Tools.gc();
    }

    public void close() throws IOException
    {
        in.close();
    }

    public int read() throws IOException
    {
        return in.read();
    }

    public int read(byte[] b, int off, int len) throws IOException
    {
        return in.read(b,off,len);
    }

    public long getPosition() throws IOException
    {
        if (fromDisk)
            return fc.position();
        return balis.getPosition();
    }

    public void setPosition(long pos) throws IOException
    {
        if (fromDisk)
        {
            fc.position(pos);
            in = new BufferedInputStream(fis);
        }
        else
            balis.setPosition(pos);
    }
}

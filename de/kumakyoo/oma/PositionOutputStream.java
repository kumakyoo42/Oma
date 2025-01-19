package de.kumakyoo.oma;

import java.io.*;
import java.util.*;
import java.nio.file.*;
import java.nio.channels.*;

public class PositionOutputStream extends OutputStream
{
    private static final int COPY_BUFFER_SIZE = 1_000_000;

    Path filename;
    boolean toDisk;

    private FileOutputStream fos;
    private FileChannel fc;
    private BufferedOutputStream bos;
    private ByteArrayListOutputStream balos;

    static List<PositionOutputStream> pos = new ArrayList<>();

    public PositionOutputStream(Path filename, boolean toDisk) throws IOException
    {
        this.filename = filename;
        this.toDisk = toDisk;

        if (Oma.verbose>=4)
            System.err.println("        Allocating '"+filename+"' ("+(toDisk?"on disk":"in memory")+").");

        if (toDisk)
        {
            fos = new FileOutputStream(filename.toString());
            fc = fos.getChannel();
            bos = new BufferedOutputStream(fos);
        }
        else
            balos = new ByteArrayListOutputStream();

        pos.add(this);
    }

    public ByteArrayListInputStream getBalis()
    {
        return balos.getBalis();
    }

    public void release() throws IOException
    {
        if (Oma.verbose>=4)
            System.out.println("        Releasing '"+filename+"'.");

        close();
        balos = null;
        if (toDisk)
            Files.delete(filename);
        filename = null;
        if (Tools.memavail()<5*Oma.memlimit)
            Tools.gc();
    }

    public void write(int b) throws IOException
    {
        if (!toDisk && Tools.memavail()<Oma.memlimit)
            freeSomeMemory();

        if (toDisk)
            bos.write(b);
        else
            balos.write(b);
    }

    public void write(byte[] b, int off, int len) throws IOException
    {
        if (!toDisk && Tools.memavail()<Oma.memlimit)
            freeSomeMemory();

        if (toDisk)
            bos.write(b,off,len);
        else
            balos.write(b,off,len);
    }

    public void close() throws IOException
    {
        if (toDisk)
            bos.close();
        pos.remove(this);
    }

    public long getPosition() throws IOException
    {
        if (toDisk)
        {
            bos.flush();
            return fc.position();
        }
        else
            return balos.getPosition();
    }

    public void setPosition(long pos) throws IOException
    {
        if (toDisk)
        {
            bos.flush();
            fc.position(pos);
        }
        else
            balos.setPosition(pos);
    }

    public void copyFrom(OmaInputStream in, long size) throws IOException
    {
        if (Oma.verbose>=4)
            System.out.println("        Copying "+size+" bytes.");

        byte[] tmp = new byte[COPY_BUFFER_SIZE];
        while (size>=COPY_BUFFER_SIZE)
        {
            in.readFully(tmp);
            write(tmp);
            size-=COPY_BUFFER_SIZE;
        }
        if (size>0)
        {
            in.readFully(tmp,0,(int)size);
            write(tmp,0,(int)size);
        }
    }

    public long fileSize() throws IOException
    {
        return toDisk?Files.size(filename):balos.getSize();
    }

    public void move(Path neu) throws IOException
    {
        if (Oma.verbose>=4)
        {
            System.err.println("        Moving '"+filename+"'");
            System.err.println("            to '"+neu+"'.");
        }
        if (toDisk)
            Files.move(filename,neu,StandardCopyOption.REPLACE_EXISTING);
        filename = neu;
    }

    public static boolean freeSomeMemory() throws IOException
    {
        int best = -1;
        long bestlength = 0;

        for (int i=0;i<pos.size();i++)
        {
            PositionOutputStream p = pos.get(i);
            if (p.toDisk) continue;

            long len = p.fileSize();
            if (len>bestlength)
            {
                bestlength = len;
                best = i;
            }
        }

        if (best>=0)
        {
            pos.get(best).switchToDisk();
            Tools.gc();
        }

        return best>=0;
    }

    public void switchToDisk() throws IOException
    {
        if (toDisk) return;

        if (Oma.verbose>=4)
            System.err.println("        Using temporary file '"+filename+"'.");

        fos = new FileOutputStream(filename.toString());
        fc = fos.getChannel();
        bos = new BufferedOutputStream(fos);

        balos.writeTo(bos);
        balos = null;

        toDisk = true;
    }
}

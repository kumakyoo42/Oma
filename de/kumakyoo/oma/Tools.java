package de.kumakyoo.oma;

import java.util.Arrays;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.Files;
import java.io.IOException;
import java.io.File;
import java.io.Reader;
import java.io.FileReader;
import java.io.InputStreamReader;
import java.io.FileInputStream;
import java.io.DataInputStream;
import java.io.BufferedInputStream;
import java.io.FileOutputStream;
import java.io.DataOutputStream;
import java.io.BufferedOutputStream;

public class Tools
{
    static final byte[] O5M_SIGNATUR = {(byte)0xff,(byte)0xe0,0x04,0x6f,0x35,0x6d,0x32};
    static final byte[] PBF_SIGNATUR = {0x0a,0x09,0x4f,0x53,0x4d};

    private static Path tmpDir = null;

    static long memavail()
    {
        return Runtime.getRuntime().maxMemory()-(Runtime.getRuntime().totalMemory()-Runtime.getRuntime().freeMemory());
    }

    static String humanReadable(long l)
    {
        if (l<1000) return Long.toString(l);

        int digits = Long.toString(l).length();
        return String.format("%."+(2-(digits-1)%3)+"f",l/Math.pow(10.0,3*((digits-1)/3)))+" KMGTE".charAt((digits-1)/3);
    }

    static long fromHumanReadable(String s)
    {
        if (s==null || s.length()<2) return -1;
        char last = s.charAt(s.length()-1);
        try {
            return switch (last)
            {
                case 'K','k' -> Long.parseLong(s.substring(0,s.length()-1))*1000L;
                case 'M','m' -> Long.parseLong(s.substring(0,s.length()-1))*1000000L;
                case 'G','g' -> Long.parseLong(s.substring(0,s.length()-1))*1000000000L;
                case 'T','t' -> Long.parseLong(s.substring(0,s.length()-1))*1000000000000L;
                case 'E','e' -> Long.parseLong(s.substring(0,s.length()-1))*1000000000000000L;
                default -> Long.parseLong(s);
            };
        } catch (NumberFormatException e) { return -1; }
    }

    static boolean isO5M(Path filename) throws IOException
    {
        DataInputStream in = new DataInputStream(Files.newInputStream(filename));
        byte[] data = new byte[7];
        in.readFully(data);
        in.close();

        return Arrays.compare(data,O5M_SIGNATUR)==0;
    }

    static boolean isPBF(Path filename) throws IOException
    {
        DataInputStream in = new DataInputStream(Files.newInputStream(filename));
        byte[] data = new byte[5];
        in.readInt();
        in.readFully(data);
        in.close();

        return Arrays.compare(data,PBF_SIGNATUR)==0;
    }

    static Reader getResource(String name, Object o) throws IOException
    {
        if (new File(name).exists())
            return new FileReader(name);

        return new InputStreamReader(o.getClass().getResourceAsStream("/"+name));
    }

    static Path tmpFile(String name) throws IOException
    {
        if (tmpDir==null)
        {
            if (Oma.tmpdir==null)
                tmpDir = Files.createTempDirectory("oma_");
            else
                tmpDir = Files.createTempDirectory(Path.of(Oma.tmpdir),"oma_");
        }

        return tmpDir.resolve(name);
    }

    static void deleteTmpDir() throws IOException
    {
        Files.delete(tmpDir);
    }

    static void gc()
    {
        System.gc();

        if (Oma.verbose>=4)
            System.out.println("        Available memory after garbage collection: "+Tools.humanReadable(Tools.memavail()));
    }
}

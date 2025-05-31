package de.kumakyoo.oma;

import java.util.List;
import java.util.ArrayList;
import java.util.StringTokenizer;
import java.io.IOException;
import java.io.EOFException;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.nio.file.Path;

public class ChunkGenerator
{
    public static long ID_MARKER = 0x7f000000L;

    private String bbs;
    private OmaOutputStream infile;
    private Path outfile;

    private List<Bounds> bounds;
    private List<Chunk> chunktable;

    private OmaOutputStream out;

    private int[] count;
    private int[] lastx;
    private int[] lasty;
    private OmaOutputStream[] cout;
    private OmaOutputStream[] pout;

    private int features;

    private byte[] buffer = new byte[1000000];

    public ChunkGenerator(String bbs, OmaOutputStream infile, Path outfile)
    {
        this.bbs = bbs;
        this.infile = infile;
        this.outfile = outfile;
    }

    public OmaOutputStream process() throws IOException
    {
        readBounds();
        Tools.allocateByteArrays();
        splitIntoChunks();
        Tools.releaseByteArrays();
        return out;
    }

    //////////////////////////////////////////////////////////////////

    private void readBounds() throws IOException
    {
        if (Oma.verbose>=2)
            System.out.println("  Reading bounds from '"+bbs+"'...");

        Bounds bb = Bounds.getWholeWorld();
        OmaInputStream in = OmaInputStream.init(infile);
        try {
            if (in.readByte()=='B')
                bb = new Bounds(in);
        } catch (EOFException e) {}
        in.close();

        bounds = new ArrayList<>();

        BufferedReader b = new BufferedReader(Tools.getResource(bbs,this));
        while (true)
        {
            String line = b.readLine();
            if (line==null) break;

            StringTokenizer t = new StringTokenizer(line);
            boolean iterate = t.countTokens()==6;
            long lon_from = Long.parseLong(t.nextToken());
            long lon_to = Long.parseLong(t.nextToken());
            long lon_step = iterate?Long.parseLong(t.nextToken()):(lon_to-lon_from);
            long lat_from = Long.parseLong(t.nextToken());
            long lat_to = Long.parseLong(t.nextToken());
            long lat_step = iterate?Long.parseLong(t.nextToken()):(lat_to-lat_from);

            for (long lon = lon_from;lon<lon_to;lon+=lon_step)
                for (long lat = lat_from;lat<lat_to;lat+=lat_step)
                {
                    Bounds tmp = new Bounds((int)lon,(int)lat,(int)(lon+lon_step),(int)(lat+lat_step));
                    if (tmp.intersects(bb))
                        bounds.add(tmp);
                }
        }
        b.close();

        bounds.add(Bounds.getWholeWorld());

        if (Oma.verbose>=2)
            System.out.println("    Found "+bounds.size()+" useful bounds.");
    }

    private void splitIntoChunks() throws IOException
    {
        if (Oma.verbose>=2)
            System.out.println("  Splitting data into chunks...");

        out = OmaOutputStream.init(outfile,true);
        writeHeader();
        Bounds bb = Bounds.getNoBounds();
        chunktable = new ArrayList<>();

        if (bounds.size()>=Oma.max_chunks && Oma.verbose>=3)
            System.out.println("    Too many chunks (>"+Oma.max_chunks+"). Using two-level splitting.");

        long saved = 0;
        long fs = infile.fileSize();
        OmaInputStream in = OmaInputStream.init(infile);

        byte type = 0;
        byte last = ' ';

        int c = 0;
        while (true)
        {
            if (!Oma.silent && ++c%100000==0)
                System.err.printf("Step 2: in: %.1f%% out: %.1f%%      \r",100.0/fs*in.getPosition(),100.0/fs*saved);

            try {
                type = in.readByte();
            } catch (EOFException e) { break; }

            if (type!=last && type!='B')
            {
                saved = in.getPosition();
                handleType(last);
                initGlobalData();
            }
            last = type;

            switch (type)
            {
            case 'B':
                bb = new Bounds(in);
                break;
            case 'N': case 'W': case 'A': case 'C':
                if (bounds.size()>=Oma.max_chunks)
                    copyElement(in,type);
                else
                    saveElementToChunk(in,type);
                break;
            default:
                System.err.println("Error: unknown type "+(char)type+".");
                System.exit(-1);
            }
        }
        in.release();
        handleType(last);
        if (!Oma.silent)
            System.err.print("Step 2:                             \r");

        writeChunkTable(bb);
        out.close();

        if (Oma.verbose>=2)
            System.out.println("    Splitting was successful ("+chunktable.size()+" chunks).");
    }

    private void handleType(byte type) throws IOException
    {
        if (type!='N' && type!='W' && type!='A' && type!='C') return;

        if (bounds.size()>=Oma.max_chunks)
        {
            for (int p=0;p<bounds.size()/Oma.max_chunks+1;p++)
            {
                count = new int[Oma.max_chunks];
                lastx = new int[Oma.max_chunks];
                lasty = new int[Oma.max_chunks];
                cout = new OmaOutputStream[Oma.max_chunks];
                for (int i=0;i<Oma.max_chunks;i++)
                    cout[i] = OmaOutputStream.init(Tools.tmpFile("chunk"+(Oma.max_chunks*p+i)));

                OmaInputStream in = OmaInputStream.init(pout[p]);
                while (true)
                    try {
                        saveElementToChunk(in,type,p*Oma.max_chunks);
                    } catch (EOFException e) { break; }
                in.release();

                saveChunks(type,p*Oma.max_chunks);
            }
        }
        else
            saveChunks(type,0);
    }

    //////////////////////////////////////////////////////////////////

    private void writeHeader() throws IOException
    {
        out.writeByte('O');
        out.writeByte('M');
        out.writeByte('A');
        out.writeByte(Oma.VERSION);
        features = Oma.zip_chunks?1:0;
        if (Oma.preserve_id) features += 4;
        if (Oma.preserve_version) features += 8;
        if (Oma.preserve_timestamp) features += 16;
        if (Oma.preserve_changeset) features += 32;
        if (Oma.preserve_user) features += 64;
        if (Oma.one_element) features += 128;
        out.writeByte(features);

        // place holder for bounding box and position of chunktable
        out.writeLong(0);
        out.writeLong(0);
        out.writeLong(0);
    }

    private void initGlobalData() throws IOException
    {
        if (bounds.size()>=Oma.max_chunks)
        {
            int b = bounds.size()/Oma.max_chunks+1;

            pout = new OmaOutputStream[b];
            for (int i=0;i<b;i++)
                pout[i] = OmaOutputStream.init(Tools.tmpFile("prechunk"+i),true);
        }
        else
        {
            int b = bounds.size()+1;

            count = new int[b];
            lastx = new int[b];
            lasty = new int[b];
            cout = new OmaOutputStream[b];
            for (int i=0;i<b;i++)
                cout[i] = OmaOutputStream.init(Tools.tmpFile("chunk"+i));
        }
    }

    //////////////////////////////////////////////////////////////////

    private void saveChunks(byte type, int delta) throws IOException
    {
        if (Oma.verbose>=3)
            System.out.println("      Saving "+cout.length+" chunks of type '"+((char)type)+"'.");

        for (int i=0;i<cout.length;i++)
        {
            if (count[i]>0)
            {
                if (!Oma.silent)
                    System.err.print("Step 2: saving chunk "+(i+1)+"/"+cout.length+"    \r");
                saveChunk(i,delta,type);
            }
            else
                cout[i].release();
        }
        if (!Oma.silent)
            System.err.print("Step 2:                                                                      \r");

        Tools.gc();
    }

    private void saveChunk(int i, int delta, byte type) throws IOException
    {
        copyChunk(i,delta,type);

        lastx[i] = 0;
        lasty[i] = 0;
        count[i] = 0;
    }

    private void copyChunk(int i, int delta, byte type) throws IOException
    {
        long start = out.getPosition();
        out.writeInt(0);
        addChunk(cout[i]);

        long end = out.getPosition();
        out.setPosition(start);
        out.writeInt(count[i]);
        out.setPosition(end);

        Bounds b = i+delta<bounds.size()?bounds.get(i+delta):Bounds.getNoBounds();
        chunktable.add(new Chunk(start,type,b));
    }

    private void addChunk(OmaOutputStream p) throws IOException
    {
        OmaInputStream in = OmaInputStream.init(p);
        while (true)
        {
            int size = in.read(buffer);
            if (size==-1) break;
            out.write(buffer,0,size);
        }
        in.release();
    }

    //////////////////////////////////////////////////////////////////

    private void copyElement(OmaInputStream in, byte type) throws IOException
    {
        ElementWithID e = readMetaData(in,type);

        int chunk = 0;

        if (type=='N')
        {
            int lon = in.readInt();
            int lat = in.readInt();
            chunk = getFirstChunk(lon,lat);
            e.writeMetaData(pout[chunk/Oma.max_chunks],features);
            pout[chunk/Oma.max_chunks].writeInt(lon);
            pout[chunk/Oma.max_chunks].writeInt(lat);
        }
        else if (type=='W')
        {
            int naz = in.readSmallInt();
            int[] lon = new int[naz];
            int[] lat = new int[naz];
            for (int i=0;i<naz;i++)
            {
                lon[i] = in.readInt();
                lat[i] = in.readInt();
                if (lon[i]>=ID_MARKER)
                    lon[i] = lat[i] = Integer.MAX_VALUE;
            }
            chunk = getFirstChunk(lon,lat);
            e.writeMetaData(pout[chunk/Oma.max_chunks],features);
            pout[chunk/Oma.max_chunks].writeSmallInt(naz);
            for (int i=0;i<naz;i++)
            {
                pout[chunk/Oma.max_chunks].writeInt(lon[i]);
                pout[chunk/Oma.max_chunks].writeInt(lat[i]);
            }
        }
        else if (type=='A')
        {
            int naz = in.readSmallInt();

            int[] lon = new int[naz];
            int[] lat = new int[naz];
            for (int i=0;i<naz;i++)
            {
                lon[i] = in.readInt();
                lat[i] = in.readInt();
                if (lon[i]>=ID_MARKER)
                    lon[i] = lat[i] = Integer.MAX_VALUE;
            }

            int haz = in.readSmallInt();
            int[][] hlon = new int[haz][];
            int[][] hlat = new int[haz][];
            for (int i=0;i<haz;i++)
            {
                naz = in.readSmallInt();
                hlon[i] = new int[naz];
                hlat[i] = new int[naz];
                for (int j=0;j<naz;j++)
                {
                    hlon[i][j] = in.readInt();
                    hlat[i][j] = in.readInt();
                    if (hlon[i][j]>=ID_MARKER)
                        hlon[i][j] = hlat[i][j] = Integer.MAX_VALUE;
                }
            }

            chunk = getFirstChunk(lon,lat,hlon,hlat);
            e.writeMetaData(pout[chunk/Oma.max_chunks],features);
            pout[chunk/Oma.max_chunks].writeSmallInt(lon.length);
            for (int i=0;i<lon.length;i++)
            {
                pout[chunk/Oma.max_chunks].writeInt(lon[i]);
                pout[chunk/Oma.max_chunks].writeInt(lat[i]);
            }
            pout[chunk/Oma.max_chunks].writeSmallInt(hlon.length);
            for (int i=0;i<hlon.length;i++)
            {
                pout[chunk/Oma.max_chunks].writeSmallInt(hlon[i].length);
                for (int j=0;j<hlon[i].length;j++)
                {
                    pout[chunk/Oma.max_chunks].writeInt(hlon[i][j]);
                    pout[chunk/Oma.max_chunks].writeInt(hlat[i][j]);
                }
            }
        }
        else if (type=='C')
        {
            in.readSmallInt();

            chunk = bounds.size();
            e.writeMetaData(pout[chunk/Oma.max_chunks],features|4);
            pout[chunk/Oma.max_chunks].writeSmallInt(0);
        }

        copyTags(in,pout[chunk/Oma.max_chunks]);
        copyMembers(in,pout[chunk/Oma.max_chunks]);
    }

    private void saveElementToChunk(OmaInputStream in, byte type) throws IOException
    {
        saveElementToChunk(in,type,0);
    }

    private void saveElementToChunk(OmaInputStream in, byte type, int delta) throws IOException
    {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        OmaOutputStream out = new OmaOutputStream(baos);

        ElementWithID e = readMetaData(in,type);

        int chunk = 0;

        if (type=='N')
        {
            int lon = in.readInt();
            int lat = in.readInt();
            chunk = getFirstChunk(lon,lat)-delta;
            lastx[chunk] = out.delta(lastx[chunk],lon);
            lasty[chunk] = out.delta(lasty[chunk],lat);
        }
        else if (type=='W')
        {
            int naz = in.readSmallInt();
            out.writeSmallInt(naz);
            int[] lon = new int[naz];
            int[] lat = new int[naz];
            for (int i=0;i<naz;i++)
            {
                lon[i] = in.readInt();
                lat[i] = in.readInt();
                if (lon[i]>=ID_MARKER)
                    lon[i] = lat[i] = Integer.MAX_VALUE;
            }
            chunk = getFirstChunk(lon,lat)-delta;
            for (int i=0;i<naz;i++)
            {
                lastx[chunk] = out.delta(lastx[chunk],lon[i]);
                lasty[chunk] = out.delta(lasty[chunk],lat[i]);
            }
        }
        else if (type=='A')
        {
            int naz = in.readSmallInt();

            int[] lon = new int[naz];
            int[] lat = new int[naz];
            for (int i=0;i<naz;i++)
            {
                lon[i] = in.readInt();
                lat[i] = in.readInt();
                if (lon[i]>=ID_MARKER)
                    lon[i] = lat[i] = Integer.MAX_VALUE;
            }

            int haz = in.readSmallInt();
            int[][] hlon = new int[haz][];
            int[][] hlat = new int[haz][];
            for (int i=0;i<haz;i++)
            {
                naz = in.readSmallInt();
                hlon[i] = new int[naz];
                hlat[i] = new int[naz];
                for (int j=0;j<naz;j++)
                {
                    hlon[i][j] = in.readInt();
                    hlat[i][j] = in.readInt();
                    if (hlon[i][j]>=ID_MARKER)
                        hlon[i][j] = hlat[i][j] = Integer.MAX_VALUE;
                }
            }

            chunk = getFirstChunk(lon,lat,hlon,hlat)-delta;
            out.writeSmallInt(lon.length);
            for (int i=0;i<lon.length;i++)
            {
                lastx[chunk] = out.delta(lastx[chunk],lon[i]);
                lasty[chunk] = out.delta(lasty[chunk],lat[i]);
            }
            out.writeSmallInt(hlon.length);
            for (int i=0;i<hlon.length;i++)
            {
                out.writeSmallInt(hlon[i].length);
                for (int j=0;j<hlon[i].length;j++)
                {
                    lastx[chunk] = out.delta(lastx[chunk],hlon[i][j]);
                    lasty[chunk] = out.delta(lasty[chunk],hlat[i][j]);
                }
            }
        }
        else if (type=='C')
        {
            in.readSmallInt();
            out.writeSmallInt(0);

            chunk = bounds.size()-delta;
        }

        copyTags(in,out);
        copyMembers(in,out);
        e.writeMetaData(out,features|(type=='C'?4:0));

        baos.writeTo(cout[chunk]);
        count[chunk]++;
    }

    //////////////////////////////////////////////////////////////////

    private ElementWithID readMetaData(OmaInputStream in, byte type) throws IOException
    {
        ElementWithID e = new ElementWithID();
        if ((features&4)!=0 || type=='C')
            e.id = in.readLong();
        if ((features&8)!=0)
            e.version = in.readSmallInt();
        if ((features&16)!=0)
            e.timestamp = in.readLong();
        if ((features&32)!=0)
            e.changeset = in.readLong();
        if ((features&64)!=0)
        {
            e.uid = in.readInt();
            e.user = in.readString();
        }
        return e;
    }

    private void copyTags(OmaInputStream in, OmaOutputStream out) throws IOException
    {
        int taz = in.readSmallInt();
        out.writeSmallInt(taz);
        for (int i=0;i<2*taz;i++)
            out.writeString(in.readString());
    }

    private void copyMembers(OmaInputStream in, OmaOutputStream out) throws IOException
    {
        int maz = in.readSmallInt();
        out.writeSmallInt(maz);
        for (int i=0;i<maz;i++)
        {
            out.writeLong(in.readLong());
            out.writeString(in.readString());
            out.writeSmallInt(in.readSmallInt());
        }
    }

    private int getFirstChunk(int lon, int lat)
    {
        for (int i=0;i<bounds.size();i++)
            if (bounds.get(i).contains(lon,lat))
                return i;

        return bounds.size();
    }

    private int getFirstChunk(int[] lon, int[] lat)
    {
        for (int i=0;i<bounds.size();i++)
            if (bounds.get(i).contains(lon,lat))
                return i;

        return bounds.size();
    }

    private int getFirstChunk(int[] lon, int[] lat, int[][] hlon, int[][] hlat)
    {
        for (int i=0;i<bounds.size();i++)
            if (bounds.get(i).contains(lon,lat,hlon,hlat))
                return i;

        return bounds.size();
    }

    //////////////////////////////////////////////////////////////////

    private void writeChunkTable(Bounds bb) throws IOException
    {
        long start = out.getPosition();
        out.writeInt(chunktable.size());
        for (Chunk chunk:chunktable)
        {
            out.writeLong(chunk.start);
            out.writeByte(chunk.type);
            chunk.bounds.write(out);
        }

        out.setPosition(5);
        bb.write(out);
        out.writeLong(start);
    }
}

package de.kumakyoo.oma;

import java.io.IOException;
import java.io.BufferedReader;
import java.io.BufferedOutputStream;
import java.nio.file.Path;
import java.util.List;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Map;
import java.util.Iterator;
import java.util.Arrays;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;

public class TypeAnalysis
{
    private String typefile;
    private OmaOutputStream infile;
    private Path outfile;

    private Chunk[] inChunks;
    private List<Chunk> outChunks;

    private OmaOutputStream out;
    private OmaInputStream in;

    private int features;

    private String[] nodeKeys;
    private String[][] nodeValues;
    private String[] wayKeys;
    private String[][] wayValues;
    private String[][] areaValues;
    private boolean[] isArea;
    private String[][] exceptions;
    private String[] collKeys;
    private String[][] collValues;
    private String[] lifeCyclePrefixes;
    private String[][] nlcpkey;
    private String[][] wlcpkey;
    private String[][] clcpkey;

    private int blocks;
    private int slices;

    private int splitcount;
    private OmaOutputStream splitout;

    public TypeAnalysis(String typefile, OmaOutputStream infile, Path outfile)
    {
        this.typefile = typefile;
        this.infile = infile;
        this.outfile = outfile;
    }

    public void process() throws IOException
    {
        readTypes();
        Tools.allocateByteArrays(200000000L);
        reorganizeChunks();
        Tools.releaseByteArrays();
    }

    //////////////////////////////////////////////////////////////////

    private void readTypes() throws IOException
    {
        if (Oma.verbose>=2)
            System.out.println("  Reading types from '"+typefile+"'...");

        List<String> nk = new ArrayList<>();
        List<List<String>> nv = new ArrayList<>();
        List<String> wk = new ArrayList<>();
        List<List<String>> wv = new ArrayList<>();
        List<List<String>> av = new ArrayList<>();
        List<Boolean> ia = new ArrayList<>();
        List<List<String>> ex = new ArrayList<>();
        List<String> lc = new ArrayList<>();
        List<String> ck = new ArrayList<>();
        List<List<String>> cv = new ArrayList<>();
        lc.add("");

        List<String> values = null;
        List<String> avalues = null;
        List<String> evalues = null;
        String mode = null;
        String key = null;
        String submode = null;
        int nr = -1;

        BufferedReader b = new BufferedReader(Tools.getResource(typefile,this));
        while (true)
        {
            String line = b.readLine();
            if (line==null) break;
            if (line.isBlank()) continue;
            if (line.charAt(0)=='#') continue;

            if (line.startsWith("      "))
            {
                if ("WAY".equals(submode))
                    values.add(line.trim());
                else if ("AREA".equals(submode))
                    avalues.add(line.trim());
                else if ("EXCEPTIONS".equals(submode))
                    evalues.add(line.trim());
            }
            else if (line.startsWith("    "))
            {
                if ("NODE".equals(mode) || "COLLECTION".equals(mode))
                    values.add(line.trim());
                else if ("WAY".equals(mode))
                {
                    if ("IS_AREA".equals(line.trim()))
                        ia.set(nr,true);
                    else if ("WAY".equals(line.trim()))
                        submode = "WAY";
                    else if ("AREA".equals(line.trim()))
                        submode = "AREA";
                    else if ("EXCEPTIONS".equals(line.trim()))
                        submode = "EXCEPTIONS";
                }
            }
            else if (line.startsWith("  "))
            {
                if ("NODE".equals(mode))
                {
                    key = line.trim();
                    nk.add(key);
                    values = new ArrayList<>();
                    nv.add(values);
                }
                else if ("WAY".equals(mode))
                {
                    key = line.trim();
                    wk.add(key);
                    ia.add(false);
                    nr++;
                    values = new ArrayList<>();
                    wv.add(values);
                    avalues = new ArrayList<>();
                    av.add(avalues);
                    evalues = new ArrayList<>();
                    ex.add(evalues);
                }
                else if ("COLLECTION".equals(mode))
                {
                    key = line.trim();
                    ck.add(key);
                    values = new ArrayList<>();
                    cv.add(values);
                }
                else if ("LIFECYCLE".equals(mode))
                {
                    key = line.trim()+":";
                    lc.add(key);
                }
            }
            else
            {
                mode = line;
                key = null;
                nr = -1;
            }
        }
        b.close();

        nodeKeys = new String[nk.size()];
        nk.toArray(nodeKeys);
        nodeValues = new String[nv.size()][];
        for (int i=0;i<nv.size();i++)
        {
            nodeValues[i] = new String[nv.get(i).size()];
            nv.get(i).toArray(nodeValues[i]);
        }
        wayKeys = new String[wk.size()];
        wk.toArray(wayKeys);
        wayValues = new String[wv.size()][];
        for (int i=0;i<wv.size();i++)
        {
            wayValues[i] = new String[wv.get(i).size()];
            wv.get(i).toArray(wayValues[i]);
        }
        areaValues = new String[av.size()][];
        for (int i=0;i<av.size();i++)
        {
            areaValues[i] = new String[av.get(i).size()];
            av.get(i).toArray(areaValues[i]);
        }
        isArea = new boolean[ia.size()];
        for (int i=0;i<ia.size();i++)
            isArea[i] = ia.get(i);
        exceptions = new String[ex.size()][];
        for (int i=0;i<ex.size();i++)
        {
            exceptions[i] = new String[ex.get(i).size()];
            ex.get(i).toArray(exceptions[i]);
        }
        collKeys = new String[ck.size()];
        ck.toArray(collKeys);
        collValues = new String[cv.size()][];
        for (int i=0;i<cv.size();i++)
        {
            collValues[i] = new String[cv.get(i).size()];
            cv.get(i).toArray(collValues[i]);
        }
        lifeCyclePrefixes = new String[lc.size()];
        lc.toArray(lifeCyclePrefixes);

        nlcpkey = new String[nodeKeys.length][lifeCyclePrefixes.length];
        for (int j=0;j<nodeKeys.length;j++)
            for (int k=0;k<lifeCyclePrefixes.length;k++)
                nlcpkey[j][k] = lifeCyclePrefixes[k]+nodeKeys[j];
        wlcpkey = new String[wayKeys.length][lifeCyclePrefixes.length];
        for (int j=0;j<wayKeys.length;j++)
            for (int k=0;k<lifeCyclePrefixes.length;k++)
                wlcpkey[j][k] = lifeCyclePrefixes[k]+wayKeys[j];
        clcpkey = new String[collKeys.length][lifeCyclePrefixes.length];
        for (int j=0;j<collKeys.length;j++)
            for (int k=0;k<lifeCyclePrefixes.length;k++)
                clcpkey[j][k] = lifeCyclePrefixes[k]+collKeys[j];

        if (Oma.verbose>=2)
        {
            System.out.println("    Read "+nodeKeys.length+" node keys, "+wayKeys.length+" way keys, "+collKeys.length+" collection keys");
            System.out.println("      and "+lifeCyclePrefixes.length+" lifecycle prefixes.");
        }
    }

    //////////////////////////////////////////////////////////////////

    private void reorganizeChunks() throws IOException
    {
        if (Oma.verbose>=2)
            System.out.println("  Determining types and sorting into blocks and slices...");

        blocks = slices = 0;

        out = OmaOutputStream.init(outfile,true);

        in = OmaInputStream.init(infile);
        copyHeader();
        addTypeHeader();
        readChunkTable();
        convertChunks();
        in.release();

        writeChunkTable();

        out.close();

        if (Oma.verbose>=2)
            System.out.println("    Analysing was successful ("+outChunks.size()+" chunks, "+blocks+" blocks, "+slices+" slices).");
    }

    private void convertChunks() throws IOException
    {
        outChunks = new ArrayList<>();

        for (int i=0;i<inChunks.length;i++)
        {
            if (!Oma.silent)
                System.err.print("Step 3: chunk: "+(i+1)+"/"+inChunks.length+"    \r");
            reorganizeChunk(inChunks[i]);
        }
        if (!Oma.silent)
            System.err.print("Step 3:                                                                      \r");
    }

    //////////////////////////////////////////////////////////////////

    private void copyHeader() throws IOException
    {
        for (int i=0;i<4;i++)
            out.writeByte(in.readByte());

        features = in.readByte();
        out.writeByte(features);

        for (int i=0;i<16;i++)
            out.writeByte(in.readByte());
        out.writeLong(0);
    }

    private void addTypeHeader() throws IOException
    {
        OmaOutputStream orig = out;

        DeflaterOutputStream dos = null;
        BufferedOutputStream bos = null;
        if (Oma.zip_chunks)
        {
            dos = new DeflaterOutputStream(out, new Deflater(Deflater.BEST_COMPRESSION));
            bos = new BufferedOutputStream(dos);
            out = new OmaOutputStream(bos);
        }

        out.writeSmallInt(4);
        out.writeByte('N');
        out.writeSmallInt(nodeKeys.length);
        for (int i=0;i<nodeKeys.length;i++)
        {
            out.writeString(nodeKeys[i]);
            out.writeSmallInt(nodeValues[i].length);
            for (int j=0;j<nodeValues[i].length;j++)
                out.writeString(nodeValues[i][j]);
        }

        out.writeByte('W');
        out.writeSmallInt(wayKeys.length);
        for (int i=0;i<wayKeys.length;i++)
        {
            out.writeString(wayKeys[i]);
            out.writeSmallInt(wayValues[i].length);
            for (int j=0;j<wayValues[i].length;j++)
                out.writeString(wayValues[i][j]);
        }

        out.writeByte('A');
        out.writeSmallInt(wayKeys.length);
        for (int i=0;i<wayKeys.length;i++)
        {
            out.writeString(wayKeys[i]);
            out.writeSmallInt(areaValues[i].length);
            for (int j=0;j<areaValues[i].length;j++)
                out.writeString(areaValues[i][j]);
        }

        out.writeByte('C');
        out.writeSmallInt(collKeys.length);
        for (int i=0;i<collKeys.length;i++)
        {
            out.writeString(collKeys[i]);
            out.writeSmallInt(collValues[i].length);
            for (int j=0;j<collValues[i].length;j++)
                out.writeString(collValues[i][j]);
        }

        if (Oma.zip_chunks)
        {
            bos.flush();
            dos.finish();
            out = orig;
        }
    }

    private void readChunkTable() throws IOException
    {
        in.setPosition(in.readLong());

        int chunk_count = in.readInt();

        inChunks = new Chunk[chunk_count];
        for (int i=0;i<chunk_count;i++)
            inChunks[i] = new Chunk(in.readLong(),in.readByte(),new Bounds(in));
    }

    //////////////////////////////////////////////////////////////////

    private void reorganizeChunk(Chunk c) throws IOException
    {
        in.setPosition(c.start);
        analyseChunkOfType(in,c.type,c.type=='W',in.readInt(),c.bounds);
        Tools.gc();
    }

    private void analyseChunkOfType(OmaInputStream in, byte type, boolean split, int count, Bounds b) throws IOException
    {
        if (split) initSplit();
        byte splittype = split?(byte)'A':type;

        String[] keys = type=='N'?nodeKeys:(type=='C'?collKeys:wayKeys);

        List<List<ElementWithID>> block = new ArrayList<>(keys.length+1);
        for (int i=0;i<keys.length+1;i++)
            block.add(new LinkedList<>());

        outChunks.add(new Chunk(out.getPosition(),splittype,b));

        boolean empty = true;
        for (int i=0;i<count;i++)
            empty &= handleNextElement(type, split, in, keys, block, b);

        if (empty)
            outChunks.remove(outChunks.size()-1);
        else
            writeBlocks(keys,splittype,block);

        if (split) analyseWays(b);
    }

    private void initSplit() throws IOException
    {
        splitcount = 0;
        splitout = OmaOutputStream.init(Tools.tmpFile("split"));
    }

    private void analyseWays(Bounds b) throws IOException
    {
        OmaInputStream splitin = OmaInputStream.init(splitout);
        analyseChunkOfType(splitin,(byte)'W',false,splitcount,b);
        splitin.release();
    }

    private boolean handleNextElement(byte type, boolean split,
                                      OmaInputStream in,
                                      String[] keys,
                                      List<List<ElementWithID>> block, Bounds b) throws IOException
    {
        boolean empty = true;

        String[][] lcpkey = type=='N'?nlcpkey:(type=='C'?clcpkey:wlcpkey);

        boolean used = false;

        ElementWithID e = type=='N'?new Node(in,features):(type=='C'?new Collection(in,features):(type=='W'?new Way(in,features):(type=='A'?new Area(in,features):new Collection(in,features))));

        boolean firstway = true;
        outer: for (int j=0;j<keys.length;j++)
            for (int k=0;k<lifeCyclePrefixes.length;k++)
                if (e.tags.containsKey(lcpkey[j][k]))
                {
                    used = true;

                    if (split && !isArea((Way)e,j))
                    {
                        if (firstway)
                        {
                            ((Way)e).write(splitout,features);
                            splitcount++;
                            firstway = false;
                        }
                        if (Oma.one_element) break outer;
                        break;
                    }

                    if (k>0)
                    {
                        e.tags.put("lifecycle",lifeCyclePrefixes[k].substring(0,lifeCyclePrefixes[k].length()-1));
                        e.tags.put(keys[j],e.tags.get(lcpkey[j][k]));
                        e.tags.remove(lcpkey[j][k]);
                    }
                    block.get(j).add(split?new Area((Way)e):e);
                    empty = false;
                    if (Oma.one_element) break outer;
                    break;
                }

        if (!used)
        {
            if (split && !isArea((Way)e,-1))
            {
                if (firstway)
                {
                    ((Way)e).write(splitout,features);
                    splitcount++;
                }
            }
            else
            {
                block.get(keys.length).add(split?new Area((Way)e):e);
                empty = false;
            }
        }

        int c = 0;
        if (Tools.memavail()<Oma.memlimit)
        {
            if (Oma.verbose>=3)
                System.out.println("      Memory low. Splitting chunk.");

            writeBlocks(keys,split?(byte)'A':type,block);

            for (int j=0;j<keys.length+1;j++)
                block.get(j).clear();
            Tools.gc();

            outChunks.add(new Chunk(out.getPosition(),split?(byte)'A':type,b));
            out.resetDelta();
            return true;
        }

        return empty;
    }

    private boolean isArea(Way e, int j)
    {
        if (!e.isClosed()) return false;
        String area = e.tags.get("area");
        if ("yes".equals(area)) return true;
        if ("no".equals(area)) return false;
        if (j==-1) return false;
        return isArea[j] != Arrays.asList(exceptions[j]).contains(e.tags.get(wayKeys[j]));
    }

    private void writeBlocks(String[] keys, byte type, List<List<ElementWithID>> block) throws IOException
    {
        long startpos = out.getPosition();
        out.writeInt(0); // Position der Sprungtabelle

        long[] start = new long[keys.length+1];

        int count = 0;
        for (int i=0;i<keys.length;i++)
        {
            if (block.get(i).isEmpty()) continue;
            start[i] = out.getPosition();
            reorganizeBlock(block.get(i),
                            type=='N'?nodeKeys[i]:(type=='C'?collKeys[i]:wayKeys[i]),
                            type=='N'?nodeValues[i]:(type=='C'?collValues[i]:(type=='W'?wayValues[i]:areaValues[i])));
            count++;
        }

        int other = keys.length;
        if (!block.get(other).isEmpty())
        {
            start[other] = out.getPosition();
            writeOtherBlock(block.get(other));
            count++;
        }

        long tablepos = out.getPosition();
        out.setPosition(startpos);
        out.writeInt((int)(tablepos-startpos));
        out.setPosition(tablepos);

        out.writeSmallInt(count);
        for (int i=0;i<=keys.length;i++)
            if (start[i]>0)
            {
                out.writeInt((int)(start[i]-startpos));
                out.writeString(i==other?"":keys[i]);
            }
        blocks += count;
    }

    private void writeOtherBlock(List<ElementWithID> block) throws IOException
    {
        long startpos = out.getPosition();
        out.writeInt(0);

        writeSlice(block,null,null);

        long tablepos = out.getPosition();
        out.setPosition(startpos);
        out.writeInt((int)(tablepos-startpos));
        out.setPosition(tablepos);

        out.writeSmallInt(1);
        out.writeInt(4);
        out.writeString("");
        blocks++;
        slices++;
    }

    private void reorganizeBlock(List<ElementWithID> block, String key, String[] values) throws IOException
    {
        long startpos = out.getPosition();
        out.writeInt(0);

        long[] start = new long[values.length+1];

        int count = 0;
        for (int j=0;j<values.length;j++)
        {
            start[j] = out.getPosition();

            int size = block.size();
            writeSlice(block,key,values[j]);
            if (block.size()==size)
            {
                out.setPosition(start[j]);
                start[j] = 0;
                continue;
            }

            long end = out.getPosition();
            out.setPosition(start[j]);
            out.writeInt(size-block.size());
            out.setPosition(end);

            count++;
        }

        if (!block.isEmpty())
        {
            start[values.length] = out.getPosition();
            writeSlice(block,null,null);
            count++;
        }

        long tablepos = out.getPosition();
        out.setPosition(startpos);
        out.writeInt((int)(tablepos-startpos));
        out.setPosition(tablepos);

        out.writeSmallInt(count);
        for (int j=0;j<=values.length;j++)
            if (start[j]>0)
            {
                out.writeInt((int)(start[j]-startpos));
                out.writeString(j==values.length?"":values[j]);
            }
        slices += count;
    }

    private void writeSlice(List<ElementWithID> block, String key, String value) throws IOException
    {
        out.writeInt(block.size());

        OmaOutputStream orig = out;

        DeflaterOutputStream dos = null;
        BufferedOutputStream bos = null;
        if (Oma.zip_chunks)
        {
            dos = new DeflaterOutputStream(out, new Deflater(Deflater.BEST_COMPRESSION));
            bos = new BufferedOutputStream(dos);
            out = new OmaOutputStream(bos);
        }

        out.resetDelta();
        if (value==null)
            writeAll(block);
        else
            writeAndRemove(block,key,value);

        if (Oma.zip_chunks)
        {
            bos.flush();
            dos.finish();
            out = orig;
        }
    }

    private void writeAll(List<ElementWithID> block) throws IOException
    {
        for (ElementWithID e:block)
            e.write(out,features);
    }

    private void writeAndRemove(List<ElementWithID> block, String key, String value) throws IOException
    {
        Iterator<ElementWithID> itr = block.iterator();
        while (itr.hasNext())
        {
            ElementWithID e = itr.next();
            if (e.tags.get(key).equals(value))
            {
                e.write(out,features);
                itr.remove();
            }
        }
    }

    private void writeChunkTable() throws IOException
    {
        long start = out.getPosition();
        out.writeInt(outChunks.size());
        for (Chunk chunk:outChunks)
        {
            out.writeLong(chunk.start);
            out.writeByte(chunk.type);
            chunk.bounds.write(out);
        }

        if (Oma.verbose>=3)
            System.out.println("      Filesize: "+Tools.humanReadable(out.getPosition()));

        out.setPosition(21);
        out.writeLong(start);
    }
}

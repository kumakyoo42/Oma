package de.kumakyoo.oma;

import java.util.Locale;
import java.util.StringTokenizer;
import java.time.Duration;
import java.io.IOException;
import java.nio.file.Path;

public class Oma
{
    static final byte VERSION = 0;

    private static Path infile;
    private static Path outfile;

    private static String bbsfile = "default.bbs";
    private static String typefile = "default.type";

    static String tmpdir = null;

    static boolean preserve_id = false;
    static boolean preserve_version = false;
    static boolean preserve_timestamp = false;
    static boolean preserve_changeset = false;
    static boolean preserve_user = false;

    static boolean zip_chunks = true;
    static boolean one_element = false;

    public static int max_chunks = 1000;

    static int verbose = 0;
    static boolean silent = false;

    static long memlimit = 100_000_000;

    private static long start,stop,start1,stop1,start2,stop2,start3,stop3;

    public static void main(String[] args) throws IOException
    {
        try {
            init(args);
            OmaOutputStream mem = step1();
            Tools.gc();
            mem = step2(mem);
            Tools.gc();
            step3(mem);
            finish();
        } catch (OutOfMemoryError e)
            {
                explainMemoryError();
                throw e;
            }
    }

    private static void init(String[] args)
    {
        start = System.currentTimeMillis();
        Locale.setDefault(Locale.ROOT);
        parseArgs(args);
    }

    private static OmaOutputStream step1() throws IOException
    {
        if (verbose>=1)
            System.out.println("Step 1: Reunifying");

        start1 = System.currentTimeMillis();

        OmaOutputStream erg = null;
        try {
            erg = new Reunify(infile,Tools.tmpFile("tmp1")).process();
        } catch (IOException e) { e.printStackTrace(); System.exit(-1); }

        stop1 = System.currentTimeMillis();

        if (verbose>=2)
            System.out.println("==================================================================");

        return erg;
    }

    private static OmaOutputStream step2(OmaOutputStream in) throws IOException
    {
        if (verbose>=1)
            System.out.println("Step 2: Generating chunks");

        start2 = System.currentTimeMillis();

        OmaOutputStream erg = null;
        try {
            erg = new ChunkGenerator(bbsfile,in,Tools.tmpFile("tmp2")).process();
        } catch (IOException e) { e.printStackTrace(); System.exit(-1); }

        stop2 = System.currentTimeMillis();

        if (verbose>=2)
            System.out.println("==================================================================");

        return erg;
    }

    private static void step3(OmaOutputStream in) throws IOException
    {
        if (verbose>=1)
            System.out.println("Step 3: Analysing types");

        start3 = System.currentTimeMillis();

        try {
            new TypeAnalysis(typefile,in,outfile).process();
        } catch (IOException e) { e.printStackTrace(); System.exit(-1); }

        stop3 = System.currentTimeMillis();

        if (verbose>=2)
            System.out.println("==================================================================");
    }

    private static void finish() throws IOException
    {
        Tools.deleteTmpDir();

        if (verbose>=1)
        {
            stop = System.currentTimeMillis();
            Duration d = Duration.ofMillis(stop-start);
            Duration d1 = Duration.ofMillis(stop1-start1);
            Duration d2 = Duration.ofMillis(stop2-start2);
            Duration d3 = Duration.ofMillis(stop3-start3);
            System.out.println(String.format("Total time used: %02d:%02d:%02d (%02d:%02d:%02d + %02d:%02d:%02d + %02d:%02d:%02d)",
                                             d.toHours(),d.toMinutesPart(),d.toSecondsPart(),
                                             d1.toHours(),d1.toMinutesPart(),d1.toSecondsPart(),
                                             d2.toHours(),d2.toMinutesPart(),d2.toSecondsPart(),
                                             d3.toHours(),d3.toMinutesPart(),d3.toSecondsPart()
                                            ));
        }
    }

    private static void usage(String error)
    {
        if (error!=null)
        {
            System.err.println("Error: "+error);
            System.err.println();
        }
        System.err.println("Usage: java -jar oma.jar [options] <input file> [<output file>]");
        System.err.println();
        System.err.println("Options:");
        System.err.println("  -b <bbs-file>  bbs-file; default: default.bbs");
        System.err.println("  -t <bbs-file>  type-file; default: default.type");
        System.err.println("  -p <list>      data to preserve (id,version,timestamp,changeset,user,");
        System.err.println("                                   all,none); default: none");
        System.err.println("  -0             do not zip slices");
        System.err.println("  -1             add each element only once");
        System.err.println("  -v             increase verboseness, can be used up to 4 times");
        System.err.println("  -s             silent mode: do not show any progress");
        System.err.println("  -c <limit>     maximum number of simultaneously generated chunks;");
        System.err.println("                 default: 1000");
        System.err.println("  -tmp <dir>     directory to use for tmp files; default: default tmp directory");
        System.err.println("  -m <limit>     set amount of spare memory; default: "+Tools.humanReadable(memlimit));
        System.err.println();
        System.err.println("  --help         print this help");
        System.exit(-1);
    }

    private static void parseArgs(String[] args)
    {
        int pos = 0;
        while (pos<args.length)
        {
            if (args[pos].length()==0) usage("empty argument");
            if (args[pos].charAt(0)=='-')
            {
                if (args[pos].equals("-v"))
                    verbose++;
                else if (args[pos].equals("-s"))
                    silent = true;
                else if (args[pos].equals("-0"))
                    zip_chunks = false;
                else if (args[pos].equals("-1"))
                    one_element = true;
                else if (args[pos].equals("-b"))
                {
                    if (pos==args.length-1) usage("missing filename after '-b'");
                    bbsfile = args[pos+1];
                    pos++;
                }
                else if (args[pos].equals("-t"))
                {
                    if (pos==args.length-1) usage("missing filename after '-t'");
                    typefile = args[pos+1];
                    pos++;
                }
                else if (args[pos].equals("-p"))
                {
                    if (pos==args.length-1) usage("missing list after '-p'");
                    setPreserve(args[pos+1]);
                    pos++;
                }
                else if (args[pos].equals("-tmp"))
                {
                    if (pos==args.length-1) usage("missing directory after '-tmp'");
                    tmpdir = args[pos+1];
                    pos++;
                }
                else if (args[pos].equals("-m"))
                {
                    if (pos==args.length-1) usage("missing parameter after '-m'");
                    memlimit = Tools.fromHumanReadable(args[pos+1]);
                    if (memlimit<0) usage("invalid memory limit '"+args[pos+1]+"'");
                    pos++;
                }
                else if (args[pos].equals("-c"))
                {
                    if (pos==args.length-1) usage("missing parameter after '-c'");
                    try {
                        max_chunks = Integer.parseInt(args[pos+1]);
                    } catch (Exception e) { usage("invalid chunk limit '"+args[pos+1]+"'"); }
                    if (max_chunks<1) usage("invalid chunk limit '"+args[pos+1]+"'");
                    pos++;
                }
                else if (args[pos].equals("--help"))
                    usage(null);
                else
                    usage("unknown option '"+args[pos]+"'");
            }
            else
            {
                if (pos!=args.length-1 && pos!=args.length-2) usage("additional arguments after filename(s)");
                infile = Path.of(args[pos]).toAbsolutePath();
                pos++;
                outfile = (pos<args.length?Path.of(args[pos]):replaceExtension(infile,".oma")).toAbsolutePath();
                return;
            }
            pos++;
        }
        usage("no input file given");
    }

    private static void setPreserve(String s)
    {
        StringTokenizer t = new StringTokenizer(s,",");
        int az = t.countTokens();
        while (t.hasMoreTokens())
        {
            String token = t.nextToken();
            switch (token)
            {
                case "all" ->
                {
                    if (az!=1) usage("'-p all' cannot be mixed with other values");
                    preserve_id = preserve_version = preserve_timestamp = preserve_changeset = preserve_user = true;
                }
                case "none" ->
                {
                    if (az!=1) usage("'-p none' cannot be mixed with other values");
                    preserve_id = preserve_version = preserve_timestamp = preserve_changeset = preserve_user = false;
                }
            case "id" -> preserve_id = true;
            case "version","v" -> preserve_version = true;
            case "timestamp", "time", "ts" -> preserve_timestamp = true;
            case "changeset", "cs" -> preserve_changeset = true;
            case "user", "uid" -> preserve_user = true;
            default -> usage("unknown element '"+token+"' to preserve");
            }
        }
    }

    private static Path replaceExtension(Path p, String ext)
    {
        int size = p.getNameCount();
        String last = p.getName(size-1).toString();
        int dotpos = last.lastIndexOf('.');
        if (dotpos>=0) last = last.substring(0,dotpos);
        last += ext;
        return p.resolveSibling(last);
    }

    private static void explainMemoryError()
    {
        System.err.println("****************************************************************");
        System.err.println("A non recoverably memory error occured.");
        System.err.println();
        System.err.println("Some ideas for trouble shooting:");
        System.err.println("- Increase amount of spare memory using -m flag.");
        System.err.println("- Decrease(!) jvm heap size using (java flag) -Xmx.");
        System.err.println("- Use a different java GC (e.g. (java flag) -XX:+UseParallelGC).");
        System.err.println("****************************************************************");
    }
}

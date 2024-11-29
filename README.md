# Oma - a converter from OSM data to OMA file format

**Warning: The oma file format is still considered experimental and
may be subject to change without warning. For this reason this
software may change too.**

Having said this: To make things more stable, feedback is needed. Any
help will be appreciated. :-)

More information on *oma file format* is coming soon. Please be patient.

## Install

Download [oma.jar](/oma.jar) and make sure that Java Runtime
Environment (JRE) is available on your system.

## Usage

    java [java options] -jar oma.jar [options] <input file> [<output file>]

### Input file

The input has to be in one of the following three formats: OSM-file
(`.osm.xml`), O5M-file (`.o5m`) or PBF-file (`.pbf`).

### Output file

The output is an oma file. If no filename is given, the name of the
inputfile is used with extension replaced by `.oma`.

### Options

    -b <bbs-file>  bbs-file; default: default.bbs
    -t <bbs-file>  type-file; default: default.type
    -p <list>      data to preserve (id,version,timestamp,changeset,user,
                                     all,none); default: none
    -nz            do not zip chunks
    -v             increase verboseness, can be used up to 4 times
    -s             silent mode: do not show any progress
    -tmp <dir>     directory to use for tmp files; default: default tmp directory
    -m <limit>     set amount of spare memory; default: 100M

    --help         print this help

### Java-Options

The Java Virtual Machine knows a lot of options. For Oma you need
probably only one of them: `-Xmx<size>` to indicate the maximum amount
of memory used. E.g. `-Xmx3G` to use 3 giga bytes of memory.

## Build

To build `oma.jar` on your own, on Linux systems you can use
`build.sh`. I havn't tested building on other operating systems.
Basically you need to compile the java files in folder
`de/kumakyoo/oma` and build a jar file from the resulting class files,
including the two `default.*`-files and the manifest file.

## Known bugs

Currently I don't know of any bugs, but probably there are a lot of
them. Especially the pbf reader is known to be incomplete, because I
didn't find any pbf files with the missing features to test with...

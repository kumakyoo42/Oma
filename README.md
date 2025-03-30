# Oma - a converter from OSM data to OMA file format

***Note: Oma software (including additional programs like
[Opa](https://github.com/kumakyoo42/Opa) and
[libraries](https://github.com/kumakyoo42/OmaLibJava)) and [related
file formats](https://github.com/kumakyoo42/oma-file-formats) are
currently experimental and subject to change without notice.***

## Install

Download [oma.jar](/oma.jar) and make sure that a Java Runtime
Environment (JRE) is available on your system.

## Usage

    java [java options] -jar oma.jar [options] <input file> [<output file>]

### Input file

The input file format has to be one of the following three:

* [OSM XML format](https://wiki.openstreetmap.org/wiki/OSM_XML)
* [O5M format](https://wiki.openstreetmap.org/wiki/O5m)
* [PBF format](https://wiki.openstreetmap.org/wiki/PBF_Format)

### Output file

The format of the output file is the [OMA
format](https://github.com/kumakyoo42/oma-file-formats/blob/main/OMA.md).
If no filename is provided, the name of the input file is used with
extension replaced by `.oma`.

### Dealing with incomplete or invalid input

Missing nodes are included with both coordinates set to 0x7fffffff.
Missing ways or missing members are not included. Holes inside of
missing areas are not included as well.

Invalid ways (and areas derived from ways) are preserved as is.

Special handling of multipolygons and boundaries: Members that do not
form a closed loop are not included. Inner members which are not
inside of an outer member (either because all nodes are outside or
because the outer member is invalid or missing) are not included.

Special handling of restrictions and destination_signs: If `via`,
`intersection` or `to` is missing, the element is not included. If
`from` is missing, an empty `from` is used.

### Options

    -b <bbs-file>  bbs-file; default: default.bbs
    -t <bbs-file>  type-file; default: default.type
    -p <list>      data to preserve (id,version,timestamp,changeset,user,
                                     all,none); default: none
    -0             do not zip slices
    -1             add each element only once
    -v             increase verboseness, can be used up to 4 times
    -s             silent mode: do not show any progress
    -tmp <dir>     directory to use for tmp files; default: default tmp directory
    -m <limit>     set amount of spare memory; default: 100M

    --help         print this help

### Java-Options

The Java Virtual Machine accepts a great many options. For Oma you
need probably only one of them: `-Xmx<size>` to increase the amount of
memory that can be used by the virtual machine. For example: Adding
`-Xmx3G` enables the use of up to 3 giga bytes of main memory.

## Build

On Linux systems you can use the shell script `build.sh` to build
`oma.jar` on your own.

Building on other platforms is neither tested nor directly supported
yet. Basically you need to compile the java files in folder
`de/kumakyoo/oma` and build a jar file from the resulting class files,
including the two `default.*`-files and the manifest file.

## Known bugs

There are no known bugs, but some known flaws:

* The PBF format reader is known to be incomplete. (I couldn't find
any files containing the missing features, so I neither could test
them nor did I need them.)

* The application may crash due to memory shortage. This cannot be
avoided when working with JAVA and large amounts of memory, because
JAVA does not provide any means to make sure that memory allocation
will work in advance. According to JAVA specs the virtual machine may
break at any time with an OutOfMemoryError. Further more, there is no
guarantee that garbage collection is invoked, even when explicitly
requested by the software.

* Due to this memory limitations, some temporary files are used, even
when they would have fitted completely into main memory.

* In the first step of the conversion, Oma tries to allocate as many
nodes as possible. This may lead to the usage of temporary files,
which could have been avoided, if the number of nodes allocated would
have been smaller.

* Bounding boxes for collections are not calculated.

package de.kumakyoo.oma;

import java.util.Map;
import java.util.*;
import java.util.stream.*;
import java.io.*;

public class ElementWithID extends Element
{
    long id;

    int version;
    long timestamp;
    long changeset;
    int uid;
    String user;

    Map<String, String> tags;

    private static long discarded = 0;

    // list taken von ID and JOSM
    private static final Set<String> discardable =
        Stream.of(
                  "created_by",
                  "converted_by",
                  "geobase:datasetName",
                  "geobase:uuid",
                  "gnis:import_uuid",
                  "import_uuid",
                  "KSJ2:ADS",
                  "KSJ2:ARE",
                  "KSJ2:AdminArea",
                  "KSJ2:COP_label",
                  "KSJ2:DFD",
                  "KSJ2:INT",
                  "KSJ2:INT_label",
                  "KSJ2:LOC",
                  "KSJ2:LPN",
                  "KSJ2:OPC",
                  "KSJ2:PubFacAdmin",
                  "KSJ2:RAC",
                  "KSJ2:RAC_label",
                  "KSJ2:RIC",
                  "KSJ2:RIN",
                  "KSJ2:WSC",
                  "KSJ2:coordinate",
                  "KSJ2:curve_id",
                  "KSJ2:curve_type",
                  "KSJ2:filename",
                  "KSJ2:lake_id",
                  "KSJ2:lat",
                  "KSJ2:long",
                  "KSJ2:river_id",
                  "odbl",
                  "odbl:note",
                  "osmarender:nameDirection",
                  "osmarender:renderName",
                  "osmarender:renderRef",
                  "osmarender:rendernames",
                  "SK53_bulk:load",
                  "sub_sea:type",
                  "tiger:upload_uuid",
                  "tiger:tlid",
                  "tiger:source",
                  "tiger:separated",
                  "yh:LINE_NAME",
                  "yh:LINE_NUM",
                  "yh:STRUCTURE",
                  "yh:TOTYUMONO",
                  "yh:TYPE",
                  "yh:WIDTH",
                  "yh:WIDTH_RANK"
                 )
        .collect(Collectors.toCollection(HashSet::new));

    public ElementWithID()
    {
    }

    public ElementWithID(long id, int version, long timestamp, long changeset, int uid, String user, Map<String, String> tags)
    {
        this.id = id;
        this.version = version;
        this.timestamp = timestamp;
        this.changeset = changeset;
        this.uid = uid;
        this.user = user;
        this.tags = tags;
        int count = this.tags.size();
        this.tags.keySet().removeIf(key -> discardable.contains(key));
        discarded += count-this.tags.size();
    }

    public static long discardedTags()
    {
        return discarded;
    }

    //////////////////////////////////////////////////////////////////

    public void write(OmaOutputStream out, int features) throws IOException
    {
        writeGeo(out);
        writeTags(out);
        writeMetaData(out,features);
    }

    public void writeGeo(OmaOutputStream out) throws IOException
    {
    }

    public void readTags(OmaInputStream in) throws IOException
    {
        tags = new HashMap<>();
        int az = in.readSmallInt();
        for (int i=0;i<az;i++)
            tags.put(in.readString(),in.readString());
    }

    public void writeTags(OmaOutputStream out) throws IOException
    {
        out.writeSmallInt(tags.size());
        for (String key:tags.keySet())
        {
            out.writeString(key);
            out.writeString(tags.get(key));
        }
    }

    public void readMetaData(OmaInputStream in, int features) throws IOException
    {
        if ((features&2)!=0)
            id = in.readLong();
        if ((features&4)!=0)
            version = in.readSmallInt();
        if ((features&8)!=0)
            timestamp = in.readLong();
        if ((features&16)!=0)
            changeset = in.readLong();
        if ((features&32)!=0)
        {
            uid = in.readInt();
            user = in.readString();
        }
    }

    public void writeMetaData(OmaOutputStream out, int features) throws IOException
    {
        if ((features&2)!=0)
            out.writeLong(id);
        if ((features&4)!=0)
            out.writeSmallInt(version);
        if ((features&8)!=0)
            out.writeLong(timestamp);
        if ((features&16)!=0)
            out.writeLong(changeset);
        if ((features&32)!=0)
        {
            out.writeInt(uid);
            out.writeString(user);
        }
    }

    public String toString()
    {
        return id+" "+version+" "+timestamp+" "+changeset+" "+uid+" "+user;
    }
}

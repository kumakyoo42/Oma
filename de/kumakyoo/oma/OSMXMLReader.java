package de.kumakyoo.oma;

import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.*;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLInputFactory;
import java.time.format.DateTimeFormatter;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

public class OSMXMLReader extends OSMReader
{
    XMLStreamReader r;

    int lon = 0;
    int lat = 0;
    long id = 0;
    int version = 0;
    long timestamp = 0;
    long changeset = 0;
    int uid = 0;
    String user = "";
    Map<String, String> tags = null;
    List<Long> nds = null;
    List<OSMMember> members = null;

    String key = null;
    String value = null;
    String type = null;
    long ref = Long.MIN_VALUE;
    String role = null;

    public OSMXMLReader(Path filename) throws IOException
    {
        try {
            r = XMLInputFactory.newInstance().createXMLStreamReader(Files.newInputStream(filename));
        } catch (XMLStreamException e) { throw new IOException("XMLStreamException"); }
    }

    public void close() throws IOException
    {
        try {
            r.close();
        } catch (XMLStreamException e) { throw new IOException("XMLStreamException"); }
    }

    public Element next() throws IOException
    {
        while (true)
        try {
            if (!r.hasNext()) return null;

            int code = r.next();

            if (code==XMLStreamConstants.START_ELEMENT)
            {
                String name = r.getName().toString();

                if ("node".equals(name))
                {
                    int c = r.getAttributeCount();
                    for (int i=0;i<c;i++)
                    {
                        String aname = r.getAttributeLocalName(i);
                        String value = r.getAttributeValue(i);

                        if ("lat".equals(aname))
                            lat = (int)(0.5+10000000*Double.parseDouble(value));
                        else if ("lon".equals(aname))
                            lon = (int)(0.5+10000000*Double.parseDouble(value));
                        else
                            checkBasicData(aname,value);
                    }

                    tags = new HashMap<>();
                }
                else if ("tag".equals(name))
                {
                    int c = r.getAttributeCount();
                    for (int i=0;i<c;i++)
                    {
                        String aname = r.getAttributeLocalName(i);

                        if ("k".equals(aname))
                            key = r.getAttributeValue(i);
                        else if ("v".equals(aname))
                            value = r.getAttributeValue(i);
                    }

                    tags.put(key,value);
                }
                else if ("way".equals(name))
                {
                    int c = r.getAttributeCount();
                    for (int i=0;i<c;i++)
                    {
                        String aname = r.getAttributeLocalName(i);
                        String value = r.getAttributeValue(i);

                        checkBasicData(aname,value);
                    }

                    tags = new HashMap<>();
                    nds = new ArrayList<>();
                }
                else if ("nd".equals(name))
                {
                    int c = r.getAttributeCount();
                    for (int i=0;i<c;i++)
                    {
                        String aname = r.getAttributeLocalName(i);

                        if ("ref".equals(aname))
                            nds.add(Long.valueOf(r.getAttributeValue(i)));
                    }
                }
                else if ("relation".equals(name))
                {
                    int c = r.getAttributeCount();
                    for (int i=0;i<c;i++)
                    {
                        String aname = r.getAttributeLocalName(i);
                        String value = r.getAttributeValue(i);

                        checkBasicData(aname,value);
                    }

                    tags = new HashMap<>();
                    members = new ArrayList<>();
                }
                else if ("member".equals(name))
                {
                    int c = r.getAttributeCount();
                    for (int i=0;i<c;i++)
                    {
                        String aname = r.getAttributeLocalName(i);

                        if ("type".equals(aname))
                            type = r.getAttributeValue(i);
                        else if ("ref".equals(aname))
                            ref = Long.valueOf(r.getAttributeValue(i));
                        else if ("role".equals(aname))
                            role = r.getAttributeValue(i);
                    }

                    members.add(new OSMMember(type,ref,role));
                }
                else if ("bounds".equals(name))
                {
                    int minlat = Integer.MIN_VALUE;
                    int minlon = Integer.MIN_VALUE;
                    int maxlat = Integer.MIN_VALUE;
                    int maxlon = Integer.MIN_VALUE;

                    int c = r.getAttributeCount();
                    for (int i=0;i<c;i++)
                    {
                        String aname = r.getAttributeLocalName(i);
                        if ("minlat".equals(aname))
                            minlat = (int)(0.5+10000000*Double.parseDouble(r.getAttributeValue(i)));
                        else if ("minlon".equals(aname))
                            minlon = (int)(0.5+10000000*Double.parseDouble(r.getAttributeValue(i)));
                        else if ("maxlat".equals(aname))
                            maxlat = (int)(0.5+10000000*Double.parseDouble(r.getAttributeValue(i)));
                        else if ("maxlon".equals(aname))
                            maxlon = (int)(0.5+10000000*Double.parseDouble(r.getAttributeValue(i)));
                    }

                    return new Bounds(minlon,minlat,maxlon,maxlat);
                }
            }
            else if (code==XMLStreamConstants.END_ELEMENT)
            {
                String name = r.getName().toString();

                if ("node".equals(name))
                    return new OSMNode(id,version,timestamp,changeset,uid,user,lon,lat,tags);
                else if ("way".equals(name))
                    return new OSMWay(id,version,timestamp,changeset,uid,user,nds,tags);
                else if ("relation".equals(name))
                    return new OSMRelation(id,version,timestamp,changeset,uid,user,members,tags);
            }
        } catch (XMLStreamException e) { throw new IOException("XMLStreamException"); }
    }

    private void checkBasicData(String name, String value)
    {
        if ("id".equals(name))
            id = Long.valueOf(value);
        else if ("version".equals(name))
            version = Integer.valueOf(value);
        else if ("timestamp".equals(name))
        {
            DateTimeFormatter f = DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss'Z'");
            timestamp = LocalDateTime.from(f.parse(value)).toEpochSecond(ZoneOffset.UTC);
        }
        else if ("changeset".equals(name))
            changeset = Long.valueOf(value);
        else if ("uid".equals(name))
            uid = Integer.valueOf(value);
        else if ("user".equals(name))
            user = value;
    }
}

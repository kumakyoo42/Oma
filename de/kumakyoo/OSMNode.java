package de.kumakyoo;

import java.util.Map;

public class OSMNode extends ElementWithID
{
    int lon;
    int lat;

    public OSMNode(long id, int version, long timestamp, long changeset, int uid, String user, int lon, int lat, Map<String, String> tags)
    {
        super(id,version,timestamp,changeset,uid,user,tags);
        this.lon = lon;
        this.lat = lat;
    }

    public String toString()
    {
        return "Node: "+super.toString()+" "+lon+" "+lat+" "+tags;
    }

}

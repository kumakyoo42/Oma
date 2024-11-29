package de.kumakyoo.oma;

import java.util.List;
import java.util.Map;

public class OSMWay extends ElementWithID
{
    List<Long> nds;

    public OSMWay(long id, int version, long timestamp, long changeset, int uid, String user, List<Long> nds, Map<String, String> tags)
    {
        super(id,version,timestamp,changeset,uid,user,tags);
        this.nds = nds;
    }

    public String toString()
    {
        return "Way: "+super.toString()+" "+nds+" "+tags;
    }

}

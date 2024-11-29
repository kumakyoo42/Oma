package de.kumakyoo.oma;

import java.util.List;
import java.util.Map;

public class OSMRelation extends ElementWithID
{
    List<Member> members;

    public OSMRelation(long id, int version, long timestamp, long changeset, int uid, String user, List<Member> members, Map<String, String> tags)
    {
        super(id,version,timestamp,changeset,uid,user,tags);
        this.members = members;
    }

    public String toString()
    {
        return "Relation: "+super.toString()+" "+members+" "+tags;
    }

}

package de.kumakyoo.oma;

import java.io.IOException;

public class Node extends ElementWithID
{
    int lon;
    int lat;

    public Node()
    {
    }

    public Node(OmaInputStream in, int features) throws IOException
    {
        lon = in.readDeltaX();
        lat = in.readDeltaY();
        readTags(in);
        readMembers(in);
        readMetaData(in,features);
    }

    public static Node readGeo(OmaInputStream in) throws IOException
    {
        Node n = new Node();
        n.lon = in.readDeltaX();
        n.lat = in.readDeltaY();
        return n;
    }

    public void writeGeo(OmaOutputStream out) throws IOException
    {
        out.writeDeltaX(lon);
        out.writeDeltaY(lat);
    }

}

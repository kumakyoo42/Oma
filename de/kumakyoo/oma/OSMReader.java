package de.kumakyoo.oma;

import java.io.IOException;
import java.nio.file.Path;

abstract public class OSMReader
{
    public static OSMReader getReader(Path filename) throws IOException
    {
        if (Tools.isO5M(filename))
            return new O5MReader(filename);
        else if (Tools.isPBF(filename))
            return new PBFReader(filename);
        else
            return new OSMXMLReader(filename);
    }

    abstract public void close() throws IOException;
    abstract public Element next() throws IOException;
}

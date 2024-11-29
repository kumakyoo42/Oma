package de.kumakyoo.oma;

public class Chunk
{
    long start;

    byte type;
    Bounds bounds;

    public Chunk(long start, byte type, Bounds bounds)
    {
        this.start = start;
        this.type = type;
        this.bounds = bounds;
    }
}

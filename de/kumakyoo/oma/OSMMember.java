package de.kumakyoo.oma;

public class OSMMember
{
    String type;
    long ref;
    String role;

    public OSMMember(String type, long ref, String role)
    {
        this.type = type;
        this.ref = ref;
        this.role = role;
    }

    public OSMMember(char type, long ref, String role)
    {
        this.type = ""+type;
        this.ref = ref;
        this.role = role;
    }

    public String toString()
    {
        return type+"/"+ref+"/"+role;
    }
}

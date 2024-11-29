package de.kumakyoo.oma;

public class Member
{
    String type;
    long ref;
    String role;

    public Member(String type, long ref, String role)
    {
        this.type = type;
        this.ref = ref;
        this.role = role;
    }

    public String toString()
    {
        return type+"/"+ref+"/"+role;
    }
}

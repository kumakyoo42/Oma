package de.kumakyoo.oma;

import java.util.List;
import java.io.*;

public class Collection extends ElementWithID
{
    public String[] noderole;
    public int[] nlon;
    public int[] nlat;

    public String[] wayrole;
    public int[][] wlon;
    public int[][] wlat;

    public Collection(OmaInputStream in, int features) throws IOException
    {
        int naz = in.readSmallInt();
        noderole = new String[naz];
        nlon = new int[naz];
        nlat = new int[naz];

        for (int k=0;k<naz;k++)
        {
            noderole[k] = in.readString();
            nlon[k] = in.readDeltaX();
            nlat[k] = in.readDeltaY();
        }

        int waz = in.readSmallInt();
        wayrole = new String[waz];
        wlon = new int[waz][];
        wlat = new int[waz][];
        for (int k=0;k<waz;k++)
        {
            wayrole[k] = in.readString();
            int az = in.readSmallInt();
            wlon[k] = new int[az];
            wlat[k] = new int[az];
            for (int i=0;i<az;i++)
            {
                wlon[k][i] = in.readDeltaX();
                wlat[k][i] = in.readDeltaY();
            }
        }

        int aaz = in.readSmallInt();

        readTags(in);
        readMetaData(in,features);
    }

    public void writeGeo(OmaOutputStream out) throws IOException
    {
        out.writeSmallInt(nlon.length);
        for (int k=0;k<nlon.length;k++)
        {
            out.writeString(noderole[k]);
            out.writeDeltaX(nlon[k]);
            out.writeDeltaY(nlat[k]);
        }

        out.writeSmallInt(wlon.length);
        for (int k=0;k<wlon.length;k++)
        {
            out.writeString(wayrole[k]);
            out.writeSmallInt(wlon[k].length);
            for (int i=0;i<wlon[k].length;i++)
            {
                out.writeDeltaX(wlon[k][i]);
                out.writeDeltaY(wlat[k][i]);
            }
        }

        out.writeSmallInt(0); // areas not yet supported
    }
}

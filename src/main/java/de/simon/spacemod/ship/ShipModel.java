package de.simon.spacemod.ship;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.GZIPInputStream;
import org.jspecify.annotations.Nullable;

/**
 * Außenform eines Schiffs als Blockraster, erzeugt aus einem 3D-Modell mit {@code tools/voxelize_stl.py}.
 * Die Modelle stammen aus der "Star Trek USS Enterprise Ultimate Collection" von Alexei Bruton
 * (Solid_Alexei), Lizenz CC BY-NC 4.0.
 *
 * <p>Rasterachsen: x = Länge (Bug bei 0), y = Höhe, z = Breite.
 */
public final class ShipModel {
    public final int sx, sy, sz;
    /** Index der Mittelebene in z (lokales z = Index - zc). */
    public final int zc;
    private final boolean[] solid;

    private ShipModel(int sx, int sy, int sz, boolean[] solid) {
        this.sx = sx;
        this.sy = sy;
        this.sz = sz;
        this.zc = sz / 2;
        this.solid = solid;
    }

    public boolean solid(int x, int y, int iz) {
        if (x < 0 || y < 0 || iz < 0 || x >= sx || y >= sy || iz >= sz) return false;
        return solid[(x * sy + y) * sz + iz];
    }

    /** Lädt das Modell eines Schiffs und skaliert es (Verkleinerung: ein Block ist massiv, wenn irgendein Quellblock es ist). */
    public static @Nullable ShipModel load(ShipDesign design, double scale) {
        try (InputStream raw = ShipModel.class.getResourceAsStream("/ships/" + design.id + ".bin.gz")) {
            if (raw == null) return null;
            DataInputStream in = new DataInputStream(new GZIPInputStream(raw));
            byte[] magic = in.readNBytes(4);
            if (!"SHIP".equals(new String(magic))) return null;
            int sx = in.readInt(), sy = in.readInt(), sz = in.readInt();
            byte[] bits = in.readAllBytes();
            if (scale >= 0.999) {
                boolean[] g = new boolean[sx * sy * sz];
                for (int i = 0; i < g.length; i++) g[i] = (bits[i >> 3] >> (7 - (i & 7)) & 1) != 0;
                return new ShipModel(sx, sy, sz, g);
            }
            int tx = (int) Math.ceil(sx * scale) + 1, ty = (int) Math.ceil(sy * scale) + 1, tz = (int) Math.ceil(sz * scale) + 1;
            boolean[] g = new boolean[tx * ty * tz];
            int i = 0;
            for (int x = 0; x < sx; x++)
                for (int y = 0; y < sy; y++)
                    for (int z = 0; z < sz; z++, i++)
                        if ((bits[i >> 3] >> (7 - (i & 7)) & 1) != 0)
                            g[((int) (x * scale) * ty + (int) (y * scale)) * tz + (int) (z * scale)] = true;
            return new ShipModel(tx, ty, tz, g);
        } catch (IOException e) {
            return null;
        }
    }
}

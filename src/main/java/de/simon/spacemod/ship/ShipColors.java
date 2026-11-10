package de.simon.spacemod.ship;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.GZIPInputStream;
import org.jspecify.annotations.Nullable;

/**
 * Farbklassen der Hülle, übertragen aus texturierten 3D-Modellen von Sketchfab
 * ({@code tools/colors_from_glb.py}). Ein Byte je Block des Modellrasters (Originalgröße).
 */
public final class ShipColors {
    public static final int LIGHT = 1, MID = 2, DARK = 3, BLACK = 4, RED = 5, TEAL = 6, BLUEPAINT = 7, COPPER = 8,
        RED_GLOW = 9, ORANGE_GLOW = 10, BLUE_GLOW = 11, VIOLET_GLOW = 12, WINDOW = 13;

    private final int sx, sy, sz, zc;
    private final byte[] cls;

    private ShipColors(int sx, int sy, int sz, byte[] cls) {
        this.sx = sx;
        this.sy = sy;
        this.sz = sz;
        this.zc = sz / 2;
        this.cls = cls;
    }

    public static @Nullable ShipColors load(ShipDesign design) {
        try (InputStream raw = ShipColors.class.getResourceAsStream("/ships/" + design.id + ".colors.gz")) {
            if (raw == null) return null;
            DataInputStream in = new DataInputStream(new GZIPInputStream(raw));
            if (!"COLR".equals(new String(in.readNBytes(4)))) return null;
            int sx = in.readInt(), sy = in.readInt(), sz = in.readInt();
            byte[] cls = in.readNBytes(sx * sy * sz);
            return new ShipColors(sx, sy, sz, cls);
        } catch (IOException e) {
            return null;
        }
    }

    private int at(int x, int y, int iz) {
        if (x < 0 || y < 0 || iz < 0 || x >= sx || y >= sy || iz >= sz) return 0;
        return cls[(x * sy + y) * sz + iz];
    }

    /**
     * Farbklasse an einer Bauplan-Position (Bauplan-Raster = Modellraster bei Größe 1, sonst verkleinert).
     * Gesucht wird in der Nähe, weil die Hülle bei Verkleinerung nicht genau auf dem Modell liegt.
     */
    public int lookup(int x, int y, int z, double scale) {
        double f = 1 / Math.min(1, scale);
        int cx = (int) Math.round(x * f), cy = (int) Math.round(y * f), cz = (int) Math.round(z * f) + zc;
        int reach = (int) Math.ceil(f);
        int c = at(cx, cy, cz);
        if (c != 0) return c;
        for (int r = 1; r <= reach; r++)
            for (int dx = -r; dx <= r; dx++)
                for (int dy = -r; dy <= r; dy++)
                    for (int dz = -r; dz <= r; dz++) {
                        c = at(cx + dx, cy + dy, cz + dz);
                        if (c != 0) return c;
                    }
        return 0;
    }
}

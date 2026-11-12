package de.simon.spacemod.defense;

/**
 * Form des Schildes als Bitraster (x schnellste Achse, je Zeile {@code words} Longs). Aus den
 * Schiffsblöcken entsteht der geschützte Raum: kleine Löcher und Vertiefungen werden geschlossen,
 * das Innere gefüllt, einzelne herausstehende Blöcke bekommen nur 1 Block Abstand, der Rumpf 2.
 * Die Schildzellen sind die äußere Schicht dieses Raums.
 */
public final class ShieldShape {
    public final int sx, sy, sz, words;
    public final long[] bits;

    public ShieldShape(int sx, int sy, int sz) {
        this.sx = sx;
        this.sy = sy;
        this.sz = sz;
        this.words = (sx + 63) >>> 6;
        this.bits = new long[words * sy * sz];
    }

    private ShieldShape(ShieldShape o, long[] bits) {
        this.sx = o.sx;
        this.sy = o.sy;
        this.sz = o.sz;
        this.words = o.words;
        this.bits = bits;
    }

    private int row(int y, int z) {
        return (y * sz + z) * words;
    }

    public boolean get(int x, int y, int z) {
        if (x < 0 || y < 0 || z < 0 || x >= sx || y >= sy || z >= sz) return false;
        return (bits[row(y, z) + (x >>> 6)] >>> (x & 63) & 1L) != 0;
    }

    public void set(int x, int y, int z) {
        bits[row(y, z) + (x >>> 6)] |= 1L << (x & 63);
    }

    public int count() {
        int n = 0;
        for (long b : bits) n += Long.bitCount(b);
        return n;
    }

    /** Liegt die Zelle im Raum und hat einen Nachbarn außerhalb (= Schildzelle)? */
    public boolean surface(int x, int y, int z) {
        return get(x, y, z) && (!get(x + 1, y, z) || !get(x - 1, y, z) || !get(x, y + 1, z) || !get(x, y - 1, z)
            || !get(x, y, z + 1) || !get(x, y, z - 1));
    }

    private ShieldShape copy(long[] b) {
        return new ShieldShape(this, b);
    }

    /** Bits außerhalb von sx in der letzten Zeilenspalte löschen. */
    private void trim(long[] b) {
        int rem = sx & 63;
        if (rem == 0) return;
        long mask = (1L << rem) - 1;
        for (int r = words - 1; r < b.length; r += words) b[r] &= mask;
    }

    private long[] dilateX(long[] a) {
        long[] o = new long[a.length];
        for (int r = 0; r < a.length; r += words)
            for (int w = 0; w < words; w++) {
                long v = a[r + w];
                long left = (v << 1) | (w > 0 ? a[r + w - 1] >>> 63 : 0);
                long right = (v >>> 1) | (w < words - 1 ? a[r + w + 1] << 63 : 0);
                o[r + w] = v | left | right;
            }
        trim(o);
        return o;
    }

    private long[] dilateZ(long[] a) {
        long[] o = a.clone();
        for (int y = 0; y < sy; y++)
            for (int z = 0; z < sz; z++) {
                int r = row(y, z);
                for (int w = 0; w < words; w++) {
                    if (z > 0) o[r + w] |= a[row(y, z - 1) + w];
                    if (z < sz - 1) o[r + w] |= a[row(y, z + 1) + w];
                }
            }
        return o;
    }

    private long[] dilateY(long[] a) {
        long[] o = a.clone();
        for (int y = 0; y < sy; y++)
            for (int z = 0; z < sz; z++) {
                int r = row(y, z);
                for (int w = 0; w < words; w++) {
                    if (y > 0) o[r + w] |= a[row(y - 1, z) + w];
                    if (y < sy - 1) o[r + w] |= a[row(y + 1, z) + w];
                }
            }
        return o;
    }

    /** Würfel-Dilatation (26 Nachbarn). */
    private long[] cube(long[] a) {
        return dilateY(dilateZ(dilateX(a)));
    }

    /** Kreuz-Dilatation (6 Nachbarn). */
    private long[] cross(long[] a) {
        long[] x = dilateX(a), z = dilateZ(a), y = dilateY(a);
        long[] o = new long[a.length];
        for (int i = 0; i < a.length; i++) o[i] = x[i] | y[i] | z[i];
        return o;
    }

    /** Annähernd kugelförmige Dilatation um r Blöcke (abwechselnd Kreuz und Würfel). */
    private long[] dilate(long[] a, int r) {
        long[] o = a;
        for (int i = 0; i < r; i++) o = (i % 2 == 0) ? cross(o) : cube(o);
        return o;
    }

    private long[] not(long[] a) {
        long[] o = new long[a.length];
        for (int i = 0; i < a.length; i++) o[i] = ~a[i];
        trim(o);
        return o;
    }

    private long[] erode(long[] a, int r) {
        return not(dilate(not(a), r));
    }

    private static long[] or(long[] a, long[] b) {
        long[] o = new long[a.length];
        for (int i = 0; i < a.length; i++) o[i] = a[i] | b[i];
        return o;
    }

    /** Außenraum per Flutfüllung vom Rand aus (6er-Nachbarschaft) durch alles, was nicht {@code wall} ist. */
    private long[] outside(long[] wall) {
        long[] out = new long[wall.length];
        int[] queue = new int[1 << 16];
        int head = 0, tail = 0, size = 0;
        int n = sx * sy * sz;
        // Startzellen: alle freien Randzellen
        for (int y = 0; y < sy; y++)
            for (int z = 0; z < sz; z++)
                for (int x = 0; x < sx; x++) {
                    if (x != 0 && x != sx - 1 && y != 0 && y != sy - 1 && z != 0 && z != sz - 1) {
                        if (x == 1) x = sx - 2; // nur Ränder
                        continue;
                    }
                    int i = (y * sz + z) * sx + x;
                    if (test(wall, x, y, z) || test(out, x, y, z)) continue;
                    mark(out, x, y, z);
                    if (size == queue.length) { queue = grow(queue, head, size); head = 0; tail = size; }
                    queue[tail] = i;
                    tail = (tail + 1) % queue.length;
                    size++;
                }
        int[] dx = {1, -1, 0, 0, 0, 0}, dy = {0, 0, 1, -1, 0, 0}, dz = {0, 0, 0, 0, 1, -1};
        while (size > 0) {
            int i = queue[head];
            head = (head + 1) % queue.length;
            size--;
            int x = i % sx, rest = i / sx, z = rest % sz, y = rest / sz;
            for (int k = 0; k < 6; k++) {
                int nx = x + dx[k], ny = y + dy[k], nz = z + dz[k];
                if (nx < 0 || ny < 0 || nz < 0 || nx >= sx || ny >= sy || nz >= sz) continue;
                if (test(wall, nx, ny, nz) || test(out, nx, ny, nz)) continue;
                mark(out, nx, ny, nz);
                if (size == queue.length) { queue = grow(queue, head, size); head = 0; tail = size; }
                queue[tail] = (ny * sz + nz) * sx + nx;
                tail = (tail + 1) % queue.length;
                size++;
            }
        }
        return out;
    }

    private static int[] grow(int[] q, int head, int size) {
        int[] n = new int[q.length * 2];
        for (int k = 0; k < size; k++) n[k] = q[(head + k) % q.length];
        return n;
    }

    private boolean test(long[] b, int x, int y, int z) {
        return (b[row(y, z) + (x >>> 6)] >>> (x & 63) & 1L) != 0;
    }

    private void mark(long[] b, int x, int y, int z) {
        b[row(y, z) + (x >>> 6)] |= 1L << (x & 63);
    }

    /**
     * Geschützter Raum aus den Schiffsblöcken (dieses Raster). Das Raster braucht rundum mindestens
     * {@link #MARGIN} freie Zellen.
     */
    public ShieldShape protectedSpace() {
        long[] solid = bits;
        long[] closed = erode(dilate(solid, 2), 2);            // Löcher bis ~4 Blöcke schließen
        long[] body = not(outside(closed));                     // Inneres füllen
        long[] core = dilate(erode(body, 1), 1);                // dünne Vorsprünge weg
        long[] prot = or(dilate(core, 2), dilate(body, 1));     // Rumpf 2 Blöcke, Vorsprünge 1 Block Abstand
        prot = erode(dilate(prot, 2), 2);                       // Vertiefungen glätten
        return copy(prot);
    }

    /** Freier Rand, den {@link #protectedSpace()} braucht. */
    public static final int MARGIN = 6;
}

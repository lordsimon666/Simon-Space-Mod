package de.simon.spacemod.ship;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/**
 * Prozeduraler Bauplan der USS Enterprise – wahlweise NCC-1701 (Originalserie) oder
 * NCC-1701-A (Refit aus den Kinofilmen), siehe {@link ShipDesign}.
 *
 * <p>Alle Maße sind in Metern der echten Enterprise angegeben (1 Block = 1 m bei {@code scale = 1}).
 * Die Hülle wird mit dem Faktor {@code scale} verkleinert. Die wichtigsten Räume – Brücke,
 * Maschinenraum (mit Warpkern) und Transporterraum – werden dagegen <b>immer in Originalgröße</b>
 * gebaut; die Hülle wird dort, wo nötig, so weit "aufgepolstert", dass die Räume hineinpassen.
 *
 * <p>Lokales Koordinatensystem des Bauplans:
 * <ul>
 *   <li>X: Längsachse, 0 = Bug (vorne), wächst nach achtern</li>
 *   <li>Y: nach oben, 0 = tiefster Punkt</li>
 *   <li>Z: Querachse, positiv = Steuerbord (rechts)</li>
 * </ul>
 * Jede Zelle speichert ein {@link ShipPart} plus Blickrichtung (so angegeben, als zeige der Bug
 * nach NORDEN) und ggf. Gitter-Verbindungen – siehe {@link #part(int)}, {@link #facing(int)}.
 */
public final class EnterpriseBlueprint {
    // Bauteile (Komponenten-IDs im Raster)
    private static final byte NONE = 0, SAUCER = 1, BRIDGE = 2, SENSOR = 3, NECK = 4, SECONDARY = 5,
        SHUTTLEBAY = 6, DISH = 7, ANTENNA = 8, PYLON = 9, NACELLE = 10, BUSSARD = 11,
        // Excelsior (NCC-1701-B)
        SPINE = 12, NECKPLATE = 13, COLUMN = 14, SPONSON = 15, FIN = 16;

    private static final int DECK_HEIGHT = 4;

    public final double scale;
    public final ShipDesign design;
    private final boolean refit;
    private final boolean excelsior;
    /** Next-Generation-Schiff (C, D, E)? */
    private final boolean tng;
    /** NX-01 aus "Star Trek: Enterprise"? */
    private final boolean nx;
    /** Hüllen mit Aztec-Plattenmuster (alle außer der Originalserie). */
    private final boolean aztecHull;
    /** Radius des Brückenmoduls (Originalgröße). */
    private final int bR;
    /**
     * Ovale Untertasse: Streckfaktoren (je ≤ 1) für die Längs- und Querachse. Alle Radien der
     * Untertasse (sR, Ringe, Räume) sind "normiert": r = hypot(dx * kx, z * kz).
     */
    private double kx = 1, kz = 1;
    /** Excelsior: halbe Breite des Unterrumpfs. */
    private double eW;
    /** Excelsior: halbe Breite der (flachen) Gondeln. */
    private double nW;
    public final int sizeX, sizeY, sizeZ;
    /** Verschiebung lokales z -> Rasterindex. */
    public final int zOffset;

    private final byte[] comp;
    private final boolean[] hull;
    /** 0 = nichts setzen, sonst (Teil+1) | Richtung << 8 | Variante bzw. Gitter-Verbindungen << 10 (16 Bit). */
    private final short[] cell;

    /** Turbolift-Verbindungen in lokalen Koordinaten. */
    public final List<LiftStop> liftStops = new ArrayList<>();
    /** Position (lokal) vor dem Kommandantensessel, für den Teleport zur Brücke. */
    public BlockPos bridgeSpawn = BlockPos.ZERO;

    /** Ein Turbolift-Halt (lokale Position des Panels + Deckname). */
    public record LiftStop(BlockPos pos, String name) {}

    // ---- Abgeleitete Geometrie (in Blöcken) ----
    private final double s;          // Hüllen-Skalierung
    private final double k;          // Skalierung der Sekundärhülle (mind. so groß, dass der Maschinenraum passt)
    // Nicht final: werden bei vorhandenem 3D-Modell aus dem Modell vermessen.
    private double sCx, sR, sMid; // Untertassensektion: Mittelpunkt X, Radius, Mittelebene
    private double eY, eR, eXf, eXr; // Sekundärhülle: Achshöhe, Radius, Front, Heck
    private double nZ, nY, nR, nX0, nX1; // Warpgondeln
    private int bridgeFloor;
    private int floorBase;

    /** Außenform aus einem 3D-Modell (oder null = eingebaute, berechnete Form). */
    private final ShipModel model;
    /** Aus dem Modell vermessene Profile der Untertasse (Index = Radius in Blöcken). */
    private double[] topProf, botProf;
    /** Aus dem Modell vermessener Unterrumpf je x: Ober-/Unterkante. */
    private double[] lowTop, lowBot;

    public EnterpriseBlueprint(double scale) {
        this(ShipDesign.NCC_1701, scale);
    }

    public EnterpriseBlueprint(ShipDesign design, double scale) {
        this.scale = scale;
        this.design = design;
        this.refit = design.refit;
        this.excelsior = design.excelsior;
        this.tng = design.tng;
        this.nx = design.nx;
        this.aztecHull = refit || excelsior || tng || nx;
        this.bR = tng ? 13 : 10;
        this.s = scale;
        this.k = Math.max(scale, 9.5 / 15.0);

        // Maße in Metern: Originalserie / Refit (NCC-1701-A) / Excelsior (NCC-1701-B)
        eR = (excelsior ? 15 : refit ? 14 : 15) * k;
        eW = 40 * k;
        eY = 1 + eR;
        sMid = eY + eR + (excelsior ? 18 : refit ? 14 : 20) * s;
        sR = (excelsior ? 94.5 : refit ? 70 : 63.5) * s;
        sCx = 1 + sR;
        // Refit: Sekundärhülle weiter vorne (Deflektor fast unter der Untertasse)
        eXf = (excelsior ? 86 : refit ? 108 : 118) * s;
        eXr = (excelsior ? 397 : refit ? 232 : 245) * s;
        nR = Math.max((excelsior ? 5.5 : refit ? 6.5 : 7) * s, 2.6);
        nW = excelsior ? Math.max(9 * s, 3.5) : nR;
        // Refit: Gondeln höher über der Untertasse und enger zusammen
        // Excelsior: Gondeln auf Höhe der Untertasse, innerhalb ihrer Breite
        nZ = excelsior ? Math.max(65 * s, 30 * k + nW + 4) : Math.max((refit ? 36 : 42) * s, eR + nR + 6);
        nY = excelsior ? sMid + 10 * s : sMid + (refit ? 32 : 8) * s;
        nX0 = (excelsior ? 227 : refit ? 140 : 150) * s;
        nX1 = (excelsior ? 467 : refit ? 305 : 289) * s;

        model = ShipModel.load(design, scale);
        if (model != null) {
            fitToModel();
            bridgeFloor = (int) Math.floor(topProf[Math.min(topProf.length - 1, (int) Math.round((bR + 1) * Math.min(1, s / 0.75)))]);
        } else {
            bridgeFloor = (int) Math.floor(saucerTop(0));
        }
        floorBase = Math.floorMod(bridgeFloor, DECK_HEIGHT);

        sizeX = model != null ? model.sx + 2 : (int) Math.ceil(Math.max(nX1, eXr) + 3);
        sizeY = model != null ? Math.max(model.sy + 2, bridgeFloor + 12) : Math.max(bridgeFloor + 12, (int) Math.ceil(nY + nR + 3));
        int halfZ = model != null ? Math.max(model.zc, model.sz - 1 - model.zc) + 2 : (int) Math.ceil(Math.max(sR, nZ + nR) + 2);
        zOffset = halfZ;
        sizeZ = 2 * halfZ + 1;

        int n = sizeX * sizeY * sizeZ;
        comp = new byte[n];
        hull = new boolean[n];
        cell = new short[n];

        buildShape();
        skinAndDecks();
        if (model != null) paintDish();
        registryLettering();
        hullDetails();
        if (refit) torpedoLaunchers();
        if (model == null) smoothSaucer(); // das Modell ist schon glatt
        boolean fullDecks = s >= 0.75;
        if (fullDecks) {
            layoutSaucerDecks();
            layoutSecondaryDecks();
        }
        buildBridge();
        BlockPos eng = buildEngineering();
        BlockPos transporter = buildTransporterRoom(fullDecks);
        linkTurbolifts(eng, transporter);
        if (tng) tngInterior();
        if (nx) nxInterior();
        liftStyle();
        connectBars();
    }

    // =====================================================================================
    //  Raster-Hilfsfunktionen
    // =====================================================================================

    private int idx(int x, int y, int z) {
        return (x * sizeY + y) * sizeZ + (z + zOffset);
    }

    private boolean inBounds(int x, int y, int z) {
        return x >= 0 && x < sizeX && y >= 0 && y < sizeY && z >= -zOffset && z <= zOffset;
    }

    private void set(int x, int y, int z, ShipPart part) {
        set(x, y, z, part, Direction.NORTH);
    }

    private void set(int x, int y, int z, ShipPart part, Direction facing) {
        set(x, y, z, part, facing, 0);
    }

    /** @param variant 0–3: Variante (Farbe/Anzeige) bzw. Halbstufe (0 = unten, 1 = oben) */
    private void set(int x, int y, int z, ShipPart part, Direction facing, int variant) {
        if (!inBounds(x, y, z)) return;
        cell[idx(x, y, z)] = (short) ((part.ordinal() + 1) | (facing.get2DDataValue() << 8) | ((variant & 3) << 10));
    }

    public static int variant(int code) {
        return (code >> 10) & 3;
    }

    /** @return Bauteil der Zelle oder {@code null}, wenn dort nichts gesetzt wird */
    public ShipPart get(int x, int y, int z) {
        if (!inBounds(x, y, z)) return null;
        int c = cell[idx(x, y, z)] & 0xFFFF;
        return c == 0 ? null : part(c);
    }

    /** Roher Zellcode (0 = nichts) – siehe {@link #part(int)}, {@link #facing(int)}. */
    public int codeAt(int x, int y, int z) {
        return inBounds(x, y, z) ? cell[idx(x, y, z)] & 0xFFFF : 0;
    }

    public static ShipPart part(int code) {
        return ShipPart.byId((code & 0xFF) - 1);
    }

    public static Direction facing(int code) {
        return Direction.from2DDataValue((code >> 8) & 3);
    }

    public static boolean bar(int code, Direction dir) {
        return (code >> (10 + dir.get2DDataValue()) & 1) != 0;
    }

    private byte compAt(int x, int y, int z) {
        return inBounds(x, y, z) ? comp[idx(x, y, z)] : NONE;
    }

    private boolean isInterior(int x, int y, int z) {
        return inBounds(x, y, z) && comp[idx(x, y, z)] != NONE && !hull[idx(x, y, z)];
    }

    /** Normierter Radius in der Untertasse (bei runder Untertasse = Abstand zur Mitte). */
    private double rad(double x, double z) {
        return Math.hypot((x - sCx) * kx, z * kz);
    }

    /** x-Position zum normierten Polarpunkt (Radius r, Winkel a: 0 = Bug, positiv = Steuerbord). */
    private double polX(double r, double a) {
        return sCx - r * Math.cos(a) / kx;
    }

    private double polZ(double r, double a) {
        return r * Math.sin(a) / kz;
    }

    /** Alle gesetzten Zellen in Bau-Reihenfolge (von vorne nach hinten, unten nach oben). */
    public void forEach(CellConsumer consumer) {
        for (int x = 0; x < sizeX; x++)
            for (int y = 0; y < sizeY; y++)
                for (int z = -zOffset; z <= zOffset; z++) {
                    int code = cell[idx(x, y, z)] & 0xFFFF;
                    if (code != 0) consumer.accept(x, y, z, code);
                }
    }

    public int countBlocks() {
        int c = 0;
        for (short id : cell) if (id != 0) c++;
        return c;
    }

    @FunctionalInterface
    public interface CellConsumer {
        void accept(int x, int y, int z, int cellCode);
    }

    // =====================================================================================
    //  Form der Hülle
    // =====================================================================================

    /** "Aztec"-Plattenmuster der Refit-Hülle auf der Untertasse (polar, blockweise Zufall). */
    private static boolean aztec(double r, double theta) {
        int ring = (int) Math.floor(r / 4);
        int seg = (int) Math.floor((theta + Math.PI) * r / 4);
        int h = ring * 73856093 ^ seg * 19349663;
        h ^= h >>> 13;
        return ((ring + seg) & 1) == 0 ? (h & 3) != 0 : (h & 7) == 0;
    }

    private static boolean aztecXY(int a, int b) {
        int i = Math.floorDiv(a, 4), j = Math.floorDiv(b, 3);
        int h = i * 73856093 ^ j * 19349663;
        h ^= h >>> 13;
        return ((i + j) & 1) == 0 ? (h & 3) != 0 : (h & 7) == 0;
    }

    private static double prof(double[] p, double r) {
        if (r <= 0) return p[0];
        int i = (int) Math.floor(r);
        if (i >= p.length - 1) return p[p.length - 1];
        double f = r - i;
        return p[i] * (1 - f) + p[i + 1] * f;
    }

    private double saucerTop(double r) {
        if (model != null) return prof(topProf, r);
        double u = Math.min(1, r / sR);
        if (excelsior) {
            // flache Untertasse, kegelförmig zur Mitte ansteigend, Stufe um den Brückenbereich
            double top = sMid + s * (2 + 10 * (1 - u));
            if (r < 0.34 * sR) top += 2.5 * s;
            return top;
        }
        double top = sMid + s * (2.5 + (excelsior ? 7 : refit ? 9 : 8) * (1 - u * u));
        if (r < (excelsior ? 20 : refit ? 24 : 22) * s) top += (refit ? 3 : 2.5) * s; // erhöhter Aufbau um die Brücke
        return top;
    }

    private double saucerBottom(double r) {
        if (model != null) return prof(botProf, r);
        double u = Math.min(1, r / sR);
        return sMid - Math.max(1.6, s * ((excelsior ? 2 : 2.5) + (excelsior ? 3 : refit ? 6.5 : 5.5) * (1 - u * u)));
    }

    private double secondaryRadius(double x) {
        if (model != null) {
            int ix = (int) Math.round(x);
            if (ix < 0 || ix >= lowTop.length || Double.isNaN(lowTop[ix])) return -1;
            return (lowTop[ix] - lowBot[ix]) / 2;
        }
        double f = (x - eXf) / (eXr - eXf);
        if (f < 0 || f > 1) return -1;
        if (excelsior) {
            double[] h = excelsiorLower(x);
            return h == null ? -1 : (h[0] - h[1]) / 2;
        }
        if (refit) {
            // bauchige Front, kurzer zylindrischer Teil, starke Verjüngung nach hinten
            if (f < 0.15) return eR * (0.72 + 0.28 * Math.sin(f / 0.15 * Math.PI / 2));
            if (f < 0.45) return eR;
            return eR * (1 - 0.55 * Math.pow((f - 0.45) / 0.55, 1.3));
        }
        if (f < 0.12) return eR * (0.8 + 0.2 * f / 0.12);
        if (f < 0.6) return eR;
        return eR * (1 - 0.4 * (f - 0.6) / 0.4);
    }

    private double nacelleRadius(double x) {
        if (x < nX0 || x > nX1) return -1;
        double capEnd = nX0 + nR;
        if (x < capEnd) {
            double d = capEnd - x;
            return Math.sqrt(Math.max(0, nR * nR - d * d));
        }
        double tail = nX1 - (refit ? 9 : 6) * s;
        if (x > tail) return nR * (1 - 0.4 * (x - tail) / (nX1 - tail));
        return nR;
    }

    /**
     * Excelsior-Unterrumpf an der Längsposition x: {Oberkante, Unterkante, halbe Breite} oder null.
     * Oben flach, vorne abgerundet, nach hinten steigt die Unterseite an und läuft spitz aus.
     */
    private double[] excelsiorLower(double x) {
        double f = (x - eXf) / (eXr - eXf);
        if (f < 0 || f > 1) return null;
        double top = eY + eR;
        double depth = 2 * eR, half = eW;
        if (f < 0.06) {
            double kf = 0.55 + 0.45 * Math.sin(f / 0.06 * Math.PI / 2);
            depth *= kf;
            half *= kf;
        } else if (f > 0.35) {
            double t = (f - 0.35) / 0.65;
            depth *= 1 - 0.85 * t;
            half *= 1 - 0.8 * t;
        }
        return new double[] {top, top - depth, half};
    }

    /** Halbe Breite des Unterrumpfs auf Höhe y (Bootskiel: unten rund, oben leicht eingezogen). */
    private static double keelHalf(double[] h, double y) {
        double yc = (h[0] + h[1]) / 2, hh = Math.max(0.5, (h[0] - h[1]) / 2);
        double v = (y - yc) / hh;
        if (v > 1 || v < -1) return -1;
        if (v >= 0.2) return h[2] * (1 - 0.25 * (v - 0.2) / 0.8);
        double q = (0.2 - v) / 1.2;
        return h[2] * Math.sqrt(Math.max(0, 1 - q * q));
    }

    private double spineEnd() {
        return sCx + 0.75 * sR;
    }

    private double spineHalf(double x) {
        double dx = x - sCx;
        if (dx < 0) return Math.sqrt(Math.max(0, 144 * s * s - dx * dx)); // runde Nase an der Brücke
        return Math.min(14 * s, 12 * s + dx * 0.04);
    }

    /**
     * Excelsior-Klasse (NCC-1701-B) nach den Bauplänen: große flache Untertasse (so breit wie das Schiff)
     * mit Rücken ("Spine"), Impulsausleger der Variante B, breiter Rückenblock (unter der Untertasse
     * gerippt) über einem Unterrumpf in Bootskiel-Form mit eingelassener Deflektorschüssel,
     * waagerechter Pylonflügel, flache Gondeln auf Höhe der Untertasse.
     */
    private byte classifyExcelsior(int x, int y, int z) {
        double dx = x - sCx;
        double r = Math.sqrt(dx * dx + (double) z * z);
        double az = Math.abs(z);
        if (r <= 10) {
            int rel = y - bridgeFloor;
            if (rel >= -1 && rel <= 6) return BRIDGE;
            if (rel == 7 && r <= 8.5) return BRIDGE;
            if (rel == 8 && r <= 5.5) return BRIDGE;
        }
        if (r <= sR && y <= saucerTop(r) && y >= saucerBottom(r)) return SAUCER;
        // Impulsausleger der B hinten seitlich auf der Untertasse
        if (dx >= 0.5 * sR && dx <= 0.85 * sR && az >= 0.42 * sR && az <= 0.66 * sR - (dx - 0.5 * sR) * 0.3
            && y >= sMid && y <= sMid + 7 * s && r <= sR + 2) return SPONSON;
        // Rücken auf der Untertasse
        if (x >= sCx - 12 * s && x <= spineEnd()) {
            double top = sMid + (16 - 6 * Math.max(0, dx) / (spineEnd() - sCx)) * s;
            if (az <= spineHalf(x) && y >= sMid && y <= top) return SPINE;
        }
        // Rückenblock: unter der Untertasse gerippt, dahinter das graue Impulsdeck bis zum Pylonflügel
        double bx0 = 110 * s, bx1 = 300 * s;
        if (x >= bx0 && x <= bx1) {
            double t = (x - bx0) / (bx1 - bx0);
            double half = (30 - 8 * t) * s;
            if (az <= half && y >= eY + eR - 1 && y <= sMid + 3 * s) return x < sCx + sR - 3 ? COLUMN : NECKPLATE;
        }
        // Unterrumpf mit Deflektor
        double[] h = excelsiorLower(x);
        if (h != null) {
            double w = keelHalf(h, y);
            if (w >= 0 && az <= w) {
                double yc = (h[0] + h[1]) / 2;
                double rho = Math.hypot(y - yc, z);
                double dishR = Math.max(2.5, 7 * s);
                if (x < eXf + 3 * s && rho <= dishR) return DISH;
                if (x < eXf + 3 * s && rho <= dishR + 1.5) return ANTENNA;
                return SECONDARY;
            }
        }
        // Gondeln (breiter als hoch)
        double nr = nacelleRadius(x);
        if (nr > 0) {
            double kf = nr / nR;
            double q = Math.pow(Math.abs(az - nZ) / (nW * kf), 4) + Math.pow(Math.abs(y - nY) / (nR * kf), 4);
            if (q <= 1) return x < nX0 + nR * 0.9 ? BUSSARD : NACELLE;
        }
        // waagerechter Pylonflügel, außen zur Gondel hochgeknickt
        double xw = 304 * s, yw = sMid - 12 * s;
        double chord = Math.max(3, 12 * s - (az / nZ) * 3 * s);
        if (Math.abs(x - xw) <= chord) {
            boolean wing = az >= 18 * s && az <= nZ - 3 * s && Math.abs(y - yw) <= Math.max(1, 1.5 * s);
            boolean riser = Math.abs(az - (nZ - 4 * s)) <= Math.max(1, 2 * s) && y >= yw && y <= nY;
            if (wing || riser) return PYLON;
        }
        return NONE;
    }

    // =====================================================================================
    //  Form aus dem 3D-Modell
    // =====================================================================================

    private boolean ms(int x, int y, int z) {
        return model.solid(x, y, z + model.zc);
    }

    /** Lauf massiver Zellen in der Säule (x, z), der y0 enthält (bzw. der nächste innerhalb von ±4). */
    private int[] runAt(int x, int z, int y0) {
        for (int d = 0; d <= 4; d++)
            for (int y : new int[] {y0 + d, y0 - d}) {
                if (!ms(x, y, z)) continue;
                int lo = y, hi = y;
                while (ms(x, lo - 1, z)) lo--;
                while (ms(x, hi + 1, z)) hi++;
                return new int[] {lo, hi};
            }
        return null;
    }

    private static double median(java.util.List<Double> v) {
        if (v.isEmpty()) return Double.NaN;
        java.util.Collections.sort(v);
        return v.get(v.size() / 2);
    }

    /**
     * Vermisst das Modell: Untertasse (Mitte, Radius, Mittelebene, Ober-/Unterseitenprofil), Gondeln
     * (Lage, Größe, Anfang/Ende) und Unterrumpf (Ober-/Unterkante je x, Front, Heck, Achse).
     * Damit funktionieren Decks, Räume, Brücke, Farben und Beschriftung wie bei der berechneten Form.
     */
    private void fitToModel() {
        int gx = model.sx, gy = model.sy;
        int zmin = -model.zc, zmax = model.sz - 1 - model.zc;
        // halbe Breite je x
        int[] hw = new int[gx];
        for (int x = 0; x < gx; x++) {
            int m = -1;
            for (int z = zmin; z <= zmax; z++)
                for (int y = 0; y < gy && m < Math.abs(z); y++)
                    if (ms(x, y, z)) { m = Math.max(m, Math.abs(z)); break; }
            hw[x] = m;
        }
        int best = 0;
        for (int x = 0; x < gx * 0.6; x++) if (hw[x] > hw[best]) best = x;
        double sum = 0;
        int cnt = 0;
        for (int x = 0; x < gx * 0.6; x++) if (hw[x] >= hw[best] - 0.5) { sum += x; cnt++; }
        sCx = sum / cnt;
        // Halbachsen: quer = größte halbe Breite, längs = Abstand vom Bug zur Mitte
        int front = 0;
        while (front < gx - 1 && hw[front] < 0) front++;
        double az = hw[best] + 0.5, ax = sCx - front + 0.5;
        if (Math.abs(ax / az - 1) < 0.08) {
            sR = az; // praktisch rund
        } else {
            sR = Math.min(ax, az);
            kx = sR / ax;
            kz = sR / az;
        }
        // Mittelebene am Rand
        java.util.List<Double> mids = new java.util.ArrayList<>();
        for (double th : new double[] {0, 0.8, -0.8, 1.57, -1.57}) {
            int x = (int) Math.round(polX(0.85 * sR, th)), z = (int) Math.round(polZ(0.85 * sR, th));
            for (int y = 0; y < gy; y++)
                if (ms(x, y, z)) {
                    int[] run = runAt(x, z, y);
                    mids.add((run[0] + run[1]) / 2.0);
                    break;
                }
        }
        sMid = median(mids);
        // Profile der Untertasse
        int nr = (int) Math.ceil(sR) + 1;
        topProf = new double[nr + 1];
        botProf = new double[nr + 1];
        for (int r = 0; r <= nr; r++) {
            java.util.List<Double> tops = new java.util.ArrayList<>(), bots = new java.util.ArrayList<>();
            for (double th : new double[] {0, 0.5, -0.5, 1.0, -1.0, 1.57, -1.57}) {
                int x = (int) Math.round(polX(r, th)), z = (int) Math.round(polZ(r, th));
                int[] run = runAt(x, z, (int) Math.round(sMid));
                if (run != null) { tops.add((double) run[1]); bots.add((double) run[0]); }
            }
            topProf[r] = median(tops);
            botProf[r] = median(bots);
        }
        for (int r = 1; r <= nr; r++) {
            if (Double.isNaN(topProf[r])) topProf[r] = topProf[r - 1];
            if (Double.isNaN(botProf[r])) botProf[r] = botProf[r - 1];
        }
        // Gondeln: Querschnitte im hinteren Drittel; gültig ist ein Schnitt, in dem die äußerste
        // Gruppe (Steuerbord) frei vom Rumpf steht. Unter den dicken Schnitten wird der kompakteste
        // genommen (am wenigsten Pylon im Querschnitt).
        int n = (int) (gx * 0.97) - (int) (gx * 0.6) + 1;
        int[] sArea = new int[n], sLo = new int[n], sHi = new int[n], sBox = new int[n];
        int maxArea = 0;
        for (int xs = (int) (gx * 0.97); xs >= gx * 0.6; xs--) {
            boolean[] col = new boolean[zmax + 1];
            for (int z = 0; z <= zmax; z++)
                for (int y = 0; y < gy && !col[z]; y++) col[z] = ms(xs, y, z);
            int hi = zmax;
            while (hi > 0 && !col[hi]) hi--;
            int lo = hi;
            while (lo > 0 && col[lo - 1]) lo--;
            if (hi <= 0 || lo <= 3) continue; // hängt am Rumpf (Pylon) oder nichts da
            int area = 0, ylo = gy, yhi = -1;
            for (int z = lo; z <= hi; z++)
                for (int y = 0; y < gy; y++) if (ms(xs, y, z)) { area++; ylo = Math.min(ylo, y); yhi = Math.max(yhi, y); }
            int i = xs - (int) (gx * 0.6);
            sArea[i] = area;
            sLo[i] = lo;
            sHi[i] = hi;
            sBox[i] = (hi - lo + 1) * (yhi - ylo + 1);
            maxArea = Math.max(maxArea, area);
        }
        int bestX = -1, bestLo = 0, bestHi = 0;
        double bestFill = 0;
        for (int i = 0; i < n; i++) {
            if (sArea[i] < 0.25 * maxArea || sArea[i] == 0) continue;
            // kompakt (gut gefüllter Querschnitt) und nicht zu klein
            double fill = sArea[i] / (double) sBox[i] * Math.sqrt(sArea[i]);
            if (fill > bestFill) { bestFill = fill; bestX = i + (int) (gx * 0.6); bestLo = sLo[i]; bestHi = sHi[i]; }
        }
        if (bestX >= 0) {
            int xs = bestX;
            double cy = 0, cz = 0;
            int ylo = gy, yhi = 0, c = 0;
            for (int z = bestLo; z <= bestHi; z++)
                for (int y = 0; y < gy; y++)
                    if (ms(xs, y, z)) { cy += y; cz += z; c++; ylo = Math.min(ylo, y); yhi = Math.max(yhi, y); }
            nY = cy / c;
            nZ = cz / c;
            nR = Math.max(1.5, (yhi - ylo + 1) / 2.0);
            nW = Math.max(1.5, (bestHi - bestLo + 1) / 2.0);
            // Gondel nach vorne und hinten verfolgen (sie darf leicht geneigt sein)
            nX0 = trackNacelle(xs, -1, c);
            nX1 = trackNacelle(xs, 1, c);
        }
        // Unterrumpf: von unten durch die Mittellinie nach oben, solange er breit genug bleibt
        lowTop = new double[gx];
        lowBot = new double[gx];
        java.util.Arrays.fill(lowTop, Double.NaN);
        java.util.Arrays.fill(lowBot, Double.NaN);
        double thick = 0;
        int xThick = -1;
        eXf = Double.NaN;
        for (int x = 0; x < gx; x++) {
            int yb = -1;
            for (int y = 0; y < gy; y++) if (ms(x, y, 0)) { yb = y; break; }
            if (yb < 0 || x < sCx) continue; // vor der Untertassenmitte gibt es keinen Unterrumpf
            double r = Math.abs(x - sCx) * kx;
            double limit = r <= sR ? saucerBottom(r) - 1 : sMid + 1;
            if (yb >= limit - 2) continue; // nur die Untertasse
            int maxw = 0, y = yb;
            while (y + 1 < limit && ms(x, y + 1, 0)) {
                int w = 0;
                while (ms(x, y + 1, w + 1)) w++;
                maxw = Math.max(maxw, w);
                if (w < 0.35 * maxw && y - yb > 3) break;
                y++;
            }
            if (y - yb < 2) continue;
            lowBot[x] = yb;
            lowTop[x] = y;
            if (Double.isNaN(eXf)) eXf = x;
            eXr = x;
            if (y - yb > thick) { thick = y - yb; xThick = x; }
        }
        if (xThick >= 0) {
            eY = (lowTop[xThick] + lowBot[xThick]) / 2;
            eR = (lowTop[xThick] - lowBot[xThick]) / 2;
            int w = 0;
            while (ms(xThick, (int) Math.round(eY), w + 1)) w++;
            eW = w;
        }
    }

    /**
     * Verfolgt die Gondel vom Schnitt xs aus in Richtung dir, bis ihr Querschnitt unter 1/6 der
     * Ausgangsfläche fällt. Der Suchbereich wandert mit dem Schwerpunkt mit.
     * @return letzte x-Position, an der die Gondel noch vorhanden ist
     */
    private int trackNacelle(int xs, int dir, int area0) {
        double cy = nY, cz = nZ;
        int x = xs, last = xs;
        while (x + dir >= 0 && x + dir < model.sx) {
            x += dir;
            double sy = 0, sz = 0;
            int c = 0;
            for (int y = (int) (cy - nR - 2); y <= cy + nR + 2; y++)
                for (int z = (int) (cz - nW - 2); z <= cz + nW + 2; z++)
                    if (z > 0 && ms(x, y, z)) { sy += y; sz += z; c++; }
            if (c * 6 < area0) {
                if (Math.abs(x - last) > 2) break; // kleine Lücken überbrücken
                continue;
            }
            if (c > 3 * area0) break; // in Pylon oder Rumpf übergegangen
            cy = sy / c;
            cz = sz / c;
            if (Math.abs(cy - nY) > 2 * nR || Math.abs(cz - nZ) > nW) break;
            last = x;
        }
        return last;
    }

    /** Next Generation: Deflektorschüssel als Ellipse vorne am Unterrumpf. */

    /**
     * Deflektorschüssel auf der Front des Unterrumpfs (Blick von vorne). Lage und Größe ergeben sich
     * aus dem Querschnitt des vorderen Unterrumpfs, das Aussehen hängt vom Schiff ab:
     * NCC-1701 große Kupferschüssel mit Antenne, -A große blaue Schüssel mit dunklem Rahmen,
     * -B, -C, -E runde blaue Schüssel, -D breite ovale Kupferschüssel mit blau leuchtender Mitte.
     */
    private void paintDish() {
        // Silhouette des Unterrumpfs von vorne gesehen: Breite je Höhe; der schmale Hals darüber
        // zählt nicht mit (Breite unter 40 % der größten Breite)
        int[] wz = new int[sizeY];
        java.util.Arrays.fill(wz, -1);
        for (int y = 0; y < sizeY; y++)
            for (int z = -zOffset; z <= zOffset; z++)
                if (frontHull(y, z, false) >= 0) wz[y] = Math.max(wz[y], Math.abs(z));
        // Typische Rumpfbreite = 75-%-Wert (einzelne breite Zeilen mit Pylonen zählen nicht);
        // der Rumpf ist der längste zusammenhängende Höhenbereich mit mind. 40 % davon
        int[] pos = java.util.Arrays.stream(wz).filter(w -> w > 0).sorted().toArray();
        if (pos.length == 0) return;
        int wmax = pos[(int) (pos.length * 0.75)];
        int ylo = 0, yhi = -1;
        for (int y = 0; y < sizeY; ) {
            if (wz[y] < 0.4 * wmax) { y++; continue; }
            int y1 = y;
            while (y1 + 1 < sizeY && wz[y1 + 1] >= 0.4 * wmax) y1++;
            if (y1 - y > yhi - ylo) { ylo = y; yhi = y1; }
            y = y1 + 1;
        }
        if (yhi < 0) return;
        double yc = (ylo + yhi) / 2.0, hy = (yhi - ylo + 1) / 2.0, hz = wmax + 0.5, m = Math.min(hy, hz);
        double cy = yc, ry, rz, inner = 0, ring = 0, depth;
        boolean concave = false;
        ShipPart outer = ShipPart.DEFLECTOR_DISH_BLUE, core = null, ringPart = ShipPart.HULL_DARK;
        switch (design) {
            // Originalserie: gewölbte Kupferschüssel (Rand vorne) in einem Hüllenring, Antenne in der Mitte
            case NCC_1701 -> { ry = rz = 0.6 * m; ring = 0.75 * m; outer = ShipPart.DEFLECTOR_DISH; ringPart = ShipPart.HULL_PLATING; depth = 0.3; concave = true; }
            // Refit: große blau leuchtende Schüssel, leicht nach vorne gewölbt, dunkler Rahmen
            case NCC_1701_A -> { ry = rz = 0.72 * m; ring = 0.84 * m; depth = 0.15; }
            // Excelsior-B: hohes blaues Oval unten am Kielrumpf
            case NCC_1701_B -> { cy = yc - 0.15 * hy; ry = 0.5 * m; rz = 0.34 * m; depth = 0.12; }
            case NCC_1701_C -> { ry = rz = 0.58 * m; depth = 0.1; }
            // Galaxy: breites flaches Oval, Kupferrand, blau leuchtender Ring, Kupfermitte
            case NCC_1701_D -> { cy = yc + 0.4 * hy; ry = 0.3 * hy; rz = 0.45 * hz; outer = ShipPart.DEFLECTOR_DISH; core = ShipPart.DEFLECTOR_DISH_BLUE; inner = 0.85; depth = 0; }
            case NCC_1701_E -> { ry = rz = 0.5 * m; depth = 0.12; }
            default -> { return; }
        }
        ry = Math.max(1.5, ry);
        rz = Math.max(1.5, rz);
        double rmin = Math.min(ry, rz), dmax = depth * rmin;
        double reach = Math.max(ring, Math.max(ry, rz)) + 1;
        // Die Schüssel ist eine Scheibe auf einer senkrechten Ebene an der vordersten Stelle der
        // Rumpffront; sie läuft nicht auf schräge Seitenflächen herum
        List<Integer> fronts = new ArrayList<>();
        for (int y = (int) Math.floor(cy - ry); y <= cy + ry; y++)
            for (int z = (int) -Math.ceil(rz); z <= rz; z++) {
                if (Math.hypot((y - cy) / ry, z / rz) > 1) continue;
                int x = frontHull(y, z, true);
                if (x >= 0) fronts.add(x);
            }
        if (fronts.isEmpty()) return;
        java.util.Collections.sort(fronts);
        int xp = fronts.get(fronts.size() / 2); // Ebene der Scheibe: mittlere Lage der Rumpffront
        int maxFill = (int) Math.ceil(Math.max(2, rmin));
        int frontX = Integer.MAX_VALUE;
        for (int y = (int) Math.floor(cy - reach); y <= cy + reach; y++)
            for (int z = (int) -Math.ceil(reach); z <= reach; z++) {
                double e = Math.hypot((y - cy) / ry, z / rz);
                boolean inRing = ring > 0 && e > 1 && Math.hypot(y - cy, z) <= ring;
                if (e > 1 && !inRing) continue;
                int x = frontHull(y, z, true);
                if (x < 0 || x > xp + maxFill) continue; // zu weit hinten = Seitenfläche
                ShipPart part = inRing ? ringPart
                    : core != null && e <= inner && e > 0.5 ? core : outer;
                // Wölbung (konkav: Rand weiter vorne, konvex: Mitte weiter vorne)
                int d = inRing ? (int) Math.round(concave ? dmax + 1 : dmax * 0.3)
                    : (int) Math.round(dmax * (concave ? e : 1 - e * e));
                int face = Math.min(xp, x) - d; // Vorderseite der Scheibe (mind. die Ebene)
                for (int k = face; k <= x; k++) set(k, y, z, part);
                if (Math.abs(y - cy) < 0.5 && z == 0) frontX = Math.min(face, x);
            }
        // Originalserie: Antenne in der Mitte der Schüssel
        if (design == ShipDesign.NCC_1701 && frontX != Integer.MAX_VALUE) {
            int len = (int) Math.max(2, Math.round(0.6 * m));
            for (int k = 1; k <= len; k++) set(frontX - k, (int) Math.round(cy), 0, ShipPart.HULL_DARK);
        }
    }

    /**
     * Vorderste Hüllenzelle des Unterrumpfs (oder Halses) bei (y, z) von vorne gesehen, sonst -1.
     * @param pylon auch Pylon-Zellen zählen (manche Rumpffronten gelten im Modell als Pylon)
     */
    private int frontHull(int y, int z, boolean pylon) {
        for (int x = (int) Math.ceil(sCx); x < sizeX; x++) {
            byte c = compAt(x, y, z);
            if (c == NONE) continue;
            return (c == SECONDARY || c == NECK || (pylon && c == PYLON) || c == DISH || c == ANTENNA) && hull[idx(x, y, z)] ? x : -1;
        }
        return -1;
    }

    /** Senkrechte Heckfläche (mind. 3 Blöcke hoch) – bei der Excelsior sitzen dort die Impulstriebwerke. */
    private boolean rearWall(int x, int y, int z) {
        return compAt(x + 1, y, z) == NONE && compAt(x + 1, y + 1, z) == NONE && compAt(x + 1, y - 1, z) == NONE
            && compAt(x, y + 1, z) != NONE && compAt(x, y - 1, z) != NONE && x > sCx + 0.3 * sR;
    }

    private boolean inEngineeringBox(int x, int y, int z) {
        int[] b = engineeringBox();
        return x >= b[0] - 1 && x <= b[1] + 1 && y >= b[2] - 1 && y <= b[3] + 1 && Math.abs(z) <= 7;
    }

    /** Lage des Maschinenraums: {x von, x bis, Boden, Decke}. */
    private int[] engineeringBox() {
        if (nx) {
            // NX-01: Maschinenraum hinten in der Untertasse zwischen den Auslegern
            int x0 = (int) Math.round(sCx + 0.4 * sR);
            int yF = (int) Math.round(sMid) - 4;
            return new int[] {x0 - 10, x0 + 10, yF, yF + 7};
        }
        double f0 = eXf + (refit || excelsior || tng ? 0.15 : 0.12) * (eXr - eXf), f1 = eXf + (refit ? 0.45 : excelsior || tng ? 0.5 : 0.6) * (eXr - eXf);
        int x0 = (int) Math.round((f0 + f1) / 2);
        int yF = (int) Math.round(eY) - 3;
        return new int[] {x0 - 10, x0 + 10, yF, yF + 7};
    }

    /** Bauteil-Zuordnung für die Form aus dem 3D-Modell. */
    private byte classifyModel(int x, int y, int z) {
        double dx = x - sCx;
        double rb = Math.sqrt(dx * dx + (double) z * z); // echter Abstand (Brückenmodul)
        double r = rad(x, z);
        double az = Math.abs(z);
        if (rb <= bR) {
            int rel = y - bridgeFloor;
            if (rel >= -1 && rel <= 6) return BRIDGE;
            if (rel == 7 && rb <= bR - 1.5) return BRIDGE;
            if (rel == 8 && rb <= bR - 4.5) return BRIDGE;
        }
        if (inEngineeringBox(x, y, z) && !ms(x, y, z) && s < 0.75) return SECONDARY; // Platz für den Maschinenraum
        if (!ms(x, y, z)) return NONE;
        // Alles über der Unterseite gehört zur Untertasse (auch Aufbauten, die höher als das Profil sind)
        if (r <= sR + 0.5 && y >= saucerBottom(r) - 0.5) return SAUCER;
        // NX-01: Gondel als Ellipse, damit der Pylon darunter nicht mitgezählt wird
        boolean inNacelle = nx ? Math.hypot((az - nZ) / (nW + 1), (y - nY) / (nR + 1)) <= 1.1
            : Math.abs(az - nZ) <= nW + 1.5 && Math.abs(y - nY) <= nR + 1.5;
        if (x >= nX0 - 1 && inNacelle)
            return !refit && x < nX0 + nR * 0.9 ? BUSSARD : NACELLE; // Refit: graue Kappe mit seitlichen Fenstern
        if (x >= 0 && x < lowTop.length && !Double.isNaN(lowTop[x]) && y >= lowBot[x] - 0.5 && y <= lowTop[x] + 0.5)
            return SECONDARY;
        return az <= 6 * s + 2 ? NECK : PYLON;
    }

    private byte classify(int x, int y, int z) {
        if (model != null) return classifyModel(x, y, z);
        if (excelsior) return classifyExcelsior(x, y, z);
        double dx = x - sCx;
        double r = Math.sqrt(dx * dx + (double) z * z);

        // Brückenmodul (immer Originalgröße: Radius 10, Kuppel oben)
        if (r <= 10) {
            int rel = y - bridgeFloor;
            if (rel >= -1 && rel <= 6) return BRIDGE;
            if (rel == 7 && r <= 8.5) return BRIDGE;
            if (rel == 8 && r <= 5.5) return BRIDGE;
        }
        // Untertassensektion (linsenförmig)
        if (r <= sR && y <= saucerTop(r) && y >= saucerBottom(r)) return SAUCER;
        // Sensorkuppel auf der Unterseite
        double domeR = Math.max(2, 7 * s);
        double domeY = saucerBottom(0) + 3 * s;
        if (dx * dx + (y - domeY) * (y - domeY) + (double) z * z <= domeR * domeR && y < saucerBottom(0) + 1) return SENSOR;

        // Hals ("Dorsal"): schräge Verbindung zwischen Untertasse und Sekundärhülle
        double neckTopX = sCx + 30 * s, neckTopY = saucerBottom(30 * s) + 1;
        double neckBotX = eXf + (refit ? 22 : 30) * s, neckBotY = eY + eR * 0.7;
        if (y >= neckBotY && y <= neckTopY) {
            double t = (neckTopY - y) / (neckTopY - neckBotY);
            double xc = neckTopX + t * (neckBotX - neckTopX);
            double chord = Math.max(4, 13 * s);
            double w = Math.max(1.5, (refit ? 5 : 4) * s * (1 - 0.25 * t));
            if (Math.abs(x - xc) <= chord && Math.abs(z) <= w) return NECK;
        }

        // Deflektorschüssel + Antenne
        double rho = Math.sqrt((y - eY) * (y - eY) + (double) z * z);
        double dishR = eR * (refit ? 0.6 : 0.7);
        double dishDepth = refit ? 1 : Math.max(1, 3 * s);
        if (rho <= dishR && x < eXf && x >= eXf - 1 - dishDepth * (rho / dishR)) return DISH;
        if (!refit && rho <= Math.max(0.5, s) && x < eXf && x >= eXf - Math.max(4, 10 * s)) return ANTENNA;
        if (refit && rho > dishR && rho <= eR * 0.72 && x >= eXf - 1 && x < eXf + 1) return ANTENNA; // Rahmen um die Schüssel

        // Sekundärhülle mit Shuttlehangar
        double er = secondaryRadius(x);
        if (er > 0 && rho <= er) return x > eXr - (refit ? 28 : 22) * s ? SHUTTLEBAY : SECONDARY;

        // Pylonen + Gondeln (symmetrisch)
        double az = Math.abs(z);
        double nr = nacelleRadius(x);
        double nd = refit
            // Refit: kantigere Gondel (abgerundetes Rechteck, schmaler als hoch)
            ? nR * Math.pow(Math.pow(Math.abs(az - nZ) / (0.75 * nR), 4) + Math.pow(Math.abs(y - nY) / nR, 4), 0.25)
            : Math.sqrt((az - nZ) * (az - nZ) + (y - nY) * (y - nY));
        if (nr > 0 && nd <= nr) return !refit && x < nX0 + nR * 0.9 ? BUSSARD : NACELLE;

        double p0z = refit ? eR * 0.5 : 0, p0y = eY + eR * (refit ? 0.4 : 0.5), p1z = nZ, p1y = nY;
        double lz = p1z - p0z, ly = p1y - p0y, len = Math.sqrt(lz * lz + ly * ly);
        double t = ((az - p0z) * lz + (y - p0y) * ly) / (len * len);
        if (t >= 0 && t <= 1) {
            double perp = Math.abs((az - p0z) * ly - (y - p0y) * lz) / len;
            double xc = refit ? (198 + 16 * t) * s : (212 + 10 * t) * s;
            double chord = Math.max(2.5, ((refit ? 11 : 12) - (refit ? 2 : 3) * t) * s);
            if (perp <= Math.max(1.0, (refit ? 2.8 : 1.6) * s) && Math.abs(x - xc) <= chord) return PYLON;
        }
        return NONE;
    }

    private void buildShape() {
        for (int x = 0; x < sizeX; x++)
            for (int y = 0; y < sizeY; y++)
                for (int z = -zOffset; z <= zOffset; z++)
                    comp[idx(x, y, z)] = classify(x, y, z);

        for (int x = 0; x < sizeX; x++)
            for (int y = 0; y < sizeY; y++)
                for (int z = -zOffset; z <= zOffset; z++) {
                    int i = idx(x, y, z);
                    if (comp[i] == NONE) continue;
                    hull[i] = compAt(x + 1, y, z) == NONE || compAt(x - 1, y, z) == NONE
                        || compAt(x, y + 1, z) == NONE || compAt(x, y - 1, z) == NONE
                        || compAt(x, y, z + 1) == NONE || compAt(x, y, z - 1) == NONE;
                }
    }

    // =====================================================================================
    //  Hüllenmaterial, Fenster und Decks
    // =====================================================================================

    private void skinAndDecks() {
        ShipPart plating = ShipPart.HULL_PLATING;
        ShipPart panel = ShipPart.HULL_PANEL;
        ShipPart dark = ShipPart.HULL_DARK;
        ShipPart window = ShipPart.HULL_WINDOW;
        // Die Galaxy-Klasse (D) hat eine kupferfarbene Schüssel, C und E eine blaue
        ShipPart dish = refit || excelsior || (tng && design != ShipDesign.NCC_1701_D) ? ShipPart.DEFLECTOR_DISH_BLUE : ShipPart.DEFLECTOR_DISH;
        ShipPart bussard = nx ? ShipPart.HULL_DARK : excelsior ? ShipPart.BUSSARD_BLUE : refit || tng ? ShipPart.BUSSARD_RED : ShipPart.BUSSARD_COLLECTOR;
        ShipPart grille = ShipPart.NACELLE_GRILLE;
        ShipPart floor = ShipPart.DECK_FLOOR;
        ShipPart light = ShipPart.CEILING_LIGHT;
        ShipPart wall = ShipPart.CORRIDOR_WALL;
        ShipPart air = ShipPart.AIR;

        for (int x = 0; x < sizeX; x++)
            for (int y = 0; y < sizeY; y++)
                for (int z = -zOffset; z <= zOffset; z++) {
                    int i = idx(x, y, z);
                    byte c = comp[i];
                    if (c == NONE) continue;
                    int deckY = Math.floorMod(y - floorBase, DECK_HEIGHT);
                    double dx = x - sCx;
                    double r = rad(x, z);

                    if (hull[i]) {
                        boolean openSide = compAt(x + 1, y, z) == NONE || compAt(x - 1, y, z) == NONE
                            || compAt(x, y, z + 1) == NONE || compAt(x, y, z - 1) == NONE;
                        boolean openUpDown = compAt(x, y + 1, z) == NONE || compAt(x, y - 1, z) == NONE;
                        ShipPart st = plating;
                        switch (c) {
                            case SAUCER, SENSOR -> {
                                if (openSide && !openUpDown && deckY == 2 && Math.floorMod(x * 7 + z * 13, 5) != 0) {
                                    st = window;
                                } else if (c == SENSOR) {
                                    st = panel;
                                } else {
                                    // dezente Plattenringe wie beim Original
                                    boolean ring = Math.abs(r - 0.55 * sR) < 0.5 || Math.abs(r - 0.82 * sR) < 0.5
                                        || Math.abs(r - 0.32 * sR) < 0.5;
                                    if (ring) st = panel;
                                    if (aztecHull && aztec(r, Math.atan2(z * kz, dx * kx))) st = ShipPart.HULL_AZTEC;
                                    // Excelsior: dunkler petrolfarbener Ring auf der Oberseite
                                    if (excelsior && compAt(x, y + 1, z) == NONE && r > 0.87 * sR && r < 0.93 * sR) st = ShipPart.HULL_TEAL;
                                    if ((excelsior || tng || nx) && model != null && rearWall(x, y, z) && y > sMid + 1) st = ShipPart.IMPULSE_ENGINE;
                                    if (excelsior && compAt(x, y + 1, z) == NONE && Math.abs(r - 0.56 * sR) < 0.8) st = ShipPart.HULL_PENNANT;
                                    // Refit: rot leuchtende Impulstriebwerke hinten am erhöhten Aufbau
                                    if (refit && dx > 0 && Math.abs(z) <= 8 * s && Math.abs(r - 24 * s) < 1.3 && openSide)
                                        st = ShipPart.IMPULSE_ENGINE;
                                }
                            }
                            case BRIDGE -> st = y >= bridgeFloor + 6 ? panel : plating;
                            case NECK, PYLON -> st = (excelsior || tng || nx) && model != null && rearWall(x, y, z) ? ShipPart.IMPULSE_ENGINE
                                : aztecHull && aztecXY(x, y + z) ? ShipPart.HULL_AZTEC
                                : Math.floorMod(x, 6) == 0 ? panel : plating;
                            case SECONDARY -> {
                                if (excelsior && compAt(x, y + 1, z) == NONE && Math.floorMod(x + Math.abs(z), 16) < 3) {
                                    st = ShipPart.HULL_TEAL; // schräge Streifen oben auf dem Unterrumpf
                                    break;
                                }
                                if (openSide && !openUpDown && deckY == 2 && Math.floorMod(x * 5 + y, 4) == 0 && Math.abs(z) > 1) st = window;
                                else if (aztecHull && aztecXY(x, y * 3 + z)) st = ShipPart.HULL_AZTEC;
                                else if (Math.floorMod(x, 8) == 0) st = panel;
                            }
                            case SHUTTLEBAY -> {
                                double rho = Math.sqrt((y - eY) * (y - eY) + (double) z * z);
                                boolean rearFace = compAt(x + 1, y, z) == NONE;
                                if (rearFace && Math.abs(z) < secondaryRadius(x) * 0.55 && y < eY + rho * 0.5) st = dark;
                                else if (Math.floorMod(x, 8) == 0) st = panel;
                            }
                            case SPINE -> {
                                boolean topFace = compAt(x, y + 1, z) == NONE;
                                if (compAt(x + 1, y, z) == NONE && x >= spineEnd() - 1.5 && Math.abs(z) <= 9 * s)
                                    st = ShipPart.IMPULSE_ENGINE; // Heck des Rückens
                                else if (topFace && Math.abs(z) >= spineHalf(x) - 3) st = ShipPart.HULL_TEAL; // Hufeisen-Streifen
                                else if (aztecXY(x, z)) st = ShipPart.HULL_AZTEC;
                            }
                            case NECKPLATE -> {
                                if (openSide && !openUpDown && deckY == 2 && Math.floorMod(x * 7 + z * 13, 5) != 0) st = window;
                                else if (aztecXY(x, z)) st = ShipPart.HULL_AZTEC;
                            }
                            case COLUMN -> st = Math.floorMod(y, 2) == 0 ? dark : panel; // gerippt
                            case SPONSON -> st = compAt(x + 1, y, z) == NONE ? ShipPart.IMPULSE_ENGINE : plating;
                            case FIN -> st = dark;
                            case DISH -> st = dish;
                            case ANTENNA -> st = dark;
                            case BUSSARD -> st = bussard;
                            case NACELLE -> {
                                boolean inner = Math.abs(z) < nZ - nR * 0.4;
                                boolean mid = Math.abs(y - nY) <= Math.max(1, nR * 0.35);
                                boolean side = Math.abs(Math.abs(z) - nZ) > nR * 0.45;
                                if (x > nX1 - 2) st = dark;
                                else if (excelsior && x > nX1 - 4 * s) st = ShipPart.IMPULSE_ENGINE; // rote Spitze hinten
                                else if (excelsior && y > nY + nR * 0.5 && Math.abs(Math.abs(z) - nZ) <= nW * 0.55)
                                    st = grille; // blauer Streifen oben
                                else if (excelsior && Math.abs(y - nY) <= 0.6) st = ShipPart.HULL_TEAL; // Seitenband
                                else if (refit && side && Math.abs(y - nY) <= nR * (model != null ? 0.3 : 0.45)
                                    && x >= nX0 + nR * 0.8 && x <= nX0 + nR * (model != null ? 1.8 : 2.4))
                                    st = bussard; // Refit: rötliche ovale Bussard-Fenster seitlich vorne
                                else if (refit && x < nX0 + nR * 2.6) st = plating; // graue Frontkappe
                                else if (tng && x < nX0 + nR * 1.2) st = bussard; // TNG: rote Bussard-Kuppel vorne
                                else if (nx && x < nX0 + nR * 0.8) st = dark; // NX: dunkle Gondelspitze
                                else if ((inner || refit || tng || nx) && mid && x < nX1 - (refit ? 9 : 6) * s) st = grille; // Refit/TNG/NX: Gitter beidseitig
                                else if ((refit || tng) && aztecXY(x, y)) st = ShipPart.HULL_AZTEC;
                                else if (Math.floorMod(x, 10) == 0) st = panel;
                            }
                            default -> {}
                        }
                        set(x, y, z, st);
                        continue;
                    }

                    // Innenraum
                    ShipPart st = air;
                    boolean decks = c == SAUCER || c == SECONDARY || c == SENSOR || c == NECKPLATE || c == COLUMN;
                    if (decks) {
                        if (deckY == 0) {
                            st = floor;
                        } else if (deckY == DECK_HEIGHT - 1 && Math.floorMod(x, 5) == 0 && Math.floorMod(z, 5) == 0) {
                            st = light;
                        } else if (c == SAUCER && s >= 0.45) {
                            // Ringkorridore mit Türen
                            boolean ringWall = Math.abs(r - 0.5 * sR) < 0.5 || Math.abs(r - 0.3 * sR) < 0.5;
                            if (ringWall) {
                                double arc = r * (Math.atan2(z, dx) + Math.PI);
                                boolean door = Math.floorMod((int) Math.floor(arc), 12) < 2 && deckY <= 2;
                                if (!door) st = wall;
                            }
                        }
                    } else if (c == SHUTTLEBAY) {
                        if (y <= eY - secondaryRadius(x) * 0.5) st = floor;
                    }
                    set(x, y, z, st);
                }
    }

    // ---- Schriften (Zeilen von oben nach unten) ----
    private static final java.util.Map<Character, String[]> FONT5x7 = new java.util.HashMap<>();
    private static final java.util.Map<Character, String[]> FONT3x5 = new java.util.HashMap<>();
    private static final java.util.Map<Character, String[]> FONT6x9 = new java.util.HashMap<>();
    static {
        FONT6x9.put('N', new String[] {"100001", "110001", "101001", "101001", "100101", "100101", "100011", "100001", "100001"});
        FONT6x9.put('C', new String[] {"011110", "100001", "100000", "100000", "100000", "100000", "100000", "100001", "011110"});
        FONT6x9.put('-', new String[] {"0000", "0000", "0000", "0000", "1111", "0000", "0000", "0000", "0000"});
        FONT6x9.put('1', new String[] {"001100", "011100", "101100", "001100", "001100", "001100", "001100", "001100", "111111"});
        FONT6x9.put('7', new String[] {"111111", "000001", "000010", "000100", "000100", "001000", "001000", "001000", "001000"});
        FONT6x9.put('A', new String[] {"001100", "010010", "100001", "100001", "111111", "100001", "100001", "100001", "100001"});
        FONT6x9.put('B', new String[] {"111110", "100001", "100001", "100001", "111110", "100001", "100001", "100001", "111110"});
        FONT6x9.put('0', new String[] {"011110", "100001", "100011", "100101", "101001", "110001", "100001", "100001", "011110"});
        FONT6x9.put('X', new String[] {"100001", "100001", "010010", "010010", "001100", "010010", "010010", "100001", "100001"});
        FONT6x9.put('D', new String[] {"111100", "100010", "100001", "100001", "100001", "100001", "100001", "100010", "111100"});
        FONT6x9.put('E', new String[] {"111111", "100000", "100000", "100000", "111110", "100000", "100000", "100000", "111111"});
        FONT5x7.put('N', new String[] {"10001", "11001", "10101", "10011", "10001", "10001", "10001"});
        FONT5x7.put('C', new String[] {"01110", "10001", "10000", "10000", "10000", "10001", "01110"});
        FONT5x7.put('-', new String[] {"000", "000", "000", "111", "000", "000", "000"});
        FONT5x7.put('1', new String[] {"00100", "01100", "00100", "00100", "00100", "00100", "01110"});
        FONT5x7.put('7', new String[] {"11111", "00001", "00010", "00100", "01000", "01000", "01000"});
        FONT5x7.put('0', new String[] {"01110", "10001", "10011", "10101", "11001", "10001", "01110"});
        FONT3x5.put('N', new String[] {"111", "101", "101", "101", "101"});
        FONT3x5.put('C', new String[] {"111", "100", "100", "100", "111"});
        FONT3x5.put('-', new String[] {"00", "00", "11", "00", "00"});
        FONT3x5.put('1', new String[] {"010", "110", "010", "010", "111"});
        FONT3x5.put('7', new String[] {"111", "001", "010", "010", "010"});
        FONT3x5.put('0', new String[] {"111", "101", "101", "101", "111"});
        FONT3x5.put('A', new String[] {"010", "101", "111", "101", "101"});
        FONT3x5.put('B', new String[] {"110", "101", "110", "101", "110"});
        FONT3x5.put('X', new String[] {"101", "101", "010", "101", "101"});
        FONT3x5.put('D', new String[] {"110", "101", "101", "101", "110"});
        FONT3x5.put('U', new String[] {"101", "101", "101", "101", "111"});
        FONT3x5.put('S', new String[] {"111", "100", "111", "001", "111"});
        FONT3x5.put('E', new String[] {"111", "100", "110", "100", "111"});
        FONT3x5.put('T', new String[] {"111", "010", "010", "010", "010"});
        FONT3x5.put('R', new String[] {"110", "101", "110", "101", "101"});
        FONT3x5.put('P', new String[] {"110", "101", "110", "100", "100"});
        FONT3x5.put('I', new String[] {"111", "010", "010", "010", "111"});
        FONT3x5.put('.', new String[] {"0", "0", "0", "0", "1"});
        FONT3x5.put(' ', new String[] {"0", "0", "0", "0", "0"});
    }

    private static int textWidth(String text, java.util.Map<Character, String[]> font) {
        int w = 0;
        for (char ch : text.toCharArray()) w += font.get(ch)[0].length() + 1;
        return w - 1;
    }

    /**
     * Kennung auf der Oberseite der Untertasse wie beim Original (lesbar von vorne, Oberkante der
     * Buchstaben zeigt nach achtern): "NCC-1701" mit Abstand im Bogen entlang des vorderen Rands,
     * darüber klein und gerade "USS ENTERPRISE".
     */
    private void registryLettering() {
        boolean big = s >= 0.75;
        var font = big ? FONT6x9 : FONT3x5;
        int spacing = big ? (refit ? 2 : 4) : (refit ? 1 : 2);
        // gedrehte Buchstaben etwas größer, damit sie sauber bleiben; bei TNG-Schiffen riesig wie im Original
        double scale = big ? GLYPH_SCALE * (tng ? 1.6 : 1.0) : 1.0;
        int h = font.get('N').length;
        double ra = sR * (excelsior ? 0.7 : big ? 0.8 : 0.78);
        String reg = design.registry;
        int total = 0;
        for (char ch : reg.toCharArray()) total += font.get(ch)[0].length() + spacing;
        total -= spacing;
        total = (int) Math.round(total * scale);
        // Bogen: erster Buchstabe auf Steuerbord (+z), dann Richtung Backbord.
        // Zweiter Durchgang: dieselbe Kennung auf der Unterseite (gespiegelt, lesbar von unten)
        for (int pass = 0; pass < 2; pass++) {
        underside = pass == 1;
        double pos = -total / 2.0;
        for (char ch : reg.toCharArray()) {
            String[] g = font.get(ch);
            int w = g[0].length();
            if (big) {
                drawGlyphOnArc(g, -(pos + w * scale / 2.0) / ra, ra, scale);
            } else {
                // kleine Schrift gedreht wäre unleserlich -> gerade Zeile vor der Brücke
                int xTop = (int) Math.round(polX(0.55 * sR, 0)) + h / 2;
                drawGlyphTop(g, xTop, (int) Math.round(-pos), 1);
            }
            pos += (w + spacing) * scale;
        }
        }
        underside = false;
        if (refit) {
            // Refit: Kennung ein zweites Mal klein hinter der Brücke
            int px = big ? 2 : 1;
            int rw = textWidth(reg, FONT3x5) * px;
            int xTop = (int) Math.round(sCx) + bR + 3 + 5 * px;
            int col = 0;
            for (char ch : reg.toCharArray()) {
                String[] g = FONT3x5.get(ch);
                drawGlyphTop(g, xTop, rw / 2 - col, px);
                col += (g[0].length() + 1) * px;
            }
        }
        // "USS ENTERPRISE" klein und gerade zwischen Brücke und Kennung, wenn genug Platz ist
        String name = nx ? "ENTERPRISE" : "USS ENTERPRISE";
        int nw = textWidth(name, FONT3x5);
        double nx = 0.5 * sR;
        if (Math.hypot(nx, nw / 2.0) > ra - h * scale / 2.0 - 1 || nx - 3 < 11) return;
        int top = (int) Math.round(polX(nx, 0)) + 2;
        int col = 0;
        for (char ch : name.toCharArray()) {
            String[] g = FONT3x5.get(ch);
            drawGlyphTop(g, top, nw / 2 - col, 1);
            col += g[0].length() + 1;
        }
    }

    /**
     * Zeichnet ein Zeichen gedreht auf den Bogen mit Radius {@code ra} um die Untertassenmitte:
     * die Oberkante zeigt zur Mitte, die Leserichtung folgt dem Bogen (wie beim Original).
     */
    private static final double GLYPH_SCALE = 1.4;

    private void drawGlyphOnArc(String[] g, double theta, double ra, double scale) {
        int h = g.length, w = g[0].length();
        double px = polX(ra, theta), pz = polZ(ra, theta);
        // Normale der (ggf. ovalen) Bahn: Gradient von rad() zeigt nach außen
        double gx = Math.cos(theta) * kx, gz = Math.sin(theta) * kz, gl = Math.hypot(gx, gz);
        double nx = gx / gl, nz = -gz / gl;                   // nach innen = Oberkante
        double tx = nz, tz = -nx;                              // Leserichtung
        int reach = (int) Math.ceil(Math.hypot(w, h) * scale / 2) + 1;
        for (int x = (int) px - reach; x <= (int) px + reach; x++)
            for (int z = (int) pz - reach; z <= (int) pz + reach; z++) {
                // 2x2 Abtastpunkte je Block, damit gedrehte Linien geschlossen bleiben
                boolean hit = false;
                for (int sx = 0; sx < 2 && !hit; sx++)
                    for (int sz = 0; sz < 2 && !hit; sz++) {
                        double dx = x + (sx - 0.5) * 0.5 - px, dz = z + (sz - 0.5) * 0.5 - pz;
                        int c = (int) Math.floor((dx * tx + dz * tz) / scale + w / 2.0);
                        int r = (int) Math.floor(-(dx * nx + dz * nz) / scale + h / 2.0);
                        hit = c >= 0 && c < w && r >= 0 && r < h && g[r].charAt(c) == '1';
                    }
                if (hit) markTop(x, z, ShipPart.HULL_MARKING);
            }
    }

    /** Zeichnet ein Zeichen auf die Oberseite: Zeile 0 bei xTop (achtern), Spalten Richtung Backbord (-z). */
    private void drawGlyphTop(String[] g, int xTop, int zLeft, int px) {
        for (int row = 0; row < g.length; row++)
            for (int c = 0; c < g[row].length(); c++) {
                if (g[row].charAt(c) != '1') continue;
                for (int ax = 0; ax < px; ax++)
                    for (int az = 0; az < px; az++)
                        markTop(xTop - row * px - ax, zLeft - c * px - az, ShipPart.HULL_MARKING);
            }
    }

    /** Schreiben die Zeichenfunktionen gerade auf die Unterseite (an der Längsachse gespiegelt)? */
    private boolean underside;

    private void markTop(int x, int z, ShipPart mark) {
        if (underside) {
            // von unten gesehen ist links und rechts vertauscht
            for (int y = 0; y < sizeY; y++) {
                if (!inBounds(x, y, -z)) return;
                int i = idx(x, y, -z);
                if (comp[i] == NONE) continue;
                if (comp[i] == SAUCER && hull[i]) set(x, y, -z, mark);
                return;
            }
            return;
        }
        for (int y = sizeY - 1; y >= 0; y--) {
            if (!inBounds(x, y, z)) return;
            int i = idx(x, y, z);
            if (comp[i] == NONE) continue;
            if (comp[i] == SAUCER && hull[i]) set(x, y, z, mark);
            return;
        }
    }

    /** Färbt die äußerste Hüllenzelle an (x, y) auf der gewählten Seite (+1 Steuerbord, -1 Backbord). */
    private void markSide(int x, int y, int side, ShipPart part) {
        for (int k = zOffset; k >= 0; k--) {
            int z = k * side;
            if (!inBounds(x, y, z)) continue;
            int i = idx(x, y, z);
            if (comp[i] == NONE) continue;
            if (hull[i]) set(x, y, z, part);
            return;
        }
    }

    /** Schreibt Text auf eine Seitenfläche. Lesbar von außen: Steuerbord läuft der Text Richtung Bug. */
    private void sideText(String text, int xStart, int yTop, int side) {
        int col = 0;
        for (char ch : text.toCharArray()) {
            String[] g = FONT3x5.get(ch);
            for (int row = 0; row < g.length; row++)
                for (int c = 0; c < g[row].length(); c++)
                    if (g[row].charAt(c) == '1') markSide(xStart - side * (col + c), yTop - row, side, ShipPart.HULL_MARKING);
            col += g[0].length() + 1;
        }
    }

    /** Kennung + rote Zierstreifen an Gondeln und Sekundärhülle, Positionslichter. */
    private void hullDetails() {
        String regText = design.registry;
        int reg = textWidth(regText, FONT3x5);
        for (int side : new int[] {1, -1}) {
            if (nx) {
                // NX-01: Kennung seitlich hinten am Rand der Untertasse
                int x = (int) Math.round(sCx + 0.45 * sR);
                sideText(regText, side > 0 ? x + reg : x, (int) Math.round(sMid) + 2, side);
                continue;
            }
            if (tng) {
                // TNG: Kennung seitlich am Unterrumpf; D und E: rote Sternenflotten-Wimpel an den Gondeln
                int ey = (int) Math.round(eY + eR * 0.25);
                int ex = (int) Math.round(eXf + 0.3 * (eXr - eXf));
                sideText(regText, side > 0 ? ex + reg : ex, ey + 2, side);
                if (design != ShipDesign.NCC_1701_C) {
                    int ny = (int) Math.round(nY + nR * 0.5);
                    int x0 = (int) Math.round(nX0 + nR * 1.6);
                    pennant(x0, (int) Math.round(nX0 + 0.7 * (nX1 - nX0)), ny, side, (int) Math.max(1, nR * 0.15));
                }
                continue;
            }
            // Gondeln: Text vorne, danach roter Streifen auf Mittelhöhe
            int ny = (int) Math.round(nY);
            int textFront = (int) Math.round(nX0 + nR * (refit ? 3.2 : 1.5)); // Refit: hinter den Bussard-Fenstern
            int start = side > 0 ? textFront + reg : textFront;
            sideText(regText, start, ny + 2, side);
            for (int x = textFront + reg + 3; x < nX1 - 6 * s; x++) markSide(x, ny, side, ShipPart.HULL_PENNANT);
            // Sekundärhülle: Text vor dem Pylon, roter Streifen darunter
            int ey = (int) Math.round(eY + eR * 0.25);
            int ex = (int) Math.round(eXf + (excelsior ? 0.25 : 0.5) * (eXr - eXf)); // Excelsior: vor den Gondeln
            if (design != ShipDesign.NCC_1701) sideText(regText, side > 0 ? ex + reg : ex, ey + 2, side); // Originalserie: keine Kennung am Unterrumpf
            int px0 = (int) Math.round(eXf + 0.15 * (eXr - eXf));
            if (design == ShipDesign.NCC_1701) {
                // Originalserie: Wimpel mit Pfeilspitze und drei roten Streifen (nach unten kürzer)
                // kurz, vorne am Rumpf (wie am Studiomodell)
                double L = eXr - eXf;
                int s0 = (int) Math.round(eXf + 0.08 * L);
                pennant(s0, (int) Math.round(s0 + 0.3 * L), ey - 5, side, 1);
                for (int x = s0 + 4; x < s0 + 0.22 * L; x++) markSide(x, ey - 7, side, ShipPart.HULL_PENNANT);
                for (int x = s0 + 4; x < s0 + 0.15 * L; x++) markSide(x, ey - 9, side, ShipPart.HULL_PENNANT);
            } else if (refit) pennant(px0, ex + reg + 2, ey - 5, side, 1); // Refit: Wimpel mit Pfeilspitze vorne
            else for (int x = px0; x < ex + reg + 2; x++) markSide(x, ey - 5, side, ShipPart.HULL_PENNANT);
        }
        if (design == ShipDesign.NCC_1701_D) {
            // zwei parallele rote Linien hinten den Hals hinunter
            for (int x = 0; x < sizeX; x++)
                for (int y = 0; y < sizeY; y++)
                    for (int z : new int[] {-2, 2}) {
                        if (compAt(x, y, z) != NECK || !hull[idx(x, y, z)]) continue;
                        if (compAt(x + 1, y, z) == NONE && compAt(x, y + 1, z) != NONE) set(x, y, z, ShipPart.HULL_PENNANT);
                    }
        }
        // Positionslichter am Untertassenrand: Backbord rot, Steuerbord grün
        int mx = (int) Math.round(sCx), my = (int) Math.round(sMid);
        markSide(mx, my, -1, ShipPart.NAV_LIGHT_RED);
        markSide(mx, my, 1, ShipPart.NAV_LIGHT_GREEN);
        markSide(mx + 1, my, -1, ShipPart.NAV_LIGHT_RED);
        markSide(mx + 1, my, 1, ShipPart.NAV_LIGHT_GREEN);
    }

    /**
     * Sternenflotten-Wimpel: vorne eine nach vorne zeigende Pfeilspitze (bis {@code half} Blöcke über
     * und unter dem Streifen), dahinter ein roter Streifen bis x1.
     */
    private void pennant(int x0, int x1, int y, int side, int half) {
        for (int x = x0; x < x1; x++) {
            int h = x - x0 < 2 * (half + 1) ? (x - x0) / 2 : 0; // Spitze zeigt nach vorne
            for (int dy = -Math.max(0, h); dy <= Math.max(0, h); dy++) markSide(x, y + dy, side, ShipPart.HULL_PENNANT);
        }
    }

    /** Refit: zwei Photonentorpedo-Werfer vorne am unteren Ende des Halses. */
    private void torpedoLaunchers() {
        // die untersten zwei sichtbaren Zeilen der Halsvorderseite (über der Sekundärhülle)
        for (int z : new int[] {-2, -1, 1, 2}) {
            int placed = 0;
            for (int y = (int) eY; y < sizeY && placed < 2; y++)
                for (int x = 0; x < sizeX; x++) {
                    if (compAt(x, y, z) != NECK) continue;
                    if (hull[idx(x, y, z)] && compAt(x - 1, y, z) == NONE) {
                        set(x, y, z, ShipPart.TORPEDO_LAUNCHER);
                        placed++;
                    }
                    break;
                }
        }
    }

    /** Glättet Ober- und Unterseite der Untertasse mit Halbstufen. */
    private void smoothSaucer() {
        for (int x = 0; x < sizeX; x++)
            for (int z = -zOffset; z <= zOffset; z++) {
                double dx = x - sCx;
                double r = Math.sqrt(dx * dx + (double) z * z);
                if (r > sR || r <= 10) continue;
                double top = saucerTop(r);
                int yt = (int) Math.floor(top);
                if (top - yt >= 0.5 && compAt(x, yt, z) == SAUCER && compAt(x, yt + 1, z) == NONE) {
                    ShipPart below = get(x, yt, z);
                    set(x, yt + 1, z, below == ShipPart.HULL_MARKING ? ShipPart.HULL_MARKING_SLAB : ShipPart.HULL_PLATING_SLAB,
                        Direction.NORTH, 0);
                }
                double bot = saucerBottom(r);
                int yb = (int) Math.ceil(bot);
                if (yb - bot >= 0.5 && compAt(x, yb, z) == SAUCER && compAt(x, yb - 1, z) == NONE) {
                    set(x, yb - 1, z, ShipPart.HULL_PLATING_SLAB, Direction.NORTH, 1);
                }
            }
    }

    // =====================================================================================
    //  Räume in Originalgröße
    // =====================================================================================

    /**
     * Kommandobrücke nach dem Vorbild der Originalserie (Ø 15 m): Kommandomulde mit Sessel und
     * Steuerkonsole, rotes Geländer, erhöhter Außenring mit Stationen und Wandmonitoren,
     * roter Abschlussstreifen, Hauptbildschirm vorne, zwei Turbolift-Türen hinten.
     */
    private void buildBridge() {
        if (excelsior) {
            buildBridgeExcelsior();
            return;
        }
        if (tng) {
            buildBridgeTng();
            return;
        }
        if (nx) {
            buildBridgeNx();
            return;
        }
        int cx = (int) Math.round(sCx);
        int yb = bridgeFloor;
        ShipPart air = ShipPart.AIR;
        ShipPart carpet = excelsior ? ShipPart.DECK_PLATE : ShipPart.BRIDGE_DECK; // Excelsior: Riffelblech
        ShipPart light = ShipPart.CEILING_LIGHT;

        for (int dx = -10; dx <= 10; dx++)
            for (int dz = -10; dz <= 10; dz++) {
                double r = Math.sqrt(dx * dx + dz * dz);
                int x = cx + dx;
                boolean front = dx < 0 && Math.abs(dz) <= 3;
                boolean aft = dx > 0 && dz >= 0 && dz <= 4; // Turbolift hinten Steuerbord
                if (r <= 7.5) {
                    set(x, yb, dz, excelsior && r <= 4.2 ? ShipPart.BRIDGE_CARPET_TEAL : carpet);
                    for (int y = yb + 1; y <= yb + 5; y++) set(x, y, dz, air);
                    set(x, yb + 6, dz, r < 1.5 || Math.abs(r - 3.5) < 0.5 ? light : ShipPart.CEILING_PANEL);
                    boolean gap = dx < 0 ? Math.abs(dz) <= 1 : (dz >= 0 && dz <= 2); // Durchgänge zur Konsole und zum Turbolift
                    if (r > 5.2) {
                        set(x, yb + 1, dz, carpet); // erhöhter Außenring
                    } else if (r > 4.2) {
                        if (gap) {
                            set(x, yb + 1, dz, ShipPart.STEP_SLAB, Direction.NORTH, 0); // Stufe
                        } else {
                            set(x, yb + 1, dz, ShipPart.HULL_MARKING); // schwarze Stufenkante
                            set(x, yb + 2, dz, excelsior ? ShipPart.BRIDGE_RAILING_WOOD : refit ? ShipPart.BRIDGE_RAILING_GRAY : ShipPart.BRIDGE_RAILING);
                        }
                    }
                    // Stationen am Außenrand des Rings
                    if (r > 6.5 && !front && !aft) {
                        int variant = Math.floorMod((int) Math.round(Math.atan2(dz, dx) * 3), 4);
                        set(x, yb + 2, dz, ShipPart.BRIDGE_STATION, inwardFacing(dx, dz), variant);
                    }
                } else if (r <= 10) {
                    // Wand (die Außenhülle bleibt erhalten)
                    if (!isInterior(x, yb + 2, dz)) continue;
                    for (int y = yb; y <= yb + 5; y++) if (isInterior(x, y, dz)) set(x, y, dz, excelsior ? ShipPart.BRIDGE_WALL_DARK : ShipPart.BRIDGE_WALL);
                    if (r <= 8.5) {
                        set(x, yb + 5, dz, excelsior ? ShipPart.BRIDGE_TRIM_CREAM : refit ? ShipPart.BRIDGE_TRIM_BLUE : ShipPart.BRIDGE_TRIM);
                        if (!front && !aft) {
                            int variant = Math.floorMod((int) Math.round(Math.atan2(dz, dx) * 5), 4);
                            set(x, yb + 3, dz, ShipPart.BRIDGE_MONITOR, inwardFacing(dx, dz), variant);
                        }
                    }
                }
            }
        // Hauptbildschirm vorne (7 x 3) mit Rahmen
        for (int dz = -3; dz <= 3; dz++)
            for (int y = yb + 2; y <= yb + 4; y++)
                set(cx - 8, y, dz, ShipPart.VIEWSCREEN, Direction.SOUTH);
        // Steuer-/Navigationskonsole mit zwei Stühlen
        for (int dz = -1; dz <= 1; dz++) set(cx - 3, yb + 1, dz, ShipPart.HELM_CONSOLE, Direction.SOUTH);
        set(cx - 2, yb + 1, -1, ShipPart.STATION_CHAIR, Direction.NORTH);
        set(cx - 2, yb + 1, 1, ShipPart.STATION_CHAIR, Direction.NORTH);
        // Kommandantensessel
        set(cx + 1, yb + 1, 0, ShipPart.CAPTAINS_CHAIR, Direction.NORTH);
        // Stühle an Wissenschafts- und Kommunikationsstation
        set(cx + 4, yb + 2, 5, ShipPart.STATION_CHAIR, Direction.EAST);
        set(cx + 4, yb + 2, -5, ShipPart.STATION_CHAIR, Direction.WEST);
        // Turbolift-Tür (hinten, etwas Steuerbord) mit leuchtendem Rahmen
        BlockPos lift = bridgeTurbolift();
        set(lift.getX(), yb + 3, lift.getZ(), ShipPart.TURBOLIFT_DOOR, Direction.NORTH);
        for (int dz : new int[] {-1, 1}) {
            set(lift.getX(), yb + 2, lift.getZ() + dz, ShipPart.TURBOLIFT_FRAME);
            set(lift.getX(), yb + 3, lift.getZ() + dz, ShipPart.TURBOLIFT_FRAME);
        }
        set(lift.getX(), yb + 4, lift.getZ(), ShipPart.TURBOLIFT_FRAME);
        bridgeSpawn = new BlockPos(cx + 2, yb + 1, 0);
    }

    /**
     * Brücke der Excelsior-Klasse: dunkle Wände mit senkrechten cremefarbenen Lichtsäulen und großen
     * grünen Schemata, dunkle Konsolen mit grünen Bildschirmen, Riffelblech-Boden, Holzgeländer,
     * petrolfarbener Kommandantensessel auf erhöhtem Podest, lange graue Steuerkonsole mit zwei Stühlen,
     * roter Teppich am Turbolift, dunkle Decke mit Spots.
     */
    private void buildBridgeExcelsior() {
        int cx = (int) Math.round(sCx);
        int yb = bridgeFloor;
        for (int dx = -10; dx <= 10; dx++)
            for (int dz = -10; dz <= 10; dz++) {
                double r = Math.sqrt(dx * dx + dz * dz);
                int x = cx + dx;
                boolean front = dx < 0 && Math.abs(dz) <= 3;
                boolean aft = dx > 0 && dz >= 0 && dz <= 4;
                int sector = Math.floorMod((int) Math.round(Math.atan2(dz, dx) / (Math.PI / 12)), 24);
                if (r <= 7.5) {
                    set(x, yb, dz, ShipPart.DECK_PLATE);
                    for (int y = yb + 1; y <= yb + 5; y++) set(x, y, dz, ShipPart.AIR);
                    boolean spot = Math.floorMod(dx, 3) == 0 && Math.floorMod(dz, 3) == 0;
                    set(x, yb + 6, dz, spot ? ShipPart.CEILING_LIGHT : ShipPart.BRIDGE_WALL_DARK);
                    boolean gap = dx < 0 ? Math.abs(dz) <= 1 : (dz >= 0 && dz <= 2);
                    if (r > 5.2) {
                        set(x, yb + 1, dz, aft ? ShipPart.TRANSPORTER_PLATFORM : ShipPart.DECK_PLATE); // Ring, hinten roter Teppich
                    } else if (r > 4.2) {
                        if (gap) {
                            set(x, yb + 1, dz, ShipPart.STEP_SLAB, Direction.NORTH, 0);
                        } else {
                            set(x, yb + 1, dz, ShipPart.BRIDGE_WALL_DARK);
                            set(x, yb + 2, dz, ShipPart.BRIDGE_RAILING_WOOD);
                        }
                    }
                    if (r > 6.5 && !front && !aft)
                        set(x, yb + 2, dz, ShipPart.EXCELSIOR_CONSOLE, inwardFacing(dx, dz), Math.floorMod(sector, 4));
                } else if (r <= 10) {
                    if (!isInterior(x, yb + 2, dz)) continue;
                    for (int y = yb; y <= yb + 5; y++) if (isInterior(x, y, dz)) set(x, y, dz, ShipPart.BRIDGE_WALL_DARK);
                    if (r <= 8.5 && !front) {
                        boolean pillar = sector % 3 == 0;
                        for (int y = yb + 2; y <= yb + 5; y++)
                            if (pillar) set(x, y, dz, ShipPart.LIGHT_PILLAR);
                        if (!pillar && !aft) {
                            set(x, yb + 3, dz, ShipPart.EXCELSIOR_DISPLAY, inwardFacing(dx, dz), Math.floorMod(sector, 4));
                            set(x, yb + 4, dz, ShipPart.EXCELSIOR_DISPLAY, inwardFacing(dx, dz), Math.floorMod(sector + 1, 4));
                        }
                    }
                }
            }
        // Hauptbildschirm vorne (7 x 3), Lichtsäulen daneben
        for (int dz = -3; dz <= 3; dz++)
            for (int y = yb + 2; y <= yb + 4; y++) set(cx - 8, y, dz, ShipPart.VIEWSCREEN, Direction.SOUTH);
        for (int y = yb + 2; y <= yb + 5; y++) {
            set(cx - 8, y, -4, ShipPart.LIGHT_PILLAR);
            set(cx - 8, y, 4, ShipPart.LIGHT_PILLAR);
        }
        // lange graue Steuerkonsole mit zwei Stühlen
        for (int dz = -1; dz <= 1; dz++) set(cx - 3, yb + 1, dz, ShipPart.HELM_CONSOLE_GRAY, Direction.SOUTH);
        set(cx - 2, yb + 1, -1, ShipPart.STATION_CHAIR_TEAL, Direction.NORTH);
        set(cx - 2, yb + 1, 1, ShipPart.STATION_CHAIR_TEAL, Direction.NORTH);
        // erhöhtes Podest mit dem Kommandantensessel
        for (int ddx = 0; ddx <= 2; ddx++)
            for (int dz = -1; dz <= 1; dz++)
                if (Math.abs(ddx - 1) + Math.abs(dz) <= 2) set(cx + ddx, yb + 1, dz, ShipPart.BRIDGE_CARPET_TEAL);
        set(cx + 1, yb + 2, 0, ShipPart.CAPTAINS_CHAIR_TEAL, Direction.NORTH);
        // Stationsstühle an Wissenschaft und Kommunikation
        set(cx + 4, yb + 2, 5, ShipPart.STATION_CHAIR_TEAL, Direction.EAST);
        set(cx + 4, yb + 2, -5, ShipPart.STATION_CHAIR_TEAL, Direction.WEST);
        // Master Systems Display an der Rückwand (wie auf der Brücke der B in "Treffen der Generationen")
        for (int dz = -4; dz <= -2; dz++)
            for (int y = yb + 3; y <= yb + 4; y++) set(cx + 8, y, dz, ShipPart.MSD_DISPLAY, Direction.NORTH, dz + 5);
        // Turbolift mit dunkler Tür, Lichtsäulen als Rahmen
        BlockPos lift = bridgeTurbolift();
        set(lift.getX(), yb + 3, lift.getZ(), ShipPart.TURBOLIFT_DOOR_TEAL, Direction.NORTH);
        for (int dz : new int[] {-1, 1})
            for (int y = yb + 2; y <= yb + 4; y++) set(lift.getX(), y, lift.getZ() + dz, ShipPart.LIGHT_PILLAR);
        bridgeSpawn = new BlockPos(cx + 3, yb + 1, 0);
    }

    /**
     * Brücke der Next-Generation-Schiffe nach dem Vorbild der Galaxy-Klasse (Ø 21 m): großer
     * Hauptbildschirm, Ops- und Conn-Konsole davor, in der Mitte Kommandantensessel mit den Sitzen
     * des Ersten Offiziers (Steuerbord) und der Counselor (Backbord), dahinter das hufeisenförmige
     * Holzgeländer mit der Taktikstation auf der erhöhten Ebene, Wissenschafts- und
     * Technikstationen an der Rückwand, zwei Turbolifte hinten. Die C hat dunkle Wände.
     */
    private void buildBridgeTng() {
        int cx = (int) Math.round(sCx);
        int yb = bridgeFloor;
        double rr = bR - 2.5;
        ShipPart wallPart = design == ShipDesign.NCC_1701_C ? ShipPart.BRIDGE_WALL_DARK : ShipPart.TNG_WALL;
        ShipPart carpet = ShipPart.TNG_CARPET;
        BlockPos lift = bridgeTurbolift();
        for (int dx = -bR; dx <= bR; dx++)
            for (int dz = -bR; dz <= bR; dz++) {
                double r = Math.sqrt(dx * dx + dz * dz);
                double rh = Math.hypot(dx, dz);                // Abstand vom Kommandobereich (Mitte)
                int x = cx + dx;
                boolean nearLift = dx >= 7 && Math.abs(Math.abs(dz) - Math.abs(lift.getZ())) <= 1.5;
                if (r <= rr) {
                    boolean raised = dx >= 0 && rh > 4.5;        // erhöhte hintere Ebene
                    set(x, yb, dz, carpet);
                    for (int y = yb + 1; y <= yb + 5; y++) set(x, y, dz, ShipPart.AIR);
                    set(x, yb + 6, dz, r < 3.2 || Math.abs(r - 7) < 0.5 ? ShipPart.CEILING_LIGHT : ShipPart.CEILING_PANEL);
                    if (raised) set(x, yb + 1, dz, carpet);
                    // Rampen an beiden Enden des Hufeisens
                    if (!raised && dx == -1 && Math.abs(dz) >= 5) set(x, yb + 1, dz, ShipPart.STEP_SLAB, Direction.NORTH, 0);
                    // Hufeisen-Geländer aus Holz, oben in der Mitte die Taktikstation
                    if (dx >= 2 && rh > 4.0 && rh <= 5.0) {
                        if (Math.abs(dz) <= 1) set(x, yb + 2, dz, ShipPart.TNG_CONSOLE, Direction.SOUTH);
                        else set(x, yb + 2, dz, ShipPart.BRIDGE_RAILING_WOOD);
                    }
                    // Wissenschafts- und Technikstationen an der Rückwand
                    if (r > rr - 1 && dx >= 3 && !nearLift)
                        set(x, yb + 2, dz, ShipPart.TNG_CONSOLE, inwardFacing(dx, dz));
                } else if (r <= bR) {
                    if (!isInterior(x, yb + 2, dz)) continue;
                    for (int y = yb; y <= yb + 5; y++) if (isInterior(x, y, dz)) set(x, y, dz, wallPart);
                    // LCARS-Anzeigen über den Stationen und an den Seitenwänden
                    if (r <= rr + 1 && dx > -8 && !nearLift && Math.floorMod(dx + dz, 2) == 0)
                        set(x, yb + 3, dz, ShipPart.LCARS_PANEL, inwardFacing(dx, dz));
                }
            }
        // Hauptbildschirm vorne (11 x 4)
        for (int dz = -5; dz <= 5; dz++)
            for (int y = yb + 1; y <= yb + 4; y++) set(cx - (int) Math.ceil(rr), y, dz, ShipPart.VIEWSCREEN, Direction.SOUTH);
        // Ops (Backbord) und Conn (Steuerbord)
        for (int side : new int[] {-1, 1}) {
            set(cx - 6, yb + 1, 2 * side, ShipPart.TNG_CONSOLE, Direction.SOUTH);
            set(cx - 6, yb + 1, 3 * side, ShipPart.TNG_CONSOLE, Direction.SOUTH);
            set(cx - 5, yb + 1, 2 * side, ShipPart.TNG_CHAIR, Direction.NORTH);
        }
        // Kommandobereich: Captain, Erster Offizier (Steuerbord), Counselor (Backbord)
        set(cx - 1, yb + 1, 0, ShipPart.CAPTAINS_CHAIR_TNG, Direction.NORTH);
        set(cx - 1, yb + 1, 2, ShipPart.TNG_CHAIR, Direction.NORTH);
        set(cx - 1, yb + 1, -2, ShipPart.TNG_CHAIR, Direction.NORTH);
        // Turbolifte hinten: Steuerbord in Betrieb, Backbord als zweite Tür
        for (int side : new int[] {1, -1}) {
            int lz = lift.getZ() * side;
            if (side > 0) set(lift.getX(), yb + 3, lz, ShipPart.TURBOLIFT_DOOR, Direction.NORTH);
            else for (int y = yb + 2; y <= yb + 3; y++) set(lift.getX(), y, lz, ShipPart.TURBOLIFT_DOOR, Direction.NORTH);
            for (int dz : new int[] {-1, 1})
                for (int y = yb + 2; y <= yb + 3; y++) set(lift.getX(), y, lz + dz, ShipPart.TURBOLIFT_FRAME);
            set(lift.getX(), yb + 4, lz, ShipPart.TURBOLIFT_FRAME);
        }
        bridgeSpawn = new BlockPos(cx - 3, yb + 1, 0);
    }

    /**
     * Brücke der NX-01 (Ø 15 m, ebener Boden): grauer Metallboden und Wände, Hauptbildschirm vorne,
     * Steuerkonsole mit Sitz davor (Mayweather), Kommandantensessel in der Mitte, Stationen rundum:
     * Kommunikation Backbord (Hoshi), Wissenschaft Steuerbord (T'Pol), Taktik hinten Steuerbord (Reed),
     * Turbolift hinten in der Mitte.
     */
    private void buildBridgeNx() {
        int cx = (int) Math.round(sCx);
        int yb = bridgeFloor;
        for (int dx = -10; dx <= 10; dx++)
            for (int dz = -10; dz <= 10; dz++) {
                double r = Math.sqrt(dx * dx + dz * dz);
                int x = cx + dx;
                boolean front = dx < 0 && Math.abs(dz) <= 3;
                boolean aft = dx > 4 && Math.abs(dz) <= 2;
                if (r <= 7.5) {
                    set(x, yb, dz, ShipPart.DECK_PLATE);
                    for (int y = yb + 1; y <= yb + 5; y++) set(x, y, dz, ShipPart.AIR);
                    set(x, yb + 6, dz, r < 2.5 || Math.abs(r - 5) < 0.5 ? ShipPart.CEILING_LIGHT : ShipPart.ENT_WALL);
                    if (r > 6.5 && !front && !aft) set(x, yb + 1, dz, ShipPart.ENT_CONSOLE, inwardFacing(dx, dz));
                } else if (r <= 10) {
                    if (!isInterior(x, yb + 2, dz)) continue;
                    for (int y = yb; y <= yb + 5; y++) if (isInterior(x, y, dz)) set(x, y, dz, ShipPart.ENT_WALL);
                    if (r <= 8.5 && !front && !aft && Math.floorMod(dx + dz, 2) == 0)
                        set(x, yb + 2, dz, ShipPart.ENT_CONSOLE, inwardFacing(dx, dz));
                }
            }
        for (int dz = -3; dz <= 3; dz++)
            for (int y = yb + 2; y <= yb + 4; y++) set(cx - 8, y, dz, ShipPart.VIEWSCREEN, Direction.SOUTH);
        // Steuerkonsole mit Sitz
        for (int dz = -1; dz <= 1; dz++) set(cx - 5, yb + 1, dz, ShipPart.ENT_CONSOLE, Direction.SOUTH);
        set(cx - 4, yb + 1, 0, ShipPart.ENT_CHAIR, Direction.NORTH);
        set(cx, yb + 1, 0, ShipPart.CAPTAINS_CHAIR_NX, Direction.NORTH);
        // Sitze an Kommunikation (Backbord) und Wissenschaft (Steuerbord)
        set(cx, yb + 1, -6, ShipPart.ENT_CHAIR, Direction.WEST);
        set(cx, yb + 1, 6, ShipPart.ENT_CHAIR, Direction.EAST);
        BlockPos lift = bridgeTurbolift();
        set(lift.getX(), yb + 2, 0, ShipPart.TURBOLIFT_DOOR, Direction.NORTH);
        for (int dz : new int[] {-1, 1})
            for (int y = yb + 1; y <= yb + 2; y++) set(lift.getX(), y, dz, ShipPart.TURBOLIFT_FRAME);
        set(lift.getX(), yb + 3, 0, ShipPart.TURBOLIFT_FRAME);
        bridgeSpawn = new BlockPos(cx + 2, yb + 1, 0);
    }

    private static Direction inwardFacing(int dx, int dz) {
        if (Math.abs(dx) >= Math.abs(dz)) return dx < 0 ? Direction.SOUTH : Direction.NORTH;
        return dz < 0 ? Direction.EAST : Direction.WEST;
    }

    private BlockPos bridgeTurbolift() {
        if (tng) return new BlockPos((int) Math.round(sCx) + 9, bridgeFloor + 2, 5);
        if (nx) return new BlockPos((int) Math.round(sCx) + 7, bridgeFloor + 1, 0);
        return new BlockPos((int) Math.round(sCx) + 8, bridgeFloor + 2, 2);
    }

    /** Hohler Raum mit Wänden, Boden und Decke; die Außenhülle bleibt erhalten. */
    private void room(int x0, int x1, int y0, int y1, int z0, int z1) {
        room(x0, x1, y0, y1, z0, z1, ShipPart.CORRIDOR_WALL);
    }

    private void room(int x0, int x1, int y0, int y1, int z0, int z1, ShipPart wall) {
        ShipPart floor = ShipPart.DECK_FLOOR;
        ShipPart air = ShipPart.AIR;
        for (int x = x0; x <= x1; x++)
            for (int y = y0; y <= y1; y++)
                for (int z = z0; z <= z1; z++) {
                    if (!inBounds(x, y, z)) continue;
                    int i = idx(x, y, z);
                    boolean boundary = x == x0 || x == x1 || y == y0 || y == y1 || z == z0 || z == z1;
                    if (boundary) {
                        if (comp[i] != NONE && !hull[i]) set(x, y, z, y == y0 ? floor : y == y1 ? ShipPart.CEILING_PANEL : wall);
                    } else {
                        set(x, y, z, air);
                    }
                }
    }

    /** Maschinenraum (19 x 11 m, zwei Ebenen) mit Warpkern in der Sekundärhülle. */
    private BlockPos buildEngineering() {
        int[] box = engineeringBox();
        int x0 = box[0] + 10;
        int yF = box[2];
        room(x0 - 10, x0 + 10, yF, yF + 7, -6, 6, tng ? ShipPart.TNG_WALL : ShipPart.CORRIDOR_WALL);

        ShipPart core = tng ? ShipPart.WARP_CORE_TNG : ShipPart.WARP_CORE;
        ShipPart conduit = ShipPart.PLASMA_CONDUIT;
        ShipPart console = tng ? ShipPart.TNG_CONSOLE : ShipPart.ENGINEERING_CONSOLE;
        ShipPart lcars = ShipPart.LCARS_PANEL;
        ShipPart floor = ShipPart.DECK_FLOOR;
        ShipPart light = ShipPart.CEILING_LIGHT;
        ShipPart rail = ShipPart.IRON_BARS;

        // Plasmaleitungen im Boden
        for (int x = x0 - 9; x <= x0 + 9; x++) set(x, yF, 0, conduit);
        for (int z = -5; z <= 5; z++) set(x0, yF, z, conduit);
        // Warpkern (kreuzförmiger Querschnitt) vom Boden bis durch die Decke
        // NX-01: waagerechter Warpreaktor in Längsrichtung statt senkrechtem Kern
        for (int y = yF - 2; y <= yF + 9 && !nx; y++) {
            if (!(y >= yF && y <= yF + 7) && !isInterior(x0, y, 0)) continue;
            set(x0, y, 0, core);
            if (y > yF && y < yF + 7) {
                set(x0 + 1, y, 0, core);
                set(x0 - 1, y, 0, core);
                set(x0, y, 1, core);
                set(x0, y, -1, core);
            }
        }
        // Geländer um den Kern
        for (int d = -2; d <= 2; d++) {
            set(x0 + d, yF + 1, -2, rail);
            set(x0 + d, yF + 1, 2, rail);
            if (d != 0) {
                set(x0 - 2, yF + 1, d, rail);
                set(x0 + 2, yF + 1, d, rail);
            }
        }
        set(x0 - 2, yF + 1, 0, ShipPart.AIR); // Zugang
        if (nx) {
            for (int d = -2; d <= 2; d++)
                for (int z : new int[] {-2, 2}) set(x0 + d, yF + 1, z, ShipPart.AIR);
            for (int d = -1; d <= 1; d++) {
                set(x0 - 2, yF + 1, d, ShipPart.AIR);
                set(x0 + 2, yF + 1, d, ShipPart.AIR);
            }
            for (int x = x0 - 7; x <= x0 + 9; x++) {
                set(x, yF + 3, 0, core);
                set(x, yF + 2, 0, core);
                set(x, yF + 4, 0, core);
                set(x, yF + 3, 1, core);
                set(x, yF + 3, -1, core);
            }
        }
        // Konsolen und Anzeigen an den Längswänden, darüber eine Galerie
        for (int x = x0 - 8; x <= x0 + 8; x++) {
            if (Math.abs(x - x0) <= 2) continue;
            set(x, yF + 1, -5, console, Direction.EAST);
            set(x, yF + 1, 5, console, Direction.WEST);
            if (Math.floorMod(x, 2) == 0) {
                set(x, yF + 2, -6, lcars, Direction.EAST);
                set(x, yF + 2, 6, lcars, Direction.WEST);
            }
        }
        for (int x = x0 - 9; x <= x0 + 9; x++) {
            for (int z : new int[] {-5, -4, 4, 5}) set(x, yF + 3, z, floor);
            set(x, yF + 4, -3, rail);
            set(x, yF + 4, 3, rail);
        }
        if (refit) {
            // Refit: waagerechter Intermix-Schacht unter der Decke nach achtern
            for (int x = x0 + 1; x <= x0 + 9; x++) set(x, yF + 6, 0, core);
        }
        // Leitern zur Galerie (am Heckende)
        for (int y = yF + 1; y <= yF + 4; y++) {
            set(x0 + 9, y, -4, ShipPart.LADDER, Direction.NORTH);
            set(x0 + 9, y, 4, ShipPart.LADDER, Direction.NORTH);
        }
        set(x0 + 9, yF + 4, -3, ShipPart.AIR);
        set(x0 + 9, yF + 4, 3, ShipPart.AIR);
        // Deckenlichter
        for (int x = x0 - 8; x <= x0 + 8; x += 3)
            for (int z : new int[] {-2, 2})
                set(x, yF + 7, z, light);
        if (tng) {
            // Next Generation: Master Systems Display (Schiffsquerschnitt) an der Rückwand und der
            // Lagetisch ("Billardtisch") vor dem Warpkern
            int[] msd = {0, 0, 1, 1, 2, 2, 3};
            for (int dz = -3; dz <= 3; dz++)
                for (int y = yF + 2; y <= yF + 4; y++) set(x0 + 10, y, dz, ShipPart.MSD_DISPLAY, Direction.NORTH, msd[dz + 3]);
            for (int x = x0 - 6; x <= x0 - 5; x++)
                for (int dz = -1; dz <= 1; dz++) set(x, yF + 1, dz, ShipPart.SITUATION_TABLE);
        }
        // Turbolift an der vorderen Wand
        BlockPos lift = new BlockPos(x0 - 10, yF + 1, 0);
        set(lift.getX(), lift.getY(), 0, ShipPart.TURBOLIFT, Direction.SOUTH);
        return lift;
    }

    /**
     * Transporterraum nach dem Vorbild der Originalserie: lila Wände, erhöhte rote Plattform mit
     * Stufe und sechs Transporterfeldern, grün schimmernde Energiewand mit Säulen dahinter,
     * Strahler an der Decke und die Bedienkonsole davor.
     */
    private BlockPos buildTransporterRoom(boolean fullDecks) {
        if (fullDecks) {
            // Fester Platz auf Deck 3 im hinteren Innenraum (Steuerbord), Tür zum hinteren Gang
            int f = bridgeFloor - 2 * DECK_HEIGHT;
            int xt = (int) Math.round(sCx + 15), zc = 8;
            if (boxInterior(xt - 4, xt + 4, f + 1, f + 3, zc - 4, zc + 4)) {
                transporterRoomAt(xt, zc, f, false);
                for (int y = f + 1; y <= f + 2; y++)
                    for (int z = zc - 6; z <= zc - 5; z++) set(xt - 2, y, z, ShipPart.AIR);
                return null;
            }
        }
        for (int off = 9; off <= sR * 0.7; off++) {
            int xt = (int) Math.round(sCx + off);
            for (int f = sizeY - 7; f > 0; f--) {
                // Nur der begehbare Innenraum muss im Schiff liegen, Boden/Decke/Wände dürfen Hülle sein
                if (!boxInterior(xt - 4, xt + 4, f + 1, f + 3, -4, 4)) continue;
                return transporterRoomAt(xt, 0, f, true);
            }
        }
        // Ausweichlösung (z.B. dünne Untertasse bei kleinen Größen): irgendwo entlang der Mittellinie
        for (int xt = 6; xt < sizeX - 6; xt++)
            for (int f = sizeY - 7; f > 0; f--)
                if (boxInterior(xt - 4, xt + 4, f + 1, f + 3, -4, 4) && !boxTouches(xt - 5, xt + 5, f, f + 4, -5, 5))
                    return transporterRoomAt(xt, 0, f, true);
        return null;
    }

    /** Liegt schon ein besonderer Raum (Maschinenraum, Brücke) im Kasten? */
    private boolean boxTouches(int x0, int x1, int y0, int y1, int z0, int z1) {
        for (int x = x0; x <= x1; x++)
            for (int y = y0; y <= y1; y++)
                for (int z = z0; z <= z1; z++) {
                    ShipPart p = get(x, y, z);
                    if (p == ShipPart.WARP_CORE || p == ShipPart.WARP_CORE_TNG || p == ShipPart.ENGINEERING_CONSOLE || p == ShipPart.TNG_CONSOLE
                        || p == ShipPart.TURBOLIFT || compAt(x, y, z) == BRIDGE) return true;
                }
        return false;
    }

    /**
     * Transporterraum (11 x 11 außen, genau ein Deck hoch) nach dem Vorbild der Originalserie.
     * @return Position des eigenen Turbolifts oder {@code null}
     */
    private BlockPos transporterRoomAt(int xt, int zc, int f, boolean withLift) {
        room(xt - 5, xt + 5, f, f + 4, zc - 5, zc + 5, tng ? ShipPart.TNG_WALL : ShipPart.TRANSPORTER_WALL);
        // Plattform (abgerundet) mit Stufe davor
        for (int x = xt; x <= xt + 4; x++)
            for (int dz = -4; dz <= 4; dz++) {
                double d = Math.sqrt(Math.pow(x - (xt + 4), 2) * 0.6 + dz * dz);
                if (d <= 4.6) set(x, f + 1, zc + dz, ShipPart.TRANSPORTER_PLATFORM);
                else if (d <= 5.6) set(x, f + 1, zc + dz, ShipPart.STEP_SLAB, Direction.NORTH, 0);
            }
        for (int dz = -3; dz <= 3; dz++)
            if (get(xt - 1, f + 1, zc + dz) == ShipPart.AIR) set(xt - 1, f + 1, zc + dz, ShipPart.STEP_SLAB, Direction.NORTH, 0);
        // Sechs Transporterfelder mit Strahlern in der Decke
        // NX-01: nur ein Transporterfeld (frühe Technik, vor allem für Fracht)
        int[][] pads = nx ? new int[][] {{xt + 2, 0}}
            : new int[][] {{xt + 3, -2}, {xt + 3, 0}, {xt + 3, 2}, {xt + 1, -2}, {xt + 1, 0}, {xt + 1, 2}};
        for (int[] pd : pads) {
            set(pd[0], f + 2, zc + pd[1], ShipPart.TRANSPORTER_PAD);
            set(pd[0], f + 4, zc + pd[1], ShipPart.TRANSPORTER_EMITTER);
        }
        // Energiewand mit Säulen an der Rückwand (NX-01: nur graue Säulen)
        for (int dz = -4; dz <= 4 && !nx; dz++)
            for (int y = f + 2; y <= f + 3; y++) {
                boolean pillar = Math.floorMod(dz, 3) == 2 || Math.abs(dz) == 4;
                set(xt + 5, y, zc + dz, pillar ? ShipPart.TRANSPORTER_PILLAR : ShipPart.TRANSPORTER_ENERGIZER);
            }
        // Bedienkonsole vor der Plattform, Bediener schaut nach achtern
        set(xt - 3, f + 1, zc, ShipPart.TRANSPORTER_CONSOLE, Direction.NORTH);
        set(xt - 3, f + 4, zc, ShipPart.CEILING_LIGHT);
        set(xt - 1, f + 4, zc - 3, ShipPart.CEILING_LIGHT);
        set(xt - 1, f + 4, zc + 3, ShipPart.CEILING_LIGHT);
        if (!withLift) return null;
        BlockPos lift = new BlockPos(xt - 5, f + 1, zc - 3);
        set(lift.getX(), lift.getY(), lift.getZ(), ShipPart.TURBOLIFT, Direction.SOUTH);
        set(lift.getX(), lift.getY() + 1, lift.getZ(), ShipPart.TURBOLIFT_DOOR, Direction.SOUTH);
        return lift;
    }

    private boolean boxInterior(int x0, int x1, int y0, int y1, int z0, int z1) {
        for (int x = x0; x <= x1; x++)
            for (int y = y0; y <= y1; y++)
                for (int z = z0; z <= z1; z++)
                    if (!isInterior(x, y, z)) return false;
        return true;
    }

    /** Deckname: auf der NX-01 tragen die Decks Buchstaben ("A-Deck"), sonst Nummern. */
    private String deckLabel(int deck) {
        if (nx) return (char) ('A' + Math.max(0, Math.min(25, deck - 1))) + "-Deck";
        return "Deck " + deck;
    }

    private int deckNumber(int floorY) {
        return Math.round((bridgeFloor - floorY) / (float) DECK_HEIGHT) + 1;
    }

    /** Turbolift-Halte für Brücke, Maschinenraum und Transporterraum (die Decks tragen sich selbst ein). */
    private void linkTurbolifts(BlockPos engineering, BlockPos transporter) {
        BlockPos bridge = bridgeTurbolift();
        set(bridge.getX(), bridge.getY(), bridge.getZ(), ShipPart.TURBOLIFT, Direction.NORTH);
        liftStops.add(new LiftStop(bridge, deckLabel(1) + " – Brücke"));
        liftStops.add(new LiftStop(engineering, deckLabel(deckNumber(engineering.getY() - 1)) + " – Maschinenraum"));
        if (transporter != null) {
            liftStops.add(new LiftStop(transporter, deckLabel(deckNumber(transporter.getY() - 1)) + " – Transporterraum"));
        }
        // In Originalgröße liegt der Transporterraum auf Deck 3 und ist über den Deck-Lift erreichbar
        for (int i = 0; i < liftStops.size(); i++)
            if (liftStops.get(i).name().startsWith(deckLabel(3) + " – Krankenstation") && transporter == null)
                liftStops.set(i, new LiftStop(liftStops.get(i).pos(), liftStops.get(i).name() + ", Transporter"));
    }

    // =====================================================================================
    //  Decks mit Räumen (Originalgröße)
    // =====================================================================================

    private enum RoomType {
        QUARTERS, BRIEFING, READY_ROOM, SICKBAY, LAB, MESS, REC, STORAGE, CARGO,
        // Next Generation
        TEN_FORWARD, HOLODECK, ARBORETUM, CARTOGRAPHY, BRIG, CLASSROOM,
        // Refit (Kinofilme)
        REC_DECK, LOUNGE,
        // NX-01
        DECON
    }

    /** Ringsektor-Raum auf einem Deck der Untertasse. Winkel: 0 = Bug, positiv Richtung Steuerbord. */
    private record Room(double rIn, double rOut, double a0, double a1, RoomType type) {}

    /**
     * Ring von Räumen zwischen zwei Ringkorridoren (normierte Radien).
     * @param doorInside Tür auf der Innenseite (sonst außen, beim innersten Ring zum ersten Ringkorridor)
     * @param bySector   Raum je Sektor (gleich große Sektoren ab dem Heck über Backbord, Bug, Steuerbord)
     */
    private record Band(double rIn, double rOut, boolean doorInside, Room[] bySector, List<Room> rooms) {
        Room at(double a) {
            int i = (int) Math.floor((a + Math.PI) / (2 * Math.PI) * bySector.length);
            return bySector[Math.max(0, Math.min(bySector.length - 1, i))];
        }
    }

    private double angle(int x, int z) {
        return Math.atan2(z * kz, -(x - sCx) * kx);
    }

    private static double angDiff(double a, double b) {
        double d = a - b;
        while (d > Math.PI) d -= 2 * Math.PI;
        while (d < -Math.PI) d += 2 * Math.PI;
        return d;
    }

    private boolean deckCell(int x, int y, int z) {
        byte c = compAt(x, y, z);
        return (c == SAUCER || c == SENSOR || c == BRIDGE) && isInterior(x, y, z);
    }

    /**
     * Radien der Ringkorridore: kleine Untertassen (Originalserie, Refit, Excelsior) haben einen Ring,
     * die großen der Next-Generation-Schiffe alle 20 m einen weiteren.
     */
    private double[] ringRadii() {
        if (sR <= 100) return new double[] {0.42 * sR};
        List<Double> r = new ArrayList<>();
        for (double v = 24; v < sR - 16; v += 20) r.add(v);
        return r.stream().mapToDouble(Double::doubleValue).toArray();
    }

    /**
     * Alle Decks der Untertasse: Turbolift im Vorraum in der Mitte, vier Gänge, Ringkorridor(e),
     * große Innenräume (Besprechungsraum, Krankenstation, Messe, Labore …) und Quartiere außen.
     * Next Generation: dazu Ten Forward am Rand, Holodecks, Arboretum, Stellare Kartographie,
     * Arrestzellen und Klassenzimmer.
     */
    private void layoutSaucerDecks() {
        int cx = (int) Math.round(sCx);
        double[] rings = ringRadii();
        int rx = (int) Math.ceil(sR / kx), rz = (int) Math.ceil(sR / kz);
        for (int f = bridgeFloor - DECK_HEIGHT; f > 0; f -= DECK_HEIGHT) {
            boolean ok = true;
            for (int y = f + 1; y <= f + 3; y++) ok &= deckCell(cx, y, 0) && deckCell(cx + 6, y, 0);
            if (!ok) continue;
            int deck = deckNumber(f);
            int index = deck - 2; // 0 = erstes Deck unter der Brücke
            // Ten Forward liegt auf dem Deck, das vorne bis an den Rand der Untertasse reicht
            boolean rim = sMid >= f + 1 && sMid < f + 1 + DECK_HEIGHT;
            List<Band> bands = deckBands(index, rings, rim);
            String deckName = saucerDeckName(index, f) + (rim && tng ? ", Ten Forward" : rim && refit ? ", Offiziers-Lounge" : "");

            // Wände, Gänge und Türen
            for (int x = cx - rx; x <= cx + rx; x++)
                for (int z = -rz; z <= rz; z++) {
                    double r = rad(x, z);
                    if (r > sR) continue;
                    double a = angle(x, z);
                    for (int y = f + 1; y <= f + 3; y++) {
                        if (!deckCell(x, y, z)) continue;
                        set(x, y, z, saucerDeckCell(x, y, z, r, a, f, rings, bands));
                    }
                    if (isInterior(x, f, z)) set(x, f, z, ShipPart.DECK_FLOOR);
                    // Deckenlichter im Raster
                    if (Math.floorMod(x, 5) == 0 && Math.floorMod(z, 5) == 0 && get(x, f + 3, z) == ShipPart.AIR
                        && deckCell(x, f + 3, z) && compAt(x, f + 4, z) != NONE) set(x, f + 3, z, ShipPart.CEILING_LIGHT);
                }
            // Vorraum mit Turbolift in der Mitte
            set(cx, f + 1, 0, ShipPart.TURBOLIFT, Direction.SOUTH);
            set(cx, f + 2, 0, ShipPart.TURBOLIFT_DOOR, Direction.SOUTH);
            set(cx, f + 3, 0, ShipPart.TURBOLIFT_FRAME);
            set(cx, f + 1, -1, ShipPart.TURBOLIFT_FRAME);
            set(cx, f + 2, -1, ShipPart.TURBOLIFT_FRAME);
            set(cx, f + 1, 1, ShipPart.TURBOLIFT_FRAME);
            set(cx, f + 2, 1, ShipPart.TURBOLIFT_FRAME);
            for (int y = f + 1; y <= f + 3; y++) set(cx - 1, y, 0, ShipPart.TURBOLIFT_FRAME); // Rückseite des Schachts
            liftStops.add(new LiftStop(new BlockPos(cx, f + 1, 0), deckLabel(deck) + " – " + deckName));

            for (Band band : bands)
                for (Room room : band.rooms()) furnish(room, f);
        }
    }

    /** Räume eines Decks, Ring für Ring von innen nach außen. */
    private List<Band> deckBands(int index, double[] rings, boolean rim) {
        boolean tenForward = tng && rim;
        List<Band> bands = new ArrayList<>();
        int last = rings.length;
        for (int b = 0; b <= last; b++) {
            double rIn = b == 0 ? 5.5 : rings[b - 1] + 1.5;
            double rOut = b == last ? sR : rings[b] - 1.5;
            int n = Math.max(8, 8 * (int) Math.round(2 * Math.PI * (rIn + rOut) / 2 / 14 / 8));
            RoomType[] type = new RoomType[n];
            int[] group = new int[n]; // gleiche Nummer = ein gemeinsamer Raum
            for (int i = 0; i < n; i++) {
                group[i] = i;
                type[i] = RoomType.QUARTERS;
            }
            if (b == 0) {
                RoomType[] inner = tng ? tngInnerRooms(index) : innerRooms(index);
                for (int i = 0; i < n; i++) type[i] = inner[i * 8 / n];
                // Refit: das Freizeitdeck vorne über zwei Sektoren
                for (int i = 0; i + 1 < n; i++) if (type[i] == RoomType.REC_DECK && type[i + 1] == RoomType.REC_DECK) group[i + 1] = group[i];
                if (tng && (index == 2 || index == 4)) // Holodecks und Kartographie: je zwei Sektoren
                    for (int i = 0; i + 1 < n; i += 2) if (type[i] == type[i + 1] && type[i] != RoomType.LAB) group[i + 1] = i;
            } else if (tng && b == 1 && index == 2) {
                for (int i = 0; i < n; i++) type[i] = RoomType.HOLODECK;
                for (int i = 0; i + 1 < n; i += 2) group[i + 1] = i;
            } else if (tng && b == 1 && index == 3 && n % 16 == 0) {
                // Arboretum: vier Sektoren am Bug
                for (int i = n / 2 - 2; i < n / 2 + 2; i++) { type[i] = RoomType.ARBORETUM; group[i] = n / 2 - 2; }
            } else if (tng && b < last && index == 1) {
                for (int i = 0; i < n; i++) type[i] = RoomType.LAB;
            }
            if (refit && rim && b == last) {
                // Offiziers-Lounge der Kinofilme: hinten an der Untertasse mit großen Fenstern
                int k = Math.max(2, n / 8);
                for (int i = 0; i < k; i++) { type[i] = RoomType.LOUNGE; group[i] = 0; }
            }
            if (tenForward && b == last) {
                // Ten Forward: großer Raum vorne am Rand mit Panoramafenstern (ca. 70°)
                int k = Math.max(2, (int) Math.round(n * 70.0 / 360 / 2) * 2);
                for (int i = n / 2 - k / 2; i < n / 2 + k / 2; i++) { type[i] = RoomType.TEN_FORWARD; group[i] = n / 2 - k / 2; }
            }
            Room[] bySector = new Room[n];
            List<Room> rooms = new ArrayList<>();
            double w = 2 * Math.PI / n;
            for (int i = 0; i < n; ) {
                int j = i;
                while (j + 1 < n && group[j + 1] == group[i]) j++;
                Room room = new Room(rIn, rOut, -Math.PI + i * w, -Math.PI + (j + 1) * w, type[i]);
                rooms.add(room);
                for (int q = i; q <= j; q++) bySector[q] = room;
                i = j + 1;
            }
            bands.add(new Band(rIn, rOut, b > 0, bySector, rooms));
        }
        return bands;
    }

    private ShipPart saucerDeckCell(int x, int y, int z, double r, double a, int f, double[] rings, List<Band> bands) {
        ShipPart wall = ShipPart.CORRIDOR_WALL;
        boolean doorHeight = y <= f + 2;
        double perpX = Math.abs(z);                    // Abstand zur Längsachse
        double perpZ = Math.abs(x - sCx);              // Abstand zur Querachse
        boolean onAxis = perpX <= 1.2 || perpZ <= 1.2;
        double lastRing = rings[rings.length - 1];
        if (r <= 4.5) return ShipPart.AIR;                                     // Vorraum
        if (r <= 5.5) return onAxis ? ShipPart.AIR : wall;                     // Wand um den Vorraum
        int b = 0;
        for (double rc : rings) {
            if (Math.abs(r - rc) <= 1.0) return ShipPart.AIR;                  // Ringkorridor
            if (r > rc) b++;
        }
        if (r < lastRing && onAxis) return ShipPart.AIR;                       // Gänge
        if (r < lastRing && (Math.abs(perpX - 2) < 0.6 || Math.abs(perpZ - 2) < 0.6)) {
            // Gangwände, mit einer Tür in jeden Innenraum nahe dem Vorraum
            boolean door = doorHeight && r < rings[0] && Math.abs(Math.max(perpX, perpZ) - 9) <= 1.0;
            return door ? ShipPart.AIR : wall;
        }
        Band band = bands.get(b);
        Room room = band.at(a);
        boolean nearIn = b > 0 && Math.abs(r - band.rIn()) < 0.6;
        boolean nearOut = b < rings.length && Math.abs(r - band.rOut()) < 0.6;
        if (nearIn || nearOut) {
            double mid = (room.a0() + room.a1()) / 2;
            boolean door = (band.doorInside() ? nearIn : nearOut) && doorHeight && Math.abs(angDiff(a, mid) * r) <= 1.0;
            return door ? ShipPart.AIR : wall;
        }
        // Trennwände zwischen den Räumen
        if (Math.abs(angDiff(a, room.a0()) * r) < 0.55 || Math.abs(angDiff(room.a1(), a) * r) < 0.55) return wall;
        return ShipPart.AIR;
    }

    /**
     * Innenräume der Decks von NCC-1701, -A und -B (Sektoren ab dem Heck über Backbord, Bug, Steuerbord).
     * Refit: Freizeitdeck mit großem Bildschirm und Arboretum wie in "Star Trek: Der Film".
     * Alle: Arrestzellen ("Brig") mit Kraftfeld wie in der Originalserie.
     */
    private RoomType[] innerRooms(int index) {
        RoomType S = RoomType.STORAGE, L = RoomType.LAB, Q = RoomType.QUARTERS, B = RoomType.BRIG;
        if (nx) return switch (index) {
            // NX-01: Situationsraum, Bereitschaftsraum, Captain's Mess; Krankenstation mit Dekontamination
            case 0 -> new RoomType[] {Q, Q, RoomType.READY_ROOM, RoomType.BRIEFING, RoomType.BRIEFING, RoomType.MESS, Q, Q};
            case 1 -> new RoomType[] {L, RoomType.DECON, L, RoomType.SICKBAY, RoomType.SICKBAY, L, RoomType.DECON, L};
            case 2 -> new RoomType[] {RoomType.REC, RoomType.REC, RoomType.MESS, RoomType.MESS, RoomType.MESS, RoomType.MESS, RoomType.REC, RoomType.REC};
            case 3 -> new RoomType[] {B, S, S, L, L, S, S, B};
            default -> new RoomType[] {S, S, Q, Q, Q, Q, S, S};
        };
        return switch (index) {
            case 0 -> new RoomType[] {Q, Q, RoomType.READY_ROOM, RoomType.BRIEFING, RoomType.BRIEFING, Q, Q, Q};
            case 1 -> new RoomType[] {L, S, L, RoomType.SICKBAY, RoomType.SICKBAY, L, S, L};
            case 2 -> refit
                ? new RoomType[] {RoomType.REC, RoomType.REC, RoomType.MESS, RoomType.REC_DECK, RoomType.REC_DECK, RoomType.MESS, RoomType.REC, RoomType.REC}
                : new RoomType[] {RoomType.REC, RoomType.REC, RoomType.MESS, RoomType.MESS, RoomType.MESS, RoomType.MESS, RoomType.REC, RoomType.REC};
            case 3 -> refit
                ? new RoomType[] {B, L, RoomType.ARBORETUM, L, L, RoomType.ARBORETUM, L, B}
                : new RoomType[] {B, L, L, L, L, L, L, B};
            default -> new RoomType[] {S, S, Q, Q, Q, Q, S, S};
        };
    }

    /**
     * Innenräume der Next-Generation-Decks (Sektoren ab dem Heck über Backbord, Bug, Steuerbord) –
     * nach den Deckplänen der Galaxy-Klasse: Beobachtungslounge hinter der Brücke, Bereitschaftsraum,
     * Krankenstation, Holodecks, Arboretum, Stellare Kartographie, Arrest, Schule.
     */
    private static RoomType[] tngInnerRooms(int index) {
        RoomType S = RoomType.STORAGE, L = RoomType.LAB, Q = RoomType.QUARTERS, H = RoomType.HOLODECK;
        RoomType C = RoomType.CARTOGRAPHY, B = RoomType.BRIG, K = RoomType.CLASSROOM;
        return switch (index) {
            case 0 -> new RoomType[] {RoomType.BRIEFING, Q, RoomType.READY_ROOM, Q, Q, Q, Q, RoomType.BRIEFING};
            case 1 -> new RoomType[] {L, S, L, RoomType.SICKBAY, RoomType.SICKBAY, L, S, L};
            case 2 -> new RoomType[] {H, H, H, H, H, H, H, H};
            case 3 -> new RoomType[] {L, L, L, L, L, L, L, L};
            case 4 -> new RoomType[] {C, C, L, L, L, L, C, C};
            case 5 -> new RoomType[] {B, B, S, L, L, S, B, B};
            case 6 -> new RoomType[] {K, K, RoomType.REC, K, K, RoomType.REC, K, K};
            case 7 -> new RoomType[] {RoomType.REC, RoomType.REC, RoomType.MESS, RoomType.MESS, RoomType.MESS, RoomType.MESS, RoomType.REC, RoomType.REC};
            case 8, 9 -> new RoomType[] {RoomType.CARGO, RoomType.CARGO, S, S, S, S, RoomType.CARGO, RoomType.CARGO};
            default -> new RoomType[] {S, S, Q, Q, Q, Q, S, S};
        };
    }

    private String saucerDeckName(int index, int f) {
        boolean deeper = deckCell((int) Math.round(sCx), f - 3, 0);
        if (nx) return switch (index) {
            case 0 -> "Situationsraum, Bereitschaftsraum, Captain's Mess";
            case 1 -> "Krankenstation, Dekontamination";
            case 2 -> "Messe & Sporthalle";
            case 3 -> "Waffenkammer, Arrest";
            default -> deeper ? "Quartiere" : "Lager";
        };
        if (tng) return switch (index) {
            case 0 -> "Beobachtungslounge, Bereitschaftsraum";
            case 1 -> "Krankenstation";
            case 2 -> "Holodecks";
            case 3 -> "Arboretum";
            case 4 -> "Stellare Kartographie";
            case 5 -> "Sicherheit, Arrestzellen";
            case 6 -> "Schule";
            case 7 -> "Gesellschaftsräume";
            case 8, 9 -> "Frachträume " + (index == 8 ? "1–2" : "3–4");
            default -> deeper ? "Quartiere" : "Lager & Sensoren";
        };
        return switch (index) {
            case 0 -> "Besprechungsraum";
            case 1 -> "Krankenstation";
            case 2 -> refit ? "Freizeitdeck & Messe" : "Messe & Freizeitraum";
            case 3 -> refit ? "Labore, Arboretum, Arrestzellen" : "Wissenschaftslabore, Arrestzellen";
            default -> deeper ? "Mannschaftsquartiere" : "Lager & Sensoren";
        };
    }

    /** Liefert die freien Bodenzellen (Luft über Boden) eines Raums. */
    private List<int[]> roomCells(Room room, int f) {
        List<int[]> cells = new ArrayList<>();
        int cx = (int) Math.round(sCx);
        double rr = Math.min(room.rOut(), sR);
        int rx = (int) Math.ceil(rr / kx), rz = (int) Math.ceil(rr / kz);
        for (int x = cx - rx; x <= cx + rx; x++)
            for (int z = -rz; z <= rz; z++) {
                double r = rad(x, z);
                if (r <= room.rIn() + 0.6 || r >= room.rOut() - 0.6) continue;
                double a = angle(x, z);
                double d0 = angDiff(a, room.a0()), d1 = angDiff(room.a1(), a);
                if (d0 * r < 0.8 || d1 * r < 0.8) continue;
                if (get(x, f + 1, z) == ShipPart.AIR && get(x, f + 2, z) == ShipPart.AIR && deckCell(x, f + 1, z)) cells.add(new int[] {x, z});
            }
        return cells;
    }

    private Direction towards(int x, int z, double tx, double tz) {
        double dx = tx - x, dz = tz - z;
        if (Math.abs(dx) >= Math.abs(dz)) return dx < 0 ? Direction.NORTH : Direction.SOUTH;
        return dz < 0 ? Direction.WEST : Direction.EAST;
    }

    private boolean free(int x, int y, int z) {
        return get(x, y, z) == ShipPart.AIR && get(x, y + 1, z) == ShipPart.AIR;
    }

    private static final int[][] NEIGHBORS4 = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    /** Färbt die Wände rund um die Raumzellen (Höhe f+1 bis f+3) um. */
    private void recolorWalls(List<int[]> cells, int f, ShipPart from, ShipPart to) {
        for (int[] c : cells)
            for (int[] d : NEIGHBORS4)
                for (int y = f + 1; y <= f + 3; y++)
                    if (get(c[0] + d[0], y, c[1] + d[1]) == from) set(c[0] + d[0], y, c[1] + d[1], to);
    }

    /** Möbliert einen Raum je nach Typ. */
    private void furnish(Room room, int f) {
        List<int[]> cells = roomCells(room, f);
        if (cells.size() < 6) return;
        double am = (room.a0() + room.a1()) / 2;
        int y = f + 1;
        // Schwerpunkt
        double sx = 0, sz = 0;
        for (int[] c : cells) { sx += c[0]; sz += c[1]; }
        int gx = (int) Math.round(sx / cells.size()), gz = (int) Math.round(sz / cells.size());
        double rOutUse = Math.min(room.rOut(), sR);
        // jede Kabine bekommt mindestens eine Deckenlampe in der Mitte
        if (get(gx, f + 3, gz) == ShipPart.AIR && deckCell(gx, f + 3, gz)) set(gx, f + 3, gz, ShipPart.CEILING_LIGHT);
        switch (room.type()) {
            case QUARTERS -> {
                // Bett an der Außenseite, Schreibtisch mit Stuhl innen
                int[] bed = nearest(cells, polX(rOutUse - 3, am), polZ(rOutUse - 3, am));
                int[] desk = nearest(cells, polX(room.rIn() + 2.5, am), polZ(room.rIn() + 2.5, am) + 1.5);
                if (bed != null) set(bed[0], y, bed[1], ShipPart.CREW_BED, towards(bed[0], bed[1], gx, gz));
                if (desk != null && (bed == null || desk[0] != bed[0] || desk[1] != bed[1])) {
                    Direction d = towards(desk[0], desk[1], polX(rOutUse, am), polZ(rOutUse, am)).getOpposite();
                    set(desk[0], y, desk[1], ShipPart.CREW_DESK, d);
                    int chx = desk[0] + (d == Direction.NORTH ? -1 : d == Direction.SOUTH ? 1 : 0);
                    int chz = desk[1] + (d == Direction.WEST ? -1 : d == Direction.EAST ? 1 : 0);
                    if (free(chx, y, chz)) set(chx, y, chz, tng ? ShipPart.TNG_CHAIR : ShipPart.STATION_CHAIR, d.getOpposite());
                }
                // TNG-Quartiere sind geräumiger: Sitzecke mit Tisch und zwei Sesseln
                if (tng && free(gx, y, gz) && free(gx - 1, y, gz) && free(gx + 1, y, gz)) {
                    set(gx, y, gz, ShipPart.MESS_TABLE);
                    set(gx - 1, y, gz, ShipPart.TNG_CHAIR, Direction.SOUTH);
                    set(gx + 1, y, gz, ShipPart.TNG_CHAIR, Direction.NORTH);
                }
            }
            case BRIEFING -> {
                // Langer Konferenztisch mit dreiseitigem Bildschirm in der Mitte (wie in der Serie)
                ShipPart chair = tng ? ShipPart.TNG_CHAIR : ShipPart.STATION_CHAIR;
                for (int dx = -3; dx <= 3; dx++)
                    for (int dz = 0; dz <= 1; dz++)
                        if (free(gx + dx, y, gz + dz)) set(gx + dx, y, gz + dz, ShipPart.CONFERENCE_TABLE);
                if (get(gx, y, gz) == ShipPart.CONFERENCE_TABLE && !tng) set(gx, y + 1, gz, ShipPart.TABLE_VIEWER);
                for (int dx = -3; dx <= 3; dx += 2) {
                    if (free(gx + dx, y, gz - 1)) set(gx + dx, y, gz - 1, chair, Direction.EAST);
                    if (free(gx + dx, y, gz + 2)) set(gx + dx, y, gz + 2, chair, Direction.WEST);
                }
                if (free(gx - 4, y, gz)) set(gx - 4, y, gz, chair, Direction.SOUTH);
                if (free(gx + 4, y, gz + 1)) set(gx + 4, y, gz + 1, chair, Direction.NORTH);
                if (tng) {
                    // Beobachtungslounge: Wandbildschirm an der Stirnseite, Modelle der früheren Enterprises
                    int[] scr = nearest(cells, gx - 6, gz);
                    if (scr != null && get(scr[0] - 1, y + 1, scr[1]) == ShipPart.CORRIDOR_WALL)
                        set(scr[0] - 1, y + 1, scr[1], ShipPart.LCARS_PANEL, Direction.SOUTH);
                }
            }
            case READY_ROOM -> {
                if (free(gx, y, gz)) set(gx, y, gz, ShipPart.CREW_DESK, Direction.SOUTH);
                if (free(gx + 1, y, gz)) set(gx + 1, y, gz, tng ? ShipPart.CAPTAINS_CHAIR_TNG : ShipPart.CAPTAINS_CHAIR, Direction.NORTH);
                if (free(gx, y, gz + 2)) set(gx, y, gz + 2, ShipPart.LCARS_PANEL, Direction.WEST);
                if (tng) {
                    // Sofa unter dem Fenster und ein Aquarium (Picards Feuerfisch)
                    int[] sofa = nearest(cells, polX(rOutUse - 2, am), polZ(rOutUse - 2, am));
                    if (sofa != null && free(sofa[0], y, sofa[1])) set(sofa[0], y, sofa[1], ShipPart.TNG_CHAIR, towards(sofa[0], sofa[1], gx, gz));
                    int[] tank = nearest(cells, polX(room.rIn() + 2, am), polZ(room.rIn() + 2, am));
                    if (tank != null && free(tank[0], y, tank[1])) set(tank[0], y, tank[1], ShipPart.VIEWPORT);
                }
            }
            case SICKBAY -> buildSickbay(room, f);
            case MESS, REC -> {
                ShipPart chair = tng ? ShipPart.TNG_CHAIR : ShipPart.STATION_CHAIR;
                for (int[] c : cells) {
                    if (Math.floorMod(c[0], 4) != 0 || Math.floorMod(c[1], 4) != 0 || !free(c[0], y, c[1])) continue;
                    if (!free(c[0] - 1, y, c[1]) || !free(c[0] + 1, y, c[1])) continue;
                    set(c[0], y, c[1], ShipPart.MESS_TABLE);
                    set(c[0] - 1, y, c[1], chair, Direction.SOUTH);
                    set(c[0] + 1, y, c[1], chair, Direction.NORTH);
                }
                if (room.type() == RoomType.REC && free(gx, y, gz)) set(gx, y, gz, ShipPart.LCARS_PANEL, Direction.NORTH);
            }
            case LAB -> {
                ShipPart a = tng ? ShipPart.TNG_CONSOLE : ShipPart.ENGINEERING_CONSOLE;
                for (int[] c : cells)
                    if (Math.floorMod(c[0], 3) == 0 && Math.floorMod(c[1], 5) == 0 && free(c[0], y, c[1]))
                        set(c[0], y, c[1], Math.floorMod(c[0] + c[1], 2) == 0 ? a : ShipPart.BRIDGE_STATION,
                            Direction.SOUTH, Math.floorMod(c[0] * 7 + c[1], 4));
            }
            case STORAGE, CARGO -> {
                for (int[] c : cells) {
                    if (Math.floorMod(c[0], 3) == 0 || Math.floorMod(c[1], 4) == 0 || !free(c[0], y, c[1])) continue;
                    if (rad(c[0], c[1]) > Math.min(room.rOut(), sR) - 3.5) continue; // Platz an der Tür
                    set(c[0], y, c[1], ShipPart.CARGO_CONTAINER);
                    if (Math.floorMod(c[0] * 31 + c[1] * 17, 3) != 0 && get(c[0], y + 1, c[1]) == ShipPart.AIR) set(c[0], y + 1, c[1], ShipPart.CARGO_CONTAINER);
                }
            }
            case TEN_FORWARD -> buildTenForward(room, cells, f);
            case HOLODECK -> {
                // Schwarzer Raum mit gelbem Gitter an Wänden, Boden und Decke, Bedienfeld neben der Tür
                recolorWalls(cells, f, ShipPart.CORRIDOR_WALL, ShipPart.HOLODECK_GRID);
                for (int[] c : cells) {
                    set(c[0], f, c[1], ShipPart.HOLODECK_GRID);
                    if (compAt(c[0], f + 4, c[1]) != NONE) set(c[0], f + 3, c[1], ShipPart.HOLODECK_GRID);
                }
                double rd = room.rIn() < 6 ? rOutUse - 1.2 : room.rIn() + 1.2; // neben der Tür
                int[] pnl = nearest(cells, polX(rd, am + 2.2 / rd), polZ(rd, am + 2.2 / rd));
                if (pnl != null) {
                    Direction in = towards(pnl[0], pnl[1], gx, gz);
                    int wx = pnl[0] - stepX(in), wz = pnl[1] - stepZ(in);
                    if (get(wx, y + 1, wz) == ShipPart.HOLODECK_GRID) set(wx, y + 1, wz, ShipPart.LCARS_PANEL, in);
                }
            }
            case ARBORETUM -> {
                // Moosboden, Blumen, Farne und kleine Bäume unter Pflanzenlampen
                for (int[] c : cells) {
                    set(c[0], f, c[1], ShipPart.GRASS);
                    int h = Math.floorMod(c[0] * 73856093 ^ c[1] * 19349663, 97);
                    if (Math.floorMod(c[0], 6) == 0 && Math.floorMod(c[1], 6) == 3 && compAt(c[0], f + 4, c[1]) != NONE) {
                        set(c[0], y, c[1], ShipPart.TREE_LOG);
                        set(c[0], y + 1, c[1], ShipPart.TREE_LOG);
                        set(c[0], y + 2, c[1], ShipPart.LEAVES);
                        for (int[] d : NEIGHBORS4)
                            for (int ly = y + 1; ly <= y + 2; ly++)
                                if (get(c[0] + d[0], ly, c[1] + d[1]) == ShipPart.AIR) set(c[0] + d[0], ly, c[1] + d[1], ShipPart.LEAVES);
                    } else if (h < 35 && get(c[0], y, c[1]) == ShipPart.AIR) {
                        set(c[0], y, c[1], ShipPart.FLOWER, Direction.NORTH, h & 3);
                    }
                }
            }
            case CARTOGRAPHY -> {
                // Stellare Kartographie: Sternenkarte rundum, Steuerpult mit Geländer in der Mitte
                recolorWalls(cells, f, ShipPart.CORRIDOR_WALL, ShipPart.STAR_MAP);
                for (int[] c : cells) {
                    set(c[0], f, c[1], ShipPart.STAR_MAP);
                    if (compAt(c[0], f + 4, c[1]) != NONE) set(c[0], f + 3, c[1], ShipPart.STAR_MAP);
                }
                if (free(gx, y, gz)) {
                    Direction d = towards(gx, gz, polX(rOutUse, am), polZ(rOutUse, am)).getOpposite();
                    set(gx, y, gz, ShipPart.TNG_CONSOLE, d);
                    for (int dx = -2; dx <= 2; dx++)
                        for (int dz = -2; dz <= 2; dz++)
                            if ((Math.abs(dx) == 2 || Math.abs(dz) == 2) && !(dx == 2 * stepX(d) && dz == 2 * stepZ(d)) && free(gx + dx, y, gz + dz))
                                set(gx + dx, y, gz + dz, ShipPart.IRON_BARS);
                }
            }
            case BRIG -> buildBrig(room, cells, f);
            case DECON -> {
                // Dekontaminationskammer der NX-01: blau leuchtende Wände, Bank in der Mitte
                recolorWalls(cells, f, ShipPart.CORRIDOR_WALL, ShipPart.DECON_LIGHT);
                for (int[] c : cells) if (compAt(c[0], f + 4, c[1]) != NONE) set(c[0], f + 3, c[1], ShipPart.DECON_LIGHT);
                if (free(gx, y, gz)) set(gx, y, gz, ShipPart.CREW_BED, Direction.NORTH);
            }
            case REC_DECK -> {
                // Freizeitdeck (Star Trek: Der Film): große Bildwand mit Bildern früherer Schiffe an der
                // Außenwand, davor Sitzreihen
                Direction face = towards(gx, gz, polX(rOutUse, am), polZ(rOutUse, am));
                for (int[] c : cells)
                    for (int[] d : NEIGHBORS4)
                        for (int wy = f + 1; wy <= f + 3; wy++) {
                            int wx = c[0] + d[0], wz = c[1] + d[1];
                            if (get(wx, wy, wz) == ShipPart.CORRIDOR_WALL && rad(wx, wz) > rOutUse - 1.5)
                                set(wx, wy, wz, ShipPart.BRIDGE_MONITOR, towards(wx, wz, gx, gz), Math.floorMod(wx + wz + wy, 4));
                        }
                for (int[] c : cells) {
                    double r = rad(c[0], c[1]);
                    if (r < rOutUse - 4 && Math.floorMod((int) Math.round(r), 3) == 0 && Math.floorMod(c[0] + c[1], 2) == 0 && free(c[0], y, c[1]))
                        set(c[0], y, c[1], ShipPart.STATION_CHAIR, face);
                }
            }
            case LOUNGE -> {
                // Offiziers-Lounge: Panoramafenster, Sitzgruppen
                for (int[] c : cells)
                    if (Math.floorMod(c[0], 4) == 0 && Math.floorMod(c[1], 4) == 0 && free(c[0], y, c[1]) && free(c[0] - 1, y, c[1]) && free(c[0] + 1, y, c[1])) {
                        set(c[0], y, c[1], ShipPart.MESS_TABLE);
                        set(c[0] - 1, y, c[1], ShipPart.STATION_CHAIR, Direction.SOUTH);
                        set(c[0] + 1, y, c[1], ShipPart.STATION_CHAIR, Direction.NORTH);
                    }
                windows(cells, f);
            }
            case CLASSROOM -> {
                // Pulte in Reihen, Blick zur Wandtafel (LCARS) an der Innenseite
                Direction face = towards(gx, gz, polX(room.rIn(), am), polZ(room.rIn(), am));
                for (int[] c : cells) {
                    if (Math.floorMod(c[0], 3) != 0 || Math.floorMod(c[1], 3) != 0) continue;
                    int bx = c[0] - stepX(face), bz = c[1] - stepZ(face);
                    if (!free(c[0], y, c[1]) || !free(bx, y, bz)) continue;
                    set(c[0], y, c[1], ShipPart.CREW_DESK, face.getOpposite());
                    set(bx, y, bz, ShipPart.TNG_CHAIR, face);
                }
                int[] board = nearest(cells, polX(room.rIn() + 1, am), polZ(room.rIn() + 1, am));
                if (board != null) {
                    int wx = board[0] + stepX(face), wz = board[1] + stepZ(face);
                    if (get(wx, y + 1, wz) == ShipPart.CORRIDOR_WALL) set(wx, y + 1, wz, ShipPart.LCARS_PANEL, face.getOpposite());
                }
            }
        }
    }

    /**
     * Ten Forward: Bar mit geschwungenem Tresen an der Innenwand, Tische mit Sesseln und große
     * Panoramafenster nach vorne hinaus.
     */
    private void buildTenForward(Room room, List<int[]> cells, int f) {
        int y = f + 1;
        double am = (room.a0() + room.a1()) / 2;
        double span = (room.a1() - room.a0()) * room.rIn();
        for (int[] c : cells) {
            double r = rad(c[0], c[1]);
            double t = angDiff(angle(c[0], c[1]), am) * r;
            if (r > room.rIn() + 2 && r < room.rIn() + 3 && Math.abs(t) > 3 && Math.abs(t) < span * 0.4) set(c[0], y, c[1], ShipPart.BAR_COUNTER);
            else if (r > room.rIn() + 6 && Math.floorMod(c[0], 4) == 0 && Math.floorMod(c[1], 4) == 0
                && free(c[0], y, c[1]) && free(c[0] - 1, y, c[1]) && free(c[0] + 1, y, c[1])) {
                set(c[0], y, c[1], ShipPart.MESS_TABLE);
                set(c[0] - 1, y, c[1], ShipPart.TNG_CHAIR, Direction.SOUTH);
                set(c[0] + 1, y, c[1], ShipPart.TNG_CHAIR, Direction.NORTH);
            }
        }
        windows(cells, f);
    }

    /** Panoramafenster: Außenhülle neben den Raumzellen durch Glas ersetzen. */
    private void windows(List<int[]> cells, int f) {
        for (int[] c : cells)
            for (int[] d : NEIGHBORS4)
                for (int wy = f + 1; wy <= f + 3; wy++) {
                    int wx = c[0] + d[0], wz = c[1] + d[1];
                    if (!inBounds(wx, wy, wz) || !hull[idx(wx, wy, wz)]) continue;
                    if (compAt(wx + d[0], wy, wz + d[1]) == NONE || compAt(wx + 2 * d[0], wy, wz + 2 * d[1]) == NONE)
                        set(wx, wy, wz, ShipPart.VIEWPORT);
                }
    }

    /** Arrestbereich: Zellen mit Kraftfeld an der Außenseite, je eine Pritsche, Wachpult an der Tür. */
    private void buildBrig(Room room, List<int[]> cells, int f) {
        int y = f + 1;
        double rOut = Math.min(room.rOut(), sR);
        double rf = room.rIn() + 0.55 * (rOut - room.rIn()); // Linie der Kraftfelder
        double span = (room.a1() - room.a0()) * rf;
        int nCells = Math.max(1, (int) (span / 4));
        boolean doorOut = room.rIn() < 6; // innerster Ring: Tür außen zum Ringkorridor
        for (int[] c : cells) {
            double r = rad(c[0], c[1]);
            double t = angDiff(angle(c[0], c[1]), room.a0()) * r;
            if (Math.abs(r - rf) < 0.6) {
                for (int yy = y; yy <= y + 2; yy++) set(c[0], yy, c[1], ShipPart.FORCE_FIELD);
            } else if (doorOut ? r < rf : r > rf) {
                double cell = t / (span / nCells);
                if (Math.abs(cell - Math.round(cell)) * (span / nCells) < 0.55 && Math.round(cell) > 0 && Math.round(cell) < nCells)
                    for (int yy = y; yy <= y + 2; yy++) set(c[0], yy, c[1], ShipPart.CORRIDOR_WALL);
            }
        }
        double am = (room.a0() + room.a1()) / 2;
        for (int k = 0; k < nCells; k++) {
            double a = room.a0() + (room.a1() - room.a0()) * (k + 0.5) / nCells;
            double rb = doorOut ? room.rIn() + 2 : rOut - 2;
            int[] bunk = nearest(cells, polX(rb, a), polZ(rb, a));
            if (bunk != null && free(bunk[0], y, bunk[1])) set(bunk[0], y, bunk[1], ShipPart.CREW_BED, towards(bunk[0], bunk[1], sCx, 0));
        }
        double rd = doorOut ? rOut - 2.5 : room.rIn() + 2.5;
        int[] desk = nearest(cells, polX(rd, am + 2.5 / rd), polZ(rd, am + 2.5 / rd));
        if (desk != null && free(desk[0], y, desk[1])) set(desk[0], y, desk[1], tng ? ShipPart.TNG_CONSOLE : ShipPart.ENGINEERING_CONSOLE, towards(desk[0], desk[1], polX(rOut, am), polZ(rOut, am)).getOpposite());
    }

    /** Biobett (2 Blöcke): Kopfende an der Wand, Fußende in Richtung {@code foot}, Monitor in der Wand darüber. */
    private boolean placeBiobed(int hx, int hz, Direction foot, int f) {
        int y = f + 1;
        int fx = hx + stepX(foot), fz = hz + stepZ(foot);
        if (!free(hx, y, hz) || !free(fx, y, fz)) return false;
        set(hx, y, hz, ShipPart.BIOBED, foot, 1);
        set(fx, y, fz, ShipPart.BIOBED, foot, 0);
        int wx = hx - stepX(foot), wz = hz - stepZ(foot);
        ShipPart wall = get(wx, y + 1, wz);
        if (wall == ShipPart.SICKBAY_WALL || wall == ShipPart.CORRIDOR_WALL) set(wx, y + 1, wz, ShipPart.BIOBED_MONITOR, foot);
        return true;
    }

    /** Lokale Schrittweite (Bug = NORDEN = -X, Steuerbord = OSTEN = +Z). */
    private static int stepX(Direction d) {
        return d == Direction.NORTH ? -1 : d == Direction.SOUTH ? 1 : 0;
    }

    private static int stepZ(Direction d) {
        return d == Direction.WEST ? -1 : d == Direction.EAST ? 1 : 0;
    }

    /**
     * Krankenstation nach dem Vorbild der Originalserie: hellblaue Wände, grüner Teppich,
     * Hauptraum mit Biobetten an der Gangwand und Untersuchungstisch, außen Einzelzimmer mit je
     * einem Biobett (Kopfende an der Wand, Diagnosemonitor darüber) und das Büro des Doktors.
     */
    private void buildSickbay(Room room, int f) {
        double span = room.a1() - room.a0();
        double am = (room.a0() + room.a1()) / 2;
        int side = am > 0 ? 1 : -1;            // Steuerbord- oder Backbordhälfte (Gang liegt bei z = 0)
        double rOut = Math.min(room.rOut(), sR);
        double rp = room.rIn() + 0.55 * (rOut - room.rIn()); // Wand zwischen Hauptraum und Einzelzimmern
        int cx = (int) Math.round(sCx);
        int rrx = (int) Math.ceil(rOut / kx) + 2, rrz = (int) Math.ceil(rOut / kz) + 2;

        // 1) Farben und Zwischenwände
        for (int x = cx - rrx; x <= cx + rrx; x++)
            for (int z = -rrz; z <= rrz; z++) {
                double r = rad(x, z);
                double a = angle(x, z);
                double d0 = angDiff(a, room.a0()) * r, d1 = angDiff(room.a1(), a) * r;
                if (r < room.rIn() - 1.2 || r > rOut + 1.2 || d0 < -1.6 || d1 < -1.6) continue;
                boolean inside = r > room.rIn() + 0.6 && r < rOut - 0.6 && d0 > 0.6 && d1 > 0.6;
                if (inside && isInterior(x, f, z)) set(x, f, z, ShipPart.SICKBAY_FLOOR);
                for (int y = f + 1; y <= f + 3; y++) {
                    if (!isInterior(x, y, z)) continue;
                    ShipPart cur = get(x, y, z);
                    if (cur == ShipPart.CORRIDOR_WALL) { set(x, y, z, ShipPart.SICKBAY_WALL); continue; }
                    if (!inside) continue;
                    if (cur != ShipPart.AIR && cur != ShipPart.CEILING_LIGHT) set(x, y, z, ShipPart.AIR); // alte Möbel weg
                    boolean doorRow = y <= f + 2;
                    if (Math.abs(r - rp) < 0.6) {
                        // Einzelzimmer-Türen in der Mitte jedes Zimmers
                        boolean door = false;
                        for (int k = 0; k < 3; k++) {
                            double mid = room.a0() + span * (k + 0.5) / 3;
                            door |= Math.abs(angDiff(a, mid) * r) <= 0.8;
                        }
                        set(x, y, z, door && doorRow ? ShipPart.AIR : ShipPart.SICKBAY_WALL);
                    } else if (r > rp) {
                        for (int k = 1; k < 3; k++)
                            if (Math.abs(angDiff(a, room.a0() + span * k / 3) * r) < 0.55) set(x, y, z, ShipPart.SICKBAY_WALL);
                    }
                }
            }

        // 2) Einzelzimmer: Bett mit dem Kopfende an der Außenwand; das Zimmer am Gang wird Büro
        int officeK = side > 0 ? 0 : 2;
        for (int k = 0; k < 3; k++) {
            double mid = room.a0() + span * (k + 0.5) / 3;
            double hr = rOut - 1.6;
            int hx = (int) Math.round(polX(hr, mid)), hz = (int) Math.round(polZ(hr, mid));
            Direction foot = towards(hx, hz, sCx, 0);
            if (k == officeK) {
                double dr = (rp + rOut) / 2;
                int dx = (int) Math.round(polX(dr, mid)), dz = (int) Math.round(polZ(dr, mid));
                if (free(dx, f + 1, dz)) {
                    set(dx, f + 1, dz, ShipPart.CREW_DESK, foot.getOpposite());
                    int chx = dx + stepX(foot.getOpposite()) * -1, chz = dz + stepZ(foot.getOpposite()) * -1;
                    if (free(chx, f + 1, chz)) set(chx, f + 1, chz, ShipPart.STATION_CHAIR, foot.getOpposite());
                }
                int cbx = hx - stepX(foot), cbz = hz - stepZ(foot);
                if (get(cbx, f + 2, cbz) == ShipPart.SICKBAY_WALL) set(cbx, f + 2, cbz, ShipPart.MEDICAL_CABINET, foot);
                continue;
            }
            // Kopfende direkt an die Wand rücken
            for (int t = 0; t < 3 && free(hx - stepX(foot), f + 1, hz - stepZ(foot)); t++) {
                hx -= stepX(foot);
                hz -= stepZ(foot);
            }
            placeBiobed(hx, hz, foot, f);
        }

        // 3) Hauptraum: Biobetten mit dem Kopfende an der Gangwand, Untersuchungstisch in der Mitte
        Direction foot = side > 0 ? Direction.EAST : Direction.WEST;
        for (int x = cx - 7; x >= cx - (int) (rp / kx) + 1; x -= 3) {
            if (Math.abs(x - (cx - 9)) <= 1) continue; // Tür vom Gang
            int hz = side * 3;
            double r1 = rad(x, side * 4);
            double a1 = angle(x, side * 4);
            boolean inRoom = r1 < rp - 1.2 && angDiff(a1, room.a0()) * r1 > 1 && angDiff(room.a1(), a1) * r1 > 1;
            if (!inRoom) continue;
            placeBiobed(x, hz, foot, f);
        }
        double wr = (room.rIn() + rp) / 2 + 1;
        int tx = (int) Math.round(polX(wr, am)), tz = (int) Math.round(polZ(wr, am));
        for (int dx = 0; dx <= 1; dx++) if (free(tx - dx, f + 1, tz)) set(tx - dx, f + 1, tz, ShipPart.EXAM_TABLE, foot);
        if (free(tx, f + 2, tz) && get(tx, f + 3, tz) == ShipPart.AIR && compAt(tx, f + 4, tz) != NONE) set(tx, f + 3, tz, ShipPart.CEILING_LIGHT);
        // Medizinschränke an der Wand zu den Einzelzimmern
        for (double da : new double[] {-span / 4, span / 4}) {
            double cr = rp - 1;
            int mx = (int) Math.round(polX(cr, am + da)), mz = (int) Math.round(polZ(cr, am + da));
            Direction in = towards(mx, mz, sCx, 0);
            int wx = mx - stepX(in), wz = mz - stepZ(in);
            if (get(wx, f + 2, wz) == ShipPart.SICKBAY_WALL) set(wx, f + 2, wz, ShipPart.MEDICAL_CABINET, in);
        }
    }

    private static int[] nearest(List<int[]> cells, double tx, double tz) {
        int[] best = null;
        double bd = Double.MAX_VALUE;
        for (int[] c : cells) {
            double d = (c[0] - tx) * (c[0] - tx) + (c[1] - tz) * (c[1] - tz);
            if (d < bd) { bd = d; best = c; }
        }
        return best;
    }

    /** Decks der Sekundärhülle: Mittelgang mit Turbolift vorne, Räume links und rechts. */
    private void layoutSecondaryDecks() {
        int xs = (int) Math.round(eXf + 0.13 * (eXr - eXf));
        int xe = (int) Math.round(eXr - 22 * s) - 1;
        String[] names = {"Frachtraum", "Quartiere", "Labore", "Lager"};
        for (int f = bridgeFloor - DECK_HEIGHT; f > 0; f -= DECK_HEIGHT) {
            if (compAt(xs, f + 1, 0) != SECONDARY) continue;
            boolean ok = true;
            for (int y = f + 1; y <= f + 3; y++) ok &= isInterior(xs, y, 0) && isInterior(xs + 1, y, 0);
            if (!ok) continue;
            int deck = deckNumber(f);
            RoomType type = switch (Math.floorMod(deck, 4)) {
                case 0 -> RoomType.CARGO;
                case 1 -> RoomType.QUARTERS;
                case 2 -> RoomType.LAB;
                default -> RoomType.STORAGE;
            };
            for (int x = xs; x <= xe; x++)
                for (int z = -zOffset; z <= zOffset; z++) {
                    if (compAt(x, f + 1, z) != SECONDARY) continue;
                    for (int y = f + 1; y <= f + 3; y++) {
                        if (!isInterior(x, y, z) || compAt(x, y, z) != SECONDARY) continue;
                        int az = Math.abs(z);
                        ShipPart st = ShipPart.AIR;
                        boolean doorPos = Math.floorMod(x - xs, 8) == 4 && y <= f + 2;
                        if (az == 2 && !doorPos) st = ShipPart.CORRIDOR_WALL;
                        else if (az > 2 && Math.floorMod(x - xs, 8) == 0) st = ShipPart.CORRIDOR_WALL;
                        set(x, y, z, st);
                    }
                    if (Math.floorMod(x, 4) == 0 && Math.floorMod(z, 4) == 0 && get(x, f + 3, z) == ShipPart.AIR
                        && isInterior(x, f + 4, z)) set(x, f + 3, z, ShipPart.CEILING_LIGHT);
                }
            // Räume möblieren (Zellen links/rechts des Gangs, zwischen den Trennwänden)
            for (int x0 = xs; x0 < xe; x0 += 8)
                for (int side : new int[] {-1, 1}) furnishSecondaryRoom(type, x0, side, f);
            set(xs, f + 1, 0, ShipPart.TURBOLIFT, Direction.SOUTH);
            set(xs, f + 2, 0, ShipPart.TURBOLIFT_DOOR, Direction.SOUTH);
            liftStops.add(new LiftStop(new BlockPos(xs, f + 1, 0), deckLabel(deck) + " – " + names[Math.floorMod(deck, 4)]));
        }
    }

    private void furnishSecondaryRoom(RoomType type, int x0, int side, int f) {
        int y = f + 1;
        for (int x = x0 + 1; x < x0 + 8; x++)
            for (int az = 3; az <= zOffset; az++) {
                int z = az * side;
                if (!free(x, y, z) || !isInterior(x, y, z) || compAt(x, y, z) != SECONDARY) continue;
                boolean nearDoor = Math.abs(x - (x0 + 4)) <= 1 && az <= 4;
                if (nearDoor) continue;
                switch (type) {
                    case CARGO, STORAGE -> {
                        if (Math.floorMod(x, 3) != 0 && az >= 5) {
                            set(x, y, z, ShipPart.CARGO_CONTAINER);
                            if (type == RoomType.CARGO && free(x, y + 1, z) && isInterior(x, y + 1, z)) set(x, y + 1, z, ShipPart.CARGO_CONTAINER);
                        }
                    }
                    case QUARTERS -> {
                        if (x == x0 + 2 && az == 5) set(x, y, z, ShipPart.CREW_BED, side > 0 ? Direction.WEST : Direction.EAST);
                        if (x == x0 + 6 && az == 5) set(x, y, z, ShipPart.CREW_DESK, side > 0 ? Direction.WEST : Direction.EAST);
                    }
                    case LAB -> {
                        if (az == 5 && Math.floorMod(x, 2) == 0)
                            set(x, y, z, ShipPart.ENGINEERING_CONSOLE, side > 0 ? Direction.WEST : Direction.EAST);
                    }
                    default -> {}
                }
            }
    }

    /** Next Generation: beige Wände und Teppichboden statt der grauen Korridore (außer im Maschinenraum). */
    private void tngInterior() {
        for (int x = 0; x < sizeX; x++)
            for (int y = 0; y < sizeY; y++)
                for (int z = -zOffset; z <= zOffset; z++) {
                    ShipPart p = get(x, y, z);
                    if (p == ShipPart.CORRIDOR_WALL) set(x, y, z, ShipPart.TNG_WALL);
                    else if (p == ShipPart.DECK_FLOOR && !inEngineeringBox(x, y, z)) set(x, y, z, ShipPart.TNG_CARPET);
                }
    }

    /** Turbolift-Panels, -Türen und -Rahmen im Stil der jeweiligen Epoche (Originalserie bleibt rot). */
    private void liftStyle() {
        ShipPart lift, door, frame;
        switch (design.era) {
            case NX -> { lift = ShipPart.TURBOLIFT_NX; door = ShipPart.TURBOLIFT_DOOR_NX; frame = ShipPart.ENT_WALL; }
            case REFIT -> { lift = ShipPart.TURBOLIFT_REFIT; door = ShipPart.TURBOLIFT_DOOR_REFIT; frame = ShipPart.BRIDGE_TRIM_BLUE; }
            case EXCELSIOR -> { lift = ShipPart.TURBOLIFT_EXCELSIOR; door = ShipPart.TURBOLIFT_DOOR_TEAL; frame = ShipPart.LIGHT_PILLAR; }
            case TNG -> { lift = ShipPart.TURBOLIFT_TNG; door = ShipPart.TURBOLIFT_DOOR_TNG; frame = ShipPart.DECK_PLATE; }
            default -> { lift = ShipPart.TURBOLIFT; door = ShipPart.TURBOLIFT_DOOR; frame = ShipPart.TURBOLIFT_FRAME; }
        }
        for (int x = 0; x < sizeX; x++)
            for (int y = 0; y < sizeY; y++)
                for (int z = -zOffset; z <= zOffset; z++) {
                    int code = codeAt(x, y, z);
                    if (code == 0) continue;
                    ShipPart p = part(code);
                    if (p == ShipPart.TURBOLIFT) set(x, y, z, lift, facing(code));
                    else if (p == ShipPart.TURBOLIFT_DOOR) set(x, y, z, door, facing(code));
                    else if (p == ShipPart.TURBOLIFT_FRAME) set(x, y, z, frame);
                }
        // Turbolift = zwei Blöcke wie eine Tür: über jedem Bedienfeld die obere Hälfte
        for (int x = 0; x < sizeX; x++)
            for (int y = 0; y + 1 < sizeY; y++)
                for (int z = -zOffset; z <= zOffset; z++) {
                    int code = codeAt(x, y, z);
                    if (code != 0 && part(code) == lift && variant(code) == 0) set(x, y + 1, z, lift, facing(code), 1);
                }
    }

    /** Ist das Bauteil ein Turbolift (in irgendeinem Stil)? */
    public static boolean isLift(ShipPart p) {
        // (untere Hälfte = Variante 0, das prüft der Aufrufer bei Bedarf)
        return p == ShipPart.TURBOLIFT || p == ShipPart.TURBOLIFT_NX || p == ShipPart.TURBOLIFT_REFIT
            || p == ShipPart.TURBOLIFT_EXCELSIOR || p == ShipPart.TURBOLIFT_TNG;
    }

    /** NX-01: graue Metallwände und Gitterboden, schlichte Sitze und Konsolen. */
    private void nxInterior() {
        for (int x = 0; x < sizeX; x++)
            for (int y = 0; y < sizeY; y++)
                for (int z = -zOffset; z <= zOffset; z++) {
                    int code = codeAt(x, y, z);
                    if (code == 0) continue;
                    ShipPart p = part(code);
                    Direction d = facing(code);
                    if (p == ShipPart.CORRIDOR_WALL || p == ShipPart.TRANSPORTER_WALL) set(x, y, z, ShipPart.ENT_WALL);
                    else if (p == ShipPart.DECK_FLOOR) set(x, y, z, ShipPart.DECK_PLATE);
                    else if (p == ShipPart.STATION_CHAIR) set(x, y, z, ShipPart.ENT_CHAIR, d);
                    else if (p == ShipPart.BRIDGE_STATION || p == ShipPart.ENGINEERING_CONSOLE || p == ShipPart.LCARS_PANEL)
                        set(x, y, z, ShipPart.ENT_CONSOLE, d); // LCARS gibt es erst ab dem 24. Jahrhundert
                }
    }

    /** Setzt die Verbindungen von Gittern (Geländer), damit sie nicht als einzelne Pfosten erscheinen. */
    private void connectBars() {
        for (int x = 0; x < sizeX; x++)
            for (int y = 0; y < sizeY; y++)
                for (int z = -zOffset; z <= zOffset; z++) {
                    ShipPart self = get(x, y, z);
                    if (self != ShipPart.IRON_BARS && self != ShipPart.BRIDGE_RAILING && self != ShipPart.BRIDGE_RAILING_GRAY
                        && self != ShipPart.BRIDGE_RAILING_WOOD) continue;
                    int code = cell[idx(x, y, z)] & 0xFFFF;
                    code &= 0x3FF; // Varianten-Bits löschen, hier stehen die Verbindungen
                    if (connects(self, get(x - 1, y, z))) code |= 1 << (10 + Direction.NORTH.get2DDataValue());
                    if (connects(self, get(x + 1, y, z))) code |= 1 << (10 + Direction.SOUTH.get2DDataValue());
                    if (connects(self, get(x, y, z + 1))) code |= 1 << (10 + Direction.EAST.get2DDataValue());
                    if (connects(self, get(x, y, z - 1))) code |= 1 << (10 + Direction.WEST.get2DDataValue());
                    cell[idx(x, y, z)] = (short) code;
                }
    }

    private static boolean connects(ShipPart self, ShipPart n) {
        if (self == ShipPart.BRIDGE_RAILING || self == ShipPart.BRIDGE_RAILING_GRAY || self == ShipPart.BRIDGE_RAILING_WOOD) return n == self;
        if (n == ShipPart.STATION_CHAIR_TEAL || n == ShipPart.CAPTAINS_CHAIR_TEAL) return false;
        return n != null && n != ShipPart.AIR && n != ShipPart.LADDER && n != ShipPart.CAPTAINS_CHAIR
            && n != ShipPart.TRANSPORTER_PAD;
    }
}

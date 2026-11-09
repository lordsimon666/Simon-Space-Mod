package de.simon.spacemod.ship;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/**
 * Prozeduraler Bauplan der USS Enterprise NCC-1701 (Originalserie).
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
        SHUTTLEBAY = 6, DISH = 7, ANTENNA = 8, PYLON = 9, NACELLE = 10, BUSSARD = 11;

    private static final int DECK_HEIGHT = 4;

    public final double scale;
    public final int sizeX, sizeY, sizeZ;
    /** Verschiebung lokales z -> Rasterindex. */
    public final int zOffset;

    private final byte[] comp;
    private final boolean[] hull;
    /** 0 = nichts setzen, sonst (Teil+1) | Richtung << 6 | Gitter-Verbindungen << 8. */
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
    private final double sCx, sR, sMid; // Untertassensektion: Mittelpunkt X, Radius, Mittelebene
    private final double eY, eR, eXf, eXr; // Sekundärhülle: Achshöhe, Radius, Front, Heck
    private final double nZ, nY, nR, nX0, nX1; // Warpgondeln
    private final int bridgeFloor;
    private final int floorBase;

    public EnterpriseBlueprint(double scale) {
        this.scale = scale;
        this.s = scale;
        this.k = Math.max(scale, 9.5 / 15.0);

        eR = 15 * k;
        eY = 1 + eR;
        sMid = eY + eR + 20 * s;
        sR = 63.5 * s;
        sCx = 1 + sR;
        eXf = 118 * s;
        eXr = 245 * s;
        nR = Math.max(7 * s, 2.6);
        nZ = Math.max(42 * s, eR + nR + 6);
        nY = sMid + 8 * s;
        nX0 = 150 * s;
        nX1 = 289 * s;

        bridgeFloor = (int) Math.floor(saucerTop(0));
        floorBase = Math.floorMod(bridgeFloor, DECK_HEIGHT);

        sizeX = (int) Math.ceil(Math.max(nX1, eXr) + 3);
        sizeY = Math.max(bridgeFloor + 12, (int) Math.ceil(nY + nR + 3));
        int halfZ = (int) Math.ceil(Math.max(sR, nZ + nR) + 2);
        zOffset = halfZ;
        sizeZ = 2 * halfZ + 1;

        int n = sizeX * sizeY * sizeZ;
        comp = new byte[n];
        hull = new boolean[n];
        cell = new short[n];

        buildShape();
        skinAndDecks();
        registryLettering();
        hullDetails();
        smoothSaucer();
        boolean fullDecks = s >= 0.75;
        if (fullDecks) {
            layoutSaucerDecks();
            layoutSecondaryDecks();
        }
        buildBridge();
        BlockPos eng = buildEngineering();
        BlockPos transporter = buildTransporterRoom(fullDecks);
        linkTurbolifts(eng, transporter);
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
        cell[idx(x, y, z)] = (short) ((part.ordinal() + 1) | (facing.get2DDataValue() << 6) | ((variant & 3) << 8));
    }

    public static int variant(int code) {
        return (code >> 8) & 3;
    }

    /** @return Bauteil der Zelle oder {@code null}, wenn dort nichts gesetzt wird */
    public ShipPart get(int x, int y, int z) {
        if (!inBounds(x, y, z)) return null;
        int c = cell[idx(x, y, z)];
        return c == 0 ? null : part(c);
    }

    public static ShipPart part(int code) {
        return ShipPart.byId((code & 0x3F) - 1);
    }

    public static Direction facing(int code) {
        return Direction.from2DDataValue((code >> 6) & 3);
    }

    public static boolean bar(int code, Direction dir) {
        return (code >> (8 + dir.get2DDataValue()) & 1) != 0;
    }

    private byte compAt(int x, int y, int z) {
        return inBounds(x, y, z) ? comp[idx(x, y, z)] : NONE;
    }

    private boolean isInterior(int x, int y, int z) {
        return inBounds(x, y, z) && comp[idx(x, y, z)] != NONE && !hull[idx(x, y, z)];
    }

    /** Alle gesetzten Zellen in Bau-Reihenfolge (von vorne nach hinten, unten nach oben). */
    public void forEach(CellConsumer consumer) {
        for (int x = 0; x < sizeX; x++)
            for (int y = 0; y < sizeY; y++)
                for (int z = -zOffset; z <= zOffset; z++) {
                    short code = cell[idx(x, y, z)];
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

    private double saucerTop(double r) {
        double u = Math.min(1, r / sR);
        double top = sMid + s * (2.5 + 8 * (1 - u * u));
        if (r < 22 * s) top += 2.5 * s; // erhöhter Aufbau um die Brücke
        return top;
    }

    private double saucerBottom(double r) {
        double u = Math.min(1, r / sR);
        return sMid - Math.max(1.6, s * (2.5 + 5.5 * (1 - u * u)));
    }

    private double secondaryRadius(double x) {
        double f = (x - eXf) / (eXr - eXf);
        if (f < 0 || f > 1) return -1;
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
        double tail = nX1 - 6 * s;
        if (x > tail) return nR * (1 - 0.4 * (x - tail) / (nX1 - tail));
        return nR;
    }

    private byte classify(int x, int y, int z) {
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
        double neckBotX = eXf + 30 * s, neckBotY = eY + eR * 0.7;
        if (y >= neckBotY && y <= neckTopY) {
            double t = (neckTopY - y) / (neckTopY - neckBotY);
            double xc = neckTopX + t * (neckBotX - neckTopX);
            double chord = Math.max(4, 13 * s);
            double w = Math.max(1.5, 4 * s * (1 - 0.25 * t));
            if (Math.abs(x - xc) <= chord && Math.abs(z) <= w) return NECK;
        }

        // Deflektorschüssel + Antenne
        double rho = Math.sqrt((y - eY) * (y - eY) + (double) z * z);
        double dishR = eR * 0.7;
        double dishDepth = Math.max(1, 3 * s);
        if (rho <= dishR && x < eXf && x >= eXf - 1 - dishDepth * (rho / dishR)) return DISH;
        if (rho <= Math.max(0.5, s) && x < eXf && x >= eXf - Math.max(4, 10 * s)) return ANTENNA;

        // Sekundärhülle mit Shuttlehangar
        double er = secondaryRadius(x);
        if (er > 0 && rho <= er) return x > eXr - 22 * s ? SHUTTLEBAY : SECONDARY;

        // Pylonen + Gondeln (symmetrisch)
        double az = Math.abs(z);
        double nr = nacelleRadius(x);
        double nd = Math.sqrt((az - nZ) * (az - nZ) + (y - nY) * (y - nY));
        if (nr > 0 && nd <= nr) return x < nX0 + nR * 0.9 ? BUSSARD : NACELLE;

        double p0z = 0, p0y = eY + eR * 0.5, p1z = nZ, p1y = nY;
        double lz = p1z - p0z, ly = p1y - p0y, len = Math.sqrt(lz * lz + ly * ly);
        double t = ((az - p0z) * lz + (y - p0y) * ly) / (len * len);
        if (t >= 0 && t <= 1) {
            double perp = Math.abs((az - p0z) * ly - (y - p0y) * lz) / len;
            double xc = (212 + 10 * t) * s + (eXr - 245 * s);
            double chord = Math.max(2.5, (12 - 3 * t) * s);
            if (perp <= Math.max(1.0, 1.6 * s) && Math.abs(x - xc) <= chord) return PYLON;
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
        ShipPart dish = ShipPart.DEFLECTOR_DISH;
        ShipPart bussard = ShipPart.BUSSARD_COLLECTOR;
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
                    double r = Math.sqrt(dx * dx + (double) z * z);

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
                                }
                            }
                            case BRIDGE -> st = y >= bridgeFloor + 6 ? panel : plating;
                            case NECK, PYLON -> st = Math.floorMod(x, 6) == 0 ? panel : plating;
                            case SECONDARY -> {
                                if (openSide && !openUpDown && deckY == 2 && Math.floorMod(x * 5 + y, 4) == 0 && Math.abs(z) > 1) st = window;
                                else if (Math.floorMod(x, 8) == 0) st = panel;
                            }
                            case SHUTTLEBAY -> {
                                double rho = Math.sqrt((y - eY) * (y - eY) + (double) z * z);
                                boolean rearFace = compAt(x + 1, y, z) == NONE;
                                if (rearFace && Math.abs(z) < secondaryRadius(x) * 0.55 && y < eY + rho * 0.5) st = dark;
                                else if (Math.floorMod(x, 8) == 0) st = panel;
                            }
                            case DISH -> st = dish;
                            case ANTENNA -> st = dark;
                            case BUSSARD -> st = bussard;
                            case NACELLE -> {
                                boolean inner = Math.abs(z) < nZ - nR * 0.4;
                                boolean mid = Math.abs(y - nY) <= Math.max(1, nR * 0.35);
                                if (x > nX1 - 2) st = dark;
                                else if (inner && mid && x < nX1 - 6 * s) st = grille;
                                else if (Math.floorMod(x, 10) == 0) st = panel;
                            }
                            default -> {}
                        }
                        set(x, y, z, st);
                        continue;
                    }

                    // Innenraum
                    ShipPart st = air;
                    boolean decks = c == SAUCER || c == SECONDARY || c == SENSOR;
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
        FONT6x9.put('0', new String[] {"011110", "100001", "100011", "100101", "101001", "110001", "100001", "100001", "011110"});
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
        int spacing = big ? 4 : 2;
        double scale = big ? GLYPH_SCALE : 1.0; // gedrehte Buchstaben etwas größer, damit sie sauber bleiben
        int h = font.get('N').length;
        double ra = sR * (big ? 0.8 : 0.78);
        String reg = "NCC-1701";
        int total = 0;
        for (char ch : reg.toCharArray()) total += font.get(ch)[0].length() + spacing;
        total -= spacing;
        total = (int) Math.round(total * scale);
        // Bogen: erster Buchstabe auf Steuerbord (+z), dann Richtung Backbord
        double pos = -total / 2.0;
        for (char ch : reg.toCharArray()) {
            String[] g = font.get(ch);
            int w = g[0].length();
            if (big) {
                drawGlyphOnArc(g, -(pos + w * scale / 2.0) / ra, ra, scale);
            } else {
                // kleine Schrift gedreht wäre unleserlich -> gerade Zeile vor der Brücke
                int xTop = (int) Math.round(sCx - 0.55 * sR) + h / 2;
                drawGlyphTop(g, xTop, (int) Math.round(-pos), 1);
            }
            pos += (w + spacing) * scale;
        }
        // "USS ENTERPRISE" klein und gerade zwischen Brücke und Kennung, wenn genug Platz ist
        String name = "USS ENTERPRISE";
        int nw = textWidth(name, FONT3x5);
        double nx = 0.5 * sR;
        if (Math.hypot(nx, nw / 2.0) > ra - h * scale / 2.0 - 1 || nx - 3 < 11) return;
        int top = (int) Math.round(sCx - nx) + 2;
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
        double px = sCx - ra * Math.cos(theta), pz = ra * Math.sin(theta);
        double nx = Math.cos(theta), nz = -Math.sin(theta);   // nach innen = Oberkante
        double tx = -Math.sin(theta), tz = -Math.cos(theta);  // Leserichtung
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

    private void markTop(int x, int z, ShipPart mark) {
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
        int reg = textWidth("NCC-1701", FONT3x5);
        for (int side : new int[] {1, -1}) {
            // Gondeln: Text vorne, danach roter Streifen auf Mittelhöhe
            int ny = (int) Math.round(nY);
            int textFront = (int) Math.round(nX0 + nR * 1.5);
            int start = side > 0 ? textFront + reg : textFront;
            sideText("NCC-1701", start, ny + 2, side);
            for (int x = textFront + reg + 3; x < nX1 - 6 * s; x++) markSide(x, ny, side, ShipPart.HULL_PENNANT);
            // Sekundärhülle: Text vor dem Pylon, roter Streifen darunter
            int ey = (int) Math.round(eY + eR * 0.25);
            int ex = (int) Math.round(eXf + 0.5 * (eXr - eXf));
            sideText("NCC-1701", side > 0 ? ex + reg : ex, ey + 2, side);
            for (int x = (int) Math.round(eXf + 0.15 * (eXr - eXf)); x < ex + reg + 2; x++) markSide(x, ey - 5, side, ShipPart.HULL_PENNANT);
        }
        // Positionslichter am Untertassenrand: Backbord rot, Steuerbord grün
        int mx = (int) Math.round(sCx), my = (int) Math.round(sMid);
        markSide(mx, my, -1, ShipPart.NAV_LIGHT_RED);
        markSide(mx, my, 1, ShipPart.NAV_LIGHT_GREEN);
        markSide(mx + 1, my, -1, ShipPart.NAV_LIGHT_RED);
        markSide(mx + 1, my, 1, ShipPart.NAV_LIGHT_GREEN);
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
        int cx = (int) Math.round(sCx);
        int yb = bridgeFloor;
        ShipPart air = ShipPart.AIR;
        ShipPart carpet = ShipPart.BRIDGE_DECK;
        ShipPart light = ShipPart.CEILING_LIGHT;

        for (int dx = -10; dx <= 10; dx++)
            for (int dz = -10; dz <= 10; dz++) {
                double r = Math.sqrt(dx * dx + dz * dz);
                int x = cx + dx;
                boolean front = dx < 0 && Math.abs(dz) <= 3;
                boolean aft = dx > 0 && dz >= 0 && dz <= 4; // Turbolift hinten Steuerbord
                if (r <= 7.5) {
                    set(x, yb, dz, carpet);
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
                            set(x, yb + 2, dz, ShipPart.BRIDGE_RAILING);
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
                    for (int y = yb; y <= yb + 5; y++) if (isInterior(x, y, dz)) set(x, y, dz, ShipPart.BRIDGE_WALL);
                    if (r <= 8.5) {
                        set(x, yb + 5, dz, ShipPart.BRIDGE_TRIM);
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

    private static Direction inwardFacing(int dx, int dz) {
        if (Math.abs(dx) >= Math.abs(dz)) return dx < 0 ? Direction.SOUTH : Direction.NORTH;
        return dz < 0 ? Direction.EAST : Direction.WEST;
    }

    private BlockPos bridgeTurbolift() {
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
        double f0 = eXf + 0.12 * (eXr - eXf), f1 = eXf + 0.6 * (eXr - eXf);
        int x0 = (int) Math.round((f0 + f1) / 2);
        int yF = (int) Math.round(eY) - 3;
        room(x0 - 10, x0 + 10, yF, yF + 7, -6, 6);

        ShipPart core = ShipPart.WARP_CORE;
        ShipPart conduit = ShipPart.PLASMA_CONDUIT;
        ShipPart console = ShipPart.ENGINEERING_CONSOLE;
        ShipPart lcars = ShipPart.LCARS_PANEL;
        ShipPart floor = ShipPart.DECK_FLOOR;
        ShipPart light = ShipPart.CEILING_LIGHT;
        ShipPart rail = ShipPart.IRON_BARS;

        // Plasmaleitungen im Boden
        for (int x = x0 - 9; x <= x0 + 9; x++) set(x, yF, 0, conduit);
        for (int z = -5; z <= 5; z++) set(x0, yF, z, conduit);
        // Warpkern (kreuzförmiger Querschnitt) vom Boden bis durch die Decke
        for (int y = yF - 2; y <= yF + 9; y++) {
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
        return null;
    }

    /**
     * Transporterraum (11 x 11 außen, genau ein Deck hoch) nach dem Vorbild der Originalserie.
     * @return Position des eigenen Turbolifts oder {@code null}
     */
    private BlockPos transporterRoomAt(int xt, int zc, int f, boolean withLift) {
        room(xt - 5, xt + 5, f, f + 4, zc - 5, zc + 5, ShipPart.TRANSPORTER_WALL);
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
        int[][] pads = {{xt + 3, -2}, {xt + 3, 0}, {xt + 3, 2}, {xt + 1, -2}, {xt + 1, 0}, {xt + 1, 2}};
        for (int[] pd : pads) {
            set(pd[0], f + 2, zc + pd[1], ShipPart.TRANSPORTER_PAD);
            set(pd[0], f + 4, zc + pd[1], ShipPart.TRANSPORTER_EMITTER);
        }
        // Energiewand mit Säulen an der Rückwand
        for (int dz = -4; dz <= 4; dz++)
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

    private int deckNumber(int floorY) {
        return Math.round((bridgeFloor - floorY) / (float) DECK_HEIGHT) + 1;
    }

    /** Turbolift-Halte für Brücke, Maschinenraum und Transporterraum (die Decks tragen sich selbst ein). */
    private void linkTurbolifts(BlockPos engineering, BlockPos transporter) {
        BlockPos bridge = bridgeTurbolift();
        set(bridge.getX(), bridge.getY(), bridge.getZ(), ShipPart.TURBOLIFT, Direction.NORTH);
        liftStops.add(new LiftStop(bridge, "Deck 1 – Brücke"));
        liftStops.add(new LiftStop(engineering, "Deck " + deckNumber(engineering.getY() - 1) + " – Maschinenraum"));
        if (transporter != null) {
            liftStops.add(new LiftStop(transporter, "Deck " + deckNumber(transporter.getY() - 1) + " – Transporterraum"));
        }
        // In Originalgröße liegt der Transporterraum auf Deck 3 und ist über den Deck-Lift erreichbar
        for (int i = 0; i < liftStops.size(); i++)
            if (liftStops.get(i).name().startsWith("Deck 3 – Krankenstation") && transporter == null)
                liftStops.set(i, new LiftStop(liftStops.get(i).pos(), "Deck 3 – Krankenstation, Transporter"));
    }

    // =====================================================================================
    //  Decks mit Räumen (Originalgröße)
    // =====================================================================================

    private enum RoomType { QUARTERS, BRIEFING, READY_ROOM, SICKBAY, LAB, MESS, REC, STORAGE, CARGO }

    /** Ringsektor-Raum auf einem Deck der Untertasse. Winkel: 0 = Bug, positiv Richtung Steuerbord. */
    private record Room(double rIn, double rOut, double a0, double a1, RoomType type) {}

    private double angle(int x, int z) {
        return Math.atan2(z, -(x - sCx));
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
     * Alle Decks der Untertasse: Turbolift im Vorraum in der Mitte, vier Gänge, Ringkorridor,
     * acht große Innenräume (Besprechungsraum, Krankenstation, Messe, Labore …) und Quartiere außen.
     */
    private void layoutSaucerDecks() {
        int cx = (int) Math.round(sCx);
        double rc = 0.42 * sR; // Ringkorridor
        for (int f = bridgeFloor - DECK_HEIGHT; f > 0; f -= DECK_HEIGHT) {
            boolean ok = true;
            for (int y = f + 1; y <= f + 3; y++) ok &= deckCell(cx, y, 0) && deckCell(cx + 6, y, 0);
            if (!ok) continue;
            int deck = deckNumber(f);
            int index = deck - 2; // 0 = erstes Deck unter der Brücke
            RoomType[] inner = innerRooms(index);
            String deckName = saucerDeckName(index, f);

            // Räume definieren
            List<Room> rooms = new ArrayList<>();
            for (int i = 0; i < 8; i++) {
                double a0 = -Math.PI + i * Math.PI / 4, a1 = a0 + Math.PI / 4;
                rooms.add(new Room(5.5, rc - 1.5, a0, a1, inner[i]));
            }
            for (int i = 0; i < 24; i++) {
                double a0 = -Math.PI + i * Math.PI / 12, a1 = a0 + Math.PI / 12;
                rooms.add(new Room(rc + 1.5, sR, a0, a1, RoomType.QUARTERS));
            }

            // Wände, Gänge und Türen
            for (int x = cx - (int) sR; x <= cx + (int) sR; x++)
                for (int z = -(int) sR; z <= (int) sR; z++) {
                    double dx = x - sCx, r = Math.sqrt(dx * dx + (double) z * z);
                    if (r > sR) continue;
                    double a = angle(x, z);
                    for (int y = f + 1; y <= f + 3; y++) {
                        if (!deckCell(x, y, z)) continue;
                        set(x, y, z, saucerDeckCell(x, y, z, r, a, f, rc));
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
            liftStops.add(new LiftStop(new BlockPos(cx, f + 1, 0), "Deck " + deck + " – " + deckName));

            for (Room room : rooms) furnish(room, f);
        }
    }

    private ShipPart saucerDeckCell(int x, int y, int z, double r, double a, int f, double rc) {
        ShipPart wall = ShipPart.CORRIDOR_WALL;
        boolean doorHeight = y <= f + 2;
        double perpX = Math.abs(z);                    // Abstand zur Längsachse
        double perpZ = Math.abs(x - sCx);              // Abstand zur Querachse
        boolean onAxis = perpX <= 1.2 || perpZ <= 1.2;
        if (r <= 4.5) return ShipPart.AIR;                                     // Vorraum
        if (r <= 5.5) return onAxis ? ShipPart.AIR : wall;                     // Wand um den Vorraum
        if (Math.abs(r - rc) <= 1.0) return ShipPart.AIR;                      // Ringkorridor
        if (r < rc && onAxis) return ShipPart.AIR;                             // Gänge
        if (r < rc && (Math.abs(perpX - 2) < 0.6 || Math.abs(perpZ - 2) < 0.6)) {
            // Gangwände, mit einer Tür in jeden Innenraum nahe dem Vorraum
            boolean door = doorHeight && Math.abs(Math.max(perpX, perpZ) - 9) <= 1.0;
            return door ? ShipPart.AIR : wall;
        }
        boolean innerZone = r < rc;
        double ringWall = innerZone ? rc - 1.5 : rc + 1.5;
        double sector = innerZone ? Math.PI / 4 : Math.PI / 12;
        double mid = (Math.floor((a + Math.PI) / sector) + 0.5) * sector - Math.PI;
        if (Math.abs(r - ringWall) < 0.6) {
            boolean door = Math.abs(angDiff(a, mid) * r) <= 1.0 && doorHeight;
            return door ? ShipPart.AIR : wall;
        }
        // Trennwände zwischen den Räumen
        double toBoundary = Math.abs(angDiff(a, mid)) - sector / 2;
        if (Math.abs(toBoundary * r) < 0.55) return wall;
        return ShipPart.AIR;
    }

    private static RoomType[] innerRooms(int index) {
        RoomType S = RoomType.STORAGE, L = RoomType.LAB, Q = RoomType.QUARTERS;
        return switch (index) {
            case 0 -> new RoomType[] {Q, Q, RoomType.READY_ROOM, RoomType.BRIEFING, RoomType.BRIEFING, Q, Q, Q};
            case 1 -> new RoomType[] {L, S, L, RoomType.SICKBAY, RoomType.SICKBAY, L, S, L};
            case 2 -> new RoomType[] {RoomType.REC, RoomType.REC, RoomType.MESS, RoomType.MESS, RoomType.MESS, RoomType.MESS, RoomType.REC, RoomType.REC};
            case 3 -> new RoomType[] {L, L, L, L, L, L, L, L};
            default -> new RoomType[] {S, S, Q, Q, Q, Q, S, S};
        };
    }

    private String saucerDeckName(int index, int f) {
        return switch (index) {
            case 0 -> "Besprechungsraum";
            case 1 -> "Krankenstation";
            case 2 -> "Messe & Freizeitraum";
            case 3 -> "Wissenschaftslabore";
            default -> deckCell((int) Math.round(sCx), f - 3, 0) ? "Mannschaftsquartiere" : "Lager & Sensoren";
        };
    }

    /** Liefert die freien Bodenzellen (Luft über Boden) eines Raums. */
    private List<int[]> roomCells(Room room, int f) {
        List<int[]> cells = new ArrayList<>();
        int cx = (int) Math.round(sCx);
        int rr = (int) Math.ceil(Math.min(room.rOut(), sR));
        for (int x = cx - rr; x <= cx + rr; x++)
            for (int z = -rr; z <= rr; z++) {
                double r = Math.hypot(x - sCx, z);
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
                int[] bed = nearest(cells, sCx - (rOutUse - 3) * Math.cos(am), (rOutUse - 3) * Math.sin(am));
                int[] desk = nearest(cells, sCx - (room.rIn() + 2.5) * Math.cos(am), (room.rIn() + 2.5) * Math.sin(am) + 1.5);
                if (bed != null) set(bed[0], y, bed[1], ShipPart.CREW_BED, towards(bed[0], bed[1], gx, gz));
                if (desk != null && (bed == null || desk[0] != bed[0] || desk[1] != bed[1])) {
                    Direction d = towards(desk[0], desk[1], sCx - rOutUse * Math.cos(am), rOutUse * Math.sin(am)).getOpposite();
                    set(desk[0], y, desk[1], ShipPart.CREW_DESK, d);
                    int chx = desk[0] + (d == Direction.NORTH ? -1 : d == Direction.SOUTH ? 1 : 0);
                    int chz = desk[1] + (d == Direction.WEST ? -1 : d == Direction.EAST ? 1 : 0);
                    if (free(chx, y, chz)) set(chx, y, chz, ShipPart.STATION_CHAIR, d.getOpposite());
                }
            }
            case BRIEFING -> {
                // Langer Konferenztisch mit dreiseitigem Bildschirm in der Mitte (wie in der Serie)
                for (int dx = -3; dx <= 3; dx++)
                    for (int dz = 0; dz <= 1; dz++)
                        if (free(gx + dx, y, gz + dz)) set(gx + dx, y, gz + dz, ShipPart.CONFERENCE_TABLE);
                if (get(gx, y, gz) == ShipPart.CONFERENCE_TABLE) set(gx, y + 1, gz, ShipPart.TABLE_VIEWER);
                for (int dx = -3; dx <= 3; dx += 2) {
                    if (free(gx + dx, y, gz - 1)) set(gx + dx, y, gz - 1, ShipPart.STATION_CHAIR, Direction.EAST);
                    if (free(gx + dx, y, gz + 2)) set(gx + dx, y, gz + 2, ShipPart.STATION_CHAIR, Direction.WEST);
                }
                if (free(gx - 4, y, gz)) set(gx - 4, y, gz, ShipPart.STATION_CHAIR, Direction.SOUTH);
                if (free(gx + 4, y, gz + 1)) set(gx + 4, y, gz + 1, ShipPart.STATION_CHAIR, Direction.NORTH);
            }
            case READY_ROOM -> {
                if (free(gx, y, gz)) set(gx, y, gz, ShipPart.CREW_DESK, Direction.SOUTH);
                if (free(gx + 1, y, gz)) set(gx + 1, y, gz, ShipPart.CAPTAINS_CHAIR, Direction.NORTH);
                if (free(gx, y, gz + 2)) set(gx, y, gz + 2, ShipPart.LCARS_PANEL, Direction.WEST);
            }
            case SICKBAY -> buildSickbay(room, f);
            case MESS, REC -> {
                for (int[] c : cells) {
                    if (Math.floorMod(c[0], 4) != 0 || Math.floorMod(c[1], 4) != 0 || !free(c[0], y, c[1])) continue;
                    if (!free(c[0] - 1, y, c[1]) || !free(c[0] + 1, y, c[1])) continue;
                    set(c[0], y, c[1], ShipPart.MESS_TABLE);
                    set(c[0] - 1, y, c[1], ShipPart.STATION_CHAIR, Direction.SOUTH);
                    set(c[0] + 1, y, c[1], ShipPart.STATION_CHAIR, Direction.NORTH);
                }
                if (room.type() == RoomType.REC && free(gx, y, gz)) set(gx, y, gz, ShipPart.LCARS_PANEL, Direction.NORTH);
            }
            case LAB -> {
                for (int[] c : cells)
                    if (Math.floorMod(c[0], 3) == 0 && Math.floorMod(c[1], 5) == 0 && free(c[0], y, c[1]))
                        set(c[0], y, c[1], Math.floorMod(c[0] + c[1], 2) == 0 ? ShipPart.ENGINEERING_CONSOLE : ShipPart.BRIDGE_STATION,
                            Direction.SOUTH, Math.floorMod(c[0] * 7 + c[1], 4));
            }
            case STORAGE, CARGO -> {
                for (int[] c : cells) {
                    if (Math.floorMod(c[0], 3) == 0 || Math.floorMod(c[1], 4) == 0 || !free(c[0], y, c[1])) continue;
                    if (Math.hypot(c[0] - sCx, c[1]) > Math.min(room.rOut(), sR) - 3.5) continue; // Platz an der Tür
                    set(c[0], y, c[1], ShipPart.CARGO_CONTAINER);
                    if (Math.floorMod(c[0] * 31 + c[1] * 17, 3) != 0 && get(c[0], y + 1, c[1]) == ShipPart.AIR) set(c[0], y + 1, c[1], ShipPart.CARGO_CONTAINER);
                }
            }
        }
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
        int rr = (int) Math.ceil(rOut) + 2;

        // 1) Farben und Zwischenwände
        for (int x = cx - rr; x <= cx + rr; x++)
            for (int z = -rr; z <= rr; z++) {
                double r = Math.hypot(x - sCx, z);
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
            int hx = (int) Math.round(sCx - hr * Math.cos(mid)), hz = (int) Math.round(hr * Math.sin(mid));
            Direction foot = towards(hx, hz, sCx, 0);
            if (k == officeK) {
                double dr = (rp + rOut) / 2;
                int dx = (int) Math.round(sCx - dr * Math.cos(mid)), dz = (int) Math.round(dr * Math.sin(mid));
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
        for (int x = cx - 7; x >= cx - (int) rp + 1; x -= 3) {
            if (Math.abs(x - (cx - 9)) <= 1) continue; // Tür vom Gang
            int hz = side * 3;
            double r1 = Math.hypot(x - sCx, side * 4);
            double a1 = angle(x, side * 4);
            boolean inRoom = r1 < rp - 1.2 && angDiff(a1, room.a0()) * r1 > 1 && angDiff(room.a1(), a1) * r1 > 1;
            if (!inRoom) continue;
            placeBiobed(x, hz, foot, f);
        }
        double wr = (room.rIn() + rp) / 2 + 1;
        int tx = (int) Math.round(sCx - wr * Math.cos(am)), tz = (int) Math.round(wr * Math.sin(am));
        for (int dx = 0; dx <= 1; dx++) if (free(tx - dx, f + 1, tz)) set(tx - dx, f + 1, tz, ShipPart.EXAM_TABLE, foot);
        if (free(tx, f + 2, tz) && get(tx, f + 3, tz) == ShipPart.AIR && compAt(tx, f + 4, tz) != NONE) set(tx, f + 3, tz, ShipPart.CEILING_LIGHT);
        // Medizinschränke an der Wand zu den Einzelzimmern
        for (double da : new double[] {-span / 4, span / 4}) {
            double cr = rp - 1;
            int mx = (int) Math.round(sCx - cr * Math.cos(am + da)), mz = (int) Math.round(cr * Math.sin(am + da));
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
            liftStops.add(new LiftStop(new BlockPos(xs, f + 1, 0), "Deck " + deck + " – " + names[Math.floorMod(deck, 4)]));
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

    /** Setzt die Verbindungen von Gittern (Geländer), damit sie nicht als einzelne Pfosten erscheinen. */
    private void connectBars() {
        for (int x = 0; x < sizeX; x++)
            for (int y = 0; y < sizeY; y++)
                for (int z = -zOffset; z <= zOffset; z++) {
                    ShipPart self = get(x, y, z);
                    if (self != ShipPart.IRON_BARS && self != ShipPart.BRIDGE_RAILING) continue;
                    int code = cell[idx(x, y, z)];
                    code &= 0xFF; // Varianten-Bits löschen, hier stehen die Verbindungen
                    if (connects(self, get(x - 1, y, z))) code |= 1 << (8 + Direction.NORTH.get2DDataValue());
                    if (connects(self, get(x + 1, y, z))) code |= 1 << (8 + Direction.SOUTH.get2DDataValue());
                    if (connects(self, get(x, y, z + 1))) code |= 1 << (8 + Direction.EAST.get2DDataValue());
                    if (connects(self, get(x, y, z - 1))) code |= 1 << (8 + Direction.WEST.get2DDataValue());
                    cell[idx(x, y, z)] = (short) code;
                }
    }

    private static boolean connects(ShipPart self, ShipPart n) {
        if (self == ShipPart.BRIDGE_RAILING) return n == ShipPart.BRIDGE_RAILING;
        return n != null && n != ShipPart.AIR && n != ShipPart.LADDER && n != ShipPart.CAPTAINS_CHAIR
            && n != ShipPart.TRANSPORTER_PAD;
    }
}

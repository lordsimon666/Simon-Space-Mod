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
    public final List<TurboLink> turboLinks = new ArrayList<>();
    /** Position (lokal) vor dem Kommandantensessel, für den Teleport zur Brücke. */
    public BlockPos bridgeSpawn = BlockPos.ZERO;

    public record TurboLink(BlockPos from, BlockPos to, String destinationKey) {}

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
        buildBridge();
        BlockPos eng = buildEngineering();
        BlockPos transporter = buildTransporterRoom();
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
        if (!inBounds(x, y, z)) return;
        cell[idx(x, y, z)] = (short) ((part.ordinal() + 1) | (facing.get2DDataValue() << 6));
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
                                    // Radiale Linien und Ringe der Untertassen-Oberseite
                                    double theta = Math.atan2(z, dx);
                                    boolean radial = Math.abs(Math.sin(4 * theta)) * r < 0.7 && r > 24 * s;
                                    boolean ring = Math.abs(r - 0.55 * sR) < 0.5 || Math.abs(r - 0.82 * sR) < 0.5;
                                    if (radial || ring) st = dark;
                                    else if (Math.floorMod((int) Math.floor(r / 7), 2) == 1) st = panel;
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

    /** "NCC-1701" auf der Oberseite der Untertassensektion. */
    private void registryLettering() {
        String[][] glyphs = {
            {"1001", "1101", "1011", "1001", "1001"}, // N
            {"0111", "1000", "1000", "1000", "0111"}, // C
            {"0111", "1000", "1000", "1000", "0111"}, // C
            {"000", "000", "111", "000", "000"},      // -
            {"010", "110", "010", "010", "111"},      // 1
            {"1111", "0001", "0010", "0100", "0100"}, // 7
            {"0110", "1001", "1001", "1001", "0110"}, // 0
            {"010", "110", "010", "010", "111"},      // 1
        };
        int px = s >= 0.75 ? 2 : 1;
        int width = 0;
        for (String[] g : glyphs) width += g[0].length() + 1;
        width -= 1;
        int startZ = -(width * px) / 2;
        int startX = (int) Math.round(sCx - sR * 0.62);
        ShipPart mark = ShipPart.HULL_MARKING;

        int col = 0;
        for (String[] g : glyphs) {
            for (int row = 0; row < 5; row++)
                for (int c = 0; c < g[row].length(); c++) {
                    if (g[row].charAt(c) != '1') continue;
                    for (int ax = 0; ax < px; ax++)
                        for (int az = 0; az < px; az++)
                            markTop(startX + row * px + ax, startZ + (col + c) * px + az, mark);
                }
            col += g[0].length() + 1;
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

    // =====================================================================================
    //  Räume in Originalgröße
    // =====================================================================================

    /** Kommandobrücke: rund, Ø 15 m, Hauptbildschirm vorne, Sessel in der Mitte, Stationen ringsum. */
    private void buildBridge() {
        int cx = (int) Math.round(sCx);
        int yb = bridgeFloor;
        ShipPart air = ShipPart.AIR;
        ShipPart floor = ShipPart.DECK_FLOOR;
        ShipPart ringFloor = ShipPart.BRIDGE_DECK;
        ShipPart wall = ShipPart.CORRIDOR_WALL;
        ShipPart light = ShipPart.CEILING_LIGHT;
        ShipPart console = ShipPart.BRIDGE_CONSOLE;
        ShipPart lcars = ShipPart.LCARS_PANEL;
        ShipPart screen = ShipPart.VIEWSCREEN;
        ShipPart rail = ShipPart.IRON_BARS;

        for (int dx = -10; dx <= 10; dx++)
            for (int dz = -10; dz <= 10; dz++) {
                double r = Math.sqrt(dx * dx + dz * dz);
                int x = cx + dx;
                if (r <= 7.5) {
                    set(x, yb, dz, floor);
                    for (int y = yb + 1; y <= yb + 5; y++) set(x, y, dz, air);
                    set(x, yb + 6, dz, r < 2.5 ? light : (Math.abs(r - 5.5) < 0.5 ? light : ShipPart.CEILING_PANEL));
                    if (r > 4.6) set(x, yb + 1, dz, ringFloor); // erhöhter Außenring
                    else if (r > 3.9 && !(dx < 0 && Math.abs(dz) <= 1) && !(dx > 0 && Math.abs(dz) <= 1)) {
                        set(x, yb + 2, dz, rail); // Geländer zur Mittelgrube
                    }
                } else if (r <= 10) {
                    // Innenwand: Stationen + LCARS-Anzeigen, Blickrichtung zur Mitte (Außenhülle bleibt)
                    if (!isInterior(x, yb + 2, dz)) continue;
                    Direction in = inwardFacing(dx, dz);
                    boolean front = dx < 0 && Math.abs(dz) <= 3;
                    boolean aft = dx > 0 && Math.abs(dz) <= 3;
                    for (int y = yb; y <= yb + 5; y++) if (isInterior(x, y, dz)) set(x, y, dz, wall);
                    if (r <= 8.5 && !front && !aft) {
                        set(x, yb + 2, dz, console, in);
                        set(x, yb + 3, dz, lcars, in);
                        set(x, yb + 4, dz, lcars, in);
                    }
                }
            }
        // Hauptbildschirm vorne (5 x 3)
        for (int dz = -2; dz <= 2; dz++)
            for (int y = yb + 2; y <= yb + 4; y++)
                set(cx - 8, y, dz, screen, Direction.SOUTH);
        // Steuer- und Navigationskonsole
        set(cx - 3, yb + 1, -1, console, Direction.NORTH);
        set(cx - 3, yb + 1, 1, console, Direction.NORTH);
        set(cx - 3, yb + 1, 0, lcars, Direction.NORTH);
        // Kommandantensessel
        set(cx, yb + 1, 0, ShipPart.CAPTAINS_CHAIR, Direction.NORTH);
        bridgeSpawn = new BlockPos(cx + 1, yb + 1, 0);
    }

    private static Direction inwardFacing(int dx, int dz) {
        if (Math.abs(dx) >= Math.abs(dz)) return dx < 0 ? Direction.SOUTH : Direction.NORTH;
        return dz < 0 ? Direction.EAST : Direction.WEST;
    }

    private BlockPos bridgeTurbolift(int side) {
        return new BlockPos((int) Math.round(sCx) + 8, bridgeFloor + 2, 2 * side);
    }

    /** Hohler Raum mit Wänden, Boden und Decke; die Außenhülle bleibt erhalten. */
    private void room(int x0, int x1, int y0, int y1, int z0, int z1) {
        ShipPart wall = ShipPart.CORRIDOR_WALL;
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

    /** Transporterraum (9 x 9 m) mit sechs Plattformen in der Untertassensektion. */
    private BlockPos buildTransporterRoom() {
        for (int off = 14; off <= sR * 0.7; off += 2) {
            int xt = (int) Math.round(sCx + off);
            for (int f = sizeY - 6; f > 0; f--) {
                // Nur der begehbare Innenraum muss im Schiff liegen, Boden/Decke/Wände dürfen Hülle sein
                if (!boxInterior(xt - 4, xt + 4, f + 1, f + 3, -4, 4)) continue;
                room(xt - 5, xt + 5, f, f + 4, -5, 5);
                ShipPart pad = ShipPart.TRANSPORTER_PAD;
                ShipPart light = ShipPart.CEILING_LIGHT;
                for (int px : new int[] {xt + 1, xt + 3})
                    for (int pz : new int[] {-2, 0, 2}) {
                        set(px, f + 1, pz, pad);
                        set(px, f + 4, pz, light); // auch wenn die Decke Außenhülle ist
                    }
                set(xt - 3, f + 1, 0, ShipPart.BRIDGE_CONSOLE, Direction.NORTH);
                set(xt - 3, f + 1, 1, ShipPart.LCARS_PANEL, Direction.NORTH);
                set(xt - 3, f + 4, 0, light);
                BlockPos lift = new BlockPos(xt - 5, f + 1, -3);
                set(lift.getX(), lift.getY(), lift.getZ(), ShipPart.TURBOLIFT, Direction.SOUTH);
                return lift;
            }
        }
        return null;
    }

    private boolean boxInterior(int x0, int x1, int y0, int y1, int z0, int z1) {
        for (int x = x0; x <= x1; x++)
            for (int y = y0; y <= y1; y++)
                for (int z = z0; z <= z1; z++)
                    if (!isInterior(x, y, z)) return false;
        return true;
    }

    private void linkTurbolifts(BlockPos engineering, BlockPos transporter) {
        ShipPart lift = ShipPart.TURBOLIFT;
        BlockPos toEng = bridgeTurbolift(-1);
        set(toEng.getX(), toEng.getY(), toEng.getZ(), lift, Direction.NORTH);
        turboLinks.add(new TurboLink(toEng, engineering, "deck.simonspacemod.engineering"));
        turboLinks.add(new TurboLink(engineering, toEng, "deck.simonspacemod.bridge"));
        if (transporter != null) {
            BlockPos toTr = bridgeTurbolift(1);
            set(toTr.getX(), toTr.getY(), toTr.getZ(), lift, Direction.NORTH);
            turboLinks.add(new TurboLink(toTr, transporter, "deck.simonspacemod.transporter"));
            turboLinks.add(new TurboLink(transporter, toTr, "deck.simonspacemod.bridge"));
        }
    }

    /** Setzt die Verbindungen von Gittern (Geländer), damit sie nicht als einzelne Pfosten erscheinen. */
    private void connectBars() {
        for (int x = 0; x < sizeX; x++)
            for (int y = 0; y < sizeY; y++)
                for (int z = -zOffset; z <= zOffset; z++) {
                    if (get(x, y, z) != ShipPart.IRON_BARS) continue;
                    int code = cell[idx(x, y, z)];
                    if (connects(get(x - 1, y, z))) code |= 1 << (8 + Direction.NORTH.get2DDataValue());
                    if (connects(get(x + 1, y, z))) code |= 1 << (8 + Direction.SOUTH.get2DDataValue());
                    if (connects(get(x, y, z + 1))) code |= 1 << (8 + Direction.EAST.get2DDataValue());
                    if (connects(get(x, y, z - 1))) code |= 1 << (8 + Direction.WEST.get2DDataValue());
                    cell[idx(x, y, z)] = (short) code;
                }
    }

    private static boolean connects(ShipPart n) {
        return n != null && n != ShipPart.AIR && n != ShipPart.LADDER && n != ShipPart.CAPTAINS_CHAIR
            && n != ShipPart.TRANSPORTER_PAD;
    }
}

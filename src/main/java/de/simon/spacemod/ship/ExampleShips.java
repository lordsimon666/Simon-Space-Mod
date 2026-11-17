package de.simon.spacemod.ship;

import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.jspecify.annotations.Nullable;

/**
 * Baupläne der Beispielschiffe (nur per Befehl {@code /simonship spawn}). Reine Daten: welcher Schiffsteil an
 * welcher Stelle sitzt. Schiffssystem: Bug = +x (Osten), oben = +y, rechts (Steuerbord) = +z; (0, 0, 0) ist die
 * Mitte des Hecks am Boden. Jedes Schiff ist abflugbereit: geschlossene Hülle, Impulsantriebe, Steuerkonsole,
 * Stromquelle und eine Luftschleuse an Backbord.
 */
public final class ExampleShips {
    public enum Type {
        /** Bergbauschiff: vier Bergbaulaser unter der Decke, Laserfokusse im Boden, Kisten, Kamera nach unten. */
        MINER("miner"),
        /** Shuttle mit 3×3×3-Kernreaktor (≈ 530 FE/t) und einer Batterie. */
        REACTOR_SMALL("reactor_small"),
        /** Frachter mit 8×8×8-Kernreaktor (10 000 FE/t): schafft gerade Warp 2. */
        REACTOR_LARGE("reactor_large"),
        /** Eine Warpgondel (höchstens Warp 5), zwei Warpkerne der Enterprise-F (80 000 FE/t). */
        WARP5("warp5"),
        /** Zwei Warpgondeln 20 Blöcke auseinander, sechs Warpkerne der Enterprise-F (240 000 FE/t): Warp 9.9. */
        WARP9("warp9");

        public final String id;

        Type(String id) {
            this.id = id;
        }
    }

    public enum Part {
        AIR, HULL, PANEL, BOTTOM, WINDOW, DECK, ENGINE, HELM, DAMPER, REACTOR_CASING, REACTOR_GLASS, REACTOR_CONTROLLER,
        WARP_CORE, WARP_CORE_F, BUSSARD, GRILLE, NAV_RED, NAV_GREEN, LASER, FOCUS, LASER_CONTROL, CHEST, CAMERA, DISPLAY,
        BATTERY, AIRLOCK
    }

    /**
     * Ein Block des Bauplans. {@code facing} im Schiffssystem (Bug = Osten), {@code value} je nach Teil: Stufe des
     * Trägheitsdämpfers bzw. Teilnummer der Luftschleuse.
     */
    public record Cell(Part part, Direction facing, int value) {}

    public record Plan(Type type, Map<BlockPos, Cell> cells, @Nullable BlockPos camera) {}

    private ExampleShips() {}

    public static Plan plan(Type type) {
        Builder b = new Builder();
        switch (type) {
            case MINER -> miner(b);
            case REACTOR_SMALL -> reactorSmall(b);
            case REACTOR_LARGE -> reactorLarge(b);
            case WARP5 -> warp5(b);
            case WARP9 -> warp9(b);
        }
        return new Plan(type, b.cells, b.camera);
    }

    // ---- die Schiffe ----

    private static void miner(Builder b) {
        b.hull(0, 14, 0, 6, -4, 4);
        // vier Laser unter der Decke, darunter 4 Blöcke Luft bis zum Fokus im Boden; Kiste daneben für die Ausbeute
        for (int x : new int[] {4, 10})
            for (int z : new int[] {-2, 2}) {
                b.set(x, 5, z, Part.LASER);
                b.set(x, 0, z, Part.FOCUS);
                b.set(x, 5, z + Integer.signum(z), Part.CHEST, Direction.WEST);
            }
        b.engines(0, new int[] {2, 3}, new int[] {-2, 2});
        b.set(2, 1, 0, Part.WARP_CORE);
        b.set(2, 2, 0, Part.WARP_CORE);
        b.set(12, 1, 0, Part.HELM, Direction.WEST);
        b.set(12, 1, -2, Part.LASER_CONTROL, Direction.WEST);
        b.damper(12, 1, 2, 3);
        b.windows(14, 2, 4, 2, 3);
        b.camera(7, 0, 0, Direction.DOWN);   // Blick nach unten auf den Abbau
        b.screen(13, 2, 3, 2);
        b.airlock(7, 1, -4);
        b.navLights(12, 6, 4);
    }

    private static void reactorSmall(Builder b) {
        b.hull(0, 10, 0, 5, -3, 3);
        b.reactor(1, 1, -1, 3, 3, 2, 0);
        b.set(0, 2, 0, Part.ENGINE);
        b.set(5, 1, 2, Part.BATTERY);
        b.set(8, 1, 0, Part.HELM, Direction.WEST);
        b.windows(10, 2, 3, 1, 2);
        b.set(10, 2, 0, Part.WINDOW);
        b.set(10, 3, 0, Part.WINDOW);
        b.airlock(6, 1, -3);
        b.navLights(8, 5, 3);
    }

    private static void reactorLarge(Builder b) {
        b.hull(0, 22, 0, 10, -6, 6);
        b.reactor(2, 1, -4, 8, 9, 2, -1);
        b.engines(0, new int[] {2, 3}, new int[] {-5, 5});
        b.set(19, 1, 0, Part.HELM, Direction.WEST);
        b.damper(19, 1, 3, 5);
        b.camera(22, 3, 0, Direction.EAST);
        b.screen(21, 2, 5, 3);
        b.windows(22, 3, 6, 3, 5);
        b.nacelle(1, 13, 7, -9, Part.HULL);
        b.nacelle(1, 13, 7, 9, Part.HULL);
        for (int z : new int[] {-1, 1})
            for (int x = 5; x <= 7; x++)
                for (int zz = 7; zz <= 8; zz++) b.set(x, 7, z * zz, Part.HULL); // Pylonen
        b.airlock(14, 1, -6);
        b.navLights(20, 10, 6);
    }

    private static void warp5(Builder b) {
        b.hull(0, 18, 0, 6, -4, 4);
        b.set(4, 1, 0, Part.WARP_CORE_F);
        b.set(4, 2, 0, Part.WARP_CORE_F);
        b.engines(0, new int[] {2, 3}, new int[] {-2, 2});
        b.set(16, 1, 0, Part.HELM, Direction.WEST);
        b.damper(16, 1, 2, 5);
        b.camera(18, 3, 0, Direction.EAST);
        b.screen(17, 2, 3, 2);
        b.windows(18, 2, 4, 2, 3);
        // eine Gondel oben auf dem Rumpf
        for (int x = 6; x <= 8; x++) b.set(x, 7, 0, Part.HULL);
        b.nacelle(1, 15, 8, 0, Part.HULL);
        b.airlock(10, 1, -4);
        b.navLights(16, 6, 4);
    }

    private static void warp9(Builder b) {
        b.hull(0, 26, 0, 7, -5, 5);
        for (int x = 5; x <= 6; x++)
            for (int y = 1; y <= 3; y++) b.set(x, y, 0, Part.WARP_CORE_F);
        b.engines(0, new int[] {2, 3, 4}, new int[] {-3, 3});
        b.set(24, 1, 0, Part.HELM, Direction.WEST);
        b.damper(24, 1, 3, 10);
        b.camera(26, 3, 0, Direction.EAST);
        b.screen(25, 2, 5, 3);
        b.windows(26, 2, 5, 3, 4);
        for (int z : new int[] {-1, 1}) {
            for (int x = 4; x <= 7; x++)
                for (int zz = 6; zz <= 9; zz++) b.set(x, 5, z * zz, Part.HULL); // Pylonen
            b.nacelle(0, 18, 5, z * 10, Part.HULL);
        }
        b.airlock(14, 1, -5);
        b.navLights(24, 7, 5);
    }

    // ---- Bauhilfe ----

    private static final class Builder {
        final Map<BlockPos, Cell> cells = new LinkedHashMap<>();
        @Nullable BlockPos camera;

        void set(int x, int y, int z, Part p) {
            set(x, y, z, p, Direction.EAST);
        }

        void set(int x, int y, int z, Part p, Direction f) {
            cells.put(new BlockPos(x, y, z), new Cell(p, f, 0));
        }

        /** Rumpf als Kasten: Boden (Hülle dunkel), Wände, Decke; innen Luft. */
        void hull(int x0, int x1, int y0, int y1, int z0, int z1) {
            for (int x = x0; x <= x1; x++)
                for (int y = y0; y <= y1; y++)
                    for (int z = z0; z <= z1; z++) {
                        boolean shell = x == x0 || x == x1 || y == y0 || y == y1 || z == z0 || z == z1;
                        set(x, y, z, !shell ? Part.AIR : y == y0 ? Part.BOTTOM : y == y1 ? Part.PANEL : Part.HULL);
                    }
        }

        /** Kernreaktor als Würfel der Kantenlänge n; Steuerblock in der Vorderseite (Bug), schaut nach vorn. */
        void reactor(int x0, int y0, int z0, int n, int controllerX, int controllerY, int controllerZ) {
            int x1 = x0 + n - 1, y1 = y0 + n - 1, z1 = z0 + n - 1;
            for (int x = x0; x <= x1; x++)
                for (int y = y0; y <= y1; y++)
                    for (int z = z0; z <= z1; z++) {
                        boolean shell = x == x0 || x == x1 || y == y0 || y == y1 || z == z0 || z == z1;
                        if (!shell) { set(x, y, z, Part.AIR); continue; }
                        // Sichtfenster: Mitte der Seitenflächen und der Oberseite
                        boolean midX = x > x0 && x < x1 && (n <= 4 || x > x0 + 1 && x < x1 - 1);
                        boolean midY = y > y0 && y < y1 && (n <= 4 || y > y0 + 1 && y < y1 - 1);
                        boolean midZ = z > z0 && z < z1 && (n <= 4 || z > z0 + 1 && z < z1 - 1);
                        boolean glass = (z == z0 || z == z1) && midX && midY || y == y1 && midX && midZ;
                        set(x, y, z, glass ? Part.REACTOR_GLASS : Part.REACTOR_CASING);
                    }
            if (controllerX != x1) throw new IllegalArgumentException("Steuerblock muss in der Vorderseite sitzen");
            set(controllerX, controllerY, controllerZ, Part.REACTOR_CONTROLLER, Direction.EAST);
        }

        void engines(int x, int[] ys, int[] zs) {
            for (int y : ys) for (int z : zs) set(x, y, z, Part.ENGINE);
        }

        void damper(int x, int y, int z, int level) {
            cells.put(new BlockPos(x, y, z), new Cell(Part.DAMPER, Direction.WEST, level));
        }

        /** Fenster in der Bugwand bei x, Höhe y0..y1, zu beiden Seiten |z| = za..zb. */
        void windows(int x, int y0, int y1, int za, int zb) {
            for (int y = y0; y <= y1; y++)
                for (int z = za; z <= zb; z++) {
                    set(x, y, z, Part.WINDOW);
                    set(x, y, -z, Part.WINDOW);
                }
        }

        void camera(int x, int y, int z, Direction f) {
            set(x, y, z, Part.CAMERA, f);
            camera = new BlockPos(x, y, z);
        }

        /** Flugbildschirm an der Innenseite der Bugwand: w breit (ungerade, mittig), h hoch, ab Höhe y. */
        void screen(int x, int y, int w, int h) {
            for (int dy = 0; dy < h; dy++)
                for (int z = -w / 2; z <= w / 2; z++) set(x, y + dy, z, Part.DISPLAY, Direction.WEST);
        }

        /** Kleine Luftschleuse (1 breit, 2 hoch, 2 tief) in der Backbordwand bei z, nach außen (Norden) gerichtet. */
        void airlock(int x, int y, int z) {
            for (int h = 0; h < 2; h++)
                for (int d = 0; d < 2; d++)
                    cells.put(new BlockPos(x, y + h, z + d), new Cell(Part.AIRLOCK, Direction.NORTH, h + 2 * d));
        }

        /** Warpgondel entlang x (x0..x1) auf Höhe y bei z: unten Hülle, oben Gondelgitter, vorn zwei Bussard-Kollektoren. */
        void nacelle(int x0, int x1, int y, int z, Part base) {
            for (int x = x0; x <= x1; x++) {
                set(x, y, z, base);
                set(x, y + 1, z, x == x0 ? Part.HULL : Part.GRILLE);
            }
            set(x1 + 1, y, z, Part.BUSSARD);
            set(x1 + 1, y + 1, z, Part.BUSSARD);
        }

        /** Positionslichter oben an den Seiten: rot an Backbord, grün an Steuerbord. */
        void navLights(int x, int y, int z) {
            set(x, y, -z, Part.NAV_RED);
            set(x, y, z, Part.NAV_GREEN);
        }
    }
}

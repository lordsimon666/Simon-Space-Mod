package de.simon.spacemod.ship;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.simon.spacemod.reactor.ReactorMath;
import de.simon.spacemod.ship.ExampleShips.Cell;
import de.simon.spacemod.ship.ExampleShips.Part;
import de.simon.spacemod.space.WarpDrive;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/** Die Beispielschiffe sind abflugbereit und schaffen die versprochene Warpstufe. */
class ExampleShipsTest {
    /** Teile, die beim Abheben außen liegen dürfen (wie ShipAssembler.isHullBlock). */
    private static final Set<Part> HULL = EnumSet.of(Part.HULL, Part.PANEL, Part.BOTTOM, Part.WINDOW, Part.ENGINE, Part.BUSSARD,
        Part.GRILLE, Part.NAV_RED, Part.NAV_GREEN, Part.FOCUS, Part.LASER, Part.CAMERA, Part.AIRLOCK);

    private static boolean solid(Cell c) {
        return c != null && c.part() != Part.AIR;
    }

    @ParameterizedTest
    @EnumSource(ExampleShips.Type.class)
    void readyToFly(ExampleShips.Type type) {
        Map<BlockPos, Cell> cells = ExampleShips.plan(type).cells();
        int x0 = Integer.MAX_VALUE, y0 = x0, z0 = x0, x1 = Integer.MIN_VALUE, y1 = x1, z1 = x1;
        for (BlockPos p : cells.keySet()) {
            x0 = Math.min(x0, p.getX()); y0 = Math.min(y0, p.getY()); z0 = Math.min(z0, p.getZ());
            x1 = Math.max(x1, p.getX()); y1 = Math.max(y1, p.getY()); z1 = Math.max(z1, p.getZ());
        }
        // alles hängt zusammen
        List<BlockPos> blocks = new ArrayList<>();
        for (var e : cells.entrySet()) if (solid(e.getValue())) blocks.add(e.getKey());
        Set<BlockPos> seen = new HashSet<>(List.of(blocks.get(0)));
        ArrayDeque<BlockPos> q = new ArrayDeque<>(seen);
        while (!q.isEmpty()) {
            BlockPos p = q.poll();
            for (Direction d : Direction.values()) {
                BlockPos n = p.relative(d);
                if (solid(cells.get(n)) && seen.add(n)) q.add(n);
            }
        }
        assertEquals(blocks.size(), seen.size(), "nicht alles zusammenhängend");
        // geschlossene Hülle: von außen erreichbare Luft berührt nur Hüllenteile
        Set<BlockPos> outside = new HashSet<>();
        BlockPos start = new BlockPos(x0 - 1, y0 - 1, z0 - 1);
        outside.add(start);
        q.add(start);
        while (!q.isEmpty()) {
            BlockPos p = q.poll();
            for (Direction d : Direction.values()) {
                BlockPos n = p.relative(d);
                if (n.getX() < x0 - 1 || n.getX() > x1 + 1 || n.getY() < y0 - 1 || n.getY() > y1 + 1 || n.getZ() < z0 - 1 || n.getZ() > z1 + 1) continue;
                if (solid(cells.get(n)) || !outside.add(n)) continue;
                q.add(n);
            }
        }
        for (BlockPos p : blocks)
            for (Direction d : Direction.values())
                if (outside.contains(p.relative(d)))
                    assertTrue(HULL.contains(cells.get(p).part()), type + ": " + cells.get(p).part() + " liegt außen bei " + p);
        // Steuerkonsole, Antrieb, Schleuse
        assertTrue(count(cells, Part.HELM) == 1 && count(cells, Part.ENGINE) >= 1 && count(cells, Part.AIRLOCK) == 4);
        // Strom und Gondeln
        long production = 10_000L * count(cells, Part.WARP_CORE) + 40_000L * count(cells, Part.WARP_CORE_F) + reactorOutput(cells);
        List<BlockPos> nacelleBlocks = new ArrayList<>();
        List<Boolean> bussard = new ArrayList<>();
        for (var e : cells.entrySet())
            if (e.getValue().part() == Part.BUSSARD || e.getValue().part() == Part.GRILLE) {
                nacelleBlocks.add(e.getKey());
                bussard.add(e.getValue().part() == Part.BUSSARD);
            }
        double warp = Math.min(WarpDrive.maxForPower(production),
            WarpDrive.maxForNacelles(WarpDrive.nacelleCount(WarpDrive.nacelleCenters(nacelleBlocks, bussard))));
        double expected = switch (type) {
            case MINER, REACTOR_SMALL -> 0;
            case REACTOR_LARGE -> 2.0;
            case WARP5 -> 5.0;
            case WARP9 -> 9.9;
        };
        assertEquals(expected, warp, 1e-9, type + ": höchste Warpstufe (Erzeugung " + production + " FE/t)");
        assertTrue(production > 0, "keine Stromquelle");
    }

    @ParameterizedTest
    @EnumSource(ExampleShips.Type.class)
    void flightScreenSeesCamera(ExampleShips.Type type) {
        var plan = ExampleShips.plan(type);
        if (plan.camera() == null) return;
        assertEquals(Part.CAMERA, plan.cells().get(plan.camera()).part());
        assertTrue(count(plan.cells(), Part.DISPLAY) >= 3, "Flugbildschirm fehlt");
        for (var e : plan.cells().entrySet()) // Bildschirm frei: davor (Richtung Heck) Luft
            if (e.getValue().part() == Part.DISPLAY) assertEquals(Part.AIR, plan.cells().get(e.getKey().west()).part());
    }

    @org.junit.jupiter.api.Test
    void minerLasersShootIntoFocus() {
        Map<BlockPos, Cell> cells = ExampleShips.plan(ExampleShips.Type.MINER).cells();
        int lasers = 0;
        for (var e : cells.entrySet()) {
            if (e.getValue().part() != Part.LASER) continue;
            lasers++;
            BlockPos p = e.getKey().below();
            int gap = 0;
            while (cells.get(p).part() == Part.AIR) { p = p.below(); gap++; }
            assertEquals(Part.FOCUS, cells.get(p).part(), "unter dem Laser kein Fokus");
            assertTrue(gap >= 1 && gap <= 4, "Fokus zu weit weg");
            assertTrue(HULL.contains(cells.get(e.getKey().above()).part()), "Laser ohne Verbindung zur Decke");
        }
        assertEquals(4, lasers);
    }

    private static int count(Map<BlockPos, Cell> cells, Part part) {
        int n = 0;
        for (Cell c : cells.values()) if (c.part() == part) n++;
        return n;
    }

    /** Leistung des (einzigen) Reaktors: Kantenlänge aus den Reaktorteilen, Würfel muss vollständig sein. */
    private static long reactorOutput(Map<BlockPos, Cell> cells) {
        int x0 = Integer.MAX_VALUE, x1 = Integer.MIN_VALUE, y0 = x0, y1 = x1, z0 = x0, z1 = x1, parts = 0;
        for (var e : cells.entrySet()) {
            Part p = e.getValue().part();
            if (p != Part.REACTOR_CASING && p != Part.REACTOR_GLASS && p != Part.REACTOR_CONTROLLER) continue;
            BlockPos b = e.getKey();
            x0 = Math.min(x0, b.getX()); x1 = Math.max(x1, b.getX());
            y0 = Math.min(y0, b.getY()); y1 = Math.max(y1, b.getY());
            z0 = Math.min(z0, b.getZ()); z1 = Math.max(z1, b.getZ());
            parts++;
        }
        if (parts == 0) return 0;
        int n = x1 - x0 + 1;
        assertTrue(y1 - y0 + 1 == n && z1 - z0 + 1 == n, "Reaktor ist kein Würfel");
        int inner = Math.max(0, n - 2);
        assertEquals(n * n * n - inner * inner * inner, parts, "Reaktorhülle unvollständig");
        return ReactorMath.output(n);
    }
}

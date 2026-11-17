package de.simon.spacemod.space;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

class WarpDriveTest {
    @Test
    void warpTwoJustPossibleWithLargestReactor() {
        int reactor8 = de.simon.spacemod.reactor.ReactorMath.output(8);
        assertEquals(10_000, reactor8, 300);
        assertTrue(WarpDrive.cost(2) <= reactor8, "Warp 2 muss mit dem 8x8x8-Reaktor gehen");
        assertTrue(WarpDrive.cost(2.1) > reactor8, "Warp 2.1 darf nicht mehr gehen");
        assertEquals(2.0, WarpDrive.maxForPower(reactor8), 1e-9);
        // je höher, desto teurer und schneller
        assertTrue(WarpDrive.cost(9.9) > 20 * WarpDrive.cost(2));
        assertTrue(WarpDrive.speed(9.9) > WarpDrive.speed(5) && WarpDrive.speed(5) > WarpDrive.speed(1));
        assertEquals(9.9, WarpDrive.clamp(12), 1e-9);
        assertEquals(1, WarpDrive.clamp(0.2), 1e-9);
    }

    /** Eine Gondel: Bussard vorne, dahinter Gitter in einer Reihe, bei (x, y, z). */
    private static void nacelle(List<BlockPos> pos, List<Boolean> b, int x, int y, int z, int grilles) {
        pos.add(new BlockPos(x, y, z));
        b.add(true);
        for (int i = 1; i <= grilles; i++) {
            pos.add(new BlockPos(x + i, y, z));
            b.add(false);
        }
    }

    @Test
    void nacelleRules() {
        List<BlockPos> pos = new ArrayList<>();
        List<Boolean> b = new ArrayList<>();
        // nur ein Gitter: keine gültige Gondel
        nacelle(pos, b, 0, 0, 0, 1);
        assertEquals(0, WarpDrive.nacelleCount(WarpDrive.nacelleCenters(pos, b)));
        assertEquals(0, WarpDrive.maxForNacelles(0), 1e-9);
        // eine Gondel: Warp 5
        pos.clear(); b.clear();
        nacelle(pos, b, 0, 0, 0, 2);
        assertEquals(1, WarpDrive.nacelleCount(WarpDrive.nacelleCenters(pos, b)));
        assertEquals(5, WarpDrive.maxForNacelles(1), 1e-9);
        // zweite Gondel zu nah (6 Blöcke): zählt als eine
        nacelle(pos, b, 0, 0, 6, 2);
        assertEquals(1, WarpDrive.nacelleCount(WarpDrive.nacelleCenters(pos, b)));
        // zweite Gondel 12 Blöcke daneben: zwei, Warp 9.9
        pos.clear(); b.clear();
        nacelle(pos, b, 0, 0, -6, 3);
        nacelle(pos, b, 0, 0, 6, 3);
        assertEquals(2, WarpDrive.nacelleCount(WarpDrive.nacelleCenters(pos, b)));
        assertEquals(9.9, WarpDrive.maxForNacelles(2), 1e-9);
    }
}

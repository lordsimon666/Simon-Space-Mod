package de.simon.spacemod.space;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.simon.spacemod.ship.ShipDesign;
import org.junit.jupiter.api.Test;

class SpaceShipsTest {
    @Test
    void newerShipsAreRarer() {
        int[] count = new int[ShipDesign.values().length];
        for (int x = -100; x < 100; x++)
            for (int z = -100; z < 100; z++) count[SpaceShips.designFor(12345L, x, z).ordinal()]++;
        for (int i = 1; i < count.length; i++) assertTrue(count[i] < count[i - 1], "Reihenfolge bei " + i);
        // NX-01 etwa 7/28, E etwa 1/28
        assertEquals(0.25, count[0] / 40000.0, 0.02);
        assertEquals(1 / 28.0, count[count.length - 1] / 40000.0, 0.01);
    }

    @Test
    void noAsteroidsNearEnterprises() {
        assertTrue(SpaceShips.nearEnterprise(1L, new net.minecraft.core.BlockPos(1000, 500, 0)), "Mitte des Schiffs");
        assertTrue(SpaceShips.nearEnterprise(1L, new net.minecraft.core.BlockPos(1000 + 150, 520, 30)), "neben dem Schiff");
        assertTrue(!SpaceShips.nearEnterprise(1L, new net.minecraft.core.BlockPos(1500, 500, 500)), "zwischen den Feldern frei");
        assertTrue(!SpaceShips.nearEnterprise(1L, new net.minecraft.core.BlockPos(1000, 900, 0)), "weit darüber");
    }

    @Test
    void asteroidsKeepTheirDistance() {
        java.util.List<net.minecraft.core.BlockPos> c = new java.util.ArrayList<>();
        int cells = 0;
        for (int i = -3; i < 3; i++)
            for (int j = 0; j < 6; j++)
                for (int k = -3; k < 3; k++) {
                    cells++;
                    var p = AsteroidGrid.center(42L, i, j, k);
                    if (p != null) c.add(p);
                }
        double min = Double.MAX_VALUE;
        for (int a = 0; a < c.size(); a++)
            for (int b = a + 1; b < c.size(); b++) min = Math.min(min, Math.sqrt(c.get(a).distSqr(c.get(b))));
        assertTrue(min >= 80, "Mindestabstand der Mittelpunkte: " + min);
        assertTrue(c.size() > cells / 2 && c.size() < cells * 0.9, "Anteil belegter Zellen: " + c.size() + "/" + cells);
    }

    @Test
    void sameCellSameShip() {
        assertEquals(SpaceShips.designFor(7L, 3, -4), SpaceShips.designFor(7L, 3, -4));
    }
}

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
    void sameCellSameShip() {
        assertEquals(SpaceShips.designFor(7L, 3, -4), SpaceShips.designFor(7L, 3, -4));
    }
}

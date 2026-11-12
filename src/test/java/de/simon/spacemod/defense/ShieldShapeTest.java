package de.simon.spacemod.defense;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ShieldShapeTest {
    /** Hohler Kasten 20 x 10 x 12 (Wände 1 Block) mitten im Raster. */
    private static ShieldShape box() {
        ShieldShape s = new ShieldShape(40, 30, 32);
        for (int x = 10; x < 30; x++)
            for (int y = 10; y < 20; y++)
                for (int z = 10; z < 22; z++)
                    if (x == 10 || x == 29 || y == 10 || y == 19 || z == 10 || z == 21) s.set(x, y, z);
        return s;
    }

    @Test
    void shieldIsTwoBlocksAwayAndInteriorIsProtected() {
        ShieldShape p = box().protectedSpace();
        assertTrue(p.get(20, 15, 16), "Inneres geschützt");
        assertTrue(p.get(8, 15, 16) && p.surface(8, 15, 16), "Schild 2 Blöcke vor der Wand");
        assertFalse(p.get(6, 15, 16), "weiter draußen nicht");
        assertFalse(p.surface(20, 15, 16), "innen keine Schildzellen");
    }

    @Test
    void smallDentIsBridged() {
        ShieldShape plain = box().protectedSpace();
        // Rille: Dach bei z=16 fehlt -> Loch im Dach (1 breit) wird geschlossen, Schild bleibt gleich
        ShieldShape holed = new ShieldShape(40, 30, 32);
        for (int x = 10; x < 30; x++)
            for (int y = 10; y < 20; y++)
                for (int z = 10; z < 22; z++)
                    if ((x == 10 || x == 29 || y == 10 || y == 19 || z == 10 || z == 21) && !(y == 19 && z == 16)) holed.set(x, y, z);
        ShieldShape p = holed.protectedSpace();
        assertTrue(p.get(20, 21, 16) && p.surface(20, 21, 16), "über der Rille 2 Blöcke Abstand wie daneben");
        assertTrue(plain.surface(20, 21, 16));
        assertTrue(p.get(20, 15, 16), "Inneres trotz Rille geschützt");
    }

    @Test
    void singleSpikeGetsLessDistance() {
        ShieldShape s = box();
        for (int y = 20; y < 24; y++) s.set(20, y, 16); // dünne Antenne 4 Blöcke nach oben
        ShieldShape p = s.protectedSpace();
        assertTrue(p.get(20, 23, 16), "Antenne geschützt");
        assertFalse(p.get(20, 26, 16), "über der Antennenspitze nicht 2+ Blöcke");
    }
}

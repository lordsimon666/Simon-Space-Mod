package de.simon.spacemod.weapons;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class TorpedoMagazinesTest {
    @Test
    void eachLauncherReloadsOnItsOwn() {
        TorpedoMagazines m = new TorpedoMagazines();
        assertEquals(1, m.ready("a", 1, 100));
        m.use("a", 1, 1, 100, 30);
        assertEquals(0, m.ready("a", 1, 101)); // lädt nach
        assertEquals(1, m.ready("b", 1, 101)); // zweiter Werfer ist noch bereit
        m.use("b", 1, 1, 101, 30);
        assertEquals(0, m.ready("a", 1, 120));
        assertEquals(1, m.ready("a", 1, 130)); // nach 30 Ticks wieder voll
    }

    @Test
    void magazineOfFiveFiresFiveThenReloads() {
        TorpedoMagazines m = new TorpedoMagazines();
        long t = 0;
        for (int i = 5; i > 0; i--) {
            assertEquals(i, m.ready("c", 5, t));
            m.use("c", 1, 5, t, 120);
            assertEquals(0, m.ready("c", 5, t + 1)); // kurze Pause zwischen zwei Schüssen
            t += TorpedoMagazines.GAP;
        }
        assertEquals(0, m.ready("c", 5, t));
        assertEquals(5, m.ready("c", 5, t - TorpedoMagazines.GAP + 120)); // nachgeladen
    }

    @Test
    void fullSpreadEmptiesTheMagazine() {
        TorpedoMagazines m = new TorpedoMagazines();
        int shots = m.ready("d", 5, 0);
        m.use("d", shots, 5, 0, 120);
        assertEquals(5, shots);
        assertEquals(0, m.ready("d", 5, 50));
        assertEquals(5, m.ready("d", 5, 120));
    }
}

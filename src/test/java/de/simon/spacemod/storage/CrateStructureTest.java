package de.simon.spacemod.storage;

import static org.junit.jupiter.api.Assertions.*;

import de.simon.spacemod.mining.MiningArea;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

class CrateStructureTest {
    private static Set<BlockPos> shell(int sx, int sy, int sz) {
        Set<BlockPos> s = new HashSet<>();
        for (int x = 0; x < sx; x++)
            for (int y = 0; y < sy; y++)
                for (int z = 0; z < sz; z++)
                    if (x == 0 || y == 0 || z == 0 || x == sx - 1 || y == sy - 1 || z == sz - 1) s.add(new BlockPos(10 + x, 60 + y, -5 + z));
        return s;
    }

    @Test
    void hollowBoxesFrom3To8() {
        for (int n = 3; n <= 8; n++) {
            Set<BlockPos> s = shell(n, n, n);
            CrateStructure.Box box = CrateStructure.validate(s, p -> !s.contains(p));
            assertNotNull(box, "Größe " + n);
            assertEquals(n * n * n, box.volume()); // Luft innen zählt mit
            assertEquals(new BlockPos(10, 60, -5), box.min());
        }
        assertNotNull(CrateStructure.validate(shell(3, 5, 8), p -> true));
        assertEquals(CrateStructure.MAX_SHELL, shell(8, 8, 8).size());
    }

    @Test
    void invalidShapes() {
        assertNull(CrateStructure.validate(shell(2, 3, 3), p -> true), "zu klein");
        assertNull(CrateStructure.validate(shell(9, 3, 3), p -> true), "zu groß");
        Set<BlockPos> hole = shell(4, 4, 4);
        hole.remove(new BlockPos(11, 60, -4));
        assertNull(CrateStructure.validate(hole, p -> true), "Loch in der Wand");
        Set<BlockPos> s = shell(4, 4, 4);
        assertNull(CrateStructure.validate(s, p -> !p.equals(new BlockPos(11, 61, -4))), "Block im Inneren");
        Set<BlockPos> extra = shell(3, 3, 3);
        extra.add(new BlockPos(13, 60, -5));
        assertNull(CrateStructure.validate(extra, p -> true), "Block außen dran");
    }

    @Test
    void connectedStopsWhenTooLarge() {
        Set<BlockPos> s = shell(5, 5, 5);
        assertEquals(s.size(), CrateStructure.connected(new BlockPos(10, 60, -5), s::contains).size());
        assertTrue(CrateStructure.connected(BlockPos.ZERO, p -> true).size() > CrateStructure.MAX_SHELL);
    }

    @Test
    void capacityPerTier() {
        assertEquals(27 * 27, 27 * CrateTier.WOOD.slotsPerBlock);
        assertEquals(512 * 132, 512 * CrateTier.NETHERITE.slotsPerBlock);
        assertTrue(512 * 132 < 1 << 30); // passt in zwei Datenfelder à 15 Bit
    }

    @Test
    void miningAreaCoversEverythingOnce() {
        for (int size : new int[] {1, 4, 5, 16}) {
            Set<String> seen = new HashSet<>();
            int depth = 3;
            for (int i = 0; i < MiningArea.count(size, depth); i++) {
                int[] o = MiningArea.offset(i, size);
                assertTrue(seen.add(o[0] + "," + o[1] + "," + o[2]));
                assertTrue(o[1] >= 0 && o[1] < depth);
                assertTrue(Math.abs(o[0]) <= size / 2 && Math.abs(o[2]) <= size / 2);
            }
            assertEquals(0, MiningArea.offset(0, size)[1]); // oben anfangen
        }
        assertEquals(16 * 16 * 32, MiningArea.count(MiningArea.MAX_SIZE, MiningArea.MAX_DEPTH));
    }

    @Test
    void rotatedMiningAreaHasNoGapsOrDoubles() {
        var start = new BlockPos(0, 10, 0);
        for (float[] a : new float[][] {{0, 0}, {30, 0}, {45, 20}, {10, -35}, {120, 60}}) {
            var q = de.simon.spacemod.flight.ShipPhysics.rotation(0, a[0], a[1]);
            var area = new de.simon.spacemod.mining.MiningArea.Rotated(q, new net.minecraft.world.phys.Vec3(100.3, 70.6, -20.2), start, 8, 6);
            Set<BlockPos> world = new HashSet<>();
            int n = 0;
            for (int i = 0; i < area.total(); i++) {
                var c = area.get(i);
                if (c == null) continue;
                n++;
                assertTrue(world.add(c.world()));
                // Mitte des Weltblocks liegt im Quader unter dem Startpunkt
                assertTrue(c.local().getY() <= 9 && c.local().getY() >= 10 - 6 - 1, "Höhe " + c.local());
            }
            // so viele Blöcke wie Volumen (±15 % durch die Rasterung schräger Kanten)
            assertTrue(Math.abs(n - 8 * 8 * 6) < 8 * 8 * 6 * 0.15, "Anzahl " + n + " bei " + a[0] + "/" + a[1]);
        }
    }
}

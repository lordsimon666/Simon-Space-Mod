package de.simon.spacemod.flight;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.junit.jupiter.api.Test;

/** Laufen im Schiff: Boden, Wand, Springen, Stufe (Block p liegt in [p - 0.5, p + 0.5]). */
class ShipWalkerTest {
    private static Map<BlockPos, VoxelShape> deck() {
        Map<BlockPos, VoxelShape> m = new HashMap<>();
        for (int x = -5; x <= 5; x++)
            for (int z = -5; z <= 5; z++) put(m, new BlockPos(x, -1, z), Shapes.block());
        return m;
    }

    private static void put(Map<BlockPos, VoxelShape> m, BlockPos p, VoxelShape shape) {
        m.put(p, shape.move(p.getX() - 0.5, p.getY() - 0.5, p.getZ() - 0.5));
    }

    private static ShipWalker.State run(Map<BlockPos, VoxelShape> m, ShipWalker.State st, int ticks, float forward, float yaw, boolean jump) {
        for (int i = 0; i < ticks; i++) st = ShipWalker.step(m, st, 0, forward, yaw, jump, false);
        return st;
    }

    @Test
    void fallsOntoTheDeckAndStands() {
        ShipWalker.State st = run(deck(), new ShipWalker.State(new Vec3(0, 2, 0), Vec3.ZERO, false), 40, 0, 0, false);
        assertEquals(-0.5, st.pos().y, 1e-6); // Oberkante des Decks
        assertTrue(st.onGround());
    }

    @Test
    void walksForwardAndStopsAtAWall() {
        Map<BlockPos, VoxelShape> m = deck();
        for (int y = 0; y <= 2; y++) for (int x = -5; x <= 5; x++) put(m, new BlockPos(x, y, 3), Shapes.block()); // Wand bei z = 3
        ShipWalker.State st = run(m, new ShipWalker.State(new Vec3(0, -0.5, 0), Vec3.ZERO, true), 60, 1, 0, false); // yaw 0 = +z
        assertEquals(2.5 - 0.3, st.pos().z, 1e-6); // Wandfläche bei 2.5, halbe Spielerbreite
    }

    @Test
    void jumpsAndLandsAgain() {
        ShipWalker.State st = new ShipWalker.State(new Vec3(0, -0.5, 0), Vec3.ZERO, true);
        st = ShipWalker.step(deck(), st, 0, 0, 0, true, false);
        assertFalse(st.onGround());
        double top = st.pos().y;
        for (int i = 0; i < 30; i++) { st = ShipWalker.step(deck(), st, 0, 0, 0, false, false); top = Math.max(top, st.pos().y); }
        assertTrue(top > 0.6, "Sprunghöhe " + top);
        assertTrue(st.onGround());
        assertEquals(-0.5, st.pos().y, 1e-6);
    }

    @Test
    void stepsUpOntoASlab() {
        Map<BlockPos, VoxelShape> m = deck();
        for (int x = -5; x <= 5; x++) for (int z = 2; z <= 5; z++) put(m, new BlockPos(x, 0, z), Shapes.box(0, 0, 0, 1, 0.5, 1));
        ShipWalker.State st = run(m, new ShipWalker.State(new Vec3(0, -0.5, 0), Vec3.ZERO, true), 40, 1, 0, false);
        assertEquals(0.0, st.pos().y, 1e-6); // auf der Stufe
        assertTrue(st.pos().z > 2.5);
    }
}

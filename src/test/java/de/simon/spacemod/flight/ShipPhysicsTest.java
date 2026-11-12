package de.simon.spacemod.flight;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.junit.jupiter.api.Test;

class ShipPhysicsTest {
    @Test
    void moreEnginesAccelerateFasterHeavierShipsSlower() {
        assertEquals(2 * ShipPhysics.acceleration(1, 100, 1), ShipPhysics.acceleration(2, 100, 1), 1e-9);
        assertTrue(ShipPhysics.acceleration(2, 400, 1) < ShipPhysics.acceleration(2, 100, 1));
        assertEquals(0, ShipPhysics.acceleration(4, 100, 0), 1e-9); // ohne Strom kein Schub
        assertEquals(0.5 * ShipPhysics.acceleration(4, 100, 1), ShipPhysics.acceleration(4, 100, 0.5), 1e-9);
    }

    @Test
    void inertiaGrowsWithSpread() {
        List<BlockPos> compact = new ArrayList<>(), wide = new ArrayList<>();
        for (int i = 0; i < 10; i++) { compact.add(new BlockPos(i % 3, 0, i / 3)); wide.add(new BlockPos(i * 3, 0, 0)); }
        assertTrue(ShipPhysics.inertia(wide) > ShipPhysics.inertia(compact));
        assertTrue(ShipPhysics.angularAcceleration(2, ShipPhysics.inertia(wide), 1) < ShipPhysics.angularAcceleration(2, ShipPhysics.inertia(compact), 1));
    }

    @Test
    void turnReachesTargetWithoutOvershoot() {
        double angle = 0, rate = 0, alpha = 0.3, max = 0;
        int ticks = 0;
        while (ticks++ < 500 && (Math.abs(ShipPhysics.wrap(90 - angle)) > 0.01 || Math.abs(rate) > 0.01)) {
            double[] r = ShipPhysics.turn(angle, rate, 90, alpha);
            angle = r[0];
            rate = r[1];
            max = Math.max(max, angle);
            assertTrue(Math.abs(rate) <= ShipPhysics.MAX_TURN_RATE + 1e-9);
        }
        assertEquals(90, angle, 0.05);
        assertTrue(max < 90.5, "Überschwingen: " + max);
        assertTrue(ticks > 10, "Trägheit: braucht Zeit zum Drehen");
    }

    @Test
    void turnTakesShortestWay() {
        double[] r = ShipPhysics.turn(170, 0, -170, 1);
        assertTrue(r[1] > 0, "von 170° nach -170° geht es über 180°");
    }

    @Test
    void rotationMatchesBlockRotationForQuarterTurns() {
        BlockPos p = new BlockPos(3, 1, -2);
        float yaw0 = 180; // Bug nach Norden
        for (int k = 0; k < 4; k++) {
            Quaternionf q = ShipPhysics.rotation(yaw0, yaw0 + 90 * k, 0);
            Vec3 v = ShipPhysics.rotate(q, p.getX(), p.getY(), p.getZ());
            Rotation rot = Rotation.values()[k]; // NONE, CW90, CW180, CCW90
            BlockPos r = p.rotate(rot);
            assertEquals(r.getX(), v.x, 1e-4, "k=" + k);
            assertEquals(r.getY(), v.y, 1e-4);
            assertEquals(r.getZ(), v.z, 1e-4, "k=" + k);
        }
    }

    @Test
    void bowPointsWhereTheCrosshairLooks() {
        // Schiff, das beim Abheben nach Süden (yaw 0) zeigte, nach yaw 90 / pitch -30 gedreht:
        // der Bug zeigt genau in die Blickrichtung
        Quaternionf q = ShipPhysics.rotation(0, 90, -30);
        Vec3 bow = ShipPhysics.rotate(q, 0, 0, 1);
        Vec3 look = ShipPhysics.forward(90, -30);
        assertEquals(look.x, bow.x, 1e-4);
        assertEquals(look.y, bow.y, 1e-4);
        assertEquals(look.z, bow.z, 1e-4);
    }
}

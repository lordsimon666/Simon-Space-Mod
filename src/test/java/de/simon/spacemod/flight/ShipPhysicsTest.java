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
    void strafeRelativeToShip() {
        // waagerecht mit Bug nach Süden (+z): rechts = Westen (-x), oben = +y
        Vec3 fwd = ShipPhysics.forward(0, 0);
        Vec3 up = ShipPhysics.velocity(Vec3.ZERO, fwd, 0, 0, 1, 0.1, 0.05);
        assertEquals(0.1, up.y, 1e-9);
        assertEquals(0, up.x, 1e-9);
        Vec3 right = ShipPhysics.velocity(Vec3.ZERO, fwd, 0, 1, 0, 0.1, 0.05);
        assertEquals(-0.1, right.x, 1e-9);
        assertEquals(0, right.y, 1e-9);
        // geneigtes Schiff: „oben“ kippt mit, steht senkrecht auf dem Bug
        Vec3 tilted = ShipPhysics.forward(0, 30);
        Vec3 u = ShipPhysics.velocity(Vec3.ZERO, tilted, 0, 0, 1, 0.1, 0.05);
        assertEquals(0, u.dot(tilted), 1e-9);
        assertTrue(u.y > 0.08 && u.z > 0.04, "oben kippt mit: " + u);
        // auf der geschobenen Achse wird nicht gedämpft, auf den anderen schon
        Vec3 v = ShipPhysics.velocity(new Vec3(0, 0.5, 1), fwd, 0, 0, 1, 0.1, 0.2);
        assertEquals(0.6, v.y, 1e-9);
        assertEquals(0.8, v.z, 1e-9);
    }

    @Test
    void dampersStrongerAndAdjustable() {
        // Standard (10 %) bremst deutlich stärker als die alten 1 % je Tick
        assertTrue(ShipPhysics.damperDecay(ShipPhysics.DEFAULT_DAMPER) > 0.1);
        assertTrue(ShipPhysics.damperDecay(10) > ShipPhysics.damperDecay(5));
        assertEquals(0, ShipPhysics.damperDecay(0), 1e-9);
        // Kosten: bis 10 % kostenlos, 100 % = 1000 FE/t
        assertEquals(0, ShipPhysics.damperCost(0));
        assertEquals(0, ShipPhysics.damperCost(ShipPhysics.DEFAULT_DAMPER));
        assertEquals(1000, ShipPhysics.damperCost(10));
        assertTrue(ShipPhysics.damperCost(5) > 0 && ShipPhysics.damperCost(5) < 1000);
        // aus Höchstgeschwindigkeit: 100 % steht nach 3 Ticks, 10 % nach 30 Ticks (zehnmal so lange)
        assertEquals(3, stopTicks(10));
        assertEquals(30, stopTicks(1), 1);
        assertEquals(6, stopTicks(5), 1);
        // mit Schub nach vorn wird seitliches Rutschen gedämpft, die Vorwärtsfahrt nicht
        Vec3 w = ShipPhysics.velocity(new Vec3(1, 0, 1), new Vec3(0, 0, 1), 1, 0, 0, 0.1, 0.5);
        assertEquals(1.1, w.z, 1e-9);
        assertEquals(0.5, w.x, 1e-9);
    }

    private static int stopTicks(int level) {
        Vec3 v = new Vec3(ShipPhysics.MAX_SPEED, 0, 0);
        int t = 0;
        while (v.length() > ShipPhysics.STOP_SPEED + 1e-9 && t < 1000) {
            v = ShipPhysics.velocity(v, new Vec3(0, 0, 1), 0, 0, 0, 0, ShipPhysics.damperDecay(level));
            t++;
        }
        return t;
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

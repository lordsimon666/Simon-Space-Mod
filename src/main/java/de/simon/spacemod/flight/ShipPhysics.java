package de.simon.spacemod.flight;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Flugphysik ohne Minecraft-Abhängigkeiten (testbar): Masse, Trägheitsmoment, Drehung mit
 * begrenzter Winkelbeschleunigung (Trägheit) und Lage im Raum.
 */
public final class ShipPhysics {
    /** Schub je Impulsantrieb (Masse · Blöcke / Tick²); Masse = 1 je Block. */
    public static final double THRUST_PER_ENGINE = 1.5;
    /** Hebelarm der Antriebe für das Drehmoment (Blöcke). */
    public static final double LEVER = 2.5;
    /** Höchstgeschwindigkeit (Blöcke / Tick) und höchste Drehrate (Grad / Tick). */
    public static final double MAX_SPEED = 3.0, MAX_TURN_RATE = 6.0;
    /** Strombedarf je Antrieb bei vollem Schub (RF / Tick). */
    public static final int RF_PER_ENGINE = 2000;
    /** Trägheitsdämpfer ohne Schub (Faktor je Tick). */
    public static final double DAMPING = 0.99;

    private ShipPhysics() {}

    /** Trägheitsmoment um den Schwerpunkt (Masse 1 je Block, plus 1/6 für den Würfel selbst). */
    public static double inertia(List<BlockPos> blocks) {
        if (blocks.isEmpty()) return 1;
        double cx = 0, cy = 0, cz = 0;
        for (BlockPos p : blocks) { cx += p.getX(); cy += p.getY(); cz += p.getZ(); }
        cx /= blocks.size(); cy /= blocks.size(); cz /= blocks.size();
        double i = 0;
        for (BlockPos p : blocks) {
            double dx = p.getX() - cx, dy = p.getY() - cy, dz = p.getZ() - cz;
            i += dx * dx + dy * dy + dz * dz + 1.0 / 6;
        }
        return i;
    }

    /** Lineare Beschleunigung (Blöcke / Tick²) bei vollem Schub. */
    public static double acceleration(int engines, double mass, double power) {
        return mass <= 0 ? 0 : engines * THRUST_PER_ENGINE * power / mass;
    }

    /** Winkelbeschleunigung (Grad / Tick²) bei vollem Ruder. */
    public static double angularAcceleration(int engines, double inertia, double power) {
        return inertia <= 0 ? 0 : Math.toDegrees(engines * THRUST_PER_ENGINE * LEVER * power / inertia);
    }

    /** Kürzester Winkelunterschied in Grad (-180..180). */
    public static double wrap(double deg) {
        deg %= 360;
        if (deg > 180) deg -= 360;
        if (deg < -180) deg += 360;
        return deg;
    }

    /**
     * Ein Tick Drehung zum Ziel mit Trägheit: die Drehrate ändert sich höchstens um {@code alpha},
     * vor dem Ziel wird rechtzeitig abgebremst (kein Überschwingen).
     * @return {neuer Winkel, neue Drehrate}
     */
    public static double[] turn(double angle, double rate, double target, double alpha) {
        double err = wrap(target - angle);
        // höchste Rate, mit der man in ganzen Ticks bei maximaler Bremsung noch vor dem Ziel steht:
        // v + (v - a) + (v - 2a) + ... <= |err|  ->  v (v + a) / (2a) <= |err|
        double stop = alpha <= 0 ? 0 : (-alpha + Math.sqrt(alpha * alpha + 8 * alpha * Math.abs(err))) / 2;
        double want = Math.signum(err) * Math.min(MAX_TURN_RATE, stop);
        double d = Math.max(-alpha, Math.min(alpha, want - rate));
        rate += d;
        if (Math.abs(err) < 0.05 && Math.abs(rate) < alpha) rate = err; // einrasten
        return new double[] {angle + rate, rate};
    }

    /**
     * Drehung des Schiffs: lokale Koordinaten (Weltachsen beim Abheben, Bug in Richtung yaw0)
     * -> Weltlage bei Gierwinkel yaw und Nickwinkel pitch (Minecraft-Konvention, Grad).
     */
    public static Quaternionf rotation(float yaw0, float yaw, float pitch) {
        return new Quaternionf().rotateY((float) Math.toRadians(-yaw)).rotateX((float) Math.toRadians(pitch))
            .rotateY((float) Math.toRadians(yaw0));
    }

    public static Vec3 rotate(Quaternionf q, double x, double y, double z) {
        Vector3f v = q.transform(new Vector3f((float) x, (float) y, (float) z));
        return new Vec3(v.x, v.y, v.z);
    }

    public static Vec3 inverse(Quaternionf q, Vec3 v) {
        Vector3f r = new Quaternionf(q).conjugate().transform(new Vector3f((float) v.x, (float) v.y, (float) v.z));
        return new Vec3(r.x, r.y, r.z);
    }

    /** Blickrichtung (Bug) bei yaw/pitch. */
    public static Vec3 forward(float yaw, float pitch) {
        double y = Math.toRadians(yaw), p = Math.toRadians(pitch);
        return new Vec3(-Math.sin(y) * Math.cos(p), -Math.sin(p), Math.cos(y) * Math.cos(p));
    }
}

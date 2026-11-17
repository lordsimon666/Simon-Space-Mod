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
    /** Trägheitsdämpfer ohne Steuerung: 10 % (Stufe 1 von 10), kostenlos. */
    public static final int DEFAULT_DAMPER = 1;
    /** Strom der Trägheitsdämpfer bei 100 % (FE / Tick). */
    public static final int DAMPER_FE_MAX = 1000;

    /** Bremsweg in Ticks aus Höchstgeschwindigkeit bei 100 % (bei Stufe l das 10/l-Fache: 10 % = 30 Ticks). */
    public static final int STOP_TICKS_FULL = 3;
    /** Ab dieser Geschwindigkeit (Blöcke / Tick) gilt das Schiff als stehend. */
    public static final double STOP_SPEED = 0.05;

    /**
     * Anteil der Geschwindigkeit, den die Trägheitsdämpfer je Tick wegnehmen (in jeder Richtung ohne Schub).
     * Die Zeit bis zum Stillstand aus voller Fahrt ist umgekehrt proportional zur Stufe: 100 % = 3 Ticks,
     * 10 % (Standard) = 30 Ticks, also zehnmal so stark; 0 % bremst gar nicht.
     */
    public static double damperDecay(int level) {
        if (level <= 0) return 0;
        double ticks = STOP_TICKS_FULL * 10.0 / level;
        return 1 - Math.pow(STOP_SPEED / MAX_SPEED, 1 / ticks);
    }

    /** Strom der Trägheitsdämpfer (FE / Tick): bis 10 % kostenlos, dann linear bis 1000 FE/t bei 100 %. */
    public static int damperCost(int level) {
        return level <= DEFAULT_DAMPER ? 0 : (int) Math.round((level - DEFAULT_DAMPER) * DAMPER_FE_MAX / (10.0 - DEFAULT_DAMPER));
    }

    /**
     * Ein Tick Geschwindigkeit, alles relativ zum Schiff: Schub entlang des Bugs (throttle), nach rechts/links
     * (strafe, A/D) und nach oben/unten (vertical, Leertaste/Strg). Die Trägheitsdämpfer bremsen jede dieser
     * Achsen, auf der gerade kein Schub gegeben wird – ohne Schub ganz, mit Schub nach vorn auch das Rutschen.
     */
    public static Vec3 velocity(Vec3 v, Vec3 forward, int throttle, int strafe, int vertical, double accel, double decay) {
        Vec3 right = right(forward), up = right.cross(forward).normalize();
        double k = 1 - decay;
        double f = v.dot(forward), r = v.dot(right), u = v.dot(up);
        f = throttle != 0 ? f + throttle * accel : f * k;
        r = strafe != 0 ? r + strafe * accel : r * k;
        u = vertical != 0 ? u + vertical * accel : u * k;
        return forward.scale(f).add(right.scale(r)).add(up.scale(u));
    }

    /** Rechte Seite des Schiffs (waagerecht, das Schiff rollt nicht). */
    public static Vec3 right(Vec3 forward) {
        Vec3 r = forward.cross(new Vec3(0, 1, 0));
        return r.lengthSqr() < 1e-9 ? new Vec3(1, 0, 0) : r.normalize();
    }

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

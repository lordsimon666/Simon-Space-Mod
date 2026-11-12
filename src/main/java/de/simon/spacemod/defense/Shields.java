package de.simon.spacemod.defense;

import de.simon.spacemod.ModBlocks;
import de.simon.spacemod.entity.FlyingShipEntity;
import de.simon.spacemod.flight.ShipPhysics;
import de.simon.spacemod.flight.Shipyard;
import de.simon.spacemod.turbolift.TurboliftNetwork;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.network.PacketDistributor;
import org.joml.Quaternionf;
import org.jspecify.annotations.Nullable;

/**
 * Alle aktiven Schilde – auch die fliegender Schiffe (deren Blöcke und Schild in der Werft liegen;
 * Treffer in der Welt werden ins Schiffssystem umgerechnet). Schilde fangen Explosionen von außen ab
 * (Stärke ~ Radius²), halten Schiffsphaser und Torpedos auf und leuchten um Treffer kurz auf –
 * blau, mit wenig Leben gelb, fast zerstört rot. Schüsse des eigenen Schiffs gehen durch das eigene
 * Schild hindurch. Fliegende Schiffe ohne (oder mit durchbrochenem) Schild nehmen Hüllenschaden.
 */
public final class Shields {
    private static final Map<ResourceKey<Level>, List<Shield>> ACTIVE = new HashMap<>();
    /** Schadenswerte (Leben je Schildblock im Zentrum des Treffers). */
    public static final float PHOTON_PER_RADIUS = 40F, QUANTUM_BONUS = 1.25F;
    /** Während einer gespiegelten Explosion in der Werft nicht noch einmal abfangen. */
    private static boolean mirroring;

    private Shields() {}

    public static void init() {
        ExplosionEvent.Detonate.BUS.addListener(Shields::onDetonate);
    }

    static void register(Shield s) {
        ACTIVE.computeIfAbsent(s.level.dimension(), k -> new ArrayList<>()).add(s);
    }

    static void unregister(Shield s) {
        List<Shield> l = ACTIVE.get(s.level.dimension());
        if (l != null) l.remove(s);
    }

    /** Ein Schild, wie es in einer Welt liegt: fest (ship == null) oder mit einem fliegenden Schiff bewegt. */
    public static final class View {
        public final Shield shield;
        public final @Nullable FlyingShipEntity ship;
        private final Quaternionf rot;

        View(Shield shield, @Nullable FlyingShipEntity ship) {
            this.shield = shield;
            this.ship = ship;
            this.rot = ship == null ? new Quaternionf() : ship.rotation(1F);
        }

        /** Weltpunkt -> Koordinaten des Schildrasters (Welt bzw. Werft). */
        public Vec3 toGrid(Vec3 w) {
            return toGridRaw(w);
        }

        Vec3 toGridRaw(Vec3 w) {
            if (ship == null) return w;
            Vec3 local = ShipPhysics.inverse(rot, w.subtract(ship.position()));
            return Vec3.atLowerCornerOf(ship.plotOrigin()).add(local).add(0.5, 0.5, 0.5);
        }

        public Vec3 toWorld(Vec3 g) {
            if (ship == null) return g;
            Vec3 local = g.subtract(Vec3.atLowerCornerOf(ship.plotOrigin())).subtract(0.5, 0.5, 0.5);
            return ShipPhysics.rotate(rot, local.x, local.y, local.z).add(ship.position());
        }

        public boolean up() {
            return shield.up;
        }

        public boolean contains(Vec3 w) {
            return shield.contains(toGrid(w));
        }

        public boolean interior(Vec3 w) {
            return shield.interior(toGrid(w));
        }

        public AABB box() {
            return ship == null ? shield.box : ship.getBoundingBox().inflate(4);
        }

        public float hpAt(Vec3 w) {
            BlockPos c = shield.nearestCell(toGrid(w), 2);
            return c == null ? 0 : shield.hp(c.asLong());
        }

        /** @return true, wenn das Schild dort gehalten hat. */
        public boolean damage(Vec3 w, float amount, double radius) {
            return shield.damage(this, toGrid(w), amount, radius);
        }

        Level worldLevel() {
            return ship == null ? shield.level : ship.level();
        }

        Quaternionf rotation() {
            return rot;
        }
    }

    /** Alle Schilde, die in dieser Welt wirken (feste und die fliegender Schiffe hier). */
    public static List<View> views(Level level) {
        List<View> out = new ArrayList<>();
        for (Shield s : ACTIVE.getOrDefault(level.dimension(), List.of())) out.add(new View(s, null));
        if (Shipyard.is(level)) return out;
        List<Shield> yard = ACTIVE.getOrDefault(Shipyard.KEY, List.of());
        if (yard.isEmpty()) return out;
        for (FlyingShipEntity ship : Shipyard.ships()) {
            if (ship.level() != level || ship.plot() < 0) continue;
            for (Shield s : yard)
                if (Shipyard.plotOf(s.console) == ship.plot()) out.add(new View(s, ship));
        }
        return out;
    }

    private static void onDetonate(ExplosionEvent.Detonate e) {
        if (mirroring || !(e.getLevel() instanceof ServerLevel level)) return;
        Vec3 c = e.getExplosion().center();
        float r = e.getExplosion().radius();
        List<FlyingShipEntity> held = new ArrayList<>();
        for (View v : views(level)) {
            if (!v.up() || !v.box().inflate(r + 2).contains(c) || v.interior(c)) continue;
            // Schaden wächst mit der Explosionsstärke (Radius²)
            if (v.damage(c, 10F * r * r, Math.max(2, r))) {
                e.getAffectedBlocks().removeIf(p -> v.contains(Vec3.atCenterOf(p)));
                e.getAffectedEntities().removeIf(en -> v.contains(en.position()));
                if (v.ship != null) held.add(v.ship);
            }
        }
        // fliegende Schiffe ohne haltendes Schild: Explosion trifft ihre echten Blöcke in der Werft
        if (Shipyard.is(level)) return;
        ServerLevel yard = Shipyard.level(level.getServer());
        if (yard == null) return;
        for (FlyingShipEntity ship : Shipyard.ships()) {
            if (ship.level() != level || ship.plot() < 0 || held.contains(ship)) continue;
            if (!ship.getBoundingBox().inflate(r + 1).contains(c)) continue;
            Vec3 g = new View(null, ship).toGridRaw(c);
            mirroring = true;
            try {
                yard.explode(null, g.x, g.y, g.z, r, false, Level.ExplosionInteraction.TNT);
            } finally {
                mirroring = false;
            }
        }
    }

    /** Treffer auf ein Schild: Schild und Punkt (Welt). */
    public record Hit(View view, Vec3 point) {
        public boolean damage(float amount, double radius) {
            return view.damage(point, amount, radius);
        }
    }

    /**
     * Erstes Schild (mit Leben an der Stelle), in das die Strecke von außen eindringt. Schilde, die
     * {@code origin} umschließen (das eigene Schiff des Schützen), werden ignoriert.
     */
    public static @Nullable Hit intercept(Level level, Vec3 from, Vec3 to, @Nullable Vec3 origin) {
        List<View> views = views(level);
        if (views.isEmpty()) return null;
        AABB seg = new AABB(from, to).inflate(1);
        Vec3 d = to.subtract(from);
        double len = d.length();
        if (len < 1e-6) return null;
        Vec3 dir = d.scale(1 / len);
        Hit best = null;
        double bestT = Double.MAX_VALUE;
        for (View v : views) {
            if (!v.up() || !v.box().intersects(seg)) continue;
            if (origin != null && v.contains(origin)) continue; // eigenes Schild: Schüsse gehen hinaus
            boolean prev = v.contains(from);
            for (double t = 0.5; t <= len && t < bestT; t += 0.5) {
                Vec3 p = from.add(dir.scale(t));
                boolean in = v.contains(p);
                if (in && !prev) {
                    if (v.hpAt(p) > 0) {
                        best = new Hit(v, p);
                        bestT = t;
                    }
                    break;
                }
                prev = in;
            }
        }
        return best;
    }

    /** Leuchten um einen Treffer: Schildblöcke im Umkreis von 10 Blöcken mit ihrem Zustand. */
    static void flash(View v, BlockPos hit) {
        Shield s = v.shield;
        if (!(v.worldLevel() instanceof ServerLevel level)) return;
        List<Integer> cells = new ArrayList<>();
        int r = 10;
        for (int x = -r; x <= r; x++)
            for (int y = -r; y <= r; y++)
                for (int z = -r; z <= r; z++) {
                    if (x * x + y * y + z * z > r * r) continue;
                    int wx = hit.getX() + x, wy = hit.getY() + y, wz = hit.getZ() + z;
                    if (!s.shell(wx, wy, wz)) continue;
                    float f = s.hp(BlockPos.asLong(wx, wy, wz)) / s.maxHp;
                    int q = Math.max(0, Math.min(255, Math.round(f * 255)));
                    cells.add(((x + 64) & 0x7F) | ((y + 64) & 0x7F) << 7 | ((z + 64) & 0x7F) << 14 | q << 21);
                }
        ShieldFlashEntity e = ModBlocks.SHIELD_FLASH.get().create(level, EntitySpawnReason.TRIGGERED);
        if (e == null) return;
        Vec3 c = v.toWorld(Vec3.atCenterOf(hit));
        e.setPos(c.x, c.y, c.z);
        level.addFreshEntity(e);
        Quaternionf q = v.rotation();
        ShieldFlashMessage msg = new ShieldFlashMessage(e.getId(), cells.stream().mapToInt(Integer::intValue).toArray(),
            new float[] {q.x, q.y, q.z, q.w});
        for (ServerPlayer p : level.players())
            if (p.distanceToSqr(c) < 192 * 192) TurboliftNetwork.CHANNEL.send(msg, PacketDistributor.PLAYER.with(p));
    }
}

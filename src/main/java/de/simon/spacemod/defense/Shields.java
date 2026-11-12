package de.simon.spacemod.defense;

import de.simon.spacemod.ModBlocks;
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
import org.jspecify.annotations.Nullable;

/**
 * Alle aktiven Schilde: fangen Explosionen von außen ab (Stärke ~ Radius²), halten Schiffsphaser
 * und Torpedos auf und lassen getroffene Bereiche (10 Blöcke um den Treffer) kurz aufleuchten –
 * blau, mit wenig Leben gelb, fast zerstört rot.
 */
public final class Shields {
    private static final Map<ResourceKey<Level>, List<Shield>> ACTIVE = new HashMap<>();
    /** Schadenswerte (Leben je Schildblock im Zentrum des Treffers). */
    public static final float PHOTON_PER_RADIUS = 40F, QUANTUM_BONUS = 1.25F;

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

    private static List<Shield> in(Level level) {
        return ACTIVE.getOrDefault(level.dimension(), List.of());
    }

    private static void onDetonate(ExplosionEvent.Detonate e) {
        if (!(e.getLevel() instanceof ServerLevel level)) return;
        Vec3 c = e.getExplosion().center();
        float r = e.getExplosion().radius();
        for (Shield s : List.copyOf(in(level))) {
            if (!s.up || !s.box.inflate(r + 2).contains(c) || s.interior(c)) continue;
            // Schaden wächst mit der Explosionsstärke (Radius²)
            if (s.damage(c, 10F * r * r, Math.max(2, r))) {
                e.getAffectedBlocks().removeIf(p -> s.contains(Vec3.atCenterOf(p)));
                e.getAffectedEntities().removeIf(en -> s.contains(en.position()));
            }
        }
    }

    /** Treffer auf ein Schild: Schild und Punkt. */
    public record Hit(Shield shield, Vec3 point) {}

    /** Erstes Schild (mit Leben an der Stelle), in das die Strecke von außen eindringt. */
    public static @Nullable Hit intercept(Level level, Vec3 from, Vec3 to) {
        List<Shield> shields = in(level);
        if (shields.isEmpty()) return null;
        AABB seg = new AABB(from, to).inflate(1);
        Vec3 d = to.subtract(from);
        double len = d.length();
        if (len < 1e-6) return null;
        Vec3 dir = d.scale(1 / len);
        Hit best = null;
        double bestT = Double.MAX_VALUE;
        for (Shield s : shields) {
            if (!s.up || !s.box.intersects(seg)) continue;
            boolean prev = s.contains(from);
            for (double t = 0.5; t <= len && t < bestT; t += 0.5) {
                Vec3 p = from.add(dir.scale(t));
                boolean in = s.contains(p);
                if (in && !prev) {
                    BlockPos cell = s.nearestCell(p, 2);
                    if (cell != null && s.hp(cell.asLong()) > 0) {
                        best = new Hit(s, p);
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
    static void flash(Shield s, BlockPos hit) {
        ServerLevel level = s.level;
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
        Vec3 c = Vec3.atLowerCornerOf(hit);
        e.setPos(c.x, c.y, c.z);
        level.addFreshEntity(e);
        int[] data = cells.stream().mapToInt(Integer::intValue).toArray();
        ShieldFlashMessage msg = new ShieldFlashMessage(e.getId(), data);
        for (ServerPlayer p : level.players())
            if (p.distanceToSqr(c) < 192 * 192) TurboliftNetwork.CHANNEL.send(msg, PacketDistributor.PLAYER.with(p));
    }
}

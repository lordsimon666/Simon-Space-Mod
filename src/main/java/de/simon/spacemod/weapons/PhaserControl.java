package de.simon.spacemod.weapons;

import de.simon.spacemod.SimonSpaceMod;
import de.simon.spacemod.block.PhaserBlock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;

/** Feuerleitung: sucht das Ziel im Blick des Spielers und lässt passende Phaser darauf feuern. */
public final class PhaserControl {
    private PhaserControl() {}

    private static final double RANGE = 192;
    private static final int COOLDOWN_TICKS = 20;
    private static final int SCAN_RADIUS = 40;
    private static final Map<String, Long> LAST_SHOT = new HashMap<>();

    public static void fire(ServerLevel level, BlockPos console, ServerPlayer player) {
        String key = level.dimension().identifier() + "@" + console.asLong();
        long now = level.getGameTime();
        Long last = LAST_SHOT.get(key);
        if (last != null && now - last < COOLDOWN_TICKS) {
            player.sendOverlayMessage(Component.translatable("message.simonspacemod.phaser.recharging"));
            return;
        }
        WeaponsData data = WeaponsData.get(level);
        List<BlockPos> phasers = data.phasers(console);
        if (phasers == null) {
            phasers = scan(level, console);
            data.set(console, phasers);
            phasers = data.phasers(console);
        }
        phasers.removeIf(p -> !(level.getBlockState(p).getBlock() instanceof PhaserBlock));
        if (phasers.isEmpty()) {
            player.sendOverlayMessage(Component.translatable("message.simonspacemod.phaser.none"));
            return;
        }
        // Schiffsbereich: Kasten um alle Phaser (darin werden Blöcke beim Zielen ignoriert)
        AABB ship = new AABB(phasers.get(0));
        for (BlockPos p : phasers) ship = ship.minmax(new AABB(p));
        ship = ship.inflate(4);

        Vec3 eye = player.getEyePosition(), look = player.getLookAngle();
        Vec3 end = eye.add(look.scale(RANGE));
        Vec3 target = end;
        boolean hit = false;
        // 1. Kreatur im Blick
        double best = RANGE;
        for (Entity e : level.getEntities(player, new AABB(eye, end).inflate(2), e -> e instanceof LivingEntity le && le.isAlive())) {
            Optional<Vec3> h = e.getBoundingBox().inflate(0.6).clip(eye, end);
            if (h.isEmpty()) continue;
            double d = h.get().distanceTo(eye);
            if (d < best) {
                best = d;
                target = e.getBoundingBox().getCenter();
                hit = true;
            }
        }
        // 2. sonst der erste Block außerhalb des Schiffs
        if (!hit) {
            for (double t = 1; t < RANGE; t += 0.5) {
                Vec3 v = eye.add(look.scale(t));
                BlockPos bp = BlockPos.containing(v);
                BlockState st = level.getBlockState(bp);
                if (st.isAir() || isShipBlock(st) || ship.contains(v) || st.getCollisionShape(level, bp).isEmpty()) continue;
                target = v;
                hit = true;
                break;
            }
        }
        // Phaser mit freier Schussbahn, am nächsten zum Ziel, mind. 6 Blöcke auseinander, höchstens 2
        final Vec3 tgt = target;
        List<BlockPos> sorted = new ArrayList<>(phasers);
        sorted.sort(Comparator.comparingDouble(p -> Vec3.atCenterOf(p).distanceToSqr(tgt)));
        List<BlockPos> chosen = new ArrayList<>();
        for (BlockPos p : sorted) {
            if (chosen.size() >= 2) break;
            if (!clear(level, p, tgt)) continue;
            if (chosen.stream().anyMatch(c -> c.distSqr(p) < 36)) continue;
            chosen.add(p);
        }
        if (chosen.isEmpty()) {
            player.sendOverlayMessage(Component.translatable("message.simonspacemod.phaser.no_arc"));
            return;
        }
        LAST_SHOT.put(key, now);
        level.playSound(null, console, SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 1.0F, 1.8F);
        for (BlockPos p : chosen) beam(level, player, p, tgt, ((PhaserBlock) level.getBlockState(p).getBlock()).kind);
        if (hit) level.explode(null, tgt.x, tgt.y, tgt.z, 1.5F, false, Level.ExplosionInteraction.NONE);
        player.sendOverlayMessage(Component.translatable("message.simonspacemod.phaser.fire"));
    }

    /** Ist der Weg vom Phaser in Richtung Ziel frei (nicht durch die eigene Hülle)? */
    private static boolean clear(ServerLevel level, BlockPos p, Vec3 target) {
        Vec3 c = Vec3.atCenterOf(p);
        Vec3 d = target.subtract(c).normalize();
        for (double t = 1.0; t <= 3.0; t += 0.5) {
            BlockPos q = BlockPos.containing(c.add(d.scale(t)));
            if (q.equals(p)) continue;
            BlockState st = level.getBlockState(q);
            if (!st.isAir() && !st.getCollisionShape(level, q).isEmpty()) return false;
        }
        return true;
    }

    private static void beam(ServerLevel level, ServerPlayer shooter, BlockPos from, Vec3 to, PhaserBlock.Kind kind) {
        Vec3 c = Vec3.atCenterOf(from);
        Vec3 dir = to.subtract(c);
        double len = dir.length();
        dir = dir.normalize();
        Vec3 start = c.add(dir.scale(0.7));
        DustParticleOptions outer = new DustParticleOptions(kind.color, 1.6F);
        DustParticleOptions inner = new DustParticleOptions(kind.core, 0.7F);
        for (double t = 0.7; t < len; t += 0.4) {
            if (kind.pulse && (t % 4.0) > 1.6) continue; // Phasenkanone: einzelne Pulse
            Vec3 v = c.add(dir.scale(t));
            level.sendParticles(outer, true, true, v.x, v.y, v.z, 1, 0, 0, 0, 0);
            level.sendParticles(inner, true, true, v.x, v.y, v.z, 1, 0, 0, 0, 0);
        }
        level.playSound(null, from, SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 3.0F, kind.pulse ? 2.0F : 1.6F);
        // Schaden an allem, was im Strahl steht
        for (Entity e : level.getEntities(shooter, new AABB(start, to).inflate(1),
                e -> e instanceof LivingEntity le && le.isAlive())) {
            if (e.getBoundingBox().inflate(0.3).clip(start, to.add(dir.scale(0.5))).isEmpty()) continue;
            e.hurtServer(level, level.damageSources().indirectMagic(shooter, shooter), kind.damage);
            e.igniteForSeconds(3);
        }
    }

    /** Selbst gebaute Waffenstation: Phaser in der Nähe suchen. */
    private static List<BlockPos> scan(ServerLevel level, BlockPos console) {
        List<BlockPos> found = new ArrayList<>();
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int x = -SCAN_RADIUS; x <= SCAN_RADIUS; x++)
            for (int y = -SCAN_RADIUS; y <= SCAN_RADIUS; y++)
                for (int z = -SCAN_RADIUS; z <= SCAN_RADIUS; z++) {
                    m.set(console.getX() + x, console.getY() + y, console.getZ() + z);
                    if (!level.isLoaded(m)) continue;
                    if (level.getBlockState(m).getBlock() instanceof PhaserBlock) found.add(m.immutable());
                }
        return found;
    }

    private static boolean isShipBlock(BlockState s) {
        Identifier id = ForgeRegistries.BLOCKS.getKey(s.getBlock());
        return id != null && id.getNamespace().equals(SimonSpaceMod.MODID);
    }
}

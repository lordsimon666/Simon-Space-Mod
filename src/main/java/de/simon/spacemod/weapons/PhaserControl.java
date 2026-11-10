package de.simon.spacemod.weapons;

import de.simon.spacemod.ModBlocks;
import de.simon.spacemod.block.PhaserBlock;
import de.simon.spacemod.block.TorpedoLauncherBlock;
import de.simon.spacemod.entity.TorpedoEntity;
import de.simon.spacemod.entity.TorpedoStyle;
import net.minecraft.world.entity.EntitySpawnReason;
import org.jspecify.annotations.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Feuerleitung: sucht das Ziel im Blick des Spielers und lässt passende Phaser darauf feuern. */
public final class PhaserControl {
    private PhaserControl() {}

    private static final double RANGE = 192;      // Kreaturen
    private static final double BLOCK_RANGE = 400; // Blöcke
    private static final int COOLDOWN_TICKS = 20;
    private static final int SCAN_RADIUS = 40;
    private static final Map<String, Long> LAST_SHOT = new HashMap<>();

    /**
     * Feuert die Phaser einer Station.
     * @param power Stufe 1: Schaden ohne Blockschaden, 2: mehr Schaden und Blockschaden, 3: am meisten
     *              Schaden und große Explosion. Wie stark, hängt vom Schiff ab (NX-01 schwach … Enterprise-E stark).
     */
    public static void fire(ServerLevel level, BlockPos console, ServerPlayer player, int power) {
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
            linkPhasers(level, console);
            phasers = data.phasers(console);
        }
        phasers.removeIf(p -> !(level.getBlockState(p).getBlock() instanceof PhaserBlock));
        if (phasers.isEmpty()) {
            player.sendOverlayMessage(Component.translatable("message.simonspacemod.phaser.none"));
            return;
        }
        AABB own = ownShip(data.battery(console), console, phasers);
        Aim aim = aim(level, player, own);
        boolean hit = aim.hit();
        Vec3 target = aim.point();
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
        LAST_SHOT.put(key, now + (power - 1) * 10L); // stärkere Schüsse laden länger nach
        // Zielgebiet geladen halten, damit Treffer und Explosion auch weit draußen wirken
        level.getChunkSource().addTicketWithRadius(TicketType.ENDER_PEARL, ChunkPos.containing(BlockPos.containing(tgt)), 2);
        WeaponsData.Battery battery = data.battery(console);
        int tier = battery != null && battery.tier() > 0 ? battery.tier() : tierOf(level, chosen.get(0));
        float damage = (6 + 2 * tier) * (power == 1 ? 1F : power == 2 ? 1.6F : 2.2F);
        float radius = power == 1 ? 1.0F : power == 2 ? 1.8F + 0.3F * tier : 2.5F + 0.6F * tier;
        level.playSound(null, console, SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 1.0F, 1.8F);
        for (BlockPos p : chosen) beam(level, player, p, tgt, ((PhaserBlock) level.getBlockState(p).getBlock()).kind, damage, power);
        // Blockschaden ab Stufe 2; nur das eigene Schiff bleibt heil
        if (hit) level.explode(null, null, spare(own), tgt.x, tgt.y, tgt.z, radius, power == 3,
            power >= 2 ? Level.ExplosionInteraction.TNT : Level.ExplosionInteraction.NONE);
        player.sendOverlayMessage(Component.translatable("message.simonspacemod.phaser.fire"));
    }

    /** Ziel im Fadenkreuz: Punkt, ggf. Kreatur, und ob überhaupt etwas getroffen wird. */
    private record Aim(Vec3 point, @Nullable Entity entity, boolean hit) {}

    private static Aim aim(ServerLevel level, ServerPlayer player, AABB own) {
        Vec3 eye = player.getEyePosition(), look = player.getLookAngle();
        Vec3 end = eye.add(look.scale(RANGE));
        Vec3 target = end;
        Entity entity = null;
        boolean hit = false;
        // 1. erster Block im Fadenkreuz (das eigene Schiff zählt nicht, andere Schiffe schon)
        double blockDist = BLOCK_RANGE;
        BlockPos prev = null;
        for (double t = 0.5; t < BLOCK_RANGE; t += 0.2) {
            Vec3 v = eye.add(look.scale(t));
            BlockPos bp = BlockPos.containing(v);
            if (bp.equals(prev)) continue;
            prev = bp;
            BlockState st = level.getBlockState(bp);
            if (st.isAir() || own.contains(v) || st.getCollisionShape(level, bp).isEmpty()) continue;
            target = v;
            blockDist = t;
            hit = true;
            break;
        }
        // 2. Kreatur im Blick, wenn sie vor dem Block steht
        double best = Math.min(RANGE, blockDist);
        for (Entity e : level.getEntities(player, new AABB(eye, end).inflate(2), e -> e instanceof LivingEntity le && le.isAlive())) {
            Optional<Vec3> h = e.getBoundingBox().inflate(0.6).clip(eye, end);
            if (h.isEmpty()) continue;
            double d = h.get().distanceTo(eye);
            if (d < best) {
                best = d;
                target = e.getBoundingBox().getCenter();
                entity = e;
                hit = true;
            }
        }
        if (!hit) target = eye.add(look.scale(BLOCK_RANGE));
        return new Aim(target, entity, hit);
    }

    /**
     * Feuert einen Photonen- (P) bzw. Quantentorpedo (O, nur Enterprise-E) aus dem Werfer, der dem
     * Ziel am nächsten ist und freie Bahn hat.
     */
    public static void torpedo(ServerLevel level, BlockPos console, ServerPlayer player, boolean quantum) {
        String key = level.dimension().identifier() + "@" + console.asLong() + (quantum ? "q" : "t");
        long now = level.getGameTime();
        Long last = LAST_SHOT.get(key);
        if (last != null && now - last < (quantum ? 50 : 30)) {
            player.sendOverlayMessage(Component.translatable("message.simonspacemod.torpedo.loading"));
            return;
        }
        WeaponsData data = WeaponsData.get(level);
        WeaponsData.Battery b = data.battery(console);
        if (b == null) {
            linkPhasers(level, console);
            b = data.battery(console);
        }
        List<BlockPos> launchers = new ArrayList<>(quantum ? b.quantums() : b.torpedoes());
        launchers.removeIf(p -> !(level.getBlockState(p).getBlock() instanceof TorpedoLauncherBlock t) || t.quantum != quantum);
        if (launchers.isEmpty()) {
            player.sendOverlayMessage(Component.translatable(quantum ? "message.simonspacemod.torpedo.no_quantum" : "message.simonspacemod.torpedo.none"));
            return;
        }
        AABB own = ownShip(b, console, b.phasers().isEmpty() ? launchers : b.phasers());
        Aim aim = aim(level, player, own);
        Vec3 tgt = aim.point();
        launchers.sort(Comparator.comparingDouble(p -> Vec3.atCenterOf(p).distanceToSqr(tgt)));
        BlockPos from = launchers.stream().filter(p -> clear(level, p, tgt)).findFirst().orElse(launchers.get(0));
        LAST_SHOT.put(key, now);
        int tier = b.tier() > 0 ? b.tier() : quantum ? 7 : 3;
        TorpedoStyle style = quantum ? TorpedoStyle.QUANTUM : TorpedoStyle.photon(tier);
        Vec3 c = Vec3.atCenterOf(from);
        Vec3 start = c.add(tgt.subtract(c).normalize().scale(1.0));
        TorpedoEntity t = ModBlocks.TORPEDO.get().create(level, EntitySpawnReason.TRIGGERED);
        if (t == null) return;
        t.setPos(start.x, start.y, start.z);
        t.setup(style, tgt, aim.entity(), own, player.getUUID());
        level.addFreshEntity(t);
        level.playSound(null, from, SoundEvents.BLAZE_SHOOT, SoundSource.BLOCKS, 3.0F, quantum ? 0.6F : 0.8F);
        player.sendOverlayMessage(Component.translatable(quantum ? "message.simonspacemod.torpedo.quantum" : "message.simonspacemod.torpedo.photon"));
    }

    /** Explosionen lassen alles im Bereich des eigenen Schiffs heil. */
    public static ExplosionDamageCalculator spare(AABB own) {
        return new ExplosionDamageCalculator() {
            @Override
            public boolean shouldBlockExplode(Explosion explosion, BlockGetter level, BlockPos pos, BlockState state, float power) {
                return !own.contains(Vec3.atCenterOf(pos)) && super.shouldBlockExplode(explosion, level, pos, state, power);
            }
        };
    }

    /**
     * Bereich des eigenen Schiffs: bei gespawnten Schiffen der gespeicherte Bauraum, bei älteren
     * Spawns der Kasten um Phaser und Kameras, bei selbst gebauten Stationen um Phaser und Station.
     */
    private static AABB ownShip(WeaponsData.Battery b, BlockPos console, List<BlockPos> phasers) {
        if (b != null && b.hasShipBox())
            return new AABB(Vec3.atLowerCornerOf(b.shipMin()), Vec3.atLowerCornerOf(b.shipMax()).add(1, 1, 1));
        AABB box = new AABB(console);
        for (BlockPos p : phasers) box = box.minmax(new AABB(p));
        if (b != null && !b.cameras().isEmpty()) {
            for (BlockPos c : b.cameras()) box = box.minmax(new AABB(c));
            return box;
        }
        return box.inflate(6);
    }

    /** Selbst gebaute Station: Phaser im Umkreis suchen und speichern. */
    public static void linkPhasers(ServerLevel level, BlockPos console) {
        List<BlockPos> phasers = new ArrayList<>(), torps = new ArrayList<>(), quants = new ArrayList<>();
        for (BlockPos p : scan(level, console)) {
            var block = level.getBlockState(p).getBlock();
            if (block instanceof PhaserBlock) phasers.add(p);
            else if (block instanceof TorpedoLauncherBlock t) (t.quantum ? quants : torps).add(p);
        }
        WeaponsData.get(level).set(console, phasers, List.of(), List.of(), List.of(), 0, BlockPos.ZERO, BlockPos.ZERO, torps, quants);
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

    /** Waffenstärke aus der Phaserart, wenn die Station nicht von einem gespawnten Schiff stammt. */
    private static int tierOf(ServerLevel level, BlockPos phaser) {
        if (!(level.getBlockState(phaser).getBlock() instanceof PhaserBlock pb)) return 1;
        return switch (pb.kind) {
            case PHASE_CANNON -> 1;
            case PHASER_BANK -> 2;
            case PHASER_EMITTER -> 3;
            case PHASER_STRIP -> 6;
        };
    }

    private static void beam(ServerLevel level, ServerPlayer shooter, BlockPos from, Vec3 to, PhaserBlock.Kind kind, float damage, int power) {
        Vec3 c = Vec3.atCenterOf(from);
        Vec3 dir = to.subtract(c);
        double len = dir.length();
        dir = dir.normalize();
        Vec3 start = c.add(dir.scale(0.7));
        DustParticleOptions outer = new DustParticleOptions(kind.color, 1.2F + 0.4F * power); // stärker = dicker
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
            e.hurtServer(level, level.damageSources().indirectMagic(shooter, shooter), damage);
            e.igniteForSeconds(2 + power * 2);
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
                    var block = level.getBlockState(m).getBlock();
                    if (block instanceof PhaserBlock || block instanceof TorpedoLauncherBlock) found.add(m.immutable());
                }
        return found;
    }
}

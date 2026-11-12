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
import de.simon.spacemod.entity.PhaserBeamEntity;
import de.simon.spacemod.power.ShipPower;
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
    public static boolean fire(ServerLevel level, BlockPos console, ServerPlayer player, int power) {
        return fire(level, console, player, power, false);
    }

    /** @param quiet Nachladen ohne Meldung (automatisches Weiterfeuern bei gehaltenem Abzug) */
    public static boolean fire(ServerLevel level, BlockPos console, ServerPlayer player, int power, boolean quiet) {
        String key = level.dimension().identifier() + "@" + console.asLong();
        long now = level.getGameTime();
        Long last = LAST_SHOT.get(key);
        if (last != null && now - last < COOLDOWN_TICKS) {
            if (!quiet) player.sendOverlayMessage(Component.translatable("message.simonspacemod.phaser.recharging"));
            return true;
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
            return false;
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
            return false;
        }
        WeaponsData.Battery battery = data.battery(console);
        int tier = battery != null && battery.tier() > 0 ? battery.tier() : tierOf(level, chosen.get(0));
        // Strom: Phaser brauchen Energie aus dem Bordnetz (Warpkerne, Reaktoren …)
        ShipPower.Grid grid = ShipPower.gridFor(level, chosen.get(0));
        if (!grid.hasPower(level)) {
            player.sendOverlayMessage(Component.translatable("message.simonspacemod.phaser.no_power"));
            level.playSound(null, console, SoundEvents.DISPENSER_FAIL, SoundSource.BLOCKS, 0.6F, 0.6F);
            return false;
        }
        // Feuert 2 Sekunden; danach (und je nach Stufe etwas länger) laden die Phaser nach
        LAST_SHOT.put(key, now + BeamShots.DURATION - COOLDOWN_TICKS + (power - 1) * 10L);
        float damage = (6 + 2 * tier) * (power == 1 ? 1F : power == 2 ? 1.6F : 2.2F);
        float radius = power == 1 ? 1.0F : power == 2 ? 1.8F + 0.3F * tier : 2.5F + 0.6F * tier;
        List<Source> sources = new ArrayList<>();
        for (BlockPos p : chosen) sources.add(fixed(level, p));
        BeamShots.start(new ShipShot(level, player, sources, aim, own, grid, damage, radius, power, null));
        player.sendOverlayMessage(Component.translatable("message.simonspacemod.phaser.fire"));
        return true;
    }

    /** Strahlsound je Phaserart (NX-01 Pulse, Originalserie, Kinofilme, Next Generation). */
    private static net.minecraftforge.registries.RegistryObject<net.minecraft.sounds.SoundEvent> shipSound(PhaserBlock.Kind kind) {
        return switch (kind) {
            case PHASE_CANNON -> de.simon.spacemod.ModSounds.PHASER_SHIP_NX;
            case PHASER_BANK -> de.simon.spacemod.ModSounds.PHASER_SHIP_TOS;
            case PHASER_EMITTER -> de.simon.spacemod.ModSounds.PHASER_SHIP_REFIT;
            case PHASER_STRIP -> de.simon.spacemod.ModSounds.PHASER_SHIP_TNG;
        };
    }

    /** Strom je Tick und Phaser während des Feuerns (Stufe 1-3). */
    public static int fePerTick(int power) {
        return 1000 * power;
    }

    /** Laufender Schuss der Schiffsphaser: Strahlen folgen dem Fadenkreuz, Treffer alle 0,5 s, Explosion am Ende. */
    /** Ausgangspunkt eines Phaserstrahls: fester Block oder Phaser auf einem fliegenden Schiff (bewegt sich mit). */
    interface Source {
        /** Mitte des Phasers in der Welt oder null, wenn er nicht mehr da ist. */
        @Nullable Vec3 center();

        PhaserBlock.@Nullable Kind kind();
    }

    private static Source fixed(ServerLevel level, BlockPos p) {
        return new Source() {
            @Override public @Nullable Vec3 center() {
                return level.getBlockState(p).getBlock() instanceof PhaserBlock ? Vec3.atCenterOf(p) : null;
            }

            @Override public PhaserBlock.@Nullable Kind kind() {
                return level.getBlockState(p).getBlock() instanceof PhaserBlock pb ? pb.kind : null;
            }
        };
    }

    private static final class ShipShot extends BeamShots.Shot {
        private final ServerLevel level;
        private final ServerPlayer player;
        private final List<Source> phasers;
        private final @Nullable Entity ownShip;
        private final List<PhaserBeamEntity> beams = new ArrayList<>();
        private final AABB own;
        private final ShipPower.Grid grid;
        private final float damage, radius;
        private final int power;
        private Aim aim;

        ShipShot(ServerLevel level, ServerPlayer player, List<Source> phasers, Aim aim, AABB own, ShipPower.Grid grid,
                 float damage, float radius, int power, @Nullable Entity ownShip) {
            this.ownShip = ownShip;
            this.level = level;
            this.player = player;
            this.phasers = phasers;
            this.aim = aim;
            this.own = own;
            this.grid = grid;
            this.damage = damage;
            this.radius = radius;
            this.power = power;
        }

        @Override
        protected boolean tick() {
            if (age >= BeamShots.DURATION || !Triggers.held(player)) return false; // Loslassen bricht ab
            // Strom für alle feuernden Phaser
            int need = fePerTick(power) * phasers.size();
            ServerLevel powerLevel = ownShip != null && de.simon.spacemod.flight.Shipyard.level(level.getServer()) != null
                ? de.simon.spacemod.flight.Shipyard.level(level.getServer()) : level;
            if (ShipPower.draw(powerLevel, grid, need, ShipPower.Category.WEAPONS) < need) {
                if (player.isAlive()) player.sendOverlayMessage(Component.translatable("message.simonspacemod.phaser.no_power"));
                return false;
            }
            // dem Fadenkreuz folgen, solange der Spieler durch die Kamera schaut
            if (age > 0 && age % 2 == 0 && player.isAlive() && player.level() == level
                && (CameraControl.inSession(player) || ownShip != null && player.getVehicle() == ownShip))
                aim = aim(level, player, own);
            Vec3 tgt = aim.entity() != null && aim.entity().isAlive() ? aim.entity().getBoundingBox().getCenter() : aim.point();
            boolean shielded = false;
            for (int i = 0; i < phasers.size(); i++) {
                Vec3 c = phasers.get(i).center();
                PhaserBlock.Kind kind = phasers.get(i).kind();
                if (c == null || kind == null) continue;
                Vec3 start = c.add(tgt.subtract(c).normalize().scale(0.7));
                Vec3 end = tgt;
                // Schild im Weg: Strahl endet am Schild und schwächt es
                var hit = de.simon.spacemod.defense.Shields.intercept(level, start, tgt, c);
                if (hit != null) {
                    end = hit.point();
                    if (age % BeamShots.PULSE == 0) hit.damage(damage, 2.5);
                    shielded = true;
                }
                if (beams.size() <= i) {
                    PhaserBeamEntity b = BeamShots.beam(level, start, end, kind.color, kind.core, 0.12F + 0.06F * power, kind.pulse);
                    if (b != null) {
                        b.sound(de.simon.spacemod.ModSounds.index(shipSound(kind)), power == 1 ? 1.0F : power == 2 ? 0.95F : 0.9F, 2.5F + 0.5F * power);
                        beams.add(b);
                    }
                } else beams.get(i).aim(start, end);
                if (age % BeamShots.PULSE == 0) hurtAlong(start, end);
            }
            // Zielgebiet geladen halten, damit Treffer und Explosion auch weit draußen wirken
            if (age % 20 == 0)
                level.getChunkSource().addTicketWithRadius(TicketType.ENDER_PEARL, ChunkPos.containing(BlockPos.containing(tgt)), 2);
            // Blockschaden ab Stufe 2 (nur das eigene Schiff bleibt heil): zur Hälfte und am Ende
            if (!shielded && aim.hit() && (age == BeamShots.DURATION / 2 || age == BeamShots.DURATION - 1))
                level.explode(null, null, spare(own), tgt.x, tgt.y, tgt.z, radius * (age == BeamShots.DURATION - 1 ? 1F : 0.7F),
                    power == 3, power >= 2 ? Level.ExplosionInteraction.TNT : Level.ExplosionInteraction.NONE);
            age++;
            return true;
        }

        private void hurtAlong(Vec3 start, Vec3 to) {
            Vec3 dir = to.subtract(start).normalize();
            for (Entity e : level.getEntities(player, new AABB(start, to).inflate(1), e -> e instanceof LivingEntity le && le.isAlive()
                    && (ownShip == null || e.getVehicle() != ownShip))) {
                if (e.getBoundingBox().inflate(0.3).clip(start, to.add(dir.scale(0.5))).isEmpty()) continue;
                e.hurtServer(level, level.damageSources().indirectMagic(player, player), damage * 0.45F);
                e.igniteForSeconds(2 + power * 2);
            }
        }

        @Override
        protected void end() {
            for (PhaserBeamEntity b : beams) b.discard();
        }
    }

    // ---- Pilot am Steuer eines fliegenden Schiffs ----

    private static final Map<java.util.UUID, Integer> PILOT_POWER = new HashMap<>();

    /** Phaserstärke des Piloten (Mausrad am Steuer), Standard 2. */
    public static int pilotPower(ServerPlayer player) {
        return PILOT_POWER.getOrDefault(player.getUUID(), 2);
    }

    public static void pilotPower(ServerPlayer player, int delta) {
        int p = Math.max(1, Math.min(3, pilotPower(player) + delta));
        PILOT_POWER.put(player.getUUID(), p);
        player.sendOverlayMessage(Component.translatable("message.simonspacemod.phaser.power." + p));
    }

    /** Mitte eines Schiffsblocks (lokal) in der Welt. */
    private static Vec3 worldOf(de.simon.spacemod.entity.FlyingShipEntity ship, BlockPos local) {
        return de.simon.spacemod.flight.ShipPhysics.rotate(ship.rotation(1F), local.getX(), local.getY(), local.getZ()).add(ship.position());
    }

    /** Freie Schussbahn aus dem Schiff: die ersten Blöcke Richtung Ziel liegen nicht im eigenen Schiff. */
    private static boolean clearLocal(de.simon.spacemod.entity.FlyingShipEntity ship, BlockPos local, Vec3 target) {
        var q = ship.rotation(1F);
        Vec3 d = de.simon.spacemod.flight.ShipPhysics.inverse(q, target.subtract(worldOf(ship, local))).normalize();
        var col = ship.structure().collision();
        for (double t = 1.0; t <= 3.0; t += 0.5) {
            BlockPos b = BlockPos.containing(local.getX() + d.x * t + 0.5, local.getY() + d.y * t + 0.5, local.getZ() + d.z * t + 0.5);
            if (!b.equals(local) && col.containsKey(b)) return false;
        }
        return true;
    }

    /** Lage aller Blöcke einer Art im fliegenden Schiff (lokal). */
    private static List<BlockPos> shipBlocks(de.simon.spacemod.entity.FlyingShipEntity ship, java.util.function.Predicate<BlockState> test) {
        List<BlockPos> out = new ArrayList<>();
        for (var e : ship.structure().blocks) if (test.test(e.state())) out.add(e.pos());
        return out;
    }

    /** Pilot: Phaser des fliegenden Schiffs feuern 2 s lang auf das Fadenkreuz (Strom aus dem Bordnetz). */
    public static boolean fireFromShip(ServerPlayer player, de.simon.spacemod.entity.FlyingShipEntity ship, int power, boolean quiet) {
        if (!(ship.level() instanceof ServerLevel level) || ship.structure() == null || ship.plot() < 0) return false;
        ServerLevel sy = de.simon.spacemod.flight.Shipyard.level(level.getServer());
        if (sy == null) return false;
        String key = "ship#" + ship.getUUID();
        long now = level.getGameTime();
        Long last = LAST_SHOT.get(key);
        if (last != null && now - last < COOLDOWN_TICKS) {
            if (!quiet) player.sendOverlayMessage(Component.translatable("message.simonspacemod.phaser.recharging"));
            return true;
        }
        List<BlockPos> phasers = shipBlocks(ship, st -> st.getBlock() instanceof PhaserBlock);
        if (phasers.isEmpty()) {
            player.sendOverlayMessage(Component.translatable("message.simonspacemod.phaser.none"));
            return false;
        }
        AABB own = ship.getBoundingBox();
        Aim aim = aim(level, player, new AABB(0, -100000, 0, 0, -100000, 0));
        Vec3 tgt = aim.point();
        phasers.sort(Comparator.comparingDouble(p -> worldOf(ship, p).distanceToSqr(tgt)));
        List<BlockPos> chosen = new ArrayList<>();
        for (BlockPos p : phasers) {
            if (chosen.size() >= 2) break;
            if (!clearLocal(ship, p, tgt) || chosen.stream().anyMatch(c -> c.distSqr(p) < 36)) continue;
            chosen.add(p);
        }
        if (chosen.isEmpty()) {
            player.sendOverlayMessage(Component.translatable("message.simonspacemod.phaser.no_arc"));
            return false;
        }
        BlockPos origin = ship.plotOrigin();
        ShipPower.Grid grid = ShipPower.gridFor(sy, origin.offset(chosen.get(0)));
        if (!grid.hasPower(sy)) {
            player.sendOverlayMessage(Component.translatable("message.simonspacemod.phaser.no_power"));
            return false;
        }
        LAST_SHOT.put(key, now + BeamShots.DURATION - COOLDOWN_TICKS + (power - 1) * 10L);
        int tier = sy.getBlockState(origin.offset(chosen.get(0))).getBlock() instanceof PhaserBlock pb ? switch (pb.kind) {
            case PHASE_CANNON -> 1;
            case PHASER_BANK -> 2;
            case PHASER_EMITTER -> 3;
            case PHASER_STRIP -> 6;
        } : 2;
        float damage = (6 + 2 * tier) * (power == 1 ? 1F : power == 2 ? 1.6F : 2.2F);
        float radius = power == 1 ? 1.0F : power == 2 ? 1.8F + 0.3F * tier : 2.5F + 0.6F * tier;
        List<Source> sources = new ArrayList<>();
        for (BlockPos local : chosen) {
            BlockPos yardPos = origin.offset(local);
            sources.add(new Source() {
                @Override public @Nullable Vec3 center() {
                    return ship.isAlive() && sy.getBlockState(yardPos).getBlock() instanceof PhaserBlock ? worldOf(ship, local) : null;
                }

                @Override public PhaserBlock.@Nullable Kind kind() {
                    return sy.getBlockState(yardPos).getBlock() instanceof PhaserBlock pb ? pb.kind : null;
                }
            });
        }
        BeamShots.start(new ShipShot(level, player, sources, aim, own, grid, damage, radius, power, ship));
        if (!quiet) player.sendOverlayMessage(Component.translatable("message.simonspacemod.phaser.fire"));
        return true;
    }

    /** Pilot: Photonen- bzw. Quantentorpedo aus dem Werfer des fliegenden Schiffs, der dem Ziel am nächsten ist. */
    public static void torpedoFromShip(ServerPlayer player, de.simon.spacemod.entity.FlyingShipEntity ship, boolean quantum) {
        if (!(ship.level() instanceof ServerLevel level) || ship.structure() == null) return;
        String key = "ship#" + ship.getUUID() + (quantum ? "q" : "t");
        long now = level.getGameTime();
        Long last = LAST_SHOT.get(key);
        if (last != null && now - last < (quantum ? 50 : 30)) {
            player.sendOverlayMessage(Component.translatable("message.simonspacemod.torpedo.loading"));
            return;
        }
        List<BlockPos> launchers = shipBlocks(ship, st -> st.getBlock() instanceof TorpedoLauncherBlock t && t.quantum == quantum);
        if (launchers.isEmpty()) {
            player.sendOverlayMessage(Component.translatable(quantum ? "message.simonspacemod.torpedo.no_quantum" : "message.simonspacemod.torpedo.none"));
            return;
        }
        AABB own = ship.getBoundingBox();
        Aim aim = aim(level, player, new AABB(0, -100000, 0, 0, -100000, 0));
        Vec3 tgt = aim.point();
        launchers.sort(Comparator.comparingDouble(p -> worldOf(ship, p).distanceToSqr(tgt)));
        Vec3 c = worldOf(ship, launchers.get(0));
        Vec3 start = c.add(tgt.subtract(c).normalize().scale(1.0));
        LAST_SHOT.put(key, now);
        TorpedoEntity t = ModBlocks.TORPEDO.get().create(level, EntitySpawnReason.TRIGGERED);
        if (t == null) return;
        t.setPos(start.x, start.y, start.z);
        t.setup(quantum ? TorpedoStyle.QUANTUM : TorpedoStyle.photon(5), tgt, aim.entity(), own, player.getUUID());
        level.getChunkSource().addTicketWithRadius(TicketType.ENDER_PEARL, ChunkPos.containing(BlockPos.containing(start)), 2);
        level.addFreshEntity(t);
        level.playSound(null, BlockPos.containing(c), (quantum ? de.simon.spacemod.ModSounds.TORPEDO_QUANTUM : de.simon.spacemod.ModSounds.TORPEDO_PHOTON).get(),
            SoundSource.BLOCKS, 3.0F, 0.95F + level.getRandom().nextFloat() * 0.1F);
        player.sendOverlayMessage(Component.translatable(quantum ? "message.simonspacemod.torpedo.quantum" : "message.simonspacemod.torpedo.photon"));
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
        // fliegende Schiffe (ihre Blöcke stehen nicht in der Welt) – trifft man ihre Hülle, zählt das
        var shipHit = de.simon.spacemod.flight.Shipyard.raycastShips(level, eye, look, Math.min(blockDist, BLOCK_RANGE));
        if (shipHit != null && shipHit.distance() < blockDist && !(player.getVehicle() == shipHit.ship())) {
            target = shipHit.point();
            blockDist = shipHit.distance();
            hit = true;
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
     * Ziel am nächsten ist.
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
        // Torpedos manövrieren und fliegen durch das eigene Schiff hindurch: immer der Werfer, der dem Ziel am nächsten ist
        BlockPos from = launchers.get(0);
        LAST_SHOT.put(key, now);
        int tier = b.tier() > 0 ? b.tier() : quantum ? 7 : 3;
        TorpedoStyle style = quantum ? TorpedoStyle.QUANTUM : TorpedoStyle.photon(tier);
        Vec3 c = Vec3.atCenterOf(from);
        Vec3 start = c.add(tgt.subtract(c).normalize().scale(1.0));
        TorpedoEntity t = ModBlocks.TORPEDO.get().create(level, EntitySpawnReason.TRIGGERED);
        if (t == null) return;
        t.setPos(start.x, start.y, start.z);
        t.setup(style, tgt, aim.entity(), own, player.getUUID());
        // Der Werfer kann weit von der Kamera weg in einem ruhenden Chunk liegen: vorher aktiv laden,
        // sonst bewegt sich der Torpedo nicht (er sichert sich danach im Flug selbst weiter)
        var chunks = level.getChunkSource();
        chunks.addTicketWithRadius(TicketType.ENDER_PEARL, ChunkPos.containing(BlockPos.containing(start)), 2);
        chunks.addTicketWithRadius(TicketType.ENDER_PEARL,
            ChunkPos.containing(BlockPos.containing(start.add(tgt.subtract(start).normalize().scale(64)))), 2);
        level.addFreshEntity(t);
        level.playSound(null, from, (quantum ? de.simon.spacemod.ModSounds.TORPEDO_QUANTUM : de.simon.spacemod.ModSounds.TORPEDO_PHOTON).get(),
            SoundSource.BLOCKS, 3.0F, 0.95F + level.getRandom().nextFloat() * 0.1F);
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

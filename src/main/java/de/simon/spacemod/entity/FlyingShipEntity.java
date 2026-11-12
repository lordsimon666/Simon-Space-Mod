package de.simon.spacemod.entity;

import de.simon.spacemod.flight.ShipPhysics;
import de.simon.spacemod.flight.ShipStructure;
import de.simon.spacemod.flight.ShipStructureMessage;
import de.simon.spacemod.turbolift.TurboliftNetwork;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.PacketDistributor;
import org.joml.Quaternionf;
import org.jspecify.annotations.Nullable;

/**
 * Fliegendes Schiff: die mit der Steuerkonsole erfassten Blöcke als bewegliches Objekt. Dreht sich
 * mit Trägheit zum Fadenkreuz des Piloten, Impulsantriebe geben Schub (Strom von den Warpkernen an
 * Bord), Masse = Anzahl Blöcke. Beim Landen wird es wieder zu Blöcken (Drehung rastet auf 90° ein).
 */
public class FlyingShipEntity extends Entity {
    private static final EntityDataAccessor<Float> YAW = SynchedEntityData.defineId(FlyingShipEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> PITCH = SynchedEntityData.defineId(FlyingShipEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> YAW0 = SynchedEntityData.defineId(FlyingShipEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> RADIUS = SynchedEntityData.defineId(FlyingShipEntity.class, EntityDataSerializers.FLOAT);
    /** Entity-ID des Piloten (-1 = niemand am Steuer). */
    private static final EntityDataAccessor<Integer> PILOT_ID = SynchedEntityData.defineId(FlyingShipEntity.class, EntityDataSerializers.INT);

    private @Nullable ShipStructure structure;
    private Vec3 velocity = Vec3.ZERO;
    private double yawRate, pitchRate;
    private @Nullable UUID pilot;
    // letzte Steuerbefehle des Piloten
    private float targetYaw, targetPitch;
    private int throttle; // -1, 0, 1
    private int controlAge = 100;
    private double lastPower = 1;
    /** Sitzplätze der Passagiere (lokal, relativ zum Drehpunkt). */
    private final Map<UUID, Vec3> seats = new HashMap<>();
    /** Server: lebende Blockdaten (Kisten, Reaktoren …) nach Index in structure.blocks. */
    private final Map<Integer, BlockEntity> live = new HashMap<>();
    private boolean liveLoaded;
    /** Client: vorherige Lage für flüssiges Zeichnen. */
    public float prevYaw, prevPitch;

    public FlyingShipEntity(EntityType<?> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    // ---- Daten ----

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(YAW, 0F).define(PITCH, 0F).define(YAW0, 0F).define(RADIUS, 1F).define(PILOT_ID, -1);
    }

    public float yaw() { return entityData.get(YAW); }
    public float pitch() { return entityData.get(PITCH); }
    public float yaw0() { return entityData.get(YAW0); }
    public @Nullable ShipStructure structure() { return structure; }

    public Quaternionf rotation(float partial) {
        float y = prevYaw + (float) ShipPhysics.wrap(yaw() - prevYaw) * partial;
        float p = prevPitch + (pitch() - prevPitch) * partial;
        return ShipPhysics.rotation(yaw0(), y, p);
    }

    public void setup(ShipStructure structure, float yaw0, @Nullable UUID pilot) {
        this.structure = structure;
        this.pilot = pilot;
        entityData.set(YAW0, yaw0);
        entityData.set(YAW, yaw0);
        entityData.set(PITCH, 0F);
        entityData.set(RADIUS, (float) structure.radius);
        targetYaw = yaw0;
        prevYaw = yaw0;
    }

    /** Client: Struktur vom Server erhalten. */
    public void setClientStructure(ShipStructure s) {
        this.structure = s;
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        return false;
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        List<ShipStructure.Entry> list = input.read("blocks", ShipStructure.LIST_CODEC).orElse(List.of());
        structure = list.isEmpty() ? null : new ShipStructure(list);
        live.clear();
        liveLoaded = false;
        entityData.set(YAW0, input.getFloatOr("yaw0", 0F));
        entityData.set(YAW, input.getFloatOr("yaw", 0F));
        entityData.set(PITCH, input.getFloatOr("pitch", 0F));
        if (structure != null) entityData.set(RADIUS, (float) structure.radius);
        velocity = new Vec3(input.getDoubleOr("vx", 0), input.getDoubleOr("vy", 0), input.getDoubleOr("vz", 0));
        targetYaw = yaw();
        targetPitch = pitch();
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        if (structure != null) output.store("blocks", ShipStructure.LIST_CODEC, currentBlocks());
        output.putFloat("yaw0", yaw0());
        output.putFloat("yaw", yaw());
        output.putFloat("pitch", pitch());
        output.putDouble("vx", velocity.x);
        output.putDouble("vy", velocity.y);
        output.putDouble("vz", velocity.z);
    }

    @Override
    public void startSeenByPlayer(ServerPlayer player) {
        super.startSeenByPlayer(player);
        if (structure != null) TurboliftNetwork.CHANNEL.send(ShipStructureMessage.of(getId(), structure), PacketDistributor.PLAYER.with(player));
    }

    // ---- Passagiere ----

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return true;
    }

    @Override
    protected void addPassenger(Entity passenger) {
        super.addPassenger(passenger);
        // Sitzplatz = aktuelle Lage relativ zum Schiff (beide Seiten rechnen gleich)
        Vec3 rel = passenger.position().subtract(position());
        seats.put(passenger.getUUID(), ShipPhysics.inverse(rotation(1F), rel));
    }

    @Override
    protected void removePassenger(Entity passenger) {
        super.removePassenger(passenger);
        seats.remove(passenger.getUUID());
        if (passenger.getUUID().equals(pilot)) setPilotNone();
    }

    /** Mitfahrer stehen (man läuft im Schiff herum), statt zu sitzen. */
    @Override
    public boolean shouldRiderSit() {
        return false;
    }

    /** Sitzplatz bzw. Standort eines Mitfahrers (lokal, relativ zum Drehpunkt). */
    public Vec3 seat(Entity passenger) {
        return seats.getOrDefault(passenger.getUUID(), Vec3.ZERO);
    }

    public void setSeat(Entity passenger, Vec3 local) {
        if (hasPassenger(passenger)) seats.put(passenger.getUUID(), local);
    }

    /** Lage der Steuerkonsole im Schiff (lokal) oder null. */
    public @Nullable BlockPos helmLocal() {
        if (structure == null) return null;
        for (ShipStructure.Entry e : structure.blocks)
            if (e.state().getBlock() instanceof de.simon.spacemod.block.ShipHelmBlock) return e.pos();
        return null;
    }

    public boolean isPilotEntity(Entity e) {
        return entityData.get(PILOT_ID) == e.getId();
    }

    private void setPilotNone() {
        pilot = null;
        entityData.set(PILOT_ID, -1);
    }

    /** Steuer übernehmen (nahe der Konsole) bzw. abgeben. */
    public void toggleHelm(ServerPlayer player) {
        if (isPilot(player)) {
            setPilotNone();
            player.sendOverlayMessage(Component.translatable("message.simonspacemod.ship.helm_off"));
            return;
        }
        BlockPos helm = helmLocal();
        if (pilot != null || helm == null) {
            player.sendOverlayMessage(Component.translatable("message.simonspacemod.ship.helm_taken"));
            return;
        }
        Vec3 here = seat(player);
        if (here.distanceTo(Vec3.atCenterOf(helm).subtract(0.5, 0.5, 0.5)) > 3.5) {
            player.sendOverlayMessage(Component.translatable("message.simonspacemod.ship.helm_far"));
            return;
        }
        setPilot(player);
        player.sendOverlayMessage(Component.translatable("message.simonspacemod.ship.helm_on"));
    }

    @Override
    protected void positionRider(Entity passenger, Entity.MoveFunction move) {
        Vec3 seat = seats.getOrDefault(passenger.getUUID(), Vec3.ZERO);
        Vec3 w = ShipPhysics.rotate(rotation(1F), seat.x, seat.y, seat.z).add(position());
        move.accept(passenger, w.x, w.y, w.z);
    }

    @Override
    public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
        return passenger.position();
    }

    /** Rechtsklick auf das fliegende Schiff: einsteigen (ohne Pilot wird man Pilot). */
    @Override
    public boolean isPickable() {
        return structure != null;
    }

    @Override
    public boolean canBeHitByProjectile() {
        return false;
    }

    @Override
    public boolean skipAttackInteraction(Entity source) {
        return true;
    }

    @Override
    public net.minecraft.world.InteractionResult interact(Player player, net.minecraft.world.InteractionHand hand, Vec3 location) {
        if (player.isPassenger()) return net.minecraft.world.InteractionResult.PASS;
        if (!level().isClientSide()) {
            player.startRiding(this, true, false);
        }
        return net.minecraft.world.InteractionResult.SUCCESS;
    }

    public boolean isPilot(Player player) {
        return player.getUUID().equals(pilot);
    }

    public void setPilot(Player player) {
        pilot = player.getUUID();
        entityData.set(PILOT_ID, player.getId());
    }

    /** Steuerbefehle des Piloten (Fadenkreuz-Richtung, Schub vor/zurück). */
    public void control(Player player, float yaw, float pitch, int throttle) {
        if (!isPilot(player)) return;
        targetYaw = yaw;
        targetPitch = Math.max(-80, Math.min(80, pitch));
        this.throttle = Integer.signum(throttle);
        controlAge = 0;
    }

    // ---- Blöcke im Flug ----

    /** Blockdaten (Kisten, Reaktor …) als lebende Objekte laden, damit sie im Flug benutzbar sind. */
    private void loadLive() {
        if (liveLoaded || structure == null || level().isClientSide()) return;
        liveLoaded = true;
        for (int i = 0; i < structure.blocks.size(); i++) {
            ShipStructure.Entry e = structure.blocks.get(i);
            if (e.data().isEmpty()) continue;
            try {
                BlockEntity be = BlockEntity.loadStatic(e.pos(), e.state(), e.data().get(), level().registryAccess());
                if (be != null) live.put(i, be);
            } catch (RuntimeException ex) {
                // Block anderer Mods, der ohne Welt nicht geladen werden kann: Daten bleiben unverändert
            }
        }
    }

    /** Lebendes Objekt des Blocks an einer lokalen Stelle (Server) oder null. */
    public @Nullable BlockEntity liveAt(BlockPos local) {
        if (structure == null) return null;
        loadLive();
        int i = structure.indexOf(local);
        return i < 0 ? null : live.get(i);
    }

    /** Blöcke mit dem aktuellen Stand der Blockdaten (zum Speichern und Landen). */
    private List<ShipStructure.Entry> currentBlocks() {
        if (live.isEmpty()) return structure.blocks;
        List<ShipStructure.Entry> out = new ArrayList<>(structure.blocks);
        live.forEach((i, be) -> {
            ShipStructure.Entry e = out.get(i);
            try {
                out.set(i, new ShipStructure.Entry(e.pos(), e.state(), Optional.of(be.saveWithFullMetadata(level().registryAccess()))));
            } catch (RuntimeException ex) {
                // alte Daten behalten
            }
        });
        return out;
    }

    /** Reaktoren laufen im Flug weiter; liefert den gespeicherten Strom aller Erzeuger an Bord. */
    private long tickGenerators() {
        loadLive();
        long stored = 0;
        for (BlockEntity be : live.values()) {
            if (be instanceof de.simon.spacemod.block.entity.ReactorControllerBlockEntity r) r.run();
            stored += be.getCapability(net.minecraftforge.common.capabilities.ForgeCapabilities.ENERGY, null)
                .map(e -> e.canExtract() ? e.extractEnergy(Integer.MAX_VALUE, true) : 0).orElse(0);
        }
        return stored;
    }

    /** Strom aus den Speichern an Bord entnehmen (Reaktoren, Generatoren anderer Mods). */
    private void drainGenerators(long amount) {
        for (BlockEntity be : live.values()) {
            if (amount <= 0) return;
            long a = amount;
            amount -= be.getCapability(net.minecraftforge.common.capabilities.ForgeCapabilities.ENERGY, null)
                .map(e -> e.canExtract() ? e.extractEnergy((int) Math.min(Integer.MAX_VALUE, a), false) : 0).orElse(0);
        }
    }

    // ---- Flug ----

    @Override
    public void tick() {
        prevYaw = yaw();
        prevPitch = pitch();
        super.tick();
        if (level().isClientSide()) {
            if (structure == null) {
                ShipStructure s = de.simon.spacemod.flight.ShipClientData.take(getId());
                if (s != null) structure = s;
            }
            updateBox();
            return;
        }
        ServerLevel level = (ServerLevel) level();
        if (structure == null) { discard(); return; }
        if (++controlAge > 10 || pilot == null) throttle = 0;

        // Strom: Antriebe brauchen Energie, wenn sie schieben oder drehen
        double turnDemand = pilot == null ? 0 : Math.min(1, (Math.abs(ShipPhysics.wrap(targetYaw - yaw())) + Math.abs(targetPitch - pitch())) / 30);
        double demand = structure.engines * ShipPhysics.RF_PER_ENGINE * Math.max(Math.abs(throttle), turnDemand);
        long stored = tickGenerators();
        double available = structure.powerPerTick + stored;
        double power = demand <= 0 ? 1 : Math.min(1, available / demand);
        lastPower = available > 0 ? power : 0;
        // was die Warpkerne nicht schaffen, kommt aus Reaktoren/Speichern
        if (demand > 0) drainGenerators((long) Math.ceil(demand * power) - structure.powerPerTick);

        // Drehung zum Fadenkreuz (mit Trägheit)
        double alpha = ShipPhysics.angularAcceleration(structure.engines, structure.inertia, lastPower);
        double ty = pilot == null ? yaw() : targetYaw, tp = pilot == null ? pitch() : targetPitch;
        double[] ry = ShipPhysics.turn(yaw(), yawRate, ty, alpha);
        double[] rp = ShipPhysics.turn(pitch(), pitchRate, tp, alpha);
        float newYaw = (float) ry[0], newPitch = (float) Math.max(-80, Math.min(80, rp[0]));

        // Schub entlang des Bugs, sonst Trägheitsdämpfer
        double accel = ShipPhysics.acceleration(structure.engines, structure.mass(), lastPower);
        Vec3 v = velocity;
        if (throttle != 0) v = v.add(ShipPhysics.forward(newYaw, newPitch).scale(throttle * accel));
        else v = v.scale(ShipPhysics.DAMPING);
        if (v.length() > ShipPhysics.MAX_SPEED) v = v.normalize().scale(ShipPhysics.MAX_SPEED);
        if (v.lengthSqr() < 1e-6) v = Vec3.ZERO;

        // Kollision: Außenblöcke an der neuen Lage dürfen nicht in Welt-Blöcken stecken
        Vec3 newPos = position().add(v);
        Quaternionf q = ShipPhysics.rotation(yaw0(), newYaw, newPitch);
        if (collides(level, newPos, q)) {
            // erst nur die Drehung versuchen, sonst ganz stehen bleiben
            if (!collides(level, position(), q)) newPos = position();
            else { newPos = position(); newYaw = yaw(); newPitch = pitch(); ry[1] = 0; rp[1] = 0; }
            v = Vec3.ZERO;
        }
        velocity = v;
        yawRate = ry[1];
        pitchRate = rp[1];
        entityData.set(YAW, newYaw);
        entityData.set(PITCH, newPitch);
        setPos(newPos);
        updateBox();
        keepLoaded(level);

        if (tickCount % 10 == 0 && pilot != null && level.getPlayerByUUID(pilot) instanceof ServerPlayer p)
            p.sendOverlayMessage(Component.translatable("message.simonspacemod.ship.hud",
                String.format("%.1f", velocity.length() * 20), throttle > 0 ? ">" : throttle < 0 ? "<" : "–",
                (int) Math.round(lastPower * 100), structure.engines, structure.blocks.size()));
    }

    private void updateBox() {
        float r = entityData.get(RADIUS);
        setBoundingBox(new AABB(position(), position()).inflate(r));
    }

    private boolean collides(ServerLevel level, Vec3 pos, Quaternionf q) {
        for (ShipStructure.Entry e : structure.surface) {
            Vec3 w = ShipPhysics.rotate(q, e.pos().getX(), e.pos().getY(), e.pos().getZ()).add(pos);
            BlockPos bp = BlockPos.containing(w);
            if (!level.isLoaded(bp)) return true;
            BlockState st = level.getBlockState(bp);
            if (!st.isAir() && !st.getCollisionShape(level, bp).isEmpty()) return true;
        }
        return false;
    }

    private void keepLoaded(ServerLevel level) {
        if (tickCount % 20 != 0) return;
        Vec3 ahead = position().add(velocity.scale(40));
        for (Vec3 p : new Vec3[] {position(), ahead})
            level.getChunkSource().addTicketWithRadius(net.minecraft.server.level.TicketType.ENDER_PEARL,
                net.minecraft.world.level.ChunkPos.containing(BlockPos.containing(p)), 2);
    }

    // ---- Landen ----

    /** Wieder zu Blöcken werden: Drehung rastet auf 90° ein, nur langsam und mit freiem Platz. */
    public void land(ServerPlayer by) {
        if (structure == null) return;
        if (!isPilot(by)) { by.sendOverlayMessage(Component.translatable("message.simonspacemod.ship.helm_needed")); return; }
        if (velocity.length() > 0.15) { by.sendOverlayMessage(Component.translatable("message.simonspacemod.ship.too_fast")); return; }
        if (Math.abs(pitch()) > 20) { by.sendOverlayMessage(Component.translatable("message.simonspacemod.ship.level_first")); return; }
        ServerLevel level = (ServerLevel) level();
        int steps = Math.floorMod(Math.round((float) ShipPhysics.wrap(yaw() - yaw0()) / 90F), 4);
        Rotation rot = switch (steps) { case 1 -> Rotation.CLOCKWISE_90; case 2 -> Rotation.CLOCKWISE_180; case 3 -> Rotation.COUNTERCLOCKWISE_90; default -> Rotation.NONE; };
        BlockPos pivot = BlockPos.containing(position());
        List<BlockPos> targets = new ArrayList<>();
        List<ShipStructure.Entry> blocks = currentBlocks();
        for (ShipStructure.Entry e : blocks) {
            BlockPos t = pivot.offset(e.pos().rotate(rot));
            BlockState here = level.getBlockState(t);
            if (!here.isAir() && !here.canBeReplaced()) {
                by.sendOverlayMessage(Component.translatable("message.simonspacemod.ship.no_room"));
                return;
            }
            targets.add(t);
        }
        // Passagiere an ihre Plätze im gelandeten Schiff
        Quaternionf q = ShipPhysics.rotation(yaw0(), yaw0() + steps * 90F, 0F);
        Map<Entity, Vec3> riders = new HashMap<>();
        for (Entity p : getPassengers()) {
            Vec3 seat = seats.getOrDefault(p.getUUID(), Vec3.ZERO);
            riders.put(p, ShipPhysics.rotate(q, seat.x, seat.y, seat.z).add(Vec3.atCenterOf(pivot)));
        }
        ejectPassengers();
        int flags = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
        for (int pass = 0; pass < 2; pass++)
            for (int i = 0; i < blocks.size(); i++) {
                ShipStructure.Entry e = blocks.get(i);
                boolean full = e.state().isCollisionShapeFullBlock(level, targets.get(i));
                if ((pass == 0) != full) continue; // erst volle Blöcke, dann Anbauteile
                BlockState st = e.state().rotate(rot);
                level.setBlock(targets.get(i), st, flags);
                Optional<net.minecraft.nbt.CompoundTag> data = e.data();
                if (data.isPresent()) {
                    BlockEntity be = BlockEntity.loadStatic(targets.get(i), st, data.get(), level.registryAccess());
                    if (be != null) level.setBlockEntity(be);
                }
            }
        riders.forEach((p, w) -> p.teleportTo(w.x, w.y + 0.05, w.z));
        discard();
    }
}

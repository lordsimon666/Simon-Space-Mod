package de.simon.spacemod.entity;

import de.simon.spacemod.flight.ShipPhysics;
import de.simon.spacemod.flight.ShipStructure;
import de.simon.spacemod.flight.ShipStructureMessage;
import de.simon.spacemod.flight.Shipyard;
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
import de.simon.spacemod.space.SpaceTravel;
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
    /** Geschwindigkeit (Blöcke/Tick) und Drehraten (Grad/Tick) für die Vorhersage auf dem Client. */
    private static final EntityDataAccessor<org.joml.Vector3fc> VEL = SynchedEntityData.defineId(FlyingShipEntity.class, EntityDataSerializers.VECTOR3);
    private static final EntityDataAccessor<Float> YAW_RATE = SynchedEntityData.defineId(FlyingShipEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> PITCH_RATE = SynchedEntityData.defineId(FlyingShipEntity.class, EntityDataSerializers.FLOAT);
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
    private int strafe;   // -1, 0, 1 (links/rechts schieben, relativ zum Schiff)
    private int vertical; // -1, 0, 1 (hoch/runter schieben, relativ zum Schiff)
    /** Trägheitsdämpfer: eingestellte und tatsächlich laufende Stufe (0..10), Lage der Steuerungen im Schiff. */
    private int damperLevel = de.simon.spacemod.flight.ShipPhysics.DEFAULT_DAMPER;
    private java.util.List<BlockPos> damperBlocks;
    /**
     * Warp: Stufe (0 = nicht im Warp), echte Lage in der Herkunftsdimension, Autopilot-Ziel und -Dimension,
     * aktuelle Kosten (FE/t).
     */
    private double warp, rx, ry, rz, tx, ty, tz;
    private boolean autopilot;
    private String warpOrigin = "", warpDest = "";
    private int warpCost;
    private int controlAge = 100;
    private double lastPower = 1;
    /** Letzte Flugrichtung (für den Rückstoß bei Kollisionen). */
    private Vec3 lastDir = Vec3.ZERO;
    /** Sitzplätze der Passagiere (lokal, relativ zum Drehpunkt). */
    private final Map<UUID, Vec3> seats = new HashMap<>();
    /** Ursprung des Bauplatzes in der Werft (Block p des Schiffs steht bei Ursprung + p). */
    private static final EntityDataAccessor<BlockPos> PLOT_ORIGIN = SynchedEntityData.defineId(FlyingShipEntity.class, EntityDataSerializers.BLOCK_POS);
    private int plot = -1;
    private int scanCursor;
    private @Nullable BlockPos helmCache;
    /** Nach einem Dimensionswechsel: genaue Plätze der Mitfahrer, die noch an die Clients gehen. */
    private Map<UUID, Vec3> restoreSeats;
    private int restoreAt;

    /** Plätze nach einem Dimensionswechsel wiederherstellen (sofort auf dem Server, kurz danach an die Clients). */
    public void restoreSeats(Map<UUID, Vec3> saved) {
        restoreSeats = new HashMap<>(saved);
        restoreAt = tickCount + 10;
        for (Entity p : getPassengers()) if (saved.containsKey(p.getUUID())) seats.put(p.getUUID(), saved.get(p.getUUID()));
    }

    public Map<UUID, Vec3> seatsCopy() {
        return new HashMap<>(seats);
    }

    /** Wer gerade ausgestiegen ist, wird nicht sofort wieder eingeladen. */
    private final Map<UUID, Long> boardCooldown = new HashMap<>();
    /** Client: vorherige Lage für flüssiges Zeichnen. */
    public float prevYaw, prevPitch;

    public FlyingShipEntity(EntityType<?> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    // ---- Daten ----

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(VEL, new org.joml.Vector3f()).define(YAW_RATE, 0F).define(PITCH_RATE, 0F).define(YAW, 0F).define(PITCH, 0F).define(YAW0, 0F).define(RADIUS, 1F).define(PILOT_ID, -1).define(PLOT_ORIGIN, BlockPos.ZERO);
    }

    public float yaw() { return entityData.get(YAW); }
    public float pitch() { return entityData.get(PITCH); }
    public float yaw0() { return entityData.get(YAW0); }
    public @Nullable ShipStructure structure() { return structure; }

    public Quaternionf rotation(float partial) {
        // Client: Lage nur im Takt der Ticks übernehmen (wie die Position), sonst ruckelt das Schiff
        float cy = level().isClientSide() ? curYaw : yaw(), cp = level().isClientSide() ? curPitch : pitch();
        float y = prevYaw + (float) ShipPhysics.wrap(cy - prevYaw) * partial;
        float p = prevPitch + (cp - prevPitch) * partial;
        return ShipPhysics.rotation(yaw0(), y, p);
    }

    /**
     * Nur die Neigung des Schiffs (Nicken um die Querachse) als Drehung in Weltkoordinaten – damit
     * kippen Blick und Spielermodell der Leute an Bord mit, als stünden sie auf dem Schiffsboden.
     */
    public Quaternionf tilt(float partial) {
        float cy = level().isClientSide() ? curYaw : yaw(), cp = level().isClientSide() ? curPitch : pitch();
        float y = prevYaw + (float) ShipPhysics.wrap(cy - prevYaw) * partial;
        float p = prevPitch + (cp - prevPitch) * partial;
        return new Quaternionf().rotateY((float) Math.toRadians(-y)).rotateX((float) Math.toRadians(p)).rotateY((float) Math.toRadians(y));
    }

    /** Gierwinkel, wie er gerade gezeichnet wird (Client: vorhergesagt). */
    public float renderYaw() {
        return level().isClientSide() ? curYaw : yaw();
    }

    /** Client: Drehung des Schiffs um die Hochachse in diesem Tick (Grad). */
    public float yawStep() {
        return (float) ShipPhysics.wrap(curYaw - prevYaw);
    }

    /**
     * Schiff, mit dem sich Blick und Modell dieses Wesens mitdrehen: an Bord (laufend oder auf einem
     * Stuhl), aber nicht am Steuer – dort steuert der Blick das Schiff.
     */
    public static @Nullable FlyingShipEntity frameOf(Entity e) {
        FlyingShipEntity s = bodyFrameOf(e);
        return s != null && !s.isPilotEntity(e) ? s : null;
    }

    /**
     * Schiff, auf dessen Boden dieses Wesen steht – auch am Steuer. Figur und Augenhöhe kippen mit dem
     * Schiffsboden, damit man sich beim Nicken nicht relativ zum Schiff vor- und zurücklehnt.
     */
    public static @Nullable FlyingShipEntity bodyFrameOf(Entity e) {
        Entity v = e.getVehicle();
        if (v instanceof SeatEntity) v = v.getVehicle();
        return v instanceof FlyingShipEntity s ? s : null;
    }

    /** Client: weiche Bewegung – neue Positionen vom Server werden im nächsten Tick übernommen. */
    private final net.minecraft.world.entity.InterpolationHandler interpolation = new net.minecraft.world.entity.InterpolationHandler(this, 1);
    private float curYaw, curPitch;
    private boolean curInit;

    @Override
    public net.minecraft.world.entity.InterpolationHandler getInterpolation() {
        return interpolation;
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
    public void remove(Entity.RemovalReason reason) {
        super.remove(reason);
        if (plot >= 0) Shipyard.unregister(plot, this);
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        return false;
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        List<ShipStructure.Entry> list = input.read("blocks", ShipStructure.LIST_CODEC).orElse(List.of());
        structure = list.isEmpty() ? null : new ShipStructure(list);
        plot = input.getIntOr("plot", -1);
        if (plot >= 0) entityData.set(PLOT_ORIGIN, Shipyard.origin(plot));
        entityData.set(YAW0, input.getFloatOr("yaw0", 0F));
        entityData.set(YAW, input.getFloatOr("yaw", 0F));
        entityData.set(PITCH, input.getFloatOr("pitch", 0F));
        if (structure != null) entityData.set(RADIUS, (float) structure.radius);
        velocity = new Vec3(input.getDoubleOr("vx", 0), input.getDoubleOr("vy", 0), input.getDoubleOr("vz", 0));
        targetYaw = yaw();
        targetPitch = pitch();
        warp = input.getDoubleOr("warp", 0);
        rx = input.getDoubleOr("wrx", 0); ry = input.getDoubleOr("wry", 0); rz = input.getDoubleOr("wrz", 0);
        tx = input.getDoubleOr("wtx", 0); ty = input.getDoubleOr("wty", 0); tz = input.getDoubleOr("wtz", 0);
        autopilot = input.getBooleanOr("wauto", false);
        warpOrigin = input.getStringOr("worigin", "");
        warpDest = input.getStringOr("wdest", "");
        warpCost = de.simon.spacemod.space.WarpDrive.cost(warp);
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        if (structure != null) output.store("blocks", ShipStructure.LIST_CODEC, structure.blocks);
        output.putInt("plot", plot);
        output.putFloat("yaw0", yaw0());
        output.putFloat("yaw", yaw());
        output.putFloat("pitch", pitch());
        output.putDouble("vx", velocity.x);
        output.putDouble("vy", velocity.y);
        output.putDouble("vz", velocity.z);
        if (warp > 0) {
            output.putDouble("warp", warp);
            output.putDouble("wrx", rx); output.putDouble("wry", ry); output.putDouble("wrz", rz);
            output.putDouble("wtx", tx); output.putDouble("wty", ty); output.putDouble("wtz", tz);
            output.putBoolean("wauto", autopilot);
            output.putString("worigin", warpOrigin);
            output.putString("wdest", warpDest);
        }
    }

    @Override
    public void startSeenByPlayer(ServerPlayer player) {
        super.startSeenByPlayer(player);
        if (structure != null) TurboliftNetwork.CHANNEL.send(ShipStructureMessage.of(getId(), structure), PacketDistributor.PLAYER.with(player));
        var all = blockEntityData(true);
        if (!all.isEmpty()) TurboliftNetwork.CHANNEL.send(new de.simon.spacemod.flight.ShipBlockEntityMessage(getId(), all), PacketDistributor.PLAYER.with(player));
    }

    // ---- Blockdaten für Renderer (Waffenständer, Truhen …) ----

    /** Server: zuletzt gesendete Anzeigedaten je Block (Hash), damit nur Änderungen rausgehen. */
    private final Map<BlockPos, Integer> sentBlockData = new HashMap<>();
    /** Client: Anzeigedaten je Block und daraus gebaute Blockdaten zum Zeichnen. */
    private final Map<BlockPos, net.minecraft.nbt.CompoundTag> clientBlockData = new HashMap<>();
    private final Map<BlockPos, BlockEntity> clientBlockEntities = new HashMap<>();

    public Map<BlockPos, net.minecraft.nbt.CompoundTag> clientBlockData() {
        return clientBlockData;
    }

    private List<de.simon.spacemod.flight.ShipBlockEntityMessage.Entry> blockEntityData(boolean all) {
        List<de.simon.spacemod.flight.ShipBlockEntityMessage.Entry> out = new ArrayList<>();
        if (structure == null || plot < 0) return out;
        for (ShipStructure.Entry e : structure.blocks) {
            if (!e.state().hasBlockEntity()) continue;
            BlockEntity be = liveAt(e.pos());
            if (be == null) continue;
            net.minecraft.nbt.CompoundTag tag;
            try { tag = be.getUpdateTag(level().registryAccess()); } catch (RuntimeException ex) { continue; }
            if (tag.isEmpty()) continue;
            int h = tag.hashCode();
            Integer old = sentBlockData.put(e.pos(), h);
            if (all || old == null || old != h) out.add(new de.simon.spacemod.flight.ShipBlockEntityMessage.Entry(e.pos().asLong(), tag));
        }
        return out;
    }

    /** Client: Blockdaten eines Schiffsblocks zum Zeichnen (null = Block hat keine). */
    public @Nullable BlockEntity clientBlockEntity(ShipStructure.Entry e) {
        BlockState st = e.state();
        if (!st.hasBlockEntity() || !(st.getBlock() instanceof net.minecraft.world.level.block.EntityBlock eb)) return null;
        BlockEntity be = clientBlockEntities.get(e.pos());
        net.minecraft.nbt.CompoundTag tag = clientBlockData.remove(e.pos());
        if (be == null || be.getBlockState() != st) {
            be = eb.newBlockEntity(e.pos(), st);
            if (be == null) return null;
            be.setLevel(level());
            clientBlockEntities.put(e.pos(), be);
            if (tag == null) tag = lastTags.get(e.pos());
        }
        if (tag != null) {
            lastTags.put(e.pos(), tag);
            try (var reporter = new net.minecraft.util.ProblemReporter.ScopedCollector(be.problemPath(), com.mojang.logging.LogUtils.getLogger())) {
                be.loadWithComponents(net.minecraft.world.level.storage.TagValueInput.create(reporter, level().registryAccess(), tag));
            } catch (RuntimeException ex) { /* Anzeige ohne Daten */ }
        }
        return be;
    }

    private final Map<BlockPos, net.minecraft.nbt.CompoundTag> lastTags = new HashMap<>();

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
        boardCooldown.put(passenger.getUUID(), (long) tickCount + 60);
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
        if (helmCache != null) return helmCache;
        for (ShipStructure.Entry e : structure.blocks)
            if (e.state().getBlock() instanceof de.simon.spacemod.block.ShipHelmBlock) return helmCache = e.pos();
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
            // von außen auf eine Luftschleuse geklickt: Schleuse bedienen, sonst einsteigen
            if (structure != null && plot >= 0 && shipyard() != null) {
                var q = rotation(1F);
                Vec3 eye = ShipPhysics.inverse(q, player.getEyePosition().subtract(position()));
                Vec3 look = ShipPhysics.inverse(q, player.getViewVector(1F));
                BlockPos hit = structure.raycast(eye, look, 8);
                if (hit != null) {
                    BlockPos t = plotOrigin().offset(hit);
                    BlockState st = shipyard().getBlockState(t);
                    if (st.getBlock() instanceof de.simon.spacemod.block.AirlockBlock) {
                        st.useWithoutItem(shipyard(), player, new net.minecraft.world.phys.BlockHitResult(Vec3.atCenterOf(t),
                            net.minecraft.core.Direction.getApproximateNearest(-look.x, -look.y, -look.z), t, false));
                        return net.minecraft.world.InteractionResult.SUCCESS;
                    }
                }
            }
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
    public void control(Player player, float yaw, float pitch, int throttle, int strafe, int vertical) {
        if (!isPilot(player)) return;
        targetYaw = yaw;
        targetPitch = Math.max(-80, Math.min(80, pitch));
        this.throttle = Integer.signum(throttle);
        this.strafe = Integer.signum(strafe);
        this.vertical = Integer.signum(vertical);
        controlAge = 0;
    }

    // ---- Werft: die echten Blöcke des Schiffs ----

    public int plot() {
        return plot;
    }

    public BlockPos plotOrigin() {
        return entityData.get(PLOT_ORIGIN);
    }

    private @Nullable ServerLevel shipyard() {
        return level() instanceof ServerLevel sl ? Shipyard.level(sl.getServer()) : null;
    }

    /** Lage eines Lebewesens im Schiffssystem (Mitfahrer: Sitzplatz, sonst umgerechnet). */
    public Vec3 localOf(Entity e) {
        if (hasPassenger(e)) return seat(e);
        return ShipPhysics.inverse(rotation(1F), e.position().subtract(position()));
    }

    /** Echter Block (in der Werft) an einer lokalen Stelle: Block-Entity oder null. */
    public @Nullable BlockEntity liveAt(BlockPos local) {
        ServerLevel sy = shipyard();
        return sy == null || plot < 0 ? null : sy.getBlockEntity(plotOrigin().offset(local));
    }

    private BlockPos[] bounds() {
        int x0 = Integer.MAX_VALUE, y0 = x0, z0 = x0, x1 = Integer.MIN_VALUE, y1 = x1, z1 = x1;
        for (ShipStructure.Entry e : structure.blocks) {
            BlockPos p = e.pos();
            x0 = Math.min(x0, p.getX()); y0 = Math.min(y0, p.getY()); z0 = Math.min(z0, p.getZ());
            x1 = Math.max(x1, p.getX()); y1 = Math.max(y1, p.getY()); z1 = Math.max(z1, p.getZ());
        }
        return new BlockPos[] {new BlockPos(x0, y0, z0), new BlockPos(x1, y1, z1)};
    }

    /**
     * Blöcke in die Werft stellen: dort bleiben sie echte Blöcke und laufen weiter (Maschinen,
     * Reaktoren, Kisten, Blöcke anderer Mods). Ohne Werft-Dimension bleibt das Schiff ein reines Abbild.
     */
    public boolean moveToShipyard() {
        ServerLevel sy = shipyard();
        if (sy == null || structure == null || plot >= 0) return false;
        plot = Shipyard.allocate(sy.getServer());
        BlockPos origin = Shipyard.origin(plot);
        entityData.set(PLOT_ORIGIN, origin);
        BlockPos[] b = bounds();
        Shipyard.force(sy, origin, b[0], b[1], true);
        int flags = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
        for (int pass = 0; pass < 2; pass++)
            for (ShipStructure.Entry e : structure.blocks) {
                BlockPos t = origin.offset(e.pos());
                boolean full = e.state().isCollisionShapeFullBlock(sy, t);
                if ((pass == 0) != full) continue; // erst volle Blöcke, dann Anbauteile
                sy.setBlock(t, e.state(), flags);
                if (e.data().isPresent()) {
                    try {
                        BlockEntity be = BlockEntity.loadStatic(t, e.state(), e.data().get(), sy.registryAccess());
                        if (be != null) sy.setBlockEntity(be);
                    } catch (RuntimeException ex) {
                        // fremder Block, der sich nicht laden lässt: bleibt ohne Daten stehen
                    }
                }
            }
        structure.stripData();
        Shipyard.register(plot, this);
        return true;
    }

    /** Blöcke mit aktuellem Zustand und Daten aus der Werft (zum Landen). */
    private List<ShipStructure.Entry> currentBlocks() {
        ServerLevel sy = shipyard();
        if (sy == null || plot < 0) return structure.blocks;
        List<ShipStructure.Entry> out = new ArrayList<>(structure.blocks.size());
        BlockPos origin = plotOrigin();
        for (ShipStructure.Entry e : structure.blocks) {
            BlockPos t = origin.offset(e.pos());
            BlockState st = sy.getBlockState(t);
            BlockEntity be = sy.getBlockEntity(t);
            Optional<net.minecraft.nbt.CompoundTag> data = Optional.empty();
            if (be != null) {
                try { data = Optional.of(be.saveWithFullMetadata(sy.registryAccess())); } catch (RuntimeException ex) { /* ohne Daten */ }
            }
            out.add(new ShipStructure.Entry(e.pos(), st, data));
        }
        return out;
    }

    /** Bauplatz räumen (nach dem Landen): Inhalte sind schon in der Welt, also keine Drops. */
    private void clearShipyard() {
        ServerLevel sy = shipyard();
        if (sy == null || plot < 0) return;
        BlockPos origin = plotOrigin();
        int flags = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS;
        for (int pass = 0; pass < 2; pass++)
            for (ShipStructure.Entry e : structure.blocks) {
                BlockPos t = origin.offset(e.pos());
                BlockState st = sy.getBlockState(t);
                if (st.isAir()) continue;
                boolean full = st.isCollisionShapeFullBlock(sy, t);
                if ((pass == 0) == full) continue; // erst Anbauteile, dann volle Blöcke
                if (sy.getBlockEntity(t) != null) sy.removeBlockEntity(t);
                sy.setBlock(t, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), flags);
            }
        BlockPos[] b = bounds();
        Shipyard.force(sy, origin, b[0], b[1], false);
        Shipyard.unregister(plot, this);
    }

    /** Zustände aus der Werft übernehmen (Türen, Lampen, Öfen …) und an die Clients schicken. */
    private void syncBlocks(ServerLevel sy) {
        int n = structure.blocks.size();
        if (n == 0) return;
        int per = Math.min(n, Math.max(512, n / 8));
        BlockPos origin = plotOrigin();
        int[] idx = new int[per];
        int[] ids = new int[per];
        int c = 0;
        for (int k = 0; k < per; k++) {
            int i = scanCursor;
            scanCursor = (scanCursor + 1) % n;
            ShipStructure.Entry e = structure.blocks.get(i);
            BlockState st = sy.getBlockState(origin.offset(e.pos()));
            if (st != e.state() && !(st.isAir() && e.state().isAir())) { // auch zerstörte Blöcke (Luft) übernehmen
                structure.setState(i, st);
                idx[c] = i;
                ids[c++] = Block.getId(st);
            }
        }
        if (c > 0) TurboliftNetwork.CHANNEL.send(new de.simon.spacemod.flight.ShipBlocksMessage(getId(),
            java.util.Arrays.copyOf(idx, c), java.util.Arrays.copyOf(ids, c)), PacketDistributor.TRACKING_ENTITY.with(this));
    }

    /**
     * Einsteigen im Flug: wer (nicht schleichend) im Schiff steht – durch eine offene Tür
     * hereingekommen oder auf dem Schiff gelandet – fliegt ab sofort mit und läuft darin herum.
     */
    private void boardWalkers(ServerLevel level) {
        for (Player p : level.getEntitiesOfClass(Player.class, getBoundingBox().inflate(RANGE + 1),
                pl -> !pl.isPassenger() && !pl.isSpectator() && !pl.isShiftKeyDown()
                    && !(pl instanceof ServerPlayer sp && de.simon.spacemod.weapons.CameraControl.active(sp)))) {
            Long until = boardCooldown.get(p.getUUID());
            if (until != null && until > tickCount) continue;
            if (standsInShip(ShipPhysics.inverse(rotation(1F), p.position().subtract(position())))) p.startRiding(this, true, false);
        }
    }

    /** Gehört man noch zum Schiff (höchstens 2 Blöcke von einem festen Schiffsblock entfernt)? */
    public boolean overShip(Vec3 local) {
        if (structure == null) return false;
        return nearShip(BlockPos.containing(local.x + 0.5, local.y + 0.55, local.z + 0.5));
    }

    // ---- Stühle im Flug ----

    private boolean landing;

    public boolean isLanding() {
        return landing;
    }

    /** Auf einen Stuhl im fliegenden Schiff setzen: ein unsichtbarer Sitz fährt mit, der Spieler sitzt darauf. */
    public void sitOnChair(ServerPlayer player, BlockPos local, BlockState chair) {
        if (!(level() instanceof ServerLevel sl)) return;
        Vec3 l = new Vec3(local.getX(), local.getY(), local.getZ());
        for (Entity p : getPassengers())
            if (p instanceof SeatEntity && seats.getOrDefault(p.getUUID(), Vec3.ZERO).distanceToSqr(l) < 0.25) return; // besetzt
        Vec3 w = ShipPhysics.rotate(rotation(1F), l.x, l.y, l.z).add(position());
        float facing = chair.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING)
            ? chair.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING).toYRot() : 0;
        float yaw = facing + (yaw() - yaw0());
        SeatEntity seat = new SeatEntity(de.simon.spacemod.ModBlocks.SEAT.get(), sl);
        seat.snapTo(w.x, w.y, w.z, yaw, 0);
        sl.addFreshEntity(seat);
        seat.startRiding(this, true, false);
        seats.put(seat.getUUID(), l);
        player.startRiding(seat, true, false);
        player.setYRot(yaw);
        player.setYHeadRot(yaw);
    }

    /** Aufstehen vom Stuhl im Flug: vor den Stuhl stellen (sonst obendrauf), wieder im Schiff laufen. */
    public void standUp(ServerPlayer player, SeatEntity seat) {
        if (landing || structure == null || !isAlive()) return;
        Vec3 s = seats.getOrDefault(seat.getUUID(), Vec3.ZERO);
        BlockPos chair = BlockPos.containing(s.x + 0.5, s.y + 0.5, s.z + 0.5);
        Vec3 stand = new Vec3(s.x, s.y + 0.5, s.z);
        int i = structure.indexOf(chair);
        if (i >= 0 && structure.blocks.get(i).state().hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING)) {
            BlockPos front = chair.relative(structure.blocks.get(i).state().getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING));
            var col = structure.collision();
            if (!col.containsKey(front) && !col.containsKey(front.above())) stand = new Vec3(front.getX(), front.getY() - 0.5, front.getZ());
        }
        player.startRiding(this, true, false);
        seats.put(player.getUUID(), stand);
        restoreSeats(Map.of(player.getUUID(), stand));
    }

    /** Reichweite, in der man zum Schiff gehört (einsteigen bzw. nicht herausfallen). */
    public static final int RANGE = 2;

    /** Füße und Kopf frei und höchstens 2 Blöcke von einem festen Schiffsblock entfernt. */
    public boolean standsInShip(Vec3 local) {
        if (structure == null) return false;
        var col = structure.collision();
        BlockPos feet = BlockPos.containing(local.x + 0.5, local.y + 0.55, local.z + 0.5);
        if (col.containsKey(feet) || col.containsKey(feet.above())) return false;
        return nearShip(feet);
    }

    /** Ist ein fester Schiffsblock höchstens {@link #RANGE} Blöcke von Füßen oder Kopf entfernt? */
    private boolean nearShip(BlockPos feet) {
        var col = structure.collision();
        for (int x = -RANGE; x <= RANGE; x++)
            for (int y = -RANGE; y <= RANGE + 1; y++)
                for (int z = -RANGE; z <= RANGE; z++)
                    if (col.containsKey(feet.offset(x, y, z))) return true;
        return false;
    }

    // ---- Flug ----

    /** Client: letzte Lage vom Server und Ticks seitdem (für die Vorhersage). */
    private @Nullable Vec3 lastServerPos;
    private float lastServerYaw, lastServerPitch;
    private int sincePos, sinceRot;

    /**
     * Client: Schiff gleichmäßig mit der gemeldeten Geschwindigkeit weiterbewegen und Abweichungen zur
     * Serverlage sanft ausgleichen. Pakete kommen nicht in gleichmäßigem Takt an – ohne Vorhersage ruckt
     * das Schiff vor und zurück, und mit ihm Kamera und Mitfahrer.
     */
    private void predict() {
        org.joml.Vector3fc vf = entityData.get(VEL);
        Vec3 vel = new Vec3(vf.x(), vf.y(), vf.z());
        Vec3 server = interpolation.position();
        if (lastServerPos == null || !server.equals(lastServerPos)) { lastServerPos = server; sincePos = 0; } else sincePos++;
        Vec3 target = server.add(vel.scale(Math.min(sincePos, 5)));
        Vec3 next = position().add(vel);
        Vec3 err = target.subtract(next);
        setPos(err.lengthSqr() > 64 ? target : next.add(err.scale(0.3)));
        float y = yaw(), pt = pitch();
        if (y != lastServerYaw || pt != lastServerPitch) { lastServerYaw = y; lastServerPitch = pt; sinceRot = 0; } else sinceRot++;
        float yr = entityData.get(YAW_RATE), pr = entityData.get(PITCH_RATE);
        float k = Math.min(sinceRot, 5);
        float ty = y + yr * k, tp = pt + pr * k;
        curYaw += yr;
        curPitch += pr;
        float ey = (float) ShipPhysics.wrap(ty - curYaw), ep = tp - curPitch;
        if (Math.abs(ey) > 30 || Math.abs(ep) > 30) { curYaw = ty; curPitch = tp; }
        else { curYaw += ey * 0.3F; curPitch += ep * 0.3F; }
    }

    @Override
    public void tick() {
        if (level().isClientSide()) {
            if (!curInit) { curYaw = yaw(); curPitch = pitch(); curInit = true; }
            prevYaw = curYaw;
            prevPitch = curPitch;
            predict();
        } else {
            prevYaw = yaw();
            prevPitch = pitch();
        }
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
        if (++controlAge > 10 || pilot == null) { throttle = 0; strafe = 0; vertical = 0; }
        if (inWarp() && autopilot) {
            // Autopilot: das Schiff fliegt im Warp sichtbar geradeaus in Richtung Ziel
            throttle = 1; strafe = 0; vertical = 0;
            targetYaw = (float) Math.toDegrees(Math.atan2(-(tx - rx), tz - rz));
            targetPitch = 0;
        }

        // Strom: Antriebe brauchen Energie, wenn sie schieben oder drehen
        double turnDemand = pilot == null ? 0 : Math.min(1, (Math.abs(ShipPhysics.wrap(targetYaw - yaw())) + Math.abs(targetPitch - pitch())) / 30);
        double demand = structure.engines * ShipPhysics.RF_PER_ENGINE * Math.max(Math.max(Math.abs(throttle), Math.max(Math.abs(strafe), Math.abs(vertical))), turnDemand);
        if (inWarp()) demand = 0; // im Warp bewegt der Warpantrieb das Schiff, die Impulsantriebe laufen ohne eigenen Strom
        ServerLevel sy = shipyard();
        if (plot < 0 && sy != null) moveToShipyard(); // Schiff aus einer älteren Version
        if (plot >= 0 && sy != null) {
            Shipyard.register(plot, this);
            // Strom aus dem Bordnetz in der Werft: Warpkerne, Reaktoren, Generatoren anderer Mods
            BlockPos helm = helmLocal();
            BlockPos src = plotOrigin().offset(helm != null ? helm : structure.blocks.get(0).pos());
            de.simon.spacemod.power.ShipPower.Grid grid = de.simon.spacemod.power.ShipPower.grid(sy, src);
            if (inWarp() && !warpPower(sy, grid)) return;
            if (demand > 0) {
                int got = de.simon.spacemod.power.ShipPower.draw(sy, grid, (int) Math.min(Integer.MAX_VALUE, Math.ceil(demand)),
                    de.simon.spacemod.power.ShipPower.Category.ENGINES);
                lastPower = Math.min(1, got / demand);
            } else if (inWarp()) {
                lastPower = 1;
            } else if (tickCount % 10 == 0) {
                lastPower = grid.hasPower(sy) ? 1 : 0;
            }
            damperLevel = damper(sy, grid);
            syncBlocks(sy);
            if (tickCount % 10 == 0) {
                var changed = blockEntityData(false);
                if (!changed.isEmpty()) TurboliftNetwork.CHANNEL.send(new de.simon.spacemod.flight.ShipBlockEntityMessage(getId(), changed),
                    PacketDistributor.TRACKING_ENTITY.with(this));
            }
        } else {
            double power = demand <= 0 ? 1 : Math.min(1, structure.powerPerTick / demand);
            lastPower = structure.powerPerTick > 0 ? power : 0;
        }
        if (tickCount % 2 == 0) boardWalkers(level);
        if (restoreSeats != null && tickCount >= restoreAt) {
            for (Entity p : getPassengers()) {
                Vec3 s = restoreSeats.get(p.getUUID());
                if (s == null) continue;
                seats.put(p.getUUID(), s);
                var msg = new de.simon.spacemod.flight.ShipSeatSync(p.getId(), (float) s.x, (float) s.y, (float) s.z, true);
                TurboliftNetwork.CHANNEL.send(msg, PacketDistributor.TRACKING_ENTITY.with(this));
                if (p instanceof ServerPlayer sp) TurboliftNetwork.CHANNEL.send(msg, PacketDistributor.PLAYER.with(sp));
            }
            restoreSeats = null;
        }

        // Drehung zum Fadenkreuz (mit Trägheit)
        double alpha = ShipPhysics.angularAcceleration(structure.engines, structure.inertia, lastPower);
        double ty = pilot == null ? yaw() : targetYaw, tp = pilot == null ? pitch() : targetPitch;
        double[] ry = ShipPhysics.turn(yaw(), yawRate, ty, alpha);
        double[] rp = ShipPhysics.turn(pitch(), pitchRate, tp, alpha);
        float newYaw = (float) ry[0], newPitch = (float) Math.max(-80, Math.min(80, rp[0]));

        // Schub nach vorn, zur Seite und nach oben/unten (relativ zum Schiff), sonst bremsen die Trägheitsdämpfer
        double accel = ShipPhysics.acceleration(structure.engines, structure.mass(), lastPower);
        Vec3 v = ShipPhysics.velocity(velocity, ShipPhysics.forward(newYaw, newPitch), throttle, strafe, vertical, accel,
            ShipPhysics.damperDecay(damperLevel));
        if (v.length() > ShipPhysics.MAX_SPEED) v = v.normalize().scale(ShipPhysics.MAX_SPEED);
        if (v.lengthSqr() < 1e-6) v = Vec3.ZERO;

        // Kollision: Außenblöcke an der neuen Lage dürfen nicht in Welt-Blöcken stecken
        Vec3 newPos = position().add(v);
        Quaternionf q = ShipPhysics.rotation(yaw0(), newYaw, newPitch);
        Quaternionf cur = ShipPhysics.rotation(yaw0(), yaw(), pitch());
        Vec3 back = lastDir.lengthSqr() > 1e-6 ? lastDir.scale(-1) : new Vec3(0, 1, 0);
        if (collides(level, position(), cur)) {
            // steckt schon fest (z. B. ein Block wurde ins Schiff gesetzt): in Gegenrichtung herausschieben
            Vec3 free = null;
            for (double d = 0.5; d <= 4 && free == null; d += 0.5)
                for (Vec3 dir : new Vec3[] {back, new Vec3(0, 1, 0), back.add(0, 1, 0).normalize()}) {
                    Vec3 p = position().add(dir.scale(d));
                    if (!collides(level, p, cur)) { free = p; break; }
                }
            newPos = free != null ? free : position().add(back.scale(0.5)); // notfalls Stück für Stück hinaus
            newYaw = yaw(); newPitch = pitch(); ry[1] = 0; rp[1] = 0;
            v = back.scale(0.05);
        } else if (collides(level, newPos, q)) {
            // Aufprall: ein Stück zurück in die Richtung, aus der das Schiff kam (Rückstoß)
            Vec3 dir = v.lengthSqr() > 1e-6 ? v.normalize() : lastDir;
            newPos = position();
            if (collides(level, newPos, q)) { q = cur; newYaw = yaw(); newPitch = pitch(); ry[1] = 0; rp[1] = 0; }
            Vec3 bounce = dir.scale(-Math.max(0.2, v.length() * 0.5));
            if (!collides(level, newPos.add(bounce), q)) newPos = newPos.add(bounce);
            v = bounce.scale(0.5);
            level.playSound(null, BlockPos.containing(newPos), net.minecraft.sounds.SoundEvents.ANVIL_LAND,
                net.minecraft.sounds.SoundSource.BLOCKS, 0.6F, 0.6F);
        } else if (v.lengthSqr() > 1e-6) {
            lastDir = v.normalize();
        }
        velocity = v;
        yawRate = ry[1];
        pitchRate = rp[1];
        entityData.set(VEL, new org.joml.Vector3f((float) v.x, (float) v.y, (float) v.z));
        entityData.set(YAW_RATE, (float) yawRate);
        entityData.set(PITCH_RATE, (float) pitchRate);
        entityData.set(YAW, newYaw);
        entityData.set(PITCH, newPitch);
        setPos(newPos);
        updateBox();
        keepLoaded(level);
        if (inWarp()) {
            if (warpAdvance()) return;
            if (tickCount % 10 == 0 && pilot != null && level.getPlayerByUUID(pilot) instanceof ServerPlayer p) p.sendOverlayMessage(warpHud());
            return;
        }
        // hoch genug: in den Weltraum (bzw. ganz unten im Weltraum zurück in die Oberwelt)
        if (tickCount % 5 == 0 && de.simon.spacemod.space.SpaceTravel.check(this, structure.radius, pilot) != this) return;

        if (tickCount % 10 == 0 && pilot != null && level.getPlayerByUUID(pilot) instanceof ServerPlayer p)
            p.sendOverlayMessage(Component.translatable("message.simonspacemod.ship.hud",
                String.format("%.1f", velocity.length() * 20),
                (throttle > 0 ? ">" : throttle < 0 ? "<" : "–") + (strafe > 0 ? "→" : strafe < 0 ? "←" : "") + (vertical > 0 ? "↑" : vertical < 0 ? "↓" : ""),
                (int) Math.round(lastPower * 100), structure.engines, structure.blocks.size(), damperLevel * 10));
    }

    /**
     * Stufe der Trägheitsdämpfer: die höchste eingestellte Steuerung im Schiff (ohne Steuerung 10 %). Über 10 %
     * kostet Strom aus dem Bordnetz (Gruppe Antrieb), aber nur, solange das Schiff sich bewegt; reicht er nicht,
     * arbeiten die Dämpfer mit 10 %.
     */
    private int damper(ServerLevel sy, de.simon.spacemod.power.ShipPower.Grid grid) {
        if (damperBlocks == null) {
            damperBlocks = new java.util.ArrayList<>();
            for (ShipStructure.Entry e : structure.blocks)
                if (e.state().getBlock() instanceof de.simon.spacemod.flight.InertialDamperBlock) damperBlocks.add(e.pos());
        }
        int level = -1;
        for (BlockPos p : damperBlocks) {
            BlockState st = sy.getBlockState(plotOrigin().offset(p));
            if (st.getBlock() instanceof de.simon.spacemod.flight.InertialDamperBlock)
                level = Math.max(level, st.getValue(de.simon.spacemod.flight.InertialDamperBlock.LEVEL));
        }
        if (level < 0) return ShipPhysics.DEFAULT_DAMPER;
        int cost = ShipPhysics.damperCost(level);
        if (cost == 0 || velocity.lengthSqr() < 1e-6) return level;
        // im Warp kommen die Dämpfer gleich nach dem Warpantrieb – sie werden als Letztes gedrosselt
        int got = inWarp() ? de.simon.spacemod.power.ShipPower.drawPriority(sy, grid, cost)
            : de.simon.spacemod.power.ShipPower.draw(sy, grid, cost, de.simon.spacemod.power.ShipPower.Category.ENGINES);
        if (got >= cost) return level;
        // nicht genug: so viele Stufen, wie der Strom hergibt
        int l = level;
        while (l > ShipPhysics.DEFAULT_DAMPER && ShipPhysics.damperCost(l) > got) l--;
        return l;
    }

    // ---- Warpantrieb ----

    public boolean inWarp() {
        return warp > 0;
    }

    /** Stromnetz des Schiffs (in der Werft) oder null. */
    public de.simon.spacemod.power.ShipPower.@Nullable Grid powerGrid() {
        ServerLevel sy = shipyard();
        if (sy == null || plot < 0 || structure == null) return null;
        BlockPos helm = helmLocal();
        return de.simon.spacemod.power.ShipPower.grid(sy, plotOrigin().offset(helm != null ? helm : structure.blocks.get(0).pos()));
    }

    /** Stromerzeugung des Schiffs (FE/t, ohne Speicher). */
    public long warpProduction() {
        ServerLevel sy = shipyard();
        var grid = powerGrid();
        return sy == null || grid == null ? 0 : de.simon.spacemod.power.ShipPower.production(sy, grid);
    }

    /** Höchste Warpstufe nach den Gondeln des Schiffs. */
    public double warpMaxNacelles() {
        return structure == null ? 0 : de.simon.spacemod.space.WarpDrive.maxForNacelles(
            de.simon.spacemod.space.WarpDrive.nacelleCount(de.simon.spacemod.space.WarpDrive.nacelles(structure)));
    }

    /** Für das Warp-Menü: {Warp, rx, ry, rz, tx, ty, tz, Autopilot, Ziel Weltraum}. */
    public double[] warpState() {
        boolean inW = inWarp();
        return new double[] {warp, inW ? rx : getX(), inW ? ry : getY(), inW ? rz : getZ(), tx, ty, tz, autopilot ? 1 : 0,
            warpDest.equals(SpaceTravel.SPACE.identifier().toString()) ? 1 : 0};
    }

    private static String fmt(double w) {
        return String.format(java.util.Locale.ROOT, "%.1f", w);
    }

    /** Prüft Gondeln und Strom für Warp {@code w}; meldet den Grund und gibt false zurück, wenn es nicht geht. */
    private boolean warpPossible(ServerPlayer p, double w) {
        double maxN = warpMaxNacelles();
        if (maxN <= 0) {
            p.sendSystemMessage(Component.translatable("message.simonspacemod.warp.no_nacelles").withStyle(net.minecraft.ChatFormatting.RED));
            return false;
        }
        if (w > maxN + 1e-9) {
            p.sendSystemMessage(Component.translatable("message.simonspacemod.warp.too_fast", fmt(maxN)).withStyle(net.minecraft.ChatFormatting.RED));
            return false;
        }
        long prod = warpProduction();
        int cost = de.simon.spacemod.space.WarpDrive.cost(w);
        if (prod < cost) {
            p.sendSystemMessage(Component.translatable("message.simonspacemod.warp.no_power", cost, prod,
                fmt(de.simon.spacemod.space.WarpDrive.maxForPower(prod))).withStyle(net.minecraft.ChatFormatting.RED));
            return false;
        }
        return true;
    }

    /** Warp starten (oder im Warp Stufe und Ziel ändern): mit Autopilot zu (x, y, z) oder manuell. */
    public void engageWarp(ServerPlayer p, boolean auto, double x, double y, double z, double w, boolean destSpace) {
        if (!isPilot(p) || structure == null || !(level() instanceof ServerLevel level)) return;
        w = de.simon.spacemod.space.WarpDrive.clamp(w);
        if (!warpPossible(p, w)) return;
        warp = w;
        warpCost = de.simon.spacemod.space.WarpDrive.cost(w);
        autopilot = auto;
        tx = x; ty = y; tz = z;
        warpDest = (destSpace ? SpaceTravel.SPACE : Level.OVERWORLD).identifier().toString();
        if (level.dimension() == de.simon.spacemod.space.WarpDrive.WARP) {
            p.sendOverlayMessage(Component.translatable(auto ? "message.simonspacemod.warp.course" : "message.simonspacemod.warp.level", fmt(w)));
            return;
        }
        ServerLevel target = level.getServer().getLevel(de.simon.spacemod.space.WarpDrive.WARP);
        if (target == null) return;
        warpOrigin = level.dimension().identifier().toString();
        rx = getX(); ry = getY(); rz = getZ();
        velocity = Vec3.ZERO;
        Vec3 pos = new Vec3((plot >= 0 ? plot : getId()) * 4096.0 + 0.5, de.simon.spacemod.space.WarpDrive.WARP_Y, 0.5);
        SpaceTravel.transfer(this, target, pos, pilot, "message.simonspacemod.warp.enter", fmt(w));
    }

    /** Warpstufe mit den Tasten ändern: hoch in ganzen Stufen bis 9.9, runter unter Warp 1 = Warp verlassen. */
    public void stepWarp(ServerPlayer p, int dir) {
        if (!isPilot(p)) return;
        if (!inWarp()) {
            p.sendOverlayMessage(Component.translatable("message.simonspacemod.warp.use_menu"));
            return;
        }
        double w;
        if (dir > 0) w = warp >= 9 ? de.simon.spacemod.space.WarpDrive.MAX_WARP : Math.floor(warp + 1e-9) + 1;
        else if (warp <= 1 + 1e-9) {
            leaveWarp("message.simonspacemod.warp.exit");
            return;
        } else w = warp > 9 ? 9 : Math.ceil(warp - 1e-9) - 1;
        w = Math.min(w, de.simon.spacemod.space.WarpDrive.MAX_WARP);
        if (!warpPossible(p, w)) return;
        warp = w;
        warpCost = de.simon.spacemod.space.WarpDrive.cost(w);
        p.sendOverlayMessage(Component.translatable("message.simonspacemod.warp.level", fmt(w)));
    }

    /** Warpstrom mit Vorrang ziehen; reicht er nicht, fällt das Schiff sofort aus dem Warp. */
    private boolean warpPower(ServerLevel sy, de.simon.spacemod.power.ShipPower.Grid grid) {
        int damperCost = ShipPhysics.damperCost(damperLevel);
        de.simon.spacemod.power.ShipPower.reserve(sy, grid, (long) warpCost + damperCost);
        long prod = tickCount % 20 == 0 ? de.simon.spacemod.power.ShipPower.production(sy, grid) : Long.MAX_VALUE;
        int got = de.simon.spacemod.power.ShipPower.drawPriority(sy, grid, warpCost);
        if (got >= warpCost && prod >= warpCost) return true;
        long have = Math.min(prod, got);
        leaveWarp("message.simonspacemod.warp.dropout", warpCost, have);
        return false;
    }

    /** Ein Tick im Warp: echte Lage weiterrechnen, Ziel erreicht -> Warp verlassen. @return true, wenn das Schiff gewechselt hat */
    private boolean warpAdvance() {
        double step = de.simon.spacemod.space.WarpDrive.speed(warp);
        if (autopilot) {
            double dx = tx - rx, dz = tz - rz, d = Math.sqrt(dx * dx + dz * dz);
            if (d <= step) {
                rx = tx; rz = tz; ry = ty;
                leaveWarp("message.simonspacemod.warp.arrived");
                return true;
            }
            rx += dx / d * step;
            rz += dz / d * step;
        } else {
            double k = step / ShipPhysics.MAX_SPEED; // volle Impulsfahrt = Warpgeschwindigkeit
            rx += velocity.x * k;
            rz += velocity.z * k;
            ry += velocity.y;
        }
        rx = Math.max(-29_000_000, Math.min(29_000_000, rx));
        rz = Math.max(-29_000_000, Math.min(29_000_000, rz));
        return false;
    }

    private Component warpHud() {
        String where = (int) rx + " " + (int) ry + " " + (int) rz;
        if (autopilot) {
            double d = Math.hypot(tx - rx, tz - rz), sec = d / Math.max(1e-9, de.simon.spacemod.space.WarpDrive.speed(warp)) / 20;
            return Component.translatable("message.simonspacemod.warp.hud_auto", fmt(warp), warpCost, where, (int) d, String.format("%.0f", Math.ceil(sec)), damperLevel * 10);
        }
        return Component.translatable("message.simonspacemod.warp.hud", fmt(warp), warpCost, where, damperLevel * 10);
    }

    /**
     * Warp verlassen: zurück in die Herkunftsdimension (Autopilot: in die Zieldimension) an die echte Lage.
     * Steht dort etwas im Weg, kommt das Schiff so weit darüber heraus, bis es frei ist.
     */
    public FlyingShipEntity leaveWarp(String message, Object... args) {
        if (!(level() instanceof ServerLevel level)) return this;
        String dim = autopilot && !warpDest.isEmpty() ? warpDest : warpOrigin;
        ServerLevel target = null;
        for (ServerLevel l : level.getServer().getAllLevels())
            if (l.dimension().identifier().toString().equals(dim)) target = l;
        if (target == null) target = level.getServer().overworld();
        double r = structure == null ? 4 : structure.radius;
        double y = Math.max(target.getMinY() + r + 2, Math.min(target.getMaxY() - r - 2, ry));
        Quaternionf q = ShipPhysics.rotation(yaw0(), yaw(), pitch());
        int cr = (int) Math.ceil((r + 2) / 16);
        for (int cx = ((int) Math.floor(rx) >> 4) - cr; cx <= ((int) Math.floor(rx) >> 4) + cr; cx++)
            for (int cz = ((int) Math.floor(rz) >> 4) - cr; cz <= ((int) Math.floor(rz) >> 4) + cr; cz++) target.getChunk(cx, cz);
        Vec3 pos = new Vec3(rx, y, rz);
        while (structure != null && collides(target, pos, q) && pos.y < target.getMaxY() - r - 2) pos = pos.add(0, 2, 0);
        warp = 0;
        warpCost = 0;
        autopilot = false;
        velocity = Vec3.ZERO;
        if (level.dimension() != de.simon.spacemod.space.WarpDrive.WARP) return this;
        return SpaceTravel.transfer(this, target, pos, pilot, message, args);
    }

    private void updateBox() {
        float r = entityData.get(RADIUS);
        setBoundingBox(new AABB(position(), position()).inflate(r));
    }

    private boolean collides(ServerLevel level, Vec3 pos, Quaternionf q) {
        for (ShipStructure.Entry e : structure.surface) {
            if (e.state().isAir()) continue; // zerstört
            Vec3 w = ShipPhysics.rotate(q, e.pos().getX(), e.pos().getY(), e.pos().getZ()).add(pos);
            BlockPos bp = BlockPos.containing(w);
            if (level.isOutsideBuildHeight(bp)) continue; // über/unter der Welt ist frei (Weg in den Weltraum)
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
            targets.add(t);
            if (e.state().isAir()) continue; // im Kampf zerstört
            BlockState here = level.getBlockState(t);
            if (!here.isAir() && !here.canBeReplaced()) {
                by.sendOverlayMessage(Component.translatable("message.simonspacemod.ship.no_room"));
                return;
            }
        }
        // Passagiere an ihre Plätze im gelandeten Schiff
        Quaternionf q = ShipPhysics.rotation(yaw0(), yaw0() + steps * 90F, 0F);
        Map<Entity, Vec3> riders = new HashMap<>();
        landing = true;
        for (Entity p : List.copyOf(getPassengers())) {
            Vec3 seat = seats.getOrDefault(p.getUUID(), Vec3.ZERO);
            Vec3 w = ShipPhysics.rotate(q, seat.x, seat.y, seat.z).add(Vec3.atCenterOf(pivot));
            if (p instanceof SeatEntity chairSeat) { // Sitzende: auf den Stuhl im gelandeten Schiff stellen
                for (Entity sitter : List.copyOf(chairSeat.getPassengers())) {
                    riders.put(sitter, w.add(0, 0.5, 0));
                    sitter.stopRiding();
                }
                chairSeat.discard();
                continue;
            }
            riders.put(p, w);
        }
        ejectPassengers();
        int flags = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
        for (int pass = 0; pass < 2; pass++)
            for (int i = 0; i < blocks.size(); i++) {
                ShipStructure.Entry e = blocks.get(i);
                if (e.state().isAir()) continue; // zerstörter Block: nichts setzen
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
        clearShipyard();
        riders.forEach((p, w) -> p.teleportTo(w.x, w.y + 0.05, w.z));
        discard();
    }
}

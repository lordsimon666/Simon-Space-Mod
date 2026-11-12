package de.simon.spacemod.entity;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * Sichtbarer Phaserstrahl (wie ein Leuchtfeuer-Strahl): beginnt an der Position der Entity und
 * endet am synchronisierten Zielpunkt. Wird vom Schuss jeden Tick nachgeführt und danach entfernt;
 * nicht gespeichert.
 */
public class PhaserBeamEntity extends Entity {
    private static final EntityDataAccessor<org.joml.Vector3fc> TO = SynchedEntityData.defineId(PhaserBeamEntity.class, EntityDataSerializers.VECTOR3);
    private static final EntityDataAccessor<Integer> COLOR = SynchedEntityData.defineId(PhaserBeamEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> CORE = SynchedEntityData.defineId(PhaserBeamEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> WIDTH = SynchedEntityData.defineId(PhaserBeamEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> PULSE = SynchedEntityData.defineId(PhaserBeamEntity.class, EntityDataSerializers.BOOLEAN);
    /** Sound des Strahls (Index in {@link de.simon.spacemod.ModSounds#BEAMS}, -1 = keiner), Tonhöhe und Lautstärke. */
    private static final EntityDataAccessor<Integer> SOUND = SynchedEntityData.defineId(PhaserBeamEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> PITCH = SynchedEntityData.defineId(PhaserBeamEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> VOLUME = SynchedEntityData.defineId(PhaserBeamEntity.class, EntityDataSerializers.FLOAT);
    private boolean soundStarted;
    /** Sicherheitsnetz: verschwindet von selbst, wenn niemand ihn mehr nachführt. */
    private int idle;

    public PhaserBeamEntity(EntityType<?> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(TO, new Vector3f()).define(COLOR, 0xFFFF6020).define(CORE, 0xFFFFE0A0).define(WIDTH, 0.1F).define(PULSE, false)
            .define(SOUND, -1).define(PITCH, 1F).define(VOLUME, 1F);
    }

    public void setup(int color, int core, float width, boolean pulse) {
        entityData.set(COLOR, 0xFF000000 | color);
        entityData.set(CORE, 0xFF000000 | core);
        entityData.set(WIDTH, width);
        entityData.set(PULSE, pulse);
    }

    public void sound(int index, float pitch, float volume) {
        entityData.set(SOUND, index);
        entityData.set(PITCH, pitch);
        entityData.set(VOLUME, volume);
    }

    public int soundIndex() { return entityData.get(SOUND); }
    public float soundPitch() { return entityData.get(PITCH); }
    public float soundVolume() { return entityData.get(VOLUME); }

    /** Strahl von {@code from} nach {@code to} legen (Server). */
    public void aim(Vec3 from, Vec3 to) {
        setPos(from);
        entityData.set(TO, new Vector3f((float) to.x, (float) to.y, (float) to.z));
        idle = 0;
    }

    public Vec3 to() {
        org.joml.Vector3fc v = entityData.get(TO);
        return new Vec3(v.x(), v.y(), v.z());
    }

    public int color() { return entityData.get(COLOR); }
    public int core() { return entityData.get(CORE); }
    public float width() { return entityData.get(WIDTH); }
    public boolean pulse() { return entityData.get(PULSE); }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide()) {
            // Sound beim Client am Strahl festmachen: verstummt, sobald der Strahl endet (Loslassen)
            if (!soundStarted && soundIndex() >= 0) {
                soundStarted = true;
                de.simon.spacemod.client.BeamSoundPlayer.play(this);
            }
        } else if (++idle > 5) discard();
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        return false;
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {}

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {}

    @Override
    public boolean shouldBeSaved() {
        return false;
    }
}

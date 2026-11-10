package de.simon.spacemod.entity;

import de.simon.spacemod.weapons.PhaserControl;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Photonen- bzw. Quantentorpedo: leuchtende Kugel, fliegt auf das Ziel zu (folgt einer anvisierten
 * Kreatur) und explodiert beim Aufprall. Das eigene Schiff durchfliegt sie und beschädigt sie nie.
 */
public class TorpedoEntity extends Entity {
    private TorpedoStyle style = TorpedoStyle.PHOTON_TNG_D;
    private Vec3 target = Vec3.ZERO;
    private @Nullable Entity targetEntity;
    private AABB own = new AABB(0, 0, 0, 0, 0, 0);
    private @Nullable UUID shooter;
    private int age;

    public TorpedoEntity(EntityType<?> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    public void setup(TorpedoStyle style, Vec3 target, @Nullable Entity targetEntity, AABB own, UUID shooter) {
        this.style = style;
        this.target = target;
        this.targetEntity = targetEntity;
        this.own = own;
        this.shooter = shooter;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {}

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        return false;
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {}

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {}

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel level)) return;
        if (++age > 400) {
            detonate(level, position());
            return;
        }
        if (targetEntity != null && targetEntity.isAlive()) target = targetEntity.getBoundingBox().getCenter();
        Vec3 pos = position();
        Vec3 to = target.subtract(pos);
        double dist = to.length();
        double speed = style.speed;
        Vec3 dir = dist < 1e-4 ? Vec3.ZERO : to.scale(1 / dist);
        double step = Math.min(speed, dist);
        // Weg in kleinen Schritten prüfen: Blöcke außerhalb des eigenen Schiffs und Kreaturen
        for (double t = 0.25; t <= step; t += 0.25) {
            Vec3 v = pos.add(dir.scale(t));
            BlockPos bp = BlockPos.containing(v);
            BlockState st = level.getBlockState(bp);
            if (!st.isAir() && !own.contains(v) && !st.getCollisionShape(level, bp).isEmpty()) {
                detonate(level, v);
                return;
            }
        }
        Vec3 next = pos.add(dir.scale(step));
        AABB swept = new AABB(pos, next).inflate(0.6);
        for (Entity e : level.getEntities(this, swept, e -> e instanceof LivingEntity le && le.isAlive()
            && !e.getUUID().equals(shooter) && !own.contains(e.position()))) {
            if (e.getBoundingBox().inflate(0.5).clip(pos, next).isPresent()) {
                detonate(level, e.getBoundingBox().getCenter());
                return;
            }
        }
        trail(level, pos, next);
        setPos(next.x, next.y, next.z);
        if (dist <= speed) detonate(level, next);
    }

    /** Leuchtspur je nach Epoche. */
    private void trail(ServerLevel level, Vec3 from, Vec3 to) {
        float size = style.size;
        if (style == TorpedoStyle.PHOTON_TOS) size *= (age % 4 < 2) ? 1.25F : 0.75F; // Originalserie: pulsierend
        DustParticleOptions glow = new DustParticleOptions(style.glow, Math.min(4F, size));
        DustParticleOptions core = new DustParticleOptions(style.core, Math.min(4F, size * 0.5F));
        Vec3 d = to.subtract(from);
        int n = 4;
        for (int i = 0; i < n; i++) {
            Vec3 v = from.add(d.scale(i / (double) n));
            level.sendParticles(glow, true, true, v.x, v.y, v.z, 2, 0.1 * size / 3, 0.1 * size / 3, 0.1 * size / 3, 0);
            level.sendParticles(core, true, true, v.x, v.y, v.z, 1, 0, 0, 0, 0);
        }
        switch (style) {
            case PHOTON_TOS -> {} // reiner Lichtball ohne Schweif
            case PHOTON_MOVIE_A, PHOTON_MOVIE_B -> {
                // Strahlenkranz: kurze Zacken in alle Richtungen
                DustParticleOptions ray = new DustParticleOptions(style.core, 0.8F);
                for (int i = 0; i < 6; i++) {
                    double a = random.nextDouble() * Math.PI * 2, b = random.nextDouble() * Math.PI - Math.PI / 2;
                    for (double k = 0.4; k <= 1.2; k += 0.4)
                        level.sendParticles(ray, true, true, to.x + Math.cos(a) * Math.cos(b) * k, to.y + Math.sin(b) * k,
                            to.z + Math.sin(a) * Math.cos(b) * k, 1, 0, 0, 0, 0);
                }
            }
            case QUANTUM -> {
                level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, true, true, from.x, from.y, from.z, 2, 0.1, 0.1, 0.1, 0.01);
                level.sendParticles(ParticleTypes.END_ROD, true, true, to.x, to.y, to.z, 2, 0.3, 0.3, 0.3, 0.02);
            }
            default -> {
                // Leuchtschweif, bei TNG/E länger
                DustParticleOptions tail = new DustParticleOptions(style.glow, Math.min(4F, size * 0.6F));
                int len = style == TorpedoStyle.PHOTONIC_NX ? 1 : 3;
                for (int i = 1; i <= len; i++) {
                    Vec3 v = from.subtract(d.scale(i * 0.5));
                    level.sendParticles(tail, true, true, v.x, v.y, v.z, 1, 0.05, 0.05, 0.05, 0);
                }
                level.sendParticles(ParticleTypes.FLAME, true, true, from.x, from.y, from.z, 1, 0.05, 0.05, 0.05, 0.01);
            }
        }
    }

    private void detonate(ServerLevel level, Vec3 at) {
        discard();
        level.explode(this, null, PhaserControl.spare(own), at.x, at.y, at.z, style.radius, false, Level.ExplosionInteraction.TNT);
        if (style == TorpedoStyle.QUANTUM) level.sendParticles(ParticleTypes.SONIC_BOOM, true, true, at.x, at.y, at.z, 1, 0, 0, 0, 0);
    }
}

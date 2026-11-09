package de.simon.spacemod.entity;

import de.simon.spacemod.block.ChairBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

/**
 * Unsichtbarer Sitz auf einem Stuhl: der Spieler reitet darauf. Verschwindet, sobald niemand mehr
 * sitzt oder der Stuhl abgebaut wurde.
 */
public class SeatEntity extends Entity {
    public SeatEntity(EntityType<?> type, Level level) {
        super(type, level);
        this.noPhysics = true;
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
        if (level().isClientSide()) return;
        if (getPassengers().isEmpty() || !ChairBlock.isSeat(level().getBlockState(blockPosition()))) discard();
    }

    /** Aufstehen: vor den Stuhl (in Blickrichtung), sonst oben drauf. */
    @Override
    public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
        BlockPos chair = blockPosition();
        BlockPos front = chair.relative(Direction.fromYRot(getYRot()));
        Level level = level();
        if (level.getBlockState(front).getCollisionShape(level, front).isEmpty()
            && level.getBlockState(front.above()).getCollisionShape(level, front.above()).isEmpty())
            return Vec3.atBottomCenterOf(front);
        return super.getDismountLocationForPassenger(passenger);
    }
}

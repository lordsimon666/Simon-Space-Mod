package de.simon.spacemod.defense;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/** Kurzes Aufleuchten des Schildes um einen Treffer (1,5 s); die Zellen kommen per {@link ShieldFlashMessage}. */
public class ShieldFlashEntity extends Entity {
    public static final int LIFE = 30;
    /** Client: Zellen (gepackt, siehe {@link Shields}). */
    public int[] cells;
    /** Client: Drehung (bei fliegenden Schiffen), x y z w. */
    public float[] rot = {0, 0, 0, 1};

    public ShieldFlashEntity(EntityType<?> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide()) {
            if (cells == null) {
                ShieldFlashMessage m = ShieldFlashMessage.take(getId());
                if (m != null) {
                    cells = m.cells();
                    rot = m.rot();
                }
            }
        } else if (tickCount > LIFE) discard();
    }

    @Override
    protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder builder) {}

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

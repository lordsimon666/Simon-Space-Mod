package de.simon.spacemod.power;

import de.simon.spacemod.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.IEnergyStorage;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.Nullable;

/**
 * Batterie: speichert 400 000 FE. Gibt Strom ans Bordnetz und an angrenzende Maschinen ab, wird von
 * angrenzenden Erzeugern (z. B. Reaktor) und aus überschüssiger Warpkernleistung geladen. Die Ladung
 * bleibt beim Abbauen im Item erhalten.
 */
public class BatteryBlockEntity extends BlockEntity {
    public static final int CAPACITY = 400_000;
    public static final int MAX_IO = 4_000;

    private int energy;

    private final IEnergyStorage storage = new IEnergyStorage() {
        @Override
        public int receiveEnergy(int max, boolean simulate) {
            int add = Math.max(0, Math.min(max, Math.min(MAX_IO, CAPACITY - energy)));
            if (!simulate && add > 0) set(energy + add);
            return add;
        }

        @Override
        public int extractEnergy(int max, boolean simulate) {
            int got = Math.max(0, Math.min(max, Math.min(MAX_IO, energy)));
            if (!simulate && got > 0) set(energy - got);
            return got;
        }

        @Override public int getEnergyStored() { return energy; }
        @Override public int getMaxEnergyStored() { return CAPACITY; }
        @Override public boolean canExtract() { return true; }
        @Override public boolean canReceive() { return true; }
    };
    private LazyOptional<IEnergyStorage> cap = LazyOptional.of(() -> storage);

    public BatteryBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.BATTERY_BE.get(), pos, state);
    }

    public int energy() {
        return energy;
    }

    public void set(int value) {
        energy = Math.max(0, Math.min(CAPACITY, value));
        setChanged();
        if (level != null && !level.isClientSide()) {
            BlockState st = getBlockState();
            int level5 = BatteryBlock.charge(energy);
            if (st.hasProperty(BatteryBlock.CHARGE) && st.getValue(BatteryBlock.CHARGE) != level5)
                level.setBlock(worldPosition, st.setValue(BatteryBlock.CHARGE, level5), 2);
        }
    }

    /** Strom an angrenzende Verbraucher abgeben (keine anderen Speicher). */
    public static void serverTick(Level level, BlockPos pos, BlockState state, BatteryBlockEntity be) {
        if (be.energy <= 0) return;
        for (Direction d : Direction.values()) {
            BlockEntity n = level.getBlockEntity(pos.relative(d));
            if (n == null || n instanceof BatteryBlockEntity) continue;
            n.getCapability(ForgeCapabilities.ENERGY, d.getOpposite()).ifPresent(e -> {
                if (e.canReceive() && !e.canExtract()) {
                    int moved = e.receiveEnergy(Math.min(be.energy, MAX_IO), false);
                    if (moved > 0) be.set(be.energy - moved);
                }
            });
            if (be.energy <= 0) return;
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        energy = input.getIntOr("energy", 0);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putInt("energy", energy);
    }

    @Override
    protected void applyImplicitComponents(DataComponentGetter components) {
        super.applyImplicitComponents(components);
        energy = components.getOrDefault(ModBlocks.WEAPON_ENERGY.get(), 0);
    }

    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder components) {
        super.collectImplicitComponents(components);
        components.set(ModBlocks.WEAPON_ENERGY.get(), energy);
    }

    @Override
    public <T> @NotNull LazyOptional<T> getCapability(@NotNull Capability<T> c, @Nullable Direction side) {
        return c == ForgeCapabilities.ENERGY ? cap.cast() : super.getCapability(c, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        cap.invalidate();
    }

    @Override
    public void reviveCaps() {
        super.reviveCaps();
        cap = LazyOptional.of(() -> storage);
    }
}

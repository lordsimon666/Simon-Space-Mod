package de.simon.spacemod.block.entity;

import de.simon.spacemod.ModBlocks;
import de.simon.spacemod.item.HandWeaponItem;
import de.simon.spacemod.power.ShipPower;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
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
 * Ladestation der Waffenkammer: bezieht Strom aus dem Bordnetz (Warpkern über Hülle und Wände)
 * und nimmt RF auch von Kabeln anderer Mods an. Lädt die eingesetzte Waffe.
 */
public class ChargingStationBlockEntity extends WeaponHolderBlockEntity {
    public static final int CAPACITY = 200_000;

    private final EnergyStorage energy = new EnergyStorage(CAPACITY, 10_000, 0) {
        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            int r = super.receiveEnergy(maxReceive, simulate);
            if (r > 0 && !simulate) setChanged();
            return r;
        }
    };
    private LazyOptional<IEnergyStorage> energyCap = LazyOptional.of(() -> energy);
    /** Zuletzt gemessene Leistung des Bordnetzes (RF/t), für die Anzeige. */
    private int gridOutput;

    public ChargingStationBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.CHARGING_STATION_BE.get(), pos, state, 1);
    }

    /** Alles mit Forge-Energie, das Strom annimmt, darf auf die Station. */
    @Override
    public boolean accepts(ItemStack stack) {
        return stack.getCapability(ForgeCapabilities.ENERGY).map(IEnergyStorage::canReceive).orElse(false);
    }

    public int stored() {
        return energy.getEnergyStored();
    }

    public int gridOutput() {
        return gridOutput;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, ChargingStationBlockEntity be) {
        if (!(level instanceof ServerLevel server)) return;
        // Strom aus dem Bordnetz
        ShipPower.Grid grid = ShipPower.grid(server, pos);
        be.gridOutput = grid.output();
        int need = CAPACITY - be.energy.getEnergyStored();
        if (need > 0 && grid.output() > 0) {
            int got = ShipPower.draw(server, grid, need);
            if (got > 0) {
                be.energy.receiveEnergy(got, false);
            }
        }
        // Waffe (oder jedes andere Gerät mit Forge-Energie) laden
        ItemStack stack = be.items.get(0);
        if (!stack.isEmpty() && be.energy.getEnergyStored() > 0) {
            int moved = stack.getCapability(ForgeCapabilities.ENERGY)
                .map(e -> e.receiveEnergy(Math.min(be.energy.getEnergyStored(), 2000), false)).orElse(0);
            if (moved > 0) {
                be.energy.extractEnergyInternal(moved);
                if (level.getGameTime() % 10 == 0) be.changed(); // Ladestand für die Anzeige
                else be.setChanged();
            }
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        energy.setStored(input.getIntOr("energy", 0));
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putInt("energy", energy.getEnergyStored());
    }

    @Override
    public <T> @NotNull LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        if (cap == ForgeCapabilities.ENERGY) return energyCap.cast();
        return super.getCapability(cap, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        energyCap.invalidate();
    }

    @Override
    public void reviveCaps() {
        super.reviveCaps();
        energyCap = LazyOptional.of(() -> energy);
    }

    /** Energiespeicher mit Zugriff zum Entnehmen und Setzen (nach außen nur Annehmen). */
    private static class EnergyStorage extends net.minecraftforge.energy.EnergyStorage {
        EnergyStorage(int capacity, int maxReceive, int maxExtract) {
            super(capacity, maxReceive, maxExtract);
        }

        void extractEnergyInternal(int amount) {
            energy = Math.max(0, energy - amount);
        }

        void setStored(int value) {
            energy = Math.max(0, Math.min(capacity, value));
        }
    }
}

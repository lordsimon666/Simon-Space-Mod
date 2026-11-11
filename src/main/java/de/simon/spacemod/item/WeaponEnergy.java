package de.simon.spacemod.item;

import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.IEnergyStorage;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.Nullable;

/**
 * Energiezelle einer Handwaffe über die Standard-Energieschnittstelle von Forge (FE/RF): so können
 * auch Ladegeräte anderer Mods die Phaser und Gewehre laden. Der Ladestand liegt am Item selbst.
 */
public class WeaponEnergy implements ICapabilityProvider, IEnergyStorage {
    private final ItemStack stack;
    private final HandWeaponItem weapon;
    private final LazyOptional<IEnergyStorage> holder = LazyOptional.of(() -> this);

    public WeaponEnergy(ItemStack stack, HandWeaponItem weapon) {
        this.stack = stack;
        this.weapon = weapon;
    }

    @Override
    public <T> @NotNull LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        return cap == ForgeCapabilities.ENERGY ? holder.cast() : LazyOptional.empty();
    }

    @Override
    public int receiveEnergy(int maxReceive, boolean simulate) {
        int e = HandWeaponItem.energy(stack);
        int add = Math.max(0, Math.min(maxReceive, Math.min(weapon.chargeRate(), weapon.capacity() - e)));
        if (!simulate && add > 0) HandWeaponItem.setEnergy(stack, e + add);
        return add;
    }

    @Override
    public int extractEnergy(int maxExtract, boolean simulate) {
        return 0; // Waffen geben keinen Strom ab
    }

    @Override
    public int getEnergyStored() {
        return HandWeaponItem.energy(stack);
    }

    @Override
    public int getMaxEnergyStored() {
        return weapon.capacity();
    }

    @Override
    public boolean canExtract() {
        return false;
    }

    @Override
    public boolean canReceive() {
        return true;
    }
}

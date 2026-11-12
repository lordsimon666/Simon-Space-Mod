package de.simon.spacemod.assembly;

import de.simon.spacemod.ModBlocks;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.items.wrapper.InvWrapper;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.Nullable;

/** Schiffsteil-Fertigung: Plätze 0-8 Zutaten, 9 Ergebnis; braucht Strom (Bordnetz/Hülle oder Kabel). */
public class AssemblyBlockEntity extends BlockEntity {
    public static final int CAPACITY = 200_000;
    public static final int MAX_RECEIVE = 4_000;
    public static final int OUTPUT = 9;

    private int selected = -1;
    private int progress;
    private int energy;

    public final SimpleContainer inventory = new SimpleContainer(10) {
        @Override
        public boolean canPlaceItem(int slot, ItemStack stack) {
            return slot != OUTPUT;
        }

        @Override
        public void setChanged() {
            super.setChanged();
            AssemblyBlockEntity.this.setChanged();
        }
    };

    /** Anzeige: gewähltes Rezept, Fortschritt, Dauer, Energie / 10, Strom vorhanden. Index 0 ist setzbar (Auswahl). */
    public final ContainerData data = new ContainerData() {
        @Override
        public int get(int i) {
            AssemblyRecipes.Recipe r = AssemblyRecipes.get(selected);
            return switch (i) {
                case 0 -> selected;
                case 1 -> progress;
                case 2 -> r == null ? 0 : r.ticks();
                case 3 -> energy / 10;
                default -> energy >= AssemblyRecipes.FE_PER_TICK ? 1 : 0;
            };
        }

        @Override
        public void set(int i, int v) {
            if (i == 0 && v != selected && (v == -1 || AssemblyRecipes.get(v) != null)) {
                selected = v;
                progress = 0;
                setChanged();
            }
        }

        @Override
        public int getCount() {
            return 5;
        }
    };

    private final IEnergyStorage energyStorage = new IEnergyStorage() {
        @Override
        public int receiveEnergy(int max, boolean simulate) {
            int add = Math.max(0, Math.min(max, Math.min(MAX_RECEIVE, CAPACITY - energy)));
            if (!simulate && add > 0) { energy += add; setChanged(); }
            return add;
        }
        @Override public int extractEnergy(int max, boolean simulate) { return 0; }
        @Override public int getEnergyStored() { return energy; }
        @Override public int getMaxEnergyStored() { return CAPACITY; }
        @Override public boolean canExtract() { return false; }
        @Override public boolean canReceive() { return true; }
    };
    private LazyOptional<IEnergyStorage> energyCap = LazyOptional.of(() -> energyStorage);
    private LazyOptional<InvWrapper> itemCap = LazyOptional.of(() -> new InvWrapper(inventory));

    public AssemblyBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.ASSEMBLY_BE.get(), pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, AssemblyBlockEntity be) {
        AssemblyRecipes.Recipe r = AssemblyRecipes.get(be.selected);
        boolean ready = r != null && AssemblyRecipes.matches(r, be.inventory::getItem) && be.fits(r);
        if (!ready) {
            if (be.progress != 0) { be.progress = 0; be.setChanged(); }
            set(level, pos, state, false);
            return;
        }
        if (be.energy < AssemblyRecipes.FE_PER_TICK) { set(level, pos, state, false); return; }
        be.energy -= AssemblyRecipes.FE_PER_TICK;
        set(level, pos, state, true);
        if (++be.progress >= r.ticks()) {
            be.progress = 0;
            for (int i = 0; i < 9; i++) if (!r.inputs()[i].isEmpty()) be.inventory.removeItem(i, r.inputs()[i].getCount());
            ItemStack out = be.inventory.getItem(OUTPUT);
            if (out.isEmpty()) be.inventory.setItem(OUTPUT, new ItemStack(r.output(), r.count()));
            else out.grow(r.count());
            be.inventory.setChanged();
        }
        be.setChanged();
    }

    private static void set(Level level, BlockPos pos, BlockState state, boolean lit) {
        if (state.getValue(AssemblyBlock.LIT) != lit) level.setBlock(pos, state.setValue(AssemblyBlock.LIT, lit), 2);
    }

    private boolean fits(AssemblyRecipes.Recipe r) {
        ItemStack out = inventory.getItem(OUTPUT);
        return out.isEmpty() || out.is(r.output()) && out.getCount() + r.count() <= out.getMaxStackSize();
    }

    public void openMenu(ServerPlayer player, Predicate<Player> stillValid) {
        player.openMenu(new SimpleMenuProvider((id, inv, p) -> new AssemblyMenu(id, inv, inventory, data, stillValid),
            Component.translatable("block.simonspacemod.ship_part_assembly")));
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        selected = input.getIntOr("selected", -1);
        progress = input.getIntOr("progress", 0);
        energy = input.getIntOr("energy", 0);
        NonNullList<ItemStack> items = NonNullList.withSize(10, ItemStack.EMPTY);
        net.minecraft.world.ContainerHelper.loadAllItems(input, items);
        for (int i = 0; i < 10; i++) inventory.setItem(i, items.get(i));
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putInt("selected", selected);
        output.putInt("progress", progress);
        output.putInt("energy", energy);
        NonNullList<ItemStack> items = NonNullList.withSize(10, ItemStack.EMPTY);
        for (int i = 0; i < 10; i++) items.set(i, inventory.getItem(i));
        net.minecraft.world.ContainerHelper.saveAllItems(output, items, true);
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        if (level != null) Containers.dropContents(level, pos, inventory);
    }

    @Override
    public <T> @NotNull LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        if (cap == ForgeCapabilities.ENERGY) return energyCap.cast();
        if (cap == ForgeCapabilities.ITEM_HANDLER) return itemCap.cast();
        return super.getCapability(cap, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        energyCap.invalidate();
        itemCap.invalidate();
    }

    @Override
    public void reviveCaps() {
        super.reviveCaps();
        energyCap = LazyOptional.of(() -> energyStorage);
        itemCap = LazyOptional.of(() -> new InvWrapper(inventory));
    }
}

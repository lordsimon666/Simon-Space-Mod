package de.simon.spacemod.block.entity;

import de.simon.spacemod.ModBlocks;
import de.simon.spacemod.block.ReactorControllerBlock;
import de.simon.spacemod.reactor.ReactorMath;
import de.simon.spacemod.reactor.ReactorMenu;
import de.simon.spacemod.reactor.ReactorStructure;
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
import net.minecraft.world.level.block.Block;
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

/**
 * Herz des Reaktors: prüft den Würfel (3 bis 8 Blöcke), verbrennt Uranbarren aus den Eingabeplätzen
 * (1 bis 9 je nach Größe) und erzeugt bis zu 10 000 FE/t. Gibt Strom an angrenzende Verbraucher ab,
 * andere Mods können ihn abziehen und mit Trichtern/Rohren Uran nachfüllen. Läuft auch im Flug.
 */
public class ReactorControllerBlockEntity extends BlockEntity {
    public static final int CAPACITY = 1_000_000;

    private int size;
    private int burnLeft, burnMax;
    private int currentOutput;
    private int energy;

    public final SimpleContainer inventory = new SimpleContainer(9) {
        @Override
        public boolean canPlaceItem(int slot, ItemStack stack) {
            return slot < slots() && stack.is(ModBlocks.URANIUM_INGOT.get());
        }

        @Override
        public void setChanged() {
            super.setChanged();
            ReactorControllerBlockEntity.this.setChanged();
        }
    };

    /** Anzeige: Größe, Leistung, Restbrenndauer, Brenndauer je Barren, Energie (in 100 FE), Plätze. */
    public final ContainerData data = new ContainerData() {
        @Override
        public int get(int i) {
            return switch (i) {
                case 0 -> size;
                case 1 -> currentOutput;
                case 2 -> burnLeft;
                case 3 -> burnMax;
                case 4 -> energy / 100; // Menüdaten sind 16 Bit: in 100 FE
                default -> slots();
            };
        }

        @Override
        public void set(int i, int v) {}

        @Override
        public int getCount() {
            return 6;
        }
    };

    private final IEnergyStorage energyStorage = new IEnergyStorage() {
        @Override public int receiveEnergy(int max, boolean simulate) { return 0; }
        @Override public int extractEnergy(int max, boolean simulate) { return extract(max, simulate); }
        @Override public int getEnergyStored() { return energy; }
        @Override public int getMaxEnergyStored() { return CAPACITY; }
        @Override public boolean canExtract() { return true; }
        @Override public boolean canReceive() { return false; }
    };
    private LazyOptional<IEnergyStorage> energyCap = LazyOptional.of(() -> energyStorage);
    private LazyOptional<InvWrapper> itemCap = LazyOptional.of(() -> new InvWrapper(inventory));

    public ReactorControllerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.REACTOR_BE.get(), pos, state);
    }

    public int size() { return size; }
    public int slots() { return ReactorMath.slots(size); }
    public int currentOutput() { return currentOutput; }
    public int energy() { return energy; }

    /** Strom entnehmen (Bordnetz, Antriebe, andere Mods). */
    public int extract(int max, boolean simulate) {
        int got = Math.min(max, energy);
        if (!simulate && got > 0) { energy -= got; setChanged(); }
        return got;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, ReactorControllerBlockEntity be) {
        if (level.getGameTime() % 40 == Math.floorMod(pos.asLong(), 40)) {
            int n = ReactorStructure.find(level, pos, state.getValue(ReactorControllerBlock.FACING));
            if (n != be.size) { be.size = n; be.setChanged(); }
            if (level instanceof net.minecraft.server.level.ServerLevel sl) de.simon.spacemod.power.PowerNetwork.addSeed(sl, pos);
        }
        be.run();
        // an angrenzende Verbraucher abgeben
        if (be.energy > 0)
            for (Direction d : Direction.values()) {
                BlockEntity n = level.getBlockEntity(pos.relative(d));
                if (n == null || n instanceof ReactorControllerBlockEntity) continue;
                n.getCapability(ForgeCapabilities.ENERGY, d.getOpposite()).ifPresent(e -> {
                    if (e.canReceive()) be.extract(e.receiveEnergy(Math.min(be.energy, ReactorMath.MAX_OUTPUT), false), false);
                });
            }
        boolean lit = be.currentOutput > 0;
        if (state.getValue(ReactorControllerBlock.LIT) != lit) level.setBlock(pos, state.setValue(ReactorControllerBlock.LIT, lit), Block.UPDATE_CLIENTS);
    }

    /** Ein Tick Kernspaltung (auch im Flug, dort mit der beim Abheben bekannten Größe). */
    public void run() {
        currentOutput = 0;
        int out = ReactorMath.output(size);
        if (out <= 0 || energy + out > CAPACITY) return; // ungültig oder Speicher voll: Pause
        if (burnLeft <= 0) {
            for (int i = 0; i < slots(); i++) {
                ItemStack s = inventory.getItem(i);
                if (s.is(ModBlocks.URANIUM_INGOT.get())) {
                    s.shrink(1);
                    inventory.setChanged();
                    burnLeft = burnMax = ReactorMath.burnTicks(size);
                    break;
                }
            }
        }
        if (burnLeft <= 0) return;
        burnLeft--;
        energy += out;
        currentOutput = out;
        setChanged();
    }

    public void openMenu(ServerPlayer player, Predicate<Player> stillValid) {
        int slots = slots();
        player.openMenu(new SimpleMenuProvider((id, inv, p) -> new ReactorMenu(id, inv, inventory, data, slots, stillValid),
            Component.translatable("gui.simonspacemod.reactor")), buf -> buf.writeVarInt(slots));
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        size = input.getIntOr("size", 0);
        burnLeft = input.getIntOr("burn", 0);
        burnMax = input.getIntOr("burnMax", 0);
        energy = input.getIntOr("energy", 0);
        NonNullList<ItemStack> items = NonNullList.withSize(9, ItemStack.EMPTY);
        net.minecraft.world.ContainerHelper.loadAllItems(input, items);
        for (int i = 0; i < 9; i++) inventory.setItem(i, items.get(i));
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putInt("size", size);
        output.putInt("burn", burnLeft);
        output.putInt("burnMax", burnMax);
        output.putInt("energy", energy);
        NonNullList<ItemStack> items = NonNullList.withSize(9, ItemStack.EMPTY);
        for (int i = 0; i < 9; i++) items.set(i, inventory.getItem(i));
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

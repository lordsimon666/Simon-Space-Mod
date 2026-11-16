package de.simon.spacemod.mining;

import de.simon.spacemod.ModBlocks;
import de.simon.spacemod.SimonSpaceMod;
import de.simon.spacemod.entity.FlyingShipEntity;
import de.simon.spacemod.flight.ShipPhysics;
import de.simon.spacemod.flight.Shipyard;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.wrapper.InvWrapper;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.Nullable;
import org.joml.Quaternionf;

/**
 * Bergbaulaser: baut einen einstellbaren Bereich (bis 16 × 16 Blöcke, bis 32 tief) unter sich ab, Schicht
 * für Schicht von oben. Zeigt immer nach unten – auf einem fliegenden Schiff nach „unten“ im Schiff,
 * dreht sich also mit. Die Ausbeute landet in 9 Plätzen und wird an angrenzende Inventare (z. B. die
 * Großkiste) weitergegeben. Braucht Strom (vom Bordnetz oder angrenzenden Erzeugern).
 */
public class MiningLaserBlockEntity extends BlockEntity {
    public static final int CAPACITY = 100_000, MAX_IN = 4_000, FE_PER_BLOCK = 250, INTERVAL = 2, CHECKS_PER_TICK = 128;
    public static final int OK = 0, NO_POWER = 1, FULL = 2, DONE = 3, OFF = 4;

    private final SimpleContainer inv = new SimpleContainer(9) {
        @Override
        public void setChanged() {
            super.setChanged();
            MiningLaserBlockEntity.this.setChanged();
        }
    };
    private int energy, size = 5, depth = 16, cursor, cooldown, status = OK, emptyPasses;
    private boolean on = true;

    private final IEnergyStorage storage = new IEnergyStorage() {
        @Override public int receiveEnergy(int max, boolean simulate) {
            int add = Math.max(0, Math.min(max, Math.min(MAX_IN, CAPACITY - energy)));
            if (!simulate && add > 0) { energy += add; setChanged(); }
            return add;
        }
        @Override public int extractEnergy(int max, boolean simulate) { return 0; }
        @Override public int getEnergyStored() { return energy; }
        @Override public int getMaxEnergyStored() { return CAPACITY; }
        @Override public boolean canExtract() { return false; }
        @Override public boolean canReceive() { return true; }
    };
    private LazyOptional<IEnergyStorage> energyCap = LazyOptional.of(() -> storage);
    private LazyOptional<IItemHandler> itemCap = LazyOptional.of(() -> new InvWrapper(inv));

    public final ContainerData data = new ContainerData() {
        @Override public int get(int i) {
            return switch (i) {
                case 0 -> size;
                case 1 -> depth;
                case 2 -> on ? 1 : 0;
                case 3 -> energy / 50;
                case 4 -> status;
                default -> Math.min(depth, cursor / Math.max(1, size * size) + 1);
            };
        }
        @Override public void set(int i, int v) {}
        @Override public int getCount() { return 6; }
    };

    public MiningLaserBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.MINING_LASER_BE.get(), pos, state);
    }

    /** Knöpfe im Menü: 0/1 Größe −/+, 2/3 Tiefe −/+, 4 an/aus; +10 = in Fünferschritten. */
    public void button(int id) {
        int step = id >= 10 ? 5 : 1;
        switch (id % 10) {
            case 0 -> size = Math.max(1, size - step);
            case 1 -> size = Math.min(MiningArea.MAX_SIZE, size + step);
            case 2 -> depth = Math.max(1, depth - step);
            case 3 -> depth = Math.min(MiningArea.MAX_DEPTH, depth + step);
            case 4 -> on = !on;
            default -> { return; }
        }
        cursor = 0;
        emptyPasses = 0;
        setChanged();
    }

    public void openMenu(ServerPlayer player) {
        player.openMenu(new SimpleMenuProvider((id, pinv, p) -> new MiningLaserMenu(id, pinv, inv, data, this,
            pl -> !isRemoved() && Shipyard.distanceSqr(pl, level, worldPosition) < 64), Component.translatable("block.simonspacemod.mining_laser")));
    }

    /** Weltlage eines Blocks im Bereich (auf einem fliegenden Schiff: gedreht mit dem Schiff). */
    private record Spot(ServerLevel level, BlockPos pos, Vec3 from) {}

    private @Nullable Spot spot(ServerLevel here, int[] o) {
        if (Shipyard.is(here)) {
            FlyingShipEntity ship = Shipyard.shipAt(worldPosition);
            if (ship == null || !(ship.level() instanceof ServerLevel wl)) return null;
            BlockPos l = worldPosition.subtract(ship.plotOrigin());
            Quaternionf q = ship.rotation(1F);
            Vec3 c = ShipPhysics.rotate(q, l.getX() + o[0], l.getY() - 1 - o[1], l.getZ() + o[2]).add(ship.position());
            Vec3 from = ShipPhysics.rotate(q, l.getX(), l.getY() - 0.55, l.getZ()).add(ship.position());
            return new Spot(wl, BlockPos.containing(c), from);
        }
        return new Spot(here, worldPosition.offset(o[0], -1 - o[1], o[2]), Vec3.atBottomCenterOf(worldPosition));
    }

    private static boolean mineable(ServerLevel wl, BlockPos p) {
        if (!wl.isLoaded(p) || wl.isOutsideBuildHeight(p)) return false;
        BlockState st = wl.getBlockState(p);
        if (st.isAir() || st.getBlock() instanceof LiquidBlock) return false;
        if (st.getDestroySpeed(wl, p) < 0 || wl.getBlockEntity(p) != null) return false; // Grundgestein, Kisten, Maschinen
        return !BuiltInRegistries.BLOCK.getKey(st.getBlock()).getNamespace().equals(SimonSpaceMod.MODID); // keine Schiffsblöcke
    }

    private boolean fits(List<ItemStack> drops) {
        NonNullList<ItemStack> copy = NonNullList.withSize(9, ItemStack.EMPTY);
        for (int i = 0; i < 9; i++) copy.set(i, inv.getItem(i).copy());
        for (ItemStack d : drops) {
            ItemStack s = d.copy();
            for (int i = 0; i < 9 && !s.isEmpty(); i++) {
                ItemStack c = copy.get(i);
                if (c.isEmpty()) { copy.set(i, s.copy()); s.setCount(0); }
                else if (ItemStack.isSameItemSameComponents(c, s)) {
                    int n = Math.min(s.getCount(), c.getMaxStackSize() - c.getCount());
                    c.grow(n); s.shrink(n);
                }
            }
            if (!s.isEmpty()) return false;
        }
        return true;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, MiningLaserBlockEntity be) {
        if (!(level instanceof ServerLevel here)) return;
        if (level.getGameTime() % 10 == 0) be.pushOut(here);
        if (!be.on) { be.status = OFF; return; }
        if (be.cooldown > 0) { be.cooldown--; return; }
        if (be.energy < FE_PER_BLOCK) { be.status = NO_POWER; return; }
        int total = MiningArea.count(be.size, be.depth);
        for (int n = 0; n < CHECKS_PER_TICK; n++) {
            if (be.cursor >= total) { // Durchgang fertig: von vorn (Schiff kann weitergeflogen sein)
                be.cursor = 0;
                if (++be.emptyPasses > 1) { be.status = DONE; be.cooldown = 40; return; }
            }
            Spot s = be.spot(here, MiningArea.offset(be.cursor, be.size));
            if (s == null) { be.status = DONE; be.cooldown = 20; return; }
            if (!mineable(s.level(), s.pos())) { be.cursor++; continue; }
            BlockState st = s.level().getBlockState(s.pos());
            List<ItemStack> drops = Block.getDrops(st, s.level(), s.pos(), null, null, new ItemStack(Items.DIAMOND_PICKAXE));
            if (!be.fits(drops)) { be.status = FULL; be.cooldown = 20; return; }
            s.level().destroyBlock(s.pos(), false);
            for (ItemStack d : drops) {
                ItemStack rest = be.inv.addItem(d);
                if (!rest.isEmpty()) Containers.dropItemStack(s.level(), s.pos().getX() + 0.5, s.pos().getY() + 0.5, s.pos().getZ() + 0.5, rest);
            }
            be.energy -= FE_PER_BLOCK;
            be.cursor++;
            be.emptyPasses = 0;
            be.status = OK;
            be.cooldown = INTERVAL;
            beam(s);
            be.setChanged();
            return;
        }
    }

    /** Sichtbarer Strahl vom Laser zum Block. */
    private static void beam(Spot s) {
        Vec3 to = Vec3.atCenterOf(s.pos()), d = to.subtract(s.from());
        int steps = Math.min(48, (int) Math.ceil(d.length() * 1.5));
        for (int i = 0; i <= steps; i++) {
            Vec3 p = s.from().add(d.scale(i / (double) Math.max(1, steps)));
            s.level().sendParticles(ParticleTypes.ELECTRIC_SPARK, p.x, p.y, p.z, 1, 0, 0, 0, 0);
        }
        s.level().sendParticles(ParticleTypes.FLAME, to.x, to.y, to.z, 3, 0.25, 0.25, 0.25, 0.01);
    }

    /** Ausbeute an angrenzende Inventare abgeben (z. B. eine Großkiste darüber oder daneben). */
    private void pushOut(ServerLevel here) {
        for (Direction d : Direction.values()) {
            BlockEntity n = here.getBlockEntity(worldPosition.relative(d));
            if (n == null || n instanceof MiningLaserBlockEntity) continue;
            n.getCapability(ForgeCapabilities.ITEM_HANDLER, d.getOpposite()).ifPresent(h -> {
                for (int i = 0; i < 9; i++) {
                    ItemStack s = inv.getItem(i);
                    if (s.isEmpty()) continue;
                    ItemStack rest = net.minecraftforge.items.ItemHandlerHelper.insertItemStacked(h, s.copy(), false);
                    if (rest.getCount() != s.getCount()) inv.setItem(i, rest);
                }
            });
        }
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        if (level != null) Containers.dropContents(level, pos, inv);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        energy = input.getIntOr("energy", 0);
        size = Math.max(1, Math.min(MiningArea.MAX_SIZE, input.getIntOr("size", 5)));
        depth = Math.max(1, Math.min(MiningArea.MAX_DEPTH, input.getIntOr("depth", 16)));
        cursor = input.getIntOr("cursor", 0);
        on = input.getBooleanOr("on", true);
        NonNullList<ItemStack> items = NonNullList.withSize(9, ItemStack.EMPTY);
        net.minecraft.world.ContainerHelper.loadAllItems(input, items);
        for (int i = 0; i < 9; i++) inv.setItem(i, items.get(i));
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putInt("energy", energy);
        output.putInt("size", size);
        output.putInt("depth", depth);
        output.putInt("cursor", cursor);
        output.putBoolean("on", on);
        NonNullList<ItemStack> items = NonNullList.withSize(9, ItemStack.EMPTY);
        for (int i = 0; i < 9; i++) items.set(i, inv.getItem(i));
        net.minecraft.world.ContainerHelper.saveAllItems(output, items, true);
    }

    @Override
    public <T> @NotNull LazyOptional<T> getCapability(@NotNull Capability<T> c, @Nullable Direction side) {
        if (c == ForgeCapabilities.ENERGY) return energyCap.cast();
        if (c == ForgeCapabilities.ITEM_HANDLER) return itemCap.cast();
        return super.getCapability(c, side);
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
        energyCap = LazyOptional.of(() -> storage);
        itemCap = LazyOptional.of(() -> new InvWrapper(inv));
    }

    public boolean stillValid(Player p) {
        return !isRemoved();
    }
}

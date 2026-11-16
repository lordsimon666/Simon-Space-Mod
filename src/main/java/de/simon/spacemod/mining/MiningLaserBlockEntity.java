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
    public static final int OK = 0, NO_POWER = 1, FULL = 2, DONE = 3, OFF = 4, BLOCKED = 5;
    /** So weit unter dem Laser darf ein Laserfokus sitzen. */
    public static final int FOCUS_RANGE = 5;

    private final SimpleContainer inv = new SimpleContainer(9) {
        @Override
        public void setChanged() {
            super.setChanged();
            MiningLaserBlockEntity.this.setChanged();
        }
    };
    private int energy, size = 5, depth = 16, cursor, cooldown, status = OK, emptyPasses;
    private boolean on = false; // startet ausgeschaltet
    /** Sichtbarer Strahl (dieselbe Entity wie beim Phaser) und Nummer des Blocks, auf den er zeigt. */
    private net.minecraft.world.entity.@Nullable Entity beam, beam2;
    private @Nullable BlockPos beamTarget;
    private int beamTicks;

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

    public boolean isOn() {
        return on;
    }

    /** Für die Laser-Steuerkonsole. */
    public void setOn(boolean value) {
        if (on == value) return;
        on = value;
        cursor = 0;
        emptyPasses = 0;
        setChanged();
    }

    public void openMenu(ServerPlayer player) {
        player.openMenu(new SimpleMenuProvider((id, pinv, p) -> new MiningLaserMenu(id, pinv, inv, data, this,
            pl -> !isRemoved() && Shipyard.distanceSqr(pl, level, worldPosition) < 64), Component.translatable("block.simonspacemod.mining_laser")));
    }

    /**
     * Bezugssystem des Lasers: auf einem fliegenden Schiff die Schiffskoordinaten (Blöcke in der Werft,
     * Mittelpunkt von Block p bei p), sonst die Welt (Mittelpunkt bei p + 0,5).
     */
    private record Frame(ServerLevel world, ServerLevel blocks, @Nullable FlyingShipEntity ship, Quaternionf q, BlockPos origin, BlockPos self) {
        Vec3 toWorld(double x, double y, double z) {
            return ship == null ? new Vec3(x + 0.5, y + 0.5, z + 0.5) : ShipPhysics.rotate(q, x, y, z).add(ship.position());
        }

        BlockState at(BlockPos p) {
            return blocks.getBlockState(ship == null ? p : origin.offset(p));
        }

        /** Gehört der Block zum Schiff? (fliegend: jeder Block in der Werft; sonst Blöcke der Mod außer Erzen) */
        boolean shipBlock(BlockPos p) {
            BlockState st = at(p);
            if (st.isAir()) return false;
            if (ship != null) return true;
            return !isModOre(st) && BuiltInRegistries.BLOCK.getKey(st.getBlock()).getNamespace().equals(SimonSpaceMod.MODID);
        }

        /** Keine Schiffsblöcke auf dem Weg von der Unterseite von a (Austritt des Strahls) zur Mitte von b. */
        boolean clear(BlockPos a, BlockPos b) {
            Vec3 from = new Vec3(0, -0.5, 0), d = Vec3.atLowerCornerOf(b.subtract(a)).subtract(from);
            int steps = (int) Math.ceil(d.length() * 4);
            for (int i = 1; i < steps; i++) {
                Vec3 q = from.add(d.scale(i / (double) steps));
                BlockPos p = a.offset(BlockPos.containing(q.x + 0.5, q.y + 0.5, q.z + 0.5));
                if (!p.equals(a) && !p.equals(b) && shipBlock(p)) return false;
            }
            return true;
        }
    }

    private @Nullable Frame frame(ServerLevel here) {
        if (Shipyard.is(here)) {
            FlyingShipEntity ship = Shipyard.shipAt(worldPosition);
            if (ship == null || !(ship.level() instanceof ServerLevel wl)) return null;
            return new Frame(wl, here, ship, ship.rotation(1F), ship.plotOrigin(), worldPosition.subtract(ship.plotOrigin()));
        }
        return new Frame(here, here, null, new Quaternionf(), BlockPos.ZERO, worldPosition);
    }

    /** Laserfokus bis zu 5 Blöcke unter dem Laser (dazwischen nur Luft), sonst null. */
    private static @Nullable BlockPos focus(Frame f) {
        for (int k = 1; k <= FOCUS_RANGE; k++) {
            BlockPos p = f.self().below(k);
            BlockState st = f.at(p);
            if (st.is(ModBlocks.LASER_FOCUS.get())) return p;
            if (!st.isAir()) return null;
        }
        return null;
    }

    /** Ein Block im Bereich: Lage im Bezugssystem und in der Welt. */
    private record Spot(ServerLevel level, BlockPos frame, BlockPos pos) {}

    /**
     * Abbaubereich unter {@code start}. Auf festem Boden die Blöcke direkt (Schicht für Schicht von oben).
     * Auf einem fliegenden Schiff ist der Bereich gedreht/geneigt: dann alle Weltblöcke, deren Mitte im
     * gedrehten Quader liegt – so entstehen keine Lücken und kein Block wird doppelt getroffen.
     */
    private final class Area {
        final Frame f;
        final BlockPos start;
        final int total;
        final MiningArea.@Nullable Rotated rot;

        Area(Frame f, BlockPos start) {
            this.f = f;
            this.start = start;
            rot = f.ship() == null ? null : new MiningArea.Rotated(f.q(), f.ship().position(), start, size, depth);
            total = rot == null ? MiningArea.count(size, depth) : rot.total();
        }

        /** Block Nummer i (von oben nach unten), null = liegt nicht im Bereich. */
        @Nullable Spot get(int i) {
            if (rot == null) {
                int[] o = MiningArea.offset(i, size);
                BlockPos t = start.offset(o[0], -1 - o[1], o[2]);
                return new Spot(f.world(), t, t);
            }
            MiningArea.Rotated.Cell c = rot.get(i);
            return c == null ? null : new Spot(f.world(), c.local(), c.world());
        }
    }

    private static boolean mineable(ServerLevel wl, BlockPos p) {
        if (!wl.isLoaded(p) || wl.isOutsideBuildHeight(p)) return false;
        BlockState st = wl.getBlockState(p);
        if (st.isAir() || st.getBlock() instanceof LiquidBlock) return false;
        if (st.getDestroySpeed(wl, p) < 0 || wl.getBlockEntity(p) != null) return false; // Grundgestein, Kisten, Maschinen
        if (isModOre(st)) return true; // Erze der Mod (Uran, Dilithium, Antimaterie, Deuterium-Eis)
        return !BuiltInRegistries.BLOCK.getKey(st.getBlock()).getNamespace().equals(SimonSpaceMod.MODID); // keine Schiffsblöcke
    }

    static boolean isModOre(BlockState st) {
        return st.is(ModBlocks.URANIUM_ORE.get()) || st.is(ModBlocks.DEEPSLATE_URANIUM_ORE.get()) || st.is(ModBlocks.DILITHIUM_ORE.get())
            || st.is(ModBlocks.ANTIMATTER_CLUSTER.get()) || st.is(ModBlocks.DEUTERIUM_ICE.get());
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
        Frame f = be.frame(here);
        BlockPos focus = f == null ? null : focus(f);
        be.updateBeam(f, focus);
        if (!be.on) { be.status = OFF; return; }
        if (be.cooldown > 0) { be.cooldown--; return; }
        if (f == null) { be.status = DONE; be.cooldown = 20; return; }
        // ohne Fokus: zeigt der Laser in einen Schiffsblock (Boden), geht nichts
        if (focus == null && f.shipBlock(f.self().below())) { be.status = BLOCKED; be.cooldown = 20; return; }
        if (be.energy < FE_PER_BLOCK) { be.status = NO_POWER; return; }
        BlockPos start = focus != null ? focus : f.self();
        Area area = be.new Area(f, start);
        int total = area.total;
        for (int n = 0; n < CHECKS_PER_TICK; n++) {
            if (be.cursor >= total) { // Durchgang fertig: von vorn (Schiff kann weitergeflogen sein)
                be.cursor = 0;
                if (++be.emptyPasses > 1) { be.status = DONE; be.cooldown = 40; return; }
            }
            Spot s = area.get(be.cursor);
            if (s == null || !mineable(s.level(), s.pos()) || !f.clear(start, s.frame())) { be.cursor++; continue; } // Schiffsblock im Weg
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
            be.beamTarget = s.pos();
            be.beamTicks = 8;
            Vec3 to = Vec3.atCenterOf(s.pos());
            s.level().sendParticles(ParticleTypes.FLAME, to.x, to.y, to.z, 3, 0.25, 0.25, 0.25, 0.01);
            be.setChanged();
            return;
        }
    }

    /**
     * Roter Strahl von der Linse zum zuletzt abgebauten Block, jeden Tick nachgeführt (das Schiff kann
     * sich bewegen). Mit Laserfokus: ein Strahl gerade hinunter in den Fokus und ein zweiter vom Fokus
     * zum Block. Verschwindet kurz nach dem letzten Abbau von selbst.
     */
    private void updateBeam(@Nullable Frame f, @Nullable BlockPos focus) {
        if (beamTicks > 0) beamTicks--;
        if (f == null || beamTicks <= 0 || beamTarget == null) {
            beam = drop(beam);
            beam2 = drop(beam2);
            return;
        }
        BlockPos self = f.self();
        Vec3 lens = f.toWorld(self.getX(), self.getY() - 0.55, self.getZ());
        Vec3 target = Vec3.atCenterOf(beamTarget);
        if (focus != null) {
            Vec3 in = f.toWorld(focus.getX(), focus.getY() + 0.45, focus.getZ()), out = f.toWorld(focus.getX(), focus.getY() - 0.5, focus.getZ());
            beam = aim(beam, f.world(), lens, in);
            beam2 = aim(beam2, f.world(), out, target);
        } else {
            beam = aim(beam, f.world(), lens, target);
            beam2 = drop(beam2);
        }
    }

    private static net.minecraft.world.entity.@Nullable Entity drop(net.minecraft.world.entity.@Nullable Entity e) {
        if (e != null) e.discard();
        return null;
    }

    private static net.minecraft.world.entity.@Nullable Entity aim(net.minecraft.world.entity.@Nullable Entity e, ServerLevel level, Vec3 from, Vec3 to) {
        if (e == null || e.isRemoved() || e.level() != level) {
            drop(e);
            de.simon.spacemod.entity.PhaserBeamEntity b = ModBlocks.PHASER_BEAM.get().create(level, net.minecraft.world.entity.EntitySpawnReason.TRIGGERED);
            if (b == null) return null;
            b.setup(0xFF2A1A, 0xFFD0B0, 0.07F, false);
            b.aim(from, to);
            level.addFreshEntity(b);
            return b;
        }
        ((de.simon.spacemod.entity.PhaserBeamEntity) e).aim(from, to);
        return e;
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        beam = drop(beam);
        beam2 = drop(beam2);
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
        on = input.getBooleanOr("on", false);
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

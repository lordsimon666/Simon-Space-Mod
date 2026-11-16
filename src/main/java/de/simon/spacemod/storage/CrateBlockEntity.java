package de.simon.spacemod.storage;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import de.simon.spacemod.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandlerModifiable;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.Nullable;

/**
 * Block einer Großkiste. Der Block in der unteren Ecke (kleinste x/y/z) ist der Hauptblock und hält das
 * Inventar; alle anderen verweisen (relativ, damit es auch im fliegenden Schiff passt) auf ihn. Jeder
 * Block öffnet das Menü und nimmt über Trichter/Rohre Gegenstände an und gibt sie ab.
 */
public class CrateBlockEntity extends BlockEntity {
    /** Gespeicherter Platz: Menge extra, denn mit Stack-Upgrades sind mehr als 99 Stück möglich. */
    private record SlotStack(int slot, ItemStack stack, int n) {
        static final Codec<SlotStack> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.fieldOf("Slot").forGetter(SlotStack::slot),
            ItemStack.MAP_CODEC.forGetter(SlotStack::stack),
            Codec.INT.optionalFieldOf("n", 0).forGetter(SlotStack::n)).apply(i, SlotStack::new));

        static SlotStack of(int slot, ItemStack s) {
            return new SlotStack(slot, s.copyWithCount(1), s.getCount());
        }

        ItemStack full() {
            return n > 0 ? stack.copyWithCount(n) : stack;
        }
    }

    public static final int UPGRADE_SLOTS = 4;
    /** Upgrade-Plätze (nur im Hauptblock benutzt). */
    final net.minecraft.world.SimpleContainer upgrades = new net.minecraft.world.SimpleContainer(UPGRADE_SLOTS) {
        @Override
        public void setChanged() {
            super.setChanged();
            CrateBlockEntity.this.setChanged();
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }

        @Override
        public boolean canPlaceItem(int slot, ItemStack stack) {
            return stack.getItem() instanceof StackUpgradeItem;
        }
    };

    /** Weg zum Hauptblock; null = gehört zu keiner fertigen Kiste. */
    private @Nullable BlockPos toMaster;
    // nur im Hauptblock:
    private CrateTier tier = CrateTier.WOOD;
    private int sx, sy, sz;
    private NonNullList<ItemStack> items = NonNullList.withSize(0, ItemStack.EMPTY);

    private final IItemHandlerModifiable handler = new Handler();
    private LazyOptional<IItemHandlerModifiable> cap = LazyOptional.of(() -> handler);

    public CrateBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.CRATE_BE.get(), pos, state);
    }

    public boolean formed() {
        return toMaster != null;
    }

    public @Nullable CrateBlockEntity master() {
        if (toMaster == null || level == null) return null;
        if (toMaster.equals(BlockPos.ZERO)) return this;
        return level.getBlockEntity(worldPosition.offset(toMaster)) instanceof CrateBlockEntity m && m.isMaster() ? m : null;
    }

    private boolean isMaster() {
        return BlockPos.ZERO.equals(toMaster);
    }

    /** Stapelvielfaches aus den eingesetzten Stack-Upgrades (die Faktoren werden addiert, ohne Upgrade 1). */
    public int multiplier() {
        return multiplierWithout(-1);
    }

    int multiplierWithout(int skipSlot) {
        int sum = 0;
        for (int i = 0; i < UPGRADE_SLOTS; i++)
            if (i != skipSlot && upgrades.getItem(i).getItem() instanceof StackUpgradeItem u) sum += u.multiplier;
        return Math.max(1, sum);
    }

    /** Höchstmenge je Platz für diese Sorte (nicht stapelbare Sachen bleiben bei 1). */
    public static int limit(ItemStack s, int multiplier) {
        return s.getMaxStackSize() <= 1 ? 1 : s.getMaxStackSize() * multiplier;
    }

    public int limitFor(ItemStack s) {
        return limit(s, multiplier());
    }

    /** Darf das Upgrade im Platz entfernt werden, ohne dass Plätze überquellen? */
    boolean canRemoveUpgrade(int slot) {
        int m = multiplierWithout(slot);
        for (ItemStack s : items) if (!s.isEmpty() && s.getCount() > limit(s, m)) return false;
        return true;
    }

    /** Stufe der ganzen Kiste wechseln (Stufen-Upgrade): mehr Plätze, Inhalt bleibt. */
    void changeTier(CrateTier to) {
        NonNullList<ItemStack> bigger = NonNullList.withSize(sx * sy * sz * to.slotsPerBlock, ItemStack.EMPTY);
        for (int i = 0; i < Math.min(items.size(), bigger.size()); i++) bigger.set(i, items.get(i));
        items = bigger;
        tier = to;
        setChanged();
    }

    public CrateStructure.Box box() {
        return new CrateStructure.Box(worldPosition, sx, sy, sz);
    }

    public int shellBlocks() {
        return sx * sy * sz - (sx - 2) * (sy - 2) * (sz - 2);
    }

    public int capacity() {
        return items.size();
    }

    private CrateSort.Mode sortMode = CrateSort.Mode.NAME;

    public CrateSort.Mode sortMode() {
        return sortMode;
    }

    void cycleSortMode() {
        sortMode = CrateSort.Mode.values()[(sortMode.ordinal() + 1) % CrateSort.Mode.values().length];
        setChanged();
    }

    /** Alles zu möglichst großen Stapeln zusammenfassen und nach der gewählten Art ordnen. */
    void sort() {
        CrateSort.sort(items, sortMode, this::limitFor);
        setChanged();
    }

    public CrateTier tier() {
        return tier;
    }

    NonNullList<ItemStack> items() {
        return items;
    }

    public String sizeText() {
        return sx + "×" + sy + "×" + sz;
    }

    // --- Bilden und Auflösen ---

    /** Neue Kiste: dieser Block ist die untere Ecke von box. */
    void formAsMaster(CrateStructure.Box box, CrateTier tier) {
        this.tier = tier;
        sx = box.sx(); sy = box.sy(); sz = box.sz();
        items = NonNullList.withSize(box.volume() * tier.slotsPerBlock, ItemStack.EMPTY);
        toMaster = BlockPos.ZERO;
        setChanged();
    }

    void join(BlockPos master) {
        toMaster = master.subtract(worldPosition);
        setChanged();
    }

    void leave() {
        toMaster = null;
        items = NonNullList.withSize(0, ItemStack.EMPTY);
        setChanged();
    }

    /** Kiste kaputt: Inhalt fällt bei {@code at} heraus, alle Blöcke sind wieder einzeln. */
    void dissolve(BlockPos at) {
        CrateBlockEntity m = master();
        if (m == null || level == null) { leave(); return; }
        for (ItemStack s : m.items) // große Stapel in normalen Stapeln herauswerfen
            while (!s.isEmpty()) Containers.dropItemStack(level, at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5, s.split(s.getMaxStackSize()));
        Containers.dropContents(level, at, m.upgrades);
        m.upgrades.clearContent();
        BlockPos min = m.worldPosition;
        for (int x = 0; x < m.sx; x++)
            for (int y = 0; y < m.sy; y++)
                for (int z = 0; z < m.sz; z++)
                    if (level.getBlockEntity(min.offset(x, y, z)) instanceof CrateBlockEntity c) c.leave();
    }

    /** Steht die Kiste noch vollständig (Wände aus Kistenblöcken, innen Luft)? */
    public boolean intact() {
        if (!isMaster() || level == null) return false;
        for (int x = 0; x < sx; x++)
            for (int y = 0; y < sy; y++)
                for (int z = 0; z < sz; z++) {
                    BlockPos p = worldPosition.offset(x, y, z);
                    boolean shell = x == 0 || y == 0 || z == 0 || x == sx - 1 || y == sy - 1 || z == sz - 1;
                    if (shell ? !(level.getBlockEntity(p) instanceof CrateBlockEntity c && c.master() == this
                        && level.getBlockState(p).getBlock() instanceof CrateBlock cb && cb.tier == tier) : !level.getBlockState(p).isAir())
                        return false;
                }
        return true;
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        if (level != null && !level.isClientSide() && formed()) dissolve(pos);
    }

    public void openMenu(ServerPlayer player, BlockPos clicked) {
        CrateBlockEntity m = master();
        if (m == null) return;
        Level lvl = level;
        Component title = Component.translatable("block.simonspacemod.crate_" + m.tier.id).append(" " + m.sizeText());
        player.openMenu(new SimpleMenuProvider((id, inv, p) -> new CrateMenu(id, inv, m,
            pl -> lvl != null && m.formed() && !m.isRemoved() && de.simon.spacemod.flight.Shipyard.distanceSqr(pl, lvl, clicked) < 100), title));
    }

    /** Stapel so weit wie möglich einsortieren (erst auffüllen, dann leere Plätze); verkleinert {@code stack}. */
    public void insert(ItemStack stack) {
        for (int i = 0; i < items.size() && !stack.isEmpty(); i++) {
            ItemStack s = items.get(i);
            if (!s.isEmpty() && ItemStack.isSameItemSameComponents(s, stack)) {
                int n = Math.min(stack.getCount(), limitFor(s) - s.getCount());
                if (n > 0) { s.grow(n); stack.shrink(n); }
            }
        }
        for (int i = 0; i < items.size() && !stack.isEmpty(); i++)
            if (items.get(i).isEmpty()) items.set(i, stack.split(limitFor(stack)));
        setChanged();
    }

    // --- Speichern ---

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        toMaster = input.getBooleanOr("formed", false)
            ? new BlockPos(input.getIntOr("mx", 0), input.getIntOr("my", 0), input.getIntOr("mz", 0)) : null;
        if (isMaster()) {
            tier = CrateTier.values()[Math.max(0, Math.min(CrateTier.values().length - 1, input.getIntOr("tier", 0)))];
            sx = input.getIntOr("sx", 3); sy = input.getIntOr("sy", 3); sz = input.getIntOr("sz", 3);
            sortMode = CrateSort.Mode.values()[Math.max(0, Math.min(CrateSort.Mode.values().length - 1, input.getIntOr("sort", 0)))];
            items = NonNullList.withSize(input.getIntOr("size", sx * sy * sz * tier.slotsPerBlock), ItemStack.EMPTY);
            for (SlotStack s : input.listOrEmpty("Items", SlotStack.CODEC))
                if (s.slot() >= 0 && s.slot() < items.size()) items.set(s.slot(), s.full());
            upgrades.clearContent();
            for (SlotStack s : input.listOrEmpty("Upgrades", SlotStack.CODEC))
                if (s.slot() >= 0 && s.slot() < UPGRADE_SLOTS) upgrades.setItem(s.slot(), s.full());
        } else items = NonNullList.withSize(0, ItemStack.EMPTY);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putBoolean("formed", toMaster != null);
        if (toMaster == null) return;
        output.putInt("mx", toMaster.getX()); output.putInt("my", toMaster.getY()); output.putInt("mz", toMaster.getZ());
        if (!isMaster()) return;
        output.putInt("tier", tier.ordinal());
        output.putInt("sx", sx); output.putInt("sy", sy); output.putInt("sz", sz);
        output.putInt("size", items.size());
        output.putInt("sort", sortMode.ordinal());
        var list = output.list("Items", SlotStack.CODEC);
        for (int i = 0; i < items.size(); i++) if (!items.get(i).isEmpty()) list.add(SlotStack.of(i, items.get(i)));
        var up = output.list("Upgrades", SlotStack.CODEC);
        for (int i = 0; i < UPGRADE_SLOTS; i++) if (!upgrades.getItem(i).isEmpty()) up.add(SlotStack.of(i, upgrades.getItem(i)));
    }

    @Override
    public <T> @NotNull LazyOptional<T> getCapability(@NotNull Capability<T> c, @Nullable Direction side) {
        return c == ForgeCapabilities.ITEM_HANDLER ? cap.cast() : super.getCapability(c, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        cap.invalidate();
    }

    @Override
    public void reviveCaps() {
        super.reviveCaps();
        cap = LazyOptional.of(() -> handler);
    }

    /** Trichter, Rohre usw.: arbeitet immer auf dem Inventar des Hauptblocks. */
    private final class Handler implements IItemHandlerModifiable {
        private NonNullList<ItemStack> inv() {
            CrateBlockEntity m = master();
            return m == null ? NonNullList.withSize(0, ItemStack.EMPTY) : m.items;
        }

        private void changed() {
            CrateBlockEntity m = master();
            if (m != null) m.setChanged();
        }

        @Override public int getSlots() { return inv().size(); }

        @Override public @NotNull ItemStack getStackInSlot(int slot) {
            var l = inv();
            return slot >= 0 && slot < l.size() ? l.get(slot) : ItemStack.EMPTY;
        }

        @Override public void setStackInSlot(int slot, @NotNull ItemStack stack) {
            var l = inv();
            if (slot >= 0 && slot < l.size()) { l.set(slot, stack); changed(); }
        }

        @Override public @NotNull ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate) {
            var l = inv();
            if (stack.isEmpty() || slot < 0 || slot >= l.size()) return stack;
            ItemStack cur = l.get(slot);
            if (!cur.isEmpty() && !ItemStack.isSameItemSameComponents(cur, stack)) return stack;
            CrateBlockEntity m = master();
            int limit = (m == null ? stack.getMaxStackSize() : m.limitFor(stack)) - cur.getCount();
            if (limit <= 0) return stack;
            int n = Math.min(limit, stack.getCount());
            if (!simulate) {
                if (cur.isEmpty()) l.set(slot, stack.copyWithCount(n));
                else cur.grow(n);
                changed();
            }
            return n == stack.getCount() ? ItemStack.EMPTY : stack.copyWithCount(stack.getCount() - n);
        }

        @Override public @NotNull ItemStack extractItem(int slot, int amount, boolean simulate) {
            var l = inv();
            if (amount <= 0 || slot < 0 || slot >= l.size()) return ItemStack.EMPTY;
            ItemStack cur = l.get(slot);
            if (cur.isEmpty()) return ItemStack.EMPTY;
            int n = Math.min(amount, cur.getCount());
            if (simulate) return cur.copyWithCount(n);
            ItemStack out = cur.split(n);
            changed();
            return out;
        }

        @Override public int getSlotLimit(int slot) {
            CrateBlockEntity m = master();
            return 64 * (m == null ? 1 : m.multiplier());
        }

        @Override public boolean isItemValid(int slot, @NotNull ItemStack stack) { return true; }
    }

    /** Für das Menü: Bereich ab offset (Zeilen à 9) als Inventar mit 54 Plätzen. */
    static final class Window implements net.minecraft.world.Container {
        private final CrateBlockEntity m;
        int offset;

        Window(CrateBlockEntity m) { this.m = m; }

        private int idx(int i) { return offset * 9 + i; }

        @Override public int getContainerSize() { return CrateMenu.VISIBLE; }
        @Override public boolean isEmpty() { return false; }
        @Override public ItemStack getItem(int i) { int k = idx(i); return k < m.items.size() ? m.items.get(k) : ItemStack.EMPTY; }
        @Override public ItemStack removeItem(int i, int n) {
            int k = idx(i);
            if (k >= m.items.size()) return ItemStack.EMPTY;
            ItemStack out = net.minecraft.world.ContainerHelper.removeItem(m.items, k, n);
            if (!out.isEmpty()) m.setChanged();
            return out;
        }
        @Override public ItemStack removeItemNoUpdate(int i) {
            int k = idx(i);
            return k < m.items.size() ? net.minecraft.world.ContainerHelper.takeItem(m.items, k) : ItemStack.EMPTY;
        }
        @Override public void setItem(int i, ItemStack s) { int k = idx(i); if (k < m.items.size()) { m.items.set(k, s); m.setChanged(); } }
        @Override public void setChanged() { m.setChanged(); }
        @Override public boolean stillValid(Player p) { return true; }
        @Override public void clearContent() {}
    }
}

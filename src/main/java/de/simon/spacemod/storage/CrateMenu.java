package de.simon.spacemod.storage;

import de.simon.spacemod.ModBlocks;
import java.util.function.Predicate;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * Menü der Großkiste: 9 × 6 sichtbare Plätze, die über eine Bildlaufleiste durch das ganze Inventar
 * wandern (bei Netherit 8×8×8 sind das über 67 000 Plätze). Die Zeile oben schickt der Client als
 * Menü-Knopf an den Server.
 */
public class CrateMenu extends AbstractContainerMenu {
    public static final int COLS = 9, ROWS = 9, VISIBLE = COLS * ROWS;
    /** Knöpfe oben rechts (Bildlauf-Knöpfe sind die Zeilennummern darunter). */
    public static final int BTN_SORT = 1_000_000, BTN_MODE = 1_000_001;
    public static final int SLOT_X = 8, SLOT_Y = 18, INV_Y = 18 + ROWS * 18 + 14, UP_X = 196, UP_Y = 18;
    /** Erster Platz des Spielerinventars (nach Kiste und Upgrade-Plätzen). */
    public static final int PLAYER_START = VISIBLE + CrateBlockEntity.UPGRADE_SLOTS;

    private final Container window;
    private final @Nullable CrateBlockEntity master;
    public final ContainerData data;
    private final Predicate<Player> stillValid;
    private int offset;

    public CrateMenu(int id, Inventory inv, FriendlyByteBuf buf) {
        this(id, inv, null, new RawContainer(VISIBLE), new SimpleContainer(CrateBlockEntity.UPGRADE_SLOTS), new SimpleContainerData(4), p -> true);
    }

    CrateMenu(int id, Inventory inv, CrateBlockEntity master, Predicate<Player> stillValid) {
        this(id, inv, master, new CrateBlockEntity.Window(master), master.upgrades, new ContainerData() {
            @Override public int get(int i) {
                return switch (i) {
                    case 0 -> master.capacity() & 0x7FFF;
                    case 1 -> master.capacity() >> 15;
                    case 2 -> master.multiplier();
                    default -> master.sortMode().ordinal();
                };
            }
            @Override public void set(int i, int v) {}
            @Override public int getCount() { return 4; }
        }, stillValid);
    }

    private CrateMenu(int id, Inventory inv, @Nullable CrateBlockEntity master, Container window, Container upgrades, ContainerData data,
                      Predicate<Player> stillValid) {
        super(ModBlocks.CRATE_MENU.get(), id);
        this.window = window;
        this.master = master;
        this.data = data;
        this.stillValid = stillValid;
        for (int i = 0; i < VISIBLE; i++) {
            final int k = i;
            addSlot(new Slot(window, i, SLOT_X + (i % COLS) * 18, SLOT_Y + (i / COLS) * 18) {
                @Override public boolean isActive() { return offset * COLS + k < capacity(); }
                @Override public boolean mayPlace(ItemStack stack) { return isActive(); }
                @Override public boolean mayPickup(Player player) { return isActive(); }
                @Override public int getMaxStackSize(ItemStack stack) { return CrateBlockEntity.limit(stack, multiplier()); }
                @Override public int getMaxStackSize() { return 64 * multiplier(); }
                // aus großen Stapeln höchstens einen normalen Stapel auf einmal nehmen
                @Override public ItemStack remove(int amount) { return super.remove(Math.min(amount, getItem().getMaxStackSize())); }
            });
        }
        for (int i = 0; i < CrateBlockEntity.UPGRADE_SLOTS; i++) {
            final int k = i;
            addSlot(new Slot(upgrades, i, UP_X, UP_Y + i * 18) {
                @Override public boolean mayPlace(ItemStack stack) { return stack.getItem() instanceof StackUpgradeItem; }
                @Override public int getMaxStackSize() { return 1; }
                // nur entfernen, wenn danach kein Platz überquillt
                @Override public boolean mayPickup(Player player) { return master == null || master.canRemoveUpgrade(k); }
            });
        }
        addStandardInventorySlots(inv, SLOT_X, INV_Y);
        addDataSlots(data);
    }

    public int capacity() {
        return data.get(0) | data.get(1) << 15;
    }

    public int multiplier() {
        return Math.max(1, data.get(2));
    }

    public CrateSort.Mode sortMode() {
        CrateSort.Mode[] m = CrateSort.Mode.values();
        return m[Math.max(0, Math.min(m.length - 1, data.get(3)))];
    }

    public int offset() {
        return offset;
    }

    public int maxOffset() {
        return Math.max(0, (capacity() + COLS - 1) / COLS - ROWS);
    }

    /** Knopf = neue oberste Zeile. */
    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id == BTN_SORT) { if (master != null) master.sort(); return true; }
        if (id == BTN_MODE) { if (master != null) master.cycleSortMode(); return true; }
        int o = Math.max(0, Math.min(maxOffset(), id));
        if (o == offset) return false;
        offset = o;
        if (window instanceof CrateBlockEntity.Window w) w.offset = o;
        return true;
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid.test(player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem(), copy = stack.copy();
        if (index < VISIBLE) { // höchstens ein normaler Stapel je Schritt (wird wiederholt, solange Platz ist)
            int n = Math.min(stack.getCount(), stack.getMaxStackSize());
            ItemStack part = stack.copyWithCount(n);
            if (!moveItemStackTo(part, PLAYER_START, slots.size(), true)) return ItemStack.EMPTY;
            stack.shrink(n - part.getCount());
        } else if (index < PLAYER_START) {
            if (!moveItemStackTo(stack, PLAYER_START, slots.size(), true)) return ItemStack.EMPTY;
        } else if (stack.getItem() instanceof StackUpgradeItem && moveItemStackTo(stack, VISIBLE, PLAYER_START, false)) {
            // Upgrade eingesetzt
        } else if (master != null) {
            int before = stack.getCount();
            master.insert(stack); // ins ganze Inventar, nicht nur den sichtbaren Teil
            if (stack.getCount() == before) return ItemStack.EMPTY;
        } else if (!moveItemStackTo(stack, 0, VISIBLE, false)) return ItemStack.EMPTY;
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setChanged();
        return stack.getCount() == copy.getCount() ? ItemStack.EMPTY : copy;
    }

    /** Client: Inventar ohne Kappung auf 99 – mit Stack-Upgrades kommen größere Stapel vom Server. */
    static final class RawContainer implements Container {
        private final net.minecraft.core.NonNullList<ItemStack> items;

        RawContainer(int size) { items = net.minecraft.core.NonNullList.withSize(size, ItemStack.EMPTY); }

        @Override public int getContainerSize() { return items.size(); }
        @Override public boolean isEmpty() { return items.stream().allMatch(ItemStack::isEmpty); }
        @Override public ItemStack getItem(int i) { return items.get(i); }
        @Override public ItemStack removeItem(int i, int n) { return net.minecraft.world.ContainerHelper.removeItem(items, i, n); }
        @Override public ItemStack removeItemNoUpdate(int i) { return net.minecraft.world.ContainerHelper.takeItem(items, i); }
        @Override public void setItem(int i, ItemStack s) { items.set(i, s); }
        @Override public int getMaxStackSize() { return Integer.MAX_VALUE; }
        @Override public void setChanged() {}
        @Override public boolean stillValid(Player p) { return true; }
        @Override public void clearContent() { items.clear(); }
    }
}

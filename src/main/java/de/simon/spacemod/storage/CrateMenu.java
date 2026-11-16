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
    public static final int COLS = 9, ROWS = 6, VISIBLE = COLS * ROWS;
    public static final int SLOT_X = 8, SLOT_Y = 18, INV_Y = 140;

    private final Container window;
    private final @Nullable CrateBlockEntity master;
    public final ContainerData data;
    private final Predicate<Player> stillValid;
    private int offset;

    public CrateMenu(int id, Inventory inv, FriendlyByteBuf buf) {
        this(id, inv, null, new SimpleContainer(VISIBLE), new SimpleContainerData(2), p -> true);
    }

    CrateMenu(int id, Inventory inv, CrateBlockEntity master, Predicate<Player> stillValid) {
        this(id, inv, master, new CrateBlockEntity.Window(master), new ContainerData() {
            @Override public int get(int i) { return i == 0 ? master.capacity() & 0x7FFF : master.capacity() >> 15; }
            @Override public void set(int i, int v) {}
            @Override public int getCount() { return 2; }
        }, stillValid);
    }

    private CrateMenu(int id, Inventory inv, @Nullable CrateBlockEntity master, Container window, ContainerData data, Predicate<Player> stillValid) {
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
            });
        }
        addStandardInventorySlots(inv, SLOT_X, INV_Y);
        addDataSlots(data);
    }

    public int capacity() {
        return data.get(0) | data.get(1) << 15;
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
        if (index < VISIBLE) {
            if (!moveItemStackTo(stack, VISIBLE, slots.size(), true)) return ItemStack.EMPTY;
        } else if (master != null) {
            int before = stack.getCount();
            master.insert(stack); // ins ganze Inventar, nicht nur den sichtbaren Teil
            if (stack.getCount() == before) return ItemStack.EMPTY;
        } else if (!moveItemStackTo(stack, 0, VISIBLE, false)) return ItemStack.EMPTY;
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setChanged();
        return stack.getCount() == copy.getCount() ? ItemStack.EMPTY : copy;
    }
}

package de.simon.spacemod.assembly;

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

/** Menü der Schiffsteil-Fertigung: 3 x 3 Zutaten (beliebige Mengen je Platz), Ergebnis, Inventar. */
public class AssemblyMenu extends AbstractContainerMenu {
    public static final int IN_X = 30, IN_Y = 17, OUT_X = 124, OUT_Y = 35;
    public final ContainerData data;
    private final Predicate<Player> stillValid;

    public AssemblyMenu(int id, Inventory inv, FriendlyByteBuf buf) {
        this(id, inv, new SimpleContainer(10), new SimpleContainerData(5), p -> true);
    }

    public AssemblyMenu(int id, Inventory inv, Container box, ContainerData data, Predicate<Player> stillValid) {
        super(ModBlocks.ASSEMBLY_MENU.get(), id);
        this.data = data;
        this.stillValid = stillValid;
        for (int i = 0; i < 9; i++) addSlot(new Slot(box, i, IN_X + (i % 3) * 18, IN_Y + (i / 3) * 18));
        addSlot(new Slot(box, AssemblyBlockEntity.OUTPUT, OUT_X, OUT_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return false;
            }
        });
        addStandardInventorySlots(inv, 8, 84);
        addDataSlots(data);
    }

    public int selected() {
        return data.get(0);
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id == selected() || AssemblyRecipes.get(id) == null) return false;
        data.set(0, id);
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
        if (index <= AssemblyBlockEntity.OUTPUT) {
            if (!moveItemStackTo(stack, 10, slots.size(), true)) return ItemStack.EMPTY;
            if (index == AssemblyBlockEntity.OUTPUT) slot.onQuickCraft(stack, copy);
        } else {
            // in den Platz, den das gewählte Rezept für diese Sorte vorsieht
            AssemblyRecipes.Recipe r = AssemblyRecipes.get(selected());
            boolean moved = false;
            if (r != null)
                for (int i = 0; i < 9 && !stack.isEmpty(); i++)
                    if (r.inputs()[i].is(stack.getItem())) moved |= moveItemStackTo(stack, i, i + 1, false);
            if (!moved && !moveItemStackTo(stack, 0, 9, false)) return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setChanged();
        return copy;
    }
}

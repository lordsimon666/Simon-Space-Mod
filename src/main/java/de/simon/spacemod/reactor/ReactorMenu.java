package de.simon.spacemod.reactor;

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

/** Reaktor-Anzeige: 1 bis 9 Eingabeplätze (3 x 3 wie beim Werfer) für Uranbarren, dazu Kennzahlen. */
public class ReactorMenu extends AbstractContainerMenu {
    public final int fuelSlots;
    public final ContainerData data;
    private final Predicate<Player> stillValid;

    /** Server. */
    public ReactorMenu(int id, Inventory inv, Container fuel, ContainerData data, int slots, Predicate<Player> stillValid) {
        super(ModBlocks.REACTOR_MENU.get(), id);
        this.fuelSlots = slots;
        this.data = data;
        this.stillValid = stillValid;
        for (int i = 0; i < slots; i++) {
            int col = i % 3, row = i / 3;
            addSlot(new Slot(fuel, i, 62 + col * 18, 17 + row * 18) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return stack.is(ModBlocks.URANIUM_INGOT.get());
                }
            });
        }
        addStandardInventorySlots(inv, 8, 84);
        addDataSlots(data);
    }

    /** Client. */
    public ReactorMenu(int id, Inventory inv, FriendlyByteBuf buf) {
        this(id, inv, new SimpleContainer(9), new SimpleContainerData(6), buf.readVarInt(), p -> true);
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
        if (index < fuelSlots) {
            if (!moveItemStackTo(stack, fuelSlots, this.slots.size(), true)) return ItemStack.EMPTY;
        } else if (!stack.is(ModBlocks.URANIUM_INGOT.get()) || !moveItemStackTo(stack, 0, fuelSlots, false)) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setChanged();
        return copy;
    }
}

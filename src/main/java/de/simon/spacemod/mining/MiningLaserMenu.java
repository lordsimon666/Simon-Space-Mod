package de.simon.spacemod.mining;

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

/** Menü des Bergbaulasers: 3 × 3 Ausbeute, Größe/Tiefe einstellen, an/aus, Energie. */
public class MiningLaserMenu extends AbstractContainerMenu {
    public static final int SLOT_X = 116, SLOT_Y = 17;
    public final ContainerData data;
    private final @Nullable MiningLaserBlockEntity be;
    private final Predicate<Player> stillValid;

    public MiningLaserMenu(int id, Inventory inv, FriendlyByteBuf buf) {
        this(id, inv, new SimpleContainer(9), new SimpleContainerData(6), null, p -> true);
    }

    public MiningLaserMenu(int id, Inventory inv, Container box, ContainerData data, @Nullable MiningLaserBlockEntity be, Predicate<Player> stillValid) {
        super(ModBlocks.MINING_LASER_MENU.get(), id);
        this.data = data;
        this.be = be;
        this.stillValid = stillValid;
        for (int i = 0; i < 9; i++) addSlot(new Slot(box, i, SLOT_X + (i % 3) * 18, SLOT_Y + (i / 3) * 18));
        addStandardInventorySlots(inv, 8, 84);
        addDataSlots(data);
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (be != null) be.button(id);
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
        if (index < 9) {
            if (!moveItemStackTo(stack, 9, slots.size(), true)) return ItemStack.EMPTY;
        } else if (!moveItemStackTo(stack, 0, 9, false)) return ItemStack.EMPTY;
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setChanged();
        return copy;
    }
}

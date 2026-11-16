package de.simon.spacemod.defense;

import de.simon.spacemod.ModBlocks;
import java.util.function.IntConsumer;
import java.util.function.Predicate;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;

/** Menü des Basisschilds: Radius, an/aus, Zustand (keine Plätze). */
public class BaseShieldMenu extends AbstractContainerMenu {
    public final ContainerData data;
    private final IntConsumer press;
    private final Predicate<Player> stillValid;

    public BaseShieldMenu(int id, Inventory inv, FriendlyByteBuf buf) {
        this(id, inv, new SimpleContainerData(7), b -> { }, p -> true);
    }

    public BaseShieldMenu(int id, Inventory inv, ContainerData data, IntConsumer press, Predicate<Player> stillValid) {
        super(ModBlocks.BASE_SHIELD_MENU.get(), id);
        this.data = data;
        this.press = press;
        this.stillValid = stillValid;
        addDataSlots(data);
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        press.accept(id);
        return true;
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid.test(player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }
}

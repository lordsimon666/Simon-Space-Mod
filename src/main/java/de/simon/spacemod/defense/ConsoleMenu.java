package de.simon.spacemod.defense;

import java.util.function.IntConsumer;
import java.util.function.Predicate;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;

/** Menü ohne Plätze für Konsolen: Anzeigewerte über {@link ContainerData}, Knöpfe über Menü-Klicks. */
public class ConsoleMenu extends AbstractContainerMenu {
    public final ContainerData data;
    private final IntConsumer action;
    private final Predicate<Player> stillValid;

    public ConsoleMenu(MenuType<?> type, int id, ContainerData data, IntConsumer action, Predicate<Player> stillValid) {
        super(type, id);
        this.data = data;
        this.action = action;
        this.stillValid = stillValid;
        addDataSlots(data);
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        action.accept(id);
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

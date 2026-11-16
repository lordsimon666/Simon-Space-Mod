package de.simon.spacemod.storage;

import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

/**
 * Stack-Upgrade (wie bei Sophisticated Storage): in einen der 4 Upgrade-Plätze der Großkiste gesetzt,
 * passen mehr Gegenstände in jeden Platz. Mehrere Upgrades zählen zusammen (2× + 4× = 6×).
 */
public class StackUpgradeItem extends Item {
    public final int multiplier;

    public StackUpgradeItem(Item.Properties properties, int multiplier) {
        super(properties);
        this.multiplier = multiplier;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext ctx, TooltipDisplay display, Consumer<Component> out, TooltipFlag flag) {
        out.accept(Component.translatable("tooltip.simonspacemod.stack_upgrade", multiplier, 64 * multiplier).withStyle(ChatFormatting.GRAY));
    }
}

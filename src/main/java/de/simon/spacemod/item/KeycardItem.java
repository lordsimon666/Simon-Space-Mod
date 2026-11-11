package de.simon.spacemod.item;

import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

/**
 * Schlüsselkarte: Sicherheit (gelb) öffnet Sicherheitstüren, Kommando (rot) öffnet Kommandotüren,
 * Captain öffnet beide. Shift-Rechtsklick mit der Karte auf eine Tür verschließt bzw. öffnet sie.
 */
public class KeycardItem extends Item {
    public enum Level {
        SECURITY(1), COMMAND(1), CAPTAIN(2);

        /** Rang beim Verschließen: Sicherheit und Kommando gleich, Captain höher. */
        public final int rank;

        Level(int rank) {
            this.rank = rank;
        }
    }

    public final Level level;

    public KeycardItem(Item.Properties props, Level level) {
        super(props.stacksTo(1));
        this.level = level;
    }

    /** Hat der Spieler irgendwo im Inventar eine Karte dieser Stufe (oder die Captain-Karte)? */
    public static boolean has(Player player, Level needed) {
        var inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s.getItem() instanceof KeycardItem k && (k.level == needed || k.level == Level.CAPTAIN)) return true;
        }
        return false;
    }

    /**
     * Shift + Rechtsklick auf eine Tür: verschließen / entriegeln. Beim Schleichen mit einem Gegenstand
     * in der Hand gibt Minecraft den Klick an den Gegenstand weiter (nicht an den Block), daher hier.
     */
    @Override
    public net.minecraft.world.InteractionResult useOn(net.minecraft.world.item.context.UseOnContext context) {
        var level = context.getLevel();
        var pos = context.getClickedPos();
        var state = level.getBlockState(pos);
        Player player = context.getPlayer();
        if (player == null || !player.isShiftKeyDown() || !(state.getBlock() instanceof de.simon.spacemod.block.StarshipDoorBlock))
            return super.useOn(context);
        if (!level.isClientSide() && level.getBlockEntity(de.simon.spacemod.block.StarshipDoorBlock.origin(pos, state))
                instanceof de.simon.spacemod.block.entity.DoorBlockEntity be) be.toggleLock(player, this.level);
        return net.minecraft.world.InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> out, TooltipFlag flag) {
        out.accept(Component.translatable("tooltip.simonspacemod.keycard." + level.name().toLowerCase()).withStyle(ChatFormatting.GRAY));
        out.accept(Component.translatable("tooltip.simonspacemod.keycard.lock").withStyle(ChatFormatting.DARK_GRAY));
    }
}

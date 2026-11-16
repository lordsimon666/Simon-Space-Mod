package de.simon.spacemod.guide;

import de.simon.spacemod.ModBlocks;
import de.simon.spacemod.SimonSpaceMod;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.entity.player.PlayerEvent;

/**
 * Handbuch der Sternenflotte: Rechtsklick öffnet es, Schleichen + Rechtsklick nimmt einen Bauplan, den es in
 * der Welt zeigt, wieder weg. Jeder Spieler bekommt beim ersten Betreten einer Welt eins.
 */
public class GuideBookItem extends Item {
    // im Teil der Spielerdaten, der den Tod übersteht: nach dem Wiederbeleben gibt es kein zweites Buch
    private static final String GOT_BOOK = SimonSpaceMod.MODID + ":got_guide_book";

    public GuideBookItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide()) ClientHook.open(player.isShiftKeyDown());
        return InteractionResult.SUCCESS;
    }

    /** Erstes Betreten: Handbuch ins Inventar. */
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        CompoundTag data = player.getPersistentData();
        CompoundTag persisted = data.getCompoundOrEmpty(ServerPlayer.PERSISTED_NBT_TAG);
        if (persisted.getBooleanOr(GOT_BOOK, false)) return;
        persisted.putBoolean(GOT_BOOK, true);
        data.put(ServerPlayer.PERSISTED_NBT_TAG, persisted);
        ItemStack book = new ItemStack(ModBlocks.GUIDE_BOOK.get());
        if (!player.getInventory().add(book)) player.drop(book, false);
    }

    /** Getrennt, damit der Server die Client-Klassen nie lädt. */
    private static final class ClientHook {
        static void open(boolean sneaking) {
            de.simon.spacemod.client.guide.GuideClient.useBook(sneaking);
        }
    }
}

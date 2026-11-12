package de.simon.spacemod.item;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

/**
 * "Kirk an Enterprise – beamen Sie mich hoch!" Beamt zurück auf die zuletzt benutzte
 * Transporterplattform – auch auf ein fliegendes Schiff, wo immer es gerade ist.
 */
public class CommunicatorItem extends Item {
    public CommunicatorItem(Item.Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (!(level instanceof ServerLevel server) || !(player instanceof ServerPlayer sp)) {
            return InteractionResult.SUCCESS;
        }
        String error = de.simon.spacemod.transporter.Transporters.beamBack(sp);
        if (error != null) {
            sp.sendOverlayMessage(Component.translatable(error));
            return InteractionResult.FAIL;
        }
        sp.sendOverlayMessage(Component.translatable("message.simonspacemod.communicator.beam_up"));
        return InteractionResult.SUCCESS_SERVER;
    }
}

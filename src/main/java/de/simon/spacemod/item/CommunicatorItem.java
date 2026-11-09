package de.simon.spacemod.item;

import de.simon.spacemod.ModBlocks;
import de.simon.spacemod.block.Beam;
import de.simon.spacemod.block.TransporterPadBlock;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

/** "Kirk an Enterprise – beamen Sie mich hoch!" Beamt zurück auf die zuletzt benutzte Transporterplattform. */
public class CommunicatorItem extends Item {
    public CommunicatorItem(Item.Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (!(level instanceof ServerLevel server) || !(player instanceof ServerPlayer sp)) {
            return InteractionResult.SUCCESS;
        }
        Optional<Long> stored = sp.getPersistentData().getLong(TransporterPadBlock.LAST_PAD_TAG);
        if (stored.isEmpty()) {
            sp.sendOverlayMessage(Component.translatable("message.simonspacemod.communicator.no_pad"));
            return InteractionResult.FAIL;
        }
        BlockPos pad = BlockPos.of(stored.get());
        server.getChunk(pad); // Chunk laden
        if (!server.getBlockState(pad).is(ModBlocks.TRANSPORTER_PAD.get())) {
            sp.sendOverlayMessage(Component.translatable("message.simonspacemod.communicator.pad_gone"));
            return InteractionResult.FAIL;
        }
        Beam.teleport(sp, server, pad.getX() + 0.5, pad.getY() + 0.2, pad.getZ() + 0.5, sp.getYRot());
        sp.sendOverlayMessage(Component.translatable("message.simonspacemod.communicator.beam_up"));
        return InteractionResult.SUCCESS_SERVER;
    }
}

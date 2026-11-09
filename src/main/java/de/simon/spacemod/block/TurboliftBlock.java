package de.simon.spacemod.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;

/**
 * Turbolift: Rechtsklick fährt zum ausgewählten Deck, Schleichen + Rechtsklick wählt ein anderes Deck
 * (z.B. von der Brücke zum Maschinenraum oder Transporterraum).
 */
public class TurboliftBlock extends FacingBlock implements EntityBlock {
    public static final MapCodec<TurboliftBlock> CODEC = simpleCodec(TurboliftBlock::new);

    public TurboliftBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TurboliftBlockEntity(pos, state);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!(level instanceof ServerLevel server) || !(player instanceof ServerPlayer sp)) {
            return InteractionResult.SUCCESS;
        }
        if (!(level.getBlockEntity(pos) instanceof TurboliftBlockEntity be) || be.getSelected() == null) {
            sp.sendOverlayMessage(Component.translatable("message.simonspacemod.turbolift.unlinked"));
            return InteractionResult.CONSUME;
        }
        // Schleichen + Rechtsklick: anderes Deck wählen
        if (sp.isShiftKeyDown()) {
            TurboliftBlockEntity.Destination next = be.cycle();
            sp.sendOverlayMessage(Component.translatable("message.simonspacemod.turbolift.selected", Component.translatable(next.key())));
            return InteractionResult.CONSUME;
        }
        TurboliftBlockEntity.Destination dest = be.getSelected();
        BlockState targetState = server.getBlockState(dest.pos());
        Direction out = targetState.hasProperty(FACING) ? targetState.getValue(FACING) : Direction.NORTH;
        BlockPos stand = dest.pos().relative(out);
        Beam.teleport(sp, server, stand.getX() + 0.5, stand.getY(), stand.getZ() + 0.5, out.toYRot());
        sp.sendOverlayMessage(Component.translatable("message.simonspacemod.turbolift.arrived", Component.translatable(dest.key())));
        return InteractionResult.CONSUME;
    }
}

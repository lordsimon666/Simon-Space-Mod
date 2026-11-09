package de.simon.spacemod.block;

import com.mojang.serialization.MapCodec;
import de.simon.spacemod.turbolift.TurboliftBlockHelper;
import de.simon.spacemod.turbolift.TurboliftData;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Turbolift: Rechtsklick öffnet die Deckauswahl. Alle Turbolifte mit demselben Netznamen sind
 * verbunden – auch selbst gebaute in eigenen Schiffen oder Basen. Die Vorderseite zeigt zum Ausstieg.
 */
public class TurboliftBlock extends FacingBlock {
    public static final MapCodec<TurboliftBlock> CODEC = simpleCodec(TurboliftBlock::new);

    public TurboliftBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (level instanceof ServerLevel server && !oldState.is(this)) TurboliftData.get(server).register(pos);
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
        if (!level.getBlockState(pos).is(this)) TurboliftData.get(level).remove(pos);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level instanceof ServerLevel server && player instanceof ServerPlayer sp) {
            TurboliftBlockHelper.openMenu(sp, server, pos);
        }
        return InteractionResult.SUCCESS;
    }
}

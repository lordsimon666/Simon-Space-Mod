package de.simon.spacemod.block;

import com.mojang.serialization.MapCodec;
import de.simon.spacemod.ModBlocks;
import de.simon.spacemod.block.entity.ReactorControllerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;

/** Steuerblock des Fusions-/Kernreaktors: sitzt in einer Seitenfläche des Würfels, Rechtsklick öffnet die Anzeige. */
public class ReactorControllerBlock extends FacingBlock implements EntityBlock {
    public static final BooleanProperty LIT = BlockStateProperties.LIT;
    public static final MapCodec<ReactorControllerBlock> CODEC = simpleCodec(ReactorControllerBlock::new);

    public ReactorControllerBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(LIT, false));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, LIT);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide() && player instanceof ServerPlayer sp && level.getBlockEntity(pos) instanceof ReactorControllerBlockEntity be)
            be.openMenu(sp, p -> de.simon.spacemod.flight.Shipyard.distanceSqr(p, level, pos) < 64 && level.getBlockEntity(pos) == be);
        return InteractionResult.SUCCESS;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ReactorControllerBlockEntity(pos, state);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide() || type != ModBlocks.REACTOR_BE.get() ? null
            : (BlockEntityTicker<T>) (BlockEntityTicker<ReactorControllerBlockEntity>) ReactorControllerBlockEntity::serverTick;
    }
}

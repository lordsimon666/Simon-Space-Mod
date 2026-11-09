package de.simon.spacemod.block;

import com.mojang.serialization.MapCodec;
import de.simon.spacemod.turbolift.TurboliftBlockHelper;
import de.simon.spacemod.turbolift.TurboliftData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import org.jspecify.annotations.Nullable;
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
    /** Zwei Blöcke hoch wie eine Tür: unten das Bedienfeld, oben die Tür. */
    public static final EnumProperty<DoubleBlockHalf> HALF = BlockStateProperties.DOUBLE_BLOCK_HALF;

    public TurboliftBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(HALF, DoubleBlockHalf.LOWER));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, HALF);
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockPos pos = context.getClickedPos();
        Level level = context.getLevel();
        if (pos.getY() >= level.getMaxY() || !level.getBlockState(pos.above()).canBeReplaced(context)) return null;
        return super.getStateForPlacement(context).setValue(HALF, DoubleBlockHalf.LOWER);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity by, ItemStack stack) {
        super.setPlacedBy(level, pos, state, by, stack);
        level.setBlock(pos.above(), state.setValue(HALF, DoubleBlockHalf.UPPER), Block.UPDATE_ALL);
    }

    /** Fehlt die andere Hälfte, verschwindet auch diese (wie bei einer Tür). */
    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos,
                                     Direction dir, BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
        boolean lower = state.getValue(HALF) == DoubleBlockHalf.LOWER;
        if (dir == (lower ? Direction.UP : Direction.DOWN)
            && !(neighbourState.is(this) && neighbourState.getValue(HALF) != state.getValue(HALF)))
            return Blocks.AIR.defaultBlockState();
        return super.updateShape(state, level, ticks, pos, dir, neighbourPos, neighbourState, random);
    }

    /** Position des Bedienfelds (untere Hälfte) – dort ist der Lift im Netz eingetragen. */
    public static BlockPos panel(BlockState state, BlockPos pos) {
        return state.hasProperty(HALF) && state.getValue(HALF) == DoubleBlockHalf.UPPER ? pos.below() : pos;
    }

    /** Ist an dieser Stelle das Bedienfeld eines Turbolifts (in einem beliebigen Stil)? */
    public static boolean isLift(BlockState state) {
        return state.getBlock() instanceof TurboliftBlock && state.getValue(HALF) == DoubleBlockHalf.LOWER;
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (level instanceof ServerLevel server && !oldState.is(this) && state.getValue(HALF) == DoubleBlockHalf.LOWER)
            TurboliftData.get(server).register(pos);
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
        if (state.getValue(HALF) == DoubleBlockHalf.LOWER && !level.getBlockState(pos).is(this)) TurboliftData.get(level).remove(pos);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level instanceof ServerLevel server && player instanceof ServerPlayer sp) {
            TurboliftBlockHelper.openMenu(sp, server, panel(state, pos));
        }
        return InteractionResult.SUCCESS;
    }
}

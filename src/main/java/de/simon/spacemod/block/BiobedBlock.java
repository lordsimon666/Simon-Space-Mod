package de.simon.spacemod.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Biobett der Krankenstation (zwei Blöcke lang wie ein Bett). FACING zeigt vom Kopf- zum Fußende;
 * das Kopfende steht an der Wand.
 */
public class BiobedBlock extends FacingBlock {
    public static final MapCodec<BiobedBlock> CODEC = simpleCodec(BiobedBlock::new);
    public static final BooleanProperty HEAD = BooleanProperty.create("head");
    private static final VoxelShape SHAPE = box(0, 0, 0, 16, 9, 16);

    public BiobedBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(HEAD, false));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, HEAD);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    private static Direction toPartner(BlockState state) {
        Direction foot = state.getValue(FACING);
        return state.getValue(HEAD) ? foot : foot.getOpposite();
    }

    /** Fußende dort, wo geklickt wurde; das Kopfende liegt in Blickrichtung des Spielers. */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction look = context.getHorizontalDirection();
        BlockPos head = context.getClickedPos().relative(look);
        if (!context.getLevel().getBlockState(head).canBeReplaced(context)) return null;
        return defaultBlockState().setValue(FACING, look.getOpposite()).setValue(HEAD, false);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity by, ItemStack stack) {
        super.setPlacedBy(level, pos, state, by, stack);
        if (!level.isClientSide()) {
            level.setBlock(pos.relative(toPartner(state)), state.setValue(HEAD, true), Block.UPDATE_ALL);
        }
    }

    /** Fehlt die andere Hälfte, verschwindet auch diese. */
    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos,
                                     Direction dir, BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
        if (dir == toPartner(state) && !(neighbourState.is(this) && neighbourState.getValue(HEAD) != state.getValue(HEAD))) {
            return Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, level, ticks, pos, dir, neighbourPos, neighbourState, random);
    }
}

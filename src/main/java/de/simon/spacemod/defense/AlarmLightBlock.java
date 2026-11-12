package de.simon.spacemod.defense;

import com.mojang.serialization.MapCodec;
import de.simon.spacemod.block.FacingBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Rote Alarmleuchte an der Wand: blinkt bei Warpkernbruch (Alarmstufe Rot), leitet Strom wie alle Blöcke der Mod. */
public class AlarmLightBlock extends FacingBlock {
    public static final BooleanProperty LIT = BlockStateProperties.LIT;
    public static final MapCodec<AlarmLightBlock> CODEC = simpleCodec(AlarmLightBlock::new);
    private static final VoxelShape N = Block.box(4, 3, 11, 12, 13, 16), E = Block.box(0, 3, 4, 5, 13, 12),
        S = Block.box(4, 3, 0, 12, 13, 5), W = Block.box(11, 3, 4, 16, 13, 12);

    public AlarmLightBlock(BlockBehaviour.Properties properties) {
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
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch (state.getValue(FACING)) {
            case EAST -> E;
            case SOUTH -> S;
            case WEST -> W;
            default -> N;
        };
    }
}

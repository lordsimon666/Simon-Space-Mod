package de.simon.spacemod.block;

import com.mojang.serialization.MapCodec;
import de.simon.spacemod.power.ShipPower;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Deckenleuchte: flaches Paneel (1/8 Block) direkt unter der Decke. Leuchtet nur mit Strom – im
 * gespawnten Schiff, solange ein Warpkern steht, sonst über leitende Blöcke zu einem Warpkern.
 */
public class CeilingLightBlock extends Block {
    public static final BooleanProperty LIT = BlockStateProperties.LIT;
    public static final MapCodec<CeilingLightBlock> CODEC = simpleCodec(CeilingLightBlock::new);
    private static final VoxelShape SHAPE = Block.box(0, 14, 0, 16, 16, 16);

    public CeilingLightBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(LIT, false));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LIT);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        if (!level.isClientSide() && !oldState.is(this)) level.scheduleTick(pos, this, 20 + level.getRandom().nextInt(40));
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        boolean on = ShipPower.powered(level, pos);
        if (on != state.getValue(LIT)) level.setBlock(pos, state.setValue(LIT, on), Block.UPDATE_CLIENTS);
        level.scheduleTick(pos, this, 80 + random.nextInt(40));
    }
}

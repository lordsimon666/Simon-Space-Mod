package de.simon.spacemod.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Block mit Blickrichtung und eigener (nicht voller) Form, z.B. Stühle und Konsolen. */
public class ShapedFacingBlock extends FacingBlock {
    public static final MapCodec<ShapedFacingBlock> CODEC = simpleCodec(ShapedFacingBlock::new);
    private final VoxelShape shape;

    public ShapedFacingBlock(BlockBehaviour.Properties properties) {
        this(properties, Shapes.block());
    }

    public ShapedFacingBlock(BlockBehaviour.Properties properties, VoxelShape shape) {
        super(properties);
        this.shape = shape;
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return shape;
    }
}

package de.simon.spacemod.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Einfacher Block ohne Richtung mit eigener Form (Tische, Bildschirm auf dem Tisch …). */
public class ShapedBlock extends Block {
    public static final MapCodec<ShapedBlock> CODEC = simpleCodec(ShapedBlock::new);
    private final VoxelShape shape;

    public ShapedBlock(BlockBehaviour.Properties properties) {
        this(properties, Shapes.block());
    }

    public ShapedBlock(BlockBehaviour.Properties properties, VoxelShape shape) {
        super(properties);
        this.shape = shape;
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return shape;
    }
}

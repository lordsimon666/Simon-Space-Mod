package de.simon.spacemod.display;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;

/**
 * Kamera: schaut in die Richtung ihrer Linse (die Seite, an die man sie setzt, zeigt nach hinten – an einer
 * Wand schaut sie also vom Wand weg, unter der Decke nach unten). Ihr Bild zeigen Bildschirme an derselben
 * Struktur (über Blöcke der Mod verbunden).
 */
public class CameraBlock extends DirectionalBlock {
    public static final MapCodec<CameraBlock> CODEC = simpleCodec(CameraBlock::new);

    public CameraBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends DirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getClickedFace());
    }
}

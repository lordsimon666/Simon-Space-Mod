package de.simon.spacemod.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;

/**
 * Decke des unteren und Boden des oberen Raums in einem Block: oben das Bodenmaterial des
 * oberen Raums, unten die Deckenverkleidung des unteren (z. B. Holodeck-Decke unter dem Korridorboden).
 */
public class DualFloorBlock extends Block {
    public static final EnumProperty<DualMaterial> TOP = EnumProperty.create("top", DualMaterial.class);
    public static final EnumProperty<DualMaterial> BOTTOM = EnumProperty.create("bottom", DualMaterial.class);
    public static final MapCodec<DualFloorBlock> CODEC = simpleCodec(DualFloorBlock::new);

    public DualFloorBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(TOP, DualMaterial.DECK_FLOOR).setValue(BOTTOM, DualMaterial.CEILING_PANEL));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(TOP, BOTTOM);
    }
}

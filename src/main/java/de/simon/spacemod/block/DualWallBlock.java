package de.simon.spacemod.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;

/**
 * Wand zwischen zwei Räumen mit zwei Seiten: die Vorderseite (Blickrichtung) zeigt das Material
 * des einen Raums, die Rückseite das des anderen – z. B. Holodeck-Gitter innen, Korridorwand außen.
 */
public class DualWallBlock extends FacingBlock {
    public static final EnumProperty<DualMaterial> FRONT = EnumProperty.create("front", DualMaterial.class);
    public static final EnumProperty<DualMaterial> BACK = EnumProperty.create("back", DualMaterial.class);
    /** Eckstück einer schrägen Wand: Vorder- und linke Seite vorne, Rück- und rechte Seite hinten. */
    public static final net.minecraft.world.level.block.state.properties.BooleanProperty CORNER =
        net.minecraft.world.level.block.state.properties.BooleanProperty.create("corner");
    public static final MapCodec<DualWallBlock> CODEC = simpleCodec(DualWallBlock::new);

    public DualWallBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(FRONT, DualMaterial.CORRIDOR_WALL).setValue(BACK, DualMaterial.TNG_WALL).setValue(CORNER, false));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, FRONT, BACK, CORNER);
    }
}

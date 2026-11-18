package de.simon.spacemod.village;

import com.mojang.serialization.MapCodec;
import de.simon.spacemod.block.ChairBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Shuttle-Pilotensitz: Sitz mit hoher Lehne, Kopfstütze und Armlehnen. Man kann darauf sitzen; Dorfbewohner
 * werden an ihm zum Sternenflotten-Dorfbewohner (Arbeitsplatz).
 */
public class ShuttleChairBlock extends ChairBlock {
    public static final VoxelShape SHAPE = Shapes.or(box(4, 0, 4, 12, 6, 12), box(2, 6, 2, 14, 9, 14), box(2, 9, 12, 14, 22, 15),
        box(1, 9, 3, 3, 13, 12), box(13, 9, 3, 15, 13, 12));
    public static final MapCodec<ShuttleChairBlock> CODEC = simpleCodec(ShuttleChairBlock::new);

    public ShuttleChairBlock(BlockBehaviour.Properties properties) {
        super(properties, SHAPE);
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }
}

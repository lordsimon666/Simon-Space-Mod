package de.simon.spacemod.village;

import com.mojang.serialization.MapCodec;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;

/**
 * Raumschiff-Bett: zwei Blöcke lang, schlanker Metallrahmen mit Leuchtstreifen. Funktioniert wie ein normales
 * Bett (schlafen, Nacht überspringen, Wiedereinstiegspunkt, explodiert in Nether und End).
 */
public class SpaceBedBlock extends BedBlock {
    public static final MapCodec<BedBlock> CODEC = simpleCodec(SpaceBedBlock::new);

    public SpaceBedBlock(BlockBehaviour.Properties properties) {
        super(DyeColor.BLUE, properties);
    }

    @Override
    public MapCodec<BedBlock> codec() {
        return CODEC;
    }
}

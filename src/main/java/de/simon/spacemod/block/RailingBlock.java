package de.simon.spacemod.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;

/** Rotes Brückengeländer (verbindet sich wie Eisengitter). */
public class RailingBlock extends IronBarsBlock {
    public static final MapCodec<RailingBlock> CODEC = simpleCodec(RailingBlock::new);

    public RailingBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    public MapCodec<? extends IronBarsBlock> codec() {
        return CODEC;
    }
}

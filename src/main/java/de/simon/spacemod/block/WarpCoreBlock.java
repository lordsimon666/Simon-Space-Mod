package de.simon.spacemod.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;

/**
 * Warpkern-Block einer Epoche: erzeugt Strom (RF pro Tick) für das Bordnetz und leitet ihn weiter.
 * Ein Block der NX-01 liefert am wenigsten, einer der Enterprise-E am meisten.
 */
public class WarpCoreBlock extends GlowParticleBlock {
    public final int rfPerTick;

    public WarpCoreBlock(BlockBehaviour.Properties properties, int rfPerTick) {
        super(properties, Kind.WARP_CORE);
        this.rfPerTick = rfPerTick;
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return simpleCodec(p -> new WarpCoreBlock(p, rfPerTick));
    }
}

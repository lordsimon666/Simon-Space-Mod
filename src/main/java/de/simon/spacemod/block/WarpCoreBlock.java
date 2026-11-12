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

    /** Als Startpunkt der Stromverteilung merken (beim Setzen und, für alte Welten, ab und zu). */
    @Override
    protected void onPlace(net.minecraft.world.level.block.state.BlockState state, net.minecraft.world.level.Level level,
                           net.minecraft.core.BlockPos pos, net.minecraft.world.level.block.state.BlockState old, boolean moved) {
        super.onPlace(state, level, pos, old, moved);
        if (level instanceof net.minecraft.server.level.ServerLevel sl && !old.is(this)) de.simon.spacemod.power.PowerNetwork.addSeed(sl, pos);
    }

    @Override
    protected boolean isRandomlyTicking(net.minecraft.world.level.block.state.BlockState state) {
        return true;
    }

    @Override
    protected void randomTick(net.minecraft.world.level.block.state.BlockState state, net.minecraft.server.level.ServerLevel level,
                              net.minecraft.core.BlockPos pos, net.minecraft.util.RandomSource random) {
        de.simon.spacemod.power.PowerNetwork.addSeed(level, pos);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return simpleCodec(p -> new WarpCoreBlock(p, rfPerTick));
    }
}

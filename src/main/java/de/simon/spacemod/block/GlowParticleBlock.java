package de.simon.spacemod.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

/** Leuchtender Block mit Partikeln (Warpkern, Bussard-Kollektor). */
public class GlowParticleBlock extends Block {
    public enum Kind { WARP_CORE, BUSSARD, ENERGIZER }

    private final Kind kind;

    public GlowParticleBlock(BlockBehaviour.Properties properties, Kind kind) {
        super(properties);
        this.kind = kind;
    }

    public GlowParticleBlock(BlockBehaviour.Properties properties) {
        this(properties, Kind.WARP_CORE);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return simpleCodec(GlowParticleBlock::new);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (kind == Kind.WARP_CORE) {
            if (random.nextInt(3) == 0) {
                level.addParticle(ParticleTypes.ELECTRIC_SPARK,
                    pos.getX() + random.nextDouble(), pos.getY() + random.nextDouble(), pos.getZ() + random.nextDouble(),
                    0, 0.05, 0);
            }
        } else if (kind == Kind.ENERGIZER) {
            if (random.nextInt(4) == 0) {
                level.addParticle(ParticleTypes.HAPPY_VILLAGER,
                    pos.getX() + random.nextDouble(), pos.getY() + random.nextDouble(), pos.getZ() + random.nextDouble(),
                    0, 0.02, 0);
            }
        } else if (random.nextInt(6) == 0) {
            level.addParticle(ParticleTypes.GLOW,
                pos.getX() + random.nextDouble(), pos.getY() + random.nextDouble(), pos.getZ() + random.nextDouble(),
                0, 0, 0);
        }
    }
}

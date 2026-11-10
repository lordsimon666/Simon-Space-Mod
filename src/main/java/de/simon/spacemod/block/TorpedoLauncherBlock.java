package de.simon.spacemod.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;

/** Torpedowerfer an der Hülle (Photonen- oder Quantentorpedos). Gefeuert wird in der Kamerasicht mit P / O. */
public class TorpedoLauncherBlock extends Block {
    public final boolean quantum;

    public TorpedoLauncherBlock(BlockBehaviour.Properties properties, boolean quantum) {
        super(properties);
        this.quantum = quantum;
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return simpleCodec(p -> new TorpedoLauncherBlock(p, quantum));
    }
}

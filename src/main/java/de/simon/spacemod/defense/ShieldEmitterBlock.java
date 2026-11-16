package de.simon.spacemod.defense;

import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;

/**
 * Schildemitter einer Enterprise (Stufe 1 = NCC-1701 ... 6 = NCC-1701-E, 7 = NCC-1701-F). Je höher die Stufe, desto
 * mehr Leben je Schildblock und desto schnellere Regeneration. Die NX-01 hat noch keine Schilde.
 */
public class ShieldEmitterBlock extends Block {
    public final int tier;

    public ShieldEmitterBlock(BlockBehaviour.Properties properties, int tier) {
        super(properties);
        this.tier = tier;
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return simpleCodec(p -> new ShieldEmitterBlock(p, tier));
    }

    /** Leben je Schildblock. */
    public static float maxHp(int tier) {
        return switch (tier) {
            // die schwächste Stufe ist so stark wie früher das Schild der E, die anderen im gleichen Verhältnis
            case 1 -> 260;
            case 2 -> 390;
            case 3 -> 520;
            case 4 -> 690;
            case 5 -> 870;
            case 6 -> 1130;
            default -> tier >= 7 ? 1420 : 170;
        };
    }
}

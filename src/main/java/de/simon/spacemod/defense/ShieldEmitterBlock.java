package de.simon.spacemod.defense;

import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;

/**
 * Schildemitter einer Enterprise (Stufe 1 = NCC-1701 ... 6 = NCC-1701-E). Je höher die Stufe, desto
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
            case 1 -> 60;
            case 2 -> 90;
            case 3 -> 120;
            case 4 -> 160;
            case 5 -> 200;
            default -> tier >= 6 ? 260 : 40;
        };
    }
}

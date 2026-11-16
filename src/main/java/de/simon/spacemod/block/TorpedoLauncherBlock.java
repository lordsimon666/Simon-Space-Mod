package de.simon.spacemod.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;

/**
 * Torpedowerfer an der Hülle (Photonen- oder Quantentorpedos). Gefeuert wird mit P / O. Jeder Werfer hat
 * ein eigenes Magazin und lädt selbst nach: normale Werfer 1 Schuss, die Spezialwerfer der C und D und
 * der Schnellfeuerwerfer der E 5 Schüsse. Die Werfer der C und D können alle 5 auf einmal feuern
 * (Full Spread, Taste F).
 */
public class TorpedoLauncherBlock extends Block {
    public final boolean quantum;
    /** Schüsse bis zum Nachladen. */
    public final int magazine;
    /** Kann alle Schüsse auf einmal fächerförmig abfeuern (Full Spread). */
    public final boolean spread;

    public TorpedoLauncherBlock(BlockBehaviour.Properties properties, boolean quantum) {
        this(properties, quantum, 1, false);
    }

    public TorpedoLauncherBlock(BlockBehaviour.Properties properties, boolean quantum, int magazine, boolean spread) {
        super(properties);
        this.quantum = quantum;
        this.magazine = magazine;
        this.spread = spread;
    }

    /** Nachladezeit in Ticks, nachdem der letzte Schuss gefallen ist. */
    public int reloadTicks() {
        if (magazine <= 1) return quantum ? 50 : 30;
        return quantum ? 140 : 120;
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return simpleCodec(p -> new TorpedoLauncherBlock(p, quantum, magazine, spread));
    }
}

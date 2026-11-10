package de.simon.spacemod.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;

/** Ein Phaser an der Außenhülle. Gefeuert wird von der Waffenstation auf der Brücke. */
public class PhaserBlock extends Block {
    /** Waffenart mit Strahlfarbe und ob sie Pulse (NX-Phasenkanone) statt Dauerstrahl schießt (Schaden: siehe PhaserControl). */
    public enum Kind {
        PHASE_CANNON(0x7FD8FF, 0xE8FAFF, true),
        PHASER_BANK(0x4F8CFF, 0xD8E6FF, false),
        PHASER_EMITTER(0xFF3A18, 0xFFD0A0, false),
        PHASER_STRIP(0xFF8418, 0xFFE8B0, false);

        public final int color, core;
        public final boolean pulse;

        Kind(int color, int core, boolean pulse) {
            this.color = color;
            this.core = core;
            this.pulse = pulse;
        }
    }

    public final Kind kind;

    public PhaserBlock(BlockBehaviour.Properties properties, Kind kind) {
        super(properties);
        this.kind = kind;
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return simpleCodec(p -> new PhaserBlock(p, kind));
    }
}

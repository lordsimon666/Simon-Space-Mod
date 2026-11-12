package de.simon.spacemod.flight;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** Erfasste Blöcke eines fliegenden Schiffs: Lage relativ zum Drehpunkt (Weltachsen beim Abheben). */
public final class ShipStructure {
    public record Entry(BlockPos pos, BlockState state, Optional<CompoundTag> data) {
        public static final Codec<Entry> CODEC = RecordCodecBuilder.create(i -> i.group(
            BlockPos.CODEC.fieldOf("p").forGetter(Entry::pos),
            BlockState.CODEC.fieldOf("s").forGetter(Entry::state),
            CompoundTag.CODEC.optionalFieldOf("d").forGetter(Entry::data)
        ).apply(i, Entry::new));
    }

    public static final Codec<List<Entry>> LIST_CODEC = Entry.CODEC.listOf();

    public final List<Entry> blocks;
    /** Außen sichtbare Blöcke (mind. eine Seite ohne Nachbarn): Kollision und Zeichnen. */
    public final List<Entry> surface = new ArrayList<>();
    public final int engines;
    public final int powerPerTick;
    public final double inertia;
    public final double radius;

    public ShipStructure(List<Entry> blocks) {
        this.blocks = List.copyOf(blocks);
        Set<BlockPos> all = new HashSet<>();
        List<BlockPos> positions = new ArrayList<>();
        int eng = 0, power = 0;
        double r = 1;
        for (Entry e : blocks) {
            all.add(e.pos());
            positions.add(e.pos());
            Block b = e.state().getBlock();
            if (b == de.simon.spacemod.ModBlocks.IMPULSE_ENGINE.get()) eng++;
            if (b instanceof de.simon.spacemod.block.WarpCoreBlock w) power += w.rfPerTick;
            r = Math.max(r, Math.sqrt(e.pos().distSqr(BlockPos.ZERO)) + 1);
        }
        for (Entry e : blocks) {
            for (Direction d : Direction.values())
                if (!all.contains(e.pos().relative(d))) { surface.add(e); break; }
        }
        this.engines = eng;
        this.powerPerTick = power;
        this.inertia = ShipPhysics.inertia(positions);
        this.radius = r;
    }

    public double mass() {
        return blocks.size();
    }
}

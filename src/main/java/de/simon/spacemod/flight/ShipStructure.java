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

    private java.util.Map<BlockPos, net.minecraft.world.phys.shapes.VoxelShape> collision;

    /** Kollisionsformen der Blöcke im Schiffssystem (Block p liegt in [p - 0.5, p + 0.5]). */
    public synchronized java.util.Map<BlockPos, net.minecraft.world.phys.shapes.VoxelShape> collision() {
        if (collision == null) {
            collision = new java.util.HashMap<>();
            for (Entry e : blocks) {
                net.minecraft.world.phys.shapes.VoxelShape shape;
                try {
                    shape = e.state().getCollisionShape(net.minecraft.world.level.EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
                } catch (RuntimeException ex) {
                    shape = net.minecraft.world.phys.shapes.Shapes.block();
                }
                if (!shape.isEmpty()) collision.put(e.pos(), shape.move(e.pos().getX() - 0.5, e.pos().getY() - 0.5, e.pos().getZ() - 0.5));
            }
        }
        return collision;
    }

    private java.util.Map<BlockPos, Integer> index;

    /** Index des Blocks an einer lokalen Stelle in {@link #blocks} oder -1. */
    public synchronized int indexOf(BlockPos local) {
        if (index == null) {
            index = new java.util.HashMap<>();
            for (int i = 0; i < blocks.size(); i++) index.put(blocks.get(i).pos(), i);
        }
        return index.getOrDefault(local, -1);
    }

    /**
     * Strahl im Schiffssystem (Block p liegt in [p - 0.5, p + 0.5]): erster Block mit Kollisionsform
     * oder Blockdaten innerhalb von {@code reach}, sonst null.
     */
    public @org.jspecify.annotations.Nullable BlockPos raycast(net.minecraft.world.phys.Vec3 from, net.minecraft.world.phys.Vec3 dir, double reach) {
        net.minecraft.world.phys.Vec3 d = dir.normalize();
        for (double t = 0; t <= reach; t += 0.05) {
            net.minecraft.world.phys.Vec3 p = from.add(d.scale(t));
            BlockPos b = BlockPos.containing(p.x + 0.5, p.y + 0.5, p.z + 0.5);
            int i = indexOf(b);
            if (i < 0) continue;
            if (blocks.get(i).data().isPresent()) return b;
            net.minecraft.world.phys.shapes.VoxelShape shape = collision().get(b);
            if (shape != null && !shape.isEmpty()) {
                for (net.minecraft.world.phys.AABB box : shape.toAabbs())
                    if (box.inflate(1e-4).contains(p)) return b;
            }
        }
        return null;
    }

    public double mass() {
        return blocks.size();
    }
}

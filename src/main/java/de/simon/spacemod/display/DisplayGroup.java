package de.simon.spacemod.display;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Bildschirm-Multiblock: alle Bildschirme in einer Ebene, die in dieselbe Richtung zeigen und aneinander
 * grenzen, bilden eine Bildfläche – wenn sie ein volles Rechteck von 1×1 bis 16×16 ergeben. Sonst ist jeder
 * Block ein eigener Bildschirm.
 *
 * @param anchor  untere linke Ecke (vom Betrachter aus), sie zeichnet das ganze Bild
 * @param right   Richtung nach rechts (vom Betrachter vor dem Bildschirm aus)
 * @param width   Breite in Blöcken, {@code height} Höhe
 * @param blocks  alle Blöcke der Fläche
 */
public record DisplayGroup(BlockPos anchor, Direction facing, Direction right, int width, int height, List<BlockPos> blocks) {
    public static final int MAX = 16;

    /** Bildschirmgruppe des Blocks bei {@code pos}; {@code states} liefert die Blöcke (Welt oder fliegendes Schiff). */
    public static DisplayGroup of(Function<BlockPos, BlockState> states, BlockPos pos) {
        BlockState st = states.apply(pos);
        Direction facing = st.hasProperty(HorizontalDirectionalBlock.FACING) ? st.getValue(HorizontalDirectionalBlock.FACING) : Direction.NORTH;
        Direction right = facing.getCounterClockWise(); // vom Betrachter aus rechts
        Set<BlockPos> seen = new HashSet<>();
        ArrayDeque<BlockPos> q = new ArrayDeque<>();
        seen.add(pos);
        q.add(pos);
        while (!q.isEmpty() && seen.size() <= MAX * MAX) {
            BlockPos p = q.poll();
            for (Direction d : new Direction[] {Direction.UP, Direction.DOWN, right, right.getOpposite()}) {
                BlockPos n = p.relative(d);
                if (seen.contains(n)) continue;
                BlockState ns = states.apply(n);
                if (ns.getBlock() instanceof DisplayBlock && ns.getValue(HorizontalDirectionalBlock.FACING) == facing) {
                    seen.add(n);
                    q.add(n);
                }
            }
        }
        // Ausdehnung entlang "rechts" und nach oben
        int minR = Integer.MAX_VALUE, maxR = Integer.MIN_VALUE, minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;
        for (BlockPos p : seen) {
            int r = along(p, right);
            minR = Math.min(minR, r); maxR = Math.max(maxR, r);
            minY = Math.min(minY, p.getY()); maxY = Math.max(maxY, p.getY());
        }
        int w = maxR - minR + 1, h = maxY - minY + 1;
        if (seen.size() > MAX * MAX || w > MAX || h > MAX || w * h != seen.size())
            return new DisplayGroup(pos, facing, right, 1, 1, List.of(pos)); // kein Rechteck: einzeln
        BlockPos anchor = null;
        for (BlockPos p : seen) if (along(p, right) == minR && p.getY() == minY) anchor = p;
        return new DisplayGroup(anchor, facing, right, w, h, new ArrayList<>(seen));
    }

    private static int along(BlockPos p, Direction d) {
        return p.getX() * d.getStepX() + p.getZ() * d.getStepZ();
    }

    public boolean isAnchor(BlockPos p) {
        return anchor.equals(p);
    }
}

package de.simon.spacemod.storage;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/**
 * Form der Großkiste (ohne Minecraft-Welt, testbar): ein hohler Quader aus Kistenblöcken, jede Kante
 * 3 bis 8 Blöcke lang. Alle sechs Seiten sind voll, das Innere ist Luft. Gezählt wird das ganze Volumen.
 */
public final class CrateStructure {
    public static final int MIN = 3, MAX = 8;
    /** Mehr Kistenblöcke kann eine gültige Kiste nicht haben (8³ − 6³). */
    public static final int MAX_SHELL = MAX * MAX * MAX - (MAX - 2) * (MAX - 2) * (MAX - 2);

    public record Box(BlockPos min, int sx, int sy, int sz) {
        public int volume() { return sx * sy * sz; }
        public BlockPos max() { return min.offset(sx - 1, sy - 1, sz - 1); }
        public boolean contains(BlockPos p) {
            return p.getX() >= min.getX() && p.getY() >= min.getY() && p.getZ() >= min.getZ()
                && p.getX() < min.getX() + sx && p.getY() < min.getY() + sy && p.getZ() < min.getZ() + sz;
        }
        public boolean onShell(BlockPos p) {
            int x = p.getX() - min.getX(), y = p.getY() - min.getY(), z = p.getZ() - min.getZ();
            return x == 0 || y == 0 || z == 0 || x == sx - 1 || y == sy - 1 || z == sz - 1;
        }
    }

    private CrateStructure() {}

    /** Alle zusammenhängenden Kistenblöcke ab start (höchstens MAX_SHELL + 1, dann ist es zu groß). */
    public static Set<BlockPos> connected(BlockPos start, Predicate<BlockPos> isCrate) {
        Set<BlockPos> seen = new HashSet<>();
        ArrayDeque<BlockPos> q = new ArrayDeque<>();
        if (!isCrate.test(start)) return seen;
        seen.add(start.immutable());
        q.add(start.immutable());
        while (!q.isEmpty() && seen.size() <= MAX_SHELL) {
            BlockPos p = q.poll();
            for (Direction d : Direction.values()) {
                BlockPos n = p.relative(d);
                if (!seen.contains(n) && isCrate.test(n)) { seen.add(n); q.add(n); }
            }
        }
        return seen;
    }

    /** Gültiger hohler Quader aus genau diesen Blöcken, sonst null. */
    public static Box validate(Set<BlockPos> blocks, Predicate<BlockPos> isAir) {
        if (blocks.isEmpty() || blocks.size() > MAX_SHELL) return null;
        int x0 = Integer.MAX_VALUE, y0 = Integer.MAX_VALUE, z0 = Integer.MAX_VALUE, x1 = Integer.MIN_VALUE, y1 = Integer.MIN_VALUE, z1 = Integer.MIN_VALUE;
        for (BlockPos p : blocks) {
            x0 = Math.min(x0, p.getX()); y0 = Math.min(y0, p.getY()); z0 = Math.min(z0, p.getZ());
            x1 = Math.max(x1, p.getX()); y1 = Math.max(y1, p.getY()); z1 = Math.max(z1, p.getZ());
        }
        int sx = x1 - x0 + 1, sy = y1 - y0 + 1, sz = z1 - z0 + 1;
        if (sx < MIN || sy < MIN || sz < MIN || sx > MAX || sy > MAX || sz > MAX) return null;
        Box box = new Box(new BlockPos(x0, y0, z0), sx, sy, sz);
        int shell = box.volume() - (sx - 2) * (sy - 2) * (sz - 2);
        if (blocks.size() != shell) return null;
        for (BlockPos p : blocks) if (!box.onShell(p)) return null;
        for (int x = 1; x < sx - 1; x++)
            for (int y = 1; y < sy - 1; y++)
                for (int z = 1; z < sz - 1; z++)
                    if (!isAir.test(box.min().offset(x, y, z))) return null;
        return box;
    }
}

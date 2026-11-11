package de.simon.spacemod.power;

import de.simon.spacemod.SimonSpaceMod;
import de.simon.spacemod.block.WarpCoreBlock;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Bordnetz: Warpkerne erzeugen Strom, alle Blöcke der Mod (Hülle, Wände, Decks, Warpkerne) leiten
 * ihn. Ein Verbraucher (Ladestation) hängt an allen Warpkernen, die über solche Blöcke mit ihm
 * verbunden sind. Bei gespawnten Schiffen sind die Kerne gespeichert, sonst werden sie gesucht.
 */
public final class ShipPower {
    private ShipPower() {}

    /** Netz: Schlüssel (für die gemeinsame Leistung) und Leistung in RF/t. */
    public record Grid(long key, int output) {
        static final Grid NONE = new Grid(Long.MIN_VALUE, 0);
    }

    private static final int SEARCH_LIMIT = 60_000;
    private static final int RECHECK_TICKS = 100;

    private record Cached(Grid grid, long time) {}

    private static final Map<String, Cached> CACHE = new HashMap<>();
    /** Pro Netz und Tick noch verfügbare Leistung (mehrere Stationen teilen sich den Kern). */
    private static final Map<String, long[]> BUDGET = new HashMap<>();

    public static Grid grid(ServerLevel level, BlockPos consumer) {
        String key = level.dimension().identifier() + "@" + consumer.asLong();
        long now = level.getGameTime();
        Cached c = CACHE.get(key);
        if (c != null && now - c.time() < RECHECK_TICKS) return c.grid();
        Grid g = compute(level, consumer);
        CACHE.put(key, new Cached(g, now));
        return g;
    }

    /** Holt bis zu {@code want} RF aus dem Netz (in diesem Tick). */
    public static int draw(ServerLevel level, Grid grid, int want) {
        String key = level.dimension().identifier() + "#" + grid.key();
        long now = level.getGameTime();
        long[] b = BUDGET.computeIfAbsent(key, k -> new long[] {-1, 0});
        if (b[0] != now) {
            b[0] = now;
            b[1] = grid.output();
        }
        int got = (int) Math.min(want, b[1]);
        b[1] -= got;
        return got;
    }

    private static Grid compute(ServerLevel level, BlockPos consumer) {
        List<BlockPos> cores = PowerData.get(level).cores(consumer);
        if (cores == null) cores = search(level, consumer);
        int out = 0;
        long key = Long.MAX_VALUE;
        for (BlockPos p : cores) {
            BlockState st = level.getBlockState(p);
            if (st.getBlock() instanceof WarpCoreBlock w) {
                out += w.rfPerTick;
                key = Math.min(key, p.asLong());
            }
        }
        return out == 0 ? Grid.NONE : new Grid(key, out);
    }

    /** Breitensuche über leitende Blöcke bis zu den Warpkernen. */
    private static List<BlockPos> search(ServerLevel level, BlockPos start) {
        List<BlockPos> cores = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        queue.add(start);
        seen.add(start.asLong());
        while (!queue.isEmpty() && seen.size() < SEARCH_LIMIT) {
            BlockPos p = queue.poll();
            for (Direction d : Direction.values()) {
                BlockPos n = p.relative(d);
                if (!seen.add(n.asLong()) || !level.isLoaded(n)) continue;
                BlockState st = level.getBlockState(n);
                if (st.getBlock() instanceof WarpCoreBlock) cores.add(n);
                if (conducts(st)) queue.add(n);
            }
        }
        return cores;
    }

    /** Leiten Strom: alle Blöcke der Mod (Hülle, Innenausbau, Warpkerne). */
    public static boolean conducts(BlockState st) {
        Identifier id = ForgeRegistries.BLOCKS.getKey(st.getBlock());
        return id != null && id.getNamespace().equals(SimonSpaceMod.MODID);
    }
}

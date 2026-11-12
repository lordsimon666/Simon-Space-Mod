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

    /**
     * Netz: Schlüssel (für die gemeinsame Leistung), feste Leistung der Warpkerne in RF/t und alle
     * weiteren Stromerzeuger mit Speicher, aus denen entnommen wird (Reaktoren, Generatoren anderer Mods).
     */
    public record Grid(long key, int output, List<BlockPos> sources) {
        static final Grid NONE = new Grid(Long.MIN_VALUE, 0, List.of());

        public boolean hasPower(ServerLevel level) {
            if (output > 0) return true;
            for (BlockPos p : sources) if (extractFrom(level, p, 1, true) > 0) return true;
            return false;
        }
    }

    /** Liefert dieser Block Strom (Speicher mit Entnahme, kein Warpkern, keine Ladestation)? */
    public static boolean isSource(ServerLevel level, BlockPos p) {
        net.minecraft.world.level.block.entity.BlockEntity be = level.getBlockEntity(p);
        if (be == null || be instanceof de.simon.spacemod.block.entity.ChargingStationBlockEntity) return false;
        return be.getCapability(net.minecraftforge.common.capabilities.ForgeCapabilities.ENERGY, null)
            .map(net.minecraftforge.energy.IEnergyStorage::canExtract).orElse(false);
    }

    static int extractFrom(ServerLevel level, BlockPos p, int max, boolean simulate) {
        if (!level.isLoaded(p)) return 0;
        net.minecraft.world.level.block.entity.BlockEntity be = level.getBlockEntity(p);
        if (be == null) return 0;
        return be.getCapability(net.minecraftforge.common.capabilities.ForgeCapabilities.ENERGY, null)
            .map(e -> e.canExtract() ? e.extractEnergy(max, simulate) : 0).orElse(0);
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

    private static final Map<String, long[]> SHIP_POWER = new HashMap<>();

    /** Licht auf 0 % gestellt (z. B. im Kampf): Lampen aus. */
    private static boolean lightsAllowed(ServerLevel level, Grid grid) {
        Alloc a = alloc(level, grid);
        return a == null || a.shares()[Category.LIGHTS.ordinal()] > 0;
    }

    /**
     * Hat ein Verbraucher (Lampe) Strom? In gespawnten Schiffen: steht noch einer ihrer Warpkerne
     * (nicht geladene Kerne zählen als vorhanden); sonst Suche über leitende Blöcke.
     */
    public static boolean powered(ServerLevel level, BlockPos pos) {
        PowerData.Ship ship = PowerData.get(level).shipAt(pos);
        if (ship == null) {
            Grid g = grid(level, pos);
            return g.hasPower(level) && lightsAllowed(level, g);
        }
        String key = level.dimension().identifier() + "!" + ship.min().asLong() + "/" + ship.max().asLong();
        long now = level.getGameTime();
        long[] c = SHIP_POWER.get(key);
        if (c != null && now - c[0] < 40) return c[1] != 0;
        boolean on = false;
        for (BlockPos core : ship.cores()) {
            if (!level.isLoaded(core) || level.getBlockState(core).getBlock() instanceof WarpCoreBlock) { on = true; break; }
        }
        SHIP_POWER.put(key, new long[] {now, on ? 1 : 0});
        // Ohne Warpkern: vielleicht versorgt ein Reaktor oder ein anderer Generator das Netz
        if (!on && !grid(level, pos).hasPower(level)) return false;
        return lightsAllowed(level, gridFor(level, pos));
    }

    /**
     * Netz für einen Verbraucher, der in einem gespawnten Schiff stehen kann (Phaser): dort die
     * gespeicherten Warpkerne des Schiffs, sonst die Suche über leitende Blöcke.
     */
    public static Grid gridFor(ServerLevel level, BlockPos pos) {
        PowerData.Ship ship = PowerData.get(level).shipAt(pos);
        if (ship == null) return grid(level, pos);
        String key = level.dimension().identifier() + "!grid" + ship.min().asLong() + "/" + ship.max().asLong();
        long now = level.getGameTime();
        Cached c = CACHE.get(key);
        if (c != null && now - c.time() < RECHECK_TICKS) return c.grid();
        Grid g = gridOf(level, ship.cores());
        if (g == Grid.NONE) g = grid(level, pos); // Kerne zerstört: vielleicht ein Reaktor im Netz
        CACHE.put(key, new Cached(g, now));
        return g;
    }

    /** Verbrauchergruppen für die Energieverteilung (Energieverteilungskonsole). */
    public enum Category { WEAPONS, SHIELDS, ENGINES, LIGHTS, MACHINES }

    /** Eingestellte Verteilung eines Netzes: Anteil je Gruppe in Prozent, Warpkern-Overdrive. */
    private record Alloc(int[] shares, boolean overdrive, long time) {}

    private static final Map<String, Alloc> ALLOC = new HashMap<>();
    private static final Map<String, long[]> USED = new HashMap<>();

    /** Von der Energieverteilungskonsole regelmäßig gemeldet (verfällt nach 3 s ohne Meldung). */
    public static void allocate(ServerLevel level, Grid grid, int[] shares, boolean overdrive) {
        if (grid == Grid.NONE) return;
        ALLOC.put(level.dimension().identifier() + "#" + grid.key(), new Alloc(shares.clone(), overdrive, level.getGameTime()));
    }

    private static @org.jspecify.annotations.Nullable Alloc alloc(ServerLevel level, Grid grid) {
        Alloc a = ALLOC.get(level.dimension().identifier() + "#" + grid.key());
        return a == null || level.getGameTime() - a.time() > 60 ? null : a;
    }

    /** Leistung der Warpkerne im Netz (mit Overdrive +50 %). */
    public static long effectiveOutput(ServerLevel level, Grid grid) {
        Alloc a = alloc(level, grid);
        return a != null && a.overdrive() ? grid.output() * 3L / 2 : grid.output();
    }

    /** Holt bis zu {@code want} RF aus dem Netz (in diesem Tick) für allgemeine Maschinen. */
    public static int draw(ServerLevel level, Grid grid, int want) {
        return draw(level, grid, want, Category.MACHINES);
    }

    /** Holt bis zu {@code want} RF aus dem Netz für eine Verbrauchergruppe (begrenzt durch die Verteilung). */
    public static int draw(ServerLevel level, Grid grid, int want, Category cat) {
        String key = level.dimension().identifier() + "#" + grid.key();
        long now = level.getGameTime();
        Alloc a = alloc(level, grid);
        long out = effectiveOutput(level, grid);
        long[] b = BUDGET.computeIfAbsent(key, k -> new long[] {-1, 0});
        long[] used = USED.computeIfAbsent(key, k -> new long[Category.values().length + 1]);
        if (b[0] != now) {
            b[0] = now;
            b[1] = out;
        }
        if (used[0] != now) {
            java.util.Arrays.fill(used, 0);
            used[0] = now;
        }
        if (a != null) {
            int share = a.shares()[cat.ordinal()];
            if (out > 0) want = (int) Math.max(0, Math.min(want, out * share / 100 - used[cat.ordinal() + 1]));
            else want = want * share / 100;
        }
        if (want <= 0) return 0;
        int got = (int) Math.min(want, b[1]);
        b[1] -= got;
        // Rest aus Reaktoren und anderen Stromerzeugern
        for (BlockPos p : grid.sources()) {
            if (got >= want) break;
            got += extractFrom(level, p, want - got, false);
        }
        used[cat.ordinal() + 1] += got;
        return got;
    }

    private static Grid compute(ServerLevel level, BlockPos consumer) {
        List<BlockPos> cores = PowerData.get(level).cores(consumer);
        if (cores == null || cores.stream().noneMatch(p -> level.getBlockState(p).getBlock() instanceof WarpCoreBlock))
            cores = search(level, consumer);
        return gridOf(level, cores);
    }

    /** Verbraucher am Netz: Position und Seite, von der aus er Strom annimmt. */
    public record Consumer(BlockPos pos, @org.jspecify.annotations.Nullable Direction side) {}

    /** Ergebnis der Suche: Erzeuger (Warpkerne und Speicher) und Verbraucher am Netz. */
    public record Found(List<BlockPos> cores, List<Consumer> consumers, List<BlockPos> alarms) {}

    private static List<BlockPos> search(ServerLevel level, BlockPos start) {
        return explore(level, start).cores();
    }

    /** Netz ab einem Block absuchen (über leitende Blöcke der Mod). */
    public static Found explore(ServerLevel level, BlockPos start) {
        List<BlockPos> cores = new ArrayList<>();
        List<Consumer> consumers = new ArrayList<>();
        List<BlockPos> alarms = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        queue.add(start);
        seen.add(start.asLong());
        classify(level, start, null, cores, consumers);
        while (!queue.isEmpty() && seen.size() < SEARCH_LIMIT) {
            BlockPos p = queue.poll();
            for (Direction d : Direction.values()) {
                BlockPos n = p.relative(d);
                if (!seen.add(n.asLong()) || !level.isLoaded(n)) continue;
                BlockState st = level.getBlockState(n);
                classify(level, n, d.getOpposite(), cores, consumers);
                if (st.getBlock() instanceof de.simon.spacemod.defense.AlarmLightBlock) alarms.add(n.immutable());
                if (conducts(st)) queue.add(n);
            }
        }
        return new Found(cores, consumers, alarms);
    }

    private static void classify(ServerLevel level, BlockPos n, @org.jspecify.annotations.Nullable Direction side, List<BlockPos> cores, List<Consumer> consumers) {
        BlockState st = level.getBlockState(n);
        if (st.getBlock() instanceof WarpCoreBlock) { cores.add(n); return; }
        if (!st.hasBlockEntity()) return;
        if (isSource(level, n)) { cores.add(n); return; }
        net.minecraft.world.level.block.entity.BlockEntity be = level.getBlockEntity(n);
        if (be == null || be instanceof de.simon.spacemod.block.entity.ChargingStationBlockEntity) return;
        boolean takes = be.getCapability(net.minecraftforge.common.capabilities.ForgeCapabilities.ENERGY, side)
            .map(net.minecraftforge.energy.IEnergyStorage::canReceive).orElse(false);
        if (takes) consumers.add(new Consumer(n.immutable(), side));
    }

    /** Netz mit Leistung aus einer Liste von Erzeugern. */
    public static Grid gridOf(ServerLevel level, List<BlockPos> cores) {
        long out = 0;
        long key = Long.MAX_VALUE;
        List<BlockPos> sources = new ArrayList<>();
        for (BlockPos p : cores) {
            BlockState st = level.getBlockState(p);
            if (st.getBlock() instanceof WarpCoreBlock w) {
                out += w.rfPerTick;
                key = Math.min(key, p.asLong());
            } else if (isSource(level, p)) {
                sources.add(p);
                key = Math.min(key, p.asLong());
            }
        }
        return out == 0 && sources.isEmpty() ? Grid.NONE : new Grid(key, (int) Math.min(Integer.MAX_VALUE, out), sources);
    }

    /** Leiten Strom: alle Blöcke der Mod (Hülle, Innenausbau, Warpkerne). */
    public static boolean conducts(BlockState st) {
        Identifier id = ForgeRegistries.BLOCKS.getKey(st.getBlock());
        return id != null && id.getNamespace().equals(SimonSpaceMod.MODID);
    }
}

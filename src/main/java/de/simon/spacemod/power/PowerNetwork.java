package de.simon.spacemod.power;

import de.simon.spacemod.block.WarpCoreBlock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.BlockEvent;

/**
 * Stromverteilung über die Hülle: jedes Netz aus leitenden Blöcken der Mod gibt den Strom seiner
 * Erzeuger (Warpkerne, Reaktoren, Generatoren anderer Mods) an alle angrenzenden Verbraucher ab –
 * jede Maschine, die Forge-Energie (RF/FE) annimmt, auch aus anderen Mods. Läuft auch in der Werft,
 * also im Flug.
 */
public final class PowerNetwork {
    private static final int RECHECK = 100;
    private static final int MAX_PUSH = 1_000_000;

    private record Net(ShipPower.Grid grid, List<ShipPower.Consumer> consumers, Set<Long> members, long time) {}

    private static final Map<String, Net> CACHE = new HashMap<>();

    private PowerNetwork() {}

    public static void init() {
        TickEvent.ServerTickEvent.Post.BUS.addListener(e -> tick(e.server()));
        BlockEvent.EntityPlaceEvent.BUS.addListener(e -> {
            if (e.getLevel() instanceof ServerLevel level && e.getPlacedBlock().hasBlockEntity()) addSeed(level, e.getPos());
            return false;
        });
    }

    public static void addSeed(ServerLevel level, BlockPos pos) {
        PowerData.get(level).addSeed(pos);
    }

    private static void tick(MinecraftServer server) {
        for (ServerLevel level : server.getAllLevels()) {
            PowerData data = PowerData.get(level);
            if (data.seeds().isEmpty()) continue;
            long now = level.getGameTime();
            Set<Long> done = new HashSet<>();
            List<Long> dead = new ArrayList<>();
            for (long s : List.copyOf(data.seeds())) {
                if (done.contains(s)) continue;
                BlockPos p = BlockPos.of(s);
                if (!level.isLoaded(p)) continue;
                if (now % 200 == Math.floorMod(s, 200) && !valid(level, p)) { dead.add(s); continue; }
                Net net = net(level, p, now);
                done.addAll(net.members());
                if (net.consumers().isEmpty() || net.grid() == ShipPower.Grid.NONE) continue;
                for (ShipPower.Consumer c : net.consumers()) push(level, net.grid(), c);
            }
            for (long d : dead) data.removeSeed(d);
        }
    }

    private static void push(ServerLevel level, ShipPower.Grid grid, ShipPower.Consumer c) {
        if (!level.isLoaded(c.pos())) return;
        BlockEntity be = level.getBlockEntity(c.pos());
        if (be == null) return;
        be.getCapability(ForgeCapabilities.ENERGY, c.side()).ifPresent(e -> {
            if (!e.canReceive()) return;
            int want = e.receiveEnergy(MAX_PUSH, true);
            if (want <= 0) return;
            int got = ShipPower.draw(level, grid, want);
            if (got > 0) e.receiveEnergy(got, false);
        });
    }

    private static Net net(ServerLevel level, BlockPos seed, long now) {
        String key = level.dimension().identifier() + "@" + seed.asLong();
        Net n = CACHE.get(key);
        if (n != null && now - n.time() < RECHECK) return n;
        ShipPower.Found f = ShipPower.explore(level, seed);
        Set<Long> members = new HashSet<>();
        for (BlockPos p : f.cores()) members.add(p.asLong());
        for (ShipPower.Consumer c : f.consumers()) members.add(c.pos().asLong());
        n = new Net(ShipPower.gridOf(level, f.cores()), f.consumers(), members, now);
        // andere Startpunkte im selben Netz nutzen dasselbe Ergebnis
        for (long m : members) CACHE.put(level.dimension().identifier() + "@" + m, n);
        CACHE.put(key, n);
        if (CACHE.size() > 20000) CACHE.clear();
        return n;
    }

    private static boolean valid(ServerLevel level, BlockPos p) {
        BlockState st = level.getBlockState(p);
        if (st.getBlock() instanceof WarpCoreBlock) return true;
        BlockEntity be = level.getBlockEntity(p);
        return be != null && be.getCapability(ForgeCapabilities.ENERGY, null).isPresent();
    }
}

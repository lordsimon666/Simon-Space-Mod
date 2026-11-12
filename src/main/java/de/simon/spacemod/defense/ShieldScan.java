package de.simon.spacemod.defense;

import de.simon.spacemod.flight.ShipAssembler;
import de.simon.spacemod.power.PowerData;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;

/**
 * Liest die Hülle eines Schiffs für das Schild ein: bei gespawnten Enterprises den gespeicherten
 * Bauraum (Abschnitt für Abschnitt über mehrere Ticks), bei selbst gebauten Schiffen alle mit der
 * Konsole verbundenen Schiffsblöcke. Danach wird die Schildform im Hintergrund berechnet.
 */
final class ShieldScan {
    private static final int SECTIONS_PER_TICK = 96;
    private static final int GATHER_CAP = 300_000;

    record Result(ShieldShape shape, int cells) {}

    final BlockPos origin;
    final ShieldShape raster;
    final List<BlockPos> emitters = new ArrayList<>();
    private final ServerLevel level;
    private final BlockPos min, max;
    private int sx, sy, sz; // nächster Abschnitt
    private boolean scanned;
    private CompletableFuture<Result> future;

    private ShieldScan(ServerLevel level, BlockPos min, BlockPos max) {
        this.level = level;
        this.min = min;
        this.max = max;
        int m = ShieldShape.MARGIN;
        this.origin = min.offset(-m, -m, -m);
        this.raster = new ShieldShape(max.getX() - min.getX() + 1 + 2 * m, max.getY() - min.getY() + 1 + 2 * m, max.getZ() - min.getZ() + 1 + 2 * m);
        this.sx = min.getX() >> 4;
        this.sy = min.getY() >> 4;
        this.sz = min.getZ() >> 4;
    }

    static ShieldScan start(ServerLevel level, BlockPos console) {
        PowerData.Ship ship = PowerData.get(level).shipAt(console);
        if (ship != null) return new ShieldScan(level, ship.min(), ship.max());
        // selbst gebaut: verbundene Schiffsblöcke sammeln
        Set<Long> seen = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        List<BlockPos> found = new ArrayList<>();
        queue.add(console);
        seen.add(console.asLong());
        int x0 = console.getX(), y0 = console.getY(), z0 = console.getZ(), x1 = x0, y1 = y0, z1 = z0;
        while (!queue.isEmpty() && found.size() < GATHER_CAP) {
            BlockPos p = queue.poll();
            found.add(p);
            x0 = Math.min(x0, p.getX()); y0 = Math.min(y0, p.getY()); z0 = Math.min(z0, p.getZ());
            x1 = Math.max(x1, p.getX()); y1 = Math.max(y1, p.getY()); z1 = Math.max(z1, p.getZ());
            for (Direction d : Direction.values()) {
                BlockPos n = p.relative(d);
                if (!seen.add(n.asLong()) || !level.isLoaded(n)) continue;
                if (ShipAssembler.isShipBlock(level.getBlockState(n))) queue.add(n);
            }
        }
        ShieldScan s = new ShieldScan(level, new BlockPos(x0, y0, z0), new BlockPos(x1, y1, z1));
        for (BlockPos p : found) {
            s.mark(p.getX(), p.getY(), p.getZ());
            if (level.getBlockState(p).getBlock() instanceof ShieldEmitterBlock) s.emitters.add(p);
        }
        s.scanned = true;
        return s;
    }

    private void mark(int wx, int wy, int wz) {
        raster.set(wx - origin.getX(), wy - origin.getY(), wz - origin.getZ());
    }

    /** Ein Schritt; @return Ergebnis, sobald fertig berechnet, sonst null. */
    Result step() {
        if (!scanned) {
            int done = 0;
            while (done < SECTIONS_PER_TICK && !scanned) {
                scanSection(sx, sy, sz);
                done++;
                if (++sx > (max.getX() >> 4)) {
                    sx = min.getX() >> 4;
                    if (++sz > (max.getZ() >> 4)) {
                        sz = min.getZ() >> 4;
                        if (++sy > (max.getY() >> 4)) scanned = true;
                    }
                }
            }
            return null;
        }
        if (future == null) {
            future = CompletableFuture.supplyAsync(() -> {
                ShieldShape p = raster.protectedSpace();
                int cells = 0;
                for (int y = 0; y < p.sy; y++)
                    for (int z = 0; z < p.sz; z++)
                        for (int x = 0; x < p.sx; x++)
                            if (p.surface(x, y, z)) cells++;
                return new Result(p, cells);
            }, net.minecraft.util.Util.backgroundExecutor());
            return null;
        }
        return future.isDone() ? future.join() : null;
    }

    private void scanSection(int cx, int cy, int cz) {
        if (cy < level.getMinSectionY() || cy > level.getMaxSectionY()) return;
        if (!level.hasChunk(cx, cz)) level.getChunk(cx, cz); // laden (gespawnte Schiffe sind groß)
        LevelChunk chunk = level.getChunk(cx, cz);
        LevelChunkSection sec = chunk.getSection(level.getSectionIndexFromSectionY(cy));
        if (sec.hasOnlyAir()) return;
        for (int y = 0; y < 16; y++)
            for (int z = 0; z < 16; z++)
                for (int x = 0; x < 16; x++) {
                    int wx = cx * 16 + x, wy = cy * 16 + y, wz = cz * 16 + z;
                    if (wx < min.getX() || wx > max.getX() || wy < min.getY() || wy > max.getY() || wz < min.getZ() || wz > max.getZ()) continue;
                    BlockState st = sec.getBlockState(x, y, z);
                    if (st.isAir() || !ShipAssembler.isShipBlock(st)) continue;
                    mark(wx, wy, wz);
                    if (st.getBlock() instanceof ShieldEmitterBlock) emitters.add(new BlockPos(wx, wy, wz));
                }
    }
}

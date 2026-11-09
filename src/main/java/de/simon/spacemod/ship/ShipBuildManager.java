package de.simon.spacemod.ship;

import de.simon.spacemod.SimonSpaceMod;
import de.simon.spacemod.ModBlocks;
import de.simon.spacemod.turbolift.TurboliftData;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.TickEvent;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

/**
 * Setzt einen {@link EnterpriseBlueprint} Stück für Stück in die Welt.
 * Der Bauplan wird asynchron berechnet, das Platzieren verteilt sich auf mehrere Server-Ticks.
 */
public final class ShipBuildManager {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final long TICK_BUDGET_NANOS = 15_000_000L; // max. 15 ms pro Tick (Licht-Updates kommen noch dazu)
    private static final List<Job> JOBS = new ArrayList<>();

    private ShipBuildManager() {}

    public static void init() {
        TickEvent.ServerTickEvent.Post.BUS.addListener(ShipBuildManager::onServerTick);
    }

    /** Ort und Ausrichtung des Schiffs in der Welt. */
    public record Placement(ServerLevel level, BlockPos origin, Direction bow) {
        /** Rechnet lokale Bauplan-Koordinaten in Weltkoordinaten um. */
        public BlockPos toWorld(EnterpriseBlueprint bp, int x, int y, int z) {
            int forward = bp.sizeX / 2 - x;
            Direction right = bow.getClockWise();
            return origin.offset(
                bow.getStepX() * forward + right.getStepX() * z,
                y,
                bow.getStepZ() * forward + right.getStepZ() * z);
        }

        public Rotation rotation() {
            return switch (bow) {
                case EAST -> Rotation.CLOCKWISE_90;
                case SOUTH -> Rotation.CLOCKWISE_180;
                case WEST -> Rotation.COUNTERCLOCKWISE_90;
                default -> Rotation.NONE;
            };
        }
    }

    public static CompletableFuture<EnterpriseBlueprint> computeAsync(ShipDesign design, double scale) {
        return CompletableFuture.supplyAsync(() -> new EnterpriseBlueprint(design, scale));
    }

    /** Wird gerade noch ein Schiff gebaut? */
    public static boolean busy() {
        return !JOBS.isEmpty();
    }

    public static void start(EnterpriseBlueprint bp, Placement placement, Consumer<Job> onDone) {
        JOBS.add(new Job(bp, placement, onDone));
    }

    private static void onServerTick(TickEvent.ServerTickEvent.Post event) {
        if (JOBS.isEmpty()) return;
        long deadline = System.nanoTime() + TICK_BUDGET_NANOS;
        Iterator<Job> it = JOBS.iterator();
        while (it.hasNext() && System.nanoTime() < deadline) {
            Job job = it.next();
            if (job.work(deadline)) {
                it.remove();
                job.finish();
            }
        }
    }

    public static final class Job {
        public final EnterpriseBlueprint blueprint;
        public final Placement placement;
        private final Consumer<Job> onDone;
        private final long[] positions;
        private final BlockState[] states;
        private final BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();
        private int cursor;

        Job(EnterpriseBlueprint bp, Placement placement, Consumer<Job> onDone) {
            this.blueprint = bp;
            this.placement = placement;
            this.onDone = onDone;
            Rotation rot = placement.rotation();
            int count = bp.countBlocks();
            positions = new long[count];
            states = new BlockState[count];
            int[] i = {0};
            java.util.Map<Integer, BlockState> cache = new java.util.HashMap<>();
            bp.forEach((x, y, z, code) -> {
                positions[i[0]] = placement.toWorld(bp, x, y, z).asLong();
                states[i[0]] = cache.computeIfAbsent(code, c -> ShipPalette.toState(c).rotate(rot));
                i[0]++;
            });
        }

        public int placed() {
            return cursor;
        }

        public int total() {
            return positions.length;
        }

        /** @return true, wenn alle Blöcke gesetzt sind */
        boolean work(long deadline) {
            ServerLevel level = placement.level();
            int n = 0;
            while (cursor < positions.length) {
                level.setBlock(mutable.set(positions[cursor]), states[cursor], Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
                cursor++;
                if (++n % 64 == 0 && System.nanoTime() > deadline) return false;
            }
            return true;
        }

        void finish() {
            // Alle Turbolifte des Schiffs in ein eigenes Netz eintragen und benennen
            ServerLevel level = placement.level();
            TurboliftData data = TurboliftData.get(level);
            String network = blueprint.design.registry + " " + placement.origin().getX() + "/" + placement.origin().getZ();
            for (EnterpriseBlueprint.LiftStop stop : blueprint.liftStops) {
                BlockPos pos = placement.toWorld(blueprint, stop.pos().getX(), stop.pos().getY(), stop.pos().getZ());
                if (level.getBlockState(pos).is(ModBlocks.TURBOLIFT.get())) {
                    data.set(pos, stop.name(), network);
                } else {
                    LOGGER.warn("[{}] Turbolift bei {} fehlt", SimonSpaceMod.MODID, pos);
                }
            }
            onDone.accept(this);
        }
    }
}

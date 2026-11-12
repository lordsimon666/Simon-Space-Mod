package de.simon.spacemod.space;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import de.simon.spacemod.SimonSpaceMod;
import de.simon.spacemod.ship.ShipBuildManager;
import de.simon.spacemod.ship.ShipDesign;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * Enterprises im Weltraum: in jedem 1000 x 1000-Feld steht in der Mitte ein Schiff in voller Größe
 * (Höhe um Y 500). Welche Enterprise es ist, entscheidet der Zufall – je neuer, desto seltener:
 * NX-01 am häufigsten, NCC-1701-E am seltensten. Gebaut wird, sobald ein Spieler näher als 700
 * Blöcke kommt (immer nur ein Schiff gleichzeitig).
 */
public final class SpaceShips {
    public static final int SPACING = 1000;
    public static final int CENTER_Y = 500;
    private static final int TRIGGER = 700;
    /** Gewichte je Schiff in der Reihenfolge von {@link ShipDesign#values()} (NX-01 ... E). */
    private static final int[] WEIGHTS = {7, 6, 5, 4, 3, 2, 1};
    private static boolean busy;
    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();

    private SpaceShips() {}

    /** Welche Enterprise in einem Feld steht (fest je Welt-Seed und Feld). */
    public static ShipDesign designFor(long seed, int cx, int cz) {
        int total = 0;
        for (int w : WEIGHTS) total += w;
        int roll = Math.floorMod(hash(seed, cx, cz), total);
        ShipDesign[] all = ShipDesign.values();
        for (int i = 0; i < all.length && i < WEIGHTS.length; i++) {
            if (roll < WEIGHTS[i]) return all[i];
            roll -= WEIGHTS[i];
        }
        return all[0];
    }

    static int hash(long seed, int cx, int cz) {
        long h = seed ^ (cx * 0x9E3779B97F4A7C15L) ^ (cz * 0xC2B2AE3D27D4EB4FL);
        h ^= h >>> 31;
        h *= 0xBF58476D1CE4E5B9L;
        h ^= h >>> 29;
        return (int) (h ^ (h >>> 32));
    }

    static void tick(MinecraftServer server, ServerLevel space) {
        if (busy || server.getTickCount() % 40 != 0 || space.players().isEmpty()) return;
        Data data = space.getDataStorage().computeIfAbsent(Data.TYPE);
        for (ServerPlayer p : space.players()) {
            int pcx = Math.round((float) (p.getX() / SPACING)), pcz = Math.round((float) (p.getZ() / SPACING));
            for (int cx = pcx - 1; cx <= pcx + 1; cx++)
                for (int cz = pcz - 1; cz <= pcz + 1; cz++) {
                    double dx = cx * SPACING - p.getX(), dz = cz * SPACING - p.getZ();
                    if (dx * dx + dz * dz > (double) TRIGGER * TRIGGER) continue;
                    long key = BlockPos.asLong(cx, 0, cz);
                    if (data.done.contains(key)) continue;
                    data.done.add(key);
                    data.setDirty();
                    spawn(space, cx, cz);
                    return;
                }
        }
    }

    private static void spawn(ServerLevel space, int cx, int cz) {
        long seed = space.getSeed();
        ShipDesign design = designFor(seed, cx, cz);
        Direction bow = Direction.from2DDataValue(Math.floorMod(hash(seed, cz, cx), 4));
        busy = true;
        ShipBuildManager.computeAsync(design, 1.0).thenAcceptAsync(bp -> {
            BlockPos origin = new BlockPos(cx * SPACING - bp.sizeX / 2, CENTER_Y - bp.sizeY / 2, cz * SPACING - bp.sizeZ / 2);
            if (origin.getY() < space.getMinY()) origin = origin.atY(space.getMinY());
            if (origin.getY() + bp.sizeY > space.getMaxY()) origin = origin.atY(space.getMaxY() - bp.sizeY);
            ShipBuildManager.start(bp, new ShipBuildManager.Placement(space, origin, bow), job -> busy = false);
            LOGGER.info("Weltraum: {} entsteht bei {}", design.registry, origin.toShortString());
        }, space.getServer()).exceptionally(err -> {
            busy = false;
            LOGGER.warn("Weltraum: Schiff konnte nicht gebaut werden", err);
            return null;
        });
    }

    static final class Data extends SavedData {
        static final Codec<Data> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.LONG.listOf().optionalFieldOf("done", List.of()).forGetter(d -> List.copyOf(d.done))
        ).apply(i, Data::new));
        static final SavedDataType<Data> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath(SimonSpaceMod.MODID, "space_ships"), Data::new, CODEC, null);
        final Set<Long> done = new HashSet<>();

        Data() {}

        Data(List<Long> list) {
            done.addAll(list);
        }
    }
}

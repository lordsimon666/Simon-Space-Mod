package de.simon.spacemod.flight;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import de.simon.spacemod.SimonSpaceMod;
import de.simon.spacemod.entity.FlyingShipEntity;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Werft: eigene, leere Dimension, in der die Blöcke fliegender Schiffe als echte Blöcke stehen
 * (wie bei Valkyrien Skies). Dort laufen Maschinen, Reaktoren, Kisten und Blöcke anderer Mods ganz
 * normal weiter; das fliegende Schiff zeigt sie an der Flugposition an. Jedes Schiff hat einen
 * eigenen Bauplatz; Block p des Schiffs (lokal) steht in der Werft bei Ursprung + p.
 */
public final class Shipyard {
    public static final ResourceKey<Level> KEY = ResourceKey.create(Registries.DIMENSION,
        Identifier.fromNamespaceAndPath(SimonSpaceMod.MODID, "shipyard"));
    /** Abstand der Bauplätze (Blöcke). */
    public static final int SPACING = 4096;
    private static final int BASE = 1_048_576;
    private static final int ROW = 256;

    /** Server: aktive Schiffe nach Bauplatz. */
    private static final Map<Integer, FlyingShipEntity> ACTIVE = new HashMap<>();

    private Shipyard() {}

    public static @Nullable ServerLevel level(MinecraftServer server) {
        return server.getLevel(KEY);
    }

    public static boolean is(Level level) {
        return level.dimension() == KEY;
    }

    public static BlockPos origin(int plot) {
        return new BlockPos(BASE + (plot % ROW) * SPACING, 0, BASE + (plot / ROW) * SPACING);
    }

    /** Bauplatz einer Werft-Position oder -1. */
    public static int plotOf(BlockPos pos) {
        int ix = Math.floorDiv(pos.getX() - BASE + SPACING / 2, SPACING);
        int iz = Math.floorDiv(pos.getZ() - BASE + SPACING / 2, SPACING);
        if (ix < 0 || ix >= ROW || iz < 0) return -1;
        return iz * ROW + ix;
    }

    public static void register(int plot, FlyingShipEntity ship) {
        ACTIVE.put(plot, ship);
    }

    public static void unregister(int plot, FlyingShipEntity ship) {
        ACTIVE.remove(plot, ship);
    }

    public static @Nullable FlyingShipEntity shipAt(BlockPos shipyardPos) {
        int plot = plotOf(shipyardPos);
        FlyingShipEntity s = plot < 0 ? null : ACTIVE.get(plot);
        return s != null && s.isAlive() ? s : null;
    }

    /**
     * Abstand² eines Spielers zu einem Block, auch wenn der Block in der Werft steht und der Spieler
     * im zugehörigen Schiff mitfliegt (sonst unendlich, wenn die Welten verschieden sind).
     */
    public static double distanceSqr(Player player, Level level, BlockPos pos) {
        if (is(level) && player.getVehicle() instanceof FlyingShipEntity ship && ship.plot() == plotOf(pos)) {
            Vec3 local = Vec3.atCenterOf(pos.subtract(ship.plotOrigin())).subtract(0.5, 0.5, 0.5);
            return ship.seat(player).add(0, player.getEyeHeight(), 0).distanceToSqr(local);
        }
        if (player.level() != level) return Double.MAX_VALUE;
        return player.distanceToSqr(Vec3.atCenterOf(pos));
    }

    /** Spieler, die (umgerechnet in die Werft) in einem Bereich der Werft stehen – z. B. vor einer Tür. */
    public static List<Player> playersIn(Level shipyard, AABB box) {
        FlyingShipEntity ship = shipAt(BlockPos.containing(box.getCenter()));
        if (ship == null) return List.of();
        List<Player> out = new ArrayList<>();
        Vec3 o = Vec3.atLowerCornerOf(ship.plotOrigin()).add(0.5, 0.5, 0.5);
        for (Player p : ship.level().getEntitiesOfClass(Player.class, ship.getBoundingBox().inflate(4))) {
            Vec3 at = ship.localOf(p).add(o);
            if (box.contains(at) || box.contains(at.add(0, 1, 0))) out.add(p);
        }
        return out;
    }

    /** Chunks eines Bauplatzes laden bzw. freigeben (bleiben geladen, damit alles weiterläuft). */
    public static void force(ServerLevel shipyard, BlockPos origin, BlockPos min, BlockPos max, boolean on) {
        int x0 = (origin.getX() + min.getX()) >> 4, x1 = (origin.getX() + max.getX()) >> 4;
        int z0 = (origin.getZ() + min.getZ()) >> 4, z1 = (origin.getZ() + max.getZ()) >> 4;
        for (int x = x0; x <= x1; x++)
            for (int z = z0; z <= z1; z++) shipyard.setChunkForced(x, z, on);
    }

    /** Nächster freier Bauplatz (gespeichert in der Oberwelt). */
    public static int allocate(MinecraftServer server) {
        Data d = server.overworld().getDataStorage().computeIfAbsent(Data.TYPE);
        int plot = d.next++;
        d.setDirty();
        return plot;
    }

    static final class Data extends SavedData {
        static final Codec<Data> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.optionalFieldOf("next", 0).forGetter(d -> d.next)
        ).apply(i, Data::new));
        static final SavedDataType<Data> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath(SimonSpaceMod.MODID, "shipyard"), Data::new, CODEC, null);
        int next;

        Data() {}

        Data(int next) {
            this.next = next;
        }
    }
}

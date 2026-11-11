package de.simon.spacemod.power;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import de.simon.spacemod.SimonSpaceMod;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import org.jspecify.annotations.Nullable;

/** Gespawnte Schiffe: welche Warpkern-Blöcke einen Verbraucher versorgen (spart die Suche). */
public final class PowerData extends SavedData {
    public record Link(BlockPos consumer, List<BlockPos> cores) {
        public static final Codec<Link> CODEC = RecordCodecBuilder.create(i -> i.group(
            BlockPos.CODEC.fieldOf("consumer").forGetter(Link::consumer),
            BlockPos.CODEC.listOf().fieldOf("cores").forGetter(Link::cores)
        ).apply(i, Link::new));
    }

    /** Gespawntes Schiff: Umriss (Welt) und seine Warpkern-Blöcke – für Lampen und andere Verbraucher. */
    public record Ship(BlockPos min, BlockPos max, List<BlockPos> cores) {
        public static final Codec<Ship> CODEC = RecordCodecBuilder.create(i -> i.group(
            BlockPos.CODEC.fieldOf("min").forGetter(Ship::min),
            BlockPos.CODEC.fieldOf("max").forGetter(Ship::max),
            BlockPos.CODEC.listOf().fieldOf("cores").forGetter(Ship::cores)
        ).apply(i, Ship::new));

        public boolean contains(BlockPos p) {
            return p.getX() >= min.getX() && p.getX() <= max.getX() && p.getY() >= min.getY() && p.getY() <= max.getY()
                && p.getZ() >= min.getZ() && p.getZ() <= max.getZ();
        }
    }

    public static final Codec<PowerData> CODEC = RecordCodecBuilder.create(i -> i.group(
        Link.CODEC.listOf().optionalFieldOf("links", List.of()).forGetter(d -> d.links.entrySet().stream().map(e -> new Link(e.getKey(), e.getValue())).toList()),
        Ship.CODEC.listOf().optionalFieldOf("ships", List.of()).forGetter(d -> d.ships)
    ).apply(i, PowerData::new));

    public static final SavedDataType<PowerData> TYPE = new SavedDataType<>(
        Identifier.fromNamespaceAndPath(SimonSpaceMod.MODID, "power"), PowerData::new, CODEC, null);

    private final Map<BlockPos, List<BlockPos>> links = new HashMap<>();
    private final List<Ship> ships = new java.util.ArrayList<>();

    public PowerData() {}

    private PowerData(List<Link> list, List<Ship> shipList) {
        for (Link l : list) links.put(l.consumer(), List.copyOf(l.cores()));
        ships.addAll(shipList);
    }

    /** Schiff eintragen (ersetzt ein Schiff mit genau gleichem Umriss). */
    public void addShip(BlockPos min, BlockPos max, List<BlockPos> cores) {
        ships.removeIf(s -> s.min().equals(min) && s.max().equals(max));
        ships.add(new Ship(min.immutable(), max.immutable(), List.copyOf(cores)));
        setDirty();
    }

    /** Das zuletzt gespawnte Schiff, in dessen Umriss die Position liegt, oder null. */
    public @Nullable Ship shipAt(BlockPos pos) {
        for (int i = ships.size() - 1; i >= 0; i--) if (ships.get(i).contains(pos)) return ships.get(i);
        return null;
    }

    public static PowerData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(TYPE);
    }

    public @Nullable List<BlockPos> cores(BlockPos consumer) {
        return links.get(consumer);
    }

    public void set(BlockPos consumer, List<BlockPos> cores) {
        links.put(consumer.immutable(), List.copyOf(cores));
        setDirty();
    }
}

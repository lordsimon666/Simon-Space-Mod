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

    public static final Codec<PowerData> CODEC = Link.CODEC.listOf().fieldOf("links").codec()
        .xmap(PowerData::new, d -> d.links.entrySet().stream().map(e -> new Link(e.getKey(), e.getValue())).toList());

    public static final SavedDataType<PowerData> TYPE = new SavedDataType<>(
        Identifier.fromNamespaceAndPath(SimonSpaceMod.MODID, "power"), PowerData::new, CODEC, null);

    private final Map<BlockPos, List<BlockPos>> links = new HashMap<>();

    public PowerData() {}

    private PowerData(List<Link> list) {
        for (Link l : list) links.put(l.consumer(), List.copyOf(l.cores()));
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

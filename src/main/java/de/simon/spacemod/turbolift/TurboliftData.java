package de.simon.spacemod.turbolift;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import de.simon.spacemod.SimonSpaceMod;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import org.jspecify.annotations.Nullable;

/**
 * Alle Turbolifte einer Dimension mit Decknamen und Netz. Lifte mit gleichem Netznamen sind
 * miteinander verbunden – so funktionieren auch selbst gebaute Fahrstühle in eigenen Schiffen und Basen.
 */
public final class TurboliftData extends SavedData {
    public static final String DEFAULT_NETWORK = "Standard";
    public static final int MAX_NAME = 40;

    public record Stop(BlockPos pos, String name, String network) {
        public static final Codec<Stop> CODEC = RecordCodecBuilder.create(i -> i.group(
            BlockPos.CODEC.fieldOf("pos").forGetter(Stop::pos),
            Codec.STRING.fieldOf("name").forGetter(Stop::name),
            Codec.STRING.fieldOf("network").forGetter(Stop::network)
        ).apply(i, Stop::new));
    }

    public static final Codec<TurboliftData> CODEC = Stop.CODEC.listOf().fieldOf("stops").codec()
        .xmap(TurboliftData::new, d -> new ArrayList<>(d.stops.values()));

    public static final SavedDataType<TurboliftData> TYPE = new SavedDataType<>(
        Identifier.fromNamespaceAndPath(SimonSpaceMod.MODID, "turbolifts"), TurboliftData::new, CODEC, null);

    private final Map<BlockPos, Stop> stops = new LinkedHashMap<>();

    public TurboliftData() {}

    private TurboliftData(List<Stop> list) {
        for (Stop s : list) stops.put(s.pos(), s);
    }

    public static TurboliftData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(TYPE);
    }

    public @Nullable Stop get(BlockPos pos) {
        return stops.get(pos);
    }

    /** Meldet einen neuen Lift an (falls noch nicht bekannt) mit Standardnamen "Deck Y=…". */
    public Stop register(BlockPos pos) {
        Stop s = stops.get(pos);
        if (s == null) {
            s = new Stop(pos.immutable(), "Deck Y=" + pos.getY(), DEFAULT_NETWORK);
            stops.put(s.pos(), s);
            setDirty();
        }
        return s;
    }

    public void set(BlockPos pos, String name, String network) {
        String n = clean(name, "Deck Y=" + pos.getY());
        String net = clean(network, DEFAULT_NETWORK);
        stops.put(pos.immutable(), new Stop(pos.immutable(), n, net));
        setDirty();
    }

    public void remove(BlockPos pos) {
        if (stops.remove(pos) != null) setDirty();
    }

    /** Alle Halte im selben Netz, von oben nach unten sortiert (inkl. des eigenen). */
    public List<Stop> network(String network) {
        List<Stop> list = new ArrayList<>();
        for (Stop s : stops.values()) if (s.network().equals(network)) list.add(s);
        list.sort(Comparator.comparingInt((Stop s) -> -s.pos().getY()).thenComparing(Stop::name));
        return list;
    }

    private static String clean(String s, String fallback) {
        String t = s == null ? "" : s.strip();
        if (t.length() > MAX_NAME) t = t.substring(0, MAX_NAME);
        return t.isEmpty() ? fallback : t;
    }
}

package de.simon.spacemod.weapons;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import de.simon.spacemod.SimonSpaceMod;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import org.jspecify.annotations.Nullable;

/** Welche Phaser und Kameras zu welcher Waffenstation gehören (pro Dimension gespeichert). */
public final class WeaponsData extends SavedData {
    /** Phaser einer Station und ihre Kamerapunkte (Position, Blickziel, Name). */
    public record Battery(BlockPos console, List<BlockPos> phasers, List<BlockPos> cameras, List<BlockPos> focus, List<String> names, int tier,
                          BlockPos shipMin, BlockPos shipMax, List<BlockPos> torpedoes, List<BlockPos> quantums) {
        public static final Codec<Battery> CODEC = RecordCodecBuilder.create(i -> i.group(
            BlockPos.CODEC.fieldOf("console").forGetter(Battery::console),
            BlockPos.CODEC.listOf().fieldOf("phasers").forGetter(Battery::phasers),
            BlockPos.CODEC.listOf().optionalFieldOf("cameras", List.of()).forGetter(Battery::cameras),
            BlockPos.CODEC.listOf().optionalFieldOf("focus", List.of()).forGetter(Battery::focus),
            Codec.STRING.listOf().optionalFieldOf("names", List.of()).forGetter(Battery::names),
            Codec.INT.optionalFieldOf("tier", 0).forGetter(Battery::tier),
            BlockPos.CODEC.optionalFieldOf("ship_min", BlockPos.ZERO).forGetter(Battery::shipMin),
            BlockPos.CODEC.optionalFieldOf("ship_max", BlockPos.ZERO).forGetter(Battery::shipMax),
            BlockPos.CODEC.listOf().optionalFieldOf("torpedoes", List.of()).forGetter(Battery::torpedoes),
            BlockPos.CODEC.listOf().optionalFieldOf("quantums", List.of()).forGetter(Battery::quantums)
        ).apply(i, Battery::new));

        Battery copy() {
            return new Battery(console, new ArrayList<>(phasers), new ArrayList<>(cameras), new ArrayList<>(focus), new ArrayList<>(names), tier, shipMin, shipMax,
                new ArrayList<>(torpedoes), new ArrayList<>(quantums));
        }

        /** Hat die Station einen gespeicherten Schiffsbereich (gespawntes Schiff)? */
        public boolean hasShipBox() {
            return !shipMin.equals(shipMax);
        }
    }

    public static final Codec<WeaponsData> CODEC = Battery.CODEC.listOf().fieldOf("batteries").codec()
        .xmap(WeaponsData::new, d -> new ArrayList<>(d.batteries.values()));

    public static final SavedDataType<WeaponsData> TYPE = new SavedDataType<>(
        Identifier.fromNamespaceAndPath(SimonSpaceMod.MODID, "weapons"), WeaponsData::new, CODEC, null);

    private final Map<BlockPos, Battery> batteries = new HashMap<>();

    public WeaponsData() {}

    private WeaponsData(List<Battery> list) {
        for (Battery b : list) batteries.put(b.console(), b.copy());
    }

    public static WeaponsData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(TYPE);
    }

    public @Nullable Battery battery(BlockPos console) {
        return batteries.get(console);
    }

    public @Nullable List<BlockPos> phasers(BlockPos console) {
        Battery b = batteries.get(console);
        return b == null ? null : b.phasers();
    }

    public void set(BlockPos console, List<BlockPos> phasers) {
        set(console, phasers, List.of(), List.of(), List.of(), 0, BlockPos.ZERO, BlockPos.ZERO, List.of(), List.of());
    }

    /** @param tier Waffenstärke des Schiffs: 1 = NX-01 ... 7 = Enterprise-E (0 = aus den Phasern ableiten) */
    public void set(BlockPos console, List<BlockPos> phasers, List<BlockPos> cameras, List<BlockPos> focus, List<String> names, int tier,
                    BlockPos shipMin, BlockPos shipMax, List<BlockPos> torpedoes, List<BlockPos> quantums) {
        batteries.put(console.immutable(),
            new Battery(console.immutable(), phasers, cameras, focus, names, tier, shipMin, shipMax, torpedoes, quantums).copy());
        setDirty();
    }
}

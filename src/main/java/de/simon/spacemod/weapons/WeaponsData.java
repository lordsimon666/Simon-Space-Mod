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
    public record Battery(BlockPos console, List<BlockPos> phasers, List<BlockPos> cameras, List<BlockPos> focus, List<String> names) {
        public static final Codec<Battery> CODEC = RecordCodecBuilder.create(i -> i.group(
            BlockPos.CODEC.fieldOf("console").forGetter(Battery::console),
            BlockPos.CODEC.listOf().fieldOf("phasers").forGetter(Battery::phasers),
            BlockPos.CODEC.listOf().optionalFieldOf("cameras", List.of()).forGetter(Battery::cameras),
            BlockPos.CODEC.listOf().optionalFieldOf("focus", List.of()).forGetter(Battery::focus),
            Codec.STRING.listOf().optionalFieldOf("names", List.of()).forGetter(Battery::names)
        ).apply(i, Battery::new));

        Battery copy() {
            return new Battery(console, new ArrayList<>(phasers), new ArrayList<>(cameras), new ArrayList<>(focus), new ArrayList<>(names));
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
        set(console, phasers, List.of(), List.of(), List.of());
    }

    public void set(BlockPos console, List<BlockPos> phasers, List<BlockPos> cameras, List<BlockPos> focus, List<String> names) {
        batteries.put(console.immutable(), new Battery(console.immutable(), phasers, cameras, focus, names).copy());
        setDirty();
    }
}

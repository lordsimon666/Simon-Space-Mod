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

/** Welche Phaser zu welcher Waffenstation gehören (pro Dimension gespeichert). */
public final class WeaponsData extends SavedData {
    public record Battery(BlockPos console, List<BlockPos> phasers) {
        public static final Codec<Battery> CODEC = RecordCodecBuilder.create(i -> i.group(
            BlockPos.CODEC.fieldOf("console").forGetter(Battery::console),
            BlockPos.CODEC.listOf().fieldOf("phasers").forGetter(Battery::phasers)
        ).apply(i, Battery::new));
    }

    public static final Codec<WeaponsData> CODEC = Battery.CODEC.listOf().fieldOf("batteries").codec()
        .xmap(WeaponsData::new, d -> d.batteries.entrySet().stream().map(e -> new Battery(e.getKey(), e.getValue())).toList());

    public static final SavedDataType<WeaponsData> TYPE = new SavedDataType<>(
        Identifier.fromNamespaceAndPath(SimonSpaceMod.MODID, "weapons"), WeaponsData::new, CODEC, null);

    private final Map<BlockPos, List<BlockPos>> batteries = new HashMap<>();

    public WeaponsData() {}

    private WeaponsData(List<Battery> list) {
        for (Battery b : list) batteries.put(b.console(), new ArrayList<>(b.phasers()));
    }

    public static WeaponsData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(TYPE);
    }

    public @Nullable List<BlockPos> phasers(BlockPos console) {
        return batteries.get(console);
    }

    public void set(BlockPos console, List<BlockPos> phasers) {
        batteries.put(console.immutable(), new ArrayList<>(phasers));
        setDirty();
    }
}

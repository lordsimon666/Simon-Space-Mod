package de.simon.spacemod.transporter;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import de.simon.spacemod.SimonSpaceMod;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/** Alle bekannten Transporterplattformen einer Welt (für Transporte von Plattform zu Plattform). */
public final class TransporterData extends SavedData {
    public static final Codec<TransporterData> CODEC = RecordCodecBuilder.create(i -> i.group(
        Codec.LONG.listOf().optionalFieldOf("pads", List.of()).forGetter(d -> List.copyOf(d.pads))
    ).apply(i, TransporterData::new));
    public static final SavedDataType<TransporterData> TYPE = new SavedDataType<>(
        Identifier.fromNamespaceAndPath(SimonSpaceMod.MODID, "transporters"), TransporterData::new, CODEC, null);

    final Set<Long> pads = new HashSet<>();

    public TransporterData() {}

    private TransporterData(List<Long> list) {
        pads.addAll(list);
    }

    public static TransporterData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(TYPE);
    }

    public void add(BlockPos p) {
        if (pads.add(p.asLong())) setDirty();
    }

    public void remove(long p) {
        if (pads.remove(p)) setDirty();
    }
}

package de.simon.spacemod.transporter;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraftforge.event.network.CustomPayloadEvent;

/** Server -> Client: Transporter-Ziele (je Gruppe: Versatz x, y, z und Anzahl Plattformen). */
public record TransporterMenuMessage(BlockPos pad, int[] groups) {
    public static final StreamCodec<RegistryFriendlyByteBuf, TransporterMenuMessage> STREAM_CODEC = StreamCodec.ofMember(
        (msg, buf) -> {
            buf.writeBlockPos(msg.pad());
            buf.writeVarInt(msg.groups().length);
            for (int v : msg.groups()) buf.writeInt(v);
        },
        buf -> {
            BlockPos p = buf.readBlockPos();
            int n = Math.min(buf.readVarInt(), 400);
            int[] g = new int[n];
            for (int i = 0; i < n; i++) g[i] = buf.readInt();
            return new TransporterMenuMessage(p, g);
        });

    public static void handle(TransporterMenuMessage msg, CustomPayloadEvent.Context ctx) {
        de.simon.spacemod.client.TransporterScreen.open(msg.pad(), msg.groups());
    }
}

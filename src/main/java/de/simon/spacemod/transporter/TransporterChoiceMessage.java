package de.simon.spacemod.transporter;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.network.CustomPayloadEvent;

/** Client -> Server: Transporterziel gewählt (-1 = Oberfläche, sonst Gruppe). */
public record TransporterChoiceMessage(BlockPos pad, int choice) {
    public static final StreamCodec<RegistryFriendlyByteBuf, TransporterChoiceMessage> STREAM_CODEC = StreamCodec.ofMember(
        (msg, buf) -> {
            buf.writeBlockPos(msg.pad());
            buf.writeVarInt(msg.choice() + 1);
        },
        buf -> new TransporterChoiceMessage(buf.readBlockPos(), buf.readVarInt() - 1));

    public static void handle(TransporterChoiceMessage msg, CustomPayloadEvent.Context ctx) {
        ServerPlayer p = ctx.getSender();
        if (p != null) Transporters.choose(p, msg.pad(), msg.choice());
    }
}

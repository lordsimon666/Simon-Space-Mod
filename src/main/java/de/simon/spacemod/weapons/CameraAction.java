package de.simon.spacemod.weapons;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.network.CustomPayloadEvent;

/** Client -> Server: in der Kamerasicht feuern (mit Blickrichtung) oder Kamera wechseln. */
public record CameraAction(boolean fire, float yRot, float xRot) {
    public static final StreamCodec<RegistryFriendlyByteBuf, CameraAction> STREAM_CODEC = StreamCodec.ofMember(
        (msg, buf) -> {
            buf.writeBoolean(msg.fire());
            buf.writeFloat(msg.yRot());
            buf.writeFloat(msg.xRot());
        },
        buf -> new CameraAction(buf.readBoolean(), buf.readFloat(), buf.readFloat()));

    public static void handle(CameraAction msg, CustomPayloadEvent.Context ctx) {
        ServerPlayer player = ctx.getSender();
        if (player == null) return;
        if (msg.fire()) CameraControl.fire(player, msg.yRot(), msg.xRot());
        else CameraControl.next(player);
    }
}

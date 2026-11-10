package de.simon.spacemod.weapons;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.network.CustomPayloadEvent;

/** Client -> Server in der Kamerasicht: feuern (mit Blickrichtung), Kamera wechseln oder Stärke ändern. */
public record CameraAction(int action, int delta, float yRot, float xRot) {
    public static final int NEXT = 0, FIRE = 1, POWER = 2, PHOTON = 3, QUANTUM = 4;

    public static final StreamCodec<RegistryFriendlyByteBuf, CameraAction> STREAM_CODEC = StreamCodec.ofMember(
        (msg, buf) -> {
            buf.writeByte(msg.action());
            buf.writeByte(msg.delta());
            buf.writeFloat(msg.yRot());
            buf.writeFloat(msg.xRot());
        },
        buf -> new CameraAction(buf.readByte(), buf.readByte(), buf.readFloat(), buf.readFloat()));

    public static void handle(CameraAction msg, CustomPayloadEvent.Context ctx) {
        ServerPlayer player = ctx.getSender();
        if (player == null) return;
        switch (msg.action()) {
            case FIRE -> CameraControl.fire(player, msg.yRot(), msg.xRot());
            case POWER -> CameraControl.power(player, msg.delta());
            case PHOTON, QUANTUM -> CameraControl.torpedo(player, msg.action() == QUANTUM, msg.yRot(), msg.xRot());
            default -> CameraControl.next(player);
        }
    }
}

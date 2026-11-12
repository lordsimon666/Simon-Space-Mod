package de.simon.spacemod.weapons;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraftforge.event.network.CustomPayloadEvent;

/** Server -> Client: Kamerasicht an/aus (der Client leitet dann Klicks um und sperrt die Bewegung). */
public record CameraState(boolean active, int shipId, float lx, float ly, float lz) {
    public CameraState(boolean active) {
        this(active, -1, 0, 0, 0);
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, CameraState> STREAM_CODEC = StreamCodec.ofMember(
        (msg, buf) -> {
            buf.writeBoolean(msg.active());
            buf.writeVarInt(msg.shipId() + 1);
            buf.writeFloat(msg.lx());
            buf.writeFloat(msg.ly());
            buf.writeFloat(msg.lz());
        },
        buf -> new CameraState(buf.readBoolean(), buf.readVarInt() - 1, buf.readFloat(), buf.readFloat(), buf.readFloat()));

    public static void handle(CameraState msg, CustomPayloadEvent.Context ctx) {
        de.simon.spacemod.client.ClientCamera.setActive(msg.active());
        de.simon.spacemod.client.ClientCamera.followShip(msg.active() ? msg.shipId() : -1, msg.lx(), msg.ly(), msg.lz());
    }
}

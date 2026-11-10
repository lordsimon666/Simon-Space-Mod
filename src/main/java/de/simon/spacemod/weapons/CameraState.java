package de.simon.spacemod.weapons;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraftforge.event.network.CustomPayloadEvent;

/** Server -> Client: Kamerasicht an/aus (der Client leitet dann Klicks um und sperrt die Bewegung). */
public record CameraState(boolean active) {
    public static final StreamCodec<RegistryFriendlyByteBuf, CameraState> STREAM_CODEC = StreamCodec.ofMember(
        (msg, buf) -> buf.writeBoolean(msg.active()), buf -> new CameraState(buf.readBoolean()));

    public static void handle(CameraState msg, CustomPayloadEvent.Context ctx) {
        de.simon.spacemod.client.ClientCamera.setActive(msg.active());
    }
}

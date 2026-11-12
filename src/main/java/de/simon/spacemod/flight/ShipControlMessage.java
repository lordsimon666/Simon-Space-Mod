package de.simon.spacemod.flight;

import de.simon.spacemod.entity.FlyingShipEntity;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.network.CustomPayloadEvent;

/**
 * Client -> Server: Steuerung des Piloten. action 0 = fliegen (Blickrichtung, Schub -1/0/1),
 * action 1 = landen.
 */
public record ShipControlMessage(int action, float yaw, float pitch, int throttle) {
    public static final StreamCodec<RegistryFriendlyByteBuf, ShipControlMessage> STREAM_CODEC = StreamCodec.ofMember(
        (msg, buf) -> {
            buf.writeByte(msg.action());
            buf.writeFloat(msg.yaw());
            buf.writeFloat(msg.pitch());
            buf.writeByte(msg.throttle());
        },
        buf -> new ShipControlMessage(buf.readByte(), buf.readFloat(), buf.readFloat(), buf.readByte()));

    public static void handle(ShipControlMessage msg, CustomPayloadEvent.Context ctx) {
        ServerPlayer player = ctx.getSender();
        if (player == null || !(player.getVehicle() instanceof FlyingShipEntity ship)) return;
        if (msg.action() == 1) ship.land(player);
        else ship.control(player, msg.yaw(), msg.pitch(), msg.throttle());
    }
}

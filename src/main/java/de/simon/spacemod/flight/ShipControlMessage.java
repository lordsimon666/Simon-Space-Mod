package de.simon.spacemod.flight;

import de.simon.spacemod.entity.FlyingShipEntity;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.network.CustomPayloadEvent;

/**
 * Client -> Server: Steuerung. action 0 = fliegen (Blickrichtung, Schub -1/0/1), 1 = landen,
 * 2 = Steuer übernehmen/abgeben, 3 = vom Schiff gefallen (absteigen).
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
        switch (msg.action()) {
            case 1 -> ship.land(player);
            case 2 -> ship.toggleHelm(player);
            case 3 -> player.stopRiding();
            default -> ship.control(player, msg.yaw(), msg.pitch(), msg.throttle());
        }
    }
}

package de.simon.spacemod.flight;

import de.simon.spacemod.entity.FlyingShipEntity;
import de.simon.spacemod.turbolift.TurboliftNetwork;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.network.CustomPayloadEvent;
import net.minecraftforge.network.PacketDistributor;

/**
 * Laufen im Schiff. Client -> Server: neuer Standort des Spielers im Schiff (lokal); der Server
 * gibt ihn als {@link ShipSeatSync} an die anderen weiter.
 */
public record ShipSeatMessage(int entityId, float x, float y, float z) {
    public static final StreamCodec<RegistryFriendlyByteBuf, ShipSeatMessage> STREAM_CODEC = StreamCodec.ofMember(
        (msg, buf) -> {
            buf.writeVarInt(msg.entityId());
            buf.writeFloat(msg.x());
            buf.writeFloat(msg.y());
            buf.writeFloat(msg.z());
        },
        buf -> new ShipSeatMessage(buf.readVarInt(), buf.readFloat(), buf.readFloat(), buf.readFloat()));

    /** Server: Standort eines laufenden Spielers übernehmen (mit Plausibilitätsprüfung) und weitergeben. */
    public static void handleServer(ShipSeatMessage msg, CustomPayloadEvent.Context ctx) {
        ServerPlayer player = ctx.getSender();
        if (player == null || !(player.getVehicle() instanceof FlyingShipEntity ship) || ship.isPilot(player)) return;
        Vec3 to = new Vec3(msg.x(), msg.y(), msg.z());
        if (to.distanceTo(ship.seat(player)) > 2.0 || ship.structure() == null || to.length() > ship.structure().radius + 8) return;
        ship.setSeat(player, to);
        TurboliftNetwork.CHANNEL.send(new ShipSeatSync(player.getId(), msg.x(), msg.y(), msg.z()), PacketDistributor.TRACKING_ENTITY.with(ship));
    }

}

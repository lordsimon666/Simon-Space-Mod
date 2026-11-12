package de.simon.spacemod.flight;

import de.simon.spacemod.entity.FlyingShipEntity;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.network.CustomPayloadEvent;

/** Server -> Clients: Standort eines Mitfahrers im Schiff (lokal), {@code entityId} = Mitfahrer. */
public record ShipSeatSync(int entityId, float x, float y, float z) {
    public static final StreamCodec<RegistryFriendlyByteBuf, ShipSeatSync> STREAM_CODEC = StreamCodec.ofMember(
        (msg, buf) -> {
            buf.writeVarInt(msg.entityId());
            buf.writeFloat(msg.x());
            buf.writeFloat(msg.y());
            buf.writeFloat(msg.z());
        },
        buf -> new ShipSeatSync(buf.readVarInt(), buf.readFloat(), buf.readFloat(), buf.readFloat()));

    public static void handle(ShipSeatSync msg, CustomPayloadEvent.Context ctx) {
        var mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.level == null || mc.player == null || msg.entityId() == mc.player.getId()) return;
        var e = mc.level.getEntity(msg.entityId());
        if (e != null && e.getVehicle() instanceof FlyingShipEntity ship) ship.setSeat(e, new Vec3(msg.x(), msg.y(), msg.z()));
    }
}

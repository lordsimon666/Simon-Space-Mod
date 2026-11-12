package de.simon.spacemod.flight;

import de.simon.spacemod.entity.FlyingShipEntity;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.network.CustomPayloadEvent;

/** Server -> Clients: Standort eines Mitfahrers im Schiff (lokal), {@code entityId} = Mitfahrer. */
public record ShipSeatSync(int entityId, float x, float y, float z, boolean force) {
    public ShipSeatSync(int entityId, float x, float y, float z) {
        this(entityId, x, y, z, false);
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, ShipSeatSync> STREAM_CODEC = StreamCodec.ofMember(
        (msg, buf) -> {
            buf.writeVarInt(msg.entityId());
            buf.writeFloat(msg.x());
            buf.writeFloat(msg.y());
            buf.writeFloat(msg.z());
            buf.writeBoolean(msg.force());
        },
        buf -> new ShipSeatSync(buf.readVarInt(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readBoolean()));

    public static void handle(ShipSeatSync msg, CustomPayloadEvent.Context ctx) {
        var mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        boolean own = msg.entityId() == mc.player.getId();
        if (own && !msg.force()) return; // den eigenen Platz bestimmt der Client selbst (Laufen)
        var e = mc.level.getEntity(msg.entityId());
        if (e != null && e.getVehicle() instanceof FlyingShipEntity ship) {
            ship.setSeat(e, new Vec3(msg.x(), msg.y(), msg.z()));
            if (own) de.simon.spacemod.client.ShipClient.resetWalk();
        }
    }
}

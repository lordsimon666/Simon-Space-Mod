package de.simon.spacemod.flight;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraftforge.event.network.CustomPayloadEvent;

/**
 * Server -> Client: Anzeigedaten der Blockdaten (Waffen im Ständer, Ladestation …) eines fliegenden
 * Schiffs, damit Blöcke mit eigenem Renderer auch im Flug richtig aussehen. Nur geänderte Blöcke.
 */
public record ShipBlockEntityMessage(int entityId, List<Entry> entries) {
    public record Entry(long pos, CompoundTag tag) {}

    public static final StreamCodec<RegistryFriendlyByteBuf, ShipBlockEntityMessage> STREAM_CODEC = StreamCodec.ofMember(
        (msg, buf) -> {
            buf.writeVarInt(msg.entityId());
            buf.writeVarInt(msg.entries().size());
            for (Entry e : msg.entries()) {
                buf.writeLong(e.pos());
                ByteBufCodecs.TRUSTED_COMPOUND_TAG.encode(buf, e.tag());
            }
        },
        buf -> {
            int id = buf.readVarInt();
            int n = buf.readVarInt();
            List<Entry> l = new ArrayList<>(n);
            for (int i = 0; i < n; i++) l.add(new Entry(buf.readLong(), ByteBufCodecs.TRUSTED_COMPOUND_TAG.decode(buf)));
            return new ShipBlockEntityMessage(id, l);
        });

    public static void handle(ShipBlockEntityMessage msg, CustomPayloadEvent.Context ctx) {
        var level = net.minecraft.client.Minecraft.getInstance().level;
        if (level != null && level.getEntity(msg.entityId()) instanceof de.simon.spacemod.entity.FlyingShipEntity ship)
            for (Entry e : msg.entries()) ship.clientBlockData().put(BlockPos.of(e.pos()), e.tag());
    }
}

package de.simon.spacemod.flight;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.event.network.CustomPayloadEvent;

/** Server -> Client: geänderte Blockzustände eines fliegenden Schiffs (Türen, Öfen, Lampen …). */
public record ShipBlocksMessage(int entityId, int[] indices, int[] states) {
    public static final StreamCodec<RegistryFriendlyByteBuf, ShipBlocksMessage> STREAM_CODEC = StreamCodec.ofMember(
        (msg, buf) -> {
            buf.writeVarInt(msg.entityId());
            buf.writeVarInt(msg.indices().length);
            for (int i = 0; i < msg.indices().length; i++) {
                buf.writeVarInt(msg.indices()[i]);
                buf.writeVarInt(msg.states()[i]);
            }
        },
        buf -> {
            int id = buf.readVarInt();
            int n = buf.readVarInt();
            int[] idx = new int[n];
            int[] st = new int[n];
            for (int i = 0; i < n; i++) { idx[i] = buf.readVarInt(); st[i] = buf.readVarInt(); }
            return new ShipBlocksMessage(id, idx, st);
        });

    public static void handle(ShipBlocksMessage msg, CustomPayloadEvent.Context ctx) {
        var level = net.minecraft.client.Minecraft.getInstance().level;
        if (level == null || !(level.getEntity(msg.entityId()) instanceof de.simon.spacemod.entity.FlyingShipEntity ship)) return;
        ShipStructure s = ship.structure();
        if (s == null) return;
        for (int i = 0; i < msg.indices().length; i++)
            if (msg.indices()[i] >= 0 && msg.indices()[i] < s.blocks.size()) s.setState(msg.indices()[i], Block.stateById(msg.states()[i]));
    }
}

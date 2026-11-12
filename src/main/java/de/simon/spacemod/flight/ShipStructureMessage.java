package de.simon.spacemod.flight;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.event.network.CustomPayloadEvent;

/** Server -> Client: Blöcke eines fliegenden Schiffs (zum Zeichnen). */
public record ShipStructureMessage(int entityId, long[] positions, int[] states) {
    public static ShipStructureMessage of(int id, ShipStructure s) {
        long[] p = new long[s.blocks.size()];
        int[] st = new int[p.length];
        for (int i = 0; i < p.length; i++) {
            p[i] = s.blocks.get(i).pos().asLong();
            st[i] = Block.getId(s.blocks.get(i).state());
        }
        return new ShipStructureMessage(id, p, st);
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, ShipStructureMessage> STREAM_CODEC = StreamCodec.ofMember(
        (msg, buf) -> {
            buf.writeVarInt(msg.entityId());
            buf.writeVarInt(msg.positions().length);
            for (int i = 0; i < msg.positions().length; i++) {
                buf.writeLong(msg.positions()[i]);
                buf.writeVarInt(msg.states()[i]);
            }
        },
        buf -> {
            int id = buf.readVarInt();
            int n = buf.readVarInt();
            long[] p = new long[n];
            int[] s = new int[n];
            for (int i = 0; i < n; i++) { p[i] = buf.readLong(); s[i] = buf.readVarInt(); }
            return new ShipStructureMessage(id, p, s);
        });

    public static void handle(ShipStructureMessage msg, CustomPayloadEvent.Context ctx) {
        List<ShipStructure.Entry> list = new ArrayList<>(msg.positions().length);
        for (int i = 0; i < msg.positions().length; i++)
            list.add(new ShipStructure.Entry(BlockPos.of(msg.positions()[i]), Block.stateById(msg.states()[i]), Optional.empty()));
        ShipStructure s = new ShipStructure(list);
        var level = net.minecraft.client.Minecraft.getInstance().level;
        if (level != null && level.getEntity(msg.entityId()) instanceof de.simon.spacemod.entity.FlyingShipEntity ship) ship.setClientStructure(s);
        else ShipClientData.put(msg.entityId(), s);
    }
}

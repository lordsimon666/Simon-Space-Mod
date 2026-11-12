package de.simon.spacemod.defense;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraftforge.event.network.CustomPayloadEvent;

/** Server -> Client: Schildblöcke eines Treffers (Lage relativ zum Treffer, Leben 0-255). */
public record ShieldFlashMessage(int entityId, int[] cells, float[] rot) {
    public static final StreamCodec<RegistryFriendlyByteBuf, ShieldFlashMessage> STREAM_CODEC = StreamCodec.ofMember(
        (msg, buf) -> {
            buf.writeVarInt(msg.entityId());
            buf.writeVarInt(msg.cells().length);
            for (int c : msg.cells()) buf.writeInt(c);
            for (int i = 0; i < 4; i++) buf.writeFloat(msg.rot()[i]);
        },
        buf -> {
            int id = buf.readVarInt();
            int n = Math.min(buf.readVarInt(), 20000);
            int[] c = new int[n];
            for (int i = 0; i < n; i++) c[i] = buf.readInt();
            float[] q = new float[4];
            for (int i = 0; i < 4; i++) q[i] = buf.readFloat();
            return new ShieldFlashMessage(id, c, q);
        });

    /** Client: Daten, deren Entity noch nicht da ist. */
    static final Map<Integer, ShieldFlashMessage> PENDING = new HashMap<>();

    public static void handle(ShieldFlashMessage msg, CustomPayloadEvent.Context ctx) {
        synchronized (PENDING) {
            PENDING.put(msg.entityId(), msg);
            if (PENDING.size() > 200) PENDING.clear();
        }
    }

    static ShieldFlashMessage take(int id) {
        synchronized (PENDING) {
            return PENDING.remove(id);
        }
    }
}

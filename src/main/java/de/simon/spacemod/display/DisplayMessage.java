package de.simon.spacemod.display;

import de.simon.spacemod.entity.FlyingShipEntity;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.network.CustomPayloadEvent;

/** Nachrichten für Bildschirme und Kameras. */
public final class DisplayMessage {
    private DisplayMessage() {}

    /** Server -> Client: Kameraliste (je Kamera Versatz x, y, z zum Bildschirm und Blickrichtung), Auswahl, Hauptbildschirm, Größe. */
    public record Menu(int[] cameras, int selected, boolean main, int width, int height) {
        public static final StreamCodec<RegistryFriendlyByteBuf, Menu> STREAM_CODEC = StreamCodec.ofMember(
            (m, buf) -> {
                buf.writeVarInt(m.cameras().length);
                for (int v : m.cameras()) buf.writeInt(v);
                buf.writeInt(m.selected());
                buf.writeBoolean(m.main());
                buf.writeByte(m.width());
                buf.writeByte(m.height());
            },
            buf -> {
                int n = Math.min(buf.readVarInt(), 4 * 256);
                int[] c = new int[n];
                for (int i = 0; i < n; i++) c[i] = buf.readInt();
                return new Menu(c, buf.readInt(), buf.readBoolean(), buf.readByte(), buf.readByte());
            });

        public static void handle(Menu m, CustomPayloadEvent.Context ctx) {
            de.simon.spacemod.client.DisplayScreen.open(m.cameras(), m.selected(), m.main(), m.width(), m.height());
        }
    }

    /** Client -> Server: Kamera wählen (-1 = aus) und Hauptbildschirm setzen. */
    public record Choose(int camera, boolean main) {
        public static final StreamCodec<RegistryFriendlyByteBuf, Choose> STREAM_CODEC = StreamCodec.ofMember(
            (m, buf) -> { buf.writeInt(m.camera()); buf.writeBoolean(m.main()); },
            buf -> new Choose(buf.readInt(), buf.readBoolean()));

        public static void handle(Choose m, CustomPayloadEvent.Context ctx) {
            ServerPlayer p = ctx.getSender();
            if (p != null) Displays.choose(p, m.camera(), m.main());
        }
    }

    /** Client -> Server: am Steuer den Hauptbildschirm zur nächsten Kamera schalten. */
    public record Cycle() {
        public static final StreamCodec<RegistryFriendlyByteBuf, Cycle> STREAM_CODEC = StreamCodec.unit(new Cycle());

        public static void handle(Cycle m, CustomPayloadEvent.Context ctx) {
            ServerPlayer p = ctx.getSender();
            if (p != null && p.getVehicle() instanceof FlyingShipEntity ship && ship.isPilot(p)) Displays.cycleMain(p, ship);
        }
    }
}

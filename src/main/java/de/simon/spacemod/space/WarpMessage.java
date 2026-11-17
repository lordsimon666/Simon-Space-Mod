package de.simon.spacemod.space;

import de.simon.spacemod.entity.FlyingShipEntity;
import de.simon.spacemod.turbolift.TurboliftNetwork;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.network.CustomPayloadEvent;
import net.minecraftforge.network.PacketDistributor;

/** Nachrichten des Warp-Menüs. */
public final class WarpMessage {
    private WarpMessage() {}

    /**
     * Client -> Server. action: 0 = Menü-Daten anfordern, 1 = Autopilot zu (x, y, z), 2 = manuell in den Warp,
     * 3 = Warp verlassen, 4 = Stufe hoch, 5 = Stufe runter.
     */
    public record Command(int action, double x, double y, double z, double warp, boolean destSpace) {
        public static final StreamCodec<RegistryFriendlyByteBuf, Command> STREAM_CODEC = StreamCodec.ofMember(
            (m, buf) -> {
                buf.writeByte(m.action());
                buf.writeDouble(m.x()); buf.writeDouble(m.y()); buf.writeDouble(m.z());
                buf.writeDouble(m.warp());
                buf.writeBoolean(m.destSpace());
            },
            buf -> new Command(buf.readByte(), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readBoolean()));

        public static void handle(Command m, CustomPayloadEvent.Context ctx) {
            ServerPlayer p = ctx.getSender();
            if (p == null || !(p.getVehicle() instanceof FlyingShipEntity ship) || !ship.isPilot(p)) return;
            if (!Double.isFinite(m.x()) || !Double.isFinite(m.y()) || !Double.isFinite(m.z()) || !Double.isFinite(m.warp())) return;
            switch (m.action()) {
                case 0 -> sendInfo(p, ship);
                case 1 -> ship.engageWarp(p, true, m.x(), m.y(), m.z(), m.warp(), m.destSpace());
                case 2 -> ship.engageWarp(p, false, 0, 0, 0, m.warp(), m.destSpace());
                case 3 -> { if (ship.inWarp()) ship.leaveWarp("message.simonspacemod.warp.exit"); }
                case 4 -> ship.stepWarp(p, 1);
                case 5 -> ship.stepWarp(p, -1);
                default -> { }
            }
        }
    }

    /** Server -> Client: Daten für das Warp-Menü. */
    public record Info(double maxNacelles, long production, double[] state) {
        public static final StreamCodec<RegistryFriendlyByteBuf, Info> STREAM_CODEC = StreamCodec.ofMember(
            (m, buf) -> {
                buf.writeDouble(m.maxNacelles());
                buf.writeLong(m.production());
                buf.writeVarInt(m.state().length);
                for (double v : m.state()) buf.writeDouble(v);
            },
            buf -> {
                double max = buf.readDouble();
                long prod = buf.readLong();
                double[] st = new double[Math.min(buf.readVarInt(), 16)];
                for (int i = 0; i < st.length; i++) st[i] = buf.readDouble();
                return new Info(max, prod, st);
            });

        public static void handle(Info m, CustomPayloadEvent.Context ctx) {
            de.simon.spacemod.client.WarpScreen.open(m.maxNacelles(), m.production(), m.state());
        }
    }

    static void sendInfo(ServerPlayer p, FlyingShipEntity ship) {
        TurboliftNetwork.CHANNEL.send(new Info(ship.warpMaxNacelles(), ship.warpProduction(), ship.warpState()), PacketDistributor.PLAYER.with(p));
    }
}

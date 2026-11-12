package de.simon.spacemod.weapons;

import de.simon.spacemod.item.HandWeaponItem;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.network.CustomPayloadEvent;

/**
 * Abzug: gefeuert wird nur, solange Linksklick gehalten wird (Handphaser, Gewehre, Kamerasicht). Loslassen bricht den Strahl ab; wer weiter hält, feuert nach dem
 * Nachladen automatisch erneut.
 */
public final class Triggers {
    /** Spieler mit gehaltenem Abzug -> frühester Tick für den nächsten Versuch. */
    private static final Map<UUID, Long> HELD = new HashMap<>();

    private Triggers() {}

    public static void init() {
        TickEvent.ServerTickEvent.Post.BUS.addListener(e -> tick(e.server()));
        PlayerEvent.PlayerLoggedOutEvent.BUS.addListener(e -> HELD.remove(e.getEntity().getUUID()));
    }

    public static boolean held(Player p) {
        return HELD.containsKey(p.getUUID());
    }

    /** Abzug gedrückt (ein Klick zählt auch dann, wenn die Loslassen-Meldung schon unterwegs ist). */
    public static void press(Player p) {
        HELD.putIfAbsent(p.getUUID(), 0L);
    }

    public static void release(Player p) {
        HELD.remove(p.getUUID());
    }

    private static void tick(MinecraftServer server) {
        if (HELD.isEmpty()) return;
        for (Map.Entry<UUID, Long> e : Map.copyOf(HELD).entrySet()) {
            ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
            if (p == null || !p.isAlive()) { HELD.remove(e.getKey()); continue; }
            long now = p.level().getGameTime();
            if (now < e.getValue()) continue;
            boolean ok;
            if (CameraControl.inSession(p)) {
                ok = CameraControl.fireHeld(p);
            } else if (p.getVehicle() instanceof de.simon.spacemod.entity.FlyingShipEntity ship && ship.isPilot(p)) {
                ok = PhaserControl.fireFromShip(p, ship, PhaserControl.pilotPower(p), true); // Pilot: Schiffsphaser
            } else {
                ItemStack stack = p.getMainHandItem();
                if (!(stack.getItem() instanceof HandWeaponItem w)) { HELD.remove(e.getKey()); continue; }
                if (p.getCooldowns().isOnCooldown(stack)) continue;
                ok = w.fire(p.level(), p, stack) != InteractionResult.FAIL;
            }
            if (!ok && HELD.containsKey(e.getKey())) HELD.put(e.getKey(), now + 20); // leer/kein Ziel: nicht jeden Tick melden
        }
    }

    /** Client -> Server: Feuertaste gehalten bzw. losgelassen (mit Blickrichtung). */
    public record Message(boolean down, float yRot, float xRot) {
        public static final StreamCodec<RegistryFriendlyByteBuf, Message> STREAM_CODEC = StreamCodec.ofMember(
            (msg, buf) -> {
                buf.writeBoolean(msg.down());
                buf.writeFloat(msg.yRot());
                buf.writeFloat(msg.xRot());
            },
            buf -> new Message(buf.readBoolean(), buf.readFloat(), buf.readFloat()));

        public static void handle(Message msg, CustomPayloadEvent.Context ctx) {
            ServerPlayer p = ctx.getSender();
            if (p == null) return;
            if (msg.down()) {
                p.setYRot(msg.yRot());
                p.setXRot(msg.xRot());
                press(p);
            } else release(p);
        }
    }
}

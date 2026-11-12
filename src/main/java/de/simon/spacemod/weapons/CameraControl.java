package de.simon.spacemod.weapons;

import de.simon.spacemod.turbolift.TurboliftNetwork;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Abilities;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.network.PacketDistributor;

/**
 * Kamerasicht der Waffenstation: der Spieler wird unsichtbar und unverwundbar an einen Kamerapunkt
 * außen am Schiff versetzt und dort festgehalten. Rechtsklick wechselt die Kamera, Linksklick feuert
 * auf das Fadenkreuz, Shift kehrt zur Station zurück.
 */
public final class CameraControl {
    private CameraControl() {}

    private static final class Session {
        final ResourceKey<Level> dim;
        final BlockPos console;
        final Vec3 back;
        final float yRot, xRot;
        final Abilities.Packed abilities;
        final boolean invisible;
        final List<Vec3> cams = new ArrayList<>();
        final List<Vec3> focus = new ArrayList<>();
        final List<String> names = new ArrayList<>();
        int index;
        int power = 1;

        Session(ServerPlayer p, BlockPos console) {
            dim = p.level().dimension();
            this.console = console;
            back = p.position();
            yRot = p.getYRot();
            xRot = p.getXRot();
            abilities = p.getAbilities().pack();
            invisible = p.isInvisible();
        }
    }

    private static final Map<UUID, Session> SESSIONS = new HashMap<>();

    public static void init() {
        TickEvent.ServerTickEvent.Post.BUS.addListener(CameraControl::onServerTick);
        PlayerEvent.PlayerLoggedOutEvent.BUS.addListener(e -> {
            if (e.getEntity() instanceof ServerPlayer sp) exit(sp, false);
        });
    }

    public static boolean active(ServerPlayer player) {
        return SESSIONS.containsKey(player.getUUID());
    }

    /** Rechtsklick auf die Waffenstation: in die Kamerasicht wechseln. */
    public static void enter(ServerLevel level, BlockPos console, ServerPlayer player) {
        if (active(player)) return;
        WeaponsData data = WeaponsData.get(level);
        WeaponsData.Battery b = data.battery(console);
        if (b == null || b.phasers().isEmpty()) {
            PhaserControl.linkPhasers(level, console); // selbst gebaute Station: Phaser suchen
            b = data.battery(console);
        }
        Session s = new Session(player, console);
        if (b != null && !b.cameras().isEmpty()) {
            for (int i = 0; i < b.cameras().size(); i++) {
                s.cams.add(Vec3.atBottomCenterOf(b.cameras().get(i)));
                s.focus.add(Vec3.atCenterOf(i < b.focus().size() ? b.focus().get(i) : b.cameras().get(i)));
                s.names.add(i < b.names().size() ? b.names().get(i) : "Kamera " + (i + 1));
            }
        } else if (b != null && !b.phasers().isEmpty()) {
            // Kameras um die Phaser herum (6 Seiten), wenn keine gespeichert sind
            AABB box = new AABB(b.phasers().get(0));
            for (BlockPos p : b.phasers()) box = box.minmax(new AABB(p));
            Vec3 c = box.getCenter();
            double[][] dirs = {{1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1}, {0, 1, 0}, {0, -1, 0}};
            String[] names = {"Osten", "Westen", "Süden", "Norden", "Oben", "Unten"};
            for (int i = 0; i < 6; i++) {
                double[] d = dirs[i];
                Vec3 cam = new Vec3(
                    d[0] > 0 ? box.maxX + 6 : d[0] < 0 ? box.minX - 6 : c.x,
                    d[1] > 0 ? box.maxY + 6 : d[1] < 0 ? box.minY - 8 : c.y,
                    d[2] > 0 ? box.maxZ + 6 : d[2] < 0 ? box.minZ - 6 : c.z);
                s.cams.add(cam);
                s.focus.add(cam.add(d[0] * 20, d[1] * 20, d[2] * 20));
                s.names.add(names[i]);
            }
        }
        if (s.cams.isEmpty()) {
            player.sendOverlayMessage(Component.translatable("message.simonspacemod.phaser.none"));
            return;
        }
        player.stopRiding();
        SESSIONS.put(player.getUUID(), s);
        Abilities a = player.getAbilities();
        a.invulnerable = true;
        a.mayfly = true;
        a.flying = true;
        a.mayBuild = false;
        player.onUpdateAbilities();
        player.setInvisible(true);
        show(player, s);
        TurboliftNetwork.CHANNEL.send(new CameraState(true), PacketDistributor.PLAYER.with(player));
    }

    /** Nächste Kamera (Rechtsklick in der Kamerasicht). */
    public static void next(ServerPlayer player) {
        Session s = SESSIONS.get(player.getUUID());
        if (s == null) return;
        s.index = (s.index + 1) % s.cams.size();
        show(player, s);
    }

    public static boolean inSession(ServerPlayer player) {
        return SESSIONS.containsKey(player.getUUID());
    }

    /** Phaserstärke 1-3 (Mausrad in der Kamerasicht). */
    public static void power(ServerPlayer player, int delta) {
        Session s = SESSIONS.get(player.getUUID());
        if (s == null) return;
        s.power = Math.max(1, Math.min(3, s.power + delta));
        player.sendOverlayMessage(Component.translatable("message.simonspacemod.phaser.power." + s.power));
    }

    /** Torpedo auf das Fadenkreuz (P = Photonen, O = Quanten). */
    public static void torpedo(ServerPlayer player, boolean quantum, float yRot, float xRot) {
        Session s = SESSIONS.get(player.getUUID());
        if (s == null) return;
        player.setYRot(yRot);
        player.setXRot(xRot);
        PhaserControl.torpedo(player.level(), s.console, player, quantum);
    }

    /** Feuern auf das Fadenkreuz (Linksklick in der Kamerasicht). */
    public static void fire(ServerPlayer player, float yRot, float xRot) {
        Session s = SESSIONS.get(player.getUUID());
        if (s == null) return;
        player.setYRot(yRot);
        player.setXRot(xRot);
        Triggers.press(player);
        PhaserControl.fire(player.level(), s.console, player, s.power);
    }

    /** Weiterfeuern bei gehaltenem Abzug (ohne Nachlade-Meldung). @return false bei Fehlschlag */
    public static boolean fireHeld(ServerPlayer player) {
        Session s = SESSIONS.get(player.getUUID());
        return s != null && PhaserControl.fire(player.level(), s.console, player, s.power, true);
    }

    private static void show(ServerPlayer player, Session s) {
        Vec3 cam = s.cams.get(s.index), f = s.focus.get(s.index);
        Vec3 d = f.subtract(cam);
        float yaw = (float) Math.toDegrees(Math.atan2(-d.x, d.z));
        float pitch = (float) Math.toDegrees(-Math.atan2(d.y, Math.hypot(d.x, d.z))) * 0.5F;
        player.teleportTo(player.level(), cam.x, cam.y, cam.z, Set.of(), yaw, pitch, false);
        player.sendOverlayMessage(Component.translatable("message.simonspacemod.camera.show",
            s.index + 1, s.cams.size(), s.names.get(s.index), s.power));
    }

    /** Zurück zur Station; Fähigkeiten und Sichtbarkeit wiederherstellen. */
    public static void exit(ServerPlayer player, boolean notify) {
        Session s = SESSIONS.remove(player.getUUID());
        if (s == null) return;
        player.getAbilities().apply(s.abilities);
        player.onUpdateAbilities();
        player.setInvisible(s.invisible);
        MinecraftServer server = player.level().getServer();
        ServerLevel level = server.getLevel(s.dim);
        if (level != null) player.teleportTo(level, s.back.x, s.back.y, s.back.z, Set.of(), s.yRot, s.xRot, false);
        player.resetFallDistance();
        if (notify) TurboliftNetwork.CHANNEL.send(new CameraState(false), PacketDistributor.PLAYER.with(player));
    }

    private static void onServerTick(TickEvent.ServerTickEvent.Post event) {
        if (SESSIONS.isEmpty()) return;
        MinecraftServer server = event.server();
        for (Iterator<Map.Entry<UUID, Session>> it = new ArrayList<>(SESSIONS.entrySet()).iterator(); it.hasNext(); ) {
            Map.Entry<UUID, Session> e = it.next();
            ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
            Session s = e.getValue();
            if (p == null) {
                SESSIONS.remove(e.getKey());
                continue;
            }
            if (p.isShiftKeyDown() || !p.isAlive() || p.level().dimension() != s.dim) {
                exit(p, true);
                continue;
            }
            // an der Kamera festhalten
            Vec3 cam = s.cams.get(s.index);
            if (p.position().distanceToSqr(cam) > 0.25)
                p.teleportTo(p.level(), cam.x, cam.y, cam.z, Set.of(), p.getYRot(), p.getXRot(), false);
            p.resetFallDistance();
        }
    }
}

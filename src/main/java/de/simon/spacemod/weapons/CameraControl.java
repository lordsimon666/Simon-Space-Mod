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
        /** Fliegendes Schiff (Kameras bewegen sich mit), Kamerapunkte im Schiffssystem, Platz im Schiff. */
        de.simon.spacemod.entity.FlyingShipEntity ship;
        final List<Vec3> localCams = new ArrayList<>();
        final List<Vec3> localFocus = new ArrayList<>();
        Vec3 seat;

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
        if (de.simon.spacemod.flight.Shipyard.is(level)) {
            enterFlying(console, player);
            return;
        }
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

    /** Mitte eines Punkts im Schiffssystem in der Welt. */
    private static Vec3 world(de.simon.spacemod.entity.FlyingShipEntity ship, Vec3 local) {
        return de.simon.spacemod.flight.ShipPhysics.rotate(ship.rotation(1F), local.x, local.y, local.z).add(ship.position());
    }

    /**
     * Waffenstation in einem fliegenden Schiff (2-Mann-Bedienung): sechs Kameras rund um das Schiff,
     * die mitfliegen; gefeuert wird mit den Phasern und Torpedos des Schiffs. Shift: zurück auf den Platz.
     */
    private static void enterFlying(BlockPos console, ServerPlayer player) {
        var ship = de.simon.spacemod.flight.Shipyard.shipAt(console);
        if (ship == null || ship.structure() == null || player.getVehicle() != ship) return;
        int x0 = Integer.MAX_VALUE, y0 = x0, z0 = x0, x1 = Integer.MIN_VALUE, y1 = x1, z1 = x1;
        for (var e : ship.structure().blocks) {
            if (e.state().isAir()) continue;
            BlockPos p = e.pos();
            x0 = Math.min(x0, p.getX()); y0 = Math.min(y0, p.getY()); z0 = Math.min(z0, p.getZ());
            x1 = Math.max(x1, p.getX()); y1 = Math.max(y1, p.getY()); z1 = Math.max(z1, p.getZ());
        }
        if (x0 > x1) return;
        Session s = new Session(player, console);
        s.ship = ship;
        s.seat = ship.seat(player);
        Vec3 c = new Vec3((x0 + x1) / 2.0, (y0 + y1) / 2.0, (z0 + z1) / 2.0);
        // Bug-Richtung im Schiffssystem (Blickrichtung beim Abheben)
        double yr = Math.toRadians(ship.yaw0());
        Vec3 fwd = new Vec3(-Math.sin(yr), 0, Math.cos(yr));
        double[][] dirs = {{1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1}, {0, 1, 0}, {0, -1, 0}};
        for (double[] d : dirs) {
            Vec3 cam = new Vec3(d[0] > 0 ? x1 + 7 : d[0] < 0 ? x0 - 7 : c.x, d[1] > 0 ? y1 + 7 : d[1] < 0 ? y0 - 8 : c.y,
                d[2] > 0 ? z1 + 7 : d[2] < 0 ? z0 - 7 : c.z);
            s.localCams.add(cam);
            s.localFocus.add(cam.add(d[0] * 20, d[1] * 20, d[2] * 20));
            double dot = d[0] * fwd.x + d[2] * fwd.z;
            String name = d[1] > 0 ? "Oben" : d[1] < 0 ? "Unten" : dot > 0.7 ? "Bug" : dot < -0.7 ? "Heck"
                : (fwd.x * d[2] - fwd.z * d[0]) > 0 ? "Steuerbord" : "Backbord";
            s.names.add(name);
            s.cams.add(cam);
            s.focus.add(cam);
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
        if (s.ship != null) PhaserControl.torpedoFromShip(player, s.ship, quantum);
        else PhaserControl.torpedo(player.level(), s.console, player, quantum);
    }

    /** Feuern auf das Fadenkreuz (Linksklick in der Kamerasicht). */
    public static void fire(ServerPlayer player, float yRot, float xRot) {
        Session s = SESSIONS.get(player.getUUID());
        if (s == null) return;
        player.setYRot(yRot);
        player.setXRot(xRot);
        Triggers.press(player);
        if (s.ship != null) PhaserControl.fireFromShip(player, s.ship, s.power, false);
        else PhaserControl.fire(player.level(), s.console, player, s.power);
    }

    /** Weiterfeuern bei gehaltenem Abzug (ohne Nachlade-Meldung). @return false bei Fehlschlag */
    public static boolean fireHeld(ServerPlayer player) {
        Session s = SESSIONS.get(player.getUUID());
        if (s != null && s.ship != null) return PhaserControl.fireFromShip(player, s.ship, s.power, true);
        return s != null && PhaserControl.fire(player.level(), s.console, player, s.power, true);
    }

    private static void show(ServerPlayer player, Session s) {
        Vec3 cam = s.cams.get(s.index), f = s.focus.get(s.index);
        if (s.ship != null) {
            cam = world(s.ship, s.localCams.get(s.index));
            f = world(s.ship, s.localFocus.get(s.index));
            Vec3 l = s.localCams.get(s.index);
            TurboliftNetwork.CHANNEL.send(new CameraState(true, s.ship.getId(), (float) l.x, (float) l.y, (float) l.z),
                PacketDistributor.PLAYER.with(player));
        }
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
        if (s.ship != null && s.ship.isAlive() && s.ship.level() == player.level()) {
            // zurück auf den Platz im fliegenden Schiff
            Vec3 w = world(s.ship, s.seat);
            player.teleportTo((ServerLevel) s.ship.level(), w.x, w.y, w.z, Set.of(), s.yRot, s.xRot, false);
            player.startRiding(s.ship, true, false);
            s.ship.setSeat(player, s.seat);
        } else if (level != null) player.teleportTo(level, s.back.x, s.back.y, s.back.z, Set.of(), s.yRot, s.xRot, false);
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
            if (s.ship != null && !s.ship.isAlive()) {
                exit(p, true);
                continue;
            }
            // an der Kamera festhalten (fliegende Schiffe: der Client fliegt mit, hier nur grob nachführen)
            Vec3 cam = s.ship != null ? world(s.ship, s.localCams.get(s.index)) : s.cams.get(s.index);
            if (p.position().distanceToSqr(cam) > (s.ship != null ? 6.0 : 0.25))
                p.teleportTo(p.level(), cam.x, cam.y, cam.z, Set.of(), p.getYRot(), p.getXRot(), false);
            p.resetFallDistance();
        }
    }
}

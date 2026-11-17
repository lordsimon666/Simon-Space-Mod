package de.simon.spacemod.client;

import de.simon.spacemod.entity.FlyingShipEntity;
import de.simon.spacemod.flight.ShipControlMessage;
import de.simon.spacemod.flight.ShipPhysics;
import de.simon.spacemod.flight.ShipSeatMessage;
import de.simon.spacemod.flight.ShipStructure;
import de.simon.spacemod.flight.ShipWalker;
import de.simon.spacemod.turbolift.TurboliftNetwork;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.event.TickEvent;
import org.lwjgl.glfw.GLFW;

/**
 * Fliegende Schiffe auf dem Client. Am Steuer: Blickrichtung + Schub (W/S) an den Server, L landen.
 * Sonst: im Schiff herumlaufen (WASD, Springen, Sprinten) mit Kollision gegen die Schiffsblöcke;
 * H = Steuer übernehmen/abgeben (nahe der Konsole).
 */
public final class ShipClient {
    public static final KeyMapping LAND = new KeyMapping("key.simonspacemod.ship_land", GLFW.GLFW_KEY_L, ClientCamera.CATEGORY);
    public static final KeyMapping HELM = new KeyMapping("key.simonspacemod.ship_helm", GLFW.GLFW_KEY_H, ClientCamera.CATEGORY);
    public static final KeyMapping WARP_MENU = new KeyMapping("key.simonspacemod.warp_menu", GLFW.GLFW_KEY_J, ClientCamera.CATEGORY);
    public static final KeyMapping WARP_UP = new KeyMapping("key.simonspacemod.warp_up", GLFW.GLFW_KEY_UP, ClientCamera.CATEGORY);
    public static final KeyMapping WARP_DOWN = new KeyMapping("key.simonspacemod.warp_down", GLFW.GLFW_KEY_DOWN, ClientCamera.CATEGORY);

    private static ShipWalker.State walk;
    private static int walkShip = -1;
    private static int outside;
    private static boolean flying, lastJump, lastShift;
    private static int jumpTimer;

    /** Flugzustand auch dem Server melden (Raumanzug verbraucht dann Ladung). */
    private static void setFlying(Minecraft mc, boolean value) {
        if (mc.player == null) return;
        mc.player.getAbilities().flying = value;
        mc.player.onUpdateAbilities();
    }

    private ShipClient() {}

    /** Platz im Schiff wurde vom Server neu gesetzt (z. B. nach einem Dimensionswechsel). */
    public static void resetWalk() {
        walk = null;
    }

    public static boolean flyingInShip() {
        return flying;
    }

    public static void init() {
        RegisterKeyMappingsEvent.BUS.addListener(e -> {
            e.register(LAND);
            e.register(HELM);
            e.register(WARP_MENU);
            e.register(WARP_UP);
            e.register(WARP_DOWN);
        });
        TickEvent.ClientTickEvent.Post.BUS.addListener(e -> tick());
        net.minecraftforge.client.event.InputEvent.InteractionKeyMappingTriggered.BUS.addListener(ShipClient::onUse);
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        ShipView.followYaw(mc);
        warpStreaks(mc);
        if (mc.player == null || !(mc.player.getVehicle() instanceof FlyingShipEntity ship)) {
            walk = null;
            return;
        }
        while (HELM.consumeClick()) TurboliftNetwork.sendToServer(new ShipControlMessage(2, 0, 0, 0));
        var in = mc.player.input.keyPresses;
        boolean shiftDown = in.shift() && !lastShift;
        lastShift = in.shift();
        if (ship.isPilotEntity(mc.player)) {
            walk = null;
            if (shiftDown) { // Shift am Steuer: Steuer abgeben (man bleibt im Schiff stehen)
                TurboliftNetwork.sendToServer(new ShipControlMessage(2, 0, 0, 0));
                return;
            }
            // Warp: J öffnet das Menü, Pfeil hoch/runter schalten die Stufe
            while (WARP_MENU.consumeClick()) TurboliftNetwork.sendToServer(new de.simon.spacemod.space.WarpMessage.Command(0, 0, 0, 0, 0, false));
            while (WARP_UP.consumeClick()) TurboliftNetwork.sendToServer(new de.simon.spacemod.space.WarpMessage.Command(4, 0, 0, 0, 0, false));
            while (WARP_DOWN.consumeClick()) TurboliftNetwork.sendToServer(new de.simon.spacemod.space.WarpMessage.Command(5, 0, 0, 0, 0, false));
            int throttle = (in.forward() ? 1 : 0) - (in.backward() ? 1 : 0);
            // seitlich schieben (A/D) und hoch/runter (Leertaste/Strg), relativ zum Schiff
            int strafe = (in.right() ? 1 : 0) - (in.left() ? 1 : 0);
            int vertical = (in.jump() ? 1 : 0) - (in.sprint() || mc.options.keySprint.isDown() ? 1 : 0);
            TurboliftNetwork.sendToServer(new ShipControlMessage(0, mc.player.getYRot(), mc.player.getXRot(), throttle, strafe, vertical));
            while (LAND.consumeClick()) TurboliftNetwork.sendToServer(new ShipControlMessage(1, 0, 0, 0));
            return;
        }
        while (LAND.consumeClick()) TurboliftNetwork.sendToServer(new ShipControlMessage(1, 0, 0, 0));
        ShipStructure s = ship.structure();
        if (s == null) return;
        // im Schiff laufen
        if (walk == null || walkShip != ship.getId()) {
            walk = new ShipWalker.State(ship.seat(mc.player), Vec3.ZERO, false);
            walkShip = ship.getId();
            flying = mc.player.getAbilities().flying && mc.player.getAbilities().mayfly; // fliegend eingestiegen
        }
        float localYaw = mc.player.getYRot() - (ship.renderYaw() - ship.yaw0());
        var mv = mc.player.input.getMoveVector();
        Vec3 before = walk.pos();
        // Fliegen im Schiff (Kreativmodus oder geladener Raumanzug): doppelt springen schaltet um
        boolean mayfly = mc.player.getAbilities().mayfly;
        if (in.jump() && !lastJump && mayfly) {
            if (jumpTimer > 0) {
                flying = !flying;
                jumpTimer = 0;
                setFlying(mc, flying);
            } else jumpTimer = 7;
        }
        if (jumpTimer > 0) jumpTimer--;
        lastJump = in.jump();
        if (flying && !mayfly) setFlying(mc, flying = false);
        if (flying) {
            walk = ShipWalker.fly(s, walk, mv.x, mv.y, localYaw, in.jump(), in.shift(), in.sprint() || mc.player.isSprinting());
            if (walk.onGround()) setFlying(mc, flying = false); // gelandet
        } else
            walk = ShipWalker.step(s, walk, mv.x, mv.y, localYaw, in.jump(), in.sprint() || mc.player.isSprinting());
        ship.setSeat(mc.player, walk.pos());
        if (walk.pos().distanceToSqr(before) > 1e-8)
            TurboliftNetwork.sendToServer(new ShipSeatMessage(mc.player.getId(), (float) walk.pos().x, (float) walk.pos().y, (float) walk.pos().z));
        // durch die Tür hinausgegangen (kein Schiffsboden mehr unter den Füßen): aussteigen
        if (!ship.overShip(walk.pos())) outside++;
        else outside = 0;
        if (outside > 3 || walk.pos().length() > s.radius + 6) {
            outside = 0;
            // vom Schiff gefallen
            TurboliftNetwork.sendToServer(new ShipControlMessage(3, 0, 0, 0));
            walk = null;
            return;
        }

    }

    /** Im Warp ziehen Lichtstreifen am Schiff vorbei (entgegen der Flugrichtung). */
    private static void warpStreaks(Minecraft mc) {
        if (mc.player == null || mc.level == null || mc.level.dimension() != de.simon.spacemod.space.WarpDrive.WARP || mc.isPaused()) return;
        Vec3 dir = new Vec3(0, 0, 1);
        if (mc.player.getVehicle() instanceof FlyingShipEntity ship) dir = de.simon.spacemod.flight.ShipPhysics.forward(ship.renderYaw(), 0);
        var rnd = mc.level.getRandom();
        Vec3 c = mc.player.position();
        for (int i = 0; i < 12; i++) {
            Vec3 side = new Vec3((rnd.nextDouble() - 0.5) * 80, (rnd.nextDouble() - 0.5) * 50, (rnd.nextDouble() - 0.5) * 80);
            Vec3 p = c.add(dir.scale(60 + rnd.nextDouble() * 20)).add(side.subtract(dir.scale(side.dot(dir))));
            Vec3 v = dir.scale(-(4 + rnd.nextDouble() * 3));
            mc.level.addParticle(net.minecraft.core.particles.ParticleTypes.END_ROD, p.x, p.y, p.z, v.x, v.y, v.z);
        }
    }

    /** Rechtsklick im fliegenden Schiff: angeklickten Schiffsblock (Kiste, Reaktor …) benutzen. */
    private static boolean onUse(net.minecraftforge.client.event.InputEvent.InteractionKeyMappingTriggered e) {
        if (!e.isUseItem()) return false;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !(mc.player.getVehicle() instanceof FlyingShipEntity ship) || ship.structure() == null) return false;
        var q = ship.rotation(1F);
        Vec3 eye = ShipPhysics.inverse(q, mc.player.getEyePosition().subtract(ship.position()));
        Vec3 look = ShipPhysics.inverse(q, mc.player.getViewVector(1F));
        net.minecraft.core.BlockPos hit = ship.structure().raycast(eye, look, de.simon.spacemod.flight.ShipUseMessage.REACH);
        if (hit == null) return false;
        TurboliftNetwork.sendToServer(new de.simon.spacemod.flight.ShipUseMessage(ship.getId(), hit, net.minecraft.core.Direction.getApproximateNearest(-look.x, -look.y, -look.z)));
        e.setSwingHand(true);
        return true;
    }

    /** Für Tests/Anzeige: Blickrichtung im Schiffssystem. */
    static float localYaw(float playerYaw, FlyingShipEntity ship) {
        return (float) ShipPhysics.wrap(playerYaw - (ship.renderYaw() - ship.yaw0()));
    }
}

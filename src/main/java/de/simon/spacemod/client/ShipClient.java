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

    private static ShipWalker.State walk;
    private static int walkShip = -1;

    private ShipClient() {}

    public static void init() {
        RegisterKeyMappingsEvent.BUS.addListener(e -> {
            e.register(LAND);
            e.register(HELM);
        });
        TickEvent.ClientTickEvent.Post.BUS.addListener(e -> tick());
        net.minecraftforge.client.event.InputEvent.InteractionKeyMappingTriggered.BUS.addListener(ShipClient::onUse);
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !(mc.player.getVehicle() instanceof FlyingShipEntity ship)) {
            walk = null;
            return;
        }
        while (HELM.consumeClick()) TurboliftNetwork.sendToServer(new ShipControlMessage(2, 0, 0, 0));
        var in = mc.player.input.keyPresses;
        if (ship.isPilotEntity(mc.player)) {
            walk = null;
            int throttle = (in.forward() ? 1 : 0) - (in.backward() ? 1 : 0);
            TurboliftNetwork.sendToServer(new ShipControlMessage(0, mc.player.getYRot(), mc.player.getXRot(), throttle));
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
        }
        float localYaw = mc.player.getYRot() - (ship.yaw() - ship.yaw0());
        var mv = mc.player.input.getMoveVector();
        Vec3 before = walk.pos();
        walk = ShipWalker.step(s, walk, mv.x, mv.y, localYaw, in.jump(), in.sprint() || mc.player.isSprinting());
        if (walk.pos().length() > s.radius + 6) {
            // vom Schiff gefallen
            TurboliftNetwork.sendToServer(new ShipControlMessage(3, 0, 0, 0));
            walk = null;
            return;
        }
        ship.setSeat(mc.player, walk.pos());
        if (walk.pos().distanceToSqr(before) > 1e-8)
            TurboliftNetwork.sendToServer(new ShipSeatMessage(mc.player.getId(), (float) walk.pos().x, (float) walk.pos().y, (float) walk.pos().z));
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
        TurboliftNetwork.sendToServer(new de.simon.spacemod.flight.ShipUseMessage(ship.getId(), hit));
        e.setSwingHand(true);
        return true;
    }

    /** Für Tests/Anzeige: Blickrichtung im Schiffssystem. */
    static float localYaw(float playerYaw, FlyingShipEntity ship) {
        return (float) ShipPhysics.wrap(playerYaw - (ship.yaw() - ship.yaw0()));
    }
}

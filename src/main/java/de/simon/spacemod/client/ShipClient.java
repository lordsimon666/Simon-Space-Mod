package de.simon.spacemod.client;

import de.simon.spacemod.entity.FlyingShipEntity;
import de.simon.spacemod.flight.ShipControlMessage;
import de.simon.spacemod.turbolift.TurboliftNetwork;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.event.TickEvent;
import org.lwjgl.glfw.GLFW;

/** Pilot: Blickrichtung und Schub (W/S) jeden Tick an den Server, L = landen. */
public final class ShipClient {
    public static final KeyMapping LAND = new KeyMapping("key.simonspacemod.ship_land", GLFW.GLFW_KEY_L, ClientCamera.CATEGORY);

    private ShipClient() {}

    public static void init() {
        RegisterKeyMappingsEvent.BUS.addListener(e -> e.register(LAND));
        TickEvent.ClientTickEvent.Post.BUS.addListener(e -> tick());
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !(mc.player.getVehicle() instanceof FlyingShipEntity)) return;
        var in = mc.player.input.keyPresses;
        int throttle = (in.forward() ? 1 : 0) - (in.backward() ? 1 : 0);
        TurboliftNetwork.sendToServer(new ShipControlMessage(0, mc.player.getYRot(), mc.player.getXRot(), throttle));
        while (LAND.consumeClick()) TurboliftNetwork.sendToServer(new ShipControlMessage(1, 0, 0, 0));
    }
}

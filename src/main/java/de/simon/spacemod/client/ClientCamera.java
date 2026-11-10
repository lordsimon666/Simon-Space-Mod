package de.simon.spacemod.client;

import de.simon.spacemod.turbolift.TurboliftNetwork;
import de.simon.spacemod.weapons.CameraAction;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec2;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.client.event.RenderHandEvent;

/** Client-Seite der Kamerasicht: Klicks umleiten, Bewegung sperren (nur Shift bleibt), Hand ausblenden. */
public final class ClientCamera {
    private ClientCamera() {}

    private static boolean active;

    public static void setActive(boolean value) {
        active = value;
    }

    public static void init() {
        InputEvent.InteractionKeyMappingTriggered.BUS.addListener(ClientCamera::onInteract);
        MovementInputUpdateEvent.BUS.addListener(ClientCamera::onMovement);
        // Mausrad: Phaserstärke (statt Hotbar wechseln)
        InputEvent.MouseScrollingEvent.BUS.addListener(e -> {
            if (!active || e.getDeltaY() == 0) return false;
            TurboliftNetwork.sendToServer(new CameraAction(CameraAction.POWER, e.getDeltaY() > 0 ? 1 : -1, 0, 0));
            return true;
        });
        RenderHandEvent.BUS.addListener(e -> active);
        ClientPlayerNetworkEvent.LoggingOut.BUS.addListener(e -> active = false);
    }

    /** @return true = Klick abfangen */
    private static boolean onInteract(InputEvent.InteractionKeyMappingTriggered e) {
        if (!active) return false;
        e.setSwingHand(false);
        var player = Minecraft.getInstance().player;
        if (player == null) return true;
        if (e.isAttack()) TurboliftNetwork.sendToServer(new CameraAction(CameraAction.FIRE, 0, player.getYRot(), player.getXRot()));
        else if (e.isUseItem()) TurboliftNetwork.sendToServer(new CameraAction(CameraAction.NEXT, 0, 0, 0));
        return true;
    }

    private static void onMovement(MovementInputUpdateEvent e) {
        if (!active) return;
        var in = e.getInput();
        in.keyPresses = new Input(false, false, false, false, false, in.keyPresses.shift(), false);
        in.moveVector = Vec2.ZERO;
    }
}

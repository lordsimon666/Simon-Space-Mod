package de.simon.spacemod.client;

import de.simon.spacemod.turbolift.TurboliftNetwork;
import de.simon.spacemod.weapons.CameraAction;
import de.simon.spacemod.SimonSpaceMod;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.event.TickEvent;
import org.lwjgl.glfw.GLFW;
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

    /** Kamera eines fliegenden Schiffs: Schiff (Entity-ID, -1 = keins) und Kamerapunkt im Schiffssystem. */
    private static int followShip = -1;
    private static net.minecraft.world.phys.Vec3 followLocal = net.minecraft.world.phys.Vec3.ZERO;

    public static void followShip(int shipId, float x, float y, float z) {
        followShip = shipId;
        followLocal = new net.minecraft.world.phys.Vec3(x, y, z);
    }

    public static void setActive(boolean value) {
        active = value;
    }

    /** P = Photonentorpedo, O = Quantentorpedo (in den Steuerungs-Einstellungen änderbar). */
    public static final KeyMapping.Category CATEGORY =
        KeyMapping.Category.register(Identifier.fromNamespaceAndPath(SimonSpaceMod.MODID, "weapons"));
    public static final KeyMapping PHOTON = new KeyMapping("key.simonspacemod.photon", GLFW.GLFW_KEY_P, CATEGORY);
    public static final KeyMapping QUANTUM = new KeyMapping("key.simonspacemod.quantum", GLFW.GLFW_KEY_O, CATEGORY);
    /** V = Modus des Handphasers (Betäuben / Töten / Abbauen). */
    public static final KeyMapping WEAPON_MODE = new KeyMapping("key.simonspacemod.weapon_mode", GLFW.GLFW_KEY_V, CATEGORY);

    public static void init() {
        RegisterKeyMappingsEvent.BUS.addListener(e -> {
            e.register(PHOTON);
            e.register(QUANTUM);
            e.register(WEAPON_MODE);
        });
        TickEvent.ClientTickEvent.Pre.BUS.addListener(e -> onTick());
        InputEvent.InteractionKeyMappingTriggered.BUS.addListener(ClientCamera::onInteract);
        MovementInputUpdateEvent.BUS.addListener(ClientCamera::onMovement);
        // Mausrad: Phaserstärke (statt Hotbar wechseln)
        InputEvent.MouseScrollingEvent.BUS.addListener(e -> {
            var pl = Minecraft.getInstance().player;
            boolean pilotNow = pl != null && pl.getVehicle() instanceof de.simon.spacemod.entity.FlyingShipEntity fs && fs.isPilotEntity(pl);
            if ((!active && !pilotNow) || e.getDeltaY() == 0) return false;
            TurboliftNetwork.sendToServer(new CameraAction(CameraAction.POWER, e.getDeltaY() > 0 ? 1 : -1, 0, 0));
            return true;
        });
        RenderHandEvent.BUS.addListener(e -> active);
        // beim Zielen mit dem Gewehr leicht heranzoomen
        net.minecraftforge.client.event.ComputeFovModifierEvent.BUS.addListener(e -> {
            if (WeaponClientExtensions.aiming(e.getPlayer(), e.getPlayer().getUseItem()))
                e.setNewFovModifier(e.getFovModifier() * 0.6F);
        });
        ClientPlayerNetworkEvent.LoggingOut.BUS.addListener(e -> active = false);
    }

    /** @return true = Klick abfangen */
    private static boolean triggerDown;

    private static boolean onInteract(InputEvent.InteractionKeyMappingTriggered e) {
        if (!active) return false;
        e.setSwingHand(false);
        var player = Minecraft.getInstance().player;
        if (player == null) return true;
        if (e.isAttack()) TurboliftNetwork.sendToServer(new CameraAction(CameraAction.FIRE, 0, player.getYRot(), player.getXRot()));
        else if (e.isUseItem()) TurboliftNetwork.sendToServer(new CameraAction(CameraAction.NEXT, 0, 0, 0));
        return true;
    }

    private static void onTick() {
        var mc = Minecraft.getInstance();
        var player = mc.player;
        // Kamera eines fliegenden Schiffs: mit dem Schiff mitfliegen (ruckelfrei auf dem Client)
        if (active && followShip >= 0 && player != null && mc.level != null
            && mc.level.getEntity(followShip) instanceof de.simon.spacemod.entity.FlyingShipEntity ship) {
            var w = de.simon.spacemod.flight.ShipPhysics.rotate(ship.rotation(1F), followLocal.x, followLocal.y, followLocal.z).add(ship.position());
            player.setPos(w.x, w.y, w.z);
            player.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
        }
        // Pilot eines fliegenden Schiffs: Linksklick feuert die Schiffsphaser (nicht schlagen)
        boolean pilot = player != null && player.getVehicle() instanceof de.simon.spacemod.entity.FlyingShipEntity fs && fs.isPilotEntity(player);
        if (pilot && mc.gui.screen() == null) while (mc.options.keyAttack.consumeClick()) {}
        // Handphaser und Gewehre: Linksklick feuert (beim Zielen und aus der Hüfte) statt zu schlagen/abzubauen
        if (!active && !pilot && player != null && mc.gui.screen() == null
            && player.getMainHandItem().getItem() instanceof de.simon.spacemod.item.HandWeaponItem) {
            boolean shot = false;
            while (mc.options.keyAttack.consumeClick()) shot = true;
            if (shot) {
                TurboliftNetwork.sendToServer(new de.simon.spacemod.item.FireWeaponMessage(player.getYRot(), player.getXRot()));
                player.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
            }
        }
        // Abzug: Zustand der Feuertaste an den Server (Strahl nur solange gehalten, danach Dauerfeuer)
        boolean want = false;
        if (player != null && mc.gui.screen() == null) {
            if (active || pilot || player.getMainHandItem().getItem() instanceof de.simon.spacemod.item.HandWeaponItem)
                want = mc.options.keyAttack.isDown();
        }
        if (player != null && (want != triggerDown || (want && player.tickCount % 5 == 0)))
            TurboliftNetwork.sendToServer(new de.simon.spacemod.weapons.Triggers.Message(want, player.getYRot(), player.getXRot()));
        triggerDown = want;
        boolean mode = false;
        while (WEAPON_MODE.consumeClick()) mode = true;
        if (mode && player != null && mc.gui.screen() == null
            && (player.getMainHandItem().getItem() instanceof de.simon.spacemod.item.HandWeaponItem
                || player.getOffhandItem().getItem() instanceof de.simon.spacemod.item.HandWeaponItem))
            TurboliftNetwork.sendToServer(new de.simon.spacemod.item.WeaponModeMessage());
        boolean photon = false, quantum = false;
        while (PHOTON.consumeClick()) photon = true;
        while (QUANTUM.consumeClick()) quantum = true;
        if (pilot && mc.gui.screen() == null) { // Torpedos vom Steuer aus
            if (PHOTON.getKey().equals(mc.options.keySocialInteractions.getKey()))
                while (mc.options.keySocialInteractions.consumeClick()) {}
            if (photon) TurboliftNetwork.sendToServer(new CameraAction(CameraAction.PHOTON, 0, player.getYRot(), player.getXRot()));
            if (quantum) TurboliftNetwork.sendToServer(new CameraAction(CameraAction.QUANTUM, 0, player.getYRot(), player.getXRot()));
            return;
        }
        if (!active || player == null) return;
        if (PHOTON.getKey().equals(mc.options.keySocialInteractions.getKey()))
            while (mc.options.keySocialInteractions.consumeClick()) {} // P öffnet in der Kamerasicht nicht die Spielerliste
        if (photon) TurboliftNetwork.sendToServer(new CameraAction(CameraAction.PHOTON, 0, player.getYRot(), player.getXRot()));
        if (quantum) TurboliftNetwork.sendToServer(new CameraAction(CameraAction.QUANTUM, 0, player.getYRot(), player.getXRot()));
    }

    private static void onMovement(MovementInputUpdateEvent e) {
        if (!active) return;
        var in = e.getInput();
        in.keyPresses = new Input(false, false, false, false, false, in.keyPresses.shift(), false);
        in.moveVector = Vec2.ZERO;
    }
}

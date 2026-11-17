package de.simon.spacemod.client;

import de.simon.spacemod.entity.FlyingShipEntity;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.client.event.ViewportEvent;

/**
 * Sicht an Bord: die Kamera kippt mit dem Schiff (siehe CameraMixin), Spielermodelle werden um die Füße
 * gedreht, sodass sie immer senkrecht auf dem Schiffsboden stehen.
 */
public final class ShipView {
    /** Rollwinkel der Kamera in diesem Bild (Forge setzt die Kamera danach noch einmal neu). */
    public static float roll;
    public static boolean tilted;
    /** Am Steuer: Rückansicht des Schiffs statt Ich-Sicht (Taste V). */
    public static boolean rearView;
    private static final Set<Object> PUSHED = Collections.newSetFromMap(new IdentityHashMap<>());

    private ShipView() {}

    public static void init() {
        ViewportEvent.ComputeCameraAngles.BUS.addListener(e -> {
            if (tilted) e.setRoll(roll);
        });
        RenderLivingEvent.Pre.BUS.addListener(e -> {
            if (!(e.getState() instanceof AvatarRenderState a)) return false;
            var mc = Minecraft.getInstance();
            if (mc.level == null || !(mc.level.getEntity(a.id) instanceof net.minecraft.world.entity.Entity ent)) return false;
            FlyingShipEntity ship = FlyingShipEntity.bodyFrameOf(ent); // auch der Pilot steht auf dem Schiffsboden
            if (ship == null) return false;
            e.getPoseStack().pushPose();
            e.getPoseStack().mulPose(ship.tilt(mc.getDeltaTracker().getGameTimeDeltaPartialTick(false)));
            PUSHED.add(e.getState());
            return false;
        });
        RenderLivingEvent.Post.BUS.addListener(e -> {
            if (PUSHED.remove(e.getState())) e.getPoseStack().popPose();
        });
    }

    /** Client-Tick: Blickrichtung dreht sich mit dem Schiff um die Hochachse. */
    static void followYaw(Minecraft mc) {
        var p = mc.player;
        if (p == null) return;
        FlyingShipEntity ship = FlyingShipEntity.frameOf(p);
        if (ship == null) return;
        float d = ship.yawStep();
        if (d == 0) return;
        p.setYRot(p.getYRot() + d);
        p.setYHeadRot(p.getYHeadRot() + d);
        p.setYBodyRot(p.yBodyRot + d);
    }
}

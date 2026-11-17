package de.simon.spacemod.mixin;

import de.simon.spacemod.client.ShipView;
import de.simon.spacemod.entity.FlyingShipEntity;
import de.simon.spacemod.flight.ShipPhysics;
import net.minecraft.client.Camera;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Kamera an Bord: kippt mit dem Schiff (Nicken als Neigung, dazu ggf. Rollen der Sicht). */
@Mixin(Camera.class)
public abstract class CameraMixin {
    @Shadow private Vec3 position;
    @Shadow private @org.jspecify.annotations.Nullable Entity entity;
    @Shadow private float eyeHeight;
    @Shadow private float eyeHeightOld;
    @Shadow private boolean detached;
    @Shadow @org.spongepowered.asm.mixin.Final private Quaternionf rotation;

    @Shadow public abstract void setRotation(float yRot, float xRot, float roll);

    @Shadow protected abstract void setPosition(Vec3 position);

    @Inject(method = "alignWithEntity", at = @At("TAIL"))
    private void simonspacemod$tilt(float partial, CallbackInfo ci) {
        ShipView.roll = 0;
        ShipView.tilted = false;
        var feed = de.simon.spacemod.client.RealFeeds.override;
        if (feed != null) {
            // Zusatz-Durchgang für einen Bildschirm: die Kamera sitzt vor der Linse der Bildschirm-Kamera
            Vec3 f = feed.forward().normalize();
            Vec3 pos = feed.pos().add(f.scale(0.55));
            float yaw = (float) Math.toDegrees(Math.atan2(-f.x, f.z)), pitch = (float) Math.toDegrees(-Math.asin(Math.max(-1, Math.min(1, f.y))));
            setRotation(yaw, pitch, 0);
            setPosition(pos);
            detached = true; // der eigene Spieler ist im Bild zu sehen
            return;
        }
        Entity e = entity;
        if (e == null || e.getVehicle() == null) return;
        if (ShipView.rearView && e.getVehicle() instanceof FlyingShipEntity rs && rs.isPilotEntity(e)) {
            // Rückansicht am Steuer: 5 Blöcke schräg hinter und über dem Schiff, Blick über das Schiff nach vorn.
            // Fest mit dem Schiff verbunden: Gieren und Nicken des Schiffs drehen die Kamera mit.
            Quaternionf frame = rs.frame(partial);
            Vec3 c = rs.getPosition(partial);
            double r = rs.structure() != null ? rs.structure().radius : 4;
            double back = r + 5 * 0.7071, high = r * 0.5 + 5 * 0.7071;
            Vec3 cam = c.add(ShipPhysics.rotate(frame, 0, high, -back));
            // Blick im Schiffssystem: geradeaus (Bug), leicht nach unten auf den Bug
            float localPitch = (float) Math.toDegrees(Math.atan2(high, back + r));
            setRotation(0, localPitch, 0);
            Quaternionf q = new Quaternionf(frame).mul(rotation);
            Vector3f eul = q.getEulerAnglesYXZ(new Vector3f());
            setRotation(180F - (float) Math.toDegrees(eul.y), -(float) Math.toDegrees(eul.x), (float) Math.toDegrees(eul.z));
            setPosition(cam);
            return;
        }
        FlyingShipEntity ship = FlyingShipEntity.bodyFrameOf(e);
        if (ship == null) return;
        boolean view = FlyingShipEntity.frameOf(e) != null; // am Steuer bleibt der Blick waagerecht (er lenkt das Schiff)
        Quaternionf t = ship.tilt(partial);
        Vec3 feet = new Vec3(Mth.lerp(partial, e.xo, e.getX()), Mth.lerp(partial, e.yo, e.getY()), Mth.lerp(partial, e.zo, e.getZ()));
        double h = Mth.lerp(partial, eyeHeightOld, eyeHeight);
        Vec3 pivotOld = feet.add(0, h, 0), pivot = feet.add(ShipPhysics.rotate(t, 0, h, 0));
        Vec3 off = position.subtract(pivotOld); // 0 in der Ich-Sicht, sonst der Abstand der Kamera hinter/vor dem Kopf
        if (!view) { // nur die Lage korrigieren: Augen über den Füßen am geneigten Schiffsboden
            setPosition(pivot.add(off));
            return;
        }
        Quaternionf q = new Quaternionf(t).mul(rotation);
        Vector3f eul = q.getEulerAnglesYXZ(new Vector3f());
        float roll = (float) Math.toDegrees(eul.z);
        setRotation(180F - (float) Math.toDegrees(eul.y), -(float) Math.toDegrees(eul.x), roll);
        setPosition(pivot.add(ShipPhysics.rotate(t, off.x, off.y, off.z)));
        ShipView.roll = roll;
        ShipView.tilted = true;
    }
}

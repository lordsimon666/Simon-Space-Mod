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
    @Shadow @org.spongepowered.asm.mixin.Final private Quaternionf rotation;

    @Shadow public abstract void setRotation(float yRot, float xRot, float roll);

    @Shadow protected abstract void setPosition(Vec3 position);

    @Inject(method = "alignWithEntity", at = @At("TAIL"))
    private void simonspacemod$tilt(float partial, CallbackInfo ci) {
        ShipView.roll = 0;
        ShipView.tilted = false;
        Entity e = entity;
        if (e == null || e.getVehicle() == null) return;
        FlyingShipEntity ship = FlyingShipEntity.frameOf(e);
        if (ship == null) return;
        Quaternionf t = ship.tilt(partial);
        Vec3 feet = new Vec3(Mth.lerp(partial, e.xo, e.getX()), Mth.lerp(partial, e.yo, e.getY()), Mth.lerp(partial, e.zo, e.getZ()));
        double h = Mth.lerp(partial, eyeHeightOld, eyeHeight);
        Vec3 pivotOld = feet.add(0, h, 0), pivot = feet.add(ShipPhysics.rotate(t, 0, h, 0));
        Vec3 off = position.subtract(pivotOld); // 0 in der Ich-Sicht, sonst der Abstand der Kamera hinter/vor dem Kopf
        Quaternionf q = new Quaternionf(t).mul(rotation);
        Vector3f eul = q.getEulerAnglesYXZ(new Vector3f());
        float roll = (float) Math.toDegrees(eul.z);
        setRotation(180F - (float) Math.toDegrees(eul.y), -(float) Math.toDegrees(eul.x), roll);
        setPosition(pivot.add(ShipPhysics.rotate(t, off.x, off.y, off.z)));
        ShipView.roll = roll;
        ShipView.tilted = true;
    }
}

package de.simon.spacemod.mixin;

import de.simon.spacemod.entity.FlyingShipEntity;
import de.simon.spacemod.flight.ShipPhysics;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * An Bord eines geneigten Schiffs: Blickrichtung und Augenhöhe kippen mit dem Schiffsboden. So zielen
 * Klicks, Handphaser und das Benutzen von Blöcken dorthin, wohin man auf dem Bildschirm schaut.
 */
@Mixin(Entity.class)
public abstract class EntityMixin {
    private static Vec3 simonspacemod$rot(Quaternionf q, Vec3 v) {
        return ShipPhysics.rotate(q, v.x, v.y, v.z);
    }

    private @org.jspecify.annotations.Nullable Quaternionf simonspacemod$tilt(float partial) {
        Entity self = (Entity) (Object) this;
        if (self.getVehicle() == null) return null;
        FlyingShipEntity ship = FlyingShipEntity.frameOf(self);
        return ship == null ? null : ship.tilt(partial);
    }

    @Inject(method = "getViewVector", at = @At("RETURN"), cancellable = true)
    private void simonspacemod$view(float partial, CallbackInfoReturnable<Vec3> cir) {
        Quaternionf q = simonspacemod$tilt(partial);
        if (q != null) cir.setReturnValue(simonspacemod$rot(q, cir.getReturnValue()));
    }

    @Inject(method = "getLookAngle", at = @At("RETURN"), cancellable = true)
    private void simonspacemod$look(CallbackInfoReturnable<Vec3> cir) {
        Quaternionf q = simonspacemod$tilt(1F);
        if (q != null) cir.setReturnValue(simonspacemod$rot(q, cir.getReturnValue()));
    }

    @Inject(method = "getEyePosition()Lnet/minecraft/world/phys/Vec3;", at = @At("RETURN"), cancellable = true)
    private void simonspacemod$eye(CallbackInfoReturnable<Vec3> cir) {
        simonspacemod$eyeTilt(1F, cir);
    }

    @Inject(method = "getEyePosition(F)Lnet/minecraft/world/phys/Vec3;", at = @At("RETURN"), cancellable = true)
    private void simonspacemod$eyePartial(float partial, CallbackInfoReturnable<Vec3> cir) {
        simonspacemod$eyeTilt(partial, cir);
    }

    private void simonspacemod$eyeTilt(float partial, CallbackInfoReturnable<Vec3> cir) {
        Entity self = (Entity) (Object) this;
        if (self.getVehicle() == null) return;
        FlyingShipEntity ship = FlyingShipEntity.bodyFrameOf(self); // auch am Steuer: Augen sitzen über den Füßen am Schiffsboden
        if (ship == null) return;
        Quaternionf q = ship.tilt(partial);
        double h = ((Entity) (Object) this).getEyeHeight();
        cir.setReturnValue(cir.getReturnValue().add(0, -h, 0).add(ShipPhysics.rotate(q, 0, h, 0)));
    }
}

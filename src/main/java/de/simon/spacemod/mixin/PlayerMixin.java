package de.simon.spacemod.mixin;

import de.simon.spacemod.entity.FlyingShipEntity;
import de.simon.spacemod.flight.Shipyard;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Reichweite für Blöcke fliegender Schiffe: die echten Blöcke stehen in der Werft, der Spieler fliegt
 * im Schiff mit. Damit Menüs (Kisten, Öfen, Maschinen anderer Mods) offen bleiben, wird der Abstand
 * im Schiffssystem gemessen.
 */
@Mixin(Player.class)
public abstract class PlayerMixin {
    @Inject(method = "isWithinBlockInteractionRange", at = @At("HEAD"), cancellable = true)
    private void simonspacemod$shipRange(BlockPos pos, double buffer, CallbackInfoReturnable<Boolean> cir) {
        Player self = (Player) (Object) this;
        if (!(self.getVehicle() instanceof FlyingShipEntity ship) || ship.plot() < 0 || Shipyard.plotOf(pos) != ship.plot()) return;
        BlockPos local = pos.subtract(ship.plotOrigin());
        AABB box = new AABB(local.getX() - 0.5, local.getY() - 0.5, local.getZ() - 0.5, local.getX() + 0.5, local.getY() + 0.5, local.getZ() + 0.5);
        Vec3 eye = ship.seat(self).add(0, self.getEyeHeight(), 0);
        double range = self.blockInteractionRange() + buffer;
        cir.setReturnValue(box.distanceToSqr(eye) < range * range);
    }
}

package de.simon.spacemod.mixin;

import de.simon.spacemod.village.StarfleetVillagers;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Wer Sternenflotten-Dorfbewohner wird, bekommt (falls noch namenlos) den Namen einer Star-Trek-Figur. */
@Mixin(Villager.class)
public abstract class VillagerMixin {
    @Inject(method = "setVillagerData", at = @At("TAIL"))
    private void simonspacemod$starfleetName(VillagerData data, CallbackInfo ci) {
        Villager self = (Villager) (Object) this;
        if (self.level() == null || self.level().isClientSide()) return;
        StarfleetVillagers.name(self);
    }
}

package de.simon.spacemod.mixin;

import de.simon.spacemod.client.RealFeeds;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Vor jedem Frame: echtes Kamerabild für einen Bildschirm zeichnen (siehe {@link RealFeeds}). */
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    @Inject(method = "extract", at = @At("HEAD"))
    private void simonspacemod$cameraFeeds(DeltaTracker deltaTracker, boolean advanceGameTime, CallbackInfo ci) {
        RealFeeds.beforeExtract((GameRenderer) (Object) this, deltaTracker, advanceGameTime);
    }
}

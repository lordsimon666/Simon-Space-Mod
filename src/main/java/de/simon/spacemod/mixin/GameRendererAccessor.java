package de.simon.spacemod.mixin;

import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.GlobalSettingsUniform;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Zugriff auf die globalen Shader-Einstellungen (enthält die Kameraposition fürs Gelände). */
@Mixin(GameRenderer.class)
public interface GameRendererAccessor {
    @Accessor("globalSettingsUniform")
    GlobalSettingsUniform simonspacemod$globalSettings();
}

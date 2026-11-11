package de.simon.spacemod.client;

import de.simon.spacemod.ModBlocks;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/** Nur auf dem Client: Bildschirme registrieren. */
public final class ClientSetup {
    private ClientSetup() {}

    /** Der Sitz ist unsichtbar. */
    public static void onRegisterRenderers(net.minecraftforge.client.event.EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModBlocks.SEAT.get(), net.minecraft.client.renderer.entity.NoopRenderer::new);
        // Torpedos bestehen nur aus Leuchtpartikeln
        event.registerEntityRenderer(ModBlocks.TORPEDO.get(), net.minecraft.client.renderer.entity.NoopRenderer::new);
        // Waffen in Ständern, Wandhaltern und auf Ladestationen
        event.registerBlockEntityRenderer(ModBlocks.RIFLE_RACK_BE.get(), WeaponHolderRenderer::new);
        event.registerBlockEntityRenderer(ModBlocks.PHASER_HOLDER_BE.get(), WeaponHolderRenderer::new);
        event.registerBlockEntityRenderer(ModBlocks.CHARGING_STATION_BE.get(), WeaponHolderRenderer::new);
    }

    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> MenuScreens.register(ModBlocks.TURBOLIFT_MENU.get(), TurboliftScreen::new));
    }
}

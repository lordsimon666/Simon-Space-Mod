package de.simon.spacemod.client;

import de.simon.spacemod.ModBlocks;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/** Nur auf dem Client: Bildschirme registrieren. */
public final class ClientSetup {
    private ClientSetup() {}

    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> MenuScreens.register(ModBlocks.TURBOLIFT_MENU.get(), TurboliftScreen::new));
    }
}

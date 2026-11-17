package de.simon.spacemod.client.guide;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/** Was das Handbuch auf dem Client tut. */
public final class GuideClient {
    private GuideClient() {}

    /** Nur auf dem Client: 3D-Bilder der Multiblöcke und der Bauplan in der Welt. */
    public static void init() {
        de.simon.spacemod.client.CameraFeeds.init();
        net.minecraftforge.client.event.RegisterPictureInPictureRendererEvent.BUS.addListener(e -> e.register(new StructurePictureRenderer()));
        net.minecraftforge.event.TickEvent.ClientTickEvent.Post.BUS.addListener(e -> StructurePreview.tick());
    }

    /** Rechtsklick öffnet das Buch; schleichend, während ein Bauplan steht, verschwindet stattdessen der Plan. */
    public static void useBook(boolean sneaking) {
        Minecraft minecraft = Minecraft.getInstance();
        if (sneaking && StructurePreview.isActive()) {
            StructurePreview.stop();
            minecraft.gui.hud.getChat().addClientSystemMessage(Component.translatable("message.simonspacemod.guide.preview_hidden"));
            return;
        }
        minecraft.gui.setScreen(new GuideScreen());
    }
}

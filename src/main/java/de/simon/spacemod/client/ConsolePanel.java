package de.simon.spacemod.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Hintergrund im Stil der Minecraft-Fenster (ohne Textur). */
final class ConsolePanel {
    private ConsolePanel() {}

    static void panel(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1) {
        g.fill(x0, y0, x1, y1, 0xFF000000);
        g.fill(x0 + 1, y0 + 1, x1 - 1, y1 - 1, 0xFFC6C6C6);
        g.fill(x0 + 1, y0 + 1, x1 - 2, y0 + 3, 0xFFFFFFFF);
        g.fill(x0 + 1, y0 + 1, x0 + 3, y1 - 2, 0xFFFFFFFF);
        g.fill(x0 + 2, y1 - 3, x1 - 1, y1 - 1, 0xFF555555);
        g.fill(x1 - 3, y0 + 2, x1 - 1, y1 - 1, 0xFF555555);
    }
}

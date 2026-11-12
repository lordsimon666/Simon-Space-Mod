package de.simon.spacemod.client;

import de.simon.spacemod.reactor.ReactorMath;
import de.simon.spacemod.reactor.ReactorMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

/** Reaktor-Anzeige auf dem Werfer-Hintergrund (3 x 3); nicht freigeschaltete Plätze sind zugedeckt. */
public class ReactorScreen extends AbstractContainerScreen<ReactorMenu> {
    private static final Identifier BG = Identifier.withDefaultNamespace("textures/gui/container/dispenser.png");

    public ReactorScreen(ReactorMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Override
    protected void init() {
        super.init();
        this.titleLabelX = (this.imageWidth - this.font.width(this.title)) / 2;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        super.extractBackground(g, mouseX, mouseY, a);
        int xo = (this.width - this.imageWidth) / 2, yo = (this.height - this.imageHeight) / 2;
        g.blit(RenderPipelines.GUI_TEXTURED, BG, xo, yo, 0F, 0F, this.imageWidth, this.imageHeight, 256, 256);
        for (int i = menu.fuelSlots; i < 9; i++) {
            int x = xo + 61 + (i % 3) * 18, y = yo + 16 + (i / 3) * 18;
            g.fill(x, y, x + 18, y + 18, 0xFFC6C6C6);
        }
        // Brennstoff-Balken rechts neben dem Raster
        int left = menu.data.get(2), max = Math.max(1, menu.data.get(3));
        int h = left <= 0 ? 0 : Math.max(1, 52 * left / max);
        g.fill(xo + 120, yo + 17, xo + 126, yo + 69, 0xFF373737);
        g.fill(xo + 120, yo + 69 - h, xo + 126, yo + 69, 0xFF5CFF5C);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor g, int xm, int ym) {
        super.extractLabels(g, xm, ym);
        int size = menu.data.get(0), out = menu.data.get(1), energy = menu.data.get(4);
        int color = 0xFF404040;
        if (size < ReactorMath.MIN) {
            g.text(font, Component.translatable("gui.simonspacemod.reactor.invalid"), 8, 20, 0xFFB02020, false);
            g.text(font, Component.translatable("gui.simonspacemod.reactor.invalid2"), 8, 30, 0xFFB02020, false);
            return;
        }
        g.text(font, size + "³", 8, 20, color, false);
        g.text(font, out + " FE/t", 8, 32, out > 0 ? 0xFF207020 : color, false);
        g.text(font, (energy / 10) + "k FE", 8, 44, color, false); // energy in 100 FE
        g.text(font, Component.translatable("gui.simonspacemod.reactor.slots", menu.fuelSlots), 8, 56, color, false);
        g.text(font, (ReactorMath.burnTicks(size) / 20) + "s", 130, 20, color, false);
        g.text(font, ReactorMath.output(size) + "", 130, 44, color, false);
    }
}

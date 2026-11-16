package de.simon.spacemod.client;

import de.simon.spacemod.defense.BaseShieldBlockEntity;
import de.simon.spacemod.defense.BaseShieldMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;

/** Basisschild: Radius −/+ (mit Shift in Achterschritten), an/aus, Zustand, Leben, Energie. */
public class BaseShieldScreen extends AbstractContainerScreen<BaseShieldMenu> {
    private static final int[][] BUTTONS = {{20, 22, 14, 0}, {92, 22, 14, 1}, {20, 44, 86, 4}};

    public BaseShieldScreen(BaseShieldMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 176, 110);
        this.inventoryLabelY = 10000; // kein Inventar
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        super.extractBackground(g, mouseX, mouseY, a);
        int xo = leftPos, yo = topPos;
        g.fill(xo, yo, xo + imageWidth, yo + imageHeight, 0xFF000000);
        g.fill(xo + 1, yo + 1, xo + imageWidth - 1, yo + imageHeight - 1, 0xFF1E2A3A);
        for (int[] b : BUTTONS) {
            boolean hover = mouseX >= xo + b[0] && mouseX < xo + b[0] + b[2] && mouseY >= yo + b[1] && mouseY < yo + b[1] + 14;
            g.fill(xo + b[0], yo + b[1], xo + b[0] + b[2], yo + b[1] + 14, 0xFF0A0F18);
            g.fill(xo + b[0] + 1, yo + b[1] + 1, xo + b[0] + b[2] - 1, yo + b[1] + 13, hover ? 0xFF4A7AB0 : 0xFF2F4C70);
        }
        // Energie
        int e = menu.data.get(4) * 1000;
        int w = Math.round(150F * Math.min(1F, e / (float) BaseShieldBlockEntity.CAPACITY));
        g.fill(xo + 12, yo + 92, xo + 164, yo + 100, 0xFF0A0F18);
        g.fill(xo + 13, yo + 93, xo + 13 + w, yo + 99, 0xFFE0A020);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor g, int xm, int ym) {
        g.text(font, title, 8, 7, 0xFF9FD0FF, false);
        int r = menu.data.get(0);
        center(g, Component.translatable("gui.simonspacemod.base_shield.radius", r).getString(), 63, 25, 0xFFFFFFFF);
        center(g, "−", 27, 25, 0xFFFFFFFF);
        center(g, "+", 99, 25, 0xFFFFFFFF);
        boolean on = menu.data.get(1) != 0;
        center(g, Component.translatable(on ? "gui.simonspacemod.mining.on" : "gui.simonspacemod.mining.off").getString(), 63, 47, 0xFFFFFFFF);
        int state = menu.data.get(2);
        Component status = switch (state) {
            case BaseShieldBlockEntity.UP -> Component.translatable("gui.simonspacemod.base_shield.up", menu.data.get(3), menu.data.get(5) * 100, menu.data.get(6));
            case BaseShieldBlockEntity.NO_POWER -> Component.translatable("gui.simonspacemod.base_shield.no_power", menu.data.get(6));
            default -> Component.translatable("gui.simonspacemod.base_shield.off");
        };
        g.text(font, status, 12, 68, state == BaseShieldBlockEntity.UP ? 0xFF60E0FF : state == BaseShieldBlockEntity.NO_POWER ? 0xFFFF6060 : 0xFFA0A0A0, false);
        g.text(font, Component.translatable("gui.simonspacemod.base_shield.energy", menu.data.get(4)), 12, 81, 0xFFE0C080, false);
    }

    private void center(GuiGraphicsExtractor g, String s, int cx, int y, int color) {
        g.text(font, s, cx - font.width(s) / 2, y, color, false);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double lx = event.x() - leftPos, ly = event.y() - topPos;
        for (int[] b : BUTTONS)
            if (lx >= b[0] && lx < b[0] + b[2] && ly >= b[1] && ly < b[1] + 14 && minecraft != null && minecraft.gameMode != null) {
                int id = b[3] < 4 && event.hasShiftDown() ? b[3] + 10 : b[3];
                minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
                minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                return true;
            }
        return super.mouseClicked(event, doubleClick);
    }
}

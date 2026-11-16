package de.simon.spacemod.client;

import de.simon.spacemod.storage.CrateMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

/** Großkiste: 9 × 6 Plätze mit Bildlaufleiste rechts (Mausrad oder ziehen). */
public class CrateScreen extends AbstractContainerScreen<CrateMenu> {
    private static final int BAR_X = 174, BAR_Y = 18, BAR_H = CrateMenu.ROWS * 18;
    private boolean dragging;

    public CrateScreen(CrateMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 190, 222);
        this.inventoryLabelY = this.imageHeight - 94;
    }

    private static void panel(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1) {
        g.fill(x0, y0, x1, y1, 0xFF000000);
        g.fill(x0 + 1, y0 + 1, x1 - 1, y1 - 1, 0xFFC6C6C6);
        g.fill(x0 + 1, y0 + 1, x1 - 2, y0 + 3, 0xFFFFFFFF);
        g.fill(x0 + 1, y0 + 1, x0 + 3, y1 - 2, 0xFFFFFFFF);
        g.fill(x0 + 2, y1 - 3, x1 - 1, y1 - 1, 0xFF555555);
        g.fill(x1 - 3, y0 + 2, x1 - 1, y1 - 1, 0xFF555555);
    }

    private static void slotBox(GuiGraphicsExtractor g, int x, int y, boolean active) {
        g.fill(x, y, x + 18, y + 18, 0xFF373737);
        g.fill(x + 1, y + 1, x + 18, y + 18, 0xFFFFFFFF);
        g.fill(x + 1, y + 1, x + 17, y + 17, active ? 0xFF8B8B8B : 0xFF5A5A5A);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        super.extractBackground(g, mouseX, mouseY, a);
        int xo = leftPos, yo = topPos;
        panel(g, xo, yo, xo + imageWidth, yo + imageHeight);
        for (Slot s : menu.slots) slotBox(g, xo + s.x - 1, yo + s.y - 1, s.isActive());
        // Bildlaufleiste
        int max = menu.maxOffset();
        g.fill(xo + BAR_X, yo + BAR_Y - 1, xo + BAR_X + 12, yo + BAR_Y + BAR_H + 1, 0xFF373737);
        g.fill(xo + BAR_X + 1, yo + BAR_Y, xo + BAR_X + 11, yo + BAR_Y + BAR_H, 0xFF8B8B8B);
        int knob = 15, ky = max == 0 ? 0 : (BAR_H - knob) * menu.offset() / max;
        int col = max == 0 ? 0xFFAAAAAA : 0xFFEEEEEE;
        g.fill(xo + BAR_X + 1, yo + BAR_Y + ky, xo + BAR_X + 11, yo + BAR_Y + ky + knob, col);
        g.fill(xo + BAR_X + 1, yo + BAR_Y + ky + knob - 1, xo + BAR_X + 11, yo + BAR_Y + ky + knob, 0xFF555555);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor g, int xm, int ym) {
        super.extractLabels(g, xm, ym);
        int cap = menu.capacity();
        int first = menu.offset() * CrateMenu.COLS + 1, last = Math.min(cap, first + CrateMenu.VISIBLE - 1);
        String info = first + "–" + last + " / " + cap;
        g.text(font, info, imageWidth - 8 - font.width(info), inventoryLabelY, 0xFF404040, false);
    }

    private void scrollTo(int row) {
        int o = Math.max(0, Math.min(menu.maxOffset(), row));
        if (o == menu.offset() || minecraft == null || minecraft.player == null || minecraft.gameMode == null) return;
        menu.clickMenuButton(minecraft.player, o);
        minecraft.gameMode.handleInventoryButtonClick(menu.containerId, o);
    }

    private void dragTo(double my) {
        double f = (my - topPos - BAR_Y - 7.5) / (BAR_H - 15);
        scrollTo((int) Math.round(Math.max(0, Math.min(1, f)) * menu.maxOffset()));
    }

    @Override
    public boolean mouseScrolled(double x, double y, double sx, double sy) {
        if (sy != 0) {
            int step = hasShiftDown() ? CrateMenu.ROWS : 1;
            scrollTo(menu.offset() - (int) Math.signum(sy) * step);
            return true;
        }
        return super.mouseScrolled(x, y, sx, sy);
    }

    private static boolean hasShiftDown() {
        var w = net.minecraft.client.Minecraft.getInstance().getWindow();
        return com.mojang.blaze3d.platform.InputConstants.isKeyDown(w, org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_SHIFT)
            || com.mojang.blaze3d.platform.InputConstants.isKeyDown(w, org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_SHIFT);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double lx = event.x() - leftPos, ly = event.y() - topPos;
        if (lx >= BAR_X && lx < BAR_X + 12 && ly >= BAR_Y && ly < BAR_Y + BAR_H) {
            dragging = true;
            dragTo(event.y());
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (dragging) { dragTo(event.y()); return true; }
        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        dragging = false;
        return super.mouseReleased(event);
    }
}

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
    /** Knöpfe oben rechts: Sortieren und Sortierart (x, y, Breite, Höhe relativ zum Fenster). */
    private static final int SORT_X = 154, MODE_X = 168, BTN_Y = 4, BTN_W = 13, BTN_H = 11;

    public CrateScreen(CrateMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 218, 114 + CrateMenu.ROWS * 18);
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
        panel(g, xo, yo, xo + 190, yo + imageHeight);
        // Upgrade-Leiste rechts
        panel(g, xo + 188, yo + 6, xo + imageWidth, yo + 18 + CrateMenu.UP_Y + 4 * 18 - 6);
        for (Slot s : menu.slots) slotBox(g, xo + s.x - 1, yo + s.y - 1, s.isActive());
        // Sortierknöpfe
        for (int bx : new int[] {SORT_X, MODE_X}) {
            boolean hover = mouseX >= xo + bx && mouseX < xo + bx + BTN_W && mouseY >= yo + BTN_Y && mouseY < yo + BTN_Y + BTN_H;
            g.fill(xo + bx, yo + BTN_Y, xo + bx + BTN_W, yo + BTN_Y + BTN_H, 0xFF373737);
            g.fill(xo + bx + 1, yo + BTN_Y + 1, xo + bx + BTN_W - 1, yo + BTN_Y + BTN_H - 1, hover ? 0xFF9AA8D0 : 0xFF7A7A7A);
        }
        for (int i = 0; i < 3; i++) // Symbol „sortieren“: drei immer kürzere Balken
            g.fill(xo + SORT_X + 3, yo + BTN_Y + 2 + i * 3, xo + SORT_X + 10 - i * 2, yo + BTN_Y + 4 + i * 3, 0xFFFFFFFF);
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
        g.text(font, info, 190 - 8 - font.width(info), inventoryLabelY, 0xFF404040, false);
        g.text(font, "Up", CrateMenu.UP_X, 7, 0xFF404040, false);
        String mode = switch (menu.sortMode()) { case NAME -> "A"; case MOD -> "M"; case COUNT -> "#"; };
        g.text(font, mode, MODE_X + 7 - font.width(mode) / 2, BTN_Y + 2, 0xFFFFFFFF, false);
        String mult = menu.multiplier() + "×";
        g.text(font, mult, CrateMenu.UP_X + 9 - font.width(mult) / 2, CrateMenu.UP_Y + 4 * 18 + 2, 0xFF404040, false);
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

    private int button(double mx, double my) {
        double lx = mx - leftPos, ly = my - topPos;
        if (ly < BTN_Y || ly >= BTN_Y + BTN_H) return -1;
        if (lx >= SORT_X && lx < SORT_X + BTN_W) return CrateMenu.BTN_SORT;
        if (lx >= MODE_X && lx < MODE_X + BTN_W) return CrateMenu.BTN_MODE;
        return -1;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        super.extractRenderState(g, mouseX, mouseY, a);
        int b = button(mouseX, mouseY);
        if (b == CrateMenu.BTN_SORT) g.setTooltipForNextFrame(font, Component.translatable("gui.simonspacemod.crate.sort"), mouseX, mouseY);
        else if (b == CrateMenu.BTN_MODE) g.setTooltipForNextFrame(font, Component.translatable("gui.simonspacemod.crate.mode",
            Component.translatable("gui.simonspacemod.crate.mode." + menu.sortMode().name().toLowerCase(java.util.Locale.ROOT))), mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        int b = button(event.x(), event.y());
        if (b >= 0 && minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, b);
            minecraft.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
            return true;
        }
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

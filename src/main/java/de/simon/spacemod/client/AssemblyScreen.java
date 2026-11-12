package de.simon.spacemod.client;

import de.simon.spacemod.assembly.AssemblyBlockEntity;
import de.simon.spacemod.assembly.AssemblyMenu;
import de.simon.spacemod.assembly.AssemblyRecipes;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Schiffsteil-Fertigung: links 3 x 3 Zutaten, Fortschritt, Ergebnis und Energie; rechts eine Tabelle
 * mit allen baubaren Blöcken (Mausrad blättert). Nach der Auswahl zeigen die Zutatenplätze ausgegraut,
 * was hinein muss – mit Menge, denn ein Platz kann mehrere Stück derselben Sorte brauchen.
 */
public class AssemblyScreen extends AbstractContainerScreen<AssemblyMenu> {
    private static final int COLS = 5, ROWS = 8, LIST_X = 184, LIST_Y = 8;
    private int scroll;

    public AssemblyScreen(AssemblyMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 280, 166);
        this.inventoryLabelY = this.imageHeight - 94;
    }

    private int maxScroll() {
        int rows = (AssemblyRecipes.all().size() + COLS - 1) / COLS;
        return Math.max(0, rows - ROWS);
    }

    private static void panel(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1) {
        g.fill(x0, y0, x1, y1, 0xFF000000);
        g.fill(x0 + 1, y0 + 1, x1 - 1, y1 - 1, 0xFFC6C6C6);
        g.fill(x0 + 1, y0 + 1, x1 - 2, y0 + 3, 0xFFFFFFFF);
        g.fill(x0 + 1, y0 + 1, x0 + 3, y1 - 2, 0xFFFFFFFF);
        g.fill(x0 + 2, y1 - 3, x1 - 1, y1 - 1, 0xFF555555);
        g.fill(x1 - 3, y0 + 2, x1 - 1, y1 - 1, 0xFF555555);
    }

    private static void slotBox(GuiGraphicsExtractor g, int x, int y) {
        g.fill(x, y, x + 18, y + 18, 0xFF373737);
        g.fill(x + 1, y + 1, x + 18, y + 18, 0xFFFFFFFF);
        g.fill(x + 1, y + 1, x + 17, y + 17, 0xFF8B8B8B);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        super.extractBackground(g, mouseX, mouseY, a);
        int xo = leftPos, yo = topPos;
        panel(g, xo, yo, xo + 178, yo + imageHeight);
        panel(g, xo + 178, yo, xo + imageWidth, yo + imageHeight);
        for (Slot s : menu.slots) slotBox(g, xo + s.x - 1, yo + s.y - 1);
        // Energie
        int e = menu.data.get(3) * 10;
        int h = Math.round(52F * Math.min(1F, e / (float) AssemblyBlockEntity.CAPACITY));
        g.fill(xo + 9, yo + 17, xo + 17, yo + 69, 0xFF373737);
        g.fill(xo + 10, yo + 68 - h, xo + 16, yo + 68, 0xFFE04040);
        // Fortschrittspfeil
        int prog = menu.data.get(1), max = Math.max(1, menu.data.get(2));
        g.fill(xo + 92, yo + 41, xo + 116, yo + 47, 0xFF8B8B8B);
        g.fill(xo + 92, yo + 41, xo + 92 + 24 * prog / max, yo + 47, 0xFF40C040);
        g.fill(xo + 116, yo + 38, xo + 118, yo + 50, 0xFF8B8B8B);
        // Ausgegraute Zutaten in leeren Plätzen
        AssemblyRecipes.Recipe r = AssemblyRecipes.get(menu.selected());
        if (r != null) {
            for (int i = 0; i < 9; i++) {
                ItemStack need = r.inputs()[i];
                Slot s = menu.slots.get(i);
                if (need.isEmpty() || s.hasItem()) continue;
                int x = xo + s.x, y = yo + s.y;
                g.fakeItem(need, x, y);
                g.fill(x, y, x + 16, y + 16, 0xA08B8B8B);
                g.itemDecorations(font, need, x, y, String.valueOf(need.getCount()));
            }
            ItemStack out = new ItemStack(r.output(), r.count());
            if (!menu.slots.get(AssemblyBlockEntity.OUTPUT).hasItem()) {
                int x = xo + AssemblyMenu.OUT_X, y = yo + AssemblyMenu.OUT_Y;
                g.fakeItem(out, x, y);
                g.fill(x, y, x + 16, y + 16, 0xA08B8B8B);
            }
        }
        // Tabelle der Blöcke
        List<AssemblyRecipes.Recipe> all = AssemblyRecipes.all();
        scroll = Math.max(0, Math.min(scroll, maxScroll()));
        for (int k = 0; k < COLS * ROWS; k++) {
            int idx = scroll * COLS + k;
            if (idx >= all.size()) break;
            int x = xo + LIST_X + (k % COLS) * 18, y = yo + LIST_Y + (k / COLS) * 18;
            slotBox(g, x, y);
            if (idx == menu.selected()) g.fill(x + 1, y + 1, x + 17, y + 17, 0xFF4F9F4F);
            g.item(new ItemStack(all.get(idx).output()), x + 1, y + 1);
        }
        // Bildlaufleiste
        int ms = maxScroll();
        g.fill(xo + 275, yo + 8, xo + 277, yo + 152, 0xFF555555);
        int bar = ms == 0 ? 144 : Math.max(10, 144 * ROWS / (ROWS + ms));
        int by = ms == 0 ? 0 : (144 - bar) * scroll / ms;
        g.fill(xo + 275, yo + 8 + by, xo + 277, yo + 8 + by + bar, 0xFFEEEEEE);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor g, int xm, int ym) {
        super.extractLabels(g, xm, ym);
        AssemblyRecipes.Recipe r = AssemblyRecipes.get(menu.selected());
        if (r == null) {
            g.text(font, Component.translatable("gui.simonspacemod.assembly.choose"), 30, 72 - 10, 0xFF404040, false);
            return;
        }
        // falsche oder zu wenige Zutaten rot markieren
        for (int i = 0; i < 9; i++) {
            ItemStack need = r.inputs()[i];
            Slot s = menu.slots.get(i);
            if (!s.hasItem()) continue;
            ItemStack have = s.getItem();
            if (need.isEmpty() || !have.is(need.getItem()) || have.getCount() < need.getCount())
                g.fill(s.x, s.y, s.x + 16, s.y + 16, 0x60FF2020);
        }
        String time = String.format("%.1f s", r.ticks() / 20F);
        g.text(font, time, 92, 25, 0xFF404040, false);
        if (menu.data.get(4) == 0) g.text(font, Component.translatable("gui.simonspacemod.assembly.no_power"), 92, 58, 0xFFB02020, false);
    }

    private int hovered(double mx, double my) {
        int lx = (int) Math.floor(mx) - leftPos - LIST_X, ly = (int) Math.floor(my) - topPos - LIST_Y;
        if (lx < 0 || ly < 0 || lx >= COLS * 18 || ly >= ROWS * 18) return -1;
        int idx = (scroll + ly / 18) * COLS + lx / 18;
        return idx < AssemblyRecipes.all().size() ? idx : -1;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        super.extractRenderState(g, mouseX, mouseY, a);
        int idx = hovered(mouseX, mouseY);
        if (idx < 0) return;
        AssemblyRecipes.Recipe r = AssemblyRecipes.get(idx);
        List<FormattedCharSequence> lines = new ArrayList<>();
        lines.add(Component.literal(r.count() + " x ").append(new ItemStack(r.output()).getHoverName()).getVisualOrderText());
        for (ItemStack s : r.inputs())
            if (!s.isEmpty()) lines.add(Component.literal("  " + s.getCount() + " x ").append(s.getHoverName()).withColor(0xAAAAAA).getVisualOrderText());
        lines.add(Component.translatable("gui.simonspacemod.assembly.cost", String.format("%.1f", r.ticks() / 20F),
            AssemblyRecipes.FE_PER_TICK).withColor(0x55FFFF).getVisualOrderText());
        g.setTooltipForNextFrame(lines, mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        int idx = hovered(event.x(), event.y());
        if (idx >= 0 && minecraft != null && minecraft.player != null && minecraft.gameMode != null) {
            if (menu.clickMenuButton(minecraft.player, idx)) {
                minecraft.gameMode.handleInventoryButtonClick(menu.containerId, idx);
                minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_STONECUTTER_SELECT_RECIPE, 1.0F));
            }
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double sx, double sy) {
        if (x >= leftPos + 178 && sy != 0) {
            scroll = Math.max(0, Math.min(maxScroll(), scroll - (int) Math.signum(sy)));
            return true;
        }
        return super.mouseScrolled(x, y, sx, sy);
    }
}

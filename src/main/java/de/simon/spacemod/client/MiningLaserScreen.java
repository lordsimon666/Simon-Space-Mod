package de.simon.spacemod.client;

import de.simon.spacemod.mining.MiningLaserBlockEntity;
import de.simon.spacemod.mining.MiningLaserMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

/** Bergbaulaser: links Größe und Tiefe (−/+, mit Shift in Fünferschritten), an/aus, Energie; rechts Ausbeute. */
public class MiningLaserScreen extends AbstractContainerScreen<MiningLaserMenu> {
    /** Knöpfe: x, y, Breite, Knopf-Nummer. */
    private static final int[][] BUTTONS = {{58, 18, 12, 0}, {94, 18, 12, 1}, {58, 36, 12, 2}, {94, 36, 12, 3}, {22, 56, 84, 4}};

    public MiningLaserScreen(MiningLaserMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 176, 166);
        this.inventoryLabelY = this.imageHeight - 94;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        super.extractBackground(g, mouseX, mouseY, a);
        int xo = leftPos, yo = topPos;
        g.fill(xo, yo, xo + imageWidth, yo + imageHeight, 0xFF000000);
        g.fill(xo + 1, yo + 1, xo + imageWidth - 1, yo + imageHeight - 1, 0xFFC6C6C6);
        for (Slot s : menu.slots) {
            g.fill(xo + s.x - 1, yo + s.y - 1, xo + s.x + 17, yo + s.y + 17, 0xFF373737);
            g.fill(xo + s.x, yo + s.y, xo + s.x + 17, yo + s.y + 17, 0xFFFFFFFF);
            g.fill(xo + s.x, yo + s.y, xo + s.x + 16, yo + s.y + 16, 0xFF8B8B8B);
        }
        for (int[] b : BUTTONS) {
            boolean hover = mouseX >= xo + b[0] && mouseX < xo + b[0] + b[2] && mouseY >= yo + b[1] && mouseY < yo + b[1] + 12;
            g.fill(xo + b[0], yo + b[1], xo + b[0] + b[2], yo + b[1] + 12, 0xFF373737);
            g.fill(xo + b[0] + 1, yo + b[1] + 1, xo + b[0] + b[2] - 1, yo + b[1] + 11, hover ? 0xFF9AA8D0 : 0xFF7A7A7A);
        }
        // Energie
        int e = menu.data.get(3) * 50;
        int h = Math.round(52F * Math.min(1F, e / (float) MiningLaserBlockEntity.CAPACITY));
        g.fill(xo + 8, yo + 17, xo + 16, yo + 69, 0xFF373737);
        g.fill(xo + 9, yo + 68 - h, xo + 15, yo + 68, 0xFFE04040);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor g, int xm, int ym) {
        super.extractLabels(g, xm, ym);
        int size = menu.data.get(0), depth = menu.data.get(1);
        g.text(font, Component.translatable("gui.simonspacemod.mining.size"), 22, 20, 0xFF404040, false);
        g.text(font, Component.translatable("gui.simonspacemod.mining.depth"), 22, 38, 0xFF404040, false);
        center(g, size + "×" + size, 82, 20, 0xFF202020);
        center(g, String.valueOf(depth), 82, 38, 0xFF202020);
        center(g, "−", 64, 20, 0xFFFFFFFF);
        center(g, "+", 100, 20, 0xFFFFFFFF);
        center(g, "−", 64, 38, 0xFFFFFFFF);
        center(g, "+", 100, 38, 0xFFFFFFFF);
        boolean on = menu.data.get(2) != 0;
        center(g, Component.translatable(on ? "gui.simonspacemod.mining.on" : "gui.simonspacemod.mining.off").getString(), 64, 58, 0xFFFFFFFF);
        int status = menu.data.get(4);
        String key = switch (status) {
            case MiningLaserBlockEntity.NO_POWER -> "no_power";
            case MiningLaserBlockEntity.FULL -> "full";
            case MiningLaserBlockEntity.DONE -> "done";
            case MiningLaserBlockEntity.OFF -> "stopped";
            default -> "working";
        };
        int color = status == MiningLaserBlockEntity.OK ? 0xFF207020 : status == MiningLaserBlockEntity.DONE ? 0xFF404040 : 0xFFB02020;
        g.text(font, Component.translatable("gui.simonspacemod.mining." + key, menu.data.get(5), depth), 22, 72, color, false);
    }

    private void center(GuiGraphicsExtractor g, String s, int cx, int y, int color) {
        g.text(font, s, cx - font.width(s) / 2, y, color, false);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double lx = event.x() - leftPos, ly = event.y() - topPos;
        for (int[] b : BUTTONS)
            if (lx >= b[0] && lx < b[0] + b[2] && ly >= b[1] && ly < b[1] + 12 && minecraft != null && minecraft.gameMode != null) {
                boolean shift = event.hasShiftDown();
                int id = b[3] < 4 && shift ? b[3] + 10 : b[3];
                minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
                minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                return true;
            }
        return super.mouseClicked(event, doubleClick);
    }
}

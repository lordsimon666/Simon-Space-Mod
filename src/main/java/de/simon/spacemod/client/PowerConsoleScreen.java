package de.simon.spacemod.client;

import de.simon.spacemod.defense.ConsoleMenu;
import de.simon.spacemod.defense.PowerConsoleBlockEntity;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** Energieverteilung: Anteil je Verbrauchergruppe, Warpkern-Overdrive mit Zeit und Bruch-Countdown. */
public class PowerConsoleScreen extends AbstractContainerScreen<ConsoleMenu> {
    private static final String[] GROUPS = {"weapons", "shields", "engines", "lights", "machines"};
    private Button overdrive;

    public PowerConsoleScreen(ConsoleMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 220, 172);
    }

    private void click(int id) {
        if (minecraft != null && minecraft.gameMode != null) minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
    }

    @Override
    protected void init() {
        super.init();
        for (int i = 0; i < 5; i++) {
            int id = i * 2;
            addRenderableWidget(Button.builder(Component.literal("-"), b -> click(id)).bounds(leftPos + 150, topPos + 20 + i * 20, 20, 18).build());
            addRenderableWidget(Button.builder(Component.literal("+"), b -> click(id + 1)).bounds(leftPos + 174, topPos + 20 + i * 20, 20, 18).build());
        }
        overdrive = addRenderableWidget(Button.builder(Component.empty(), b -> click(10)).bounds(leftPos + 10, topPos + 124, 200, 20).build());
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        boolean on = menu.data.get(5) == 1;
        overdrive.setMessage(Component.translatable(on ? "gui.simonspacemod.power.overdrive_on" : "gui.simonspacemod.power.overdrive_off"));
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        super.extractBackground(g, mouseX, mouseY, a);
        ConsolePanel.panel(g, leftPos, topPos, leftPos + imageWidth, topPos + imageHeight);
        for (int i = 0; i < 5; i++) {
            int v = menu.data.get(i);
            int y = topPos + 26 + i * 20;
            g.fill(leftPos + 80, y, leftPos + 140, y + 6, 0xFF373737);
            g.fill(leftPos + 80, y, leftPos + 80 + 60 * v / 100, y + 6, v >= 75 ? 0xFF40C040 : v >= 25 ? 0xFFE0C030 : 0xFFD04030);
        }
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor g, int xm, int ym) {
        g.text(font, title, 8, 6, 0xFF404040, false);
        long kfe = menu.data.get(8) | ((long) menu.data.get(9) << 15);
        String out = kfe >= 10000 ? String.format("%.1f MFE/t", kfe / 1000.0) : kfe + " kFE/t";
        g.text(font, out, imageWidth - 8 - font.width(out), 6, 0xFF206020, false);
        for (int i = 0; i < 5; i++) {
            g.text(font, Component.translatable("gui.simonspacemod.power." + GROUPS[i]), 10, 25 + i * 20, 0xFF404040, false);
            g.text(font, menu.data.get(i) + "%", 80 + 62, 25 + i * 20, 0xFF404040, false);
        }
        int odSec = menu.data.get(6), breach = menu.data.get(7);
        Component status;
        int color;
        if (breach > 0) {
            status = Component.translatable("gui.simonspacemod.power.breach", breach - 1);
            color = (System.currentTimeMillis() / 400) % 2 == 0 ? 0xFFE02020 : 0xFF801010;
        } else if (menu.data.get(5) == 1) {
            int left = Math.max(0, PowerConsoleBlockEntity.OVERDRIVE_LIMIT / 20 - odSec);
            status = Component.translatable("gui.simonspacemod.power.overdrive_time", left / 60, String.format("%02d", left % 60));
            color = left < 60 ? 0xFFD04020 : 0xFFB07010;
        } else if (odSec > 0) {
            status = Component.translatable("gui.simonspacemod.power.cooling", odSec);
            color = 0xFF406080;
        } else {
            status = Component.translatable("gui.simonspacemod.power.normal");
            color = 0xFF206020;
        }
        g.text(font, status, 10, 152, color, false);
    }
}

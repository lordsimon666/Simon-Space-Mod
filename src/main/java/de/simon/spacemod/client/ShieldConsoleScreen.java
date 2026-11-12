package de.simon.spacemod.client;

import de.simon.spacemod.defense.ConsoleMenu;
import de.simon.spacemod.defense.ShieldConsoleBlockEntity;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** Schildsteuerung: Zustand, Stärke (Durchschnitt und schwächster Block), an/aus, Umverteilen. */
public class ShieldConsoleScreen extends AbstractContainerScreen<ConsoleMenu> {
    private Button toggle, rebalance;

    public ShieldConsoleScreen(ConsoleMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 200, 130);
    }

    private void click(int id) {
        if (minecraft != null && minecraft.gameMode != null) minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
    }

    @Override
    protected void init() {
        super.init();
        toggle = addRenderableWidget(Button.builder(Component.empty(), b -> click(0)).bounds(leftPos + 10, topPos + 82, 85, 20).build());
        rebalance = addRenderableWidget(Button.builder(Component.empty(), b -> click(1)).bounds(leftPos + 105, topPos + 82, 85, 20).build());
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        toggle.setMessage(Component.translatable(menu.data.get(6) == 1 ? "gui.simonspacemod.shield.turn_off" : "gui.simonspacemod.shield.turn_on"));
        int cd = menu.data.get(5);
        rebalance.setMessage(cd > 0 ? Component.literal(cd + " s") : Component.translatable("gui.simonspacemod.shield.rebalance"));
        rebalance.active = cd == 0 && menu.data.get(0) == ShieldConsoleBlockEntity.UP;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        super.extractBackground(g, mouseX, mouseY, a);
        ConsolePanel.panel(g, leftPos, topPos, leftPos + imageWidth, topPos + imageHeight);
        bar(g, topPos + 40, menu.data.get(1));
        bar(g, topPos + 60, menu.data.get(2));
    }

    private void bar(GuiGraphicsExtractor g, int y, int v) {
        g.fill(leftPos + 90, y, leftPos + 190, y + 7, 0xFF373737);
        g.fill(leftPos + 90, y, leftPos + 90 + v, y + 7, v > 60 ? 0xFF4F9FFF : v > 25 ? 0xFFE8C030 : 0xFFE03020);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor g, int xm, int ym) {
        g.text(font, title, 8, 6, 0xFF404040, false);
        int state = menu.data.get(0);
        String key = switch (state) {
            case ShieldConsoleBlockEntity.SCANNING -> "scanning";
            case ShieldConsoleBlockEntity.UP -> "up";
            case ShieldConsoleBlockEntity.NO_EMITTER -> "no_emitter";
            case ShieldConsoleBlockEntity.NO_POWER -> "no_power";
            default -> "off";
        };
        int color = state == ShieldConsoleBlockEntity.UP ? 0xFF2060C0 : state == ShieldConsoleBlockEntity.OFF ? 0xFF606060 : 0xFFB03020;
        g.text(font, Component.translatable("gui.simonspacemod.shield." + key), 10, 22, color, false);
        if (menu.data.get(4) > 0) {
            String info = Component.translatable("gui.simonspacemod.shield.info", menu.data.get(4), menu.data.get(3) * 100).getString();
            g.text(font, info, imageWidth - 10 - font.width(info), 22, 0xFF404040, false);
        }
        g.text(font, Component.translatable("gui.simonspacemod.shield.average"), 10, 40, 0xFF404040, false);
        g.text(font, Component.translatable("gui.simonspacemod.shield.weakest"), 10, 60, 0xFF404040, false);
        g.text(font, menu.data.get(1) + "%", 60, 40, 0xFF404040, false);
        g.text(font, menu.data.get(2) + "%", 60, 60, 0xFF404040, false);
        g.text(font, Component.translatable("gui.simonspacemod.shield.hint"), 10, 112, 0xFF606060, false);
    }
}

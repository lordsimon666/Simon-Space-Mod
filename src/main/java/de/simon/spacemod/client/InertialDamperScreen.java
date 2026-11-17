package de.simon.spacemod.client;

import de.simon.spacemod.defense.ConsoleMenu;
import de.simon.spacemod.flight.ShipPhysics;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * Trägheitsdämpfer-Steuerung: Schieberegler 0–100 % in Zehnerschritten, darunter Stromkosten und wie schnell
 * das Schiff aus voller Fahrt zum Stehen kommt.
 */
public class InertialDamperScreen extends AbstractContainerScreen<ConsoleMenu> {
    private Slider slider;

    public InertialDamperScreen(ConsoleMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 200, 104);
    }

    private int level() {
        return menu.data.get(0);
    }

    @Override
    protected void init() {
        super.init();
        slider = addRenderableWidget(new Slider(leftPos + 10, topPos + 22, imageWidth - 20, level()));
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        // Wert vom Server übernehmen, solange der Spieler nicht gerade zieht
        if (slider != null && !slider.isFocused() && slider.level() != level()) slider.set(level());
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        super.extractBackground(g, mouseX, mouseY, a);
        ConsolePanel.panel(g, leftPos, topPos, leftPos + imageWidth, topPos + imageHeight);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor g, int xm, int ym) {
        g.text(font, title, 8, 6, 0xFF404040, false);
        int l = slider != null ? slider.level() : level();
        int cost = ShipPhysics.damperCost(l);
        Component costText = cost == 0 ? Component.translatable("gui.simonspacemod.damper.free")
            : Component.translatable("gui.simonspacemod.damper.cost", cost);
        g.text(font, costText, 10, 50, cost == 0 ? 0xFF207020 : cost >= 700 ? 0xFFB03020 : 0xFF806010, false);
        Component stop;
        double decay = ShipPhysics.damperDecay(l);
        if (decay <= 0) stop = Component.translatable("gui.simonspacemod.damper.off");
        else {
            // Ticks, bis die Höchstgeschwindigkeit auf Schritttempo gebremst ist
            int ticks = (int) Math.ceil(Math.log(ShipPhysics.STOP_SPEED / ShipPhysics.MAX_SPEED) / Math.log(1 - decay) - 1e-6);
            Component time = ticks < 20 ? Component.translatable("gui.simonspacemod.damper.ticks", ticks)
                : Component.translatable("gui.simonspacemod.damper.seconds", String.format("%.1f", ticks / 20.0));
            stop = Component.translatable("gui.simonspacemod.damper.stop", time);
        }
        g.text(font, stop, 10, 64, 0xFF404040, false);
        g.text(font, Component.translatable("gui.simonspacemod.damper.hint"), 10, 82, 0xFF707070, false);
    }

    /** Schieberegler mit 11 Stufen; jede Änderung geht sofort an den Block. */
    private final class Slider extends AbstractSliderButton {
        Slider(int x, int y, int w, int level) {
            super(x, y, w, 20, Component.empty(), level / 10.0);
            updateMessage();
        }

        int level() {
            return (int) Math.round(value * 10);
        }

        void set(int level) {
            value = level / 10.0;
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.translatable("gui.simonspacemod.damper.level", level() * 10));
        }

        @Override
        protected void applyValue() {
            value = level() / 10.0; // einrasten
            if (minecraft != null && minecraft.gameMode != null && level() != InertialDamperScreen.this.level())
                minecraft.gameMode.handleInventoryButtonClick(menu.containerId, level());
        }
    }
}

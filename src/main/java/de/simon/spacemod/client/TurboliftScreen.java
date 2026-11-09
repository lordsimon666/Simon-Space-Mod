package de.simon.spacemod.client;

import de.simon.spacemod.turbolift.TurboliftData;
import de.simon.spacemod.turbolift.TurboliftMenu;
import de.simon.spacemod.turbolift.TurboliftNetwork;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.MenuAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * Turbolift-Bedienfeld: Liste aller Decks im selben Netz (Klick = losfahren) und Felder, um diesem
 * Lift einen Decknamen und ein Netz zu geben.
 */
public class TurboliftScreen extends Screen implements MenuAccess<TurboliftMenu> {
    private static final int W = 260, ROWS = 7, ROW_H = 20;
    private static final int PANEL = 0xE0101418, FRAME = 0xFFC83228, LABEL = 0xFFFFB050;

    private final TurboliftMenu menu;
    private EditBox nameBox, networkBox;
    private String nameText, networkText;
    private int scroll;

    public TurboliftScreen(TurboliftMenu menu, Inventory inventory, Component title) {
        super(title);
        this.menu = menu;
        this.nameText = menu.name;
        this.networkText = menu.network;
        // zum eigenen Deck scrollen
        for (int i = 0; i < menu.destinations.size(); i++)
            if (menu.destinations.get(i).pos().equals(menu.liftPos)) scroll = Math.max(0, i - ROWS / 2);
        clampScroll();
    }

    @Override
    public TurboliftMenu getMenu() {
        return menu;
    }

    private int left() { return (width - W) / 2; }
    private int top() { return (height - (110 + ROWS * ROW_H)) / 2; }

    private void clampScroll() {
        scroll = Math.max(0, Math.min(scroll, Math.max(0, menu.destinations.size() - ROWS)));
    }

    @Override
    protected void init() {
        int l = left(), t = top();
        nameBox = new EditBox(font, l + 70, t + 22, 180, 16, Component.translatable("gui.simonspacemod.turbolift.name"));
        nameBox.setMaxLength(TurboliftData.MAX_NAME);
        nameBox.setValue(nameText);
        nameBox.setResponder(v -> nameText = v);
        addRenderableWidget(nameBox);
        networkBox = new EditBox(font, l + 70, t + 42, 120, 16, Component.translatable("gui.simonspacemod.turbolift.network"));
        networkBox.setMaxLength(TurboliftData.MAX_NAME);
        networkBox.setValue(networkText);
        networkBox.setResponder(v -> networkText = v);
        addRenderableWidget(networkBox);
        addRenderableWidget(Button.builder(Component.translatable("gui.simonspacemod.turbolift.save"),
                b -> TurboliftNetwork.sendToServer(new TurboliftNetwork.Rename(menu.liftPos, nameText, networkText)))
            .bounds(l + 194, t + 41, 56, 18).build());

        int listTop = t + 80;
        for (int row = 0; row < ROWS; row++) {
            int i = scroll + row;
            if (i >= menu.destinations.size()) break;
            TurboliftMenu.Entry e = menu.destinations.get(i);
            boolean here = e.pos().equals(menu.liftPos);
            Component label = here
                ? Component.translatable("gui.simonspacemod.turbolift.here", e.name())
                : Component.literal(e.name());
            int index = i;
            Button b = Button.builder(label, btn -> minecraft.gameMode.handleInventoryButtonClick(menu.containerId, index))
                .bounds(l + 10, listTop + row * ROW_H, W - 44, 18).build();
            b.active = !here;
            addRenderableWidget(b);
        }
        if (menu.destinations.size() > ROWS) {
            addRenderableWidget(Button.builder(Component.literal("▲"), b -> scrollBy(-ROWS + 1))
                .bounds(l + W - 30, listTop, 20, 18).build());
            addRenderableWidget(Button.builder(Component.literal("▼"), b -> scrollBy(ROWS - 1))
                .bounds(l + W - 30, listTop + (ROWS - 1) * ROW_H, 20, 18).build());
        }
    }

    private void scrollBy(int delta) {
        int old = scroll;
        scroll += delta;
        clampScroll();
        if (scroll != old) rebuildWidgets();
    }

    @Override
    public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        if (scrollY != 0) {
            scrollBy(scrollY > 0 ? -1 : 1);
            return true;
        }
        return super.mouseScrolled(x, y, scrollX, scrollY);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        super.extractBackground(g, mouseX, mouseY, a);
        int l = left(), t = top(), h = 110 + ROWS * ROW_H - 10;
        g.fill(l - 2, t - 2, l + W + 2, t + h + 2, FRAME);
        g.fill(l, t, l + W, t + h, PANEL);
        g.fill(l, t, l + W, t + 16, 0xFFC83228);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        super.extractRenderState(g, mouseX, mouseY, a);
        int l = left(), t = top();
        g.centeredText(font, title.getString(), l + W / 2, t + 4, 0xFFFFFFFF);
        g.text(font, Component.translatable("gui.simonspacemod.turbolift.name"), l + 10, t + 26, LABEL);
        g.text(font, Component.translatable("gui.simonspacemod.turbolift.network"), l + 10, t + 46, LABEL);
        g.text(font, Component.translatable("gui.simonspacemod.turbolift.choose", menu.destinations.size()), l + 10, t + 66, LABEL);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        if (minecraft != null && minecraft.player != null) minecraft.player.closeContainer();
        else super.onClose();
    }
}

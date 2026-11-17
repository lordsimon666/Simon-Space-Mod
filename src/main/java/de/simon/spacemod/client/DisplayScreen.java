package de.simon.spacemod.client;

import de.simon.spacemod.display.DisplayMessage;
import de.simon.spacemod.turbolift.TurboliftNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;

/** Bildschirm-Menü: alle Kameras der Struktur zur Auswahl, Hauptbildschirm an/aus. */
public class DisplayScreen extends Screen {
    private static final int W = 260, ROWS = 8;
    private final int[] cams; // je Kamera: dx, dy, dz, Richtung
    private final int selected;
    private final boolean main;
    private final int width0, height0;
    private int scroll;

    private DisplayScreen(int[] cams, int selected, boolean main, int w, int h) {
        super(Component.translatable("gui.simonspacemod.display.title"));
        this.cams = cams;
        this.selected = selected;
        this.main = main;
        this.width0 = w;
        this.height0 = h;
    }

    public static void open(int[] cams, int selected, boolean main, int w, int h) {
        Minecraft.getInstance().gui.setScreen(new DisplayScreen(cams, selected, main, w, h));
    }

    private int count() {
        return cams.length / 4;
    }

    @Override
    protected void init() {
        clearWidgets();
        int x = (width - W) / 2, y = Math.max(30, (height - (ROWS + 3) * 22) / 2);
        addRenderableWidget(Button.builder(Component.translatable(main ? "gui.simonspacemod.display.main_on" : "gui.simonspacemod.display.main_off"),
            b -> choose(selected, !main)).bounds(x, y, W, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.simonspacemod.display.off"), b -> choose(-1, main))
            .bounds(x, y + 22, W, 20).build()).active = selected >= 0;
        for (int row = 0; row < ROWS && scroll + row < count(); row++) {
            int i = scroll + row;
            int dx = cams[i * 4], dy = cams[i * 4 + 1], dz = cams[i * 4 + 2];
            Direction d = Direction.from3DDataValue(cams[i * 4 + 3]);
            int dist = (int) Math.round(Math.sqrt((double) dx * dx + (double) dy * dy + (double) dz * dz));
            Component label = Component.translatable("gui.simonspacemod.display.camera", i + 1, dist,
                Component.translatable("gui.simonspacemod.display.dir." + d.getSerializedName()));
            if (i == selected) label = Component.literal("▶ ").append(label);
            addRenderableWidget(Button.builder(label, b -> choose(i, main)).bounds(x, y + 50 + row * 22, W, 20).build());
        }
        if (count() == 0)
            addRenderableWidget(Button.builder(Component.translatable("gui.simonspacemod.display.none"), b -> {})
                .bounds(x, y + 50, W, 20).build()).active = false;
    }

    private void choose(int camera, boolean asMain) {
        TurboliftNetwork.sendToServer(new DisplayMessage.Choose(camera, asMain));
        onClose();
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        int old = scroll;
        scroll = Math.max(0, Math.min(Math.max(0, count() - ROWS), scroll - (int) Math.signum(sy)));
        if (scroll != old) init();
        return true;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        super.extractRenderState(g, mouseX, mouseY, a);
        Component t = Component.translatable("gui.simonspacemod.display.title_size", width0, height0, count());
        g.text(font, t, (width - font.width(t)) / 2, 12, 0xFFFFFFFF, true);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}

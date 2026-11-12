package de.simon.spacemod.client;

import de.simon.spacemod.transporter.TransporterChoiceMessage;
import de.simon.spacemod.turbolift.TurboliftNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

/** Transporter-Menü: zuerst „Auf die Oberfläche“, darunter die Transporter in der Nähe (nach Entfernung). */
public class TransporterScreen extends Screen {
    private static final String[] DIRS = {"S", "SW", "W", "NW", "N", "NO", "O", "SO"};
    private final BlockPos pad;
    private final int[] groups;

    private TransporterScreen(BlockPos pad, int[] groups) {
        super(Component.translatable("gui.simonspacemod.transporter.title"));
        this.pad = pad;
        this.groups = groups;
    }

    public static void open(BlockPos pad, int[] groups) {
        Minecraft.getInstance().gui.setScreen(new TransporterScreen(pad, groups));
    }

    private void choose(int i) {
        TurboliftNetwork.sendToServer(new TransporterChoiceMessage(pad, i));
        onClose();
    }

    @Override
    protected void init() {
        int w = 260, x = (width - w) / 2;
        int n = groups.length / 5;
        int y = Math.max(30, (height - (n + 1) * 24) / 2);
        addRenderableWidget(Button.builder(Component.translatable("gui.simonspacemod.transporter.surface"), b -> choose(-1))
            .bounds(x, y, w, 20).build());
        for (int i = 0; i < n; i++) {
            int dx = groups[i * 5], dy = groups[i * 5 + 1], dz = groups[i * 5 + 2], count = groups[i * 5 + 3];
            boolean flying = groups[i * 5 + 4] == 1;
            int dist = (int) Math.round(Math.sqrt((double) dx * dx + (double) dy * dy + (double) dz * dz));
            String dir = DIRS[Math.floorMod((int) Math.round(Math.toDegrees(Math.atan2(-dx, dz)) / 45.0), 8)];
            Component label = Component.translatable(flying ? "gui.simonspacemod.transporter.target_ship" : "gui.simonspacemod.transporter.target",
                dist, dir, (dy >= 0 ? "+" : "") + dy, count);
            int idx = i;
            addRenderableWidget(Button.builder(label, b -> choose(idx)).bounds(x, y + 24 * (i + 1), w, 20).build());
        }
        if (n == 0)
            addRenderableWidget(Button.builder(Component.translatable("gui.simonspacemod.transporter.none"), b -> {}).bounds(x, y + 24, w, 20).build()).active = false;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        super.extractRenderState(g, mouseX, mouseY, a);
        g.text(font, title, (width - font.width(title)) / 2, 12, 0xFFFFFFFF, true);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}

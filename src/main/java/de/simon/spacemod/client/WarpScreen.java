package de.simon.spacemod.client;

import de.simon.spacemod.space.WarpDrive;
import de.simon.spacemod.space.WarpMessage;
import de.simon.spacemod.turbolift.TurboliftNetwork;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Warp-Menü (Taste J am Steuer): Zielkoordinaten, Warpstufe (Schieberegler), Zieldimension, dazu Energie,
 * Erzeugung und Reisezeit. Autopilot fliegt zu den Koordinaten, manuell fliegt man im Warp selbst
 * (Pfeil hoch/runter schalten die Stufe).
 */
public class WarpScreen extends Screen {
    private static final int W = 280, H = 200;
    private final double maxNacelles;
    private final long production;
    private final double[] state; // Warp, rx, ry, rz, tx, ty, tz, Autopilot, Ziel Weltraum
    private EditBox bx, by, bz;
    private WarpSlider slider;
    private boolean destSpace;

    private WarpScreen(double maxNacelles, long production, double[] state) {
        super(Component.translatable("gui.simonspacemod.warp.title"));
        this.maxNacelles = maxNacelles;
        this.production = production;
        this.state = state;
        this.destSpace = state.length > 8 && state[8] == 1;
    }

    public static void open(double maxNacelles, long production, double[] state) {
        Minecraft.getInstance().gui.setScreen(new WarpScreen(maxNacelles, production, state));
    }

    private boolean inWarp() {
        return state[0] > 0;
    }

    private int left() { return (width - W) / 2; }
    private int top() { return (height - H) / 2; }

    @Override
    protected void init() {
        int l = left(), t = top();
        boolean auto = state[7] == 1;
        double fx = auto ? state[4] : state[1], fy = auto ? state[5] : state[2], fz = auto ? state[6] : state[3];
        bx = coord(l + 24, t + 24, fx, "x");
        by = coord(l + 110, t + 24, fy, "y");
        bz = coord(l + 196, t + 24, fz, "z");
        double start = inWarp() ? state[0] : Math.min(2, Math.max(1, maxNacelles));
        slider = addRenderableWidget(new WarpSlider(l + 10, t + 48, W - 20, start));
        slider.active = maxNacelles > 0;
        addRenderableWidget(Button.builder(destLabel(), b -> { destSpace = !destSpace; b.setMessage(destLabel()); })
            .bounds(l + 10, t + 72, W - 20, 20).build());
        Button autoB = addRenderableWidget(Button.builder(Component.translatable("gui.simonspacemod.warp.autopilot"), b -> send(1))
            .bounds(l + 10, t + 150, (W - 30) / 2, 20).build());
        Button manualB = addRenderableWidget(Button.builder(Component.translatable(inWarp() ? "gui.simonspacemod.warp.manual_set" : "gui.simonspacemod.warp.manual"),
            b -> send(2)).bounds(l + 20 + (W - 30) / 2, t + 150, (W - 30) / 2, 20).build());
        autoB.active = manualB.active = maxNacelles > 0;
        Button exitB = addRenderableWidget(Button.builder(Component.translatable("gui.simonspacemod.warp.exit"), b -> send(3))
            .bounds(l + 10, t + 174, W - 20, 20).build());
        exitB.active = inWarp();
    }

    private EditBox coord(int x, int y, double v, String name) {
        EditBox e = new EditBox(font, x, y, 70, 16, Component.literal(name));
        e.setMaxLength(9);
        e.setValue(String.valueOf((long) Math.floor(v)));
        addRenderableWidget(e);
        return e;
    }

    private Component destLabel() {
        return Component.translatable(destSpace ? "gui.simonspacemod.warp.dest_space" : "gui.simonspacemod.warp.dest_planet");
    }

    private static double parse(EditBox e, double fallback) {
        try { return Long.parseLong(e.getValue()); } catch (NumberFormatException ex) { return fallback; }
    }

    private void send(int action) {
        double x = parse(bx, state[1]), y = parse(by, state[2]), z = parse(bz, state[3]);
        TurboliftNetwork.sendToServer(new WarpMessage.Command(action, x, y, z, slider.warp(), destSpace));
        onClose();
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        super.extractBackground(g, mouseX, mouseY, a);
        int l = left(), t = top();
        g.fill(l - 2, t - 2, l + W + 2, t + H + 2, 0xFF0A1433);
        g.fill(l, t, l + W, t + H, 0xFF101C44);
        g.fill(l, t, l + W, t + 16, 0xFF2A4FB0);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        super.extractRenderState(g, mouseX, mouseY, a);
        int l = left(), t = top();
        g.text(font, title, l + 8, t + 4, 0xFFFFFFFF, false);
        g.text(font, "X", l + 12, t + 28, 0xFFAFC8FF, false);
        g.text(font, "Y", l + 98, t + 28, 0xFFAFC8FF, false);
        g.text(font, "Z", l + 184, t + 28, 0xFFAFC8FF, false);
        double w = slider.warp();
        int cost = WarpDrive.cost(w);
        // Gondeln
        Component nac = maxNacelles <= 0 ? Component.translatable("gui.simonspacemod.warp.no_nacelles")
            : Component.translatable("gui.simonspacemod.warp.nacelles", fmt(maxNacelles));
        g.text(font, nac, l + 10, t + 98, maxNacelles <= 0 ? 0xFFFF6060 : 0xFFAFC8FF, false);
        // Energie
        boolean enough = production >= cost;
        g.text(font, Component.translatable("gui.simonspacemod.warp.power", cost, production, fmt(WarpDrive.maxForPower(production))),
            l + 10, t + 110, enough ? 0xFF80FF90 : 0xFFFF6060, false);
        // Reisezeit zu den Koordinaten
        double dx = parse(bx, state[1]) - state[1], dz = parse(bz, state[3]) - state[3];
        double dist = Math.sqrt(dx * dx + dz * dz), sec = dist / WarpDrive.speed(w) / 20;
        g.text(font, Component.translatable("gui.simonspacemod.warp.eta", (long) dist, sec < 1 ? "<1" : String.format(Locale.ROOT, "%.0f", sec),
            (long) (WarpDrive.speed(w) * 20)), l + 10, t + 122, 0xFFFFFFFF, false);
        if (inWarp())
            g.text(font, Component.translatable("gui.simonspacemod.warp.current", fmt(state[0])), l + 10, t + 134, 0xFFFFD45A, false);
    }

    private static String fmt(double w) {
        return String.format(Locale.ROOT, "%.1f", w);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** Warpstufe 1.0 bis zur Gondel-Grenze (höchstens 9.9) in Zehntelschritten. */
    private final class WarpSlider extends AbstractSliderButton {
        WarpSlider(int x, int y, int w, double warp) {
            super(x, y, w, 20, Component.empty(), 0);
            value = (Math.max(1, Math.min(top(), warp)) - 1) / Math.max(1e-9, top() - 1);
            updateMessage();
        }

        private double top() {
            return Math.max(1, maxNacelles);
        }

        double warp() {
            return Math.round((1 + value * (top() - 1)) * 10) / 10.0;
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.translatable("gui.simonspacemod.warp.factor", fmt(warp())));
        }

        @Override
        protected void applyValue() {}
    }
}

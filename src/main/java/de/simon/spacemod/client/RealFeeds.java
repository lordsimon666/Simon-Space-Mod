package de.simon.spacemod.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTexture;
import de.simon.spacemod.SimonSpaceMod;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

/**
 * Echtes Kamerabild: einmal pro Frame wird die Welt zusätzlich aus Sicht einer Bildschirm-Kamera gezeichnet
 * (wie beim Panorama-Foto des Spiels: Kamera umsetzen, extrahieren, Welt zeichnen – ohne HUD und Hand) und das
 * Ergebnis in eine eigene GPU-Textur kopiert. Mehrere Kameras wechseln sich ab. Geht dabei etwas schief, fallen
 * alle Bildschirme auf das einfache Bild aus {@link CameraFeeds} zurück.
 */
public final class RealFeeds {
    /** Lage, die die Hauptkamera während des Zusatz-Durchgangs annimmt (sonst null). */
    public static CameraFeeds.@Nullable Pose override;
    private static boolean busy, failed;

    private static final class Feed extends AbstractTexture {
        final Identifier id;
        CameraFeeds.Pose pose;
        long used;
        boolean ready;
        int w, h;
        boolean has;

        Feed(Identifier id) {
            this.id = id;
        }

        void ensure(int width, int height, com.mojang.blaze3d.GpuFormat format) {
            if (has && w == width && h == height) return;
            releaseTextures();
            var device = RenderSystem.getDevice();
            texture = device.createTexture(() -> "simonspacemod real camera", GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING,
                format, width, height, 1, 1);
            textureView = device.createTextureView(texture);
            sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
            w = width;
            h = height;
            has = true;
        }
    }

    private static final Map<String, Feed> FEEDS = new LinkedHashMap<>();
    private static int counter, next;
    private static long frame;

    private RealFeeds() {}

    public static boolean failed() {
        return failed;
    }

    /** Textur des echten Bildes (null, solange noch keins gezeichnet wurde). Merkt die Kameralage für den nächsten Frame vor. */
    public static @Nullable Feed feedFor(String key, CameraFeeds.Pose pose) {
        if (failed) return null;
        Feed f = FEEDS.get(key);
        if (f == null) {
            f = new Feed(Identifier.fromNamespaceAndPath(SimonSpaceMod.MODID, "camera_real/" + (counter++)));
            FEEDS.put(key, f);
        }
        f.pose = pose;
        f.used = frame;
        return f.ready ? f : null;
    }

    public static @Nullable Identifier texture(String key, CameraFeeds.Pose pose) {
        Feed f = feedFor(key, pose);
        return f == null ? null : f.id;
    }

    /** Seitenverhältnis (Breite / Höhe) des echten Bildes. */
    public static float aspect(String key) {
        Feed f = FEEDS.get(key);
        return f == null || f.h == 0 ? 16 / 9F : f.w / (float) f.h;
    }

    /** Vor dem normalen Extrahieren eines Frames: ein Zusatz-Durchgang für eine Kamera. */
    public static void beforeExtract(GameRenderer gr, DeltaTracker dt, boolean advance) {
        frame++;
        Minecraft mc = Minecraft.getInstance();
        if (busy || failed || !advance || mc.level == null || mc.player == null) return;
        // nicht mehr gezeichnete Bildschirme freigeben
        FEEDS.entrySet().removeIf(en -> {
            if (frame - en.getValue().used < 600) return false;
            mc.getTextureManager().release(en.getValue().id);
            return true;
        });
        List<Feed> active = new ArrayList<>();
        for (Feed f : FEEDS.values()) if (frame - f.used < 30 && f.pose != null) active.add(f);
        if (active.isEmpty()) return;
        Feed f = active.get(Math.floorMod(next++, active.size()));
        var main = gr.mainRenderTarget();
        boolean hide = !mc.gui.hud.isHidden(); // HUD und Hand für den Zusatz-Durchgang ausblenden
        busy = true;
        try {
            override = f.pose;
            if (hide) mc.gui.hud.toggle();
            gr.update(dt);
            gr.extract(dt, true);
            var enc = RenderSystem.getDevice().createCommandEncoder();
            enc.clearColorAndDepthTextures(main.getColorTexture(), new org.joml.Vector4f(0, 0, 0, 1), main.getDepthTexture(), 0.0);
            // Kameraposition für das Gelände steckt in den globalen Shader-Einstellungen, die das Spiel erst im
            // normalen Frame setzt – ohne das würde das Gelände aus Sicht des Spielers gezeichnet
            var rs = gr.gameRenderState();
            var win = rs.windowRenderState;
            var opt = rs.optionsRenderState;
            ((de.simon.spacemod.mixin.GameRendererAccessor) gr).simonspacemod$globalSettings().update(win.width, win.height, opt.glintStrength,
                mc.level.getGameTime(), dt, opt.menuBackgroundBlurriness, rs.levelRenderState.cameraRenderState.pos,
                opt.textureFiltering == net.minecraft.client.TextureFilteringMethod.RGSS);
            gr.renderLevel(dt);
            boolean fresh = !f.has;
            f.ensure(main.width, main.height, main.getColorTexture().getFormat());
            if (fresh || mc.getTextureManager().getTexture(f.id) != f) mc.getTextureManager().register(f.id, f);
            enc.copyTextureToTexture(main.getColorTexture(), f.getTexture(), 0, 0, 0, 0, 0, main.width, main.height);
            f.ready = true;
        } catch (Throwable t) {
            failed = true;
            com.mojang.logging.LogUtils.getLogger().error("Echtes Kamerabild nicht möglich, Bildschirme zeigen das einfache Bild", t);
        } finally {
            override = null;
            if (hide && mc.gui.hud.isHidden()) mc.gui.hud.toggle();
            busy = false;
            gr.update(dt); // Kamera zurück zum Spieler für den normalen Frame
        }
    }
}

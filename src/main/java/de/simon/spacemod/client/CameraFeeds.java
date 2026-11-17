package de.simon.spacemod.client;

import com.mojang.blaze3d.platform.NativeImage;
import de.simon.spacemod.SimonSpaceMod;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;

/**
 * Live-Bilder der Kameras: Für jede sichtbare Bildfläche tastet der Client die Welt von der Kamera aus mit
 * Strahlen ab (Blockfarben mit Licht, Schattierung der Seiten und Entfernungsnebel, Wesen als Farbflecken,
 * Himmel bzw. Sterne) und malt sie in eine Textur. Die Arbeit ist über die Ticks verteilt (Zeile für Zeile),
 * ein fertiges Bild wird hochgeladen – so laufen auch mehrere große Bildschirme flüssig.
 */
public final class CameraFeeds {
    /** Strahlen je Tick über alle Bildschirme zusammen. */
    private static final int RAY_BUDGET = 6000;
    private static final double RANGE = 96, FOV = Math.toRadians(70);

    /** Lage der Kamera in der Welt: Mittelpunkt, Blickrichtung, oben. */
    public record Pose(Vec3 pos, Vec3 forward, Vec3 up) {}

    private static final class Feed {
        final Identifier id;
        final DynamicTexture tex;
        final int w, h;
        Pose pose;
        int row;
        long used;
        int[] buf;
        List<AABB> boxes = List.of();
        List<Integer> boxColors = List.of();

        Feed(Identifier id, DynamicTexture tex, int w, int h) {
            this.id = id;
            this.tex = tex;
            this.w = w;
            this.h = h;
            this.buf = new int[w * h];
        }
    }

    private static final Map<String, Feed> FEEDS = new HashMap<>();
    private static int counter;
    private static Identifier noSignal;
    private static long tick;

    private CameraFeeds() {}

    public static void init() {
        TickEvent.ClientTickEvent.Post.BUS.addListener(e -> update());
    }

    /** Auflösung: bis 128 Bildpunkte auf der langen Seite, 8 bis 32 je Block. */
    public static int pixelsPerBlock(int w, int h) {
        return Math.max(8, Math.min(32, 128 / Math.max(w, h)));
    }

    /** Textur des Bildes für eine Bildfläche (wird bei Bedarf angelegt); die Lage der Kamera wird jedes Bild erneuert. */
    public static Identifier feed(String key, int blocksW, int blocksH, Pose pose) {
        int ppb = pixelsPerBlock(blocksW, blocksH), w = blocksW * ppb, h = blocksH * ppb;
        Feed f = FEEDS.get(key);
        if (f == null || f.w != w || f.h != h) {
            if (f != null) Minecraft.getInstance().getTextureManager().release(f.id);
            Identifier id = Identifier.fromNamespaceAndPath(SimonSpaceMod.MODID, "camera_feed/" + (counter++));
            DynamicTexture tex = new DynamicTexture(() -> "simonspacemod camera feed", w, h, true);
            for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) tex.getPixels().setPixel(x, y, 0xFF101418);
            tex.upload();
            Minecraft.getInstance().getTextureManager().register(id, tex);
            f = new Feed(id, tex, w, h);
            FEEDS.put(key, f);
        }
        f.pose = pose;
        f.used = tick;
        return f.id;
    }

    /** Rauschen für Bildschirme ohne Kamera. */
    public static Identifier noSignal() {
        if (noSignal == null) {
            DynamicTexture tex = new DynamicTexture(() -> "simonspacemod no signal", 32, 32, true);
            java.util.Random r = new java.util.Random(7);
            for (int y = 0; y < 32; y++)
                for (int x = 0; x < 32; x++) {
                    int g = 20 + r.nextInt(50);
                    tex.getPixels().setPixel(x, y, 0xFF000000 | g << 16 | g << 8 | (g + 10));
                }
            tex.upload();
            noSignal = Identifier.fromNamespaceAndPath(SimonSpaceMod.MODID, "camera_feed/no_signal");
            Minecraft.getInstance().getTextureManager().register(noSignal, tex);
        }
        return noSignal;
    }

    private static void update() {
        tick++;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) {
            if (!FEEDS.isEmpty()) {
                for (Feed f : FEEDS.values()) mc.getTextureManager().release(f.id);
                FEEDS.clear();
            }
            return;
        }
        // nicht mehr gezeichnete Bildschirme freigeben
        FEEDS.entrySet().removeIf(en -> {
            if (tick - en.getValue().used < 100) return false;
            mc.getTextureManager().release(en.getValue().id);
            return true;
        });
        List<Feed> active = new ArrayList<>();
        for (Feed f : FEEDS.values()) if (tick - f.used < 10 && f.pose != null) active.add(f);
        if (active.isEmpty()) return;
        int budget = RAY_BUDGET / active.size();
        for (Feed f : active) {
            int rows = Math.max(1, budget / f.w);
            for (int i = 0; i < rows; i++) {
                if (f.row == 0) gatherEntities(level, f);
                renderRow(level, f, f.row);
                if (++f.row >= f.h) {
                    f.row = 0;
                    NativeImage img = f.tex.getPixels();
                    for (int y = 0; y < f.h; y++)
                        for (int x = 0; x < f.w; x++) img.setPixel(x, y, f.buf[y * f.w + x]);
                    f.tex.upload();
                    break;
                }
            }
        }
    }

    private static void gatherEntities(ClientLevel level, Feed f) {
        List<AABB> boxes = new ArrayList<>();
        List<Integer> colors = new ArrayList<>();
        for (Entity e : level.getEntities((Entity) null, new AABB(f.pose.pos(), f.pose.pos()).inflate(64),
                e -> e.isAlive() && !(e instanceof de.simon.spacemod.entity.FlyingShipEntity) && !e.isSpectator())) {
            boxes.add(e.getBoundingBox());
            colors.add(e instanceof Player ? 0x3A5FC8 : 0x000000 | (e.getType().hashCode() * 0x9E3779B1 & 0xFFFFFF) | 0x404040);
        }
        f.boxes = boxes;
        f.boxColors = colors;
    }

    private static void renderRow(ClientLevel level, Feed f, int y) {
        Pose p = f.pose;
        Vec3 fw = p.forward().normalize(), up = p.up().normalize(), right = fw.cross(up).normalize();
        up = right.cross(fw).normalize();
        double t = Math.tan(FOV / 2), aspect = f.w / (double) f.h;
        Vec3 start = p.pos().add(fw.scale(0.51));
        boolean space = level.dimension() == de.simon.spacemod.space.SpaceTravel.SPACE || level.dimension() == de.simon.spacemod.space.WarpDrive.WARP;
        double vy = (1 - 2 * (y + 0.5) / f.h) * t;
        for (int x = 0; x < f.w; x++) {
            double vx = (2 * (x + 0.5) / f.w - 1) * t * aspect;
            Vec3 d = fw.add(right.scale(vx)).add(up.scale(vy)).normalize();
            f.buf[y * f.w + x] = 0xFF000000 | trace(level, f, start, d, space);
        }
    }

    /** Farbe eines Strahls (RGB). */
    private static int trace(ClientLevel level, Feed f, Vec3 o, Vec3 d, boolean space) {
        // Wesen
        double entDist = RANGE;
        int entColor = -1;
        Vec3 end = o.add(d.scale(RANGE));
        for (int i = 0; i < f.boxes.size(); i++) {
            var hit = f.boxes.get(i).clip(o, end);
            if (hit.isPresent()) {
                double dist = hit.get().distanceTo(o);
                if (dist < entDist) { entDist = dist; entColor = f.boxColors.get(i); }
            }
        }
        // Blöcke (Schritt für Schritt durch das Raster)
        int x = (int) Math.floor(o.x), y = (int) Math.floor(o.y), z = (int) Math.floor(o.z);
        int sx = d.x > 0 ? 1 : -1, sy = d.y > 0 ? 1 : -1, sz = d.z > 0 ? 1 : -1;
        double tdx = Math.abs(1 / d.x), tdy = Math.abs(1 / d.y), tdz = Math.abs(1 / d.z);
        double tmx = (d.x > 0 ? Math.floor(o.x) + 1 - o.x : o.x - Math.floor(o.x)) * tdx;
        double tmy = (d.y > 0 ? Math.floor(o.y) + 1 - o.y : o.y - Math.floor(o.y)) * tdy;
        double tmz = (d.z > 0 ? Math.floor(o.z) + 1 - o.z : o.z - Math.floor(o.z)) * tdz;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        double dist = 0;
        int face = 1; // 0 = x, 1 = y, 2 = z
        while (dist < Math.min(RANGE, entDist)) {
            pos.set(x, y, z);
            if (level.isOutsideBuildHeight(pos)) break;
            BlockState st = level.getBlockState(pos);
            if (!st.isAir()) {
                int col = st.getMapColor(level, pos).col;
                if (col == 0) col = 0x808080;
                double shade = face == 1 ? (d.y < 0 ? 1.0 : 0.55) : face == 0 ? 0.82 : 0.7;
                pos.set(x - (face == 0 ? sx : 0), y - (face == 1 ? sy : 0), z - (face == 2 ? sz : 0));
                double light = 0.25 + 0.75 * level.getMaxLocalRawBrightness(pos) / 15.0;
                return fog(scale(col, shade * light), dist, space, d);
            }
            if (tmx < tmy && tmx < tmz) { x += sx; dist = tmx; tmx += tdx; face = 0; }
            else if (tmy < tmz) { y += sy; dist = tmy; tmy += tdy; face = 1; }
            else { z += sz; dist = tmz; tmz += tdz; face = 2; }
        }
        if (entColor >= 0) return fog(entColor, entDist, space, d);
        return sky(d, space);
    }

    private static int scale(int rgb, double f) {
        int r = (int) Math.min(255, ((rgb >> 16) & 255) * f), g = (int) Math.min(255, ((rgb >> 8) & 255) * f), b = (int) Math.min(255, (rgb & 255) * f);
        return r << 16 | g << 8 | b;
    }

    private static int fog(int rgb, double dist, boolean space, Vec3 d) {
        double k = Math.min(1, dist / RANGE);
        k = k * k * 0.75;
        int s = space ? 0x000000 : sky(d, false);
        int r = (int) (((rgb >> 16) & 255) * (1 - k) + ((s >> 16) & 255) * k);
        int g = (int) (((rgb >> 8) & 255) * (1 - k) + ((s >> 8) & 255) * k);
        int b = (int) ((rgb & 255) * (1 - k) + (s & 255) * k);
        return r << 16 | g << 8 | b;
    }

    private static int sky(Vec3 d, boolean space) {
        if (space) {
            // Sterne: feste Richtungen
            long hsh = Math.round(d.x * 300) * 73856093L ^ Math.round(d.y * 300) * 19349663L ^ Math.round(d.z * 300) * 83492791L;
            return Math.floorMod(hsh, 211) == 0 ? 0xE8ECFF : 0x02030A;
        }
        double k = Math.max(0, Math.min(1, d.y * 1.5 + 0.3));
        int r = (int) (0xC8 * (1 - k) + 0x6A * k), g = (int) (0xDC * (1 - k) + 0x9A * k), b = (int) (0xFF * (1 - k) + 0xF0 * k);
        return r << 16 | g << 8 | b;
    }
}

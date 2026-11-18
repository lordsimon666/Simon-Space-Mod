package de.simon.spacemod.client;

import com.mojang.blaze3d.platform.NativeImage;
import de.simon.spacemod.SimonSpaceMod;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

/**
 * Hauttexturen der Sternenflotten-Dorfbewohner: Haut der Figur (Gesicht, Haare) plus die Uniform aus den
 * Rüstungstexturen der Uniform-Gegenstände, dazu die passende Hose (NX blau, sonst schwarz) und Stiefel;
 * bei der Hälfte der Dorfbewohner ist die Uniform zerrissen. Wird einmal je Kombination
 * zusammengesetzt und als eigene Textur gemerkt.
 */
public final class CrewSkins {
    private static final Map<String, Identifier> CACHE = new HashMap<>();

    private CrewSkins() {}

    /**
     * @param tear Muster der Risse (0..3) oder -1 für eine heile Uniform
     */
    public static Identifier texture(String skin, String uniform, int tear) {
        String key = skin + "/" + uniform + (tear >= 0 ? "/torn" + tear : "");
        Identifier id = CACHE.get(key);
        if (id != null) return id;
        id = Identifier.fromNamespaceAndPath(SimonSpaceMod.MODID, "starfleet_crew/" + key);
        try {
            NativeImage img = load("textures/entity/starfleet/skin_" + skin + ".png");
            if (img == null) throw new IllegalStateException("Haut fehlt: " + skin);
            int skinTone = img.getPixel(45, 31); // Hand: bleibt immer frei
            String eq = "textures/entity/equipment/";
            if (uniform.equals("outfit_seven")) {
                // Sevens Anzug: Oberteil und Hose aus einem Stück, ohne Stiefel
                overlay(img, load(eq + "humanoid_leggings/outfit_seven_legs.png"), Part.LEGS);
            } else {
                // Hose passend zum Oberteil: nur die NX-Uniform hat die blaue Hose
                String trousers = uniform.startsWith("uniform_nx_") ? "trousers_nx" : "trousers_black";
                overlay(img, load(eq + "humanoid_leggings/" + trousers + ".png"), Part.LEGS);
                overlay(img, load(eq + "humanoid/boots_starfleet.png"), Part.LEGS);
            }
            overlay(img, load(eq + "humanoid/" + uniform + ".png"), Part.TOP);
            if (tear >= 0) tear(img, skinTone, tear);
            Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(() -> "simonspacemod crew " + key, img));
        } catch (Exception e) {
            com.mojang.logging.LogUtils.getLogger().warn("Sternenflotten-Haut {} nicht möglich: {}", key, e.toString());
            id = Identifier.fromNamespaceAndPath(SimonSpaceMod.MODID, "textures/entity/starfleet/skin_" + skin + ".png");
        }
        CACHE.put(key, id);
        return id;
    }

    /** Flächen der Kleidung (x, y, Breite, Höhe): Rumpf vorn/hinten, Arme vorn/außen, Beine vorn. */
    private static final int[][] CLOTH = {{20, 20, 8, 12}, {32, 20, 8, 12}, {44, 20, 4, 10}, {40, 20, 4, 10}, {36, 52, 4, 10},
        {4, 20, 4, 12}, {20, 52, 4, 12}};

    /** Risse: kurze schräge Schlitze, durch die die Haut zu sehen ist, mit angesengten (dunkleren) Rändern. */
    private static void tear(NativeImage img, int skinTone, int pattern) {
        java.util.Random r = new java.util.Random(4711L + pattern * 977L);
        int tears = 4 + r.nextInt(3);
        for (int t = 0; t < tears; t++) {
            int[] f = CLOTH[t == 0 ? 0 : r.nextInt(CLOTH.length)];
            int len = 2 + r.nextInt(3), dx = r.nextBoolean() ? 1 : -1;
            int x = f[0] + r.nextInt(f[2]), y = f[1] + r.nextInt(Math.max(1, f[3] - len));
            for (int i = 0; i < len; i++) {
                int px = x + (i % 2 == 0 ? 0 : dx) * (i / 2 + 1) / 2, py = y + i;
                if (px < f[0] || px >= f[0] + f[2] || py >= f[1] + f[3]) break;
                img.setPixel(px, py, skinTone);
                for (int[] n : new int[][] {{1, 0}, {-1, 0}}) { // Ränder angesengt
                    int nx = px + n[0];
                    if (nx < f[0] || nx >= f[0] + f[2]) continue;
                    int c = img.getPixel(nx, py);
                    if (c != skinTone) img.setPixel(nx, py, darker(c));
                }
            }
        }
    }

    private static int darker(int argb) {
        int r = (argb >> 16 & 0xFF) * 3 / 5, g = (argb >> 8 & 0xFF) * 3 / 5, b = (argb & 0xFF) * 3 / 5;
        return 0xFF000000 | r << 16 | g << 8 | b;
    }

    private static @Nullable NativeImage load(String path) throws java.io.IOException {
        var res = Minecraft.getInstance().getResourceManager().getResource(Identifier.fromNamespaceAndPath(SimonSpaceMod.MODID, path));
        if (res.isEmpty()) return null;
        try (InputStream in = res.get().open()) {
            return NativeImage.read(in);
        }
    }

    /**
     * Rüstungstextur (64×32, Schnittmuster wie die obere Hälfte einer Haut) über die Haut legen: Rumpf und rechte
     * Gliedmaßen an ihre Stelle, die linken Gliedmaßen zusätzlich an ihre eigenen Plätze der 64×64-Haut.
     * {@code part}: nur Rumpf und Arme (Oberteil), nur Beine (Hose, Stiefel) oder alles (Anzug).
     */
    private enum Part { TOP, LEGS, ALL }

    private static void overlay(NativeImage skin, @Nullable NativeImage armor, Part part) {
        if (armor == null) return;
        try (armor) {
            int sx = armor.getWidth() / 64; // höher aufgelöste Texturen
            for (int y = 16; y < 32; y++)
                for (int x = 0; x < 56; x++) {
                    boolean leg = x < 16, body = x >= 16 && x < 40, arm = x >= 40;
                    if (part == Part.TOP && leg || part == Part.LEGS && !leg) continue;
                    boolean legs = part == Part.LEGS;
                    if (arm && (y >= 30 || y < 20 && x >= 48 && x < 52)) continue; // Hände bleiben frei
                    int c = armor.getPixel(x * sx, y * sx);
                    if ((c >>> 24) < 128) continue;
                    skin.setPixel(x, y, c | 0xFF000000);
                    if (leg) skin.setPixel(x + 16, y + 32, c | 0xFF000000);       // linkes Bein
                    else if (arm) skin.setPixel(x - 8, y + 32, c | 0xFF000000);   // linker Arm
                    else if (body && legs) skin.setPixel(x, y, c | 0xFF000000);
                }
        }
    }
}

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
 * Rüstungstexturen der Uniform-Gegenstände, dazu die passende Hose (NX blau, sonst schwarz) und Stiefel. Wird einmal je Kombination
 * zusammengesetzt und als eigene Textur gemerkt.
 */
public final class CrewSkins {
    private static final Map<String, Identifier> CACHE = new HashMap<>();

    private CrewSkins() {}

    public static Identifier texture(String skin, String uniform) {
        String key = skin + "/" + uniform;
        Identifier id = CACHE.get(key);
        if (id != null) return id;
        id = Identifier.fromNamespaceAndPath(SimonSpaceMod.MODID, "starfleet_crew/" + skin + "/" + uniform);
        try {
            NativeImage img = load("textures/entity/starfleet/skin_" + skin + ".png");
            if (img == null) throw new IllegalStateException("Haut fehlt: " + skin);
            // Hose passend zum Oberteil: nur die NX-Uniform hat die blaue Hose
            String trousers = uniform.startsWith("uniform_nx_") ? "trousers_nx" : "trousers_black";
            overlay(img, load("textures/entity/equipment/humanoid_leggings/" + trousers + ".png"), true);
            overlay(img, load("textures/entity/equipment/humanoid/boots_starfleet.png"), true);
            overlay(img, load("textures/entity/equipment/humanoid/" + uniform + ".png"), false);
            Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(() -> "simonspacemod crew " + key, img));
        } catch (Exception e) {
            com.mojang.logging.LogUtils.getLogger().warn("Sternenflotten-Haut {} nicht möglich: {}", key, e.toString());
            id = Identifier.fromNamespaceAndPath(SimonSpaceMod.MODID, "textures/entity/starfleet/skin_" + skin + ".png");
        }
        CACHE.put(key, id);
        return id;
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
     * {@code legs}: nur Beine (Hose, Stiefel), sonst Rumpf und Arme (Uniform).
     */
    private static void overlay(NativeImage skin, @Nullable NativeImage armor, boolean legs) {
        if (armor == null) return;
        try (armor) {
            int sx = armor.getWidth() / 64; // höher aufgelöste Texturen
            for (int y = 16; y < 32; y++)
                for (int x = 0; x < 56; x++) {
                    boolean leg = x < 16, body = x >= 16 && x < 40, arm = x >= 40;
                    if (legs ? !leg : leg) continue;
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

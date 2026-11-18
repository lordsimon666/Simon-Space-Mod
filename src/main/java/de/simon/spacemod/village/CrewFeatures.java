package de.simon.spacemod.village;

import java.util.List;
import java.util.Map;

/**
 * 3D-Merkmale am Kopf der Sternenflotten-Dorfbewohner: Vulkanier-Ohren, Sevens Implantat, Dutt und lange Haare,
 * La Forges Visier, Worfs Stirnwülste, Bärte. Jeder Quader liegt im Kopf-System des Spielermodells (Kopf = x -4..4,
 * y -8..0, z -4..4, vorn = -z, die linke Seite der Figur = +x) und nimmt seine Farbe aus einem Streifen der
 * Merkmal-Textur textures/entity/starfleet/features_&lt;haut&gt;.png (16 Pixel hoch je Streifen).
 */
public final class CrewFeatures {
    /** Farbstreifen: Haut, Haare, Metall, dunkle Haut (Wülste) bzw. dunkles Metall. */
    public static final int SKIN = 0, HAIR = 1, METAL = 2, DARK = 3;

    public record Box(int band, float x, float y, float z, float w, float h, float d) {}

    private static Box b(int band, double x, double y, double z, double w, double h, double d) {
        return new Box(band, (float) x, (float) y, (float) z, (float) w, (float) h, (float) d);
    }

    private static final List<Box> VULCAN_EARS = List.of(
        b(SKIN, 4, -5, -1, 1, 2, 1.5), b(SKIN, 4, -6.6, -0.5, 0.8, 1.6, 1),
        b(SKIN, -5, -5, -1, 1, 2, 1.5), b(SKIN, -4.8, -6.6, -0.5, 0.8, 1.6, 1));
    private static final Box BOWL = b(HAIR, -4.25, -8.25, -4.25, 8.5, 2.5, 8.5);
    private static final List<Box> SHORT = List.of(
        b(HAIR, -4.3, -8.6, -4.3, 8.6, 1, 8.6), b(HAIR, -4.3, -8, 3.6, 8.6, 4, 0.7));

    private static List<Box> concat(List<Box> a, Box... more) {
        java.util.ArrayList<Box> l = new java.util.ArrayList<>(a);
        l.addAll(List.of(more));
        return List.copyOf(l);
    }

    public static final Map<String, List<Box>> BY_SKIN = Map.ofEntries(
        Map.entry("spock", concat(VULCAN_EARS, BOWL)),
        Map.entry("tuvok", concat(VULCAN_EARS, BOWL)),
        Map.entry("tpol", concat(VULCAN_EARS, BOWL, b(HAIR, -4.25, -8.25, 3, 8.5, 6, 1.5))),
        // Implantat über dem linken Auge und an der Schläfe, Dutt hinten
        Map.entry("seven", List.of(b(METAL, 1.5, -5.6, -4.6, 2.6, 1.6, 0.8), b(METAL, 4, -5.3, -3.5, 0.6, 1, 3),
            b(DARK, 2.3, -5.2, -4.8, 1, 0.8, 0.3), b(HAIR, -1.5, -8.5, 3.8, 3, 3, 2))),
        Map.entry("janeway", List.of(b(HAIR, -1.5, -6.5, 4, 3, 3, 2), b(HAIR, -4.3, -8.6, -4.3, 8.6, 1, 8.6))),
        Map.entry("uhura", List.of(b(HAIR, -2.5, -10, -1.5, 5, 2.2, 5), b(HAIR, -4.3, -8.6, -4.3, 8.6, 1, 8.6))),
        Map.entry("troi", List.of(b(HAIR, -4.5, -8.5, 3, 9, 12, 1.5), b(HAIR, -4.7, -8, -2, 0.8, 9, 5), b(HAIR, 3.9, -8, -2, 0.8, 9, 5),
            b(HAIR, -4.4, -8.9, -4.4, 8.8, 1.2, 8.8))),
        Map.entry("crusher", List.of(b(HAIR, -4.5, -8.5, 3, 9, 7.5, 1.5), b(HAIR, -4.7, -8, -2, 0.8, 6.5, 5), b(HAIR, 3.9, -8, -2, 0.8, 6.5, 5),
            b(HAIR, -4.4, -8.8, -4.4, 8.8, 1, 8.8))),
        Map.entry("sato", List.of(b(HAIR, -4.4, -8.4, 3.5, 8.8, 10, 1), b(HAIR, -4.6, -8, -1, 0.6, 7, 4), b(HAIR, 4, -8, -1, 0.6, 7, 4))),
        // lange Haare, Stirnwülste, Spitzbart
        Map.entry("worf", List.of(b(HAIR, -4.2, -7, 4, 8.4, 10, 1), b(DARK, -3, -7.8, -4.6, 6, 1, 0.6), b(DARK, -2, -6.8, -4.5, 4, 0.8, 0.5),
            b(DARK, -0.5, -8.4, -4.5, 1, 1.4, 0.6), b(HAIR, -1, -1.2, -4.45, 2, 1.6, 0.5))),
        Map.entry("laforge", List.of(b(METAL, -4.6, -5, -4.75, 9.2, 1.3, 0.8), b(DARK, -3.6, -4.8, -4.85, 7.2, 0.8, 0.2),
            b(METAL, 4.1, -5.2, -4, 0.6, 1, 2), b(METAL, -4.7, -5.2, -4, 0.6, 1, 2))),
        Map.entry("riker", List.of(b(HAIR, -4.2, -1.4, -4.5, 8.4, 1.6, 0.6), b(HAIR, -4.2, -3.2, -4.5, 1.6, 2, 0.6),
            b(HAIR, 2.6, -3.2, -4.5, 1.6, 2, 0.6), b(HAIR, -2, -3.1, -4.5, 4, 0.6, 0.5), b(HAIR, -4.3, -8.6, -4.3, 8.6, 1, 8.6))),
        Map.entry("scott", concat(SHORT, b(HAIR, -2, -3.1, -4.45, 4, 0.7, 0.5))),
        Map.entry("sisko", List.of(b(HAIR, -1.2, -1.5, -4.45, 2.4, 1.7, 0.5))),
        // Haarkranz
        Map.entry("picard", List.of(b(HAIR, -4.3, -6, 1, 0.6, 2.5, 3.3), b(HAIR, 3.7, -6, 1, 0.6, 2.5, 3.3), b(HAIR, -4.3, -6, 3.7, 8.6, 2.5, 0.6))),
        Map.entry("data", List.of(b(HAIR, -4.2, -8.5, -4.2, 8.4, 0.7, 8.4))),
        Map.entry("chekov", concat(SHORT, b(HAIR, -4.3, -8.3, -4.6, 8.6, 1.6, 0.7))),
        Map.entry("pike", concat(SHORT, b(HAIR, -3.5, -9.3, -4.3, 7, 1, 3))),
        Map.entry("kirk", SHORT), Map.entry("mccoy", SHORT), Map.entry("sulu", SHORT), Map.entry("archer", SHORT),
        Map.entry("bashir", SHORT), Map.entry("obrien", SHORT));

    private CrewFeatures() {}

    public static List<Box> of(String skin) {
        return BY_SKIN.getOrDefault(skin, List.of());
    }
}

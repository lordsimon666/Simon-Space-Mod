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
        b(SKIN, 4, -5, -1, 1.1, 2, 1.5), b(SKIN, 4.2, -6.9, -0.4, 0.9, 1.9, 1),
        b(SKIN, -5.1, -5, -1, 1.1, 2, 1.5), b(SKIN, -5.1, -6.9, -0.4, 0.9, 1.9, 1));

    /** Dicke der Haarschale über dem Kopf. */
    private static final double T = 0.45, O = 4 + T; // Außenmaß

    /**
     * Geschlossene Haarschale: Kappe oben, Hinterkopf bis {@code back} Pixel unter dem Scheitel, Seiten ab {@code sideZ}
     * (vorn = -4) bis {@code side} Pixel lang, Pony vorn {@code front} Pixel lang. So entstehen keine Lücken und Kanten.
     */
    private static List<Box> hair(double back, double side, double sideZ, double front, boolean cap) {
        java.util.ArrayList<Box> l = new java.util.ArrayList<>();
        if (cap) l.add(b(HAIR, -O, -8 - T, -O, 2 * O, T, 2 * O));
        if (back > 0) l.add(b(HAIR, -O, -8, 4, 2 * O, back, T));
        if (side > 0) {
            l.add(b(HAIR, -O, -8, sideZ, T, side, 4 - sideZ));
            l.add(b(HAIR, 4, -8, sideZ, T, side, 4 - sideZ));
        }
        if (back > side + 0.5) { // hinten an den Seiten so lang wie der Hinterkopf, damit keine kahle Ecke bleibt
            double z0 = Math.max(sideZ, 1.5);
            l.add(b(HAIR, -O, -8 + side, z0, T, back - side, 4 - z0));
            l.add(b(HAIR, 4, -8 + side, z0, T, back - side, 4 - z0));
        }
        if (front > 0) l.add(b(HAIR, -O, -8, -O, 2 * O, front, T));
        return l;
    }

    private static List<Box> join(List<Box> a, List<Box> c, Box... more) {
        java.util.ArrayList<Box> l = new java.util.ArrayList<>(a);
        l.addAll(c);
        l.addAll(List.of(more));
        return List.copyOf(l);
    }

    private static List<Box> join(List<Box> a, Box... more) {
        return join(a, List.of(), more);
    }

    private static final List<Box> SHORT = hair(6.5, 2.5, -1, 1, true);
    /** Topfschnitt der Vulkanier: gerader Pony, die Ohrspitzen schauen unter den Seiten heraus. */
    private static final List<Box> BOWL = hair(6.5, 3.4, -4.45, 2.5, true);

    public static final Map<String, List<Box>> BY_SKIN = Map.ofEntries(
        Map.entry("spock", join(BOWL, VULCAN_EARS)),
        Map.entry("tuvok", join(BOWL, VULCAN_EARS)),
        Map.entry("tpol", join(hair(7.5, 3.4, -4.45, 2.5, true), VULCAN_EARS)),
        // Implantat über dem linken Auge und an der Schläfe, Haare hochgesteckt mit Dutt
        Map.entry("seven", join(hair(6.5, 2.5, -1, 1, true), b(METAL, 1.5, -5.6, -4.6, 2.6, 1.6, 0.8), b(METAL, 4, -5.3, -3.5, 0.6, 1, 3),
            b(DARK, 2.3, -5.2, -4.8, 1, 0.8, 0.3), b(HAIR, -1.5, -8.2, 4.4, 3, 3, 1.8))),
        Map.entry("janeway", join(hair(6.5, 2.5, -1, 1, true), b(HAIR, -1.5, -6.8, 4.4, 3, 3, 1.8))),
        Map.entry("uhura", join(hair(6.5, 2, -1, 1, true), b(HAIR, -2.5, -10.2, -1.5, 5, 1.8, 5))),
        // lange Haare bis über die Schultern, außen etwas voller
        Map.entry("troi", join(hair(11.5, 9, -2.5, 1, true), b(HAIR, -4.7, -7.5, 4.4, 9.4, 10.5, 0.5))),
        Map.entry("crusher", hair(7, 6.5, -2.5, 1.2, true)),
        Map.entry("sato", hair(10, 7, -1.5, 1, true)),
        // Worf: lange Mähne, Stirnwülste mit Kamm über den Kopf, voller Kinn- und Schnurrbart wie in TNG
        Map.entry("worf", join(hair(9.5, 6, 0, 0, true), List.of(
            b(HAIR, -4.7, -6.5, 4.4, 9.4, 8.5, 0.6),
            b(DARK, -1, -9.0, -4.55, 2, 2.4, 1), b(DARK, -0.8, -9.1, -3.6, 1.6, 0.7, 5.5),
            b(DARK, -3.1, -7.9, -4.6, 2.2, 0.8, 0.6), b(DARK, 0.9, -7.9, -4.6, 2.2, 0.8, 0.6), b(DARK, -2.5, -6.9, -4.55, 5, 0.6, 0.5),
            b(HAIR, -2.2, -3.2, -4.55, 4.4, 0.6, 0.55), b(HAIR, -2.7, -3.2, -4.55, 0.6, 1.7, 0.55), b(HAIR, 2.1, -3.2, -4.55, 0.6, 1.7, 0.55),
            b(HAIR, -2.7, -1.6, -4.55, 5.4, 1.6, 0.55), b(HAIR, -1.5, -0.1, -4.5, 3, 1.3, 1.4),
            b(HAIR, -4.45, -2.6, -4.0, 0.5, 2.4, 3.2), b(HAIR, 3.95, -2.6, -4.0, 0.5, 2.4, 3.2),
            b(HAIR, -4.45, -1.2, -4.5, 1.8, 1.0, 0.5), b(HAIR, 2.65, -1.2, -4.5, 1.8, 1.0, 0.5)))),
        Map.entry("laforge", List.of(b(METAL, -4.6, -5, -4.75, 9.2, 1.3, 0.8), b(DARK, -3.6, -4.8, -4.85, 7.2, 0.8, 0.2),
            b(METAL, 4.1, -5.2, -4, 0.6, 1, 2), b(METAL, -4.7, -5.2, -4, 0.6, 1, 2))),
        Map.entry("riker", join(SHORT, b(HAIR, -4.2, -1.4, -4.5, 8.4, 1.6, 0.6), b(HAIR, -4.2, -3.2, -4.5, 1.6, 2, 0.6),
            b(HAIR, 2.6, -3.2, -4.5, 1.6, 2, 0.6), b(HAIR, -2, -3.1, -4.5, 4, 0.6, 0.5))),
        Map.entry("scott", join(SHORT, b(HAIR, -2, -3.1, -4.45, 4, 0.7, 0.5))),
        Map.entry("sisko", List.of(b(HAIR, -1.2, -1.5, -4.45, 2.4, 1.7, 0.5))),
        // Haarkranz: ein durchgehender Ring an Seiten und Hinterkopf
        Map.entry("picard", List.of(b(HAIR, -O, -6, 4, 2 * O, 4.6, T), b(HAIR, -O, -6, 0.5, T, 3, 3.5), b(HAIR, 4, -6, 0.5, T, 3, 3.5),
            b(HAIR, -O, -3, 2, T, 1.6, 2), b(HAIR, 4, -3, 2, T, 1.6, 2))),
        Map.entry("data", hair(6.5, 2, -1, 0.6, true)),
        Map.entry("chekov", join(hair(6.5, 2.5, -1, 2, true), b(HAIR, -3, -8.6, -4.9, 6, 2.6, 0.5))),
        Map.entry("pike", join(SHORT, b(HAIR, -3.5, -9.3, -4.45, 7, 0.9, 3.5))),
        Map.entry("kirk", SHORT), Map.entry("mccoy", SHORT), Map.entry("sulu", SHORT), Map.entry("archer", SHORT),
        Map.entry("bashir", SHORT), Map.entry("obrien", SHORT));

    private CrewFeatures() {}

    public static List<Box> of(String skin) {
        return BY_SKIN.getOrDefault(skin, List.of());
    }
}

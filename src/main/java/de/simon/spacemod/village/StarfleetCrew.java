package de.simon.spacemod.village;

import java.util.List;
import java.util.UUID;
import net.minecraft.util.RandomSource;

/**
 * Namen und Aussehen der Sternenflotten-Dorfbewohner.
 *
 * <ul>
 * <li>Mit 25 % heißen sie wie eine bekannte Figur aus Star Trek. Dann tragen sie deren Haut
 * (textures/entity/starfleet/skin_&lt;id&gt;.png) und eine der Uniformen, die die Figur in Serie oder Filmen trug –
 * bei mehreren zufällig, aber fest je Dorfbewohner (aus seiner UUID).</li>
 * <li>Mit 75 % bekommen sie einen erfundenen Namen, eine Haut ohne markante Merkmale und eine zufällige Uniform.</li>
 * </ul>
 * Kleidung: "uniform_&lt;epoche&gt;_&lt;abteilung&gt;" sind die Uniform-Gegenstände, "outfit_&lt;name&gt;" eigene
 * Kleidung der Dorfbewohner (Sevens Anzug, Kirks grüne Tunika).
 */
public final class StarfleetCrew {
    /** Bekannte Figur: Name, Haut, Uniformen und die Schlüsselkarte, die sie verkauft ("captain", "command", "security"). */
    public record Member(String name, String skin, String card, List<String> outfits) {}

    /** Schlüsselkarten nach Rang: Captains die Captain-Karte, Erste Offiziere und Führungsoffiziere Kommando, sonst Sicherheit. */
    private static final java.util.Map<String, String> CARDS = java.util.Map.ofEntries(
        java.util.Map.entry("picard", "captain"), java.util.Map.entry("kirk", "captain"), java.util.Map.entry("janeway", "captain"),
        java.util.Map.entry("sisko", "captain"), java.util.Map.entry("archer", "captain"), java.util.Map.entry("pike", "captain"),
        java.util.Map.entry("riker", "command"), java.util.Map.entry("spock", "command"), java.util.Map.entry("data", "command"),
        java.util.Map.entry("tpol", "command"), java.util.Map.entry("burnham", "command"), java.util.Map.entry("crusher", "command"),
        java.util.Map.entry("troi", "command"), java.util.Map.entry("laforge", "command"), java.util.Map.entry("mccoy", "command"),
        java.util.Map.entry("scott", "command"), java.util.Map.entry("sulu", "command"));

    private static Member m(String name, String skin, String... outfits) {
        return new Member(name, skin, CARDS.getOrDefault(skin, "security"), List.of(outfits));
    }


    public static final List<Member> CREW = List.of(
        m("Jean-Luc Picard", "picard", "uniform_tng_command", "uniform_ds9_command", "uniform_fc_command"),
        m("William Riker", "riker", "uniform_tng_command", "uniform_ds9_command", "uniform_fc_command"),
        m("Data", "data", "uniform_tng_operations", "uniform_ds9_operations", "uniform_fc_operations"),
        m("Worf", "worf", "uniform_tng_operations", "uniform_ds9_operations", "uniform_fc_command"),
        m("Geordi La Forge", "laforge", "uniform_tng_operations", "uniform_ds9_operations", "uniform_fc_operations"),
        m("Beverly Crusher", "crusher", "uniform_tng_science", "uniform_ds9_science", "uniform_fc_science"),
        m("Deanna Troi", "troi", "uniform_tng_science", "uniform_ds9_science", "uniform_fc_science"),
        m("James T. Kirk", "kirk", "uniform_tos_command", "outfit_kirk_green", "uniform_movie_command"),
        m("Spock", "spock", "uniform_tos_science", "uniform_movie_science"),
        m("Leonard McCoy", "mccoy", "uniform_tos_science", "uniform_movie_science"),
        m("Montgomery Scott", "scott", "uniform_tos_operations", "uniform_movie_operations"),
        m("Nyota Uhura", "uhura", "uniform_tos_operations", "uniform_movie_operations"),
        m("Hikaru Sulu", "sulu", "uniform_tos_science", "uniform_tos_command", "uniform_movie_command"),
        m("Pavel Chekov", "chekov", "uniform_tos_command", "uniform_movie_operations"),
        m("Kathryn Janeway", "janeway", "uniform_ds9_command"),
        m("Benjamin Sisko", "sisko", "uniform_ds9_command", "uniform_fc_command"),
        // Seven trägt meist keine offizielle Uniform
        m("Seven of Nine", "seven", "outfit_seven", "outfit_seven", "outfit_seven", "uniform_ds9_science"),
        m("Tuvok", "tuvok", "uniform_ds9_operations"),
        m("Jonathan Archer", "archer", "uniform_nx_command"),
        m("T'Pol", "tpol", "uniform_nx_science"),
        m("Hoshi Sato", "sato", "uniform_nx_operations"),
        m("Michael Burnham", "burnham", "uniform_nx_science", "uniform_tos_science"),
        m("Christopher Pike", "pike", "uniform_tos_command"),
        m("Miles O'Brien", "obrien", "uniform_tng_operations", "uniform_ds9_operations", "uniform_fc_operations"),
        m("Julian Bashir", "bashir", "uniform_ds9_science", "uniform_fc_science"));

    /** Häute ohne markante Merkmale (kein Visier, keine Stirnwülste, keine Vulkanier-Brauen, kein Bart, keine Glatze). */
    public static final List<String> PLAIN_SKINS = List.of(
        "archer", "bashir", "burnham", "chekov", "crusher", "janeway", "kirk", "mccoy", "obrien", "sato", "sulu", "troi", "uhura");

    /** Bausteine für erfundene Namen (keine bekannte Figur). */
    public static final List<String> FIRST_NAMES = List.of(
        "Alex", "Mara", "Tomas", "Yuki", "Kofi", "Lena", "Ravi", "Ingrid", "Mateo", "Aiko", "Nils", "Priya", "Jonas", "Zara",
        "Elena", "Omar", "Freya", "Kai", "Talia", "Viktor", "Selin", "Diego", "Amara", "Hugo", "Noemi", "Bastian", "Leila",
        "Emil", "Sora", "Ida", "Malik", "Greta", "Tariq", "Juna", "Felix", "Anouk");
    public static final List<String> LAST_NAMES = List.of(
        "Vance", "Okafor", "Lindqvist", "Moreau", "Tanaka", "Kowalski", "Reyes", "Haddad", "Brandt", "Nakamura", "Petrov",
        "Mensah", "Larsen", "Castillo", "Weber", "Singh", "Dubois", "Novak", "Ferreira", "Holm", "Abara", "Kaplan", "Rossi",
        "Varga", "Quinn", "Achebe", "Falk", "Ibarra", "Strand", "Oyelaran", "Bergmann", "Celik", "Marlow", "Thorne");

    /** Anteil der Dorfbewohner mit dem Namen einer bekannten Figur. */
    public static final double CANON_SHARE = 0.25;

    /** Uniformen in der Reihenfolge Epoche × Abteilung, wie die Uniform-Gegenstände. */
    public static final String[] ERAS = {"nx", "tos", "movie", "tng", "ds9", "fc"};
    public static final String[] DIVISIONS = {"command", "science", "operations"};

    private StarfleetCrew() {}

    /** Neuer Name: mit 25 % eine bekannte Figur, sonst ein erfundener Name. */
    public static String randomName(RandomSource rnd) {
        if (rnd.nextDouble() < CANON_SHARE) return CREW.get(rnd.nextInt(CREW.size())).name();
        return FIRST_NAMES.get(rnd.nextInt(FIRST_NAMES.size())) + " " + LAST_NAMES.get(rnd.nextInt(LAST_NAMES.size()));
    }

    /** Bekannte Figur zu einem Namen oder null. */
    public static Member canon(String name) {
        for (Member m : CREW) if (m.name().equals(name)) return m;
        return null;
    }

    /** Haut zu Name und UUID: die der Figur, sonst eine unauffällige, fest aus der UUID. */
    public static String skin(String name, UUID uuid) {
        Member m = canon(name);
        return m != null ? m.skin() : PLAIN_SKINS.get(Math.floorMod(mix(uuid, 17), PLAIN_SKINS.size()));
    }

    /** Kleidung zu Name und UUID: eine der Uniformen der Figur, sonst eine zufällige Uniform – fest aus der UUID. */
    public static String outfit(String name, UUID uuid) {
        Member m = canon(name);
        if (m != null) return m.outfits().get(Math.floorMod(mix(uuid, 31), m.outfits().size()));
        return uniform(uuid);
    }

    /** Risse in der Uniform: bei der Hälfte der Dorfbewohner, dann eines von vier Mustern; sonst -1 (heil). */
    public static int tear(UUID uuid) {
        int h = mix(uuid, 53);
        return (h & 1) == 0 ? -1 : (h >>> 1) & 3;
    }

    /** Stufe, ab der eine Figur ihre Schlüsselkarte anbietet: Sicherheit 1, Kommando 2, Captain 3. */
    public static int cardLevel(String card) {
        return switch (card) {
            case "captain" -> 3;
            case "command" -> 2;
            default -> 1;
        };
    }

    /** Preis der Schlüsselkarte in Smaragden. */
    public static int cardPrice(String card) {
        return switch (card) {
            case "captain" -> 40;
            case "command" -> 24;
            default -> 12;
        };
    }

    /** Zufällige Uniform (eine der 18), fest aus der UUID. */
    public static String uniform(UUID uuid) {
        int i = Math.floorMod(mix(uuid, 31), ERAS.length * DIVISIONS.length);
        return "uniform_" + ERAS[i / DIVISIONS.length] + "_" + DIVISIONS[i % DIVISIONS.length];
    }

    private static int mix(UUID uuid, int salt) {
        long h = uuid.getLeastSignificantBits() ^ (uuid.getMostSignificantBits() * salt);
        h ^= h >>> 29;
        h *= 0xBF58476D1CE4E5B9L;
        h ^= h >>> 32;
        return (int) h;
    }
}

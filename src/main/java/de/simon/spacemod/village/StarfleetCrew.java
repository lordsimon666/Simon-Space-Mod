package de.simon.spacemod.village;

import java.util.List;
import java.util.UUID;
import net.minecraft.util.RandomSource;

/**
 * Namen der Sternenflotten-Dorfbewohner (bekannte Figuren aus Star Trek) und ihr Aussehen: Zu jedem Namen gibt es
 * eine Haut (textures/entity/starfleet/skin_&lt;id&gt;.png) mit passender Frisur, Hautfarbe und Merkmalen. Die Uniform
 * wählt der Zufall – fest je Dorfbewohner (aus seiner UUID).
 */
public final class StarfleetCrew {
    public record Member(String name, String skin) {}

    public static final List<Member> CREW = List.of(
        new Member("Jean-Luc Picard", "picard"), new Member("William Riker", "riker"), new Member("Data", "data"),
        new Member("Worf", "worf"), new Member("Geordi La Forge", "laforge"), new Member("Beverly Crusher", "crusher"),
        new Member("Deanna Troi", "troi"), new Member("James T. Kirk", "kirk"), new Member("Spock", "spock"),
        new Member("Leonard McCoy", "mccoy"), new Member("Montgomery Scott", "scott"), new Member("Nyota Uhura", "uhura"),
        new Member("Hikaru Sulu", "sulu"), new Member("Pavel Chekov", "chekov"), new Member("Kathryn Janeway", "janeway"),
        new Member("Benjamin Sisko", "sisko"), new Member("Seven of Nine", "seven"), new Member("Tuvok", "tuvok"),
        new Member("Jonathan Archer", "archer"), new Member("T'Pol", "tpol"), new Member("Hoshi Sato", "sato"),
        new Member("Michael Burnham", "burnham"), new Member("Christopher Pike", "pike"), new Member("Miles O'Brien", "obrien"),
        new Member("Julian Bashir", "bashir"));

    /** Uniformen (Brustteil) in der Reihenfolge Epoche × Abteilung, wie die Uniform-Gegenstände. */
    public static final String[] ERAS = {"nx", "tos", "movie", "tng", "fc"};
    public static final String[] DIVISIONS = {"command", "science", "operations"};

    private StarfleetCrew() {}

    public static Member random(RandomSource rnd) {
        return CREW.get(rnd.nextInt(CREW.size()));
    }

    /** Figur zu einem Namen; unbekannte Namen (z. B. per Namensschild umbenannt) bekommen eine feste Haut aus der UUID. */
    public static Member byName(String name, UUID uuid) {
        for (Member m : CREW) if (m.name().equals(name)) return m;
        return CREW.get(Math.floorMod(uuid.hashCode(), CREW.size()));
    }

    /** Uniform eines Dorfbewohners: "uniform_&lt;epoche&gt;_&lt;abteilung&gt;", fest aus der UUID. */
    public static String uniform(UUID uuid) {
        long h = uuid.getLeastSignificantBits() ^ (uuid.getMostSignificantBits() * 31);
        int i = (int) Math.floorMod(h, (long) ERAS.length * DIVISIONS.length);
        return "uniform_" + ERAS[i / DIVISIONS.length] + "_" + DIVISIONS[i % DIVISIONS.length];
    }
}

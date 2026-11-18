package de.simon.spacemod.village;

/** Wie selten das Shuttle-Haus in Dörfern ist: etwa jedes fünfzigste Dorf (bei rund neun Häusern je Dorf). */
public final class VillageShare {
    public static final double VILLAGE_SHARE = 0.02, HOUSES_PER_VILLAGE = 9;

    private VillageShare() {}

    /** Chance je Ziehung, damit ein Haus mit Gewicht 1 neben dem Gesamtgewicht {@code total} so oft vorkommt. */
    public static float chance(int total) {
        double perHouse = 1 - Math.pow(1 - VILLAGE_SHARE, 1 / HOUSES_PER_VILLAGE);
        return (float) Math.min(1, perHouse * (total + 1));
    }
}

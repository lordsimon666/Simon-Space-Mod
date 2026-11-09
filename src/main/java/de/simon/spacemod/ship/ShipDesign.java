package de.simon.spacemod.ship;

/** Welche Enterprise gebaut wird. */
public enum ShipDesign {
    /** USS Enterprise NCC-1701, Originalserie (289 m). */
    NCC_1701("ncc-1701", "NCC-1701", Era.TOS),
    /** USS Enterprise NCC-1701-A, Refit aus den Kinofilmen (305 m). */
    NCC_1701_A("ncc-1701-a", "NCC-1701-A", Era.REFIT),
    /** USS Enterprise NCC-1701-B, Excelsior-Klasse (Variante B, 467 m). */
    NCC_1701_B("ncc-1701-b", "NCC-1701-B", Era.EXCELSIOR),
    /** USS Enterprise NCC-1701-C, Ambassador-Klasse (526 m). */
    NCC_1701_C("ncc-1701-c", "NCC-1701-C", Era.TNG),
    /** USS Enterprise NCC-1701-D, Galaxy-Klasse (642 m). */
    NCC_1701_D("ncc-1701-d", "NCC-1701-D", Era.TNG),
    /** USS Enterprise NCC-1701-E, Sovereign-Klasse (685 m). */
    NCC_1701_E("ncc-1701-e", "NCC-1701-E", Era.TNG);

    /** Bau-Epoche: bestimmt Farben, Brücke und Räume. */
    public enum Era { TOS, REFIT, EXCELSIOR, TNG }

    /** Name im Befehl. */
    public final String id;
    /** Kennung auf der Hülle. */
    public final String registry;
    public final Era era;
    /** Refit-Design (Kinofilme)? */
    public final boolean refit;
    /** Excelsior-Klasse? */
    public final boolean excelsior;
    /** Schiff aus der Zeit von "The Next Generation" (C, D, E)? */
    public final boolean tng;

    ShipDesign(String id, String registry, Era era) {
        this.id = id;
        this.registry = registry;
        this.era = era;
        this.refit = era == Era.REFIT;
        this.excelsior = era == Era.EXCELSIOR;
        this.tng = era == Era.TNG;
    }
}

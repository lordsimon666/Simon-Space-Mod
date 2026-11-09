package de.simon.spacemod.ship;

/** Welche Enterprise gebaut wird. */
public enum ShipDesign {
    /** USS Enterprise NCC-1701, Originalserie (289 m). */
    NCC_1701("ncc-1701", "NCC-1701", false),
    /** USS Enterprise NCC-1701-A, Refit aus den Kinofilmen (305 m). */
    NCC_1701_A("ncc-1701-a", "NCC-1701-A", true);

    /** Name im Befehl. */
    public final String id;
    /** Kennung auf der Hülle. */
    public final String registry;
    /** Refit-Design (Kinofilme)? */
    public final boolean refit;

    ShipDesign(String id, String registry, boolean refit) {
        this.id = id;
        this.registry = registry;
        this.refit = refit;
    }
}

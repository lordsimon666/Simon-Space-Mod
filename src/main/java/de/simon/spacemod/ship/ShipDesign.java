package de.simon.spacemod.ship;

/** Welche Enterprise gebaut wird. */
public enum ShipDesign {
    /** USS Enterprise NCC-1701, Originalserie (289 m). */
    NCC_1701("ncc-1701", "NCC-1701", false),
    /** USS Enterprise NCC-1701-A, Refit aus den Kinofilmen (305 m). */
    NCC_1701_A("ncc-1701-a", "NCC-1701-A", true),
    /** USS Enterprise NCC-1701-B, Excelsior-Klasse (Variante B, 467 m). */
    NCC_1701_B("ncc-1701-b", "NCC-1701-B", false, true);

    /** Name im Befehl. */
    public final String id;
    /** Kennung auf der Hülle. */
    public final String registry;
    /** Refit-Design (Kinofilme)? */
    public final boolean refit;
    /** Excelsior-Klasse? */
    public final boolean excelsior;

    ShipDesign(String id, String registry, boolean refit) {
        this(id, registry, refit, false);
    }

    ShipDesign(String id, String registry, boolean refit, boolean excelsior) {
        this.id = id;
        this.registry = registry;
        this.refit = refit;
        this.excelsior = excelsior;
    }
}

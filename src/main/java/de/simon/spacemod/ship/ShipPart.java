package de.simon.spacemod.ship;

/**
 * Abstrakte Bauteile des Bauplans. Unabhängig von Minecraft-Registern, damit der Bauplan
 * auch ohne laufendes Spiel (z.B. in Tests) berechnet werden kann.
 * Die Zuordnung zu echten Blöcken übernimmt {@link ShipPalette}.
 */
public enum ShipPart {
    AIR(0x000000),
    HULL_PLATING(0xCECEC8),
    HULL_PANEL(0xBEBEBA),
    HULL_DARK(0x787A7E),
    HULL_WINDOW(0xFFECAA),
    HULL_MARKING(0x1C1C1E),
    DEFLECTOR_DISH(0xE89848),
    BUSSARD_COLLECTOR(0xFF3020),
    NACELLE_GRILLE(0x60A0FF),
    DECK_FLOOR(0x4E5056),
    BRIDGE_DECK(0x961E22),
    CORRIDOR_WALL(0xDED4BE),
    CEILING_LIGHT(0xFAFAF0),
    CEILING_PANEL(0xE6E6E2),
    BRIDGE_CONSOLE(0x202028),
    ENGINEERING_CONSOLE(0x22222A),
    LCARS_PANEL(0xFF9900),
    VIEWSCREEN(0x020208),
    CAPTAINS_CHAIR(0xC8A030),
    WARP_CORE(0x50B0FF),
    PLASMA_CONDUIT(0x3C8CFF),
    TRANSPORTER_PAD(0xD0D0DA),
    TURBOLIFT(0xC83428),
    IRON_BARS(0x6E6E6E),
    LADDER(0x9C7A48);

    /** Farbe für Vorschaubilder. */
    public final int color;

    ShipPart(int color) {
        this.color = color;
    }

    private static final ShipPart[] VALUES = values();

    public static ShipPart byId(int id) {
        return VALUES[id];
    }
}

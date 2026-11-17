package de.simon.spacemod;

import java.util.List;

/**
 * Reihenfolge im Kreativ-Tab und in der Schiffsteil-Fertigung: zuerst die Grundausstattung, dann
 * Rohstoffe, Energie, Schilde, Waffen, Lagerung, Brücke, Lifte/Türen, Einrichtung, Licht, Innenausbau,
 * Außenhülle und zuletzt Kleidung. Gestufte Teile stehen in der Reihenfolge ihrer Stufen.
 */
public final class TabOrder {
    private TabOrder() {}

    /** Stufenketten in ihrer Reihenfolge (alles andere bleibt in der Registrierreihenfolge). */
    private static final List<String> TIERED = List.of(
        "guide_book", "ship_part_assembly", "communicator", "space_suit", "keycard_security", "keycard_command", "keycard_captain",
        "phaser_nx", "phaser_tos", "phaser_movie", "phaser_tng", "phaser_fc", "rifle_nx", "rifle_tos", "rifle_tng", "rifle_fc",
        "warp_core_nx", "warp_core", "warp_core_tng", "warp_core_e", "warp_core_f",
        "torpedo_launcher", "torpedo_launcher_spread", "torpedo_launcher_rapid",
        "quantum_launcher", "quantum_launcher_rapid", "quantum_launcher_dual",
        "crate_wood", "crate_copper", "crate_iron", "crate_gold", "crate_diamond", "crate_netherite");

    /** Gruppe eines Registernamens (kleiner = weiter vorne). */
    public static int category(String p) {
        if (p.equals("guide_book") || p.equals("ship_part_assembly") || p.equals("communicator") || p.equals("space_suit") || p.startsWith("keycard_")
            || p.matches("(phaser|rifle)_(nx|tos|movie|tng|fc)")) return 0;                                   // Grundausstattung
        if (p.contains("_ore") || p.contains("uranium") || p.startsWith("dilithium") || p.startsWith("antimatter")
            || p.startsWith("deuterium")) return 1;                                                            // Rohstoffe
        if (p.startsWith("warp_core") || p.startsWith("reactor_") || p.equals("battery") || p.equals("charging_station")
            || p.equals("plasma_conduit") || p.equals("power_console") || p.startsWith("mining_laser") || p.equals("laser_focus"))
            return 2;                                                                                          // Energie & Maschinen
        if (p.contains("shield")) return 3;                                                                    // Schilde
        if (p.contains("torpedo") || p.contains("quantum") || p.startsWith("phase") || p.equals("weapons_console")
            || p.equals("rifle_rack") || p.equals("phaser_holder")) return 4;                                  // Waffen
        if (p.startsWith("crate") || p.startsWith("stack_upgrade")) return 5;                                  // Lagerung
        if (p.startsWith("transporter")) return 7;                                                             // Transporter (auch Konsole)
        if (p.equals("ship_helm") || p.contains("helm") || p.equals("inertial_damper") || p.equals("camera") || p.equals("display") || p.contains("captains_chair") || p.startsWith("station_chair")
            || p.contains("console") || p.equals("viewscreen") || p.equals("msd_display") || p.equals("lcars_panel")
            || p.equals("bridge_station") || p.equals("bridge_monitor") || p.equals("excelsior_display") || p.equals("star_map")
            || p.equals("situation_table") || p.equals("alarm_light")) return 6;                               // Steuerung & Brücke
        if (p.startsWith("turbolift") || p.startsWith("transporter") || p.startsWith("door_") || p.startsWith("airlock")
            || p.equals("force_field")) return 7;                                                              // Lifte, Transporter, Türen
        if (p.contains("table") || p.contains("bed") || p.contains("desk") || p.contains("cabinet") || p.contains("container")
            || p.contains("counter") || p.contains("chair") || p.equals("holodeck_grid") || p.equals("decon_light")) return 8; // Einrichtung
        if (p.contains("light") || p.contains("lamp")) return 9;                                               // Licht
        if (p.startsWith("bridge_") || p.equals("step_slab")) return 10;                                       // Brückenausbau
        if (p.startsWith("hull") || p.contains("deflector") || p.contains("bussard") || p.contains("nacelle")
            || p.contains("impulse")) return 12;                                                               // Außenhülle & Antrieb
        if (p.startsWith("uniform") || p.startsWith("trousers") || p.startsWith("boots")) return 13;           // Kleidung
        return 11;                                                                                             // Innenausbau
    }

    /** Sortierschlüssel; {@code registration} = Position in der Registrierreihenfolge. */
    public static int key(String path, int registration) {
        int t = TIERED.indexOf(path);
        return category(path) * 100_000 + (t >= 0 ? t : 1000 + registration);
    }
}

package de.simon.spacemod.client.guide;

import de.simon.spacemod.SimonSpaceMod;
import de.simon.spacemod.guide.GuideStructure;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

/**
 * Was im Handbuch steht: Kategorien, darin Kapitel, darin Seiten. Die Texte stehen in den Sprachdateien unter
 * {@code guide.simonspacemod.<kapitel>.<n>}; ein langer Text läuft einfach über die nächsten Seiten weiter.
 * Items werden mit ihrer ID genannt ({@code minecraft:...} oder ohne Namensraum die der Mod), Rezepte mit
 * dem Namen ihrer Rezeptdatei bzw. {@code assembly:<block>} für die Schiffsteil-Fertigung.
 */
public final class GuideBook {
    /** Eine Kategorie auf der Startseite. */
    public record Category(String id, String icon, List<Entry> entries) {
        public Component title() {
            return Component.translatable("guide.simonspacemod.category." + id);
        }

        public Component description() {
            return Component.translatable("guide.simonspacemod.category." + id + ".desc");
        }
    }

    /** Ein Kapitel einer Kategorie. */
    public record Entry(String id, String icon, List<Page> pages) {
        public Component title() {
            return Component.translatable("guide.simonspacemod.entry." + id);
        }
    }

    /** Was eine Seite zeigt; jede Seite der Liste beginnt eine neue Seite im Buch. */
    public sealed interface Page permits Text, Spotlight, Crafting, Structure {
        /** Der Text unter dem, was die Seite zeigt, oder null. */
        @Nullable
        String text();
    }

    /** Nur Text. */
    public record Text(String text) implements Page {}

    /** Ein Item groß mit Namen, darunter Text. */
    public record Spotlight(String item, @Nullable String text) implements Page {}

    /** Ein Rezept (Rezeptdatei oder {@code assembly:<block>}), darunter Text. */
    public record Crafting(String recipe, @Nullable String text) implements Page {}

    /** Ein Multiblock in 3D, den man drehen und in der Welt zeigen kann, darunter Text. */
    public record Structure(GuideStructure structure, @Nullable String text) implements Page {}

    public static final List<Category> CATEGORIES = List.of(
        new Category("basics", "guide_book", List.of(
            entry("welcome", "guide_book", text("welcome.1"), spotlight("guide_book", "welcome.2"), crafting("guide_book")),
            entry("first_steps", "battery", text("first_steps.1"), text("first_steps.2"), spotlight("battery", "first_steps.3")),
            entry("assembly", "ship_part_assembly", text("assembly.1"), crafting("ship_part_assembly"), text("assembly.2")),
            entry("tiers", "warp_core_f", text("tiers.1")),
            entry("communicator", "communicator", text("communicator.1"), crafting("communicator")))),
        new Category("resources", "uranium_ingot", List.of(
            entry("uranium", "raw_uranium", text("uranium.1"), spotlight("uranium_ore", null),
                crafting("uranium_ingot_from_smelting_raw_uranium")),
            entry("space_resources", "dilithium_crystal", text("space_resources.1"), spotlight("dilithium_crystal", "space_resources.2"),
                spotlight("deuterium", "space_resources.3")),
            entry("antimatter", "antimatter_pod", text("antimatter.1"), crafting("antimatter_pod")))),
        new Category("power", "warp_core_tng", List.of(
            entry("reactor", "reactor_controller", text("reactor.1"), structure(GuideStructure.REACTOR, "reactor.2"),
                text("reactor.3"), crafting("assembly:reactor_controller"), crafting("assembly:reactor_casing"),
                crafting("assembly:reactor_glass")),
            entry("warp_cores", "warp_core_e", text("warp_cores.1"), crafting("assembly:warp_core_nx"), text("warp_cores.2"),
                crafting("assembly:warp_core"), crafting("assembly:warp_core_tng"), crafting("assembly:warp_core_e"),
                crafting("assembly:warp_core_f")),
            entry("grid", "plasma_conduit", text("grid.1"), text("grid.2")),
            entry("power_console", "power_console", text("power_console.1"), text("power_console.2"), crafting("assembly:power_console")),
            entry("battery", "battery", text("battery.1"), crafting("assembly:battery"), crafting("assembly:charging_station")))),
        new Category("ships", "ship_helm", List.of(
            entry("own_ship", "ship_helm", text("own_ship.1"), text("own_ship.2"), crafting("assembly:ship_helm")),
            entry("flying", "impulse_engine", text("flying.1"), text("flying.2"), crafting("assembly:impulse_engine"), text("flying.3"),
                crafting("assembly:inertial_damper")),
            entry("aboard", "station_chair", text("aboard.1"), text("aboard.2")),
            entry("airlock", "airlock", text("airlock.1"), crafting("assembly:airlock"), crafting("assembly:airlock_small")),
            entry("space", "space_suit", text("space.1"), text("space.2")),
            entry("space_suit", "space_suit", text("space_suit.1"), crafting("space_suit")),
            entry("warp", "nacelle_grille", text("warp.1"), text("warp.2"), crafting("assembly:bussard_collector"),
                crafting("assembly:nacelle_grille"), text("warp.3")))),
        new Category("weapons", "quantum_launcher_dual", List.of(
            entry("phasers", "phaser_bank", text("phasers.1"), crafting("assembly:phaser_bank"), text("phasers.2")),
            entry("torpedoes", "torpedo_launcher", text("torpedoes.1"), crafting("assembly:torpedo_launcher"),
                crafting("assembly:torpedo_launcher_spread"), crafting("assembly:torpedo_launcher_rapid"), text("torpedoes.2"),
                crafting("assembly:quantum_launcher"), crafting("assembly:quantum_launcher_rapid"),
                crafting("assembly:quantum_launcher_dual")),
            entry("weapons_station", "weapons_console", text("weapons_station.1"), text("weapons_station.2"),
                crafting("assembly:weapons_console")),
            entry("hand_phasers", "phaser_tng", text("hand_phasers.1"), crafting("phaser_nx"), crafting("phaser_tos"),
                crafting("phaser_movie"), crafting("phaser_tng"), crafting("phaser_fc"), text("hand_phasers.2"),
                crafting("rifle_nx"), crafting("rifle_tos"), crafting("rifle_tng"), crafting("rifle_fc")))),
        new Category("shields", "shield_emitter_e", List.of(
            entry("ship_shields", "shield_emitter_d", text("ship_shields.1"), text("ship_shields.2"),
                crafting("assembly:shield_emitter_tos"), crafting("assembly:shield_emitter_refit"), crafting("assembly:shield_console")),
            entry("base_shield", "base_shield_tos", text("base_shield.1"), crafting("assembly:base_shield_tos"),
                crafting("assembly:base_shield_refit"), text("base_shield.2")))),
        new Category("storage", "crate_diamond", List.of(
            entry("crates", "crate_wood", text("crates.1"), structure(GuideStructure.CRATE, "crates.2"), crafting("assembly:crate_wood"),
                crafting("assembly:crate_copper"), text("crates.3")),
            entry("crate_upgrades", "crate_upgrade_wood_to_copper", text("crate_upgrades.1"), crafting("crate_upgrade_wood_to_copper"),
                text("crate_upgrades.2"), crafting("stack_upgrade_tier_1"), crafting("stack_upgrade_tier_2")),
            entry("mining_laser", "mining_laser", text("mining_laser.1"), crafting("assembly:mining_laser"), text("mining_laser.2"),
                crafting("assembly:laser_focus"), crafting("assembly:mining_laser_control")))),
        new Category("enterprise", "captains_chair", List.of(
            entry("enterprises", "captains_chair", text("enterprises.1"), text("enterprises.2")),
            entry("turbolift", "turbolift", text("turbolift.1")),
            entry("transporter", "transporter_pad", text("transporter.1")),
            entry("doors", "keycard_captain", text("doors.1"), spotlight("keycard_captain", "doors.2")),
            entry("armory", "rifle_rack", text("armory.1"))))
    );

    private GuideBook() {}

    private static Entry entry(String id, String icon, Page... pages) {
        return new Entry(id, icon, List.of(pages));
    }

    private static Text text(String key) {
        return new Text(key);
    }

    private static Spotlight spotlight(String item, @Nullable String key) {
        return new Spotlight(item, key);
    }

    private static Crafting crafting(String recipe) {
        return new Crafting(recipe, null);
    }

    private static Structure structure(GuideStructure structure, String key) {
        return new Structure(structure, key);
    }

    /** Sprachschlüssel eines Seitentexts. */
    public static String key(String text) {
        return "guide.simonspacemod." + text;
    }

    /** Ein Item über seine ID; ohne Namensraum eins der Mod. Unbekannt: Barriere. */
    public static ItemStack item(String id) {
        Identifier key = Identifier.tryParse(id.contains(":") ? id : SimonSpaceMod.MODID + ":" + id);
        Item item = key == null ? null : ForgeRegistries.ITEMS.getValue(key);
        return new ItemStack(item == null || item == Items.AIR ? Items.BARRIER : item);
    }
}

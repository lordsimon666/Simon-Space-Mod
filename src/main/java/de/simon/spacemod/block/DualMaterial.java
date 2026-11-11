package de.simon.spacemod.block;

import net.minecraft.util.StringRepresentable;

/**
 * Materialien für zweiseitige Wand- und Deckenblöcke (gleiche Reihenfolge wie
 * {@code EnterpriseBlueprint.DUAL_MATERIALS}).
 */
public enum DualMaterial implements StringRepresentable {
    DECK_FLOOR, BRIDGE_DECK, CORRIDOR_WALL, CEILING_PANEL, BRIDGE_WALL, TRANSPORTER_WALL, SICKBAY_WALL,
    SICKBAY_FLOOR, BRIDGE_CARPET_TEAL, DECK_PLATE, BRIDGE_WALL_DARK, TNG_CARPET, TNG_WALL, HOLODECK_GRID,
    ENT_WALL, TRANSPORTER_PLATFORM;

    private final String id = name().toLowerCase();

    @Override
    public String getSerializedName() {
        return id;
    }
}

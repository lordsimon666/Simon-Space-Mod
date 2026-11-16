package de.simon.spacemod.storage;

/** Stufen der Großkiste; Plätze je Block wie bei den Kisten von Sophisticated Storage. */
public enum CrateTier {
    WOOD("wood", 27), COPPER("copper", 45), IRON("iron", 54), GOLD("gold", 81), DIAMOND("diamond", 108), NETHERITE("netherite", 132);

    public final String id;
    /** Inventarplätze je Block der Kiste (auch für die Luft im Inneren). */
    public final int slotsPerBlock;

    CrateTier(String id, int slotsPerBlock) {
        this.id = id;
        this.slotsPerBlock = slotsPerBlock;
    }
}

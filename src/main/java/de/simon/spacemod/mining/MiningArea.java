package de.simon.spacemod.mining;

/** Abbaubereich unter dem Laser (testbar): Schicht für Schicht von oben, je Schicht size × size. */
public final class MiningArea {
    public static final int MAX_SIZE = 16, MAX_DEPTH = 32;

    private MiningArea() {}

    public static int count(int size, int depth) {
        return size * size * depth;
    }

    /** Versatz {dx, dy (0 = direkt unter dem Laser), dz} zur laufenden Nummer. */
    public static int[] offset(int index, int size) {
        int layer = index / (size * size), rem = index % (size * size);
        int lo = -(size - 1) / 2;
        return new int[] {lo + rem % size, layer, lo + rem / size};
    }
}

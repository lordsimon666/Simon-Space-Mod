package de.simon.spacemod.space;

import net.minecraft.core.BlockPos;

/**
 * Raster der Asteroiden: höchstens einer je 136er-Würfel, um bis zu 15 Blöcke versetzt. Mittelpunkte
 * liegen so mindestens 106 Blöcke auseinander; da ein Asteroid höchstens 16 Blöcke Radius hat, bleiben
 * zwischen zwei Asteroiden immer mindestens 74 Blöcke frei (meist um 100).
 */
public final class AsteroidGrid {
    public static final int CELL = 136, JITTER = 15, MAX_RADIUS = 16, BOTTOM = 60;

    private AsteroidGrid() {}

    public static long cellHash(long seed, int i, int j, int k) {
        long h = seed ^ (i * 0x9E3779B97F4A7C15L) ^ (j * 0xC2B2AE3D27D4EB4FL) ^ (k * 0x165667B19E3779F9L);
        h ^= h >>> 31;
        h *= 0xBF58476D1CE4E5B9L;
        h ^= h >>> 29;
        return h;
    }

    /** Mittelpunkt des Asteroiden einer Rasterzelle oder null (etwa 30 % der Zellen sind leer). */
    public static BlockPos center(long seed, int i, int j, int k) {
        long h = cellHash(seed, i, j, k);
        if (Math.floorMod(h, 100) >= 70) return null;
        int jx = (int) Math.floorMod(h >> 8, 2 * JITTER + 1) - JITTER;
        int jy = (int) Math.floorMod(h >> 20, 2 * JITTER + 1) - JITTER;
        int jz = (int) Math.floorMod(h >> 32, 2 * JITTER + 1) - JITTER;
        return new BlockPos(i * CELL + CELL / 2 + jx, BOTTOM + j * CELL + jy, k * CELL + CELL / 2 + jz);
    }

}

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

    /**
     * Gedrehter Abbaubereich (fliegendes Schiff): alle Weltblöcke, deren Mitte im gedrehten Quader unter
     * {@code start} liegt (Schiffskoordinaten: Mitte von Block p bei p). Nummeriert von oben nach unten.
     */
    public static final class Rotated {
        public record Cell(net.minecraft.core.BlockPos local, net.minecraft.core.BlockPos world) {}

        private final org.joml.Quaternionf q;
        private final net.minecraft.world.phys.Vec3 shipPos;
        private final net.minecraft.core.BlockPos start;
        private final int size, depth, lo;
        private final int x0, y1, z0, nx, ny, nz;

        public Rotated(org.joml.Quaternionf q, net.minecraft.world.phys.Vec3 shipPos, net.minecraft.core.BlockPos start, int size, int depth) {
            this.q = q;
            this.shipPos = shipPos;
            this.start = start;
            this.size = size;
            this.depth = depth;
            lo = -(size - 1) / 2;
            double minX = Double.MAX_VALUE, minY = minX, minZ = minX, maxX = -minX, maxY = maxX, maxZ = maxX;
            for (int c = 0; c < 8; c++) {
                double x = start.getX() + ((c & 1) == 0 ? lo - 0.5 : lo + size - 0.5);
                double y = start.getY() + ((c & 2) == 0 ? -depth - 0.5 : -0.5);
                double z = start.getZ() + ((c & 4) == 0 ? lo - 0.5 : lo + size - 0.5);
                var w = de.simon.spacemod.flight.ShipPhysics.rotate(q, x, y, z).add(shipPos);
                minX = Math.min(minX, w.x); minY = Math.min(minY, w.y); minZ = Math.min(minZ, w.z);
                maxX = Math.max(maxX, w.x); maxY = Math.max(maxY, w.y); maxZ = Math.max(maxZ, w.z);
            }
            x0 = (int) Math.floor(minX); z0 = (int) Math.floor(minZ);
            int y0 = (int) Math.floor(minY);
            y1 = (int) Math.floor(maxY);
            nx = (int) Math.floor(maxX) - x0 + 1; ny = y1 - y0 + 1; nz = (int) Math.floor(maxZ) - z0 + 1;
        }

        public int total() {
            return nx * ny * nz;
        }

        /** Block Nummer i, null = liegt nicht im Bereich. */
        public Cell get(int i) {
            int layer = i / (nx * nz), rem = i % (nx * nz);
            var w = new net.minecraft.core.BlockPos(x0 + rem % nx, y1 - layer, z0 + rem / nx);
            var l = de.simon.spacemod.flight.ShipPhysics.inverse(q, net.minecraft.world.phys.Vec3.atCenterOf(w).subtract(shipPos));
            double dx = l.x - start.getX(), dy = l.y - start.getY(), dz = l.z - start.getZ();
            if (dx < lo - 0.5 || dx >= lo + size - 0.5 || dz < lo - 0.5 || dz >= lo + size - 0.5 || dy < -depth - 0.5 || dy >= -0.5) return null;
            return new Cell(net.minecraft.core.BlockPos.containing(l.x + 0.5, l.y + 0.5, l.z + 0.5), w);
        }
    }
}

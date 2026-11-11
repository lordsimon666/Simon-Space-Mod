package de.simon.spacemod.ship;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayDeque;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Räume: vom Hauptflur erreichbar, beleuchtet; Waffenkammer am Hauptflur. */
class RoomsTest {
    static boolean walk(EnterpriseBlueprint bp, int x, int y, int z) {
        ShipPart p = bp.get(x, y, z), b = bp.get(x, y - 1, z);
        return p == ShipPart.AIR && bp.get(x, y + 1, z) == ShipPart.AIR && b != null
            && b.name().matches(".*(FLOOR|DECK|CARPET|PLATE|GRID|DUAL|HULL|WALL|STEP|PLATFORM|PANEL|PLASMA|PAD).*");
    }

    static long key(int x, int y, int z) {
        return ((long) x << 40) | ((long) (y & 0xFFFFF) << 20) | (z & 0xFFFFF);
    }

    @ParameterizedTest
    @CsvSource({"NX_01,0.75,12", "NCC_1701,0.5,15", "NCC_1701,1.0,5", "NCC_1701_A,0.75,15", "NCC_1701_B,0.75,10", "NCC_1701_C,0.75,5",
                "NCC_1701_D,0.5,5", "NCC_1701_D,0.75,5", "NCC_1701_E,0.5,5", "NCC_1701_E,0.75,5", "NCC_1701_E,1.0,5"})
    void roomsReachableAndLit(ShipDesign d, double s, int maxUnreachablePercent) {
        EnterpriseBlueprint bp = new EnterpriseBlueprint(d, s);
        int sx = bp.sizeX, sz = 2 * bp.zOffset + 1;
        long walkTotal = 0, unreachable = 0, dark = 0;
        String ex = "";
        // 3D-Flutung: von allen Turbolift-Ausgängen, eine Stufe hoch/runter, Leitern, Türen
        java.util.Set<Long> seen = new java.util.HashSet<>();
        ArrayDeque<int[]> q = new ArrayDeque<>();
        for (EnterpriseBlueprint.LiftStop st : bp.liftStops) {
            BlockPos p = st.pos();
            for (int[] dd : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}})
                for (int dy = -1; dy <= 1; dy++) {
                    int x = p.getX() + dd[0], y = p.getY() + dy, z = p.getZ() + dd[1];
                    if (walk(bp, x, y, z) && seen.add(key(x, y, z))) q.add(new int[] {x, y, z});
                }
        }
        while (!q.isEmpty()) {
            int[] c = q.poll();
            for (int[] dd : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                int nx = c[0] + dd[0], nz = c[2] + dd[1];
                // Tür: hindurchgehen (2 Schritte)
                if (EnterpriseBlueprint.isDoor(bp.get(nx, c[1], nz))) {
                    int tx = nx + dd[0], tz = nz + dd[1];
                    if (walk(bp, tx, c[1], tz) && seen.add(key(tx, c[1], tz))) q.add(new int[] {tx, c[1], tz});
                    continue;
                }
                for (int dy = -1; dy <= 1; dy++) {
                    int ny = c[1] + dy;
                    if (dy == 1 && bp.get(c[0], c[1] + 2, c[2]) != ShipPart.AIR) continue; // Kopffreiheit beim Hochsteigen
                    if (walk(bp, nx, ny, nz) && seen.add(key(nx, ny, nz))) { q.add(new int[] {nx, ny, nz}); break; }
                }
            }
            // Leitern
            for (int dy : new int[] {-1, 1}) {
                for (int k = 1; k < 30; k++) {
                    ShipPart lp = bp.get(c[0], c[1] + dy * k, c[2]);
                    boolean ladderNear = false;
                    for (int[] dd : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {0, 0}}) ladderNear |= bp.get(c[0] + dd[0], c[1] + dy * (k - 1), c[2] + dd[1]) == ShipPart.LADDER;
                    if (!ladderNear) break;
                    if (walk(bp, c[0], c[1] + dy * k, c[2]) && seen.add(key(c[0], c[1] + dy * k, c[2]))) q.add(new int[] {c[0], c[1] + dy * k, c[2]});
                    if (lp != ShipPart.AIR && lp != ShipPart.LADDER) break;
                }
            }
        }
        // Räume (überwiegend von Innenwänden begrenzt) zählen
        for (int x = 0; x < bp.sizeX; x++) for (int y = 2; y < bp.sizeY - 2; y++) for (int z = -bp.zOffset; z <= bp.zOffset; z++) {
            if (!walk(bp, x, y, z) || bp.get(x, y - 1, z).name().startsWith("HULL")) continue; // Hohlraum auf der Hülle
            int hull = 0, inner = 0;
            for (int[] dd : new int[][] {{3, 0}, {-3, 0}, {0, 3}, {0, -3}}) {
                for (int k = 1; k <= 12; k++) {
                    ShipPart w = bp.get(x + dd[0] / 3 * k, y, z + dd[1] / 3 * k);
                    if (w == ShipPart.AIR) continue;
                    if (w != null) { if (w.name().startsWith("HULL")) hull++; else inner++; }
                    break;
                }
            }
            if (hull >= 2) continue; // Hohlraum an der Hülle
            walkTotal++;
            if (!seen.contains(key(x, y, z))) { unreachable++; if (ex.length() < 160) ex += " (" + x + "," + y + "," + z + ")"; }
            boolean lit = false;
            for (int dx = -4; dx <= 4 && !lit; dx++) for (int dz = -4; dz <= 4 && !lit; dz++) for (int dy = 0; dy <= 6 && !lit; dy++) {
                ShipPart p = bp.get(x + dx, y + dy, z + dz);
                lit = p == ShipPart.CEILING_LIGHT || p == ShipPart.WARP_CORE || p == ShipPart.WARP_CORE_TNG || p == ShipPart.WARP_CORE_E || p == ShipPart.WARP_CORE_NX;
            }
            if (!lit) dark++;
        }
        System.out.printf("ROOMS %s %.2f walk=%d unreachable=%d (%.1f%%) dark=%d (%.1f%%)%s%n", d, s, walkTotal, unreachable,
            100.0 * unreachable / walkTotal, dark, 100.0 * dark / walkTotal, ex);
        assertTrue(unreachable * 100 < walkTotal * maxUnreachablePercent, "zu viele unerreichbare Räume: " + unreachable + ex);
        assertTrue(dark * 10 < walkTotal, "zu viele dunkle Stellen: " + dark);

        // keine halbhohen Wände: eine Wand (mind. 2 hoch, beidseitig Luft) endet nicht unter der Decke
        int half = 0;
        for (int x = 1; x < bp.sizeX - 1; x++) for (int z = -bp.zOffset + 1; z < bp.zOffset; z++) for (int y = 2; y < bp.sizeY - 2; y++) {
            ShipPart p = bp.get(x, y, z), b = bp.get(x, y - 1, z);
            if (p == null || b == null || !p.name().contains("WALL") || p.name().startsWith("HULL") || !b.name().contains("WALL")) continue;
            if (bp.get(x, y + 1, z) != ShipPart.AIR) continue;
            boolean thin = bp.get(x - 1, y, z) == ShipPart.AIR && bp.get(x + 1, y, z) == ShipPart.AIR
                || bp.get(x, y, z - 1) == ShipPart.AIR && bp.get(x, y, z + 1) == ShipPart.AIR;
            int k = 1; while (k < 10 && bp.get(x, y + k, z) == ShipPart.AIR) k++;
            if (thin && k < 10 && bp.get(x, y + k, z) != null) half++;
        }
        System.out.println("HALFWALLS " + d + " " + s + " " + half);
        assertTrue(half < 40, "halbhohe Wände: " + half);
    }
}

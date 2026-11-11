package de.simon.spacemod.ship;
/** Holodeck-Gitter darf nur ins Holodeck zeigen (Wände und Decken). */
class DualFacesTest {
    static int floorBelow(EnterpriseBlueprint bp, int x, int y, int z) {
        for (int yy = y - 1; yy >= y - 4; yy--) { ShipPart p = bp.get(x, yy, z); if (p == null) return -1; if (p != ShipPart.AIR) return p == ShipPart.HOLODECK_GRID ? 1 : p == ShipPart.DUAL_FLOOR && EnterpriseBlueprint.DUAL_MATERIALS[EnterpriseBlueprint.dualA(bp.codeAt(x, yy, z))] == ShipPart.HOLODECK_GRID ? 1 : 0; }
        return -1;
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({"NCC_1701_D,0.75", "NCC_1701_D,1.0", "NCC_1701_E,0.75", "NCC_1701_E,1.0"})
    void gridOnlyFacesHolodeck(ShipDesign d, double s) {
        EnterpriseBlueprint bp = new EnterpriseBlueprint(d, s);
        int wrong = 0, right = 0, ceilWrong = 0, floorWrong = 0; String ex = "";
        int[][] dirs = {{1,0},{-1,0},{0,1},{0,-1}};
        for (int x = 1; x < bp.sizeX - 1; x++) for (int y = 1; y < bp.sizeY - 1; y++) for (int z = -bp.zOffset + 1; z < bp.zOffset; z++) {
            int c = bp.codeAt(x, y, z); if (c == 0) continue; ShipPart p = EnterpriseBlueprint.part(c);
            for (int[] dd : dirs) {
                if (bp.get(x + dd[0], y, z + dd[1]) != ShipPart.AIR) continue;
                boolean grid;
                if (p == ShipPart.HOLODECK_GRID) grid = true;
                else if (p == ShipPart.DUAL_WALL) {
                    var f = EnterpriseBlueprint.facing(c);
                    boolean front = dd[0] == (f == net.minecraft.core.Direction.NORTH ? -1 : f == net.minecraft.core.Direction.SOUTH ? 1 : 0) && dd[1] == (f == net.minecraft.core.Direction.WEST ? -1 : f == net.minecraft.core.Direction.EAST ? 1 : 0);
                    if (EnterpriseBlueprint.variant(c) == 1) { // Ecke: Front + links (gegen den Uhrzeigersinn) vorne
                        var l = f.getCounterClockWise();
                        boolean left = dd[0] == (l == net.minecraft.core.Direction.NORTH ? -1 : l == net.minecraft.core.Direction.SOUTH ? 1 : 0) && dd[1] == (l == net.minecraft.core.Direction.WEST ? -1 : l == net.minecraft.core.Direction.EAST ? 1 : 0);
                        front = front || left;
                    }
                    grid = EnterpriseBlueprint.DUAL_MATERIALS[front ? EnterpriseBlueprint.dualA(c) : EnterpriseBlueprint.dualB(c)] == ShipPart.HOLODECK_GRID;
                } else continue;
                if (!grid) continue;
                if (floorBelow(bp, x + dd[0], y, z + dd[1]) == 1) right++; else { wrong++; if (ex.length() < 200) ex += " (" + (x + dd[0]) + "," + y + "," + (z + dd[1]) + ")"; }
            }
            // Decke: Gitter nach unten in einen Raum ohne Gitterboden
            if (bp.get(x, y - 1, z) == ShipPart.AIR && (p == ShipPart.HOLODECK_GRID || p == ShipPart.DUAL_FLOOR && EnterpriseBlueprint.DUAL_MATERIALS[EnterpriseBlueprint.dualB(c)] == ShipPart.HOLODECK_GRID)
                && floorBelow(bp, x, y - 1, z) == 0) ceilWrong++;
        }
        System.out.println("GRID " + d + " " + s + " right=" + right + " wrong=" + wrong + " ceilWrong=" + ceilWrong + ex);
        org.junit.jupiter.api.Assertions.assertTrue(right > 0, "kein Holodeck");
        org.junit.jupiter.api.Assertions.assertTrue(wrong * 50 < right, "Holodeck-Gitter zeigt in den Gang: " + wrong + ex);
        org.junit.jupiter.api.Assertions.assertTrue(ceilWrong * 50 < right, "Holodeck-Gitter an fremden Decken: " + ceilWrong);
    }
}

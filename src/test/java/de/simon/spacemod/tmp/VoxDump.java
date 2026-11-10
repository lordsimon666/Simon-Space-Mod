package de.simon.spacemod.tmp;

import de.simon.spacemod.ship.*;
import java.io.*;
import org.junit.jupiter.api.Test;

class VoxDump {
    @Test
    void dump() throws Exception {
        String dir = System.getProperty("ship.preview.dir");
        for (ShipDesign d : new ShipDesign[] {ShipDesign.NCC_1701, ShipDesign.NCC_1701_A, ShipDesign.NCC_1701_B, ShipDesign.NCC_1701_C, ShipDesign.NCC_1701_D}) {
            EnterpriseBlueprint bp = new EnterpriseBlueprint(d, 1.0);
            try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(new File(dir, "vox_" + d.id + ".bin"))))) {
                bp.forEach((x, y, z, code) -> {
                    ShipPart p = EnterpriseBlueprint.part(code);
                    if (p == ShipPart.AIR) return;
                    int[][] n = {{1,0,0},{-1,0,0},{0,1,0},{0,-1,0},{0,0,1},{0,0,-1}};
                    boolean outside = false;
                    for (int[] o : n) if (bp.get(x + o[0], y + o[1], z + o[2]) == null) { outside = true; break; }
                    if (!outside) return;
                    try { out.writeShort(x); out.writeShort(y); out.writeShort(z); out.writeInt(p.color); } catch (IOException e) { throw new UncheckedIOException(e); }
                });
            }
        }
    }
}

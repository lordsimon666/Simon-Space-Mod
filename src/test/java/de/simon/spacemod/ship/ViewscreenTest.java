package de.simon.spacemod.ship;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/** Der Hauptbildschirm liegt von der Brücke aus ganz frei und zeigt mit der Rückseite nie nach draußen oder in einen Nachbarraum. */
class ViewscreenTest {
    @ParameterizedTest
    @EnumSource(ShipDesign.class)
    void viewscreenVisibleAndBacked(ShipDesign d) {
        EnterpriseBlueprint bp = new EnterpriseBlueprint(d, 1.0);
        int total = 0, visible = 0, exposed = 0;
        for (int x = 0; x < bp.sizeX; x++)
            for (int y = 0; y < bp.sizeY; y++)
                for (int z = -bp.zOffset; z <= bp.zOffset; z++) {
                    if (bp.get(x, y, z) != ShipPart.VIEWSCREEN) continue;
                    total++;
                    if (bp.get(x + 1, y, z) == ShipPart.AIR) visible++;
                    ShipPart back = bp.get(x - 1, y, z);
                    if (back == null || back == ShipPart.AIR || back.name().contains("CARPET")) exposed++;
                }
        assertTrue(total >= 15, "Hauptbildschirm fehlt");
        assertTrue(visible >= total - 1, "Hauptbildschirm verdeckt: " + visible + " von " + total + " sichtbar");
        assertEquals(0, exposed, "Rückseite des Hauptbildschirms liegt frei");
    }
}

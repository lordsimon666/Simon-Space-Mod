package de.simon.spacemod.village;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class StarfleetCrewTest {
    private static final Path RES = Path.of("src/main/resources");

    @Test
    void everyNameHasASkin() {
        Set<String> names = new HashSet<>();
        for (StarfleetCrew.Member m : StarfleetCrew.CREW) {
            assertTrue(names.add(m.name()), "doppelter Name " + m.name());
            assertTrue(Files.exists(RES.resolve("assets/simonspacemod/textures/entity/starfleet/skin_" + m.skin() + ".png")), "Haut fehlt: " + m.skin());
            assertEquals(m, StarfleetCrew.byName(m.name(), UUID.randomUUID()));
        }
    }

    @Test
    void uniformsAreRandomButFixedAndExist() {
        Set<String> seen = new HashSet<>();
        java.util.Random r = new java.util.Random(1);
        for (int i = 0; i < 2000; i++) {
            UUID id = new UUID(r.nextLong(), r.nextLong());
            String u = StarfleetCrew.uniform(id);
            assertEquals(u, StarfleetCrew.uniform(id));
            seen.add(u);
        }
        assertEquals(15, seen.size(), "nicht alle Uniformen kommen vor");
        for (String u : seen)
            assertTrue(Files.exists(RES.resolve("assets/simonspacemod/textures/entity/equipment/humanoid/" + u + ".png")), u);
    }

    @Test
    void shuttleHouseInAboutTwoPercentOfVillages() {
        for (int total : new int[] {68, 72, 76, 81, 87}) {
            double perDraw = VillageShare.chance(total) / (total + 1.0);
            double perVillage = 1 - Math.pow(1 - perDraw, VillageShare.HOUSES_PER_VILLAGE);
            assertEquals(0.02, perVillage, 0.001);
        }
    }

    @Test
    void tradesNeverSellFunctionalBlocks() throws Exception {
        try (var files = Files.walk(RES.resolve("data/simonspacemod/villager_trade/starfleet"))) {
            for (Path f : files.filter(Files::isRegularFile).toList()) {
                String item = f.getFileName().toString().replace(".json", "");
                assertTrue(item.startsWith("uniform_") || item.startsWith("trousers") || item.startsWith("boots") || item.contains("chair")
                    || item.contains("table") || item.contains("desk") || item.contains("carpet") || item.contains("railing")
                    || item.equals("cargo_container") || item.equals("table_viewer") || item.equals("hull_pennant"), "kein Deko: " + item);
                assertTrue(!item.contains("bed") && !item.contains("console") && !item.contains("light"), item);
            }
        }
    }
}

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
            assertEquals(m.skin(), StarfleetCrew.skin(m.name(), UUID.randomUUID()));
            assertTrue(!m.outfits().isEmpty());
            for (String o : m.outfits()) assertTrue(Files.exists(outfitTexture(o)), "Kleidung fehlt: " + o);
        }
    }

    private static Path outfitTexture(String o) {
        return RES.resolve("assets/simonspacemod/textures/entity/equipment/humanoid/" + o + ".png");
    }

    @Test
    void canonCharactersWearTheirOwnUniforms() {
        java.util.Random r = new java.util.Random(3);
        Set<String> picard = new HashSet<>(), seven = new HashSet<>();
        for (int i = 0; i < 500; i++) {
            UUID id = new UUID(r.nextLong(), r.nextLong());
            picard.add(StarfleetCrew.outfit("Jean-Luc Picard", id));
            seven.add(StarfleetCrew.outfit("Seven of Nine", id));
            assertEquals("uniform_nx_command", StarfleetCrew.outfit("Jonathan Archer", id));
        }
        assertEquals(Set.of("uniform_tng_command", "uniform_ds9_command", "uniform_fc_command"), picard);
        assertTrue(seven.contains("outfit_seven"));
        assertTrue(StarfleetCrew.CREW.stream().anyMatch(m -> m.outfits().contains("outfit_kirk_green")));
    }

    @Test
    void madeUpNamesAreCommonPlainAndRandom() {
        net.minecraft.util.RandomSource rnd = net.minecraft.util.RandomSource.create(5);
        int canon = 0, n = 20000;
        java.util.Random r = new java.util.Random(9);
        Set<String> uniforms = new HashSet<>();
        for (int i = 0; i < n; i++) {
            String name = StarfleetCrew.randomName(rnd);
            if (StarfleetCrew.canon(name) != null) { canon++; continue; }
            UUID id = new UUID(r.nextLong(), r.nextLong());
            String skin = StarfleetCrew.skin(name, id);
            assertTrue(StarfleetCrew.PLAIN_SKINS.contains(skin));
            assertTrue(!Set.of("data", "seven", "worf", "laforge", "spock", "tuvok", "tpol", "picard", "sisko", "riker", "scott").contains(skin));
            uniforms.add(StarfleetCrew.outfit(name, id));
        }
        assertEquals(0.25, canon / (double) n, 0.015, "Anteil bekannter Figuren");
        assertEquals(18, uniforms.size());
    }

    @Test
    void keycardsByRank() {
        assertEquals("captain", StarfleetCrew.canon("Jean-Luc Picard").card());
        assertEquals("security", StarfleetCrew.canon("Worf").card());
        assertEquals("command", StarfleetCrew.canon("William Riker").card());
        assertEquals(null, StarfleetCrew.canon("Mara Lindqvist"), "erfundene Namen haben keine Karte");
        for (StarfleetCrew.Member m : StarfleetCrew.CREW) assertTrue(Set.of("captain", "command", "security").contains(m.card()));
    }

    @Test
    void halfTheUniformsAreTorn() {
        java.util.Random r = new java.util.Random(11);
        int torn = 0, n = 10000;
        Set<Integer> patterns = new HashSet<>();
        for (int i = 0; i < n; i++) {
            int t = StarfleetCrew.tear(new UUID(r.nextLong(), r.nextLong()));
            if (t >= 0) { torn++; patterns.add(t); }
        }
        assertEquals(0.5, torn / (double) n, 0.02);
        assertEquals(Set.of(0, 1, 2, 3), patterns);
    }

    @Test
    void outfitsAreWearable() {
        for (String o : new String[] {"outfit_seven", "outfit_kirk_green"})
            assertTrue(Files.exists(RES.resolve("assets/simonspacemod/equipment/" + o + ".json")), o);
        assertTrue(Files.exists(RES.resolve("assets/simonspacemod/textures/entity/equipment/humanoid_leggings/outfit_seven_legs.png")));
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
        assertEquals(18, seen.size(), "nicht alle Uniformen kommen vor");
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
                assertTrue(item.startsWith("uniform_") || item.startsWith("outfit_") || item.startsWith("trousers") || item.startsWith("boots") || item.contains("chair")
                    || item.contains("table") || item.contains("desk") || item.contains("carpet") || item.contains("railing")
                    || item.equals("cargo_container") || item.equals("table_viewer") || item.equals("hull_pennant"), "kein Deko: " + item);
                assertTrue(!item.contains("bed") && !item.contains("console") && !item.contains("light"), item);
            }
        }
    }
}

package de.simon.spacemod.ship;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.File;
import java.util.EnumMap;
import java.util.Map;
import javax.imageio.ImageIO;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Prüft den Bauplan ohne laufendes Minecraft und rendert Vorschaubilder. */
class EnterpriseBlueprintTest {

    @ParameterizedTest
    @CsvSource({"NX_01,0.4", "NX_01,0.75", "NX_01,1.0",
                "NCC_1701,0.4", "NCC_1701,0.5", "NCC_1701,0.75", "NCC_1701,1.0",
                "NCC_1701_A,0.4", "NCC_1701_A,0.5", "NCC_1701_A,0.75", "NCC_1701_A,1.0",
                "NCC_1701_B,0.4", "NCC_1701_B,0.5", "NCC_1701_B,0.75", "NCC_1701_B,1.0",
                "NCC_1701_C,0.4", "NCC_1701_C,0.75", "NCC_1701_C,1.0",
                "NCC_1701_D,0.4", "NCC_1701_D,0.75", "NCC_1701_D,1.0",
                "NCC_1701_E,0.4", "NCC_1701_E,0.75", "NCC_1701_E,1.0"})
    void blueprintIsConsistent(ShipDesign design, double scale) throws Exception {
        EnterpriseBlueprint bp = new EnterpriseBlueprint(design, scale);
        Map<ShipPart, Integer> counts = new EnumMap<>(ShipPart.class);
        bp.forEach((x, y, z, code) -> counts.merge(EnterpriseBlueprint.part(code), 1, Integer::sum));
        System.out.printf(design + " scale %.2f: %d x %d x %d, %d Zellen, %s, lifts=%d%n",
            scale, bp.sizeX, bp.sizeZ, bp.sizeY, bp.countBlocks(), counts, bp.liftStops.size());

        // Hülle dicht: keine Luft und keine Möbel direkt am Außenraum
        assertEquals(0, bp.countLeaks(), "Loch in der Hülle");

        // Passt in die normale Overworld-Bauhöhe (-64..320 = 384 Blöcke)
        assertTrue(bp.sizeY < 384 - 64, "zu hoch: " + bp.sizeY);

        // Wichtige Räume vorhanden
        assertTrue(counts.getOrDefault(ShipPart.CAPTAINS_CHAIR, 0) + counts.getOrDefault(ShipPart.CAPTAINS_CHAIR_TEAL, 0)
            + counts.getOrDefault(ShipPart.CAPTAINS_CHAIR_TNG, 0) + counts.getOrDefault(ShipPart.CAPTAINS_CHAIR_NX, 0) >= 1);
        assertEquals(design.tng ? 44 : 21, counts.get(ShipPart.VIEWSCREEN));
        assertTrue(counts.getOrDefault(ShipPart.BRIDGE_RAILING, 0) + counts.getOrDefault(ShipPart.BRIDGE_RAILING_GRAY, 0)
            + counts.getOrDefault(ShipPart.BRIDGE_RAILING_WOOD, 0) > (design.tng ? 8 : 20) || design.nx, "Geländer fehlt");
        assertTrue(counts.getOrDefault(ShipPart.BRIDGE_STATION, 0) + counts.getOrDefault(ShipPart.EXCELSIOR_CONSOLE, 0)
            + counts.getOrDefault(ShipPart.TNG_CONSOLE, 0) + counts.getOrDefault(ShipPart.ENT_CONSOLE, 0) > 10, "Stationen fehlen");
        assertTrue(counts.getOrDefault(ShipPart.TRANSPORTER_ENERGIZER, 0) > 0 || design.nx, "Energiewand fehlt");
        assertTrue(bp.warpCores.size() >= 30, "Warpkern fehlt");
        assertEquals(1, counts.getOrDefault(ShipPart.POWER_CONSOLE, 0), "Energieverteilung im Maschinenraum fehlt");
        assertTrue(counts.getOrDefault(ShipPart.ALARM_LIGHT, 0) >= 4, "Alarmleuchten fehlen");
        assertEquals(design.nx ? 0 : 1, counts.getOrDefault(ShipPart.SHIELD_EMITTER, 0), "Schildemitter");
        assertEquals(design.nx ? 0 : 1, counts.getOrDefault(ShipPart.SHIELD_CONSOLE, 0), "Schildkonsole auf der Brücke");
        assertEquals(2, bp.armoryChargers.size(), "Waffenkammer: Ladestationen fehlen");
        assertEquals(bp.hasRifles() ? 3 : 0, bp.armoryRacks.size(), "Waffenkammer: Gewehrständer");
        assertTrue(bp.armoryHolders.size() >= 2, "Waffenkammer: Phaserhalter fehlen");
        // zweiseitige Wände zwischen Räumen mit verschiedenem Wandmaterial (Krankenstation, Holodeck ...)
        if (scale >= 0.75) assertTrue(counts.getOrDefault(ShipPart.DUAL_WALL, 0) > 0, "keine zweiseitigen Wände");
        // Türen: Waffenkammer mit Sicherheitstür, bei voller Deckaufteilung Türen in vielen Räumen
        boolean armoryLift = bp.liftStops.stream().anyMatch(st -> st.name().endsWith("– Waffenkammer"));
        assertTrue(bp.liftStops.stream().anyMatch(st -> st.name().contains("Waffenkammer")), "kein Turbolift-Halt nennt die Waffenkammer");
        // Türen sind auf beiden Seiten frei
        for (BlockPos o : bp.doors) {
            int c = bp.codeAt(o.getX(), o.getY(), o.getZ());
            boolean alongZ = EnterpriseBlueprint.facing(c) == net.minecraft.core.Direction.SOUTH;
            int px = alongZ ? 1 : 0, pz = alongZ ? 0 : 1;
            for (int side : new int[] {1, -1}) for (int col = 0; col <= 1; col++) for (int dy = 0; dy <= 1; dy++) {
                int cx = alongZ ? o.getX() : o.getX() - col, cz = alongZ ? o.getZ() + col : o.getZ();
                assertEquals(ShipPart.AIR, bp.get(cx + side * px, o.getY() + dy, cz + side * pz), "Tür zugestellt bei " + o);
            }
        }
        if (scale >= 0.75) assertTrue(bp.armoryDoor != null && !armoryLift, "Waffenkammer: nur über eine Tür vom Gang, kein eigener Lift");
        assertTrue(bp.armoryDoor != null || armoryLift, "Waffenkammer ohne Zugang");
        int[] ab = bp.armoryBounds();
        assertNotNull(ab, "keine Waffenkammer");
        if (bp.armoryDoor != null) {
            int ai = bp.doors.indexOf(bp.armoryDoor);
            assertTrue(ai >= 0 && bp.doorKinds.get(ai) == EnterpriseBlueprint.DOOR_SECURITY, "Waffenkammer braucht eine Sicherheitstür");
            // genau eine Tür in den Wänden der Kammer
            long inWalls = bp.doors.stream().filter(d -> d.getX() >= ab[0] && d.getX() <= ab[1] && d.getZ() >= ab[2] && d.getZ() <= ab[3]
                && d.getY() > ab[4] && d.getY() < ab[4] + 4).count();
            assertEquals(1, inWalls, "Waffenkammer darf nur eine Tür haben");
        }
        // bei geschlossener Tür kommt man aus der Kammer nirgendwohin (Luft-Flutung in Kopf- und Fußhöhe)
        for (int y = ab[4] + 1; y <= ab[4] + 3; y++) {
            java.util.ArrayDeque<int[]> q = new java.util.ArrayDeque<>();
            java.util.Set<Long> seen = new java.util.HashSet<>();
            int cx = (ab[0] + ab[1]) / 2, cz = (ab[2] + ab[3]) / 2;
            for (int x = ab[0] + 1; x < ab[1]; x++) for (int z = ab[2] + 1; z < ab[3]; z++)
                if (bp.get(x, y, z) == ShipPart.AIR) { q.add(new int[] {x, z}); seen.add(((long) x << 32) | (z & 0xffffffffL)); }
            while (!q.isEmpty()) {
                int[] c = q.poll();
                assertTrue(c[0] > ab[0] && c[0] < ab[1] && c[1] > ab[2] && c[1] < ab[3], "Waffenkammer undicht bei " + c[0] + "," + y + "," + c[1]);
                for (int[] d : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                    int nx = c[0] + d[0], nz = c[1] + d[1];
                    ShipPart np = bp.get(nx, y, nz);
                    if (np == null || np != ShipPart.AIR && !np.name().contains("CHAIR") && np != ShipPart.CEILING_LIGHT) continue;
                    if (seen.add(((long) nx << 32) | (nz & 0xffffffffL))) q.add(new int[] {nx, nz});
                }
            }
        }
        if (scale >= 0.75) assertTrue(bp.doors.size() >= 15, "zu wenige Türen: " + bp.doors.size());
        for (BlockPos d : bp.doors) {
            int code = bp.codeAt(d.getX(), d.getY(), d.getZ());
            assertTrue(EnterpriseBlueprint.isDoor(EnterpriseBlueprint.part(code)) && EnterpriseBlueprint.variant(code) == 0, "Tür kaputt bei " + d);
            int parts = 0;
            for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) for (int dy = 0; dy <= 1; dy++)
                if (EnterpriseBlueprint.isDoor(bp.get(d.getX() + dx, d.getY() + dy, d.getZ() + dz))) parts++;
            assertTrue(parts >= 4, "Tür unvollständig bei " + d);
        }
        assertEquals(design.nx ? 1 : 6, counts.get(ShipPart.TRANSPORTER_PAD), "Transporterraum fehlt");
        // Turbolift-Halte: mindestens Brücke, Maschinenraum, Transporterraum; in Originalgröße alle Decks
        assertEquals(1, bp.weaponStations.size(), "Waffenstation fehlt");
        ShipPart gun = design.nx ? ShipPart.PHASE_CANNON : design.tng ? ShipPart.PHASER_STRIP
            : design.refit || design.excelsior ? ShipPart.PHASER_EMITTER : ShipPart.PHASER_BANK;
        int guns = counts.getOrDefault(gun, 0);
        assertTrue(guns >= (gun == ShipPart.PHASER_EMITTER || design.tng ? 8 : 4), "zu wenige Phaser: " + guns);
        assertEquals(guns, bp.phasers.size(), "Phaserliste unvollständig");
        assertTrue(bp.cameras.size() >= 7, "zu wenige Kameras: " + bp.cameraNames);
        assertTrue(bp.torpedoes.size() >= 2, "Torpedowerfer fehlen: " + bp.torpedoes.size());
        assertEquals(design == ShipDesign.NCC_1701_E, !bp.quantums.isEmpty(), "Quantentorpedos nur bei der E");
        for (BlockPos c : bp.cameras)
            for (int dy = -1; dy <= 2; dy++) {
                var cp = bp.get(c.getX(), c.getY() + dy, c.getZ());
                assertTrue(cp == null || cp == ShipPart.AIR, "Kamera steckt in der Hülle: " + c);
            }
        assertTrue(bp.liftStops.size() >= 3, "zu wenige Turbolift-Halte");
        if (scale >= 0.75) assertTrue(bp.liftStops.size() >= (design.nx ? 5 : 8), "Decks fehlen: " + bp.liftStops.size());
        assertTrue(counts.getOrDefault(ShipPart.BIOBED, 0) > 0 || scale < 0.75, "Krankenstation fehlt");
        assertTrue(counts.getOrDefault(ShipPart.CONFERENCE_TABLE, 0) > 0 || scale < 0.75, "Besprechungsraum fehlt");
        java.util.Set<String> names = new java.util.HashSet<>();
        for (EnterpriseBlueprint.LiftStop stop : bp.liftStops) {
            BlockPos p = stop.pos();
            assertTrue(names.add(stop.name()), "doppelter Deckname " + stop.name());
            assertTrue(EnterpriseBlueprint.isLift(bp.get(p.getX(), p.getY(), p.getZ())), "kein Panel bei " + p + " (" + stop.name() + ")");
            BlockPos stand = localStep(p, EnterpriseBlueprint.facing(bp.codeAt(p.getX(), p.getY(), p.getZ())));
            assertStandable(bp, stand, "Turbolift " + stop.name());
        }
        System.out.println("Decks: " + bp.liftStops.stream().map(EnterpriseBlueprint.LiftStop::name).toList());
        assertStandable(bp, bp.bridgeSpawn, "Brücke");

        render(bp, scale);
        if (design.excelsior) {
            assertTrue(counts.getOrDefault(ShipPart.HULL_TEAL, 0) > 0, "petrolfarbene Akzente fehlen");
            assertTrue(counts.getOrDefault(ShipPart.IMPULSE_ENGINE, 0) > 0, "Impulstriebwerke fehlen");
            assertTrue(counts.getOrDefault(ShipPart.DEFLECTOR_DISH_BLUE, 0) > 0, "blaue Deflektorschüssel fehlt");
        }
        if (design.tng) {
            assertTrue(counts.getOrDefault(ShipPart.HULL_AZTEC, 0) > 0, "Aztec-Muster fehlt");
            assertTrue(counts.getOrDefault(ShipPart.BUSSARD_RED, 0) > 0, "rote Bussard-Kollektoren fehlen");
            assertTrue(counts.getOrDefault(ShipPart.TNG_CHAIR, 0) >= 4, "TNG-Brücke fehlt");
            if (scale >= 0.75) {
                assertTrue(counts.getOrDefault(ShipPart.HOLODECK_GRID, 0) > 0, "Holodecks fehlen");
                assertTrue(counts.getOrDefault(ShipPart.MSD_DISPLAY, 0) == 21, "Master Systems Display fehlt");
            }
        }
        if (design.refit) {
            assertTrue(counts.getOrDefault(ShipPart.DEFLECTOR_DISH_BLUE, 0) > 0, "blaue Deflektorschüssel fehlt");
            assertTrue(counts.getOrDefault(ShipPart.TORPEDO_LAUNCHER, 0) > 0, "Torpedowerfer fehlen");
            assertTrue(counts.getOrDefault(ShipPart.HULL_AZTEC, 0) > 0, "Aztec-Muster fehlt");
        }
    }

    /** Im Bauplan zeigt NORDEN zum Bug (-X), OSTEN nach Steuerbord (+Z). */
    private static BlockPos localStep(BlockPos p, net.minecraft.core.Direction dir) {
        return switch (dir) {
            case NORTH -> p.offset(-1, 0, 0);
            case SOUTH -> p.offset(1, 0, 0);
            case EAST -> p.offset(0, 0, 1);
            case WEST -> p.offset(0, 0, -1);
            default -> p;
        };
    }

    private static void assertStandable(EnterpriseBlueprint bp, BlockPos p, String what) {
        assertEquals(ShipPart.AIR, bp.get(p.getX(), p.getY(), p.getZ()), what + ": Füße nicht frei bei " + p);
        assertEquals(ShipPart.AIR, bp.get(p.getX(), p.getY() + 1, p.getZ()), what + ": Kopf nicht frei bei " + p);
        ShipPart below = bp.get(p.getX(), p.getY() - 1, p.getZ());
        assertNotNull(below, what + ": kein Boden");
        assertTrue(below != ShipPart.AIR, what + ": Boden ist Luft bei " + p);
    }

    // ------------------------------------------------------------------ Vorschaubilder

    private static void render(EnterpriseBlueprint bp, double scale) throws Exception {
        String dir = System.getProperty("ship.preview.dir");
        if (dir == null) return;
        new File(dir).mkdirs();
        int px = scale < 0.6 ? 4 : 2;
        String tag = bp.design.id + "_" + String.format("%03d", Math.round(scale * 100));

        // Draufsicht (oberster Block jeder Säule)
        BufferedImage top = new BufferedImage(bp.sizeX * px, bp.sizeZ * px, BufferedImage.TYPE_INT_RGB);
        for (int x = 0; x < bp.sizeX; x++)
            for (int z = -bp.zOffset; z <= bp.zOffset; z++)
                for (int y = bp.sizeY - 1; y >= 0; y--) {
                    ShipPart p = bp.get(x, y, z);
                    if (p != null && p != ShipPart.AIR) {
                        fill(top, x, z + bp.zOffset, px, shadeByHeight(p.color, y, bp.sizeY));
                        break;
                    }
                }
        ImageIO.write(top, "png", new File(dir, "top_" + tag + ".png"));

        // Untersicht (unterster Block jeder Säule, wie von unten gesehen: links/rechts vertauscht)
        BufferedImage bottom = new BufferedImage(bp.sizeX * px, bp.sizeZ * px, BufferedImage.TYPE_INT_RGB);
        for (int x = 0; x < bp.sizeX; x++)
            for (int z = -bp.zOffset; z <= bp.zOffset; z++)
                for (int y = 0; y < bp.sizeY; y++) {
                    ShipPart p = bp.get(x, y, z);
                    if (p != null && p != ShipPart.AIR) {
                        fill(bottom, x, bp.zOffset - z, px, p.color);
                        break;
                    }
                }
        ImageIO.write(bottom, "png", new File(dir, "bottom_" + tag + ".png"));

        // Seitenansicht (von Backbord, vorderster Block)
        BufferedImage side = new BufferedImage(bp.sizeX * px, bp.sizeY * px, BufferedImage.TYPE_INT_RGB);
        for (int x = 0; x < bp.sizeX; x++)
            for (int y = 0; y < bp.sizeY; y++)
                for (int z = -bp.zOffset; z <= bp.zOffset; z++) {
                    ShipPart p = bp.get(x, y, z);
                    if (p != null && p != ShipPart.AIR) {
                        fill(side, x, bp.sizeY - 1 - y, px, p.color);
                        break;
                    }
                }
        ImageIO.write(side, "png", new File(dir, "side_" + tag + ".png"));

        // Vorderansicht (vom Bug aus, vorderster Block)
        BufferedImage front = new BufferedImage(bp.sizeZ * px, bp.sizeY * px, BufferedImage.TYPE_INT_RGB);
        for (int z = -bp.zOffset; z <= bp.zOffset; z++)
            for (int y = 0; y < bp.sizeY; y++)
                for (int x = 0; x < bp.sizeX; x++) {
                    ShipPart p = bp.get(x, y, z);
                    if (p != null && p != ShipPart.AIR) {
                        fill(front, z + bp.zOffset, bp.sizeY - 1 - y, px, shadeByHeight(p.color, bp.sizeX - x, bp.sizeX));
                        break;
                    }
                }
        ImageIO.write(front, "png", new File(dir, "front_" + tag + ".png"));

        // Rückansicht (vom Heck aus)
        BufferedImage back = new BufferedImage(bp.sizeZ * px, bp.sizeY * px, BufferedImage.TYPE_INT_RGB);
        for (int z = -bp.zOffset; z <= bp.zOffset; z++)
            for (int y = 0; y < bp.sizeY; y++)
                for (int x = bp.sizeX - 1; x >= 0; x--) {
                    ShipPart p = bp.get(x, y, z);
                    if (p != null && p != ShipPart.AIR) {
                        fill(back, z + bp.zOffset, bp.sizeY - 1 - y, px, shadeByHeight(p.color, x, bp.sizeX));
                        break;
                    }
                }
        ImageIO.write(back, "png", new File(dir, "back_" + tag + ".png"));

        // Längsschnitt durch die Mittelachse (z = 0) – zeigt Innenräume
        BufferedImage cut = new BufferedImage(bp.sizeX * px, bp.sizeY * px, BufferedImage.TYPE_INT_RGB);
        for (int x = 0; x < bp.sizeX; x++)
            for (int y = 0; y < bp.sizeY; y++) {
                ShipPart p = bp.get(x, y, 0);
                int c = p == null ? 0x101018 : p == ShipPart.AIR ? 0x303040 : p.color;
                fill(cut, x, bp.sizeY - 1 - y, px, c);
            }
        ImageIO.write(cut, "png", new File(dir, "cut_" + tag + ".png"));

        // Grundrisse aller Turbolift-Decks (Schnitt 1 Block über dem Boden)
        for (EnterpriseBlueprint.LiftStop stop : bp.liftStops) {
            int y = stop.pos().getY();
            BufferedImage plan = new BufferedImage(bp.sizeX * px, bp.sizeZ * px, BufferedImage.TYPE_INT_RGB);
            for (int x = 0; x < bp.sizeX; x++)
                for (int z = -bp.zOffset; z <= bp.zOffset; z++) {
                    ShipPart p = bp.get(x, y, z);
                    int c = p == null ? 0x101018 : p == ShipPart.AIR ? 0x404050 : p.color;
                    fill(plan, x, z + bp.zOffset, px, c);
                }
            String deck = stop.name().replaceAll("[^A-Za-z0-9]+", "_");
            ImageIO.write(plan, "png", new File(dir, "plan_" + tag + "_" + deck + ".png"));
        }
    }

    private static int shadeByHeight(int rgb, int y, int h) {
        double f = 0.55 + 0.45 * y / h;
        int r = (int) (((rgb >> 16) & 255) * f), g = (int) (((rgb >> 8) & 255) * f), b = (int) ((rgb & 255) * f);
        return (r << 16) | (g << 8) | b;
    }

    private static void fill(BufferedImage img, int x, int y, int px, int rgb) {
        for (int i = 0; i < px; i++)
            for (int j = 0; j < px; j++)
                img.setRGB(x * px + i, y * px + j, rgb);
    }
}

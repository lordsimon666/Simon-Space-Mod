package de.simon.spacemod.ship;

import de.simon.spacemod.ModBlocks;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CrossCollisionBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.properties.SlabType;
import de.simon.spacemod.block.VariantFacingBlock;
import de.simon.spacemod.block.BiobedBlock;
import de.simon.spacemod.block.TurboliftBlock;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/** Übersetzt Bauplan-Zellen in echte Minecraft-Blockzustände (Bug zeigt nach Norden). */
public final class ShipPalette {
    private ShipPalette() {}

    public static BlockState toState(int cellCode) {
        ShipPart part = EnterpriseBlueprint.part(cellCode);
        Direction facing = EnterpriseBlueprint.facing(cellCode);
        BlockState st = switch (part) {
            case AIR -> Blocks.AIR.defaultBlockState();
            case HULL_PLATING -> ModBlocks.HULL_PLATING.get().defaultBlockState();
            case HULL_PANEL -> ModBlocks.HULL_PANEL.get().defaultBlockState();
            case HULL_DARK -> ModBlocks.HULL_DARK.get().defaultBlockState();
            case HULL_WINDOW -> ModBlocks.HULL_WINDOW.get().defaultBlockState();
            case HULL_MARKING -> ModBlocks.HULL_MARKING.get().defaultBlockState();
            case DEFLECTOR_DISH -> ModBlocks.DEFLECTOR_DISH.get().defaultBlockState();
            case BUSSARD_COLLECTOR -> ModBlocks.BUSSARD_COLLECTOR.get().defaultBlockState();
            case NACELLE_GRILLE -> ModBlocks.NACELLE_GRILLE.get().defaultBlockState();
            case DECK_FLOOR -> ModBlocks.DECK_FLOOR.get().defaultBlockState();
            case BRIDGE_DECK -> ModBlocks.BRIDGE_DECK.get().defaultBlockState();
            case CORRIDOR_WALL -> ModBlocks.CORRIDOR_WALL.get().defaultBlockState();
            case CEILING_LIGHT -> ModBlocks.CEILING_LIGHT.get().defaultBlockState().setValue(de.simon.spacemod.block.CeilingLightBlock.LIT, true);
            case CEILING_PANEL -> ModBlocks.CEILING_PANEL.get().defaultBlockState();
            case BRIDGE_CONSOLE -> ModBlocks.BRIDGE_CONSOLE.get().defaultBlockState();
            case ENGINEERING_CONSOLE -> ModBlocks.ENGINEERING_CONSOLE.get().defaultBlockState();
            case LCARS_PANEL -> ModBlocks.LCARS_PANEL.get().defaultBlockState();
            case VIEWSCREEN -> ModBlocks.VIEWSCREEN.get().defaultBlockState();
            case CAPTAINS_CHAIR -> ModBlocks.CAPTAINS_CHAIR.get().defaultBlockState();
            case WARP_CORE -> ModBlocks.WARP_CORE.get().defaultBlockState();
            case PLASMA_CONDUIT -> ModBlocks.PLASMA_CONDUIT.get().defaultBlockState();
            case TRANSPORTER_PAD -> ModBlocks.TRANSPORTER_PAD.get().defaultBlockState();
            case TURBOLIFT -> lift(ModBlocks.TURBOLIFT.get().defaultBlockState(), cellCode);
            case LADDER -> Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, facing);
            case HULL_PLATING_SLAB -> slab(ModBlocks.HULL_PLATING_SLAB.get().defaultBlockState(), cellCode);
            case HULL_MARKING_SLAB -> slab(ModBlocks.HULL_MARKING_SLAB.get().defaultBlockState(), cellCode);
            case STEP_SLAB -> slab(ModBlocks.STEP_SLAB.get().defaultBlockState(), cellCode);
            case HULL_PENNANT -> ModBlocks.HULL_PENNANT.get().defaultBlockState();
            case NAV_LIGHT_RED -> ModBlocks.NAV_LIGHT_RED.get().defaultBlockState();
            case NAV_LIGHT_GREEN -> ModBlocks.NAV_LIGHT_GREEN.get().defaultBlockState();
            case BRIDGE_WALL -> ModBlocks.BRIDGE_WALL.get().defaultBlockState();
            case BRIDGE_TRIM -> ModBlocks.BRIDGE_TRIM.get().defaultBlockState();
            case BRIDGE_STATION -> ModBlocks.BRIDGE_STATION.get().defaultBlockState()
                .setValue(VariantFacingBlock.VARIANT, EnterpriseBlueprint.variant(cellCode));
            case BRIDGE_MONITOR -> ModBlocks.BRIDGE_MONITOR.get().defaultBlockState()
                .setValue(VariantFacingBlock.VARIANT, EnterpriseBlueprint.variant(cellCode));
            case STATION_CHAIR -> ModBlocks.STATION_CHAIR.get().defaultBlockState();
            case HELM_CONSOLE -> ModBlocks.HELM_CONSOLE.get().defaultBlockState();
            case TURBOLIFT_DOOR -> ModBlocks.TURBOLIFT_DOOR.get().defaultBlockState();
            case TURBOLIFT_FRAME -> ModBlocks.TURBOLIFT_FRAME.get().defaultBlockState();
            case TRANSPORTER_WALL -> ModBlocks.TRANSPORTER_WALL.get().defaultBlockState();
            case TRANSPORTER_PLATFORM -> ModBlocks.TRANSPORTER_PLATFORM.get().defaultBlockState();
            case TRANSPORTER_ENERGIZER -> ModBlocks.TRANSPORTER_ENERGIZER.get().defaultBlockState();
            case TRANSPORTER_PILLAR -> ModBlocks.TRANSPORTER_PILLAR.get().defaultBlockState();
            case TRANSPORTER_EMITTER -> ModBlocks.TRANSPORTER_EMITTER.get().defaultBlockState();
            case TRANSPORTER_CONSOLE -> ModBlocks.TRANSPORTER_CONSOLE.get().defaultBlockState();
            case CONFERENCE_TABLE -> ModBlocks.CONFERENCE_TABLE.get().defaultBlockState();
            case TABLE_VIEWER -> ModBlocks.TABLE_VIEWER.get().defaultBlockState();
            case BIOBED -> ModBlocks.BIOBED.get().defaultBlockState().setValue(BiobedBlock.HEAD, EnterpriseBlueprint.variant(cellCode) == 1);
            case BIOBED_MONITOR -> ModBlocks.BIOBED_MONITOR.get().defaultBlockState();
            case EXAM_TABLE -> ModBlocks.EXAM_TABLE.get().defaultBlockState();
            case MEDICAL_CABINET -> ModBlocks.MEDICAL_CABINET.get().defaultBlockState();
            case SICKBAY_WALL -> ModBlocks.SICKBAY_WALL.get().defaultBlockState();
            case SICKBAY_FLOOR -> ModBlocks.SICKBAY_FLOOR.get().defaultBlockState();
            case CREW_BED -> ModBlocks.CREW_BED.get().defaultBlockState();
            case CREW_DESK -> ModBlocks.CREW_DESK.get().defaultBlockState();
            case CARGO_CONTAINER -> ModBlocks.CARGO_CONTAINER.get().defaultBlockState();
            case MESS_TABLE -> ModBlocks.MESS_TABLE.get().defaultBlockState();
            case BRIDGE_RAILING -> bars(ModBlocks.BRIDGE_RAILING.get().defaultBlockState(), cellCode);
            case BRIDGE_RAILING_GRAY -> bars(ModBlocks.BRIDGE_RAILING_GRAY.get().defaultBlockState(), cellCode);
            case BRIDGE_TRIM_BLUE -> ModBlocks.BRIDGE_TRIM_BLUE.get().defaultBlockState();
            case DEFLECTOR_DISH_BLUE -> ModBlocks.DEFLECTOR_DISH_BLUE.get().defaultBlockState();
            case BUSSARD_RED -> ModBlocks.BUSSARD_RED.get().defaultBlockState();
            case HULL_AZTEC -> ModBlocks.HULL_AZTEC.get().defaultBlockState();
            case TORPEDO_LAUNCHER -> ModBlocks.TORPEDO_LAUNCHER.get().defaultBlockState();
            case IMPULSE_ENGINE -> ModBlocks.IMPULSE_ENGINE.get().defaultBlockState();
            case HULL_TEAL -> ModBlocks.HULL_TEAL.get().defaultBlockState();
            case BUSSARD_BLUE -> ModBlocks.BUSSARD_BLUE.get().defaultBlockState();
            case BRIDGE_RAILING_WOOD -> bars(ModBlocks.BRIDGE_RAILING_WOOD.get().defaultBlockState(), cellCode);
            case BRIDGE_TRIM_CREAM -> ModBlocks.BRIDGE_TRIM_CREAM.get().defaultBlockState();
            case BRIDGE_CARPET_TEAL -> ModBlocks.BRIDGE_CARPET_TEAL.get().defaultBlockState();
            case DECK_PLATE -> ModBlocks.DECK_PLATE.get().defaultBlockState();
            case BRIDGE_WALL_DARK -> ModBlocks.BRIDGE_WALL_DARK.get().defaultBlockState();
            case EXCELSIOR_CONSOLE -> ModBlocks.EXCELSIOR_CONSOLE.get().defaultBlockState()
                .setValue(VariantFacingBlock.VARIANT, EnterpriseBlueprint.variant(cellCode));
            case EXCELSIOR_DISPLAY -> ModBlocks.EXCELSIOR_DISPLAY.get().defaultBlockState()
                .setValue(VariantFacingBlock.VARIANT, EnterpriseBlueprint.variant(cellCode));
            case LIGHT_PILLAR -> ModBlocks.LIGHT_PILLAR.get().defaultBlockState();
            case CAPTAINS_CHAIR_TEAL -> ModBlocks.CAPTAINS_CHAIR_TEAL.get().defaultBlockState();
            case STATION_CHAIR_TEAL -> ModBlocks.STATION_CHAIR_TEAL.get().defaultBlockState();
            case TURBOLIFT_DOOR_TEAL -> ModBlocks.TURBOLIFT_DOOR_TEAL.get().defaultBlockState();
            case HELM_CONSOLE_GRAY -> ModBlocks.HELM_CONSOLE_GRAY.get().defaultBlockState();
            case TNG_CARPET -> ModBlocks.TNG_CARPET.get().defaultBlockState();
            case TNG_WALL -> ModBlocks.TNG_WALL.get().defaultBlockState();
            case TNG_CONSOLE -> ModBlocks.TNG_CONSOLE.get().defaultBlockState();
            case TNG_CHAIR -> ModBlocks.TNG_CHAIR.get().defaultBlockState();
            case CAPTAINS_CHAIR_TNG -> ModBlocks.CAPTAINS_CHAIR_TNG.get().defaultBlockState();
            case WARP_CORE_TNG -> ModBlocks.WARP_CORE_TNG.get().defaultBlockState();
            case MSD_DISPLAY -> ModBlocks.MSD_DISPLAY.get().defaultBlockState()
                .setValue(VariantFacingBlock.VARIANT, EnterpriseBlueprint.variant(cellCode));
            case SITUATION_TABLE -> ModBlocks.SITUATION_TABLE.get().defaultBlockState();
            case HOLODECK_GRID -> ModBlocks.HOLODECK_GRID.get().defaultBlockState();
            case FORCE_FIELD -> ModBlocks.FORCE_FIELD.get().defaultBlockState();
            case STAR_MAP -> ModBlocks.STAR_MAP.get().defaultBlockState();
            case BAR_COUNTER -> ModBlocks.BAR_COUNTER.get().defaultBlockState();
            case VIEWPORT -> Blocks.GLASS.defaultBlockState();
            case GRASS -> Blocks.MOSS_BLOCK.defaultBlockState();
            case FLOWER -> switch (EnterpriseBlueprint.variant(cellCode)) {
                case 0 -> Blocks.POPPY.defaultBlockState();
                case 1 -> Blocks.DANDELION.defaultBlockState();
                case 2 -> Blocks.BLUE_ORCHID.defaultBlockState();
                default -> Blocks.FERN.defaultBlockState();
            };
            case LEAVES -> Blocks.AZALEA_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
            case TREE_LOG -> Blocks.BIRCH_LOG.defaultBlockState();
            case ENT_WALL -> ModBlocks.ENT_WALL.get().defaultBlockState();
            case ENT_CONSOLE -> ModBlocks.ENT_CONSOLE.get().defaultBlockState();
            case ENT_CHAIR -> ModBlocks.ENT_CHAIR.get().defaultBlockState();
            case CAPTAINS_CHAIR_NX -> ModBlocks.CAPTAINS_CHAIR_NX.get().defaultBlockState();
            case DECON_LIGHT -> ModBlocks.DECON_LIGHT.get().defaultBlockState();
            case TURBOLIFT_NX -> lift(ModBlocks.TURBOLIFT_NX.get().defaultBlockState(), cellCode);
            case TURBOLIFT_REFIT -> lift(ModBlocks.TURBOLIFT_REFIT.get().defaultBlockState(), cellCode);
            case TURBOLIFT_EXCELSIOR -> lift(ModBlocks.TURBOLIFT_EXCELSIOR.get().defaultBlockState(), cellCode);
            case TURBOLIFT_TNG -> lift(ModBlocks.TURBOLIFT_TNG.get().defaultBlockState(), cellCode);
            case TURBOLIFT_DOOR_NX -> ModBlocks.TURBOLIFT_DOOR_NX.get().defaultBlockState();
            case TURBOLIFT_DOOR_REFIT -> ModBlocks.TURBOLIFT_DOOR_REFIT.get().defaultBlockState();
            case TURBOLIFT_DOOR_TNG -> ModBlocks.TURBOLIFT_DOOR_TNG.get().defaultBlockState();
            case PHASER_STRIP -> ModBlocks.PHASER_STRIP.get().defaultBlockState();
            case DEFLECTOR_RING -> ModBlocks.DEFLECTOR_RING.get().defaultBlockState();
            case DEFLECTOR_VIOLET -> ModBlocks.DEFLECTOR_VIOLET.get().defaultBlockState();
            case PHASE_CANNON -> ModBlocks.PHASE_CANNON.get().defaultBlockState();
            case WARP_CORE_NX -> ModBlocks.WARP_CORE_NX.get().defaultBlockState();
            case WARP_CORE_E -> ModBlocks.WARP_CORE_E.get().defaultBlockState();
            case RIFLE_RACK -> ModBlocks.RIFLE_RACK.get().defaultBlockState().setValue(de.simon.spacemod.block.RifleRackBlock.HALF,
                EnterpriseBlueprint.variant(cellCode) == 1 ? DoubleBlockHalf.UPPER : DoubleBlockHalf.LOWER);
            case PHASER_HOLDER -> ModBlocks.PHASER_HOLDER.get().defaultBlockState();
            case CHARGING_STATION -> ModBlocks.CHARGING_STATION.get().defaultBlockState();
            case DUAL_WALL -> ModBlocks.DUAL_WALL.get().defaultBlockState()
                .setValue(de.simon.spacemod.block.DualWallBlock.FRONT, de.simon.spacemod.block.DualMaterial.values()[EnterpriseBlueprint.dualA(cellCode)])
                .setValue(de.simon.spacemod.block.DualWallBlock.BACK, de.simon.spacemod.block.DualMaterial.values()[EnterpriseBlueprint.dualB(cellCode)])
                .setValue(de.simon.spacemod.block.DualWallBlock.CORNER, EnterpriseBlueprint.variant(cellCode) == 1);
            case DUAL_FLOOR -> ModBlocks.DUAL_FLOOR.get().defaultBlockState()
                .setValue(de.simon.spacemod.block.DualFloorBlock.TOP, de.simon.spacemod.block.DualMaterial.values()[EnterpriseBlueprint.dualA(cellCode)])
                .setValue(de.simon.spacemod.block.DualFloorBlock.BOTTOM, de.simon.spacemod.block.DualMaterial.values()[EnterpriseBlueprint.dualB(cellCode)]);
            case DOOR_NX -> door("nx", cellCode);
            case DOOR_TOS -> door("tos", cellCode);
            case DOOR_REFIT -> door("refit", cellCode);
            case DOOR_TNG -> door("tng", cellCode);
            case QUANTUM_LAUNCHER -> ModBlocks.QUANTUM_LAUNCHER.get().defaultBlockState();
            case PHASER_BANK -> ModBlocks.PHASER_BANK.get().defaultBlockState();
            case PHASER_EMITTER -> ModBlocks.PHASER_EMITTER.get().defaultBlockState();
            case WEAPONS_CONSOLE -> ModBlocks.WEAPONS_CONSOLE.get().defaultBlockState()
                .setValue(VariantFacingBlock.VARIANT, EnterpriseBlueprint.variant(cellCode));
            case IRON_BARS -> Blocks.IRON_BARS.defaultBlockState()
                .setValue(CrossCollisionBlock.NORTH, EnterpriseBlueprint.bar(cellCode, Direction.NORTH))
                .setValue(CrossCollisionBlock.EAST, EnterpriseBlueprint.bar(cellCode, Direction.EAST))
                .setValue(CrossCollisionBlock.SOUTH, EnterpriseBlueprint.bar(cellCode, Direction.SOUTH))
                .setValue(CrossCollisionBlock.WEST, EnterpriseBlueprint.bar(cellCode, Direction.WEST));
        };
        if (st.hasProperty(BlockStateProperties.HORIZONTAL_FACING) && part != ShipPart.LADDER) {
            st = st.setValue(BlockStateProperties.HORIZONTAL_FACING, facing);
        }
        return st;
    }

    /** Tür 2 x 2: Variante = Teil, Bits 12-13 = Art. */
    private static BlockState door(String era, int code) {
        var kind = de.simon.spacemod.block.StarshipDoorBlock.Kind.values()[Math.min(2, EnterpriseBlueprint.doorKind(code))];
        return ModBlocks.door(era, kind).defaultBlockState().setValue(de.simon.spacemod.block.StarshipDoorBlock.PART, EnterpriseBlueprint.variant(code));
    }

    /** Turbolift: Variante 1 = obere Hälfte (Tür). */
    private static BlockState lift(BlockState st, int code) {
        return st.setValue(TurboliftBlock.HALF, EnterpriseBlueprint.variant(code) == 1 ? DoubleBlockHalf.UPPER : DoubleBlockHalf.LOWER);
    }

    private static BlockState slab(BlockState st, int code) {
        return st.setValue(SlabBlock.TYPE, EnterpriseBlueprint.variant(code) == 1 ? SlabType.TOP : SlabType.BOTTOM);
    }

    private static BlockState bars(BlockState st, int code) {
        return st
            .setValue(CrossCollisionBlock.NORTH, EnterpriseBlueprint.bar(code, Direction.NORTH))
            .setValue(CrossCollisionBlock.EAST, EnterpriseBlueprint.bar(code, Direction.EAST))
            .setValue(CrossCollisionBlock.SOUTH, EnterpriseBlueprint.bar(code, Direction.SOUTH))
            .setValue(CrossCollisionBlock.WEST, EnterpriseBlueprint.bar(code, Direction.WEST));
    }
}

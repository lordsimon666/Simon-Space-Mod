package de.simon.spacemod.ship;

import de.simon.spacemod.ModBlocks;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CrossCollisionBlock;
import net.minecraft.world.level.block.LadderBlock;
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
            case CEILING_LIGHT -> ModBlocks.CEILING_LIGHT.get().defaultBlockState();
            case BRIDGE_CONSOLE -> ModBlocks.BRIDGE_CONSOLE.get().defaultBlockState();
            case ENGINEERING_CONSOLE -> ModBlocks.ENGINEERING_CONSOLE.get().defaultBlockState();
            case LCARS_PANEL -> ModBlocks.LCARS_PANEL.get().defaultBlockState();
            case VIEWSCREEN -> ModBlocks.VIEWSCREEN.get().defaultBlockState();
            case CAPTAINS_CHAIR -> ModBlocks.CAPTAINS_CHAIR.get().defaultBlockState();
            case WARP_CORE -> ModBlocks.WARP_CORE.get().defaultBlockState();
            case PLASMA_CONDUIT -> ModBlocks.PLASMA_CONDUIT.get().defaultBlockState();
            case TRANSPORTER_PAD -> ModBlocks.TRANSPORTER_PAD.get().defaultBlockState();
            case TURBOLIFT -> ModBlocks.TURBOLIFT.get().defaultBlockState();
            case LADDER -> Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, facing);
            case IRON_BARS -> Blocks.IRON_BARS.defaultBlockState()
                .setValue(CrossCollisionBlock.NORTH, EnterpriseBlueprint.bar(cellCode, Direction.NORTH))
                .setValue(CrossCollisionBlock.EAST, EnterpriseBlueprint.bar(cellCode, Direction.EAST))
                .setValue(CrossCollisionBlock.SOUTH, EnterpriseBlueprint.bar(cellCode, Direction.SOUTH))
                .setValue(CrossCollisionBlock.WEST, EnterpriseBlueprint.bar(cellCode, Direction.WEST));
        };
        if (st.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
            st = st.setValue(BlockStateProperties.HORIZONTAL_FACING, facing);
        }
        return st;
    }
}

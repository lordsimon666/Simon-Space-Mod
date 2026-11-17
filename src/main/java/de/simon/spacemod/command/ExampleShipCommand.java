package de.simon.spacemod.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import de.simon.spacemod.ModBlocks;
import de.simon.spacemod.block.AirlockBlock;
import de.simon.spacemod.block.entity.ReactorControllerBlockEntity;
import de.simon.spacemod.display.DisplayBlockEntity;
import de.simon.spacemod.flight.InertialDamperBlock;
import de.simon.spacemod.power.BatteryBlock;
import de.simon.spacemod.power.BatteryBlockEntity;
import de.simon.spacemod.ship.ExampleShips;
import de.simon.spacemod.ship.ExampleShips.Cell;
import de.simon.spacemod.ship.ExampleShips.Part;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.RegisterCommandsEvent;
import org.jspecify.annotations.Nullable;

/**
 * {@code /simonship spawn <miner|reactor_small|reactor_large|warp5|warp9> [Position] [north|east|south|west]}:
 * setzt ein abflugbereites Beispielschiff (siehe {@link ExampleShips}). Ohne Position entsteht es vor dem
 * Spieler auf dem Boden, ohne Richtung zeigt der Bug in Blickrichtung. Reaktoren sind voll mit Uran und
 * geladen, Batterien voll, der Flugbildschirm zeigt das Bild der Bugkamera.
 */
public final class ExampleShipCommand {
    private ExampleShipCommand() {}

    public static void onRegisterCommands(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        var spawn = Commands.literal("spawn");
        for (ExampleShips.Type type : ExampleShips.Type.values()) {
            var pos = Commands.argument("pos", BlockPosArgument.blockPos())
                .executes(ctx -> spawn(ctx, type, BlockPosArgument.getBlockPos(ctx, "pos"), null));
            for (Direction dir : Direction.Plane.HORIZONTAL)
                pos.then(Commands.literal(dir.getSerializedName())
                    .executes(ctx -> spawn(ctx, type, BlockPosArgument.getBlockPos(ctx, "pos"), dir)));
            spawn.then(Commands.literal(type.id).executes(ctx -> spawn(ctx, type, null, null)).then(pos));
        }
        dispatcher.register(Commands.literal("simonship").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS)).then(spawn));
    }

    private static int spawn(CommandContext<CommandSourceStack> ctx, ExampleShips.Type type, @Nullable BlockPos pos, @Nullable Direction bow) {
        CommandSourceStack source = ctx.getSource();
        ServerLevel level = source.getLevel();
        Direction look = Direction.fromYRot(source.getRotation().y);
        Direction fwd = bow != null ? bow : look;
        ExampleShips.Plan plan = ExampleShips.plan(type);
        BlockPos origin = pos != null ? pos : BlockPos.containing(source.getPosition()).relative(look, 4);
        Rotation rot = rotation(fwd);
        // Umriss in der Welt
        BlockPos lo = null, hi = null;
        for (BlockPos p : plan.cells().keySet()) {
            BlockPos w = origin.offset(p.rotate(rot));
            lo = lo == null ? w : new BlockPos(Math.min(lo.getX(), w.getX()), Math.min(lo.getY(), w.getY()), Math.min(lo.getZ(), w.getZ()));
            hi = hi == null ? w : new BlockPos(Math.max(hi.getX(), w.getX()), Math.max(hi.getY(), w.getY()), Math.max(hi.getZ(), w.getZ()));
        }
        if (lo == null) return 0;
        if (lo.getY() < level.getMinY() || hi.getY() > level.getMaxY()) {
            source.sendFailure(Component.translatable("command.simonspacemod.simonship.too_high"));
            return 0;
        }
        if (level.dimension() == de.simon.spacemod.space.SpaceTravel.SPACE)
            de.simon.spacemod.space.AsteroidFeature.clearAround(level, lo, hi, 20);
        // Platz schaffen: Umriss plus ein Block Rand rundherum (auch darunter), damit nichts Fremdes am Schiff
        // hängt – das Schiff steht dann einen Block über dem Grund, die Schleuse ist eine Stufe hoch
        int flags = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
        for (BlockPos p : BlockPos.betweenClosed(lo.offset(-1, -1, -1), hi.offset(1, 1, 1))) {
            if (level.getBlockEntity(p) != null) level.removeBlockEntity(p);
            if (!level.getBlockState(p).isAir()) level.setBlock(p, Blocks.AIR.defaultBlockState(), flags | Block.UPDATE_SUPPRESS_DROPS);
        }
        // erst volle Blöcke, dann alles, was an ihnen hängt
        List<Map.Entry<BlockPos, Cell>> later = new ArrayList<>();
        for (var e : plan.cells().entrySet()) {
            if (e.getValue().part() == Part.AIR) continue;
            BlockState st = state(e.getValue());
            if (st.isCollisionShapeFullBlock(level, BlockPos.ZERO)) level.setBlock(origin.offset(e.getKey().rotate(rot)), st.rotate(rot), flags);
            else later.add(e);
        }
        for (var e : later) level.setBlock(origin.offset(e.getKey().rotate(rot)), state(e.getValue()).rotate(rot), flags);
        // Inhalte: Reaktoren voll, Batterien voll, Bildschirme auf die Bugkamera
        BlockPos cam = plan.camera() == null ? null : origin.offset(plan.camera().rotate(rot));
        for (var e : plan.cells().entrySet()) {
            BlockPos w = origin.offset(e.getKey().rotate(rot));
            switch (e.getValue().part()) {
                case REACTOR_CONTROLLER -> { if (level.getBlockEntity(w) instanceof ReactorControllerBlockEntity r) r.fillCompletely(); }
                case BATTERY -> {
                    if (level.getBlockEntity(w) instanceof BatteryBlockEntity b) b.set(BatteryBlockEntity.CAPACITY);
                    level.setBlock(w, level.getBlockState(w).setValue(BatteryBlock.CHARGE, BatteryBlock.charge(BatteryBlockEntity.CAPACITY)), flags);
                }
                case DISPLAY -> { if (cam != null && level.getBlockEntity(w) instanceof DisplayBlockEntity d) d.set(cam, true); }
                default -> {}
            }
        }
        source.sendSuccess(() -> Component.translatable("command.simonspacemod.simonship.done",
            Component.translatable("command.simonspacemod.simonship." + type.id), origin.toShortString()), true);
        return 1;
    }

    /** Drehung vom Bauplan (Bug = Osten) zur gewünschten Bugrichtung. */
    private static Rotation rotation(Direction fwd) {
        return switch (fwd) {
            case SOUTH -> Rotation.CLOCKWISE_90;
            case WEST -> Rotation.CLOCKWISE_180;
            case NORTH -> Rotation.COUNTERCLOCKWISE_90;
            default -> Rotation.NONE;
        };
    }

    /** Blockzustand im Bauplan-System (Bug = Osten). */
    private static BlockState state(Cell c) {
        Direction f = c.facing();
        return switch (c.part()) {
            case AIR -> Blocks.AIR.defaultBlockState();
            case HULL -> ModBlocks.HULL_PLATING.get().defaultBlockState();
            case PANEL -> ModBlocks.HULL_PANEL.get().defaultBlockState();
            case BOTTOM -> ModBlocks.HULL_DARK.get().defaultBlockState();
            case WINDOW -> ModBlocks.HULL_WINDOW.get().defaultBlockState();
            case DECK -> ModBlocks.DECK_PLATE.get().defaultBlockState();
            case ENGINE -> ModBlocks.IMPULSE_ENGINE.get().defaultBlockState();
            case HELM -> horizontal(ModBlocks.SHIP_HELM.get(), f);
            case DAMPER -> horizontal(ModBlocks.INERTIAL_DAMPER.get(), f).setValue(InertialDamperBlock.LEVEL, c.value());
            case REACTOR_CASING -> ModBlocks.REACTOR_CASING.get().defaultBlockState();
            case REACTOR_GLASS -> ModBlocks.REACTOR_GLASS.get().defaultBlockState();
            case REACTOR_CONTROLLER -> horizontal(ModBlocks.REACTOR_CONTROLLER.get(), f);
            case WARP_CORE -> ModBlocks.WARP_CORE.get().defaultBlockState();
            case WARP_CORE_F -> ModBlocks.WARP_CORE_F.get().defaultBlockState();
            case BUSSARD -> ModBlocks.BUSSARD_RED.get().defaultBlockState();
            case GRILLE -> ModBlocks.NACELLE_GRILLE.get().defaultBlockState();
            case NAV_RED -> ModBlocks.NAV_LIGHT_RED.get().defaultBlockState();
            case NAV_GREEN -> ModBlocks.NAV_LIGHT_GREEN.get().defaultBlockState();
            case LASER -> ModBlocks.MINING_LASER.get().defaultBlockState();
            case FOCUS -> ModBlocks.LASER_FOCUS.get().defaultBlockState();
            case LASER_CONTROL -> horizontal(ModBlocks.MINING_LASER_CONTROL.get(), f);
            case CHEST -> Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, f);
            case CAMERA -> ModBlocks.CAMERA.get().defaultBlockState().setValue(DirectionalBlock.FACING, f);
            case DISPLAY -> horizontal(ModBlocks.DISPLAY.get(), f);
            case BATTERY -> ModBlocks.BATTERY.get().defaultBlockState();
            case AIRLOCK -> horizontal(ModBlocks.AIRLOCK_SMALL.get(), f).setValue(AirlockBlock.PART, c.value());
        };
    }

    private static BlockState horizontal(Block b, Direction f) {
        return b.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, f);
    }
}

package de.simon.spacemod.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import de.simon.spacemod.ship.EnterpriseBlueprint;
import de.simon.spacemod.ship.ShipBuildManager;
import java.util.function.ToDoubleFunction;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.RegisterCommandsEvent;
import org.jspecify.annotations.Nullable;

/**
 * {@code /enterprise spawn [full|compact|mini|scale <faktor>] [position] [north|east|south|west]}
 *
 * <ul>
 *   <li>{@code full}    – Originalgröße 1:1 (289 x 127 x ~72 Blöcke)</li>
 *   <li>{@code compact} – Hülle 1:2 (Standard); Brücke, Maschinenraum, Transporterraum bleiben 1:1</li>
 *   <li>{@code mini}    – Hülle 1:2,5; wichtige Räume bleiben 1:1</li>
 *   <li>{@code scale}   – freier Faktor zwischen 0,4 und 1,0</li>
 * </ul>
 * Die Position ist die Mitte des Schiffs am tiefsten Punkt. Die Richtung gibt an, wohin der Bug zeigt.
 */
public final class EnterpriseCommand {
    public static final double FULL = 1.0, COMPACT = 0.5, MINI = 0.4;

    private EnterpriseCommand() {}

    public static void onRegisterCommands(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        var spawn = Commands.literal("spawn")
            .executes(ctx -> spawn(ctx, COMPACT, null, null));
        spawn.then(withPlacement(Commands.literal("full"), _ -> FULL));
        spawn.then(withPlacement(Commands.literal("compact"), _ -> COMPACT));
        spawn.then(withPlacement(Commands.literal("mini"), _ -> MINI));
        spawn.then(Commands.literal("scale").then(withPlacement(
            Commands.argument("factor", DoubleArgumentType.doubleArg(MINI, FULL)),
            ctx -> DoubleArgumentType.getDouble(ctx, "factor"))));

        dispatcher.register(Commands.literal("enterprise")
            .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
            .then(spawn));
    }

    /** Hängt die optionalen Argumente [Position] [Richtung] an. */
    private static <T extends ArgumentBuilder<CommandSourceStack, T>> T withPlacement(
            T node, ToDoubleFunction<CommandContext<CommandSourceStack>> scale) {
        var pos = Commands.argument("pos", BlockPosArgument.blockPos())
            .executes(ctx -> spawn(ctx, scale.applyAsDouble(ctx), BlockPosArgument.getBlockPos(ctx, "pos"), null));
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            pos.then(Commands.literal(dir.getSerializedName())
                .executes(ctx -> spawn(ctx, scale.applyAsDouble(ctx), BlockPosArgument.getBlockPos(ctx, "pos"), dir)));
        }
        return node.executes(ctx -> spawn(ctx, scale.applyAsDouble(ctx), null, null)).then(pos);
    }

    private static int spawn(CommandContext<CommandSourceStack> ctx, double scale, @Nullable BlockPos pos, @Nullable Direction bow) {
        CommandSourceStack source = ctx.getSource();
        ServerLevel level = source.getLevel();
        Vec3 here = source.getPosition();
        Direction look = Direction.fromYRot(source.getRotation().y);

        source.sendSuccess(() -> Component.translatable("command.simonspacemod.enterprise.computing",
            String.format("%.0f%%", scale * 100)), true);

        ShipBuildManager.computeAsync(scale).thenAcceptAsync(bp -> {
            // Ohne Richtung zeigt der Bug zum Spieler; ohne Position entsteht das Schiff vor dem Spieler
            Direction shipBow = bow != null ? bow : look.getOpposite();
            BlockPos origin = pos;
            if (origin == null) {
                BlockPos feet = BlockPos.containing(here);
                origin = feet.relative(look, bp.sizeX / 2 + 12).above(2);
                if (origin.getY() + bp.sizeY > level.getMaxY()) {
                    origin = origin.atY(level.getMaxY() - bp.sizeY);
                }
            }
            if (origin.getY() < level.getMinY() || origin.getY() + bp.sizeY - 1 > level.getMaxY()) {
                source.sendFailure(Component.translatable("command.simonspacemod.enterprise.too_high",
                    bp.sizeY, level.getMinY(), level.getMaxY()));
                return;
            }
            var placement = new ShipBuildManager.Placement(level, origin, shipBow);
            BlockPos bridge = placement.toWorld(bp, bp.bridgeSpawn.getX(), bp.bridgeSpawn.getY(), bp.bridgeSpawn.getZ());
            final BlockPos finalOrigin = origin;
            source.sendSuccess(() -> Component.translatable("command.simonspacemod.enterprise.started",
                bp.sizeX, bp.sizeZ, bp.sizeY, finalOrigin.toShortString()), true);

            ShipBuildManager.start(bp, placement, job -> source.sendSuccess(() -> finishedMessage(job, bridge, bp), true));
        }, source.getServer()).exceptionally(err -> {
            source.sendFailure(Component.literal("Enterprise: " + err.getMessage()));
            return null;
        });
        return 1;
    }

    private static Component finishedMessage(ShipBuildManager.Job job, BlockPos bridge, EnterpriseBlueprint bp) {
        String tp = "/tp @s " + bridge.getX() + " " + bridge.getY() + " " + bridge.getZ();
        Component link = Component.translatable("command.simonspacemod.enterprise.to_bridge")
            .withStyle(style -> style
                .withColor(ChatFormatting.AQUA)
                .withUnderlined(true)
                .withClickEvent(new ClickEvent.RunCommand(tp))
                .withHoverEvent(new HoverEvent.ShowText(Component.literal(tp))));
        return Component.translatable("command.simonspacemod.enterprise.done", job.total()).append(" ").append(link);
    }
}

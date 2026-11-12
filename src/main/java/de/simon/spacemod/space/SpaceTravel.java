package de.simon.spacemod.space;

import de.simon.spacemod.SimonSpaceMod;
import de.simon.spacemod.entity.FlyingShipEntity;
import java.util.UUID;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;

/**
 * Weltraum: eigene Dimension (Bauhöhe bis 1007). Ein fliegendes Schiff, das in der Oberwelt hoch
 * genug steigt, kommt in den Weltraum; sinkt es dort ganz nach unten, kehrt es in die Oberwelt zurück.
 * Wer ohne Schiff aus dem Weltraum fällt, landet mit Sanftem Fall in der Oberwelt.
 */
public final class SpaceTravel {
    public static final ResourceKey<Level> SPACE = ResourceKey.create(Registries.DIMENSION,
        Identifier.fromNamespaceAndPath(SimonSpaceMod.MODID, "space"));
    /** Oberwelt: so hoch über dem Bauhöhen-Limit (plus Schiffsradius) geht es in den Weltraum. */
    public static final int CLIMB = 60;
    /** Weltraum: unter dieser Höhe (Mittelpunkt) geht es zurück in die Oberwelt. */
    public static final int SPACE_FLOOR = 12;

    private SpaceTravel() {}

    public static void init() {
        TickEvent.ServerTickEvent.Post.BUS.addListener(e -> tick(e.server()));
    }

    public static boolean isSpace(Level level) {
        return level.dimension() == SPACE;
    }

    /** Höhe, ab der ein Schiff in der Oberwelt in den Weltraum wechselt. */
    public static double exitHeight(Level overworld, double radius) {
        return overworld.getMaxY() + radius + CLIMB;
    }

    /** Prüft die Flughöhe und wechselt bei Bedarf die Dimension. Gibt das (evtl. neue) Schiff zurück. */
    public static FlyingShipEntity check(FlyingShipEntity ship, double radius, @org.jspecify.annotations.Nullable UUID pilot) {
        if (!(ship.level() instanceof ServerLevel level)) return ship;
        MinecraftServer server = level.getServer();
        ServerLevel target;
        Vec3 pos;
        if (level.dimension() == Level.OVERWORLD && ship.getY() > exitHeight(level, radius)) {
            target = server.getLevel(SPACE);
            pos = new Vec3(ship.getX(), Math.max(48, radius + 24), ship.getZ());
        } else if (isSpace(level) && ship.getY() < SPACE_FLOOR) {
            target = server.overworld();
            pos = new Vec3(ship.getX(), target.getMaxY() + radius + CLIMB - 24, ship.getZ());
        } else return ship;
        if (target == null) return ship;
        Entity moved = ship.teleport(new TeleportTransition(target, pos, Vec3.ZERO, ship.getYRot(), ship.getXRot(), TeleportTransition.DO_NOTHING));
        if (!(moved instanceof FlyingShipEntity ns)) return ship;
        for (Entity p : ns.getPassengers()) {
            if (pilot != null && p.getUUID().equals(pilot) && p instanceof Player pl) ns.setPilot(pl);
            if (p instanceof ServerPlayer sp)
                sp.sendOverlayMessage(Component.translatable(isSpace(target) ? "message.simonspacemod.space.enter" : "message.simonspacemod.space.leave"));
        }
        return ns;
    }

    private static void tick(MinecraftServer server) {
        if (server.getTickCount() % 10 != 0) return;
        ServerLevel space = server.getLevel(SPACE);
        if (space == null) return;
        for (ServerPlayer p : java.util.List.copyOf(space.players())) {
            if (p.isPassenger() || p.getY() > space.getMinY() - 8) continue;
            ServerLevel ow = server.overworld();
            p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 20 * 60));
            p.teleport(new TeleportTransition(ow, new Vec3(p.getX(), ow.getMaxY() + 10, p.getZ()), Vec3.ZERO, p.getYRot(), p.getXRot(),
                TeleportTransition.DO_NOTHING));
        }
        SpaceShips.tick(server, space);
    }
}

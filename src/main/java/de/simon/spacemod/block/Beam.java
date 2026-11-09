package de.simon.spacemod.block;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import java.util.Set;

/** Hilfsfunktionen zum "Beamen" eines Spielers inkl. Effekten. */
public final class Beam {
    private Beam() {}

    public static void teleport(ServerPlayer player, ServerLevel level, double x, double y, double z, float yRot) {
        effects(level, player.getX(), player.getY(), player.getZ());
        player.teleportTo(level, x, y, z, Set.of(), yRot, player.getXRot(), false);
        effects(level, x, y, z);
    }

    private static void effects(ServerLevel level, double x, double y, double z) {
        level.sendParticles(ParticleTypes.END_ROD, x, y + 1, z, 40, 0.3, 0.9, 0.3, 0.02);
        level.playSound(null, x, y, z, SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.6F, 1.8F);
    }
}

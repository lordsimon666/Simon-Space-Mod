package de.simon.spacemod.weapons;

import de.simon.spacemod.ModBlocks;
import de.simon.spacemod.entity.PhaserBeamEntity;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import org.jspecify.annotations.Nullable;

/**
 * Laufende Phaserschüsse: jeder Schuss feuert 2 Sekunden lang einen sichtbaren Strahl (wie ein
 * Leuchtfeuer), folgt dabei dem Ziel und wirkt in Abständen auf das, was er trifft.
 */
public final class BeamShots {
    /** Feuerdauer in Ticks (2 Sekunden). */
    public static final int DURATION = 40;
    /** Abstand der Treffer während des Strahls. */
    public static final int PULSE = 10;

    /** Ein Schuss: {@link #tick()} liefert false, wenn er endet. */
    public abstract static class Shot {
        protected int age;

        protected abstract boolean tick();

        protected void end() {}
    }

    private static final List<Shot> ACTIVE = new ArrayList<>();

    private BeamShots() {}

    public static void init() {
        TickEvent.ServerTickEvent.Post.BUS.addListener(e -> tickAll());
    }

    /** Schuss starten (erster Tick sofort). */
    public static void start(Shot s) {
        if (s.tick()) ACTIVE.add(s);
        else s.end();
    }

    private static void tickAll() {
        if (ACTIVE.isEmpty()) return;
        for (Shot s : List.copyOf(ACTIVE)) {
            boolean go;
            try {
                go = s.tick();
            } catch (RuntimeException ex) {
                go = false;
            }
            if (!go) {
                s.end();
                ACTIVE.remove(s);
            }
        }
    }

    /** Sichtbaren Strahl anlegen. */
    public static @Nullable PhaserBeamEntity beam(ServerLevel level, Vec3 from, Vec3 to, int color, int core, float width, boolean pulse) {
        PhaserBeamEntity b = ModBlocks.PHASER_BEAM.get().create(level, EntitySpawnReason.TRIGGERED);
        if (b == null) return null;
        b.setup(color, core, width, pulse);
        b.aim(from, to);
        level.addFreshEntity(b);
        return b;
    }
}

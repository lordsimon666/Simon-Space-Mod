package de.simon.spacemod.defense;

import de.simon.spacemod.ModBlocks;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;

/**
 * Warpkernbruch: eine riesige Explosion, die eine Kugel (mit unruhigem Rand) aus dem Schiff reißt.
 * Der Radius hängt vom stärksten Kern ab: NX-01 110 Blöcke (die NX-01 ist danach so gut wie weg),
 * Originalserie 120, Next Generation 135, Enterprise-E 150 (reißt ein großes Loch in die E).
 * Wird über mehrere Ticks abgearbeitet (Abschnitt für Abschnitt).
 */
public final class WarpBreach {
    private static final int SECTIONS_PER_TICK = 48;
    private static final List<Job> JOBS = new ArrayList<>();

    private record Job(ServerLevel level, Vec3 center, double radius, long seed, List<long[]> sections) {}

    private WarpBreach() {}

    public static void init() {
        TickEvent.ServerTickEvent.Post.BUS.addListener(e -> tick(e.server()));
    }

    /** Radius je nach stärkstem Warpkern. */
    public static double radius(BlockState core) {
        if (core.is(ModBlocks.WARP_CORE_E.get())) return 150;
        if (core.is(ModBlocks.WARP_CORE_TNG.get())) return 135;
        if (core.is(ModBlocks.WARP_CORE_NX.get())) return 110;
        return 120;
    }

    public static void detonate(ServerLevel level, Vec3 center, double radius) {
        // Blitz, Knall und Druckwelle für alles in der Nähe
        level.playSound(null, BlockPos.containing(center), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.BLOCKS, 16F, 0.5F);
        level.explode(null, center.x, center.y, center.z, 12F, true, Level.ExplosionInteraction.TNT);
        for (Entity e : level.getEntities((Entity) null, new AABB(center, center).inflate(radius * 1.2),
                e -> e instanceof LivingEntity)) {
            double d = e.position().distanceTo(center);
            if (d > radius * 1.2) continue;
            float dmg = (float) (d < radius ? 1000 : 1000 * (radius * 1.2 - d) / (radius * 0.2));
            e.hurtServer(level, level.damageSources().explosion(null, null), dmg);
        }
        // Abschnitte (16er-Würfel) in der Kugel einsammeln
        List<long[]> sections = new ArrayList<>();
        int r = (int) Math.ceil(radius * 1.1);
        int sx0 = (int) Math.floor((center.x - r) / 16), sx1 = (int) Math.floor((center.x + r) / 16);
        int sy0 = Math.max(level.getMinSectionY(), (int) Math.floor((center.y - r) / 16)), sy1 = Math.min(level.getMaxSectionY(), (int) Math.floor((center.y + r) / 16));
        int sz0 = (int) Math.floor((center.z - r) / 16), sz1 = (int) Math.floor((center.z + r) / 16);
        for (int x = sx0; x <= sx1; x++)
            for (int z = sz0; z <= sz1; z++)
                for (int y = sy0; y <= sy1; y++) {
                    double cx = x * 16 + 8 - center.x, cy = y * 16 + 8 - center.y, cz = z * 16 + 8 - center.z;
                    if (Math.sqrt(cx * cx + cy * cy + cz * cz) - 14 > radius * 1.1) continue;
                    sections.add(new long[] {x, y, z});
                }
        // von innen nach außen
        sections.sort((a, b) -> Double.compare(dist2(a, center), dist2(b, center)));
        JOBS.add(new Job(level, center, radius, level.getRandom().nextLong(), sections));
    }

    private static double dist2(long[] s, Vec3 c) {
        double dx = s[0] * 16 + 8 - c.x, dy = s[1] * 16 + 8 - c.y, dz = s[2] * 16 + 8 - c.z;
        return dx * dx + dy * dy + dz * dz;
    }

    /** Unruhiger Rand: Radius schwankt je Richtung um ±10 %. */
    private static double edge(Job j, double dx, double dy, double dz) {
        double len = Math.sqrt(dx * dx + dy * dy + dz * dz) + 1e-9;
        double ux = dx / len, uy = dy / len, uz = dz / len;
        long s = j.seed();
        double n = Math.sin(ux * 5.1 + (s & 7)) * Math.cos(uy * 4.3 + ((s >> 3) & 7)) + Math.sin(uz * 6.7 + ux * 2.0 + ((s >> 6) & 7)) * 0.6;
        return j.radius() * (1 + 0.1 * n / 1.6);
    }

    private static void tick(MinecraftServer server) {
        if (JOBS.isEmpty()) return;
        Job j = JOBS.get(0);
        int done = 0;
        int flags = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS;
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        while (!j.sections().isEmpty() && done < SECTIONS_PER_TICK) {
            long[] s = j.sections().remove(0);
            done++;
            int cx = (int) s[0], cy = (int) s[1], cz = (int) s[2];
            if (!j.level().hasChunk(cx, cz)) continue;
            LevelChunk chunk = j.level().getChunk(cx, cz);
            LevelChunkSection sec = chunk.getSection(j.level().getSectionIndexFromSectionY(cy));
            if (sec.hasOnlyAir()) continue;
            for (int x = 0; x < 16; x++)
                for (int y = 0; y < 16; y++)
                    for (int z = 0; z < 16; z++) {
                        BlockState st = sec.getBlockState(x, y, z);
                        if (st.isAir()) continue;
                        int wx = cx * 16 + x, wy = cy * 16 + y, wz = cz * 16 + z;
                        double dx = wx + 0.5 - j.center().x, dy = wy + 0.5 - j.center().y, dz = wz + 0.5 - j.center().z;
                        double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
                        if (d > edge(j, dx, dy, dz)) continue;
                        m.set(wx, wy, wz);
                        if (st.hasBlockEntity()) j.level().removeBlockEntity(m);
                        j.level().setBlock(m, Blocks.AIR.defaultBlockState(), flags);
                    }
        }
        if (j.sections().isEmpty()) JOBS.remove(0);
    }
}

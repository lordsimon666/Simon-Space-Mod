package de.simon.spacemod.space;

import com.mojang.serialization.Codec;
import de.simon.spacemod.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/**
 * Asteroid im Weltraum: unregelmäßiger Felsbrocken (3 bis 12 Blöcke Radius) aus Stein, Andesit, Tuff,
 * Tiefenschiefer oder Basalt mit Erzen, Uran, Dilithium und im Kern manchmal Antimaterie. Etwa jeder
 * sechste ist ein Eisbrocken mit Deuterium.
 */
public class AsteroidFeature extends Feature<NoneFeatureConfiguration> {
    public AsteroidFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    /** Einmal je Chunk: alle Asteroiden des Rasters, deren Mittelpunkt in diesem Chunk liegt. */
    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> ctx) {
        WorldGenLevel level = ctx.level();
        int x0 = ctx.origin().getX() & ~15, z0 = ctx.origin().getZ() & ~15;
        long seed = level.getSeed();
        boolean any = false;
        int i0 = Math.floorDiv(x0 - AsteroidGrid.JITTER - AsteroidGrid.CELL, AsteroidGrid.CELL), i1 = Math.floorDiv(x0 + 16 + AsteroidGrid.JITTER, AsteroidGrid.CELL) + 1;
        int k0 = Math.floorDiv(z0 - AsteroidGrid.JITTER - AsteroidGrid.CELL, AsteroidGrid.CELL), k1 = Math.floorDiv(z0 + 16 + AsteroidGrid.JITTER, AsteroidGrid.CELL) + 1;
        for (int i = i0; i <= i1; i++)
            for (int k = k0; k <= k1; k++)
                for (int j = 0; AsteroidGrid.BOTTOM + j * AsteroidGrid.CELL <= 960; j++) {
                    BlockPos c = AsteroidGrid.center(seed, i, j, k);
                    if (c == null || c.getX() < x0 || c.getX() > x0 + 15 || c.getZ() < z0 || c.getZ() > z0 + 15) continue;
                    if (level.isOutsideBuildHeight(c) || SpaceShips.nearEnterprise(seed, c)) continue; // 50 Blöcke um die Enterprises frei
                    any |= placeOne(level, RandomSource.create(AsteroidGrid.cellHash(seed, i, j, k)), c);
                }
        return any;
    }

    private boolean placeOne(WorldGenLevel level, RandomSource rnd, BlockPos o) {
        double r = 3 + rnd.nextInt(10);
        boolean ice = rnd.nextInt(6) == 0;
        Block base = ice ? Blocks.PACKED_ICE : switch (rnd.nextInt(5)) {
            case 0 -> Blocks.ANDESITE;
            case 1 -> Blocks.TUFF;
            case 2 -> Blocks.DEEPSLATE;
            case 3 -> Blocks.BASALT;
            default -> Blocks.STONE;
        };
        boolean deep = base == Blocks.DEEPSLATE || base == Blocks.BASALT || base == Blocks.TUFF;
        // Beulen: ein paar zufällige Kugeln um den Mittelpunkt
        int bumps = 3 + rnd.nextInt(4);
        double[][] b = new double[bumps][4];
        for (double[] k : b) {
            k[0] = (rnd.nextDouble() - 0.5) * r;
            k[1] = (rnd.nextDouble() - 0.5) * r * 0.8;
            k[2] = (rnd.nextDouble() - 0.5) * r;
            k[3] = r * (0.45 + rnd.nextDouble() * 0.4);
        }
        int R = (int) Math.ceil(r * 1.3);
        boolean placed = false;
        for (int x = -R; x <= R; x++)
            for (int y = -R; y <= R; y++)
                for (int z = -R; z <= R; z++) {
                    double d = Math.sqrt(x * x + y * y * 1.3 + z * z);
                    boolean in = d <= r * 0.75;
                    for (double[] k : b) {
                        double dx = x - k[0], dy = y - k[1], dz = z - k[2];
                        if (dx * dx + dy * dy + dz * dz <= k[3] * k[3]) { in = true; break; }
                    }
                    if (!in) continue;
                    BlockPos p = o.offset(x, y, z);
                    if (level.isOutsideBuildHeight(p)) continue;
                    double core = d / r; // 0 = Mitte
                    level.setBlock(p, pick(rnd, base, ice, deep, core), Block.UPDATE_CLIENTS);
                    placed = true;
                }
        if (placed) LOGGER.debug("Asteroid bei {} (Radius {})", o, r);
        return placed;
    }

    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();

    private static BlockState pick(RandomSource rnd, Block base, boolean ice, boolean deep, double core) {
        int roll = rnd.nextInt(1000);
        if (ice) {
            if (roll < 90) return ModBlocks.DEUTERIUM_ICE.get().defaultBlockState();
            if (roll < 160) return Blocks.BLUE_ICE.defaultBlockState();
            if (roll < 175 && core < 0.5) return ModBlocks.DILITHIUM_ORE.get().defaultBlockState();
            return base.defaultBlockState();
        }
        if (core < 0.35 && roll < 6) return ModBlocks.ANTIMATTER_CLUSTER.get().defaultBlockState();
        roll = rnd.nextInt(1000);
        Block ore;
        if (roll < 30) ore = deep ? Blocks.DEEPSLATE_COAL_ORE : Blocks.COAL_ORE;
        else if (roll < 55) ore = deep ? Blocks.DEEPSLATE_IRON_ORE : Blocks.IRON_ORE;
        else if (roll < 75) ore = deep ? Blocks.DEEPSLATE_COPPER_ORE : Blocks.COPPER_ORE;
        else if (roll < 85) ore = deep ? Blocks.DEEPSLATE_REDSTONE_ORE : Blocks.REDSTONE_ORE;
        else if (roll < 93) ore = deep ? Blocks.DEEPSLATE_GOLD_ORE : Blocks.GOLD_ORE;
        else if (roll < 99) ore = deep ? Blocks.DEEPSLATE_LAPIS_ORE : Blocks.LAPIS_ORE;
        else if (roll < 107) ore = deep ? ModBlocks.DEEPSLATE_URANIUM_ORE.get() : ModBlocks.URANIUM_ORE.get();
        else if (roll < 113) ore = ModBlocks.DILITHIUM_ORE.get();
        else if (roll < 116) ore = deep ? Blocks.DEEPSLATE_DIAMOND_ORE : Blocks.DIAMOND_ORE;
        else if (roll < 117) ore = deep ? Blocks.DEEPSLATE_EMERALD_ORE : Blocks.EMERALD_ORE;
        else ore = base;
        return ore.defaultBlockState();
    }
}

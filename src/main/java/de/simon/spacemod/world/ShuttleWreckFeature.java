package de.simon.spacemod.world;

import com.mojang.serialization.Codec;
import de.simon.spacemod.ModBlocks;
import de.simon.spacemod.SimonSpaceMod;
import de.simon.spacemod.power.BatteryBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.level.storage.loot.LootTable;

/**
 * Seltenes Shuttle-Wrack an der Oberfläche: zerbeulter Rumpf mit Löchern, 1-4 Impulsantriebe, eine
 * Batterie mit 75-100 % Ladung (genug Strom, um mit der Schiffsteil-Fertigung einen ersten
 * Kernreaktor zu bauen), eine Kiste mit Bauteilen und Uran, Sitz und Steuerkonsole. Wer die Löcher
 * mit Hüllenblöcken flickt, kann das Shuttle wieder fliegen lassen.
 */
public class ShuttleWreckFeature extends Feature<NoneFeatureConfiguration> {
    public static final ResourceKey<LootTable> LOOT = ResourceKey.create(Registries.LOOT_TABLE,
        Identifier.fromNamespaceAndPath(SimonSpaceMod.MODID, "chests/shuttle_wreck"));

    public ShuttleWreckFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> ctx) {
        WorldGenLevel level = ctx.level();
        RandomSource rnd = ctx.random();
        BlockPos o = level.getHeightmapPos(Heightmap.Types.WORLD_SURFACE_WG, ctx.origin());
        // nur auf festem, halbwegs ebenem Boden (nicht im Wasser)
        BlockState ground = level.getBlockState(o.below());
        if (!ground.getFluidState().isEmpty() || !ground.isSolid()) return false;
        int min = o.getY(), max = o.getY();
        for (int[] c : new int[][] {{-5, -3}, {5, -3}, {-5, 3}, {5, 3}}) {
            int y = level.getHeightmapPos(Heightmap.Types.WORLD_SURFACE_WG, o.offset(c[0], 0, c[1])).getY();
            min = Math.min(min, y);
            max = Math.max(max, y);
        }
        if (max - min > 3) return false;
        Direction fwd = Direction.from2DDataValue(rnd.nextInt(4));
        BlockPos base = o.below(); // halb im Boden
        // Rumpf 9 lang, 5 breit, 4 hoch; Kanten abgeschrägt
        for (int x = 0; x < 9; x++)
            for (int y = 0; y < 4; y++)
                for (int z = -2; z <= 2; z++) {
                    boolean shell = x == 0 || x == 8 || y == 0 || y == 3 || Math.abs(z) == 2;
                    boolean corner = Math.abs(z) == 2 && (y == 0 || y == 3);
                    BlockPos p = at(base, fwd, x, y, z);
                    if (corner) continue;
                    if (!shell) {
                        level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                        continue;
                    }
                    // Löcher im Rumpf (nicht im Boden)
                    if (y > 0 && rnd.nextInt(100) < 18) {
                        level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                        continue;
                    }
                    BlockState st = y == 0 ? ModBlocks.DECK_PLATE.get().defaultBlockState()
                        : x == 8 && y == 2 && Math.abs(z) <= 1 ? ModBlocks.HULL_WINDOW.get().defaultBlockState()
                        : y == 3 ? ModBlocks.HULL_PANEL.get().defaultBlockState() : ModBlocks.HULL_PLATING.get().defaultBlockState();
                    level.setBlock(p, st, Block.UPDATE_CLIENTS);
                }
        // 1-4 Impulsantriebe am Heck
        int[][] engines = {{1, -1}, {1, 1}, {2, -1}, {2, 1}};
        int n = 1 + rnd.nextInt(4);
        for (int i = 0; i < n; i++)
            level.setBlock(at(base, fwd, 0, engines[i][0], engines[i][1]), ModBlocks.IMPULSE_ENGINE.get().defaultBlockState(), Block.UPDATE_CLIENTS);
        // Innenleben
        BlockPos battery = at(base, fwd, 6, 1, -1);
        int energy = BatteryBlockEntity.CAPACITY * (75 + rnd.nextInt(26)) / 100;
        level.setBlock(battery, ModBlocks.BATTERY.get().defaultBlockState()
            .setValue(de.simon.spacemod.power.BatteryBlock.CHARGE, de.simon.spacemod.power.BatteryBlock.charge(energy)), Block.UPDATE_CLIENTS);
        if (level.getBlockEntity(battery) instanceof BatteryBlockEntity b) b.set(energy);
        BlockPos chest = at(base, fwd, 6, 1, 1);
        level.setBlock(chest, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, rot(fwd, Direction.WEST)), Block.UPDATE_CLIENTS);
        RandomizableContainer.setBlockEntityLootTable(level, rnd, chest, LOOT);
        level.setBlock(at(base, fwd, 7, 1, 0), ModBlocks.SHIP_HELM.get().defaultBlockState()
            .setValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING, rot(fwd, Direction.WEST)), Block.UPDATE_CLIENTS);
        level.setBlock(at(base, fwd, 5, 1, 0), ModBlocks.STATION_CHAIR.get().defaultBlockState()
            .setValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING, rot(fwd, Direction.WEST)), Block.UPDATE_CLIENTS);
        // Trümmer daneben
        for (int i = 0; i < 2 + rnd.nextInt(3); i++) {
            BlockPos d = level.getHeightmapPos(Heightmap.Types.WORLD_SURFACE_WG, o.offset(rnd.nextInt(13) - 6, 0, rnd.nextInt(11) - 5));
            if (level.getBlockState(d.below()).isSolid() && level.getBlockState(d).isAir())
                level.setBlock(d, ModBlocks.HULL_PLATING.get().defaultBlockState(), Block.UPDATE_CLIENTS);
        }
        return true;
    }

    /** Lokale Lage (x = Länge Richtung Bug, z = quer) in der Welt; das Heck liegt bei base. */
    private static BlockPos at(BlockPos base, Direction fwd, int x, int y, int z) {
        Direction side = fwd.getClockWise();
        return base.relative(fwd, x - 4).relative(side, z).above(y);
    }

    /** Richtung im Shuttle (WEST = zum Heck schauend, also "nach vorn blickend" für Konsolen) in die Welt drehen. */
    private static Direction rot(Direction fwd, Direction local) {
        return local == Direction.WEST ? fwd.getOpposite() : fwd;
    }
}

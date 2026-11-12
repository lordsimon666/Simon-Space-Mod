package de.simon.spacemod.flight;

import de.simon.spacemod.ModBlocks;
import de.simon.spacemod.entity.FlyingShipEntity;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Macht aus der Struktur um eine Steuerkonsole ein fliegendes Schiff: alle zusammenhängenden Blöcke
 * außer natürlichem Gelände (Erde, Stein, Sand, Laub, Pflanzen, Flüssigkeiten ...). Höchstens so
 * viele Blöcke wie die Spielregel {@code simonspacemod:max_ship_blocks} erlaubt, und nur mit
 * geschlossener Hülle: jeder Block, der Außenluft berührt, muss ein Hüllenblock sein.
 */
public final class ShipAssembler {

    private ShipAssembler() {}

    public static boolean isShipBlock(BlockState st) {
        if (st.isAir() || !st.getFluidState().isEmpty() && st.getBlock() instanceof net.minecraft.world.level.block.LiquidBlock) return false;
        if (st.getDestroySpeed(null, BlockPos.ZERO) < 0) return false; // Grundgestein & Co.
        if (st.canBeReplaced()) return false;                              // Gras, Blumen, Schnee ...
        return !(st.is(BlockTags.DIRT) || st.is(BlockTags.BASE_STONE_OVERWORLD) || st.is(BlockTags.BASE_STONE_NETHER)
            || st.is(BlockTags.SAND) || st.is(BlockTags.LEAVES) || st.is(Blocks.GRAVEL) || st.is(BlockTags.ICE)
            || st.is(BlockTags.SNOW));
    }

    /** Darüber wird gar nicht weitergezählt (z. B. wenn die Konsole in einer großen Basis steht). */
    public static final int COUNT_CAP = 200_000;

    /** Hüllenblöcke: dürfen (und müssen) außen liegen. */
    public static boolean isHullBlock(BlockState st) {
        var id = net.minecraftforge.registries.ForgeRegistries.BLOCKS.getKey(st.getBlock());
        if (id == null || !id.getNamespace().equals(de.simon.spacemod.SimonSpaceMod.MODID)) return false;
        String n = id.getPath();
        if (st.getBlock() instanceof de.simon.spacemod.block.StarshipDoorBlock) return true; // Luken
        return n.startsWith("hull") || n.startsWith("deflector") || n.startsWith("bussard") || n.startsWith("nav_light")
            || n.startsWith("phase") || n.startsWith("phaser_bank") || n.startsWith("phaser_emitter") || n.startsWith("phaser_strip")
            || n.equals("impulse_engine") || n.equals("torpedo_launcher") || n.equals("quantum_launcher") || n.equals("nacelle_grille")
            || n.equals("viewport");
    }

    public static void assemble(ServerLevel level, BlockPos helm, Direction forward, ServerPlayer player) {
        int max = level.getGameRules().get(ModBlocks.MAX_SHIP_BLOCKS.get());
        // zusammenhängende Blöcke sammeln (vollständig zählen, damit die Meldung stimmt)
        Set<BlockPos> seen = new HashSet<>();
        List<BlockPos> found = new ArrayList<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        queue.add(helm);
        seen.add(helm);
        while (!queue.isEmpty() && found.size() <= COUNT_CAP) {
            BlockPos p = queue.poll();
            found.add(p);
            for (Direction d : Direction.values()) {
                BlockPos n = p.relative(d);
                if (seen.contains(n) || !level.isLoaded(n)) continue;
                seen.add(n);
                if (isShipBlock(level.getBlockState(n))) queue.add(n.immutable());
            }
        }
        if (found.size() > max) {
            if (found.size() > COUNT_CAP) player.sendSystemMessage(Component.translatable("message.simonspacemod.ship.too_big_huge", COUNT_CAP, max));
            else player.sendSystemMessage(Component.translatable("message.simonspacemod.ship.too_big", found.size(), max, found.size() - max));
            return;
        }
        // geschlossene Hülle: alles, was Außenluft berührt, muss ein Hüllenblock sein
        Set<BlockPos> inShip = new HashSet<>(found);
        int x0 = Integer.MAX_VALUE, y0 = Integer.MAX_VALUE, z0 = Integer.MAX_VALUE, x1 = Integer.MIN_VALUE, y1 = Integer.MIN_VALUE, z1 = Integer.MIN_VALUE;
        for (BlockPos p : found) {
            x0 = Math.min(x0, p.getX()); y0 = Math.min(y0, p.getY()); z0 = Math.min(z0, p.getZ());
            x1 = Math.max(x1, p.getX()); y1 = Math.max(y1, p.getY()); z1 = Math.max(z1, p.getZ());
        }
        x0--; y0--; z0--; x1++; y1++; z1++;
        Set<BlockPos> outside = new HashSet<>();
        ArrayDeque<BlockPos> oq = new ArrayDeque<>();
        BlockPos start = new BlockPos(x0, y0, z0);
        outside.add(start);
        oq.add(start);
        while (!oq.isEmpty()) {
            BlockPos p = oq.poll();
            for (Direction d : Direction.values()) {
                BlockPos n = p.relative(d);
                if (n.getX() < x0 || n.getX() > x1 || n.getY() < y0 || n.getY() > y1 || n.getZ() < z0 || n.getZ() > z1) continue;
                if (inShip.contains(n) || !outside.add(n)) continue;
                oq.add(n);
            }
        }
        int bad = 0;
        BlockPos example = null;
        for (BlockPos p : found) {
            boolean exterior = false;
            for (Direction d : Direction.values()) if (outside.contains(p.relative(d))) { exterior = true; break; }
            if (exterior && !isHullBlock(level.getBlockState(p))) {
                bad++;
                if (example == null) example = p;
            }
        }
        if (bad > 0) {
            player.sendSystemMessage(Component.translatable("message.simonspacemod.ship.hull_open", bad,
                level.getBlockState(example).getBlock().getName(), example.getX(), example.getY(), example.getZ()));
            return;
        }
        int engines = 0;
        for (BlockPos p : found) if (level.getBlockState(p).is(ModBlocks.IMPULSE_ENGINE.get())) engines++;
        if (engines == 0) {
            player.sendSystemMessage(Component.translatable("message.simonspacemod.ship.no_engine"));
            return;
        }
        // Drehpunkt = Block am Schwerpunkt
        double cx = 0, cy = 0, cz = 0;
        for (BlockPos p : found) { cx += p.getX(); cy += p.getY(); cz += p.getZ(); }
        BlockPos pivot = BlockPos.containing(cx / found.size() + 0.5, cy / found.size() + 0.5, cz / found.size() + 0.5);
        List<ShipStructure.Entry> entries = new ArrayList<>();
        AABB box = null;
        for (BlockPos p : found) {
            BlockState st = level.getBlockState(p);
            BlockEntity be = level.getBlockEntity(p);
            Optional<net.minecraft.nbt.CompoundTag> data = be == null ? Optional.empty() : Optional.of(be.saveWithFullMetadata(level.registryAccess()));
            entries.add(new ShipStructure.Entry(p.subtract(pivot), st, data));
            AABB b = new AABB(p);
            box = box == null ? b : box.minmax(b);
        }
        ShipStructure structure = new ShipStructure(entries);
        // Passagiere: alle Lebewesen auf/in der Struktur
        List<LivingEntity> riders = level.getEntitiesOfClass(LivingEntity.class, box.inflate(1, 2, 1), e -> !e.isPassenger() && !e.isSpectator());
        // Blöcke entfernen: Inhalte bleiben im Schiff (Block-Entities zuerst weg, keine Drops, keine Nachbar-Updates)
        int flags = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS;
        for (int pass = 0; pass < 2; pass++)
            for (BlockPos p : found) {
                BlockState st = level.getBlockState(p);
                if (st.isAir()) continue;
                boolean full = st.isCollisionShapeFullBlock(level, p);
                if ((pass == 0) == full) continue; // erst Anbauteile, dann volle Blöcke
                if (level.getBlockEntity(p) != null) level.removeBlockEntity(p);
                level.setBlock(p, Blocks.AIR.defaultBlockState(), flags);
            }
        FlyingShipEntity ship = ModBlocks.FLYING_SHIP.get().create(level, net.minecraft.world.entity.EntitySpawnReason.TRIGGERED);
        if (ship == null) return;
        Vec3 center = Vec3.atCenterOf(pivot);
        ship.setPos(center.x, center.y, center.z);
        ship.setup(structure, forward.toYRot(), player.getUUID());
        level.addFreshEntity(ship);
        for (Entity e : riders) e.startRiding(ship, true, false);
        if (!player.isPassenger()) player.startRiding(ship, true, false);
        ship.setPilot(player);
        player.sendOverlayMessage(Component.translatable("message.simonspacemod.ship.launched", found.size(), structure.engines));
    }
}

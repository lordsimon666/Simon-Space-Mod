package de.simon.spacemod.transporter;

import de.simon.spacemod.ModBlocks;
import de.simon.spacemod.block.Beam;
import de.simon.spacemod.block.TransporterPadBlock;
import de.simon.spacemod.defense.Shields;
import de.simon.spacemod.turbolift.TurboliftNetwork;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.PacketDistributor;

/**
 * Transporter: auf die Oberfläche beamen oder zu einer anderen Plattform im Umkreis von 500 Blöcken.
 * Plattformen, die höchstens 10 Blöcke auseinanderliegen, bilden eine Gruppe (ein Transporterraum);
 * Ziel ist eine zufällige Plattform der Gruppe. Plattformen in 10 Blöcken um die eigene werden
 * ignoriert. Liegt ein aktives Schild zwischen Start und Ziel (eines umschließt nur einen der beiden
 * Punkte), ist kein Transport möglich.
 */
public final class Transporters {
    public static final int RANGE = 500, GROUP = 10;

    private Transporters() {}

    /** Gruppe von Plattformen. */
    public record Group(List<BlockPos> pads, Vec3 center) {}

    public static List<Group> groups(ServerLevel level, BlockPos from) {
        TransporterData data = TransporterData.get(level);
        List<BlockPos> cand = new ArrayList<>();
        List<Long> dead = new ArrayList<>();
        for (long l : data.pads) {
            BlockPos p = BlockPos.of(l);
            double d = Math.sqrt(p.distSqr(from));
            if (d > RANGE || d <= GROUP) continue;
            if (level.isLoaded(p) && !(level.getBlockState(p).getBlock() instanceof TransporterPadBlock)) {
                dead.add(l);
                continue;
            }
            cand.add(p);
        }
        dead.forEach(data::remove);
        cand.sort(Comparator.comparingDouble(p -> p.distSqr(from)));
        List<Group> groups = new ArrayList<>();
        boolean[] used = new boolean[cand.size()];
        for (int i = 0; i < cand.size(); i++) {
            if (used[i]) continue;
            List<BlockPos> g = new ArrayList<>();
            List<Integer> queue = new ArrayList<>(List.of(i));
            used[i] = true;
            while (!queue.isEmpty()) {
                int k = queue.remove(queue.size() - 1);
                g.add(cand.get(k));
                for (int j = 0; j < cand.size(); j++)
                    if (!used[j] && cand.get(j).distSqr(cand.get(k)) <= GROUP * GROUP) {
                        used[j] = true;
                        queue.add(j);
                    }
            }
            Vec3 c = Vec3.ZERO;
            for (BlockPos p : g) c = c.add(Vec3.atCenterOf(p));
            groups.add(new Group(g, c.scale(1.0 / g.size())));
        }
        groups.sort(Comparator.comparingDouble(g -> g.center().distanceToSqr(Vec3.atCenterOf(from))));
        return groups.size() > 12 ? groups.subList(0, 12) : groups;
    }

    /** Rechtsklick auf eine Plattform: Menü mit Zielen schicken. */
    public static void openMenu(ServerLevel level, BlockPos pad, ServerPlayer player) {
        TransporterData.get(level).add(pad);
        List<Group> groups = groups(level, pad);
        int[] data = new int[groups.size() * 4];
        for (int i = 0; i < groups.size(); i++) {
            Vec3 d = groups.get(i).center().subtract(Vec3.atCenterOf(pad));
            data[i * 4] = (int) Math.round(d.x);
            data[i * 4 + 1] = (int) Math.round(d.y);
            data[i * 4 + 2] = (int) Math.round(d.z);
            data[i * 4 + 3] = groups.get(i).pads().size();
        }
        TurboliftNetwork.CHANNEL.send(new TransporterMenuMessage(pad, data), PacketDistributor.PLAYER.with(player));
    }

    /** Liegt ein aktives Schild zwischen den beiden Punkten? */
    public static boolean shielded(ServerLevel level, Vec3 a, Vec3 b) {
        for (Shields.View v : Shields.views(level))
            if (v.up() && v.contains(a) != v.contains(b)) return true;
        return false;
    }

    /** Auswahl: -1 = Oberfläche, sonst Gruppe. */
    public static void choose(ServerPlayer player, BlockPos pad, int choice) {
        if (!(player.level() instanceof ServerLevel level)) return;
        if (!(level.getBlockState(pad).getBlock() instanceof TransporterPadBlock)) return;
        if (player.distanceToSqr(Vec3.atCenterOf(pad)) > 64) return;
        Vec3 target;
        if (choice < 0) {
            BlockPos ground = TransporterPadBlock.findGroundBelow(level, pad);
            if (ground == null) {
                player.sendOverlayMessage(Component.translatable("message.simonspacemod.transporter.no_ground"));
                return;
            }
            target = new Vec3(ground.getX() + 0.5, ground.getY(), ground.getZ() + 0.5);
        } else {
            List<Group> groups = groups(level, pad);
            if (choice >= groups.size()) return;
            List<BlockPos> g = groups.get(choice).pads();
            BlockPos t = g.get(level.getRandom().nextInt(g.size()));
            level.getChunk(t); // Ziel laden
            if (!(level.getBlockState(t).getBlock() instanceof TransporterPadBlock)) {
                TransporterData.get(level).remove(t.asLong());
                player.sendOverlayMessage(Component.translatable("message.simonspacemod.transporter.target_gone"));
                return;
            }
            target = new Vec3(t.getX() + 0.5, t.getY() + 0.25, t.getZ() + 0.5);
        }
        if (shielded(level, player.position(), target)) {
            player.sendOverlayMessage(Component.translatable("message.simonspacemod.transporter.shielded"));
            level.playSound(null, pad, net.minecraft.sounds.SoundEvents.BEACON_DEACTIVATE, net.minecraft.sounds.SoundSource.BLOCKS, 1F, 0.6F);
            return;
        }
        player.getPersistentData().putLong(TransporterPadBlock.LAST_PAD_TAG, pad.asLong());
        if (choice < 0 && !player.getInventory().contains(stack -> stack.is(ModBlocks.COMMUNICATOR.get())))
            player.getInventory().add(new ItemStack(ModBlocks.COMMUNICATOR.get()));
        Beam.teleport(player, level, target.x, target.y, target.z, player.getYRot());
        player.sendOverlayMessage(Component.translatable("message.simonspacemod.transporter.energize"));
    }
}

package de.simon.spacemod.transporter;

import de.simon.spacemod.ModBlocks;
import de.simon.spacemod.block.Beam;
import de.simon.spacemod.block.TransporterPadBlock;
import de.simon.spacemod.defense.Shields;
import de.simon.spacemod.entity.FlyingShipEntity;
import de.simon.spacemod.flight.ShipPhysics;
import de.simon.spacemod.flight.ShipStructure;
import de.simon.spacemod.flight.Shipyard;
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
import org.jspecify.annotations.Nullable;

/**
 * Transporter: auf die Oberfläche beamen oder zu einer anderen Plattform im Umkreis von 500 Blöcken –
 * auch zu und von fliegenden Schiffen (wer auf ein fliegendes Schiff gebeamt wird, fliegt mit).
 * Plattformen, die höchstens 10 Blöcke auseinanderliegen, bilden eine Gruppe (ein Transporterraum);
 * Ziel ist eine zufällige Plattform der Gruppe. Plattformen in 10 Blöcken um die eigene werden
 * ignoriert. Liegt ein aktives Schild zwischen Start und Ziel, ist kein Transport möglich.
 */
public final class Transporters {
    public static final int RANGE = 500, GROUP = 10;

    private Transporters() {}

    /** Ziel-Plattform: fest in der Welt (ship == null, pos = Welt) oder auf einem fliegenden Schiff (pos = lokal). */
    public record Pad(@Nullable FlyingShipEntity ship, BlockPos pos) {
        /** Mitte der Plattform in der Welt (jetzt). */
        public Vec3 world() {
            if (ship == null) return Vec3.atCenterOf(pos);
            return ShipPhysics.rotate(ship.rotation(1F), pos.getX(), pos.getY(), pos.getZ()).add(ship.position());
        }
    }

    /** Gruppe von Plattformen. */
    public record Group(List<Pad> pads, Vec3 center) {}

    /** Wo die benutzte Plattform gerade ist: Welt, Lage, ggf. fliegendes Schiff (lokal). */
    private record Source(ServerLevel level, Vec3 pos, @Nullable FlyingShipEntity ship, @Nullable BlockPos local) {}

    private static @Nullable Source source(ServerLevel padLevel, BlockPos pad) {
        if (!Shipyard.is(padLevel)) return new Source(padLevel, Vec3.atCenterOf(pad), null, null);
        FlyingShipEntity ship = Shipyard.shipAt(pad);
        if (ship == null || !(ship.level() instanceof ServerLevel world)) return null;
        BlockPos local = pad.subtract(ship.plotOrigin());
        return new Source(world, new Pad(ship, local).world(), ship, local);
    }

    public static List<Group> groups(ServerLevel level, Vec3 from) {
        TransporterData data = TransporterData.get(level);
        List<Pad> cand = new ArrayList<>();
        List<Long> dead = new ArrayList<>();
        for (long l : data.pads) {
            BlockPos p = BlockPos.of(l);
            double d = Vec3.atCenterOf(p).distanceTo(from);
            if (d > RANGE || d <= GROUP) continue;
            if (level.isLoaded(p) && !(level.getBlockState(p).getBlock() instanceof TransporterPadBlock)) {
                dead.add(l);
                continue;
            }
            cand.add(new Pad(null, p));
        }
        dead.forEach(data::remove);
        // Plattformen fliegender Schiffe in dieser Welt
        for (FlyingShipEntity ship : Shipyard.ships()) {
            if (ship.level() != level || ship.structure() == null || ship.position().distanceTo(from) > RANGE + ship.structure().radius) continue;
            for (ShipStructure.Entry e : ship.structure().blocks) {
                if (!(e.state().getBlock() instanceof TransporterPadBlock)) continue;
                Pad p = new Pad(ship, e.pos());
                double d = p.world().distanceTo(from);
                if (d <= RANGE && d > GROUP) cand.add(p);
            }
        }
        cand.sort(Comparator.comparingDouble(p -> p.world().distanceToSqr(from)));
        List<Group> groups = new ArrayList<>();
        boolean[] used = new boolean[cand.size()];
        for (int i = 0; i < cand.size(); i++) {
            if (used[i]) continue;
            List<Pad> g = new ArrayList<>();
            List<Integer> queue = new ArrayList<>(List.of(i));
            used[i] = true;
            while (!queue.isEmpty()) {
                int k = queue.remove(queue.size() - 1);
                g.add(cand.get(k));
                for (int j = 0; j < cand.size(); j++)
                    if (!used[j] && cand.get(j).ship() == cand.get(k).ship()
                        && cand.get(j).world().distanceToSqr(cand.get(k).world()) <= GROUP * GROUP) {
                        used[j] = true;
                        queue.add(j);
                    }
            }
            Vec3 c = Vec3.ZERO;
            for (Pad p : g) c = c.add(p.world());
            groups.add(new Group(g, c.scale(1.0 / g.size())));
        }
        groups.sort(Comparator.comparingDouble(g -> g.center().distanceToSqr(from)));
        return groups.size() > 12 ? groups.subList(0, 12) : groups;
    }

    /** Rechtsklick auf eine Plattform (auch im fliegenden Schiff): Menü mit Zielen schicken. */
    public static void openMenu(ServerLevel padLevel, BlockPos pad, ServerPlayer player) {
        TransporterData.get(padLevel).add(pad);
        Source src = source(padLevel, pad);
        if (src == null) return;
        List<Group> groups = groups(src.level(), src.pos());
        int[] data = new int[groups.size() * 5];
        for (int i = 0; i < groups.size(); i++) {
            Vec3 d = groups.get(i).center().subtract(src.pos());
            data[i * 5] = (int) Math.round(d.x);
            data[i * 5 + 1] = (int) Math.round(d.y);
            data[i * 5 + 2] = (int) Math.round(d.z);
            data[i * 5 + 3] = groups.get(i).pads().size();
            data[i * 5 + 4] = groups.get(i).pads().get(0).ship() != null ? 1 : 0;
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
        ServerLevel padLevel = level;
        // Plattform in einem fliegenden Schiff (Koordinaten der Werft)?
        if (player.getVehicle() instanceof FlyingShipEntity ship && ship.plot() >= 0 && Shipyard.plotOf(pad) == ship.plot()) {
            ServerLevel sy = Shipyard.level(level.getServer());
            if (sy == null) return;
            padLevel = sy;
            if (ship.seat(player).distanceTo(Vec3.atLowerCornerOf(pad.subtract(ship.plotOrigin()))) > 8) return;
        } else if (player.distanceToSqr(Vec3.atCenterOf(pad)) > 64) return;
        if (!(padLevel.getBlockState(pad).getBlock() instanceof TransporterPadBlock)) return;
        Source src = source(padLevel, pad);
        if (src == null) return;
        Vec3 target;
        Pad onShip = null;
        if (choice < 0) {
            BlockPos ground = TransporterPadBlock.findGroundBelow(src.level(), BlockPos.containing(src.pos()));
            if (ground == null) {
                player.sendOverlayMessage(Component.translatable("message.simonspacemod.transporter.no_ground"));
                return;
            }
            target = new Vec3(ground.getX() + 0.5, ground.getY(), ground.getZ() + 0.5);
        } else {
            List<Group> groups = groups(src.level(), src.pos());
            if (choice >= groups.size()) return;
            List<Pad> g = groups.get(choice).pads();
            Pad t = g.get(level.getRandom().nextInt(g.size()));
            if (t.ship() == null) {
                level.getChunk(t.pos()); // Ziel laden
                if (!(src.level().getBlockState(t.pos()).getBlock() instanceof TransporterPadBlock)) {
                    TransporterData.get(src.level()).remove(t.pos().asLong());
                    player.sendOverlayMessage(Component.translatable("message.simonspacemod.transporter.target_gone"));
                    return;
                }
                target = new Vec3(t.pos().getX() + 0.5, t.pos().getY() + 0.25, t.pos().getZ() + 0.5);
            } else {
                if (!t.ship().isAlive()) return;
                onShip = t;
                target = t.world().add(0, -0.3, 0);
            }
        }
        Vec3 from = player.position();
        if (shielded(src.level(), from, target)) {
            player.sendOverlayMessage(Component.translatable("message.simonspacemod.transporter.shielded"));
            src.level().playSound(null, BlockPos.containing(src.pos()), net.minecraft.sounds.SoundEvents.BEACON_DEACTIVATE,
                net.minecraft.sounds.SoundSource.BLOCKS, 1F, 0.6F);
            return;
        }
        if (src.ship() == null) player.getPersistentData().putLong(TransporterPadBlock.LAST_PAD_TAG, pad.asLong());
        if (choice < 0 && !player.getInventory().contains(stack -> stack.is(ModBlocks.COMMUNICATOR.get())))
            player.getInventory().add(new ItemStack(ModBlocks.COMMUNICATOR.get()));
        Beam.teleport(player, src.level(), target.x, target.y, target.z, player.getYRot());
        if (onShip != null) {
            // auf ein fliegendes Schiff: an Bord gehen und auf der Plattform stehen
            FlyingShipEntity ship = onShip.ship();
            player.startRiding(ship, true, false);
            BlockPos l = onShip.pos();
            ship.setSeat(player, new Vec3(l.getX(), l.getY() - 0.5 + 0.2, l.getZ()));
        }
        player.sendOverlayMessage(Component.translatable("message.simonspacemod.transporter.energize"));
    }
}

package de.simon.spacemod.display;

import de.simon.spacemod.entity.FlyingShipEntity;
import de.simon.spacemod.power.ShipPower;
import de.simon.spacemod.turbolift.TurboliftNetwork;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraftforge.network.PacketDistributor;
import org.jspecify.annotations.Nullable;

/** Server: Kameraliste eines Bildschirms, Auswahl, Hauptbildschirm und Durchschalten am Steuer. */
public final class Displays {
    private static final int SEARCH_LIMIT = 60_000;

    private Displays() {}

    /** Kameras und Bildschirme einer Struktur (über Blöcke der Mod verbunden). */
    public record Found(List<BlockPos> cameras, List<BlockPos> displays) {}

    /** Offenes Menü je Spieler: in welcher Welt, welcher Bildschirm, welche Kameras. */
    private record Pending(Level level, BlockPos display, List<BlockPos> cameras) {}

    private static final Map<UUID, Pending> PENDING = new HashMap<>();

    public static Found explore(Level level, BlockPos start) {
        List<BlockPos> cams = new ArrayList<>(), displays = new ArrayList<>();
        Set<BlockPos> seen = new HashSet<>();
        ArrayDeque<BlockPos> q = new ArrayDeque<>();
        seen.add(start);
        q.add(start);
        while (!q.isEmpty() && seen.size() < SEARCH_LIMIT) {
            BlockPos p = q.poll();
            var st = level.getBlockState(p);
            if (st.getBlock() instanceof CameraBlock) cams.add(p);
            if (st.getBlock() instanceof DisplayBlock) displays.add(p);
            for (Direction d : Direction.values()) {
                BlockPos n = p.relative(d);
                if (seen.contains(n) || !level.isLoaded(n)) continue;
                seen.add(n);
                if (ShipPower.conducts(level.getBlockState(n))) q.add(n.immutable());
            }
        }
        cams.sort(java.util.Comparator.comparingDouble(c -> c.distSqr(start)));
        return new Found(cams, displays);
    }

    /** Rechtsklick auf einen Bildschirm: Kameraliste schicken. */
    static void openMenu(ServerPlayer player, Level level, BlockPos pos) {
        Found f = explore(level, pos);
        DisplayGroup g = DisplayGroup.of(level::getBlockState, pos);
        BlockPos sel = level.getBlockEntity(g.anchor()) instanceof DisplayBlockEntity be ? be.camera() : null;
        boolean main = level.getBlockEntity(g.anchor()) instanceof DisplayBlockEntity be2 && be2.isMain();
        int[] data = new int[f.cameras().size() * 4];
        for (int i = 0; i < f.cameras().size(); i++) {
            BlockPos c = f.cameras().get(i);
            data[i * 4] = c.getX() - pos.getX();
            data[i * 4 + 1] = c.getY() - pos.getY();
            data[i * 4 + 2] = c.getZ() - pos.getZ();
            data[i * 4 + 3] = level.getBlockState(c).getValue(DirectionalBlock.FACING).get3DDataValue();
        }
        PENDING.put(player.getUUID(), new Pending(level, pos, f.cameras()));
        TurboliftNetwork.CHANNEL.send(new DisplayMessage.Menu(data, sel == null ? -1 : f.cameras().indexOf(sel), main, g.width(), g.height()),
            PacketDistributor.PLAYER.with(player));
    }

    /** Auswahl aus dem Menü: Kamera (oder -1 = aus) und Hauptbildschirm. */
    static void choose(ServerPlayer player, int camera, boolean main) {
        Pending p = PENDING.get(player.getUUID());
        if (p == null || !p.level().isLoaded(p.display()) || !(p.level().getBlockState(p.display()).getBlock() instanceof DisplayBlock)) return;
        if (de.simon.spacemod.flight.Shipyard.distanceSqr(player, p.level(), p.display()) > 64) return;
        BlockPos cam = camera >= 0 && camera < p.cameras().size() ? p.cameras().get(camera) : null;
        if (main) // nur ein Hauptbildschirm je Struktur
            for (BlockPos d : explore(p.level(), p.display()).displays())
                if (p.level().getBlockEntity(d) instanceof DisplayBlockEntity be && be.isMain()) be.set(be.camera(), false);
        setGroup(p.level(), p.display(), cam, main);
    }

    private static void setGroup(Level level, BlockPos display, @Nullable BlockPos cam, boolean main) {
        for (BlockPos b : DisplayGroup.of(level::getBlockState, display).blocks())
            if (level.getBlockEntity(b) instanceof DisplayBlockEntity be) be.set(cam, main);
    }

    /** Rechtsklick am Steuer: Hauptbildschirm des Schiffs zur nächsten Kamera schalten. */
    static void cycleMain(ServerPlayer player, FlyingShipEntity ship) {
        ServerLevel sy = ship.level() instanceof ServerLevel l ? de.simon.spacemod.flight.Shipyard.level(l.getServer()) : null;
        var s = ship.structure();
        if (sy == null || s == null || ship.plot() < 0) return;
        BlockPos o = ship.plotOrigin();
        List<BlockPos> cams = new ArrayList<>();
        List<BlockPos> mains = new ArrayList<>();
        for (var e : s.blocks) {
            if (e.state().getBlock() instanceof CameraBlock) cams.add(o.offset(e.pos()));
            if (e.state().getBlock() instanceof DisplayBlock && sy.getBlockEntity(o.offset(e.pos())) instanceof DisplayBlockEntity be && be.isMain())
                mains.add(o.offset(e.pos()));
        }
        if (mains.isEmpty()) {
            player.sendOverlayMessage(Component.translatable("message.simonspacemod.display.no_main"));
            return;
        }
        if (cams.isEmpty()) {
            player.sendOverlayMessage(Component.translatable("message.simonspacemod.display.no_cameras"));
            return;
        }
        cams.sort(java.util.Comparator.comparingLong(BlockPos::asLong));
        BlockPos first = mains.get(0);
        BlockPos cur = sy.getBlockEntity(first) instanceof DisplayBlockEntity be ? be.camera() : null;
        int next = (cams.indexOf(cur) + 1) % cams.size();
        for (BlockPos m : mains) setGroup(sy, m, cams.get(next), true);
        player.sendOverlayMessage(Component.translatable("message.simonspacemod.display.camera", next + 1, cams.size()));
    }
}

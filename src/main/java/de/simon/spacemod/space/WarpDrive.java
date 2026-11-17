package de.simon.spacemod.space;

import de.simon.spacemod.SimonSpaceMod;
import de.simon.spacemod.flight.ShipStructure;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * Warpantrieb: Stufen, Energie, Geschwindigkeit und Warpgondeln. Ein Schiff im Warp fliegt in der eigenen
 * Warp-Dimension; seine echte Lage (in der Dimension, aus der es kam) wird mitgerechnet.
 *
 * <ul>
 * <li>Energie: 2375 · Warp² FE/t – Warp 2 (9500 FE/t) schafft ein 8×8×8-Kernreaktor (10 000 FE/t) gerade so.</li>
 * <li>Geschwindigkeit: 5 · Warp^(10/3) Blöcke je Tick (Warp 1 = 100 Blöcke/s, Warp 5 ≈ 4300, Warp 9.9 ≈ 208 000).</li>
 * <li>Gondeln: eine Gondel = mindestens 1 Bussard-Kollektor und 2 Gondelgitter nah beieinander. Eine Gondel
 * erlaubt höchstens Warp 5, zwei Gondeln mit mindestens 10 Blöcken Abstand Warp 9.9; schneller geht nicht.</li>
 * </ul>
 */
public final class WarpDrive {
    public static final ResourceKey<Level> WARP = ResourceKey.create(Registries.DIMENSION,
        Identifier.fromNamespaceAndPath(SimonSpaceMod.MODID, "warp"));
    public static final double MAX_WARP = 9.9, ONE_NACELLE_MAX = 5.0;
    public static final double FE_FACTOR = 2375;
    /** Mindestabstand zweier Gondeln (Mittelpunkte), damit sie als zwei zählen. */
    public static final double NACELLE_SPACING = 10;
    /** Höhe der Schiffe in der Warp-Dimension. */
    public static final int WARP_Y = 500;

    private WarpDrive() {}

    /** Wer im Warp vom Schiff fällt, landet mit Sanftem Fall in der Oberwelt (an derselben Stelle). */
    public static void init() {
        net.minecraftforge.event.TickEvent.ServerTickEvent.Post.BUS.addListener(e -> {
            if (e.server().getTickCount() % 10 != 0) return;
            var warp = e.server().getLevel(WARP);
            if (warp == null) return;
            for (var p : java.util.List.copyOf(warp.players())) {
                if (p.isPassenger() || p.getY() > WARP_Y - 60) continue;
                var ow = e.server().overworld();
                p.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.SLOW_FALLING, 20 * 60));
                p.teleport(new net.minecraft.world.level.portal.TeleportTransition(ow, new net.minecraft.world.phys.Vec3(p.getX(), ow.getMaxY() + 10, p.getZ()),
                    net.minecraft.world.phys.Vec3.ZERO, p.getYRot(), p.getXRot(), net.minecraft.world.level.portal.TeleportTransition.DO_NOTHING));
            }
        });
    }

    /**
     * Im Warp sind alle Waffen gesperrt: Spieler in der Warp-Dimension (an Bord eines Schiffs im Warp) und
     * Waffenstationen in der Werft, deren Schiff gerade im Warp ist. Meldet es dem Spieler.
     */
    public static boolean weaponsLocked(net.minecraft.world.entity.player.@org.jspecify.annotations.Nullable Player player,
                                        @org.jspecify.annotations.Nullable Level level, @org.jspecify.annotations.Nullable BlockPos pos) {
        boolean locked = player != null && player.level().dimension() == WARP;
        if (!locked && level != null && pos != null && de.simon.spacemod.flight.Shipyard.is(level)) {
            var ship = de.simon.spacemod.flight.Shipyard.shipAt(pos);
            locked = ship != null && ship.inWarp();
        }
        if (locked && player instanceof net.minecraft.server.level.ServerPlayer sp)
            sp.sendOverlayMessage(net.minecraft.network.chat.Component.translatable("message.simonspacemod.warp.no_weapons")
                .withStyle(net.minecraft.ChatFormatting.RED));
        return locked;
    }

    /** Energie bei Warp {@code w} (FE/t). */
    public static int cost(double w) {
        return w <= 0 ? 0 : (int) Math.round(FE_FACTOR * w * w);
    }

    /** Echte Strecke je Tick bei Warp {@code w} (Blöcke). */
    public static double speed(double w) {
        return w <= 0 ? 0 : 5 * Math.pow(w, 10.0 / 3);
    }

    /** Höchste Warpstufe, die die Erzeugung schafft (eine Nachkommastelle, abgerundet). */
    public static double maxForPower(long production) {
        return Math.floor(Math.sqrt(production / FE_FACTOR) * 10) / 10;
    }

    /** Gültige Warpstufe (eine Nachkommastelle, 1 bis 9.9). */
    public static double clamp(double w) {
        return Math.max(1, Math.min(MAX_WARP, Math.round(w * 10) / 10.0));
    }

    private static boolean bussard(String id) {
        return id.startsWith("bussard");
    }

    private static boolean grille(String id) {
        return id.equals("nacelle_grille");
    }

    /** Mittelpunkte der gültigen Warpgondeln des Schiffs. */
    public static List<double[]> nacelles(ShipStructure s) {
        List<BlockPos> pos = new ArrayList<>();
        List<Boolean> isBussard = new ArrayList<>();
        for (ShipStructure.Entry e : s.blocks) {
            var key = BuiltInRegistries.BLOCK.getKey(e.state().getBlock());
            if (!key.getNamespace().equals(SimonSpaceMod.MODID)) continue;
            String id = key.getPath();
            if (bussard(id) || grille(id)) {
                pos.add(e.pos());
                isBussard.add(bussard(id));
            }
        }
        return nacelleCenters(pos, isBussard);
    }

    /** Gondeln aus den Lagen der Gondelblöcke (true = Bussard-Kollektor, false = Gondelgitter). */
    public static List<double[]> nacelleCenters(List<BlockPos> pos, List<Boolean> isBussard) {
        int n = pos.size();
        int[] parent = new int[n];
        for (int i = 0; i < n; i++) parent[i] = i;
        // Teile einer Gondel liegen höchstens 3 Blöcke auseinander (Hülle dazwischen ist erlaubt)
        for (int i = 0; i < n; i++)
            for (int j = i + 1; j < n; j++) {
                BlockPos a = pos.get(i), b = pos.get(j);
                if (Math.max(Math.abs(a.getX() - b.getX()), Math.max(Math.abs(a.getY() - b.getY()), Math.abs(a.getZ() - b.getZ()))) <= 3)
                    parent[find(parent, i)] = find(parent, j);
            }
        java.util.Map<Integer, double[]> groups = new java.util.HashMap<>(); // x, y, z, Anzahl, Bussards, Gitter
        for (int i = 0; i < n; i++) {
            double[] g = groups.computeIfAbsent(find(parent, i), k -> new double[6]);
            BlockPos p = pos.get(i);
            g[0] += p.getX(); g[1] += p.getY(); g[2] += p.getZ(); g[3]++;
            if (isBussard.get(i)) g[4]++; else g[5]++;
        }
        List<double[]> out = new ArrayList<>();
        for (double[] g : groups.values())
            if (g[4] >= 1 && g[5] >= 2) out.add(new double[] {g[0] / g[3], g[1] / g[3], g[2] / g[3]});
        return out;
    }

    private static int find(int[] p, int i) {
        while (p[i] != i) i = p[i] = p[p[i]];
        return i;
    }

    /** Wie viele Gondeln zählen: 0, 1 oder 2 (zwei nur bei mindestens 10 Blöcken Abstand). */
    public static int nacelleCount(List<double[]> nacelles) {
        if (nacelles.isEmpty()) return 0;
        for (int i = 0; i < nacelles.size(); i++)
            for (int j = i + 1; j < nacelles.size(); j++) {
                double[] a = nacelles.get(i), b = nacelles.get(j);
                if (Math.sqrt((a[0] - b[0]) * (a[0] - b[0]) + (a[1] - b[1]) * (a[1] - b[1]) + (a[2] - b[2]) * (a[2] - b[2])) >= NACELLE_SPACING)
                    return 2;
            }
        return 1;
    }

    /** Höchste Warpstufe nach Gondeln: keine 0, eine 5, zwei 9.9. */
    public static double maxForNacelles(int count) {
        return count <= 0 ? 0 : count == 1 ? ONE_NACELLE_MAX : MAX_WARP;
    }
}

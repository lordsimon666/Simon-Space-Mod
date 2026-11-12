package de.simon.spacemod.defense;

import de.simon.spacemod.ModBlocks;
import de.simon.spacemod.ModSounds;
import de.simon.spacemod.block.WarpCoreBlock;
import de.simon.spacemod.power.ShipPower;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

/**
 * Energieverteilung des Schiffs: Anteil je Verbrauchergruppe (Waffen, Schilde, Antrieb, Licht,
 * Maschinen) und Warpkern-Overdrive (+50 % Leistung). Läuft der Overdrive länger als 5 Minuten,
 * beginnt ein Warpkernbruch: Alarmstufe Rot, nach 2 Minuten explodiert der Kern – außer er wurde
 * vorher entfernt.
 */
public class PowerConsoleBlockEntity extends BlockEntity {
    public static final int OVERDRIVE_LIMIT = 20 * 60 * 5;
    public static final int BREACH_TICKS = 20 * 60 * 2;
    public static final int STEP = 25;
    /** So lange muss der Overdrive aus sein, bis die angesammelte Overdrive-Zeit zurückgesetzt wird. */
    public static final int RESET_TICKS = 20 * 60 * 30;

    private final int[] shares = {100, 100, 100, 100, 100};
    private boolean overdrive;
    private int overdriveTicks;
    private int offTicks;
    private int breachTicks = -1;
    private long kfe;
    private boolean forced;
    private boolean scanned;
    private List<BlockPos> alarms = List.of();
    private List<BlockPos> cores = List.of();

    public final ContainerData data = new ContainerData() {
        @Override
        public int get(int i) {
            if (i < 5) return shares[i];
            return switch (i) {
                case 5 -> overdrive ? 1 : 0;
                case 6 -> overdriveTicks / 20;
                case 7 -> breachTicks < 0 ? 0 : breachTicks / 20 + 1;
                case 8 -> (int) (kfe & 0x7FFF);
                case 9 -> (int) ((kfe >> 15) & 0x7FFF);
                default -> overdriveTicks > 0 && !overdrive ? (RESET_TICKS - offTicks + 19) / 20 : 0;
            };
        }

        @Override
        public void set(int i, int v) {}

        @Override
        public int getCount() {
            return 11;
        }
    };

    public PowerConsoleBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.POWER_CONSOLE_BE.get(), pos, state);
    }

    /** Knöpfe: 0-9 = Gruppe (id / 2) weniger/mehr, 10 = Overdrive an/aus. */
    public void press(int id, ServerPlayer player) {
        if (id >= 0 && id < 10) {
            int c = id / 2;
            shares[c] = Math.max(0, Math.min(100, shares[c] + (id % 2 == 0 ? -STEP : STEP)));
        } else if (id == 10) {
            overdrive = !overdrive;
            player.sendOverlayMessage(Component.translatable(overdrive ? "message.simonspacemod.power.overdrive_on" : "message.simonspacemod.power.overdrive_off")
                .withStyle(overdrive ? ChatFormatting.GOLD : ChatFormatting.AQUA));
        }
        setChanged();
    }

    public void openMenu(ServerPlayer player) {
        player.openMenu(new SimpleMenuProvider((id, inv, p) -> new ConsoleMenu(ModBlocks.POWER_CONSOLE_MENU.get(), id, data,
            b -> press(b, player), pl -> de.simon.spacemod.flight.Shipyard.distanceSqr(pl, level, worldPosition) < 64
                && level.getBlockEntity(worldPosition) == this),
            Component.translatable("block.simonspacemod.power_console")));
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, PowerConsoleBlockEntity be) {
        if (!(level instanceof ServerLevel sl)) return;
        long t = level.getGameTime();
        if (t % 10 == 0) {
            ShipPower.Grid grid = ShipPower.gridFor(sl, pos);
            ShipPower.allocate(sl, grid, be.shares, be.overdrive);
            be.kfe = ShipPower.effectiveOutput(sl, grid) / 1000;
        }
        if (be.overdrive) {
            be.offTicks = 0;
            be.overdriveTicks++;
            if (be.overdriveTicks == OVERDRIVE_LIMIT - 1200) be.broadcast(sl, Component.translatable("message.simonspacemod.power.overheat")
                .withStyle(ChatFormatting.GOLD), false);
            if (be.overdriveTicks >= OVERDRIVE_LIMIT && be.breachTicks < 0) be.startBreach(sl);
        } else if (be.overdriveTicks > 0) {
            // die Overdrive-Zeit bleibt stehen und wird erst nach 30 Minuten ohne Overdrive zurückgesetzt
            // (an- und ausschalten bringt also nichts)
            if (++be.offTicks >= RESET_TICKS) {
                be.overdriveTicks = 0;
                be.offTicks = 0;
            }
        }
        if (be.breachTicks >= 0) be.breachTick(sl);
        boolean force = be.overdrive || be.breachTicks >= 0;
        if (force != be.forced) {
            sl.setChunkForced(pos.getX() >> 4, pos.getZ() >> 4, force);
            be.forced = force;
        }
        if (t % 20 == 0) be.setChanged();
    }

    private void scan(ServerLevel level) {
        scanned = true;
        ShipPower.Found f = ShipPower.explore(level, worldPosition);
        alarms = f.alarms();
        cores = f.cores().stream().filter(p -> level.getBlockState(p).getBlock() instanceof WarpCoreBlock).toList();
    }

    private void startBreach(ServerLevel level) {
        scan(level);
        if (cores.isEmpty()) {
            overdrive = false;
            overdriveTicks = 0;
            return;
        }
        breachTicks = BREACH_TICKS;
        broadcast(level, Component.translatable("message.simonspacemod.power.breach").withStyle(ChatFormatting.RED, ChatFormatting.BOLD), true);
    }

    private void breachTick(ServerLevel level) {
        if (!scanned || breachTicks % 100 == 0) scan(level);
        if (cores.isEmpty()) { // Kern entfernt/abgeworfen
            breachTicks = -1;
            overdrive = false;
            overdriveTicks = 0;
            lights(level, false);
            broadcast(level, Component.translatable("message.simonspacemod.power.breach_averted").withStyle(ChatFormatting.GREEN), true);
            return;
        }
        if (breachTicks % 10 == 0) lights(level, (breachTicks / 10) % 2 == 0);
        if (breachTicks % 40 == 0) {
            int n = 0;
            for (BlockPos a : alarms) {
                if (n++ >= 8) break;
                level.playSound(null, a, ModSounds.RED_ALERT.get(), SoundSource.BLOCKS, 3F, 1F);
            }
            level.playSound(null, worldPosition, ModSounds.RED_ALERT.get(), SoundSource.BLOCKS, 2F, 1F);
        }
        if (breachTicks % 20 == 0)
            broadcast(level, Component.translatable("message.simonspacemod.power.countdown", breachTicks / 20).withStyle(ChatFormatting.RED), false);
        if (breachTicks-- > 0) return;
        // Explosion am Warpkern
        Vec3 c = Vec3.ZERO;
        double radius = 0;
        for (BlockPos p : cores) {
            c = c.add(Vec3.atCenterOf(p));
            radius = Math.max(radius, WarpBreach.radius(level.getBlockState(p)));
        }
        c = c.scale(1.0 / cores.size());
        lights(level, false);
        breachTicks = -1;
        overdrive = false;
        overdriveTicks = 0;
        WarpBreach.detonate(level, c, radius);
    }

    private void lights(ServerLevel level, boolean on) {
        for (BlockPos a : alarms) {
            if (!level.isLoaded(a)) continue;
            BlockState st = level.getBlockState(a);
            if (st.getBlock() instanceof AlarmLightBlock && st.getValue(AlarmLightBlock.LIT) != on)
                level.setBlock(a, st.setValue(AlarmLightBlock.LIT, on), 2);
        }
    }

    /** Meldung an alle in der Nähe (300 Blöcke). */
    private void broadcast(ServerLevel level, Component msg, boolean chat) {
        for (ServerPlayer p : level.players()) {
            if (p.distanceToSqr(Vec3.atCenterOf(worldPosition)) > 300 * 300) continue;
            if (chat) p.sendSystemMessage(msg);
            else p.sendOverlayMessage(msg);
        }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (forced && level instanceof ServerLevel sl) sl.setChunkForced(worldPosition.getX() >> 4, worldPosition.getZ() >> 4, false);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        for (int i = 0; i < 5; i++) shares[i] = input.getIntOr("share" + i, 100);
        overdrive = input.getBooleanOr("overdrive", false);
        overdriveTicks = input.getIntOr("odTicks", 0);
        offTicks = input.getIntOr("offTicks", 0);
        breachTicks = input.getIntOr("breach", -1);
        forced = input.getBooleanOr("forced", false);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        for (int i = 0; i < 5; i++) output.putInt("share" + i, shares[i]);
        output.putBoolean("overdrive", overdrive);
        output.putInt("odTicks", overdriveTicks);
        output.putInt("offTicks", offTicks);
        output.putInt("breach", breachTicks);
        output.putBoolean("forced", forced);
    }
}

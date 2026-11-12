package de.simon.spacemod.defense;

import com.mojang.serialization.Codec;
import de.simon.spacemod.ModBlocks;
import de.simon.spacemod.power.ShipPower;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * Schildsteuerung (Brücke): Schild ein-/ausschalten und Umverteilen (Energie von starken zu
 * schwachen Schildblöcken). Beim Einschalten wird die Hülle eingelesen und die Schildform berechnet.
 */
public class ShieldConsoleBlockEntity extends BlockEntity {
    public static final int OFF = 0, SCANNING = 1, UP = 2, NO_EMITTER = 3, NO_POWER = 4;
    /** Strom je Lebenspunkt bei der Regeneration. */
    public static final int FE_PER_HP = 100;
    private static final int REBALANCE_COOLDOWN = 200;

    private boolean on;
    private int state = OFF;
    private Shield shield;
    private ShieldScan scan;
    private int rebalanceCooldown;
    private boolean forced;
    // gespeicherter Zustand, bis das Schild wieder berechnet ist
    private int savedCells = -1;
    private float savedBulk = -1;
    private List<Long> savedKeys = List.of();
    private List<Float> savedHp = List.of();

    public final ContainerData data = new ContainerData() {
        @Override
        public int get(int i) {
            return switch (i) {
                case 0 -> state;
                case 1 -> shield == null ? 0 : Math.round(shield.average() * 100);
                case 2 -> shield == null ? 0 : Math.round(shield.weakest() * 100);
                case 3 -> shield == null ? 0 : Math.min(32767, shield.cells / 100);
                case 4 -> shield == null ? 0 : shield.tier;
                case 5 -> (rebalanceCooldown + 19) / 20;
                default -> on ? 1 : 0;
            };
        }

        @Override
        public void set(int i, int v) {}

        @Override
        public int getCount() {
            return 7;
        }
    };

    public ShieldConsoleBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.SHIELD_CONSOLE_BE.get(), pos, state);
    }

    /** Knöpfe: 0 = an/aus, 1 = umverteilen. */
    public void press(int id, ServerPlayer player) {
        if (id == 0) {
            on = !on;
            if (!on) shutdown(true);
            player.sendOverlayMessage(Component.translatable(on ? "message.simonspacemod.shield.on" : "message.simonspacemod.shield.off")
                .withStyle(on ? ChatFormatting.AQUA : ChatFormatting.GRAY));
        } else if (id == 1 && shield != null && rebalanceCooldown == 0) {
            shield.rebalance();
            rebalanceCooldown = REBALANCE_COOLDOWN;
            player.sendOverlayMessage(Component.translatable("message.simonspacemod.shield.rebalanced", Math.round(shield.average() * 100)));
        }
        setChanged();
    }

    public void openMenu(ServerPlayer player) {
        player.openMenu(new SimpleMenuProvider((id, inv, p) -> new ConsoleMenu(ModBlocks.SHIELD_CONSOLE_MENU.get(), id, data,
            b -> press(b, player), pl -> de.simon.spacemod.flight.Shipyard.distanceSqr(pl, level, worldPosition) < 64
                && level.getBlockEntity(worldPosition) == this),
            Component.translatable("block.simonspacemod.shield_console")));
    }

    private void shutdown(boolean keepData) {
        if (shield != null) {
            if (keepData) remember();
            Shields.unregister(shield);
            shield = null;
        }
        scan = null;
        state = OFF;
    }

    private void remember() {
        savedCells = shield.cells;
        savedBulk = shield.bulk;
        List<Long> k = new ArrayList<>();
        List<Float> v = new ArrayList<>();
        for (var e : shield.damaged.long2FloatEntrySet()) {
            k.add(e.getLongKey());
            v.add(e.getFloatValue());
        }
        savedKeys = k;
        savedHp = v;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState st, ShieldConsoleBlockEntity be) {
        if (!(level instanceof ServerLevel sl)) return;
        if (be.rebalanceCooldown > 0) be.rebalanceCooldown--;
        if (be.forced != be.on) {
            sl.setChunkForced(pos.getX() >> 4, pos.getZ() >> 4, be.on);
            be.forced = be.on;
        }
        if (!be.on) {
            if (be.shield != null || be.scan != null) be.shutdown(true); // auch wenn per Befehl ausgeschaltet
            return;
        }
        if (be.shield == null) {
            if (be.scan == null) {
                be.scan = ShieldScan.start(sl, pos);
                be.state = SCANNING;
            }
            ShieldScan.Result r = be.scan.step();
            if (r == null) return;
            int tier = 0;
            for (BlockPos e : be.scan.emitters)
                if (sl.getBlockState(e).getBlock() instanceof ShieldEmitterBlock em) tier = Math.max(tier, em.tier);
            if (tier == 0) {
                be.scan = null;
                be.on = false;
                be.state = NO_EMITTER;
                return;
            }
            be.shield = new Shield(sl, pos, be.scan.origin, r.shape(), tier, List.copyOf(be.scan.emitters), r.cells());
            be.scan = null;
            if (be.savedCells == be.shield.cells && be.savedBulk >= 0) {
                be.shield.bulk = Math.min(be.shield.maxHp, be.savedBulk);
                for (int i = 0; i < be.savedKeys.size(); i++) be.shield.damaged.put((long) be.savedKeys.get(i), (float) be.savedHp.get(i));
            }
            Shields.register(be.shield);
        }
        if (level.getGameTime() % 10 != 0) return;
        Shield s = be.shield;
        // Emitter noch da?
        if (level.getGameTime() % 100 == 0 && s.emitters.stream().noneMatch(e -> !sl.isLoaded(e) || sl.getBlockState(e).getBlock() instanceof ShieldEmitterBlock)) {
            be.shutdown(false);
            be.on = false;
            be.state = NO_EMITTER;
            return;
        }
        // Strom: Halten + Regeneration (Gruppe "Schilde" der Energieverteilung)
        ShipPower.Grid grid = ShipPower.gridFor(sl, s.emitters.get(0));
        long upkeep = s.upkeep() * 10L;
        int needing = s.damaged.size() + (s.bulk < s.maxHp ? s.cells - s.damaged.size() : 0);
        long regenFe = (long) (Math.min(needing, 20000) * s.maxHp * 0.02F * 0.5F * FE_PER_HP);
        int got = ShipPower.draw(sl, grid, (int) Math.min(Integer.MAX_VALUE, upkeep + regenFe), ShipPower.Category.SHIELDS);
        if (got < upkeep) {
            s.up = false;
            be.state = NO_POWER;
            return;
        }
        s.up = true;
        be.state = UP;
        s.regenerate((float) (got - upkeep) / FE_PER_HP, 0.5F);
        if (level.getGameTime() % 200 == 0) be.setChanged();
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (shield != null) Shields.unregister(shield);
        if (forced && level instanceof ServerLevel sl) sl.setChunkForced(worldPosition.getX() >> 4, worldPosition.getZ() >> 4, false);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        on = input.getBooleanOr("on", false);
        forced = input.getBooleanOr("forced", false);
        savedCells = input.getIntOr("cells", -1);
        savedBulk = input.getFloatOr("bulk", -1);
        savedKeys = input.read("dmgKeys", Codec.LONG.listOf()).orElse(List.of());
        savedHp = input.read("dmgHp", Codec.FLOAT.listOf()).orElse(List.of());
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putBoolean("on", on);
        output.putBoolean("forced", forced);
        if (shield != null) remember();
        output.putInt("cells", savedCells);
        output.putFloat("bulk", savedBulk);
        output.store("dmgKeys", Codec.LONG.listOf(), savedKeys);
        output.store("dmgHp", Codec.FLOAT.listOf(), savedHp);
    }
}

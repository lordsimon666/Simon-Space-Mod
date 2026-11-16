package de.simon.spacemod.defense;

import de.simon.spacemod.ModBlocks;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.IEnergyStorage;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.Nullable;

/**
 * Basisschild: Kuppel mit einstellbarem Radius (16 bis 128 Blöcke) um den Generator. Funktioniert wie
 * die Schiffsschilde (Leben je Schildblock, Regeneration, hält Torpedos, Phaser und Explosionen ab),
 * braucht aber keine Emitter – nur Strom, den der Generator speichert (vom Bordnetz, von Batterien oder
 * Generatoren anderer Mods daneben).
 */
public class BaseShieldBlockEntity extends BlockEntity {
    public static final int MIN_RADIUS = 16, MAX_RADIUS = 128;
    /** Stärke wie die Schildstufe 2 (390 Leben je Schildblock). */
    public static final int TIER = 2;
    public static final int CAPACITY = 2_000_000, MAX_IN = 50_000;
    public static final int OFF = 0, UP = 2, NO_POWER = 4;
    /** Strom je Lebenspunkt bei der Regeneration und höchstens so viel Strom je halbe Sekunde dafür. */
    public static final int FE_PER_HP = 10, REGEN_BUDGET = 40_000;

    private int radius = 32;
    private boolean on;
    private int state = OFF;
    private int energy;
    private @Nullable Shield shield;
    private boolean forced;

    private final IEnergyStorage storage = new IEnergyStorage() {
        @Override public int receiveEnergy(int max, boolean simulate) {
            int add = Math.max(0, Math.min(max, Math.min(MAX_IN, CAPACITY - energy)));
            if (!simulate && add > 0) { energy += add; setChanged(); }
            return add;
        }
        @Override public int extractEnergy(int max, boolean simulate) { return 0; }
        @Override public int getEnergyStored() { return energy; }
        @Override public int getMaxEnergyStored() { return CAPACITY; }
        @Override public boolean canExtract() { return false; }
        @Override public boolean canReceive() { return true; }
    };
    private LazyOptional<IEnergyStorage> energyCap = LazyOptional.of(() -> storage);

    public final ContainerData data = new ContainerData() {
        @Override public int get(int i) {
            return switch (i) {
                case 0 -> radius;
                case 1 -> on ? 1 : 0;
                case 2 -> state;
                case 3 -> shield == null ? 0 : Math.round(shield.average() * 100);
                case 4 -> energy / 1000;
                case 5 -> shield == null ? 0 : Math.min(32767, shield.cells / 100);
                default -> shield == null ? 0 : Math.min(32767, shield.upkeep());
            };
        }
        @Override public void set(int i, int v) {}
        @Override public int getCount() { return 7; }
    };

    public BaseShieldBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.BASE_SHIELD_BE.get(), pos, state);
    }

    /** Knöpfe: 0/1 Radius −/+ 1, 10/11 Radius −/+ 8, 4 an/aus. */
    public void press(int id, ServerPlayer player) {
        int old = radius;
        switch (id) {
            case 0 -> radius--;
            case 1 -> radius++;
            case 10 -> radius -= 8;
            case 11 -> radius += 8;
            case 4 -> {
                on = !on;
                if (!on) shutdown();
                player.sendOverlayMessage(Component.translatable(on ? "message.simonspacemod.shield.on" : "message.simonspacemod.shield.off")
                    .withStyle(on ? ChatFormatting.AQUA : ChatFormatting.GRAY));
            }
            default -> { }
        }
        radius = Math.max(MIN_RADIUS, Math.min(MAX_RADIUS, radius));
        if (radius != old) shutdown(); // neue Größe: Kuppel neu aufbauen (mit vollem Leben erst nach Regeneration)
        setChanged();
    }

    public void openMenu(ServerPlayer player) {
        player.openMenu(new SimpleMenuProvider((id, inv, p) -> new BaseShieldMenu(id, inv, data, b -> press(b, player),
            pl -> de.simon.spacemod.flight.Shipyard.distanceSqr(pl, level, worldPosition) < 64 && level.getBlockEntity(worldPosition) == this),
            Component.translatable("block.simonspacemod.base_shield")));
    }

    private void shutdown() {
        if (shield != null) Shields.unregister(shield);
        shield = null;
        state = OFF;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState st, BaseShieldBlockEntity be) {
        if (!(level instanceof ServerLevel sl)) return;
        if (be.forced != be.on) {
            sl.setChunkForced(pos.getX() >> 4, pos.getZ() >> 4, be.on);
            be.forced = be.on;
        }
        if (!be.on) {
            if (be.shield != null) be.shutdown();
            return;
        }
        if (be.shield == null) {
            ShieldShape shape = ShieldShape.dome(be.radius);
            be.shield = new Shield(sl, pos, pos.offset(-be.radius, 0, -be.radius), shape, TIER, java.util.List.of(pos), shape.shellCells());
            Shields.register(be.shield);
        }
        if (level.getGameTime() % 10 != 0) return;
        Shield s = be.shield;
        long upkeep = s.upkeep() * 10L;
        if (be.energy < upkeep) {
            s.up = false;
            be.state = NO_POWER;
            return;
        }
        be.energy -= (int) upkeep;
        s.up = true;
        be.state = UP;
        // Regeneration mit dem übrigen Strom (wie bei den Schiffsschilden: höchstens 2 % je Block und Sekunde)
        int spare = Math.min(be.energy, REGEN_BUDGET);
        float used = s.regenerate((float) spare / FE_PER_HP, 0.5F);
        be.energy -= (int) Math.min(be.energy, used * FE_PER_HP);
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
        radius = Math.max(MIN_RADIUS, Math.min(MAX_RADIUS, input.getIntOr("radius", 32)));
        on = input.getBooleanOr("on", false);
        forced = input.getBooleanOr("forced", false);
        energy = input.getIntOr("energy", 0);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putInt("radius", radius);
        output.putBoolean("on", on);
        output.putBoolean("forced", forced);
        output.putInt("energy", energy);
    }

    @Override
    public <T> @NotNull LazyOptional<T> getCapability(@NotNull Capability<T> c, @Nullable Direction side) {
        return c == ForgeCapabilities.ENERGY ? energyCap.cast() : super.getCapability(c, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        energyCap.invalidate();
    }

    @Override
    public void reviveCaps() {
        super.reviveCaps();
        energyCap = LazyOptional.of(() -> storage);
    }
}

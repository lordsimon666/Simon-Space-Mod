package de.simon.spacemod.item;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.event.TickEvent;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.Nullable;

/**
 * Raumanzug (Brustteil mit Schubdüsen): solange er geladen ist, kann man wie im Kreativmodus
 * fliegen (doppelt Springen). Fliegen verbraucht Ladung; geladen wird an der Ladestation (oder mit
 * jedem Forge-Energie-Ladegerät). Ist er leer, endet der Flug mit kurzem Sanftem Fall.
 */
public class SpaceSuitItem extends Item {
    public static final int CAPACITY = 240_000;
    public static final int CHARGE_RATE = 2_000;
    /** Verbrauch je Tick im Flug (voll geladen: 10 Minuten Flug). */
    public static final int FLY_COST = 20;

    private static final Set<UUID> GRANTED = new HashSet<>();

    public SpaceSuitItem(Item.Properties properties) {
        super(properties);
    }

    public static void init() {
        TickEvent.PlayerTickEvent.Post.BUS.addListener(e -> {
            if (e.player() instanceof ServerPlayer sp) tick(sp);
        });
    }

    private static void tick(ServerPlayer p) {
        if (p.isCreative() || p.isSpectator()) {
            GRANTED.remove(p.getUUID());
            return;
        }
        ItemStack chest = p.getItemBySlot(EquipmentSlot.CHEST);
        boolean suit = chest.getItem() instanceof SpaceSuitItem && HandWeaponItem.energy(chest) > 0;
        var ab = p.getAbilities();
        if (suit) {
            if (!ab.mayfly) {
                ab.mayfly = true;
                GRANTED.add(p.getUUID());
                p.onUpdateAbilities();
            }
            if (ab.flying) {
                HandWeaponItem.setEnergy(chest, HandWeaponItem.energy(chest) - FLY_COST);
                p.fallDistance = 0;
                if (HandWeaponItem.energy(chest) <= 0)
                    p.sendOverlayMessage(Component.translatable("message.simonspacemod.suit.empty").withStyle(ChatFormatting.RED));
            }
        } else if (GRANTED.remove(p.getUUID())) {
            boolean wasFlying = ab.flying;
            ab.mayfly = false;
            ab.flying = false;
            p.onUpdateAbilities();
            if (wasFlying) p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 20 * 6));
        }
    }

    public static boolean isSuit(Player p) {
        return p.getItemBySlot(EquipmentSlot.CHEST).getItem() instanceof SpaceSuitItem;
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return true;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return Math.round(13F * HandWeaponItem.energy(stack) / CAPACITY);
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return Mth.hsvToRgb(0.55F, 0.7F, 1F);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> out, TooltipFlag flag) {
        out.accept(Component.translatable("tooltip.simonspacemod.weapon.energy", HandWeaponItem.energy(stack), CAPACITY).withStyle(ChatFormatting.AQUA));
        out.accept(Component.translatable("tooltip.simonspacemod.suit").withStyle(ChatFormatting.GRAY));
    }

    /** Energiezelle des Anzugs als Forge-Energie (Ladestation und Ladegeräte anderer Mods). */
    public static final class Energy implements ICapabilityProvider, IEnergyStorage {
        private final ItemStack stack;
        private final LazyOptional<IEnergyStorage> holder = LazyOptional.of(() -> this);

        public Energy(ItemStack stack) {
            this.stack = stack;
        }

        @Override
        public <T> @NotNull LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
            return cap == ForgeCapabilities.ENERGY ? holder.cast() : LazyOptional.empty();
        }

        @Override
        public int receiveEnergy(int max, boolean simulate) {
            int e = HandWeaponItem.energy(stack);
            int add = Math.max(0, Math.min(max, Math.min(CHARGE_RATE, CAPACITY - e)));
            if (!simulate && add > 0) HandWeaponItem.setEnergy(stack, e + add);
            return add;
        }

        @Override public int extractEnergy(int max, boolean simulate) { return 0; }
        @Override public int getEnergyStored() { return HandWeaponItem.energy(stack); }
        @Override public int getMaxEnergyStored() { return CAPACITY; }
        @Override public boolean canExtract() { return false; }
        @Override public boolean canReceive() { return true; }
    }
}

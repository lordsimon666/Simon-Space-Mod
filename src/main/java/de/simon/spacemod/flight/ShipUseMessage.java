package de.simon.spacemod.flight;

import de.simon.spacemod.block.entity.ReactorControllerBlockEntity;
import de.simon.spacemod.entity.FlyingShipEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.Nameable;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.DispenserMenu;
import net.minecraft.world.inventory.HopperMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.network.CustomPayloadEvent;

/**
 * Blöcke im fliegenden Schiff benutzen. Client -> Server: angeklickter Block (lokal). Kisten,
 * Fässer, Trichter, Spender und der Reaktor öffnen ihr Menü (z. B. um Uran nachzulegen).
 */
public record ShipUseMessage(int entityId, BlockPos local) {
    public static final double REACH = 5.5;

    public static final StreamCodec<RegistryFriendlyByteBuf, ShipUseMessage> STREAM_CODEC = StreamCodec.ofMember(
        (msg, buf) -> {
            buf.writeVarInt(msg.entityId());
            buf.writeBlockPos(msg.local());
        },
        buf -> new ShipUseMessage(buf.readVarInt(), buf.readBlockPos()));

    public static void handle(ShipUseMessage msg, CustomPayloadEvent.Context ctx) {
        ServerPlayer player = ctx.getSender();
        if (player == null || !(player.getVehicle() instanceof FlyingShipEntity ship) || ship.getId() != msg.entityId()) return;
        ShipStructure s = ship.structure();
        if (s == null || s.indexOf(msg.local()) < 0) return;
        Vec3 eye = ship.seat(player).add(0, player.getEyeHeight(), 0);
        if (eye.distanceTo(Vec3.atLowerCornerOf(msg.local())) > REACH + 1) return;
        BlockEntity be = ship.liveAt(msg.local());
        java.util.function.Predicate<Player> valid = p -> p.getVehicle() == ship && ship.isAlive();
        if (be instanceof ReactorControllerBlockEntity reactor) {
            reactor.openMenu(player, valid);
            return;
        }
        if (be instanceof Container c) {
            Container box = new ShipContainer(c, valid);
            Component title = be instanceof Nameable n ? n.getDisplayName() : be.getBlockState().getBlock().getName();
            SimpleMenuProvider provider = switch (c.getContainerSize()) {
                case 27 -> new SimpleMenuProvider((id, inv, p) -> ChestMenu.threeRows(id, inv, box), title);
                case 54 -> new SimpleMenuProvider((id, inv, p) -> ChestMenu.sixRows(id, inv, box), title);
                case 9 -> new SimpleMenuProvider((id, inv, p) -> new DispenserMenu(id, inv, box), title);
                case 5 -> new SimpleMenuProvider((id, inv, p) -> new HopperMenu(id, inv, box), title);
                default -> null;
            };
            if (provider != null) {
                player.openMenu(provider);
                return;
            }
        }
        player.sendOverlayMessage(Component.translatable("message.simonspacemod.ship.no_use"));
    }

    /** Leitet an den Block im Schiff weiter; gültig, solange der Spieler mitfliegt (ohne Öffnen-Animation). */
    private record ShipContainer(Container inner, java.util.function.Predicate<Player> valid) implements Container {
        @Override public int getContainerSize() { return inner.getContainerSize(); }
        @Override public boolean isEmpty() { return inner.isEmpty(); }
        @Override public ItemStack getItem(int slot) { return inner.getItem(slot); }
        @Override public ItemStack removeItem(int slot, int count) { return inner.removeItem(slot, count); }
        @Override public ItemStack removeItemNoUpdate(int slot) { return inner.removeItemNoUpdate(slot); }
        @Override public void setItem(int slot, ItemStack stack) { inner.setItem(slot, stack); }
        @Override public int getMaxStackSize() { return inner.getMaxStackSize(); }
        @Override public boolean canPlaceItem(int slot, ItemStack stack) { return inner.canPlaceItem(slot, stack); }
        @Override public void setChanged() {}
        @Override public boolean stillValid(Player player) { return valid.test(player); }
        @Override public void clearContent() { inner.clearContent(); }
    }

}

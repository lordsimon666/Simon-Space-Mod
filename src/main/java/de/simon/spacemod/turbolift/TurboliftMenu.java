package de.simon.spacemod.turbolift;

import de.simon.spacemod.ModBlocks;
import de.simon.spacemod.block.Beam;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Menü des Turbolifts (ohne Inventar). Der Server schickt beim Öffnen die Liste der Decks mit;
 * ein Klick auf ein Deck kommt als normaler Menü-Button-Klick zurück zum Server.
 */
public class TurboliftMenu extends AbstractContainerMenu {
    public record Entry(BlockPos pos, String name) {}

    public final BlockPos liftPos;
    public final String name;
    public final String network;
    public final List<Entry> destinations;

    /** Server-Seite. */
    public TurboliftMenu(int id, Inventory inv, BlockPos liftPos, TurboliftData.Stop self, List<TurboliftData.Stop> stops) {
        super(ModBlocks.TURBOLIFT_MENU.get(), id);
        this.liftPos = liftPos;
        this.name = self.name();
        this.network = self.network();
        this.destinations = new ArrayList<>();
        for (TurboliftData.Stop s : stops) destinations.add(new Entry(s.pos(), s.name()));
    }

    /** Client-Seite: Daten kommen aus dem Puffer. */
    public TurboliftMenu(int id, Inventory inv, FriendlyByteBuf buf) {
        super(ModBlocks.TURBOLIFT_MENU.get(), id);
        this.liftPos = buf.readBlockPos();
        this.name = buf.readUtf();
        this.network = buf.readUtf();
        int n = buf.readVarInt();
        this.destinations = new ArrayList<>();
        for (int i = 0; i < n; i++) destinations.add(new Entry(buf.readBlockPos(), buf.readUtf()));
    }

    public static void write(FriendlyByteBuf buf, BlockPos liftPos, TurboliftData.Stop self, List<TurboliftData.Stop> stops) {
        buf.writeBlockPos(liftPos);
        buf.writeUtf(self.name());
        buf.writeUtf(self.network());
        buf.writeVarInt(stops.size());
        for (TurboliftData.Stop s : stops) {
            buf.writeBlockPos(s.pos());
            buf.writeUtf(s.name());
        }
    }

    /** Button-ID = Index des Ziel-Decks. */
    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (!(player instanceof ServerPlayer sp) || id < 0 || id >= destinations.size()) return false;
        Entry dest = destinations.get(id);
        if (dest.pos().equals(liftPos)) return false;
        ServerLevel level = sp.level();
        BlockState target = level.getBlockState(dest.pos());
        if (!target.is(ModBlocks.TURBOLIFT.get())) {
            TurboliftData.get(level).remove(dest.pos());
            sp.sendOverlayMessage(Component.translatable("message.simonspacemod.turbolift.gone"));
            return false;
        }
        Direction out = target.getValue(HorizontalDirectionalBlock.FACING);
        BlockPos stand = dest.pos().relative(out);
        sp.closeContainer();
        Beam.teleport(sp, level, stand.getX() + 0.5, stand.getY(), stand.getZ() + 0.5, out.toYRot());
        sp.sendOverlayMessage(Component.translatable("message.simonspacemod.turbolift.arrived", dest.name()));
        return true;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slot) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return player.distanceToSqr(liftPos.getX() + 0.5, liftPos.getY() + 0.5, liftPos.getZ() + 0.5) < 64
            && player.level().getBlockState(liftPos).is(ModBlocks.TURBOLIFT.get());
    }
}

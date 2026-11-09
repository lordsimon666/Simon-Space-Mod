package de.simon.spacemod.turbolift;

import de.simon.spacemod.ModBlocks;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;

/** Öffnet das Turbolift-Menü mit allen Decks des Netzes. */
public final class TurboliftBlockHelper {
    private TurboliftBlockHelper() {}

    public static void openMenu(ServerPlayer player, ServerLevel level, BlockPos pos) {
        if (!level.getBlockState(pos).is(ModBlocks.TURBOLIFT.get())) return;
        TurboliftData data = TurboliftData.get(level);
        TurboliftData.Stop self = data.register(pos);
        // Halte aussortieren, die es nicht mehr gibt (nur in geladenen Chunks prüfbar)
        for (TurboliftData.Stop s : data.network(self.network()))
            if (level.isLoaded(s.pos()) && !level.getBlockState(s.pos()).is(ModBlocks.TURBOLIFT.get())) data.remove(s.pos());
        List<TurboliftData.Stop> stops = data.network(self.network());
        player.openMenu(new SimpleMenuProvider(
                (id, inv, p) -> new TurboliftMenu(id, inv, pos, self, stops),
                Component.translatable("gui.simonspacemod.turbolift")),
            buf -> TurboliftMenu.write(buf, pos, self, stops));
    }
}

package de.simon.spacemod.guide;

import de.simon.spacemod.ModBlocks;
import de.simon.spacemod.block.ReactorControllerBlock;
import de.simon.spacemod.storage.CrateTier;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Ein Multiblock, den das Handbuch in 3D zeigt und als Bauplan in die Welt stellen kann: seine Blöcke,
 * die unterste Schicht bei y = 0 und die Mitte bei x = z = 0, damit er auf dem Block steht, auf den er
 * gesetzt wird.
 */
public record GuideStructure(String id, List<Part> parts) {
    /** Ein Block: wohin, was und ob der Spieler ihn setzt. */
    public record Part(BlockPos pos, Supplier<BlockState> state, boolean build) {}

    /** Kleinster Kernreaktor: hohler 3×3×3-Würfel aus Reaktorhülle, Glas in den Seitenmitten, vorne der Steuerblock. */
    public static final GuideStructure REACTOR = reactor();
    /** Kleinste Großkiste: hohler 3×3×3-Würfel aus Holz-Kistenblöcken. */
    public static final GuideStructure CRATE = crate();

    private static GuideStructure reactor() {
        List<Part> parts = new ArrayList<>();
        for (int x = -1; x <= 1; x++)
            for (int y = 0; y <= 2; y++)
                for (int z = -1; z <= 1; z++) {
                    int edges = (Math.abs(x) == 1 ? 1 : 0) + (y != 1 ? 1 : 0) + (Math.abs(z) == 1 ? 1 : 0);
                    if (edges == 0) continue; // innen bleibt frei
                    BlockPos p = new BlockPos(x, y, z);
                    if (x == 0 && y == 1 && z == -1)
                        parts.add(new Part(p, () -> ModBlocks.REACTOR_CONTROLLER.get().defaultBlockState()
                            .setValue(ReactorControllerBlock.FACING, Direction.NORTH), true));
                    else if (edges == 1)
                        parts.add(new Part(p, () -> ModBlocks.REACTOR_GLASS.get().defaultBlockState(), true));
                    else
                        parts.add(new Part(p, () -> ModBlocks.REACTOR_CASING.get().defaultBlockState(), true));
                }
        return new GuideStructure("reactor", List.copyOf(parts));
    }

    private static GuideStructure crate() {
        List<Part> parts = new ArrayList<>();
        for (int x = -1; x <= 1; x++)
            for (int y = 0; y <= 2; y++)
                for (int z = -1; z <= 1; z++)
                    if (x != 0 || y != 1 || z != 0)
                        parts.add(new Part(new BlockPos(x, y, z), () -> ModBlocks.CRATES.get(CrateTier.WOOD).get().defaultBlockState(), true));
        return new GuideStructure("crate", List.copyOf(parts));
    }

    /** Wie viele Schichten hoch. */
    public int height() {
        int h = 0;
        for (Part part : parts) h = Math.max(h, part.pos().getY() + 1);
        return h;
    }
}

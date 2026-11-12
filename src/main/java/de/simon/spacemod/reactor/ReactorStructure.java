package de.simon.spacemod.reactor;

import de.simon.spacemod.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Multiblock: ein Würfel (3 bis 8 Blöcke Kantenlänge), dessen Hülle ganz aus Reaktorhülle,
 * Reaktorglas und genau einem Steuerblock besteht. Der Steuerblock sitzt in einer Seitenfläche und
 * schaut nach außen; der Würfel liegt hinter ihm. Das Innere darf beliebig sein.
 */
public final class ReactorStructure {
    private ReactorStructure() {}

    public static boolean isPart(BlockState st) {
        return st.is(ModBlocks.REACTOR_CASING.get()) || st.is(ModBlocks.REACTOR_GLASS.get()) || st.is(ModBlocks.REACTOR_CONTROLLER.get());
    }

    /** @return Kantenlänge des größten gültigen Würfels oder 0 */
    public static int find(Level level, BlockPos controller, Direction facing) {
        Direction in = facing.getOpposite(), u = facing.getClockWise();
        for (int n = ReactorMath.MAX; n >= ReactorMath.MIN; n--)
            for (int a = 0; a < n; a++)
                for (int b = 0; b < n; b++)
                    if (valid(level, controller, controller.relative(u, -a).below(b), u, in, n)) return n;
        return 0;
    }

    private static boolean valid(Level level, BlockPos controller, BlockPos corner, Direction u, Direction in, int n) {
        for (int i = 0; i < n; i++)
            for (int j = 0; j < n; j++)
                for (int k = 0; k < n; k++) {
                    boolean shell = i == 0 || i == n - 1 || j == 0 || j == n - 1 || k == 0 || k == n - 1;
                    if (!shell) continue;
                    BlockPos p = corner.relative(u, i).above(j).relative(in, k);
                    if (!level.isLoaded(p)) return false;
                    BlockState st = level.getBlockState(p);
                    if (!isPart(st)) return false;
                    if (st.is(ModBlocks.REACTOR_CONTROLLER.get()) && !p.equals(controller)) return false; // nur ein Steuerblock
                }
        return true;
    }
}

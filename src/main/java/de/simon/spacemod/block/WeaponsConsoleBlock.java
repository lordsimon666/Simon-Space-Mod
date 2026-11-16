package de.simon.spacemod.block;

import com.mojang.serialization.MapCodec;
import de.simon.spacemod.weapons.CameraControl;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Waffenstation: Rechtsklick wechselt in die Kamerasicht außen am Schiff. Dort wechselt Rechtsklick die
 * Kamera, Linksklick feuert die Phaser auf das Fadenkreuz, Shift kehrt zurück. Variante = Stil der Epoche.
 */
public class WeaponsConsoleBlock extends VariantFacingBlock {
    public static final VoxelShape SHAPE = box(0, 0, 2, 16, 13, 14);

    public WeaponsConsoleBlock(BlockBehaviour.Properties properties) {
        super(properties, SHAPE);
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return simpleCodec(WeaponsConsoleBlock::new);
    }

    /** Redstone-Signal: automatische Abwehr (Phaser Stufe 1 auf feindliche Kreaturen), solange es anliegt. */
    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, net.minecraft.world.level.block.Block block,
                                   net.minecraft.world.level.redstone.@org.jspecify.annotations.Nullable Orientation orientation, boolean movedByPiston) {
        super.neighborChanged(state, level, pos, block, orientation, movedByPiston);
        if (!level.isClientSide() && level.hasNeighborSignal(pos)) level.scheduleTick(pos, this, 1);
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, net.minecraft.util.RandomSource random) {
        if (!level.hasNeighborSignal(pos)) return;
        de.simon.spacemod.weapons.PhaserControl.autoFire(level, pos);
        level.scheduleTick(pos, this, 10);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level instanceof ServerLevel server && player instanceof ServerPlayer sp) CameraControl.enter(server, pos, sp);
        return InteractionResult.SUCCESS;
    }
}

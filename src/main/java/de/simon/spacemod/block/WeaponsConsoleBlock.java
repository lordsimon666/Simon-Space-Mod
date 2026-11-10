package de.simon.spacemod.block;

import com.mojang.serialization.MapCodec;
import de.simon.spacemod.weapons.PhaserControl;
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
 * Waffenstation: Rechtsklick feuert die Phaser des Schiffs auf das Ziel, das man anschaut
 * (Kreatur im Blick, sonst der Block dahinter). Variante = Stil der Epoche.
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

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level instanceof ServerLevel server && player instanceof ServerPlayer sp) PhaserControl.fire(server, pos, sp);
        return InteractionResult.SUCCESS;
    }
}

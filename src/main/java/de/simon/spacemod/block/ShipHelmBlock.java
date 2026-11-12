package de.simon.spacemod.block;

import com.mojang.serialization.MapCodec;
import de.simon.spacemod.flight.ShipAssembler;
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

/**
 * Steuerkonsole für eigene Schiffe: Rechtsklick macht die Struktur, in der sie steht, zu einem
 * fliegenden Schiff (Bug in Blickrichtung des Piloten hinter der Konsole). Nicht auf den Enterprises.
 */
public class ShipHelmBlock extends FacingBlock {
    public static final MapCodec<ShipHelmBlock> CODEC = simpleCodec(ShipHelmBlock::new);

    public ShipHelmBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level instanceof ServerLevel server && player instanceof ServerPlayer sp && !sp.isPassenger())
            ShipAssembler.assemble(server, pos, state.getValue(FACING).getOpposite(), sp);
        return InteractionResult.SUCCESS;
    }
}

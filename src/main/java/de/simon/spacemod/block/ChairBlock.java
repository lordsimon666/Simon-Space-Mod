package de.simon.spacemod.block;

import com.mojang.serialization.MapCodec;
import de.simon.spacemod.ModBlocks;
import de.simon.spacemod.entity.SeatEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Stuhl, auf den man sich mit Rechtsklick setzen kann. */
public class ChairBlock extends ShapedFacingBlock {
    public static final VoxelShape CHAIR_SHAPE = Shapes.or(box(6, 0, 6, 10, 7, 10), box(3, 7, 3, 13, 9, 13), box(3, 9, 11, 13, 20, 14));
    public static final MapCodec<ChairBlock> CODEC = simpleCodec(p -> new ChairBlock(p, CHAIR_SHAPE));

    public ChairBlock(BlockBehaviour.Properties properties, VoxelShape shape) {
        super(properties, shape);
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        return sit(state, level, pos, player);
    }

    /** Kann man auf diesem Block sitzen? */
    public static boolean isSeat(BlockState state) {
        return state.getBlock() instanceof ChairBlock || state.getBlock() instanceof CaptainsChairBlock;
    }

    /** Setzt den Spieler auf den Stuhl, mit Blick in die Richtung, in die der Stuhl zeigt. */
    public static InteractionResult sit(BlockState state, Level level, BlockPos pos, Player player) {
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        if (player.isPassenger() || player.isShiftKeyDown()) return InteractionResult.PASS;
        if (!level.getEntitiesOfClass(SeatEntity.class, new AABB(pos)).isEmpty()) return InteractionResult.PASS; // besetzt
        float yaw = state.getValue(FACING).toYRot();
        SeatEntity seat = new SeatEntity(ModBlocks.SEAT.get(), level);
        seat.snapTo(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, yaw, 0);
        level.addFreshEntity(seat);
        player.startRiding(seat);
        player.setYRot(yaw);
        player.setYHeadRot(yaw);
        return InteractionResult.SUCCESS;
    }
}

package de.simon.spacemod.block;

import com.mojang.serialization.MapCodec;
import de.simon.spacemod.ModBlocks;
import de.simon.spacemod.block.entity.ChargingStationBlockEntity;
import de.simon.spacemod.block.entity.WeaponHolderBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * Ladestation: ein Schrank mit Glasfront. Ein Phaser steht sichtbar darin, ein Gewehr hängt außen
 * an der Seite. Geladen wird aus dem Bordnetz.
 */
public class ChargingStationBlock extends WeaponHolderBlock {
    private static final VoxelShape SHAPE = Block.box(1, 0, 1, 15, 16, 15);
    /** Rote Warnleuchte: an = keine Energie; blinkt, wenn ohne Strom geladen werden soll. */
    public static final net.minecraft.world.level.block.state.properties.BooleanProperty RED =
        net.minecraft.world.level.block.state.properties.BooleanProperty.create("red");

    public ChargingStationBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(RED, false));
    }

    @Override
    protected void createBlockStateDefinition(net.minecraft.world.level.block.state.StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(RED);
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return simpleCodec(ChargingStationBlock::new);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected int slotAt(BlockState state, BlockPos pos, Vec3 hit) {
        return 0;
    }

    @Override
    protected InteractionResult onEmpty(WeaponHolderBlockEntity be, Level level, Player player) {
        if (be instanceof ChargingStationBlockEntity cs && player instanceof ServerPlayer sp)
            sp.sendOverlayMessage(Component.translatable("message.simonspacemod.charger.status", cs.stored(),
                ChargingStationBlockEntity.CAPACITY, cs.gridOutput()));
        return InteractionResult.SUCCESS;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ChargingStationBlockEntity(pos, state);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide() || type != ModBlocks.CHARGING_STATION_BE.get() ? null
            : (BlockEntityTicker<T>) (BlockEntityTicker<ChargingStationBlockEntity>) ChargingStationBlockEntity::serverTick;
    }
}

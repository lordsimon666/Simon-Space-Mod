package de.simon.spacemod.defense;

import com.mojang.serialization.MapCodec;
import de.simon.spacemod.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;

/** Basisschild-Generator: erzeugt eine Schildkuppel (Radius 16 bis 128), Stufe 1 bis 6 wie die Schiffsschilde. */
public class BaseShieldBlock extends BaseEntityBlock {
    /** Schildstufe 1 (NCC-1701) bis 6 (NCC-1701-E), wie bei den Schildemittern. */
    public final int tier;

    public BaseShieldBlock(BlockBehaviour.Properties properties, int tier) {
        super(properties);
        this.tier = tier;
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return simpleCodec(p -> new BaseShieldBlock(p, tier));
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new BaseShieldBlockEntity(pos, state);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide() && player instanceof ServerPlayer sp && level.getBlockEntity(pos) instanceof BaseShieldBlockEntity be) be.openMenu(sp);
        return InteractionResult.SUCCESS;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide() || type != ModBlocks.BASE_SHIELD_BE.get() ? null
            : (BlockEntityTicker<T>) (BlockEntityTicker<BaseShieldBlockEntity>) BaseShieldBlockEntity::serverTick;
    }
}

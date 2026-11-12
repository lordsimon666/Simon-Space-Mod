package de.simon.spacemod.power;

import com.mojang.serialization.MapCodec;
import de.simon.spacemod.ModBlocks;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import org.jspecify.annotations.Nullable;

/** Batterie-Block; die Seiten zeigen den Ladestand in 5 Stufen. */
public class BatteryBlock extends Block implements EntityBlock {
    public static final IntegerProperty CHARGE = IntegerProperty.create("charge", 0, 4);
    public static final MapCodec<BatteryBlock> CODEC = simpleCodec(BatteryBlock::new);

    public BatteryBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(CHARGE, 0));
    }

    public static int charge(int energy) {
        return energy <= 0 ? 0 : Math.min(4, 1 + energy * 4 / (BatteryBlockEntity.CAPACITY + 1));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(CHARGE);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new BatteryBlockEntity(pos, state);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide() || type != ModBlocks.BATTERY_BE.get() ? null
            : (BlockEntityTicker<T>) (BlockEntityTicker<BatteryBlockEntity>) BatteryBlockEntity::serverTick;
    }

    /** Tooltip mit Ladestand (über das Item). */
    public static void tooltip(ItemStack stack, Consumer<Component> out) {
        int e = stack.getOrDefault(ModBlocks.WEAPON_ENERGY.get(), 0);
        out.accept(Component.translatable("tooltip.simonspacemod.weapon.energy", e, BatteryBlockEntity.CAPACITY).withStyle(ChatFormatting.AQUA));
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moved) {
        super.onPlace(state, level, pos, old, moved);
        if (level instanceof net.minecraft.server.level.ServerLevel sl) PowerNetwork.addSeed(sl, pos);
    }

}

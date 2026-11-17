package de.simon.spacemod.flight;

import com.mojang.serialization.MapCodec;
import de.simon.spacemod.block.FacingBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Trägheitsdämpfer-Steuerung: stellt ein, wie stark ein fliegendes Schiff ohne Schub abbremst – 0 bis 100 %
 * in Zehnerschritten (Rechtsklick +10 %, Schleichen + Rechtsklick −10 %). Ohne Steuerung arbeiten die
 * Dämpfer mit 10 % und kosten nichts; jede Stufe darüber kostet Strom aus dem Bordnetz (100 % = 1000 FE/t).
 */
public class InertialDamperBlock extends FacingBlock {
    public static final MapCodec<InertialDamperBlock> CODEC = simpleCodec(InertialDamperBlock::new);
    /** Stärke in Zehnteln (0 = aus, 10 = 100 %). */
    public static final IntegerProperty LEVEL = IntegerProperty.create("level", 0, 10);

    public InertialDamperBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(LEVEL, ShipPhysics.DEFAULT_DAMPER));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(LEVEL);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide() || !(player instanceof ServerPlayer sp)) return InteractionResult.SUCCESS;
        int l = Math.max(0, Math.min(10, state.getValue(LEVEL) + (player.isShiftKeyDown() ? -1 : 1)));
        level.setBlock(pos, state.setValue(LEVEL, l), Block.UPDATE_ALL);
        level.playSound(null, pos, SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.BLOCKS, 0.5F, 0.8F + l * 0.06F);
        sp.sendOverlayMessage(Component.translatable("message.simonspacemod.damper.level", l * 10, ShipPhysics.damperCost(l)));
        return InteractionResult.SUCCESS;
    }
}

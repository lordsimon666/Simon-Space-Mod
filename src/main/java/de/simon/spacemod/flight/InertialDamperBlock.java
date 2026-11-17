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
 * in Zehnerschritten, eingestellt im Menü mit Schieberegler. Ohne Steuerung arbeiten die
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

    /**
     * Rechtsklick öffnet das Menü mit Schieberegler (auch im Flug, über den echten Block in der Werft).
     * Der Wert wird live aus dem Block gelesen, Änderungen setzen die Stufe direkt im Block.
     */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide() || !(player instanceof ServerPlayer sp)) return InteractionResult.SUCCESS;
        net.minecraft.world.inventory.ContainerData data = new net.minecraft.world.inventory.ContainerData() {
            @Override
            public int get(int i) {
                BlockState st = level.getBlockState(pos);
                return st.getBlock() instanceof InertialDamperBlock ? st.getValue(LEVEL) : 0;
            }

            @Override
            public void set(int i, int v) {}

            @Override
            public int getCount() {
                return 1;
            }
        };
        sp.openMenu(new net.minecraft.world.SimpleMenuProvider((id, inv, p) -> new de.simon.spacemod.defense.ConsoleMenu(
            de.simon.spacemod.ModBlocks.INERTIAL_DAMPER_MENU.get(), id, data, l -> setLevel(level, pos, l),
            pl -> Shipyard.distanceSqr(pl, level, pos) < 64 && level.getBlockState(pos).getBlock() instanceof InertialDamperBlock),
            Component.translatable("block.simonspacemod.inertial_damper")));
        return InteractionResult.SUCCESS;
    }

    private static void setLevel(Level level, BlockPos pos, int l) {
        BlockState st = level.getBlockState(pos);
        if (!(st.getBlock() instanceof InertialDamperBlock) || l < 0 || l > 10 || st.getValue(LEVEL) == l) return;
        level.setBlock(pos, st.setValue(LEVEL, l), Block.UPDATE_ALL);
        level.playSound(null, pos, SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.BLOCKS, 0.4F, 0.8F + l * 0.06F);
    }
}

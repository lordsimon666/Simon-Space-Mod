package de.simon.spacemod.defense;

import com.mojang.serialization.MapCodec;
import de.simon.spacemod.ModBlocks;
import de.simon.spacemod.block.FacingBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;

/** Konsole mit Menü: Energieverteilung (Maschinenraum) oder Schildsteuerung (Brücke). */
public class ConsoleBlock extends FacingBlock implements EntityBlock {
    public enum Kind { POWER, SHIELD }

    public final Kind kind;

    public ConsoleBlock(BlockBehaviour.Properties properties, Kind kind) {
        super(properties);
        this.kind = kind;
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return simpleCodec(p -> new ConsoleBlock(p, kind));
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide() && player instanceof ServerPlayer sp) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof PowerConsoleBlockEntity p) p.openMenu(sp);
            else if (be instanceof ShieldConsoleBlockEntity s) s.openMenu(sp);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return kind == Kind.POWER ? new PowerConsoleBlockEntity(pos, state) : new ShieldConsoleBlockEntity(pos, state);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide()) return null;
        if (type == ModBlocks.POWER_CONSOLE_BE.get())
            return (BlockEntityTicker<T>) (BlockEntityTicker<PowerConsoleBlockEntity>) PowerConsoleBlockEntity::serverTick;
        if (type == ModBlocks.SHIELD_CONSOLE_BE.get())
            return (BlockEntityTicker<T>) (BlockEntityTicker<ShieldConsoleBlockEntity>) ShieldConsoleBlockEntity::serverTick;
        return null;
    }
}

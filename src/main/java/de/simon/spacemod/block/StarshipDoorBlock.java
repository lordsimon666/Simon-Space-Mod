package de.simon.spacemod.block;

import com.mojang.serialization.MapCodec;
import de.simon.spacemod.ModBlocks;
import de.simon.spacemod.block.entity.DoorBlockEntity;
import de.simon.spacemod.item.KeycardItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * Schiebetür 2 x 2 Blöcke im Stil einer Epoche: öffnet sich automatisch, wenn ein berechtigter
 * Spieler davorsteht (nicht beim Schleichen). Sicherheits- und Kommandotüren brauchen die passende
 * Schlüsselkarte im Inventar. Shift-Rechtsklick mit einer Karte verschließt/öffnet die Tür.
 */
public class StarshipDoorBlock extends FacingBlock implements EntityBlock {
    public enum Kind { NORMAL, SECURITY, COMMAND }

    /** 0 = unten links, 1 = unten rechts, 2 = oben links, 3 = oben rechts (von vorne gesehen). */
    public static final IntegerProperty PART = IntegerProperty.create("part", 0, 3);
    public static final BooleanProperty OPEN = BlockStateProperties.OPEN;

    private static final VoxelShape PANEL_Z = Block.box(0, 0, 6, 16, 16, 10);
    private static final VoxelShape PANEL_X = Block.box(6, 0, 0, 10, 16, 16);

    public final Kind kind;

    public StarshipDoorBlock(BlockBehaviour.Properties properties, Kind kind) {
        super(properties);
        this.kind = kind;
        registerDefaultState(defaultBlockState().setValue(PART, 0).setValue(OPEN, false));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return simpleCodec(p -> new StarshipDoorBlock(p, kind));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, PART, OPEN);
    }

    /** Richtung "rechts" von vorne gesehen. */
    public static Direction right(BlockState state) {
        return state.getValue(FACING).getCounterClockWise();
    }

    public static BlockPos partPos(BlockPos origin, BlockState state, int part) {
        return origin.relative(right(state), part % 2).above(part / 2);
    }

    public static BlockPos origin(BlockPos pos, BlockState state) {
        int part = state.getValue(PART);
        return pos.relative(right(state), -(part % 2)).below(part / 2);
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState st = super.getStateForPlacement(context).setValue(PART, 0).setValue(OPEN, false);
        Level level = context.getLevel();
        for (int p = 1; p < 4; p++) {
            BlockPos q = partPos(context.getClickedPos(), st, p);
            if (q.getY() > level.getMaxY() || !level.getBlockState(q).canBeReplaced(context)) return null;
        }
        return st;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity by, ItemStack stack) {
        super.setPlacedBy(level, pos, state, by, stack);
        for (int p = 1; p < 4; p++) level.setBlock(partPos(pos, state, p), state.setValue(PART, p), Block.UPDATE_ALL);
    }

    /** Fehlt ein Teil, verschwindet die ganze Tür (ohne weitere Drops). */
    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
        if (level.getBlockState(pos).is(this)) return;
        BlockPos o = origin(pos, state);
        for (int p = 0; p < 4; p++) {
            BlockPos q = partPos(o, state, p);
            BlockState other = level.getBlockState(q);
            if (other.is(this) && other.getValue(PART) == p && other.getValue(FACING) == state.getValue(FACING))
                level.setBlock(q, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS);
        }
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(FACING).getAxis() == Direction.Axis.Z ? PANEL_Z : PANEL_X;
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(OPEN) ? Shapes.empty() : getShape(state, level, pos, context);
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                          InteractionHand hand, BlockHitResult hit) {
        if (!(stack.getItem() instanceof KeycardItem card) || !player.isShiftKeyDown()) return InteractionResult.TRY_WITH_EMPTY_HAND;
        if (!level.isClientSide() && level.getBlockEntity(origin(pos, state)) instanceof DoorBlockEntity be) be.toggleLock(player, card.level);
        return InteractionResult.SUCCESS;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide() && level.getBlockEntity(origin(pos, state)) instanceof DoorBlockEntity be) be.showStatus(player);
        return InteractionResult.SUCCESS;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return state.getValue(PART) == 0 ? new DoorBlockEntity(pos, state) : null;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide() || type != ModBlocks.DOOR_BE.get() ? null
            : (BlockEntityTicker<T>) (BlockEntityTicker<DoorBlockEntity>) DoorBlockEntity::serverTick;
    }
}

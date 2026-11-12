package de.simon.spacemod.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * Luftschleuse: 2 breit x 2 hoch x 2 tief (bzw. 1 breit) mit je einer Doppeltür (bzw. einfachen Tür)
 * vorne und hinten. Ist eine Seite offen, ist die andere immer zu. Öffnet nur auf Rechtsklick oder
 * Redstone, nie von selbst. Einzige Tür, die bei fliegenden Schiffen als Hülle zählt.
 */
public class AirlockBlock extends HorizontalDirectionalBlock {
    public enum Side implements StringRepresentable {
        NONE, FRONT, BACK;

        @Override
        public String getSerializedName() {
            return name().toLowerCase();
        }
    }

    /** Teil: Breite + 2 * Höhe + 4 * Tiefe. */
    public static final IntegerProperty PART = IntegerProperty.create("part", 0, 7);
    public static final EnumProperty<Side> OPEN = EnumProperty.create("open", Side.class);
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;

    private static final VoxelShape N = Block.box(0, 0, 0, 16, 16, 2), S = Block.box(0, 0, 14, 16, 16, 16),
        W = Block.box(0, 0, 0, 2, 16, 16), E = Block.box(14, 0, 0, 16, 16, 16);

    public final int width;

    public AirlockBlock(BlockBehaviour.Properties properties, int width) {
        super(properties);
        this.width = width;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(PART, 0)
            .setValue(OPEN, Side.NONE).setValue(POWERED, false));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return simpleCodec(p -> new AirlockBlock(p, width));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, PART, OPEN, POWERED);
    }

    public int parts() {
        return width * 4;
    }

    /** Teilnummer aus Breite, Höhe, Tiefe. */
    public int part(int w, int h, int d) {
        return width == 2 ? w + 2 * h + 4 * d : h + 2 * d;
    }

    public int w(int part) { return width == 2 ? part % 2 : 0; }
    public int h(int part) { return width == 2 ? (part / 2) % 2 : part % 2; }
    public int d(int part) { return width == 2 ? part / 4 : part / 2; }

    public static Direction right(BlockState state) {
        return state.getValue(FACING).getCounterClockWise();
    }

    public BlockPos partPos(BlockPos origin, BlockState state, int part) {
        return origin.relative(right(state), w(part)).above(h(part)).relative(state.getValue(FACING).getOpposite(), d(part));
    }

    public BlockPos origin(BlockPos pos, BlockState state) {
        int part = state.getValue(PART);
        return pos.relative(right(state), -w(part)).below(h(part)).relative(state.getValue(FACING).getOpposite(), -d(part));
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState st = defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
        Level level = context.getLevel();
        for (int p = 1; p < parts(); p++) {
            BlockPos q = partPos(context.getClickedPos(), st, p);
            if (q.getY() > level.getMaxY() || !level.getBlockState(q).canBeReplaced(context)) return null;
        }
        return st;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity by, ItemStack stack) {
        super.setPlacedBy(level, pos, state, by, stack);
        for (int p = 1; p < parts(); p++) level.setBlock(partPos(pos, state, p), state.setValue(PART, p), Block.UPDATE_ALL);
    }

    /** Fehlt ein Teil, verschwindet die ganze Schleuse (ohne weitere Drops). */
    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
        if (level.getBlockState(pos).is(this)) return;
        BlockPos o = origin(pos, state);
        for (int p = 0; p < parts(); p++) {
            BlockPos q = partPos(o, state, p);
            BlockState other = level.getBlockState(q);
            if (other.is(this) && other.getValue(PART) == p && other.getValue(FACING) == state.getValue(FACING))
                level.setBlock(q, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS);
        }
    }

    /** Türblatt dieses Teils: vorne (Tiefe 0) zur Vorderseite, hinten (Tiefe 1) zur Rückseite. */
    private VoxelShape panel(BlockState state) {
        Direction f = state.getValue(FACING);
        Direction side = d(state.getValue(PART)) == 0 ? f : f.getOpposite();
        return switch (side) {
            case SOUTH -> S;
            case WEST -> W;
            case EAST -> E;
            default -> N;
        };
    }

    public boolean isOpen(BlockState state) {
        Side o = state.getValue(OPEN);
        return o != Side.NONE && (o == Side.FRONT) == (d(state.getValue(PART)) == 0);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return panel(state);
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return isOpen(state) ? Shapes.empty() : panel(state);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide()) {
            Side side = d(state.getValue(PART)) == 0 ? Side.FRONT : Side.BACK;
            set(level, pos, state, state.getValue(OPEN) == side ? Side.NONE : side, state.getValue(POWERED));
        }
        return InteractionResult.SUCCESS;
    }

    /**
     * Redstone: Signal an der Vorderseite öffnet vorne, an der Rückseite hinten (die andere Seite
     * schließt); fällt das Signal weg, schließt die Schleuse. Nur bei Änderungen des Signals.
     */
    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, @Nullable Orientation orientation, boolean movedByPiston) {
        if (level.isClientSide()) return;
        BlockPos o = origin(pos, state);
        boolean front = false, back = false;
        for (int p = 0; p < parts(); p++) {
            if (!level.hasNeighborSignal(partPos(o, state, p))) continue;
            if (d(p) == 0) front = true;
            else back = true;
        }
        boolean powered = front || back;
        if (powered == state.getValue(POWERED)) return;
        Side target = front && !back ? Side.FRONT : back && !front ? Side.BACK : powered ? state.getValue(OPEN) : Side.NONE;
        set(level, pos, state, target, powered);
    }

    private void set(Level level, BlockPos pos, BlockState state, Side open, boolean powered) {
        Side before = state.getValue(OPEN);
        BlockPos o = origin(pos, state);
        for (int p = 0; p < parts(); p++) {
            BlockPos q = partPos(o, state, p);
            BlockState s = level.getBlockState(q);
            if (s.is(this)) level.setBlock(q, s.setValue(OPEN, open).setValue(POWERED, powered), Block.UPDATE_CLIENTS);
        }
        if (before != open)
            level.playSound(null, pos, open == Side.NONE ? SoundEvents.PISTON_CONTRACT : SoundEvents.PISTON_EXTEND, SoundSource.BLOCKS, 0.5F, 0.8F);
    }
}

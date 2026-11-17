package de.simon.spacemod.display;

import de.simon.spacemod.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Rotation;
import de.simon.spacemod.block.FacingBlock;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/** Bildschirm: gewählte Kamera (Lage in derselben Welt bzw. Werft) und ob er der Hauptbildschirm ist. */
public class DisplayBlockEntity extends BlockEntity {
    /**
     * Lage der Kamera relativ zum Bildschirm, im Bezugssystem eines nach Norden zeigenden Bildschirms –
     * bleibt so richtig, wenn das Schiff abhebt (Werft), fliegt oder gedreht landet.
     */
    private @Nullable BlockPos camera;
    private boolean main;

    public DisplayBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.DISPLAY_BE.get(), pos, state);
    }

    /** Position der Kamera in der Welt bzw. im Schiff, in der der Bildschirm gerade steht. */
    public @Nullable BlockPos camera() {
        return camera == null ? null : worldPosition.offset(camera.rotate(toFacing()));
    }

    private Rotation toFacing() {
        BlockState st = getBlockState();
        Direction f = st.hasProperty(FacingBlock.FACING) ? st.getValue(FacingBlock.FACING) : Direction.NORTH;
        return switch (f) {
            case EAST -> Rotation.CLOCKWISE_90;
            case SOUTH -> Rotation.CLOCKWISE_180;
            case WEST -> Rotation.COUNTERCLOCKWISE_90;
            default -> Rotation.NONE;
        };
    }

    private static Rotation inverse(Rotation r) {
        return switch (r) {
            case CLOCKWISE_90 -> Rotation.COUNTERCLOCKWISE_90;
            case COUNTERCLOCKWISE_90 -> Rotation.CLOCKWISE_90;
            default -> r;
        };
    }

    private @Nullable BlockPos relative(@Nullable BlockPos abs) {
        return abs == null ? null : abs.subtract(worldPosition).rotate(inverse(toFacing()));
    }

    public boolean isMain() {
        return main;
    }

    public void set(@Nullable BlockPos camera, boolean main) {
        this.camera = relative(camera);
        this.main = main;
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        long r = input.getLongOr("camera_rel", Long.MIN_VALUE);
        long c = input.getLongOr("camera", Long.MIN_VALUE); // ältere Welten: feste Weltposition
        camera = r != Long.MIN_VALUE ? BlockPos.of(r) : c == Long.MIN_VALUE ? null : relative(BlockPos.of(c));
        main = input.getBooleanOr("main", false);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        if (camera != null) output.putLong("camera_rel", camera.asLong());
        output.putBoolean("main", main);
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        try (ProblemReporter.ScopedCollector reporter = new ProblemReporter.ScopedCollector(problemPath(), com.mojang.logging.LogUtils.getLogger())) {
            TagValueOutput output = TagValueOutput.createWithContext(reporter, registries);
            saveAdditional(output);
            return output.buildResult();
        }
    }
}

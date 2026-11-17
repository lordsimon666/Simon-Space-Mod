package de.simon.spacemod.display;

import de.simon.spacemod.ModBlocks;
import net.minecraft.core.BlockPos;
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
    private @Nullable BlockPos camera;
    private boolean main;

    public DisplayBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.DISPLAY_BE.get(), pos, state);
    }

    public @Nullable BlockPos camera() {
        return camera;
    }

    public boolean isMain() {
        return main;
    }

    public void set(@Nullable BlockPos camera, boolean main) {
        this.camera = camera;
        this.main = main;
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        long c = input.getLongOr("camera", Long.MIN_VALUE);
        camera = c == Long.MIN_VALUE ? null : BlockPos.of(c);
        main = input.getBooleanOr("main", false);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        if (camera != null) output.putLong("camera", camera.asLong());
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

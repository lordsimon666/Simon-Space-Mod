package de.simon.spacemod.block;

import de.simon.spacemod.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/** Speichert das Ziel eines Turbolifts (Position des Ziel-Panels + Name des Decks). */
public class TurboliftBlockEntity extends BlockEntity {
    private @Nullable BlockPos target;
    private String destination = "";

    public TurboliftBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.TURBOLIFT_BE.get(), pos, state);
    }

    public void setTarget(BlockPos target, String destination) {
        this.target = target.immutable();
        this.destination = destination;
        setChanged();
    }

    public @Nullable BlockPos getTarget() {
        return target;
    }

    public String getDestination() {
        return destination;
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        if (target != null) {
            output.putLong("Target", target.asLong());
        }
        output.putString("Destination", destination);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        target = input.getLong("Target").map(BlockPos::of).orElse(null);
        destination = input.getStringOr("Destination", "");
    }
}

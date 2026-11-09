package de.simon.spacemod.block;

import de.simon.spacemod.ModBlocks;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/**
 * Speichert die Ziele eines Turbolifts (Position des Ziel-Panels + Übersetzungsschlüssel des Decks)
 * und welches Ziel gerade ausgewählt ist.
 */
public class TurboliftBlockEntity extends BlockEntity {
    public record Destination(BlockPos pos, String key) {}

    private final List<Destination> destinations = new ArrayList<>();
    private int selected;

    public TurboliftBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.TURBOLIFT_BE.get(), pos, state);
    }

    public void clearDestinations() {
        destinations.clear();
        selected = 0;
        setChanged();
    }

    public void addDestination(BlockPos target, String key) {
        destinations.add(new Destination(target.immutable(), key));
        setChanged();
    }

    public @Nullable Destination getSelected() {
        return destinations.isEmpty() ? null : destinations.get(Math.floorMod(selected, destinations.size()));
    }

    public int destinationCount() {
        return destinations.size();
    }

    /** Wählt das nächste Ziel aus und gibt es zurück. */
    public @Nullable Destination cycle() {
        if (destinations.isEmpty()) return null;
        selected = Math.floorMod(selected + 1, destinations.size());
        setChanged();
        return getSelected();
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putInt("Count", destinations.size());
        output.putInt("Selected", selected);
        for (int i = 0; i < destinations.size(); i++) {
            output.putLong("Target" + i, destinations.get(i).pos().asLong());
            output.putString("Destination" + i, destinations.get(i).key());
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        destinations.clear();
        int count = input.getIntOr("Count", 0);
        for (int i = 0; i < count; i++) {
            int idx = i;
            input.getLong("Target" + i).ifPresent(p ->
                destinations.add(new Destination(BlockPos.of(p), input.getStringOr("Destination" + idx, ""))));
        }
        selected = input.getIntOr("Selected", 0);
    }
}

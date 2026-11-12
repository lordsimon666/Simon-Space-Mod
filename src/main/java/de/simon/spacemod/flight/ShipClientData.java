package de.simon.spacemod.flight;

import java.util.HashMap;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/** Client: Strukturen, die vor dem Schiff selbst angekommen sind. */
public final class ShipClientData {
    private static final Map<Integer, ShipStructure> PENDING = new HashMap<>();

    private ShipClientData() {}

    public static synchronized void put(int id, ShipStructure s) {
        PENDING.put(id, s);
    }

    public static synchronized @Nullable ShipStructure take(int id) {
        return PENDING.remove(id);
    }
}

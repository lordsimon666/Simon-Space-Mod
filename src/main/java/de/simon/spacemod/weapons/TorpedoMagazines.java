package de.simon.spacemod.weapons;

import java.util.HashMap;
import java.util.Map;

/**
 * Magazine der Torpedowerfer (testbar ohne Minecraft): jeder Werfer hat eigene Schüsse und lädt nach
 * Ablauf der Nachladezeit seit dem letzten Schuss wieder voll. Zwischen zwei Schüssen aus demselben
 * Werfer liegen mindestens {@link #GAP} Ticks.
 */
public final class TorpedoMagazines {
    public static final int GAP = 5;

    private static final class State {
        int shots;
        long last = Long.MIN_VALUE / 2;
        long reloadAt;
    }

    private final Map<String, State> states = new HashMap<>();

    private State state(String key, int magazine, long now) {
        State s = states.computeIfAbsent(key, k -> {
            State n = new State();
            n.shots = magazine;
            return n;
        });
        if (s.shots < magazine && now >= s.reloadAt) s.shots = magazine;
        if (s.shots > magazine) s.shots = magazine;
        return s;
    }

    /** Schüsse, die der Werfer jetzt abgeben kann (0 = lädt nach oder hat gerade gefeuert). */
    public int ready(String key, int magazine, long now) {
        State s = state(key, magazine, now);
        return now - s.last >= GAP ? s.shots : 0;
    }

    public void use(String key, int shots, int magazine, long now, int reloadTicks) {
        State s = state(key, magazine, now);
        s.shots = Math.max(0, s.shots - shots);
        s.last = now;
        s.reloadAt = now + reloadTicks;
        if (states.size() > 5000) states.clear();
    }
}

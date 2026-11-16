package de.simon.spacemod.storage;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

class CrateSortTest {
    record S(String id, String name, int count, int max) {}

    private static CrateSort.Kind<S> kind(int multiplier) {
        return new CrateSort.Kind<>() {
            @Override public boolean same(S a, S b) { return a.id.equals(b.id); }
            @Override public int count(S s) { return s.count; }
            @Override public int limit(S s) { return s.max <= 1 ? 1 : s.max * multiplier; }
            @Override public String name(S s) { return s.name; }
            @Override public String id(S s) { return s.id; }
            @Override public S withCount(S s, int n) { return new S(s.id, s.name, n, s.max); }
        };
    }

    private static final List<S> CRATE = List.of(new S("minecraft:dirt", "dirt", 30, 64), new S("minecraft:cobblestone", "cobblestone", 50, 64),
        new S("minecraft:dirt", "dirt", 20, 64), new S("minecraft:cobblestone", "cobblestone", 64, 64),
        new S("simonspacemod:raw_uranium", "raw uranium", 5, 64), new S("minecraft:cobblestone", "cobblestone", 22, 64),
        new S("minecraft:diamond_sword", "diamond sword", 1, 1), new S("minecraft:diamond_sword", "diamond sword", 1, 1));

    private static String show(List<S> l) {
        return String.join(",", l.stream().map(s -> s.name.split(" ")[0] + s.count).toList());
    }

    @Test
    void nameIsAlphabeticalWithPartialStackLast() {
        assertEquals("cobblestone64,cobblestone64,cobblestone8,diamond1,diamond1,dirt50,raw5",
            show(CrateSort.arrange(CRATE, CrateSort.Mode.NAME, kind(1))));
    }

    @Test
    void countPutsLargestTotalFirst() {
        // 136 Bruchstein vor 50 Erde vor 5 Uran vor 2 Schwertern
        assertEquals("cobblestone64,cobblestone64,cobblestone8,dirt50,raw5,diamond1,diamond1",
            show(CrateSort.arrange(CRATE, CrateSort.Mode.COUNT, kind(1))));
    }

    @Test
    void modGroupsByNamespace() {
        assertEquals("cobblestone64,cobblestone64,cobblestone8,diamond1,diamond1,dirt50,raw5",
            show(CrateSort.arrange(CRATE, CrateSort.Mode.MOD, kind(1))));
    }

    @Test
    void stackUpgradesMakeBiggerStacks() {
        assertEquals("cobblestone136,diamond1,diamond1,dirt50,raw5", show(CrateSort.arrange(CRATE, CrateSort.Mode.NAME, kind(4))));
    }
}

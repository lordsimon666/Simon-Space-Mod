package de.simon.spacemod.storage;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.ToIntFunction;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;

/**
 * Sortieren der Großkiste: gleiche Gegenstände werden zu möglichst großen Stapeln zusammengefasst
 * (volle Stapel zuerst, der angebrochene am Ende der Gruppe) und die Gruppen geordnet – nach Name,
 * nach Mod oder nach Gesamtmenge in der Kiste (die größte Menge zuerst).
 */
public final class CrateSort {
    public enum Mode { NAME, MOD, COUNT }

    private CrateSort() {}

    /** Beschreibung einer Stapel-Art für den Algorithmus (testbar ohne Minecraft-Registries). */
    public interface Kind<S> {
        boolean same(S a, S b);
        int count(S s);
        int limit(S s);
        /** Anzeigename (klein geschrieben) und Kennung "mod:name". */
        String name(S s);
        String id(S s);
        S withCount(S s, int n);
    }

    private static final class Group<S> {
        final S proto;
        long total;
        final String name, id;

        Group(S proto, String name, String id) {
            this.proto = proto;
            this.name = name;
            this.id = id;
        }

        String mod() {
            int i = id.indexOf(':');
            return i < 0 ? "" : id.substring(0, i);
        }
    }

    /** Neue Belegung (ohne leere Plätze) aus den Stapeln. */
    public static <S> List<S> arrange(List<S> stacks, Mode mode, Kind<S> k) {
        List<Group<S>> groups = new ArrayList<>();
        java.util.Map<String, List<Group<S>>> byId = new java.util.HashMap<>();
        for (S s : stacks) {
            List<Group<S>> same = byId.computeIfAbsent(k.id(s), x -> new ArrayList<>());
            Group<S> g = null;
            for (Group<S> c : same) if (k.same(c.proto, s)) { g = c; break; }
            if (g == null) { g = new Group<>(s, k.name(s), k.id(s)); same.add(g); groups.add(g); }
            g.total += k.count(s);
        }
        Comparator<Group<S>> byName = Comparator.<Group<S>, String>comparing(g -> g.name).thenComparing(g -> g.id);
        groups.sort(switch (mode) {
            case NAME -> byName;
            case MOD -> Comparator.<Group<S>, String>comparing(Group::mod).thenComparing(byName);
            case COUNT -> Comparator.<Group<S>>comparingLong(g -> -g.total).thenComparing(byName);
        });
        List<S> out = new ArrayList<>();
        for (Group<S> g : groups) {
            int max = Math.max(1, k.limit(g.proto));
            for (long left = g.total; left > 0; ) {
                int n = (int) Math.min(max, left);
                out.add(k.withCount(g.proto, n));
                left -= n;
            }
        }
        return out;
    }

    public static void sort(NonNullList<ItemStack> items, Mode mode, ToIntFunction<ItemStack> limit) {
        List<ItemStack> stacks = new ArrayList<>();
        for (ItemStack s : items) if (!s.isEmpty()) stacks.add(s);
        List<ItemStack> out = arrange(stacks, mode, new Kind<>() {
            @Override public boolean same(ItemStack a, ItemStack b) { return ItemStack.isSameItemSameComponents(a, b); }
            @Override public int count(ItemStack s) { return s.getCount(); }
            @Override public int limit(ItemStack s) { return limit.applyAsInt(s); }
            @Override public String name(ItemStack s) { return s.getHoverName().getString().toLowerCase(java.util.Locale.ROOT); }
            @Override public String id(ItemStack s) { return BuiltInRegistries.ITEM.getKey(s.getItem()).toString(); }
            @Override public ItemStack withCount(ItemStack s, int n) { return s.copyWithCount(n); }
        });
        if (out.size() > items.size()) return; // kann nicht passieren (Zusammenfassen spart nur Platz)
        for (int i = 0; i < items.size(); i++) items.set(i, i < out.size() ? out.get(i) : ItemStack.EMPTY);
    }
}

package de.simon.spacemod.assembly;

import de.simon.spacemod.ModBlocks;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.registries.RegistryObject;

/**
 * Rezepte der Schiffsteil-Fertigung: für jeden Block der Mod (außer der Fertigung selbst und den
 * Erzen) bis zu 9 Zutaten, je Platz eine Sorte in beliebiger Menge (z. B. 3 Eisen in einem Platz –
 * so können mehr als 9 Gegenstände gebraucht werden). Die Zutaten ergeben sich aus der Art des
 * Blocks (Hülle, Konsole, Licht, Tür, Waffe, Antrieb …); die Dauer wächst mit der Zutatenmenge.
 */
public final class AssemblyRecipes {
    public record Recipe(Item output, int count, ItemStack[] inputs, int ticks) {}

    /** Strom je Tick während der Fertigung. */
    public static final int FE_PER_TICK = 80;

    private static final Set<String> EXCLUDED = Set.of("ship_part_assembly", "uranium_ore", "deepslate_uranium_ore",
        "dilithium_ore", "antimatter_cluster", "deuterium_ice");

    private static List<Recipe> all;

    private AssemblyRecipes() {}

    /** Beim Serverstart einmal aufbauen (und zählen), damit Fehler sofort auffallen. */
    public static void init() {
        net.minecraftforge.event.server.ServerStartedEvent.BUS.addListener(e ->
        {
            var log = com.mojang.logging.LogUtils.getLogger();
            log.info("Schiffsteil-Fertigung: {} Rezepte", all().size());
            for (Recipe r : all())
                log.debug("Rezept {} x{} <- {} ({} t)", r.output(), r.count(),
                    java.util.Arrays.stream(r.inputs()).filter(st -> !st.isEmpty()).map(st -> st.getCount() + " " + st.getItem()).toList(), r.ticks());
        });
    }

    public static synchronized List<Recipe> all() {
        if (all == null) {
            List<Recipe> list = new ArrayList<>();
            for (RegistryObject<Block> ro : ModBlocks.BLOCKS.getEntries()) {
                String path = ro.getId().getPath();
                if (EXCLUDED.contains(path) || !ro.isPresent()) continue;
                Item item = ro.get().asItem();
                if (item == Items.AIR) continue;
                list.add(recipeFor(path, item));
            }
            all = List.copyOf(list);
        }
        return all;
    }

    public static Recipe get(int index) {
        List<Recipe> l = all();
        return index >= 0 && index < l.size() ? l.get(index) : null;
    }

    private static final class B {
        final List<ItemStack> in = new ArrayList<>();
        int count = 1;

        B add(Item item, int n) {
            if (n <= 0 || in.size() >= 9) return this;
            for (ItemStack s : in)
                if (s.is(item) && s.getCount() + n <= 64) { s.grow(n); return this; }
            in.add(new ItemStack(item, n));
            return this;
        }
    }

    static Recipe recipeFor(String p, Item output) {
        B b = new B();
        if (p.startsWith("warp_core")) {
            boolean late = p.endsWith("_e") || p.endsWith("_tng");
            b.add(Items.IRON_BLOCK, 2).add(ModBlocks.DILITHIUM_CRYSTAL.get(), p.endsWith("_nx") ? 0 : late ? 4 : 2)
                .add(ModBlocks.ANTIMATTER_POD.get(), late ? 3 : 2).add(ModBlocks.DEUTERIUM.get(), 3)
                .add(Items.REDSTONE_BLOCK, 1).add(late ? Items.DIAMOND : Items.GOLD_INGOT, late ? 2 : 3);
            b.count = 2;
        } else if (p.startsWith("shield_emitter")) {
            // je neuer das Schiff, desto aufwendiger
            int tier = 1 + java.util.List.of("tos", "refit", "excelsior", "c", "d", "e").indexOf(p.substring("shield_emitter_".length()));
            b.add(Items.IRON_BLOCK, 1 + tier / 2).add(Items.DIAMOND, tier).add(Items.REDSTONE_BLOCK, 2).add(Items.GLOWSTONE_DUST, 4)
                .add(Items.LAPIS_BLOCK, 2);
            if (tier >= 4) b.add(ModBlocks.DILITHIUM_CRYSTAL.get(), tier - 2);
            if (tier >= 6) b.add(ModBlocks.ANTIMATTER_POD.get(), 1);
        } else if (p.startsWith("reactor_")) {
            if (p.equals("reactor_controller"))
                b.add(Items.IRON_INGOT, 8).add(Items.COPPER_INGOT, 8).add(ModBlocks.URANIUM_INGOT.get(), 3).add(Items.DIAMOND, 2).add(Items.REDSTONE_BLOCK, 1);
            else if (p.equals("reactor_glass")) { b.add(Items.GLASS, 4).add(Items.IRON_INGOT, 1).add(Items.COPPER_INGOT, 1); b.count = 4; }
            else { b.add(Items.IRON_INGOT, 4).add(Items.COPPER_INGOT, 4).add(ModBlocks.URANIUM_INGOT.get(), 1); b.count = 8; }
        } else if (p.startsWith("airlock")) {
            int w = p.equals("airlock") ? 2 : 1;
            b.add(Items.IRON_INGOT, 6 * w).add(Items.GLASS_PANE, 2 * w).add(Items.REDSTONE, 2).add(Items.IRON_DOOR, 2 * w).add(Items.DYE.yellow(), 1);
        } else if (p.startsWith("door") || p.contains("turbolift")) {
            b.add(Items.IRON_INGOT, p.contains("frame") ? 2 : 4).add(Items.REDSTONE, 1).add(Items.GLASS_PANE, 1);
            if (p.contains("security")) b.add(Items.DYE.yellow(), 2);
            if (p.contains("command")) b.add(Items.DYE.red(), 2);
            if (p.equals("turbolift")) b.add(Items.ENDER_PEARL, 1);
        } else if (p.contains("torpedo") || p.contains("quantum") || p.contains("launcher")) {
            if (p.contains("quantum")) // Quantentorpedos: deutlich teurer, mit Dilithium und Antimaterie
                b.add(Items.IRON_BLOCK, 2).add(Items.REDSTONE_BLOCK, 2).add(Items.TNT, 4).add(Items.GOLD_INGOT, 6).add(Items.DIAMOND, 4)
                    .add(ModBlocks.DILITHIUM_CRYSTAL.get(), 4).add(ModBlocks.ANTIMATTER_POD.get(), 2).add(Items.ENDER_PEARL, 2);
            else
                b.add(Items.IRON_INGOT, 6).add(Items.REDSTONE, 4).add(Items.TNT, 2).add(Items.GOLD_INGOT, 2).add(Items.DIAMOND, 1);
        } else if (p.contains("charging") || p.contains("rack") || p.contains("holder") || p.contains("cabinet")) {
            b.add(Items.IRON_INGOT, 5).add(Items.REDSTONE, 2).add(Items.GLASS_PANE, 1).add(Items.GOLD_INGOT, 1);
        } else if (p.startsWith("phase") || p.contains("weapon")) {
            b.add(Items.IRON_INGOT, 4).add(Items.REDSTONE, 4).add(Items.GOLD_INGOT, 1).add(Items.DIAMOND, 1).add(Items.GLOWSTONE_DUST, 2);
        } else if (p.contains("impulse") || p.contains("bussard") || p.contains("deflector") || p.contains("nacelle")) {
            b.add(Items.IRON_INGOT, 6).add(Items.REDSTONE, 3).add(Items.GOLD_INGOT, 2).add(Items.GLOWSTONE_DUST, 3).add(Items.COPPER_INGOT, 2);
            color(b, p);
        } else if (p.contains("transporter")) {
            b.add(Items.IRON_INGOT, 2).add(Items.REDSTONE, 2).add(Items.GLOWSTONE_DUST, 1);
            if (p.contains("console") || p.contains("emitter") || p.contains("pillar") || p.contains("field")) b.add(Items.ENDER_PEARL, 1);
            else b.count = 4;
        } else if (p.contains("chair") || p.contains("seat") || p.contains("bed") || p.contains("bunk") || p.contains("sofa")) {
            b.add(Items.IRON_INGOT, 2).add(Items.WOOL.white(), 2);
            color(b, p);
        } else if (p.contains("console") || p.contains("lcars") || p.contains("station") || p.contains("monitor") || p.contains("screen")
            || p.contains("display") || p.contains("msd") || p.contains("helm") || p.contains("starmap") || p.contains("star_map")
            || p.contains("situation") || p.contains("biobed") || p.contains("bio_bed")) {
            b.add(Items.IRON_INGOT, 3).add(Items.REDSTONE, 3).add(Items.GLASS_PANE, 2).add(Items.GOLD_INGOT, 1).add(Items.GLOWSTONE_DUST, 1);
            if (p.contains("helm")) b.add(Items.DIAMOND, 1).add(Items.COMPASS, 1);
        } else if (p.contains("light") || p.contains("lamp") || p.contains("glow") || p.contains("decon") || p.contains("column")
            || (p.contains("strip") && !p.contains("phaser"))) {
            b.add(Items.GLOWSTONE_DUST, 2).add(Items.IRON_INGOT, 1).add(Items.GLASS, 1);
            color(b, p);
            b.count = 4;
        } else if (p.contains("window") || p.contains("viewport") || p.contains("glass") || p.contains("force")) {
            b.add(Items.GLASS, 3).add(Items.IRON_INGOT, 1);
            if (p.contains("force")) b.add(Items.REDSTONE, 2).add(Items.DYE.lightBlue(), 1);
            b.count = 4;
        } else if (p.contains("table") || p.contains("desk") || p.contains("bar") || p.contains("counter") || p.contains("container")) {
            b.add(Items.IRON_INGOT, 3).add(Items.SMOOTH_STONE, 2);
            if (p.contains("container")) b.add(Items.CHEST, 1);
            color(b, p);
        } else if (p.contains("carpet")) {
            b.add(Items.WOOL.white(), 2).add(Items.STRING, 1);
            color(b, p);
            b.count = 8;
        } else if (p.contains("plasma") || p.contains("conduit")) {
            b.add(Items.IRON_INGOT, 2).add(Items.GLOWSTONE_DUST, 2).add(Items.GLASS, 1).add(Items.REDSTONE, 1);
            b.count = 4;
        } else {
            // Hülle, Wände, Böden, Decken, Zierstreifen, Geländer …
            b.add(Items.IRON_INGOT, 2).add(p.contains("wood") ? Items.OAK_PLANKS : Items.SMOOTH_STONE, 2);
            color(b, p);
            b.count = 8;
        }
        if (b.in.isEmpty()) b.add(Items.IRON_INGOT, 2);
        ItemStack[] in = new ItemStack[9];
        for (int i = 0; i < 9; i++) in[i] = i < b.in.size() ? b.in.get(i) : ItemStack.EMPTY;
        int total = 0;
        for (ItemStack s : b.in) total += s.getCount();
        return new Recipe(output, b.count, in, Math.min(1200, 40 + total * 10));
    }

    private static void color(B b, String p) {
        if (p.contains("red")) b.add(Items.DYE.red(), 1);
        else if (p.contains("blue")) b.add(Items.DYE.blue(), 1);
        else if (p.contains("teal") || p.contains("petrol") || p.contains("cyan")) b.add(Items.DYE.cyan(), 1);
        else if (p.contains("orange")) b.add(Items.DYE.orange(), 1);
        else if (p.contains("green")) b.add(Items.DYE.green(), 1);
        else if (p.contains("yellow") || p.contains("gold")) b.add(Items.DYE.yellow(), 1);
        else if (p.contains("black") || p.contains("dark")) b.add(Items.DYE.black(), 1);
        else if (p.contains("cream") || p.contains("white")) b.add(Items.DYE.white(), 1);
        else if (p.contains("gray") || p.contains("grey")) b.add(Items.DYE.gray(), 1);
        else if (p.contains("purple") || p.contains("holodeck")) b.add(Items.DYE.purple(), 1);
    }

    /** Liegen die Zutaten bereit? (Platz i braucht mindestens die angegebene Menge der Sorte.) */
    public static boolean matches(Recipe r, java.util.function.IntFunction<ItemStack> slot) {
        for (int i = 0; i < 9; i++) {
            ItemStack need = r.inputs()[i];
            if (need.isEmpty()) continue;
            ItemStack have = slot.apply(i);
            if (!have.is(need.getItem()) || have.getCount() < need.getCount()) return false;
        }
        return true;
    }
}

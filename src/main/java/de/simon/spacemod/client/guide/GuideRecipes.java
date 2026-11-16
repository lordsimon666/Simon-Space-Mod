package de.simon.spacemod.client.guide;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import de.simon.spacemod.SimonSpaceMod;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The recipes the guide book shows, read from the mod's own recipe files. They are in the mod's jar, so the
 * book can show them on a server too, where the client is not sent the recipes. An ingredient that can be one
 * of several items (a tag like planks) shows them in turn.
 */
public final class GuideRecipes {
    /** What kind of recipe it is, for the picture between ingredients and result. */
    public enum Kind {
        CRAFTING(Items.CRAFTING_TABLE), SMELTING(Items.FURNACE), BLASTING(Items.BLAST_FURNACE), SMOKING(Items.SMOKER),
        CAMPFIRE(Items.CAMPFIRE), STONECUTTING(Items.STONECUTTER), SMITHING(Items.SMITHING_TABLE),
        /** Schiffsteil-Fertigung: 3 × 3 Plätze, je Platz eine Sorte in beliebiger Menge. */
        ASSEMBLY(null);

        @Nullable
        private final Item station;

        Kind(@Nullable Item station) {
            this.station = station;
        }

        public ItemStack station() {
            return this.station == null ? new ItemStack(de.simon.spacemod.ModBlocks.SHIP_PART_ASSEMBLY.get()) : new ItemStack(this.station);
        }

        /** Mit 3 × 3-Raster gezeichnet. */
        public boolean grid() {
            return this == CRAFTING || this == ASSEMBLY;
        }
    }

    /**
     * A recipe: its kind, for crafting the 3 x 3 grid (row by row, empty slots empty lists), otherwise the
     * ingredients in a row; and the result.
     */
    public record Recipe(Kind kind, List<List<ItemStack>> ingredients, ItemStack result) {
    }

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Map<String, Optional<Recipe>> RECIPES = new HashMap<>();

    private GuideRecipes() {
    }

    /** The recipe from {@code data/simonspacemod/recipe/<name>.json}, or null if there is none the book can show. */
    @Nullable
    public static Recipe get(String name) {
        return RECIPES.computeIfAbsent(name, n -> n.startsWith("assembly:") ? assembly(n.substring(9)) : load(n)).orElse(null);
    }

    /** Rezept der Schiffsteil-Fertigung für einen Block der Mod (Registername). */
    private static Optional<Recipe> assembly(String block) {
        ItemStack want = GuideBook.item(block);
        for (de.simon.spacemod.assembly.AssemblyRecipes.Recipe r : de.simon.spacemod.assembly.AssemblyRecipes.all()) {
            if (r.output() != want.getItem()) continue;
            List<List<ItemStack>> ingredients = new ArrayList<>();
            for (ItemStack in : r.inputs()) ingredients.add(in.isEmpty() ? List.of() : List.of(in.copy()));
            return Optional.of(new Recipe(Kind.ASSEMBLY, ingredients, new ItemStack(r.output(), r.count())));
        }
        return Optional.empty();
    }

    private static Optional<Recipe> load(String name) {
        try (InputStream in = GuideRecipes.class.getResourceAsStream("/data/" + SimonSpaceMod.MODID + "/recipe/" + name + ".json")) {
            if (in == null)
                return Optional.empty();
            JsonObject json = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            return Optional.ofNullable(parse(json));
        } catch (IOException | RuntimeException e) {
            LOGGER.warn("Guide book: can't read recipe {}", name, e);
            return Optional.empty();
        }
    }

    @Nullable
    private static Recipe parse(JsonObject json) {
        String type = json.get("type").getAsString();
        ItemStack result = result(json.get("result"));
        List<List<ItemStack>> ingredients = new ArrayList<>();
        switch (type) {
            case "minecraft:crafting_shaped" -> {
                JsonArray pattern = json.getAsJsonArray("pattern");
                JsonObject key = json.getAsJsonObject("key");
                for (int row = 0; row < 3; row++) {
                    String line = row < pattern.size() ? pattern.get(row).getAsString() : "";
                    for (int column = 0; column < 3; column++) {
                        char symbol = column < line.length() ? line.charAt(column) : ' ';
                        ingredients.add(symbol == ' ' ? List.of() : ingredient(key.get(String.valueOf(symbol))));
                    }
                }
                return new Recipe(Kind.CRAFTING, ingredients, result);
            }
            case "minecraft:crafting_shapeless" -> {
                for (JsonElement ingredient : json.getAsJsonArray("ingredients"))
                    ingredients.add(ingredient(ingredient));
                while (ingredients.size() < 9)
                    ingredients.add(List.of());
                return new Recipe(Kind.CRAFTING, ingredients, result);
            }
            case "minecraft:smithing_transform" -> {
                ingredients.add(ingredient(json.get("template")));
                ingredients.add(ingredient(json.get("base")));
                ingredients.add(ingredient(json.get("addition")));
                return new Recipe(Kind.SMITHING, ingredients, result);
            }
            default -> {
                Kind kind = switch (type) {
                    case "minecraft:smelting" -> Kind.SMELTING;
                    case "minecraft:blasting" -> Kind.BLASTING;
                    case "minecraft:smoking" -> Kind.SMOKING;
                    case "minecraft:campfire_cooking" -> Kind.CAMPFIRE;
                    case "minecraft:stonecutting" -> Kind.STONECUTTING;
                    default -> null;
                };
                if (kind == null)
                    return null;
                ingredients.add(ingredient(json.get("ingredient")));
                return new Recipe(kind, ingredients, result);
            }
        }
    }

    private static ItemStack result(JsonElement json) {
        if (json.isJsonPrimitive())
            return GuideBook.item(json.getAsString());
        JsonObject result = json.getAsJsonObject();
        ItemStack stack = GuideBook.item(result.get("id").getAsString());
        if (result.has("count"))
            stack.setCount(result.get("count").getAsInt());
        return stack;
    }

    /** The items an ingredient can be: an item id, a tag ("#..."), a list of those, or the old {"item"} / {"tag"} form. */
    private static List<ItemStack> ingredient(@Nullable JsonElement json) {
        if (json == null || json.isJsonNull())
            return List.of();
        List<ItemStack> items = new ArrayList<>();
        if (json.isJsonArray()) {
            for (JsonElement option : json.getAsJsonArray())
                items.addAll(ingredient(option));
        } else if (json.isJsonObject()) {
            JsonObject object = json.getAsJsonObject();
            if (object.has("item"))
                items.add(GuideBook.item(object.get("item").getAsString()));
            else if (object.has("tag"))
                items.addAll(tag(object.get("tag").getAsString()));
        } else {
            String id = json.getAsString();
            if (id.startsWith("#"))
                items.addAll(tag(id.substring(1)));
            else
                items.add(GuideBook.item(id));
        }
        return items;
    }

    /** Every item in a tag (the client knows the tags once it is in a world). */
    private static List<ItemStack> tag(String id) {
        Identifier key = Identifier.tryParse(id);
        if (key == null)
            return List.of();
        TagKey<Item> tag = TagKey.create(Registries.ITEM, key);
        List<ItemStack> items = new ArrayList<>();
        for (Item item : BuiltInRegistries.ITEM) {
            ItemStack stack = new ItemStack(item);
            if (!stack.isEmpty() && stack.is(tag))
                items.add(stack);
        }
        return items;
    }
}

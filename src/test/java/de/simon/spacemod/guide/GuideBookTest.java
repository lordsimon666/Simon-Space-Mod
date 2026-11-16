package de.simon.spacemod.guide;

import static org.junit.jupiter.api.Assertions.assertTrue;

import de.simon.spacemod.client.guide.GuideBook;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Jeder Text des Handbuchs steht in beiden Sprachen, jedes Rezept und jedes Item gibt es; das Easter Egg kommt nicht vor. */
class GuideBookTest {
    private static String res(String path) throws Exception {
        var in = GuideBookTest.class.getResourceAsStream(path);
        return in == null ? null : new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
    }

    @Test
    void everythingExists() throws Exception {
        String de = res("/assets/simonspacemod/lang/de_de.json"), en = res("/assets/simonspacemod/lang/en_us.json");
        List<String> keys = new ArrayList<>(List.of("title", "subtitle", "intro", "categories", "chapters", "no_recipe"));
        List<String> missing = new ArrayList<>();
        for (GuideBook.Category c : GuideBook.CATEGORIES) {
            keys.add("category." + c.id());
            keys.add("category." + c.id() + ".desc");
            checkItem(c.icon(), missing);
            for (GuideBook.Entry e : c.entries()) {
                keys.add("entry." + e.id());
                checkItem(e.icon(), missing);
                for (GuideBook.Page p : e.pages()) {
                    if (p.text() != null) keys.add(p.text());
                    if (p instanceof GuideBook.Spotlight s) checkItem(s.item(), missing);
                    if (p instanceof GuideBook.Crafting r) {
                        if (r.recipe().startsWith("assembly:")) checkItem(r.recipe().substring(9), missing);
                        else if (res("/data/simonspacemod/recipe/" + r.recipe() + ".json") == null) missing.add("Rezept " + r.recipe());
                    }
                }
            }
        }
        for (String k : keys) {
            String full = "\"guide.simonspacemod." + k + "\"";
            if (!de.contains(full)) missing.add("de " + k);
            if (!en.contains(full)) missing.add("en " + k);
        }
        assertTrue(missing.isEmpty(), "fehlt: " + missing);
        // Easter Egg bleibt geheim
        for (String lang : new String[] {de, en})
            for (String line : lang.split("\n"))
                if (line.contains("\"guide.simonspacemod.")) assertTrue(!line.toLowerCase().matches(".*(atom|chicken|hühnchen|huhn).*"), line);
    }

    private static void checkItem(String id, List<String> missing) throws Exception {
        String name = id.contains(":") ? id.substring(id.indexOf(':') + 1) : id;
        if (id.startsWith("minecraft:")) return;
        if (res("/assets/simonspacemod/items/" + name + ".json") == null) missing.add("Item " + id);
    }
}

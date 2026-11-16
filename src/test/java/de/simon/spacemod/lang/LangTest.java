package de.simon.spacemod.lang;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** Übersetzungen: Deutsch und Englisch vollständig, und jeder feste Textschlüssel im Code ist übersetzt. */
class LangTest {
    private static final Path LANG = Path.of("src/main/resources/assets/simonspacemod/lang");

    private static Set<String> keys(String file) throws Exception {
        JsonObject o = JsonParser.parseString(Files.readString(LANG.resolve(file))).getAsJsonObject();
        return new TreeSet<>(o.keySet());
    }

    @Test
    void germanAndEnglishHaveTheSameKeys() throws Exception {
        Set<String> de = keys("de_de.json"), en = keys("en_us.json");
        Set<String> onlyDe = new TreeSet<>(de), onlyEn = new TreeSet<>(en);
        onlyDe.removeAll(en);
        onlyEn.removeAll(de);
        assertTrue(onlyDe.isEmpty(), "nur Deutsch: " + onlyDe);
        assertTrue(onlyEn.isEmpty(), "nur Englisch: " + onlyEn);
    }

    @Test
    void everyLiteralKeyInTheCodeIsTranslated() throws Exception {
        Set<String> en = keys("en_us.json");
        Pattern p = Pattern.compile("translatable\\(\\s*\"((?:message|gui|tooltip|key|command|block|item)\\.simonspacemod\\.[a-z0-9_.]+)\"");
        Set<String> missing = new TreeSet<>();
        try (Stream<Path> files = Files.walk(Path.of("src/main/java"))) {
            for (Path f : (Iterable<Path>) files.filter(x -> x.toString().endsWith(".java"))::iterator) {
                Matcher m = p.matcher(Files.readString(f));
                while (m.find()) {
                    String k = m.group(1);
                    if (k.endsWith(".") || k.endsWith("_")) continue; // zusammengesetzt ("…crate_" + Stufe): nicht prüfbar
                    if (!en.contains(k)) missing.add(k + " (" + f.getFileName() + ")");
                }
            }
        }
        assertTrue(missing.isEmpty(), "ohne Übersetzung: " + missing);
    }
}

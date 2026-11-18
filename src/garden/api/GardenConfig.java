package garden.api;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import garden.Species;

/**
 * The plants to seed the API's garden with, read from a JSON file in the format the
 * API document asks for:
 *
 * <pre>
 * { "plants": [ { "name": "Juniper", "amount": 10 }, { "name": "Pine", "amount": 10 } ] }
 * </pre>
 *
 * Names are matched with {@link Species#fromName}. Bad entries are skipped with a warning
 * rather than stopping the garden, and the result always has every species and at least
 * {@link #MIN_PLANTS} plants, as the API requires.
 */
public final class GardenConfig {

    /** The config file, relative to the working directory. */
    public static final String FILE_NAME = "garden-config.json";
    public static final int MIN_PLANTS = 10;
    /** No single entry may plant more than this, so a typo can't create a million trees. */
    public static final int MAX_PER_ENTRY = 100;
    /** Used when there is no config file, or it can't be read. */
    public static final int DEFAULT_PER_SPECIES = 10;

    private final Map<Species, Integer> amounts;
    private final List<String> warnings;
    private final String source;

    private GardenConfig(Map<Species, Integer> amounts, List<String> warnings, String source) {
        this.amounts = amounts;
        this.warnings = warnings;
        this.source = source;
    }

    /** How many of each species to plant, in the order they were listed. */
    public Map<Species, Integer> getAmounts() { return Collections.unmodifiableMap(amounts); }

    /** Problems found and fixed while reading the config, for the log. */
    public List<String> getWarnings() { return Collections.unmodifiableList(warnings); }

    /** Where the plants came from (the file, or the built-in defaults). */
    public String getSource() { return source; }

    public int getTotal() {
        int total = 0;
        for (int n : amounts.values()) total += n;
        return total;
    }

    /**
     * Reads {@value #FILE_NAME} from {@code directory}. Never throws: a missing or broken
     * file falls back to {@link #defaults()} and says why in the warnings.
     */
    public static GardenConfig load(File directory) {
        File file = new File(directory, FILE_NAME);
        if (!file.isFile()) {
            GardenConfig fallback = defaults();
            fallback.addWarning(FILE_NAME + " not found in the working directory; using the built-in plant list");
            return fallback;
        }
        try {
            String text = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
            GardenConfig config = parse(text);
            return new GardenConfig(config.amounts, config.warnings, FILE_NAME);
        } catch (IOException | IllegalArgumentException problem) {
            GardenConfig fallback = defaults();
            fallback.addWarning(FILE_NAME + " could not be used (" + problem.getMessage() + "); using the built-in plant list");
            return fallback;
        }
    }

    /**
     * Parses config text.
     *
     * @throws IllegalArgumentException if it isn't JSON or has no "plants" list
     */
    public static GardenConfig parse(String json) {
        Object root = Json.parse(json);
        if (!(root instanceof Map)) throw new IllegalArgumentException("the config must be a JSON object");
        Object plants = ((Map<?, ?>) root).get("plants");
        if (!(plants instanceof List)) throw new IllegalArgumentException("the config needs a \"plants\" list");

        Map<Species, Integer> amounts = new LinkedHashMap<Species, Integer>();
        List<String> warnings = new ArrayList<String>();
        int index = 0;
        for (Object item : (List<?>) plants) {
            index++;
            if (!(item instanceof Map)) {
                warnings.add("plants entry " + index + " is not an object; skipped");
                continue;
            }
            Map<?, ?> entry = (Map<?, ?>) item;
            Object name = entry.get("name");
            Object amount = entry.get("amount");
            if (!(name instanceof String)) {
                warnings.add("plants entry " + index + " has no \"name\"; skipped");
                continue;
            }
            Species species = Species.fromName((String) name);
            if (species == null) {
                warnings.add("unknown plant \"" + name + "\" in entry " + index + "; skipped (known: " + knownNames() + ")");
                continue;
            }
            if (!(amount instanceof Double) || ((Double) amount) % 1 != 0) {
                warnings.add("\"" + name + "\" needs a whole-number \"amount\"; skipped");
                continue;
            }
            double value = (Double) amount;
            if (value < 0) {
                warnings.add("\"" + name + "\" has a negative amount; skipped");
                continue;
            }
            int count = (int) Math.min(value, MAX_PER_ENTRY);
            if (value > MAX_PER_ENTRY) {
                warnings.add("\"" + name + "\" amount " + (long) value + " is too large; planting " + MAX_PER_ENTRY);
            }
            Integer before = amounts.get(species);
            amounts.put(species, (before == null ? 0 : before) + count);
        }
        ensureMinimums(amounts, warnings);
        return new GardenConfig(amounts, warnings, "config text");
    }

    /** One of each species, ten of each: the same 40 trees as the GUI. */
    public static GardenConfig defaults() {
        Map<Species, Integer> amounts = new LinkedHashMap<Species, Integer>();
        for (Species species : Species.values()) {
            amounts.put(species, DEFAULT_PER_SPECIES);
        }
        return new GardenConfig(amounts, new ArrayList<String>(), "built-in defaults");
    }

    /** Every species must be in the garden, and there must be at least MIN_PLANTS plants. */
    private static void ensureMinimums(Map<Species, Integer> amounts, List<String> warnings) {
        for (Species species : Species.values()) {
            Integer n = amounts.get(species);
            if (n == null || n == 0) {
                amounts.put(species, 1);
                warnings.add("no " + species.displayName + " listed; added 1 so every variety is in the garden");
            }
        }
        int total = 0;
        for (int n : amounts.values()) total += n;
        if (total >= MIN_PLANTS) return;
        int missing = MIN_PLANTS - total;
        List<Species> order = new ArrayList<Species>(amounts.keySet());
        for (int i = 0; i < missing; i++) {
            Species species = order.get(i % order.size());
            amounts.put(species, amounts.get(species) + 1);
        }
        warnings.add("only " + total + " plants listed; added " + missing + " to reach the minimum of " + MIN_PLANTS);
    }

    private void addWarning(String warning) {
        warnings.add(warning);
    }

    private static String knownNames() {
        StringBuilder names = new StringBuilder();
        for (Species species : Species.values()) {
            if (names.length() > 0) names.append(", ");
            names.append(species.displayName);
        }
        return names.toString();
    }
}

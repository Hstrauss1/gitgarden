package garden.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import garden.Species;

class GardenConfigTest {

    @TempDir
    Path folder;

    private static String config(String entries) {
        return "{ \"plants\": [ " + entries + " ] }";
    }

    private static boolean anyWarningContains(GardenConfig config, String text) {
        for (String warning : config.getWarnings()) {
            if (warning.contains(text)) return true;
        }
        return false;
    }

    @Test
    void readsTheFormatFromTheApiDocument() {
        GardenConfig config = GardenConfig.parse(config(
                "{ \"name\": \"Juniper\", \"amount\": 4 }, { \"name\": \"Cherry Blossom\", \"amount\": 3 },"
                        + "{ \"name\": \"Japanese Maple\", \"amount\": 2 }, { \"name\": \"Pine\", \"amount\": 1 }"));
        Map<Species, Integer> amounts = config.getAmounts();
        assertEquals(Arrays.asList(Species.JUNIPER, Species.CHERRY, Species.MAPLE, Species.PINE), new ArrayList<Species>(amounts.keySet()));
        assertEquals(Integer.valueOf(4), amounts.get(Species.JUNIPER));
        assertEquals(Integer.valueOf(1), amounts.get(Species.PINE));
        assertEquals(10, config.getTotal());
        assertTrue(config.getWarnings().isEmpty(), config.getWarnings().toString());
    }

    @Test
    void namesAreMatchedLoosely() {
        GardenConfig config = GardenConfig.parse(config(
                "{ \"name\": \"juniper\", \"amount\": 3 }, { \"name\": \"CHERRY\", \"amount\": 3 },"
                        + "{ \"name\": \"japanese_maple\", \"amount\": 3 }, { \"name\": \" pine \", \"amount\": 3 }"));
        assertEquals(12, config.getTotal());
        assertTrue(config.getWarnings().isEmpty(), config.getWarnings().toString());
    }

    @Test
    void handlesWhitespaceKeyOrderAndEscapes() {
        GardenConfig config = GardenConfig.parse("\n{\n  \"plants\" : [\n    {\"amount\":5,\"name\":\"Cherry\\u0020Blossom\"},\n"
                + "    {\"name\":\"Pine\",\"amount\":5.0, \"note\": [true, null, \"x\"]}\n  ]\n}\n");
        assertEquals(Integer.valueOf(5), config.getAmounts().get(Species.CHERRY));
        assertEquals(Integer.valueOf(5), config.getAmounts().get(Species.PINE));
    }

    @Test
    void duplicateNamesAreAddedTogether() {
        GardenConfig config = GardenConfig.parse(config(
                "{ \"name\": \"Pine\", \"amount\": 4 }, { \"name\": \"pine\", \"amount\": 3 }"));
        assertEquals(Integer.valueOf(7), config.getAmounts().get(Species.PINE));
    }

    @Test
    void badEntriesAreSkippedWithAWarning() {
        GardenConfig config = GardenConfig.parse(config(
                "{ \"name\": \"Rose\", \"amount\": 5 }, { \"name\": \"Pine\", \"amount\": -2 },"
                        + "{ \"name\": \"Juniper\", \"amount\": 2.5 }, { \"amount\": 3 }, 7,"
                        + "{ \"name\": \"Cherry Blossom\", \"amount\": \"lots\" }"));
        assertTrue(anyWarningContains(config, "unknown plant \"Rose\""));
        assertTrue(anyWarningContains(config, "negative"));
        assertTrue(anyWarningContains(config, "whole-number"));
        assertTrue(anyWarningContains(config, "no \"name\""));
        assertTrue(anyWarningContains(config, "not an object"));
    }

    @Test
    void hugeAmountsAreCapped() {
        GardenConfig config = GardenConfig.parse(config("{ \"name\": \"Pine\", \"amount\": 1000000 }"));
        assertEquals(Integer.valueOf(GardenConfig.MAX_PER_ENTRY), config.getAmounts().get(Species.PINE));
        assertTrue(anyWarningContains(config, "too large"));
    }

    @Test
    void everyVarietyIsAlwaysPlanted() {
        GardenConfig config = GardenConfig.parse(config("{ \"name\": \"Pine\", \"amount\": 12 }"));
        for (Species species : Species.values()) {
            assertTrue(config.getAmounts().get(species) >= 1, species.displayName);
        }
        assertTrue(anyWarningContains(config, "every variety"));
    }

    @Test
    void atLeastTenPlantsAreAlwaysPlanted() {
        GardenConfig config = GardenConfig.parse(config(
                "{ \"name\": \"Juniper\", \"amount\": 1 }, { \"name\": \"Cherry Blossom\", \"amount\": 1 },"
                        + "{ \"name\": \"Japanese Maple\", \"amount\": 1 }, { \"name\": \"Pine\", \"amount\": 1 }"));
        assertEquals(GardenConfig.MIN_PLANTS, config.getTotal());
        assertTrue(anyWarningContains(config, "minimum"));

        GardenConfig empty = GardenConfig.parse("{ \"plants\": [] }");
        assertEquals(GardenConfig.MIN_PLANTS, empty.getTotal());
    }

    @Test
    void textThatIsNotAConfigIsRejected() {
        List<String> broken = Arrays.asList("", "not json", "{ \"plants\": [ { \"name\": \"Pine\" ", "[]",
                "{ \"trees\": [] }", "{ \"plants\": 5 }", "{ \"plants\": [] } extra", "{ 'plants': [] }");
        for (String text : broken) {
            assertThrows(IllegalArgumentException.class, () -> GardenConfig.parse(text), text);
        }
    }

    @Test
    void loadReadsTheFileFromTheFolder() throws IOException {
        Files.write(folder.resolve(GardenConfig.FILE_NAME), config(
                "{ \"name\": \"Juniper\", \"amount\": 5 }, { \"name\": \"Cherry Blossom\", \"amount\": 5 },"
                        + "{ \"name\": \"Japanese Maple\", \"amount\": 5 }, { \"name\": \"Pine\", \"amount\": 5 }")
                .getBytes(StandardCharsets.UTF_8));
        GardenConfig config = GardenConfig.load(folder.toFile());
        assertEquals(GardenConfig.FILE_NAME, config.getSource());
        assertEquals(20, config.getTotal());
    }

    @Test
    void loadFallsBackToDefaultsWhenTheFileIsMissing() {
        GardenConfig config = GardenConfig.load(folder.toFile());
        assertEquals("built-in defaults", config.getSource());
        assertEquals(Species.values().length * GardenConfig.DEFAULT_PER_SPECIES, config.getTotal());
        assertTrue(anyWarningContains(config, "not found"));
    }

    @Test
    void loadFallsBackToDefaultsWhenTheFileIsBroken() throws IOException {
        File file = folder.resolve(GardenConfig.FILE_NAME).toFile();
        Files.write(file.toPath(), "{ plants: oops".getBytes(StandardCharsets.UTF_8));
        GardenConfig config = GardenConfig.load(folder.toFile());
        assertEquals("built-in defaults", config.getSource());
        assertTrue(anyWarningContains(config, "could not be used"));
    }
}

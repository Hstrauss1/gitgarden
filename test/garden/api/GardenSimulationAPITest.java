package garden.api;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import garden.GardenModule;
import garden.Plot;
import garden.Species;

/**
 * The API against the rules in "Gardening System APIs", on a fake clock: one simulated
 * day is DAY milliseconds of fake time, so a 24-day run takes a moment.
 */
class GardenSimulationAPITest {

    private static final long DAY = 1000;

    @TempDir
    Path folder;

    private final AtomicLong now = new AtomicLong(1_000_000);
    private GardenSimulationAPI api;

    private GardenSimulationAPI newApi() {
        api = new GardenSimulationAPI(new GardenSimulationAPI.Options()
                .dayMillis(DAY).clock(now::get).directory(folder.toFile())
                .background(false).echo(false).seed(7));
        return api;
    }

    private GardenSimulationAPI started() throws IOException {
        Files.write(folder.resolve(GardenConfig.FILE_NAME), ("{ \"plants\": [ { \"name\": \"Juniper\", \"amount\": 3 },"
                + " { \"name\": \"Cherry Blossom\", \"amount\": 3 }, { \"name\": \"Japanese Maple\", \"amount\": 2 },"
                + " { \"name\": \"Pine\", \"amount\": 2 } ] }").getBytes(StandardCharsets.UTF_8));
        newApi().initializeGarden();
        return api;
    }

    private void passDays(double days) {
        now.addAndGet((long) (days * DAY));
        api.catchUp();
    }

    private String log() throws IOException {
        return new String(Files.readAllBytes(folder.resolve(GardenSimulationAPI.LOG_FILE)), StandardCharsets.UTF_8);
    }

    private List<Plot> plotsOf(Species species) {
        List<Plot> found = new ArrayList<Plot>();
        for (Plot plot : api.garden().getPlots()) {
            if (plot.getSpecies() == species) found.add(plot);
        }
        return found;
    }

    private void switchOff(String moduleName) {
        for (GardenModule module : api.garden().getModules()) {
            if (module.getName().equals(moduleName)) module.setEnabled(false, api.garden().getGarden());
        }
    }

    @AfterEach
    void tearDown() {
        if (api != null) api.shutdown();
    }

    // ---- initializeGarden and getPlants ----

    @Test
    void initializePlantsTheConfigAndStartsDayOne() throws IOException {
        started();
        assertEquals(1, api.getDay());
        assertEquals(10, api.garden().getPlots().size());
        assertEquals(10, api.garden().countAlive());
        String log = log();
        assertTrue(log.contains("| INIT "), log);
        assertTrue(log.contains("Planted from garden-config.json: 3 Juniper, 3 Cherry Blossom, 2 Japanese Maple, 2 Pine"), log);
    }

    @Test
    void withoutAConfigFileItPlantsTheDefaultsAndSaysSo() throws IOException {
        newApi().initializeGarden();
        assertEquals(40, api.garden().countAlive());
        assertTrue(log().contains("not found"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void getPlantsListsEveryVarietyWithParallelLists() throws IOException {
        started();
        HashMap<String, Object> plants = api.getPlants();
        List<String> names = (List<String>) plants.get("plants");
        List<Integer> water = (List<Integer>) plants.get("waterRequirement");
        List<List<String>> parasites = (List<List<String>>) plants.get("parasites");
        assertEquals(4, names.size());
        assertEquals(names.size(), water.size());
        assertEquals(names.size(), parasites.size());
        for (int i = 0; i < names.size(); i++) {
            Species species = Species.fromName(names.get(i));
            assertEquals(species.waterRequirement, (int) water.get(i));
            assertEquals(species.getParasites(), parasites.get(i));
        }
    }

    @Test
    void logAndConfigUseRelativePathsByDefault() {
        GardenSimulationAPI.Options defaults = new GardenSimulationAPI.Options();
        assertFalse(defaults.directory.isAbsolute());
        assertFalse(new File(defaults.directory, GardenSimulationAPI.LOG_FILE).isAbsolute());
        assertEquals(GardenSimulationAPI.REAL_DAY_MILLIS, defaults.dayMillis);
    }

    // ---- the clock ----

    @Test
    void eachDayLengthOfTimeIsANewDay() throws IOException {
        started();
        passDays(0.999);
        assertEquals(1, api.getDay());
        passDays(0.001);
        assertEquals(2, api.getDay());
        passDays(22);
        assertEquals(24, api.getDay());
        assertTrue(log().contains("| END_OF_DAY "));
    }

    @Test
    void healthCarriesOverFromDayToDay() throws IOException {
        started();
        switchOff("Pest control");
        api.parasite("pine beetles");
        passDays(1);
        double afterOne = plotsOf(Species.PINE).get(0).getHealth();
        assertTrue(afterOne < 100);
        passDays(1);
        assertTrue(plotsOf(Species.PINE).get(0).getHealth() < afterOne);
    }

    // ---- rain and temperature ----

    @Test
    void rainWetsEachBedByItsWaterRequirement() throws IOException {
        started();
        Plot juniper = plotsOf(Species.JUNIPER).get(0);
        Plot maple = plotsOf(Species.MAPLE).get(0);
        double juniperBefore = juniper.getMoisture();
        double mapleBefore = maple.getMoisture();
        api.rain(5);   // half a juniper's daily need, a fifth of a maple's
        passDays(0.5);
        assertEquals(Math.min(100, juniperBefore + 7.5), juniper.getMoisture(), 1e-6);   // half has fallen
        passDays(0.5);
        assertEquals(Math.min(100, juniperBefore + 15), juniper.getMoisture(), 1e-6);   // no drying while it rains
        assertEquals(Math.min(100, mapleBefore + 6), maple.getMoisture(), 1e-6);
    }

    @Test
    void rainStopsAfterOneDayUnlessItRainsAgain() throws IOException {
        started();
        api.rain(25);
        assertTrue(api.garden().isRaining());
        passDays(1);
        assertFalse(api.garden().isRaining());
        assertTrue(log().contains("| RAIN_RESET "));

        api.rain(10);
        passDays(0.5);
        assertTrue(api.garden().isRaining());
        passDays(0.5);
        api.rain(10);   // the next day is rainy too
        assertTrue(api.garden().isRaining());
    }

    @Test
    void temperatureGoesBackToNormalWhenTheDayEnds() throws IOException {
        started();
        api.temperature(100);
        assertEquals(100, api.garden().getTemperatureF());
        passDays(0.9);
        assertEquals(100, api.garden().getTemperatureF());
        passDays(0.1);
        assertEquals(ApiGarden.DEFAULT_TEMPERATURE_F, api.garden().getTemperatureF());
        assertTrue(log().contains("| TEMPERATURE_RESET "));
    }

    @Test
    void shadeClothProtectsMaplesFromAHotDayButNotTheHottest() throws IOException {
        started();
        api.temperature(100);
        passDays(1);
        assertEquals(100, plotsOf(Species.MAPLE).get(0).getHealth(), 1e-9);

        api.temperature(120);
        passDays(1);
        assertTrue(plotsOf(Species.MAPLE).get(0).getHealth() < 90);
        assertTrue(plotsOf(Species.JUNIPER).get(0).getHealth() > plotsOf(Species.MAPLE).get(0).getHealth());
    }

    @Test
    void withoutClimateControlA105DegreeDayHurtsMaples() throws IOException {
        started();
        switchOff("Climate control");
        api.temperature(105);
        passDays(1);
        assertTrue(plotsOf(Species.MAPLE).get(0).getHealth() < 90);
    }

    // ---- parasites and pest control ----

    @Test
    void parasitesOnlyInfestVulnerablePlants() throws IOException {
        started();
        api.parasite("Aphid");
        Map<Plot, ParasiteControl.Infestation> infested = api.garden().getInfestations();
        assertEquals(plotsOf(Species.CHERRY).size() + plotsOf(Species.MAPLE).size() + plotsOf(Species.PINE).size(), infested.size());
        for (Plot plot : plotsOf(Species.JUNIPER)) {
            assertFalse(infested.containsKey(plot));
        }
    }

    @Test
    void anUnknownParasiteInfestsNothingAndIsLogged() throws IOException {
        started();
        api.parasite("insects");
        assertTrue(api.garden().getInfestations().isEmpty());
        assertTrue(log().contains("No plant in this garden is vulnerable to \"insects\""));
    }

    @Test
    void pestControlNeverHealsThePlant() throws IOException {
        started();
        Plot pine = plotsOf(Species.PINE).get(0);
        api.parasite("pine beetles");
        double last = pine.getHealth();
        int steps = 0;
        while (api.garden().getInfestations().containsKey(pine)) {
            now.addAndGet(DAY / ApiGarden.TICKS_PER_DAY);
            api.catchUp();
            if (api.garden().getInfestations().containsKey(pine)) {
                // once cleared, it may start healing in that same step; until then it must not
                assertTrue(pine.getHealth() <= last + 1e-9, "health rose while infested");
            }
            last = pine.getHealth();
            assertTrue(++steps < ApiGarden.TICKS_PER_DAY, "pest control should clear it within a day");
        }
        assertTrue(pine.getHealth() < 100, "cleared, but the damage is still there");
        assertTrue(pine.hasTree());
    }

    @Test
    void untreatedParasitesKillAndDeadTypesLeaveGetPlants() throws IOException {
        started();
        switchOff("Pest control");
        api.parasite("pine beetles");
        passDays(5);
        for (Plot pine : plotsOf(Species.PINE)) {
            assertFalse(pine.hasTree());
        }
        @SuppressWarnings("unchecked")
        List<String> names = (List<String>) api.getPlants().get("plants");
        assertFalse(names.contains("Pine"));
        assertTrue(names.contains("Juniper"));
        api.getState();
        String log = log();
        assertTrue(log.contains("| ALERT "));
        assertTrue(log.contains("DEAD   since day"));
    }

    // ---- robustness ----

    @Test
    void callsBeforeInitializeStillWork() throws IOException {
        newApi();
        assertDoesNotThrow(() -> api.rain(10));
        assertEquals(1, api.getDay());
        assertTrue(log().contains("called before initializeGarden()"));
    }

    @Test
    void badInputIsLoggedNotThrown() throws IOException {
        started();
        assertDoesNotThrow(() -> {
            api.parasite(null);
            api.parasite("   ");
            api.rain(-5);
            api.temperature(500);
            api.temperature(-40);
        });
        String log = log();
        assertTrue(log.contains("without a name"));
        assertTrue(log.contains("Negative rainfall"));
        assertTrue(log.contains("Outside the expected 40-120"));
        assertFalse(log.contains("| ERROR "));
    }

    @Test
    void theExampleScriptsMethodNamesWorkToo() throws IOException {
        started();
        api.parasites("aphids");
        assertFalse(api.garden().getInfestations().isEmpty());
        api.getStatus();
        assertTrue(log().contains("| STATE "));
    }

    @Test
    void aFullRandomRunEndsWithAClearReportAndNoErrors() throws IOException {
        started();
        Random random = new Random(2026);
        @SuppressWarnings("unchecked")
        List<List<String>> parasites = (List<List<String>>) api.getPlants().get("parasites");
        for (int day = 1; day <= 24; day++) {
            switch (random.nextInt(3)) {
                case 0: api.rain(random.nextInt(51)); break;
                case 1: api.temperature(40 + random.nextInt(81)); break;
                default:
                    List<String> options = parasites.get(random.nextInt(parasites.size()));
                    api.parasite(options.get(random.nextInt(options.size())));
            }
            passDays(1);
        }
        api.getState();
        String log = log();
        assertFalse(log.contains("| ERROR "), "errors in log.txt");
        assertTrue(log.contains("| STATE ") && log.contains("24 days done"));
        assertTrue(log.contains("EVERY PLANT"));
        assertEquals(10, api.garden().countAlive(), "with every module on, nothing should die");
    }
}

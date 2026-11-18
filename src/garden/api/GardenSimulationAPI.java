package garden.api;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

import garden.GardenLog;
import garden.Plot;
import garden.Species;
import garden.WateringSystem;

/**
 * The garden's scripted API, as described in "Gardening System APIs": a test script calls
 * {@link #initializeGarden()}, then {@link #rain}, {@link #temperature} or {@link #parasite}
 * once a day (one real hour), and {@link #getState()} at the end. No GUI is opened.
 *
 * Every event is written to {@code log.txt} in the working directory. No method ever
 * throws: problems are logged as ERROR lines and the garden keeps going. Calling a method
 * before {@code initializeGarden()} initializes the garden first.
 *
 * The method names used in the API document's example script, {@link #parasites} and
 * {@link #getStatus}, also work.
 */
public class GardenSimulationAPI {

    /** One simulated day lasts one real hour, as the API document says. */
    public static final long REAL_DAY_MILLIS = 60L * 60 * 1000;
    /** Set this system property (milliseconds) to run days faster, e.g. -Dgarden.dayMillis=5000. */
    public static final String DAY_MILLIS_PROPERTY = "garden.dayMillis";
    public static final String LOG_FILE = "log.txt";

    /** Settings for a run; the defaults are what the graders' script gets. */
    public static final class Options {
        long dayMillis = Long.getLong(DAY_MILLIS_PROPERTY, REAL_DAY_MILLIS);
        LongSupplier clock = System::currentTimeMillis;
        File directory = new File(".");
        boolean background = true;
        boolean echo = true;
        Long seed;
        double recoveryRate = Plot.RECOVERY;

        /** Real milliseconds per simulated day. */
        public Options dayMillis(long millis) { dayMillis = Math.max(1, millis); return this; }
        /** Where "now" comes from (tests pass a fake clock). */
        public Options clock(LongSupplier now) { clock = now; return this; }
        /** Folder for log.txt and garden-config.json (relative paths stay relative). */
        public Options directory(File folder) { directory = folder; return this; }
        /** Keep the simulation and log.txt moving between calls (on by default). */
        public Options background(boolean on) { background = on; return this; }
        /** Also print log lines to standard output (on by default). */
        public Options echo(boolean on) { echo = on; return this; }
        /** Fixed random seed, for repeatable runs. */
        public Options seed(long value) { seed = value; return this; }
        /** Health each plant regains per simulated second when nothing hurts it (default 2, i.e. 20 a day). */
        public Options recoveryRate(double perSecond) { recoveryRate = perSecond; return this; }
    }

    private final Options options;
    private ApiGarden garden;
    private ApiLog log;
    private ScheduledExecutorService ticker;
    private long startMillis;

    public GardenSimulationAPI() {
        this(new Options());
    }

    /** @param dayMillis real milliseconds per simulated day (the default is one hour) */
    public GardenSimulationAPI(long dayMillis) {
        this(new Options().dayMillis(dayMillis));
    }

    public GardenSimulationAPI(Options options) {
        this.options = options;
    }

    // ---- the API ----

    /** Plants the garden from garden-config.json (or the built-in list) and starts day 1. */
    public synchronized void initializeGarden() {
        try {
            stopTicker();
            if (log != null) log.close();
            log = new ApiLog(new File(options.directory, LOG_FILE), options.echo);
            garden = null;
            startMillis = options.clock.getAsLong();
            log.info(1, "INIT", "-", "initializeGarden() called: the clock starts now, day 1 begins. One day = "
                    + describeDay(options.dayMillis));

            GardenConfig config = GardenConfig.load(options.directory);
            for (String warning : config.getWarnings()) {
                log.warn(1, "CONFIG", "warning", warning);
            }
            Random random = options.seed == null ? new Random() : new Random(options.seed);
            garden = new ApiGarden(config, log, random, options.recoveryRate);
            final ApiGarden current = garden;
            log.setCounter(() -> current.countAlive() + "/" + current.getPlots().size());
            GardenLog.setListener(this::fromGardenLog);

            StringBuilder planted = new StringBuilder();
            for (Map.Entry<Species, Integer> entry : config.getAmounts().entrySet()) {
                if (planted.length() > 0) planted.append(", ");
                planted.append(entry.getValue()).append(" ").append(entry.getKey().displayName);
            }
            log.info(1, "CONFIG", config.getTotal() + " plants", "Planted from " + config.getSource() + ": " + planted);
            log.info(1, "INIT", "4 modules", "Running: watering system (sprinklers on below "
                    + (int) WateringSystem.ON_BELOW + "% soil moisture), drainage (drains open above "
                    + (int) Drainage.OPEN_ABOVE + "%), climate control, pest control");
            startTicker();
        } catch (RuntimeException | Error problem) {
            logError("initializeGarden", problem);
        }
    }

    /**
     * The plant types still alive: "plants" (names), "waterRequirement" (mm a day) and
     * "parasites" (what each is vulnerable to), as parallel lists. A type disappears once
     * all of its plants have died.
     */
    public synchronized HashMap<String, Object> getPlants() {
        HashMap<String, Object> result = new HashMap<String, Object>();
        List<String> names = new ArrayList<String>();
        List<Integer> water = new ArrayList<Integer>();
        List<List<String>> parasites = new ArrayList<List<String>>();
        result.put("plants", names);
        result.put("waterRequirement", water);
        result.put("parasites", parasites);
        try {
            ready("getPlants");
            for (Species species : garden.livingVarieties()) {
                names.add(species.displayName);
                water.add(species.waterRequirement);
                parasites.add(new ArrayList<String>(species.getParasites()));
            }
            log.info(garden.currentDay(), "GET_PLANTS", names.size() + " types", "plants=" + names
                    + ", waterRequirement=" + water + ", parasites=" + parasites);
        } catch (RuntimeException | Error problem) {
            logError("getPlants", problem);
        }
        return result;
    }

    /** Rain today, in mm. Each plant's bed gets wetter in proportion to its water requirement. */
    public synchronized void rain(int amount) {
        try {
            ready("rain");
            if (amount < 0) {
                log.warn(garden.currentDay(), "RAIN", amount + " mm", "Negative rainfall makes no sense; treated as 0 mm");
                amount = 0;
            }
            garden.rain(amount);
        } catch (RuntimeException | Error problem) {
            logError("rain", problem);
        }
    }

    /** Today's temperature in °F (40 to 120 expected); it goes back to 68°F when the day ends. */
    public synchronized void temperature(int fahrenheit) {
        try {
            ready("temperature");
            if (fahrenheit < 40 || fahrenheit > 120) {
                log.warn(garden.currentDay(), "TEMPERATURE", fahrenheit + " F",
                        "Outside the expected 40-120°F range; using it anyway");
            }
            garden.temperature(fahrenheit);
        } catch (RuntimeException | Error problem) {
            logError("temperature", problem);
        }
    }

    /** An infestation of the named parasite on every living plant vulnerable to it. */
    public synchronized void parasite(String name) {
        try {
            ready("parasite");
            if (name == null || name.trim().isEmpty()) {
                log.warn(garden.currentDay(), "PARASITE", "(none)", "parasite() was called without a name; nothing happened");
                return;
            }
            garden.parasite(name);
        } catch (RuntimeException | Error problem) {
            logError("parasite", problem);
        }
    }

    /** Same as {@link #parasite}; the API document's example script uses this name. */
    public void parasites(String name) {
        parasite(name);
    }

    /** Logs the full state of the garden: every plant alive or dead, health, and the modules. */
    public synchronized void getState() {
        try {
            ready("getState");
            garden.report();
        } catch (RuntimeException | Error problem) {
            logError("getState", problem);
        }
    }

    /** Same as {@link #getState}; the API document's example script uses this name. */
    public void getStatus() {
        getState();
    }

    /** Stops the background clock and closes log.txt. The script doesn't have to call this. */
    public synchronized void shutdown() {
        stopTicker();
        GardenLog.setListener(null);
        if (log != null) log.close();
    }

    // ---- for tests and the balance check ----

    /** Brings the simulation up to the current time. */
    public synchronized void catchUp() {
        if (garden == null) return;
        try {
            long elapsed = Math.max(0, options.clock.getAsLong() - startMillis);
            long target = elapsed * ApiGarden.TICKS_PER_DAY / options.dayMillis;
            while (garden.getTicks() < target) {
                garden.tick();
            }
        } catch (RuntimeException | Error problem) {
            logError("simulation clock", problem);
        }
    }

    /** The current simulated day (1 until the first day ends), or 0 before initializeGarden(). */
    public synchronized int getDay() {
        return garden == null ? 0 : garden.currentDay();
    }

    synchronized ApiGarden garden() { return garden; }
    synchronized ApiLog log() { return log; }

    // ---- inside ----

    /** Every API call first catches up with the clock (and initializes if the script forgot). */
    private void ready(String caller) {
        if (garden == null) {
            System.err.println(caller + "() was called before initializeGarden(); initializing now");
            initializeGarden();
            if (log != null) {
                log.warn(1, "INIT", caller + "()", caller + "() was called before initializeGarden(); the garden was initialized first");
            }
        }
        if (garden == null) throw new IllegalStateException("the garden could not be initialized");
        catchUp();
    }

    /** Plant events from the shared model (wilting, recovery, death) go into log.txt too. */
    private void fromGardenLog(GardenLog.Entry entry) {
        ApiLog current = log;
        ApiGarden now = garden;
        if (current == null || now == null) return;
        if (entry.level == GardenLog.Level.INFO && entry.category != GardenLog.Category.PLANT) return;   // sprinkler chatter is summarized daily
        if (entry.message.contains(": planted a ")) return;   // already in the CONFIG line
        current.log(entry.level, now.currentDay(), entry.category.name(), "-", entry.message);
    }

    private void logError(String where, Throwable problem) {
        try {
            if (log != null) {
                log.error(garden == null ? 1 : garden.currentDay(), where, problem);
            } else {
                System.err.println(where + " failed: " + problem);
            }
        } catch (RuntimeException ignored) {
            // nothing more we can do; the garden must keep going
        }
    }

    private void startTicker() {
        if (!options.background) return;
        long period = Math.max(20, options.dayMillis / ApiGarden.TICKS_PER_DAY);
        ticker = Executors.newSingleThreadScheduledExecutor(task -> {
            Thread thread = new Thread(task, "garden-clock");
            thread.setDaemon(true);   // never keeps the script's JVM alive
            return thread;
        });
        ticker.scheduleAtFixedRate(() -> {
            try {
                catchUp();
            } catch (Throwable problem) {
                logError("background clock", problem);
            }
        }, period, period, TimeUnit.MILLISECONDS);
    }

    private void stopTicker() {
        if (ticker != null) {
            ticker.shutdownNow();
            ticker = null;
        }
    }

    private static String describeDay(long millis) {
        if (millis % REAL_DAY_MILLIS == 0) return (millis / REAL_DAY_MILLIS) + " real hour(s)";
        if (millis >= 1000) return String.format("%.1f real seconds", millis / 1000.0);
        return millis + " real ms";
    }
}

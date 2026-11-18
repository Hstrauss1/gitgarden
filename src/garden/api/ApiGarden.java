package garden.api;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import garden.Garden;
import garden.GameClock;
import garden.GardenModule;
import garden.Pipe;
import garden.Plot;
import garden.Species;
import garden.WateringSystem;
import garden.Weather;
import garden.api.ParasiteControl.Infestation;

/**
 * The garden behind {@link GardenSimulationAPI}: the same {@link Plot}s, {@link Species}
 * and {@link WateringSystem} as the GUI, with no random weather, critters or replanting.
 * The test script controls the weather instead.
 *
 * Time moves in ticks of 0.25 simulated seconds, {@value #TICKS_PER_DAY} to a day
 * (a day is {@link GameClock#SECONDS_PER_DAY} simulated seconds, as in the GUI).
 * Each tick, in order: the modules run, parasites feed, and every plot updates
 * (soil drying, sprinklers, damage, healing). At the end of each day, rain and
 * temperature go back to normal and a summary is logged.
 */
final class ApiGarden {

    static final int TICKS_PER_DAY = 40;
    static final double TICK = GameClock.SECONDS_PER_DAY / TICKS_PER_DAY;
    /** The temperature on a day nobody set one, and what it resets to. */
    static final int DEFAULT_TEMPERATURE_F = 68;
    /** From this temperature the soil dries like in a drought. */
    static final int HEATWAVE_F = 95;
    /** Moisture points that rain of a plant's whole daily water requirement adds to its bed. */
    static final double RAIN_PER_REQUIREMENT = 30;
    /** Health a fresh infestation eats per simulated second (30 a day if nobody treats it). */
    static final double PARASITE_DAMAGE = 3.0;
    static final int PLOTS_PER_PIPE = 8;

    private final Garden garden = new Garden();
    private final WateringSystem watering;
    private final ClimateControl climate;
    private final ParasiteControl pestControl;
    private final Drainage drainage;
    private final List<GardenModule> modules;
    private final List<Plot> plots = new ArrayList<Plot>();
    private final Map<Plot, Infestation> infestations = new LinkedHashMap<Plot, Infestation>();
    private final Map<Plot, String> deaths = new LinkedHashMap<Plot, String>();
    /** Each plant's lowest health so far, and when and why, so getState() shows damage that has since healed. */
    private final Map<Plot, String> worstMoment = new HashMap<Plot, String>();
    private final Map<Plot, Double> lowestHealth = new HashMap<Plot, Double>();
    private final Set<Plot> sprinklersOn = new HashSet<Plot>();
    /** Rain still to fall today, in moisture points, per species (it falls evenly over the rest of the day). */
    private final Map<Species, Double> rainStillToFall = new LinkedHashMap<Species, Double>();
    private final List<Species> varieties;
    private final ApiLog log;

    private long ticks;
    private int rainToday = -1;      // mm, or -1 for a dry day
    private int temperatureF = DEFAULT_TEMPERATURE_F;

    // today's tallies, for the END_OF_DAY line
    private int sprinklerStarts;
    private double litresAtDayStart;
    private int diedToday;
    private int spraysAtDayStart;

    ApiGarden(GardenConfig config, ApiLog log, Random random, double recoveryRate) {
        this.log = log;
        this.varieties = new ArrayList<Species>(config.getAmounts().keySet());
        watering = garden.getWatering();
        climate = new ClimateControl(this);
        pestControl = new ParasiteControl(infestations, this);
        drainage = new Drainage(this);
        modules = Collections.unmodifiableList(new ArrayList<GardenModule>(
                java.util.Arrays.asList(watering, drainage, climate, pestControl)));

        Pipe pipe = null;
        int number = 0;
        for (Map.Entry<Species, Integer> entry : config.getAmounts().entrySet()) {
            for (int i = 0; i < entry.getValue(); i++) {
                if (number % PLOTS_PER_PIPE == 0) {
                    if (pipe != null) garden.addPipe(pipe);
                    pipe = new Pipe("Row " + (number / PLOTS_PER_PIPE + 1));
                }
                number++;
                // stagger starting moisture so the sprinklers don't all fire together
                Plot plot = new Plot(String.format("Plot %02d", number), 55 + random.nextInt(36));
                plot.plant(entry.getKey(), random.nextLong(), 16, 3, 0);
                plot.setRecoveryRate(recoveryRate);
                pipe.add(plot);
                plots.add(plot);
            }
        }
        if (pipe != null) garden.addPipe(pipe);
    }

    // ---- what the test script can do ----

    /**
     * Rain falls evenly over the rest of the day. A plant gets {@link #RAIN_PER_REQUIREMENT}
     * moisture points for each whole daily water requirement of rain, so the same rain
     * soaks a drought-hardy juniper more than a thirsty maple. Soil doesn't dry while it rains.
     */
    void rain(int mm) {
        rainToday = Math.max(rainToday, 0) + mm;
        StringBuilder soaked = new StringBuilder();
        for (Species species : varieties) {
            double points = mm / (double) species.waterRequirement * RAIN_PER_REQUIREMENT;
            Double before = rainStillToFall.get(species);
            rainStillToFall.put(species, (before == null ? 0 : before) + points);
            if (soaked.length() > 0) soaked.append(", ");
            soaked.append(String.format("%s +%.0f", species.displayName, points));
        }
        log.info(currentDay(), "RAIN", mm + " mm", "Rain falling for the rest of the day; soil moisture points it adds: " + soaked
                + "; soil doesn't dry while it rains");
    }

    void temperature(int fahrenheit) {
        temperatureF = fahrenheit;
        String effect;
        double bed = climate.isEnabled() ? toCelsius(fahrenheit) - (toCelsius(fahrenheit) > ClimateControl.SHADE_ABOVE_C ? ClimateControl.SHADE_COOLING : 0)
                : toCelsius(fahrenheit);
        if (fahrenheit < 32) effect = "freezing: frost-tender trees need the bed heating";
        else if (bed > Plot.HEAT_LIMIT) effect = "dangerously hot: trees suffer heat stress even under the shade cloth";
        else if (fahrenheit >= HEATWAVE_F) effect = "heatwave: soil dries 3x faster today, the shade cloth goes on";
        else effect = "normal growing weather";
        log.info(currentDay(), "TEMPERATURE", fahrenheit + " F", String.format("Today's temperature is %d°F (%.1f°C): %s",
                fahrenheit, toCelsius(fahrenheit), effect));
    }

    void parasite(String name) {
        List<String> species = new ArrayList<String>();
        int infested = 0;
        for (Species kind : varieties) {
            if (!kind.isVulnerableTo(name)) continue;
            species.add(kind.displayName);
            for (Plot plot : plots) {
                if (plot.getSpecies() != kind || !plot.hasTree()) continue;
                Infestation pest = infestations.get(plot);
                if (pest == null) {
                    infestations.put(plot, new Infestation(name.trim()));
                } else {
                    pest.severity = Math.min(2, pest.severity + 1);   // a second wave on top of the first
                }
                infested++;
            }
        }
        if (species.isEmpty()) {
            log.warn(currentDay(), "PARASITE", name, "No plant in this garden is vulnerable to \"" + name
                    + "\", so nothing was infested. Known parasites: " + knownParasites());
        } else {
            log.warn(currentDay(), "PARASITE", name, String.format("%d plants infested (%s are vulnerable to %s)",
                    infested, String.join(", ", species), name));
        }
    }

    // ---- the passage of time ----

    void tick() {
        garden.getClock().advance(TICK);
        Weather weather = rainToday >= 0 ? Weather.RAIN : temperatureF >= HEATWAVE_F ? Weather.DROUGHT : Weather.CLEAR;
        GameClock.Season season = garden.getClock().getSeason();
        double air = toCelsius(temperatureF);

        climate.setAirTemperature(air);
        for (GardenModule module : modules) {
            safely(module.getName(), () -> module.update(TICK, garden));
        }
        final double bed = climate.bedTemperature(air);
        final Map<Species, Double> rainNow = rainThisTick();

        for (Plot plot : plots) {
            safely(plot.getName(), () -> {
                Infestation pest = infestations.get(plot);
                if (pest != null && plot.hasTree()) {
                    plot.parasiteDamage(PARASITE_DAMAGE * pest.severity * TICK, pest.parasite + " infestation");
                }
                Double rain = rainNow.get(plot.getSpecies());
                plot.tick(TICK, weather, season, bed, rain == null ? 0 : rain / TICK);
                noteSprinkler(plot);
                noteLowest(plot);
                noteDeath(plot);
            });
        }

        ticks++;
        if (ticks % TICKS_PER_DAY == 0) {
            safely("End of day", this::endOfDay);
        }
    }

    /** Shares out today's remaining rain over the ticks left in the day; moisture points per species. */
    private Map<Species, Double> rainThisTick() {
        Map<Species, Double> now = new LinkedHashMap<Species, Double>();
        long ticksLeft = TICKS_PER_DAY - ticks % TICKS_PER_DAY;
        for (Map.Entry<Species, Double> entry : rainStillToFall.entrySet()) {
            double share = entry.getValue() / ticksLeft;
            now.put(entry.getKey(), share);
            entry.setValue(entry.getValue() - share);
        }
        return now;
    }

    private void noteSprinkler(Plot plot) {
        if (plot.isSprinklerOn()) {
            if (sprinklersOn.add(plot)) sprinklerStarts++;
        } else {
            sprinklersOn.remove(plot);
        }
    }

    private void noteLowest(Plot plot) {
        Double lowest = lowestHealth.get(plot);
        if (plot.getHealth() < (lowest == null ? 100 : lowest) - 1e-9) {
            lowestHealth.put(plot, plot.getHealth());
            worstMoment.put(plot, "day " + currentDay() + ", " + plot.getLastDamage());
        }
    }

    /** The lowest health this plant has had (100 if it was never hurt). */
    double getLowestHealth(Plot plot) {
        Double lowest = lowestHealth.get(plot);
        return lowest == null ? 100 : lowest;
    }

    private void noteDeath(Plot plot) {
        if (plot.getState() != Plot.State.DEAD || deaths.containsKey(plot)) return;
        deaths.put(plot, "day " + currentDay() + ", " + plot.getLastDamage());
        infestations.remove(plot);
        diedToday++;
    }

    private void endOfDay() {
        int day = (int) (ticks / TICKS_PER_DAY);
        double total = 0;
        double lowest = 101;
        Plot weakest = null;
        int alive = 0;
        for (Plot plot : plots) {
            if (!plot.hasTree()) continue;
            alive++;
            total += plot.getHealth();
            if (plot.getHealth() < lowest) {
                lowest = plot.getHealth();
                weakest = plot;
            }
        }
        String weather = (rainToday >= 0 ? "rain " + rainToday + " mm, " : "") + temperatureF + "°F";
        log.info(day, "END_OF_DAY", alive + " alive", String.format(
                "Day %d over (%s): average health %.0f%%, lowest %s at %.0f%%, %d infested, %d sprinkler starts, %.0f L water, %d sprays, %d died today",
                day, weather, alive == 0 ? 0 : total / alive, weakest == null ? "-" : describe(weakest), weakest == null ? 0 : lowest,
                infestations.size(), sprinklerStarts, watering.getLitres() - litresAtDayStart,
                pestControl.getSprays() - spraysAtDayStart, diedToday));

        if (rainToday >= 0) {
            log.info(day, "RAIN_RESET", "dry", "The rain has passed; the soil dries normally again from tomorrow");
            rainToday = -1;
            rainStillToFall.clear();
        }
        if (temperatureF != DEFAULT_TEMPERATURE_F) {
            log.info(day, "TEMPERATURE_RESET", DEFAULT_TEMPERATURE_F + " F",
                    "The day is over; the temperature goes back to " + DEFAULT_TEMPERATURE_F + "°F");
            temperatureF = DEFAULT_TEMPERATURE_F;
        }
        sprinklerStarts = 0;
        litresAtDayStart = watering.getLitres();
        spraysAtDayStart = pestControl.getSprays();
        diedToday = 0;
    }

    /** Runs a piece of the simulation; if it throws, the error is logged and the garden carries on. */
    private void safely(String what, Runnable work) {
        try {
            work.run();
        } catch (RuntimeException problem) {
            log.error(currentDay(), what, problem);
        }
    }

    // ---- reports ----

    /** Writes the full getState() report. */
    void report() {
        int day = (int) (ticks / TICKS_PER_DAY);
        int alive = countAlive();
        log.info(currentDay(), "STATE", day + " days done", String.format("%d of %d plants alive, %d dead; %d infested; %d errors, %d warnings, %d alerts logged so far",
                alive, plots.size(), plots.size() - alive, infestations.size(), log.getErrors(), log.getWarnings(), log.getAlerts()));
        log.raw("    BY SPECIES");
        for (Species species : varieties) {
            int count = 0;
            int living = 0;
            double health = 0;
            double lowest = 100;
            for (Plot plot : plots) {
                if (plot.getSpecies() != species) continue;
                count++;
                lowest = Math.min(lowest, getLowestHealth(plot));
                if (plot.hasTree()) {
                    living++;
                    health += plot.getHealth();
                }
            }
            log.raw(String.format("      %-15s %2d/%-2d alive   average health now %3.0f%%   lowest during the run %3.0f%%   needs %d mm water/day   parasites: %s",
                    species.displayName, living, count, living == 0 ? 0 : health / living, lowest, species.waterRequirement,
                    String.join(", ", species.getParasites())));
        }
        log.raw("    EVERY PLANT");
        for (Plot plot : plots) {
            String status;
            if (plot.hasTree()) {
                Infestation pest = infestations.get(plot);
                double lowest = getLowestHealth(plot);
                status = String.format("ALIVE  health %3.0f%%  soil %3.0f%%  %-46s%s%s", plot.getHealth(), plot.getMoisture(),
                        lowest > 99.5 ? "never hurt" : String.format("lowest %.0f%% (%s)", lowest, worstMoment.get(plot)),
                        plot.isSprinklerOn() ? "  sprinkler on" : "",
                        pest == null ? "" : String.format("  infested: %s (strength %.0f%%)", pest.parasite, pest.severity * 100));
            } else {
                status = "DEAD   since " + deaths.get(plot);
            }
            log.raw(String.format("      %-8s %-15s %s", plot.getName(), plot.getSpecies().displayName, status));
        }
        log.raw("    MODULES");
        for (GardenModule module : modules) {
            log.raw(String.format("      %-16s %s", module.getName(), module.getStatus()));
        }
        log.raw(String.format("    Weather now: %s, %d°F", rainToday >= 0 ? "rain (" + rainToday + " mm today)" : "dry", temperatureF));
    }

    // ---- questions ----

    int currentDay() {
        return (int) (ticks / TICKS_PER_DAY) + 1;
    }

    long getTicks() { return ticks; }

    int countAlive() {
        int alive = 0;
        for (Plot plot : plots) {
            if (plot.hasTree()) alive++;
        }
        return alive;
    }

    /** Species with at least one living plant, in config order. */
    List<Species> livingVarieties() {
        List<Species> living = new ArrayList<Species>();
        for (Species species : varieties) {
            for (Plot plot : plots) {
                if (plot.getSpecies() == species && plot.hasTree()) {
                    living.add(species);
                    break;
                }
            }
        }
        return living;
    }

    String describe(Plot plot) {
        return plot.getName() + " (" + plot.getSpecies().displayName + ")";
    }

    private String knownParasites() {
        List<String> names = new ArrayList<String>();
        for (Species species : varieties) {
            for (String parasite : species.getParasites()) {
                if (!names.contains(parasite)) names.add(parasite);
            }
        }
        return String.join(", ", names);
    }

    ApiLog log() { return log; }
    List<Plot> getPlots() { return Collections.unmodifiableList(plots); }
    List<GardenModule> getModules() { return modules; }
    Map<Plot, Infestation> getInfestations() { return Collections.unmodifiableMap(infestations); }
    Garden getGarden() { return garden; }
    int getTemperatureF() { return temperatureF; }
    boolean isRaining() { return rainToday >= 0; }

    static double toCelsius(double fahrenheit) {
        return (fahrenheit - 32) * 5 / 9;
    }

    static double toFahrenheit(double celsius) {
        return celsius * 9 / 5 + 32;
    }
}

package garden;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * The whole garden model: plots, pipes, the calendar, the weather and the automated
 * modules. {@link #step} advances everything by one small slice of simulated time.
 * Every module runs inside its own try/catch, so a problem in one is logged and the
 * rest of the garden keeps going.
 */
public final class Garden {

    private final List<Plot> plots = new ArrayList<Plot>();
    private final List<Pipe> pipes = new ArrayList<Pipe>();
    private final GameClock clock = new GameClock();
    private final WeatherSystem weather = new WeatherSystem();
    private final Random random = new Random();

    private final WateringSystem watering = new WateringSystem();
    private final PestControlSystem pestControl = new PestControlSystem(this);
    private final HeatingSystem heating = new HeatingSystem(this);
    private final ReplantingSystem replanting = new ReplantingSystem();
    private final List<GardenModule> modules = new ArrayList<GardenModule>();

    private Critters critters;
    private int baseLife = 16;
    private int baseMultiplier = 3;

    private int harvested;
    private int deaths;
    private int felled;
    private int stomped;
    private Weather lastWeather = Weather.CLEAR;
    private int lastDay = -1;
    private int lastSeason = -1;
    private final DailyTally dayStart = new DailyTally();
    private final List<Plot> deadLastStep = new ArrayList<Plot>();

    public Garden() {
        Collections.addAll(modules, watering, heating, pestControl, replanting);
    }

    public void addPipe(Pipe pipe) {
        pipes.add(pipe);
        plots.addAll(pipe.getPlots());
    }

    /** Advances the garden by {@code dt} simulated seconds (keep it small, about 0.25 s). */
    public void step(double dt) {
        clock.advance(dt);
        safely("Calendar", () -> logCalendar());
        safely("Weather", () -> {
            weather.update(dt, clock.getSeason());
            logWeatherChange();
        });

        for (GardenModule module : modules) {
            safely(module.getName(), () -> module.update(dt, this));
        }
        safely("Heating frost check", () -> heating.checkFrost(this));

        double bedTemperature = heating.isEnabled() ? heating.bedTemperature(getAirTemperature()) : getAirTemperature();
        for (Plot plot : plots) {
            safely(plot.getName(), () -> {
                if (plot.tick(dt, weather.getCurrent(), clock.getSeason(), bedTemperature)) {
                    harvest(plot, false);
                }
            });
        }
        countDeaths();
    }

    /** Runs a piece of the simulation; if it throws, the error is logged and the garden carries on. */
    private void safely(String what, Runnable work) {
        try {
            work.run();
        } catch (RuntimeException e) {
            GardenLog.error(GardenLog.Category.SYSTEM, what, e);
        }
    }

    // ---- planting and harvesting ----

    /** Plants a random species with a random seed, using the current Life and Multiplier settings. */
    public void replant(Plot plot) {
        Species[] all = Species.values();
        plot.plant(all[random.nextInt(all.length)], random.nextLong(), baseLife, baseMultiplier, clock.getDays());
    }

    public void harvest(Plot plot, boolean byHand) {
        harvested++;
        GardenLog.info(byHand ? GardenLog.Category.USER : GardenLog.Category.PLANT, "%s: %s harvested %s (age %s)",
                plot.getName(), plot.getSpecies().displayName, byHand ? "by hand" : "automatically",
                clock.describeAge(plot.getPlantedDay()));
        replant(plot);
    }

    public void noteFelled() { felled++; }
    public void noteStomped() { stomped++; }

    private void countDeaths() {
        for (Plot plot : plots) {
            boolean dead = plot.getState() == Plot.State.DEAD;
            if (dead && !deadLastStep.contains(plot)) {
                deadLastStep.add(plot);
                deaths++;
            } else if (!dead) {
                deadLastStep.remove(plot);
            }
        }
    }

    // ---- climate ----

    /**
     * Air temperature in °C: a seasonal average, warmest at 3pm and coldest at 3am,
     * hotter in a drought and colder in rain or wind. Winter nights drop below freezing.
     */
    public double getAirTemperature() {
        double[] seasonAverage = {14, 26, 12, 1};
        double base = seasonAverage[clock.getSeason().ordinal()];
        double daily = 6 * Math.cos(2 * Math.PI * (clock.getHour() - 15) / 24);
        double weatherShift;
        switch (weather.getCurrent()) {
            case DROUGHT: weatherShift = 7; break;
            case RAIN: weatherShift = -3; break;
            case WINDSTORM: weatherShift = -4; break;
            default: weatherShift = 0;
        }
        return base + daily + weatherShift;
    }

    // ---- logging the passage of time and weather ----

    private void logWeatherChange() {
        Weather now = weather.getCurrent();
        if (now == lastWeather) return;
        if (lastWeather != Weather.CLEAR) {
            GardenLog.info(GardenLog.Category.WEATHER, "%s ended", lastWeather.label);
        }
        if (now != Weather.CLEAR) {
            double days = weather.getTimeLeft() / GameClock.SECONDS_PER_DAY;
            switch (now) {
                case RAIN:
                    GardenLog.info(GardenLog.Category.WEATHER, "Rain began (about %.1f days): every bed is watered, cherry trees grow faster, pine roots may rot", days);
                    break;
                case DROUGHT:
                    GardenLog.warn(GardenLog.Category.WEATHER, "Drought began (about %.1f days): soil dries 3x faster, maples are thirstiest", days);
                    break;
                case WINDSTORM:
                    GardenLog.warn(GardenLog.Category.WEATHER, "Windstorm began (about %.1f days): cherry and maple trees will be damaged, pines are safe", days);
                    break;
                default:
                    break;
            }
        }
        lastWeather = now;
    }

    private void logCalendar() {
        int day = (int) clock.getDays();
        if (day == lastDay) return;
        if (lastDay >= 0) {
            dailyReport(lastDay);
        }
        lastDay = day;
        int season = clock.getSeason().ordinal();
        if (season != lastSeason) {
            if (clock.getMonth() == 1 && clock.getDayOfMonth() == 1 && lastSeason >= 0) {
                GardenLog.info(GardenLog.Category.TIME, "Happy new year: Year %d begins", clock.getYear());
            }
            GardenLog.info(GardenLog.Category.TIME, "%s has begun (Year %d, month %d)", clock.getSeason().label, clock.getYear(), clock.getMonth());
            lastSeason = season;
        }
    }

    private void dailyReport(int day) {
        int alive = 0;
        int wilting = 0;
        int dead = 0;
        int empty = 0;
        double health = 0;
        for (Plot plot : plots) {
            switch (plot.getState()) {
                case DEAD: dead++; break;
                case EMPTY: empty++; break;
                case WILTING: wilting++; alive++; health += plot.getHealth(); break;
                default: alive++; health += plot.getHealth();
            }
        }
        DailyTally now = new DailyTally();
        GardenLog.divider(String.format("DAILY REPORT  day %d  (%s)", day + 1, clock.format()));
        GardenLog.info(GardenLog.Category.TIME,
                "Day %d report: %d alive (%d wilting, average health %.0f%%), %d dead, %d empty | "
                        + "today: %d harvested, %d died, %d pests sprayed, %d stomped, %.0f L water | weather %s, air %.1f°C",
                day + 1, alive, wilting, alive == 0 ? 0 : health / alive, dead, empty,
                now.harvested - dayStart.harvested, now.deaths - dayStart.deaths,
                now.sprayed - dayStart.sprayed, now.stomped - dayStart.stomped, now.litres - dayStart.litres,
                weather.getCurrent().label, getAirTemperature());
        dayStart.copy(now);
    }

    /** Running totals, so the daily report can say what happened today. */
    private final class DailyTally {
        int harvested = Garden.this.harvested;
        int deaths = Garden.this.deaths;
        int sprayed = pestControl.getKilled();
        int stomped = Garden.this.stomped;
        double litres = watering.getLitres();

        void copy(DailyTally other) {
            harvested = other.harvested;
            deaths = other.deaths;
            sprayed = other.sprayed;
            stomped = other.stomped;
            litres = other.litres;
        }
    }

    // ---- accessors ----

    public List<Plot> getPlots() { return plots; }
    public List<Pipe> getPipes() { return pipes; }
    public GameClock getClock() { return clock; }
    public WeatherSystem getWeather() { return weather; }
    public List<GardenModule> getModules() { return modules; }
    public WateringSystem getWatering() { return watering; }
    public HeatingSystem getHeating() { return heating; }
    public PestControlSystem getPestControl() { return pestControl; }
    public Critters getCritters() { return critters; }
    public void setCritters(Critters critters) { this.critters = critters; }
    public void setBaseLife(int life) { baseLife = life; }
    public void setBaseMultiplier(int multiplier) { baseMultiplier = multiplier; }
    public int getHarvested() { return harvested; }
    public int getDeaths() { return deaths; }
    public int getFelled() { return felled; }
    public int getStomped() { return stomped; }
}

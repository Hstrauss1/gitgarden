package garden;

import java.util.Collections;
import java.util.List;

/**
 * One tilled plot on the farm: the tree growing in it and its soil moisture.
 *
 * {@link #tick} runs the plant's biology: soil dries out (or is watered by rain and
 * by its sprinkler, which the {@link WateringSystem} switches), growth speed depends
 * on moisture, season and weather, and a finished tree is auto-harvested after a while.
 * Dry soil, cockroaches, wind, root rot and frost cost the tree health; at zero it dies,
 * and a dead tree has to be chopped down before the bed can be replanted.
 */
public class Plot {

    /** Grid size of the plot's canvas, in character cells. */
    public static final int COLS = 22;
    public static final int ROWS = 18;
    /** The trunk starts in the middle of the soil bed; the rows below are the bed's front edge. */
    public static final int START_X = COLS / 2;
    public static final int START_Y = ROWS - 3;

    // automation tuning (units per simulated second)
    private static final double DRAIN_RATE = 3.0;
    private static final double SPRINKLER_RATE = 30.0;
    public static final double WILT_BELOW = 15.0;
    private static final double GROWTH_RATE = 4.0;     // glyphs per second at full moisture
    private static final double HARVEST_AFTER = 10.0;  // seconds a grown tree waits before auto-harvest
    private static final double MANUAL_WATER = 35.0;
    private static final double WILT_DAMAGE = 5.0;     // health lost per second in dry soil
    private static final double RECOVERY = 2.0;        // health regained per second once watered
    private static final double POLLINATION_RATE = 2.0; // extra glyphs per second while a bee visits
    private static final double WATERLOGGED = 95.0;    // rain on soil this wet rots some roots
    public static final int CHOPS_TO_FELL = 3;

    /** EMPTY: a dead tree was chopped down and the bed is waiting to be replanted. */
    public enum State { GROWING, WILTING, MATURE, DEAD, EMPTY }

    private final String name;
    private Species species = Species.JUNIPER;
    private long seed;
    private List<Glyph> glyphs = Collections.emptyList();

    private double grown;          // how many glyphs are revealed (fractional)
    private double moisture;       // 0..100
    private boolean sprinklerOn;
    private double matureTime;
    private double health = 100;   // 0..100; drops while wilting
    private boolean dead;
    private boolean empty;
    private int chops;
    private double plantedDay;
    private String lastDamage = "unknown causes";
    private boolean wasWilting;

    public Plot(String name, double moisture) {
        this.name = name;
        this.moisture = moisture;
    }

    /** Generates a fresh tree for this plot. */
    public void plant(Species species, long seed, int baseLife, int baseMultiplier, double today) {
        this.species = species;
        this.plantedDay = today;
        this.seed = seed;
        int life = Math.max(6, baseLife + species.lifeModifier);
        int multiplier = Math.max(2, baseMultiplier + species.multiplierModifier);
        this.glyphs = new BonsaiGenerator(seed, life, multiplier, species.leaves).grow(START_X, START_Y);
        this.grown = 0;
        this.matureTime = 0;
        this.health = 100;
        this.dead = false;
        this.empty = false;
        this.chops = 0;
        this.wasWilting = false;
        this.sprinklerOn = false;
        GardenLog.info(GardenLog.Category.PLANT, "%s: planted a %s (%d growth steps)", name, species.displayName, glyphs.size());
    }

    /**
     * Advances the plant's biology.
     *
     * @param dt          simulated seconds since the last tick
     * @param weather     the current weather; each species copes with it differently
     * @param season      the season, which speeds up or slows down growth and drying
     * @param temperature air temperature at this plot in °C (heaters raise it)
     * @return true when the tree should be auto-harvested
     */
    public boolean tick(double dt, Weather weather, GameClock.Season season, double temperature) {
        // soil dries out (faster in a drought, and some species are thirstier than others)
        double drain = DRAIN_RATE * weather.drainMultiplier * season.drain;
        if (weather == Weather.DROUGHT) drain *= species.droughtFactor;
        moisture = Math.max(0, moisture - drain * dt);
        moisture = Math.min(100, moisture + weather.rainfall * dt);
        if (sprinklerOn) {
            moisture = Math.min(100, moisture + SPRINKLER_RATE * dt);
        }

        if (dead || empty) {
            return false;
        }

        // everything that can hurt the tree this tick; the biggest one gets the blame if it dies
        double damage = 0;
        double worst = 0;
        String cause = null;
        double drought = moisture < WILT_BELOW ? WILT_DAMAGE : 0;
        double wind = weather.windy ? species.windDamage : 0;
        double rot = weather == Weather.RAIN && moisture > WATERLOGGED ? species.rotDamage : 0;
        double frost = temperature < 0 ? species.frostDamage : 0;
        double[] amounts = {drought, wind, rot, frost};
        String[] causes = {"drought (dry soil)", "windstorm", "root rot (waterlogged soil)", "frost"};
        for (int i = 0; i < amounts.length; i++) {
            damage += amounts[i];
            if (amounts[i] > worst) {
                worst = amounts[i];
                cause = causes[i];
            }
        }
        if (damage > 0) {
            hurt(damage * dt, cause);
        } else {
            health = Math.min(100, health + RECOVERY * dt);
        }

        boolean wilting = moisture < WILT_BELOW && !dead;
        if (wilting != wasWilting) {
            wasWilting = wilting;
            if (wilting) {
                GardenLog.warn(GardenLog.Category.PLANT, "%s (%s): wilting, soil only %.0f%% wet", name, species.displayName, moisture);
            } else if (!dead) {
                GardenLog.info(GardenLog.Category.PLANT, "%s (%s): recovered from wilting, soil %.0f%% wet", name, species.displayName, moisture);
            }
        }
        if (dead || wilting) {
            return false;
        }

        if (getState() == State.MATURE) {
            matureTime += dt;
            return matureTime >= HARVEST_AFTER;
        }

        // 40% speed when barely watered, 100% when soaked; some species love the rain
        double factor = 0.4 + 0.6 * (moisture - WILT_BELOW) / (100 - WILT_BELOW);
        if (weather == Weather.RAIN) factor *= species.rainGrowth;
        factor *= season.growth;
        grown = Math.min(glyphs.size(), grown + GROWTH_RATE * factor * dt);
        return false;
    }

    /** Set by the {@link WateringSystem}. */
    public void setSprinkler(boolean on) {
        sprinklerOn = on;
    }

    /** A bee hovering over the tree: growing trees grow a little faster. */
    public void pollinate(double dt) {
        if (getState() == State.GROWING) {
            grown = Math.min(glyphs.size(), grown + POLLINATION_RATE * dt);
        }
    }

    /** A cockroach eating the tree. */
    public void chew(double damage) {
        hurt(damage, "cockroach");
    }

    private void hurt(double damage, String cause) {
        if (dead || empty) return;
        lastDamage = cause;
        health = Math.max(0, health - damage);
        if (health == 0) {
            dead = true;
            sprinklerOn = false;
            GardenLog.alert(GardenLog.Category.PLANT, "%s (%s): DIED from %s", name, species.displayName, cause);
        }
    }

    /**
     * One swing of the axe at a dead tree.
     *
     * @return true when this chop felled it, leaving an empty bed
     */
    public boolean chop() {
        if (!dead) return false;
        chops++;
        if (chops < CHOPS_TO_FELL) return false;
        dead = false;
        empty = true;
        glyphs = Collections.emptyList();
        grown = 0;
        GardenLog.info(GardenLog.Category.PLANT, "%s: dead %s chopped down, bed is empty", name, species.displayName);
        return true;
    }

    /** Manual watering, like using the watering can. */
    public void water() {
        moisture = Math.min(100, moisture + MANUAL_WATER);
    }

    public State getState() {
        if (dead) return State.DEAD;
        if (empty) return State.EMPTY;
        if (moisture < WILT_BELOW) return State.WILTING;
        if (grown >= glyphs.size()) return State.MATURE;
        return State.GROWING;
    }

    public String getStatusText() {
        switch (getState()) {
            case MATURE:
                return String.format("Ready to harvest (auto in %.0fs)", Math.max(0, HARVEST_AFTER - matureTime));
            case WILTING:
                return String.format("Wilting, needs water! Health %.0f%%", health);
            case DEAD:
                return String.format("Dead. Click to chop it down (%d/%d)", chops, CHOPS_TO_FELL);
            case EMPTY:
                return "Empty bed. Click to plant a new bonsai";
            default:
                return String.format("Growing %d%%", (int) (100 * getProgress()));
        }
    }

    public String getName() { return name; }
    public Species getSpecies() { return species; }
    public long getSeed() { return seed; }
    public List<Glyph> getGlyphs() { return glyphs; }
    public int getVisibleCount() { return (int) grown; }
    public double getMoisture() { return moisture; }
    public boolean isSprinklerOn() { return sprinklerOn; }
    public double getHealth() { return health; }
    public int getChops() { return chops; }

    /** A living tree stands here (not dead, not chopped down). */
    public boolean hasTree() { return !dead && !empty; }
    public double getPlantedDay() { return plantedDay; }
    public String getLastDamage() { return lastDamage; }

    public double getProgress() {
        return glyphs.isEmpty() ? 1 : grown / glyphs.size();
    }
}

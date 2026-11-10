package garden;

import java.util.Random;

/**
 * Rolls random weather events: stretches of clear weather broken up by
 * droughts, rain and windstorms, with the odds set by the season.
 * Events can also be forced from the menu. Times are simulated seconds.
 */
public class WeatherSystem {

    private static final double CLEAR_MIN = 35;    // seconds of clear weather between events
    private static final double CLEAR_MAX = 70;
    private static final double EVENT_MIN = 18;    // how long an event lasts
    private static final double EVENT_MAX = 30;

    private final Random rng = new Random();
    private Weather current = Weather.CLEAR;
    private double timeLeft = between(CLEAR_MIN, CLEAR_MAX);

    public void update(double dt, GameClock.Season season) {
        timeLeft -= dt;
        if (timeLeft > 0) return;
        if (current == Weather.CLEAR) {
            set(pickEvent(season));
        } else {
            set(Weather.CLEAR);
        }
    }

    /** Spring is rainy, summer brings droughts, autumn is windy. */
    private Weather pickEvent(GameClock.Season season) {
        int total = season.rainWeight + season.droughtWeight + season.windWeight;
        int roll = rng.nextInt(total);
        if (roll < season.rainWeight) return Weather.RAIN;
        if (roll < season.rainWeight + season.droughtWeight) return Weather.DROUGHT;
        return Weather.WINDSTORM;
    }

    /** Starts the given weather now (clear weather lasts until the next random event). */
    public void set(Weather weather) {
        current = weather;
        timeLeft = weather == Weather.CLEAR ? between(CLEAR_MIN, CLEAR_MAX) : between(EVENT_MIN, EVENT_MAX);
    }

    public Weather getCurrent() { return current; }
    public double getTimeLeft() { return Math.max(0, timeLeft); }

    private double between(double min, double max) {
        return min + rng.nextDouble() * (max - min);
    }
}

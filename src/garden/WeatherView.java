package garden;

import java.util.Random;

import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.RadialGradient;
import javafx.scene.paint.Stop;

/**
 * Draws the sky over the whole farm: weather (rain streaks, drought haze, wind gusts
 * with blowing leaves), a tint for the season, and darkness at night.
 */
public final class WeatherView {

    private static final Color RAIN_SKY = Color.web("#1d2a3a", 0.22);
    private static final Color RAIN_STREAK = Color.web("#cfe8ff", 0.55);
    private static final Color DROUGHT_HAZE = Color.web("#ffb347", 0.16);
    private static final Color WIND_SKY = Color.web("#6c7a80", 0.10);
    private static final Color WIND_STREAK = Color.web("#ffffff", 0.35);
    private static final Color[] BLOWING_LEAVES = {
        Color.web("#7fd36a"), Color.web("#ffb05a"), Color.web("#ff8cc6"), Color.web("#c9a24a"),
    };
    private static final Color WINTER = Color.web("#dbe9ff", 0.16);
    private static final Color AUTUMN = Color.web("#ff9a3c", 0.07);
    private static final Color NIGHT = Color.web("#0b1430");

    private static final int RAIN_DROPS = 220;
    private static final int WIND_STREAKS = 26;
    private static final int LEAVES = 30;

    private WeatherView() {
    }

    /**
     * @param showNight false at high speeds, where days would flash past
     */
    public static void render(Canvas canvas, Weather weather, GameClock clock, long nanos, boolean showNight) {
        GraphicsContext g = canvas.getGraphicsContext2D();
        double w = canvas.getWidth();
        double h = canvas.getHeight();
        g.clearRect(0, 0, w, h);
        double time = nanos / 1e9;

        switch (weather) {
            case RAIN:
                drawRain(g, w, h, time);
                break;
            case DROUGHT:
                drawDrought(g, w, h, time);
                break;
            case WINDSTORM:
                drawWind(g, w, h, time);
                break;
            default:
                break;
        }

        GameClock.Season season = clock.getSeason();
        if (season == GameClock.Season.WINTER || season == GameClock.Season.AUTUMN) {
            g.setFill(season == GameClock.Season.WINTER ? WINTER : AUTUMN);
            g.fillRect(0, 0, w, h);
        }

        if (showNight) {
            double dark = clock.getDarkness();
            if (dark > 0) {
                g.setFill(NIGHT);
                g.setGlobalAlpha(dark * 0.45);
                g.fillRect(0, 0, w, h);
                g.setGlobalAlpha(1);
            }
        }
    }

    /** Slanted streaks falling on a loop; each drop has a fixed lane and speed. */
    private static void drawRain(GraphicsContext g, double w, double h, double time) {
        g.setFill(RAIN_SKY);
        g.fillRect(0, 0, w, h);
        Random lanes = new Random(7);
        g.setStroke(RAIN_STREAK);
        g.setLineWidth(1);
        for (int i = 0; i < RAIN_DROPS; i++) {
            double x0 = lanes.nextDouble() * (w + 100);
            double speed = 420 + lanes.nextDouble() * 220;
            double offset = lanes.nextDouble() * (h + 40);
            double y = (offset + time * speed) % (h + 40) - 20;
            double x = x0 - y * 0.25;
            g.strokeLine(x, y, x - 3, y + 11);
        }
    }

    /** Hot orange haze and a pulsing sun glow in the corner. */
    private static void drawDrought(GraphicsContext g, double w, double h, double time) {
        g.setFill(DROUGHT_HAZE);
        g.fillRect(0, 0, w, h);
        double pulse = 1 + 0.06 * Math.sin(time * 1.5);
        RadialGradient sun = new RadialGradient(0, 0, w - 40, 30, 260 * pulse, false, CycleMethod.NO_CYCLE,
                new Stop(0, Color.web("#fff3b0", 0.55)),
                new Stop(0.25, Color.web("#ffd36b", 0.25)),
                new Stop(1, Color.web("#ffd36b", 0)));
        g.setFill(sun);
        g.fillRect(0, 0, w, h);
    }

    /** Gust lines and leaves torn off the trees, all racing left to right. */
    private static void drawWind(GraphicsContext g, double w, double h, double time) {
        g.setFill(WIND_SKY);
        g.fillRect(0, 0, w, h);
        Random lanes = new Random(11);
        g.setStroke(WIND_STREAK);
        g.setLineWidth(1);
        for (int i = 0; i < WIND_STREAKS; i++) {
            double y = lanes.nextDouble() * h;
            double len = 30 + lanes.nextDouble() * 60;
            double speed = 500 + lanes.nextDouble() * 300;
            double x = (lanes.nextDouble() * (w + 200) + time * speed) % (w + 200) - 100;
            double wave = Math.sin(time * 3 + i) * 3;
            g.strokeLine(x, y + wave, x + len, y + wave + 1);
        }
        for (int i = 0; i < LEAVES; i++) {
            double baseY = lanes.nextDouble() * h;
            double speed = 160 + lanes.nextDouble() * 160;
            double x = (lanes.nextDouble() * (w + 60) + time * speed) % (w + 60) - 30;
            double y = baseY + Math.sin(time * 4 + i * 1.7) * 10;
            g.setFill(BLOWING_LEAVES[i % BLOWING_LEAVES.length]);
            g.save();
            g.translate(x, y);
            g.rotate(time * 400 + i * 40);
            g.fillOval(-2, -1, 4, 2);
            g.restore();
        }
    }
}

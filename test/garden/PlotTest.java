package garden;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Damage and healing in {@link Plot}. Spring is used throughout (soil dries 2.7 points a
 * second in clear weather), and 20°C unless a test is about temperature.
 */
class PlotTest {

    private static final double EPS = 1e-9;
    private static final GameClock.Season SPRING = GameClock.Season.SPRING;
    private static final double MILD = 20;

    private static Plot planted(Species species, double moisture) {
        Plot plot = new Plot("Test plot", moisture);
        plot.plant(species, 42L, 16, 3, 0);
        return plot;
    }

    @Test
    void newTreeStartsAtFullHealth() {
        Plot plot = planted(Species.JUNIPER, 60);
        assertEquals(100, plot.getHealth(), EPS);
        assertTrue(plot.hasTree());
    }

    // ---- damage ----

    @Test
    void drySoilCostsFiveHealthPerSecond() {
        Plot plot = planted(Species.JUNIPER, 5);
        plot.tick(1.0, Weather.CLEAR, SPRING, MILD);
        assertEquals(95, plot.getHealth(), EPS);
        assertEquals("drought (dry soil)", plot.getLastDamage());
        assertEquals(Plot.State.WILTING, plot.getState());
    }

    @Test
    void windstormHurtsCherryButNotPine() {
        Plot cherry = planted(Species.CHERRY, 80);
        Plot pine = planted(Species.PINE, 80);
        cherry.tick(1.0, Weather.WINDSTORM, SPRING, MILD);
        pine.tick(1.0, Weather.WINDSTORM, SPRING, MILD);
        assertEquals(97.5, cherry.getHealth(), EPS);
        assertEquals(100, pine.getHealth(), EPS);
    }

    @Test
    void frostBelowZeroHurtsTenderSpeciesOnly() {
        Plot cherry = planted(Species.CHERRY, 80);
        Plot juniper = planted(Species.JUNIPER, 80);
        Plot pine = planted(Species.PINE, 80);
        for (Plot plot : new Plot[]{cherry, juniper, pine}) {
            plot.tick(1.0, Weather.CLEAR, SPRING, -1);
        }
        assertEquals(97.5, cherry.getHealth(), EPS);
        assertEquals(99.7, juniper.getHealth(), EPS);
        assertEquals(100, pine.getHealth(), EPS);
        assertEquals("frost", cherry.getLastDamage());
    }

    @Test
    void heatStressStartsAboveFortyDegrees() {
        Plot atLimit = planted(Species.MAPLE, 80);
        Plot above = planted(Species.MAPLE, 80);
        atLimit.tick(1.0, Weather.CLEAR, SPRING, Plot.HEAT_LIMIT);
        above.tick(1.0, Weather.CLEAR, SPRING, Plot.HEAT_LIMIT + 1);
        assertEquals(100, atLimit.getHealth(), EPS);
        assertEquals(98, above.getHealth(), EPS);
        assertEquals("heat stress", above.getLastDamage());
    }

    @Test
    void rainOnWaterloggedSoilRotsPineRootsOnly() {
        Plot pine = planted(Species.PINE, 99);
        Plot cherry = planted(Species.CHERRY, 99);
        Plot dryPine = planted(Species.PINE, 50);
        pine.tick(1.0, Weather.RAIN, SPRING, MILD);
        cherry.tick(1.0, Weather.RAIN, SPRING, MILD);
        dryPine.tick(1.0, Weather.RAIN, SPRING, MILD);
        assertEquals(98.5, pine.getHealth(), EPS);
        assertEquals(100, cherry.getHealth(), EPS);
        assertEquals(100, dryPine.getHealth(), EPS);
    }

    @Test
    void theBiggestDamageGetsTheBlame() {
        Plot maple = planted(Species.MAPLE, 100);
        maple.tick(1.0, Weather.WINDSTORM, SPRING, -1);   // wind 2.0 + frost 1.5
        assertEquals(96.5, maple.getHealth(), EPS);
        assertEquals("windstorm", maple.getLastDamage());
    }

    @Test
    void parasiteDamageIsTakenStraightAway() {
        Plot plot = planted(Species.CHERRY, 80);
        plot.parasiteDamage(12.5, "aphids infestation");
        assertEquals(87.5, plot.getHealth(), EPS);
        assertEquals("aphids infestation", plot.getLastDamage());
        plot.parasiteDamage(-5, "nothing");
        assertEquals(87.5, plot.getHealth(), EPS);
    }

    // ---- healing ----

    @Test
    void healsTwoPerSecondOnceNothingHurts() {
        Plot plot = planted(Species.PINE, 80);
        plot.parasiteDamage(30, "pine beetles");
        plot.tick(1.0, Weather.CLEAR, SPRING, MILD);   // the parasites fed just before this tick: no healing
        assertEquals(70, plot.getHealth(), EPS);
        plot.tick(1.0, Weather.CLEAR, SPRING, MILD);
        assertEquals(72, plot.getHealth(), EPS);
    }

    @Test
    void healingStopsAtOneHundred() {
        Plot plot = planted(Species.PINE, 80);
        plot.parasiteDamage(1, "aphids");
        for (int i = 0; i < 5; i++) {
            plot.tick(1.0, Weather.CLEAR, SPRING, MILD);
        }
        assertEquals(100, plot.getHealth(), EPS);
    }

    @Test
    void noHealingWhileStillBeingHurt() {
        Plot cherry = planted(Species.CHERRY, 80);
        cherry.parasiteDamage(20, "aphids");
        cherry.tick(1.0, Weather.CLEAR, SPRING, MILD);
        cherry.tick(1.0, Weather.WINDSTORM, SPRING, MILD);
        assertEquals(77.5, cherry.getHealth(), EPS);
    }

    @Test
    void defaultHealingIsTwentyPercentADay() {
        assertEquals(20, Plot.RECOVERY * GameClock.SECONDS_PER_DAY, EPS);
        assertEquals(Plot.RECOVERY, planted(Species.PINE, 50).getRecoveryRate(), EPS);
    }

    @Test
    void recoveryRateCanBeSlowedForApiRuns() {
        Plot plot = planted(Species.PINE, 80);
        plot.setRecoveryRate(0.5);
        plot.parasiteDamage(30, "aphids");
        plot.tick(1.0, Weather.CLEAR, SPRING, MILD);
        plot.tick(1.0, Weather.CLEAR, SPRING, MILD);
        assertEquals(70.5, plot.getHealth(), EPS);

        plot.setRecoveryRate(-3);
        assertEquals(0, plot.getRecoveryRate(), EPS);
    }

    // ---- death ----

    @Test
    void treeDiesAtZeroAndRemembersWhy() {
        Plot plot = planted(Species.PINE, 80);
        plot.parasiteDamage(150, "pine beetles infestation");
        assertEquals(0, plot.getHealth(), EPS);
        assertEquals(Plot.State.DEAD, plot.getState());
        assertFalse(plot.hasTree());
        assertEquals("pine beetles infestation", plot.getLastDamage());
    }

    @Test
    void deadTreeNeitherHealsNorTakesMoreDamage() {
        Plot plot = planted(Species.MAPLE, 80);
        plot.parasiteDamage(100, "aphids");
        for (int i = 0; i < 10; i++) {
            plot.tick(1.0, Weather.CLEAR, SPRING, MILD);
        }
        plot.parasiteDamage(10, "scale insects");
        assertEquals(0, plot.getHealth(), EPS);
        assertEquals("aphids", plot.getLastDamage());
        assertEquals(Plot.State.DEAD, plot.getState());
    }

    // ---- water ----

    @Test
    void soakAddsMoistureUpToOneHundred() {
        Plot plot = planted(Species.JUNIPER, 50);
        plot.soak(30);
        assertEquals(80, plot.getMoisture(), EPS);
        plot.soak(30);
        assertEquals(100, plot.getMoisture(), EPS);
        plot.soak(-20);
        assertEquals(100, plot.getMoisture(), EPS);
    }

    @Test
    void explicitRainfallReplacesTheWeathersRain() {
        Plot plot = planted(Species.CHERRY, 50);
        plot.tick(1.0, Weather.RAIN, SPRING, MILD, 0);
        assertEquals(50, plot.getMoisture(), EPS);   // rain weather: no drying, and no extra rain given
        plot.tick(1.0, Weather.RAIN, SPRING, MILD);
        assertEquals(62, plot.getMoisture(), EPS);   // the weather's own 12 per second
    }
}

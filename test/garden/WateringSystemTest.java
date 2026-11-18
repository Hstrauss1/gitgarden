package garden;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The watering module's switching: on below 30% soil moisture, off at 90%, nothing through
 * a closed pipe or for a dead tree, and everything off when the module is switched off.
 * The moisture sensors are noisy by up to 1 point, so test values stay well clear of the
 * thresholds.
 */
class WateringSystemTest {

    private static final double STEP = 0.25;
    private Garden garden;
    private WateringSystem watering;
    private Pipe pipe;
    private Plot plot;

    @BeforeEach
    void setUp() {
        garden = new Garden();
        watering = garden.getWatering();
        pipe = new Pipe("Row 1");
        plot = new Plot("Plot 1", 20);
        plot.plant(Species.PINE, 7L, 16, 3, 0);
        pipe.add(plot);
        garden.addPipe(pipe);
    }

    private void runModule() {
        watering.update(STEP, garden);
    }

    @Test
    void turnsOnBelowThirtyPercent() {
        runModule();
        assertTrue(plot.isSprinklerOn());
        assertEquals(1, watering.getRunning());
    }

    @Test
    void staysOffInTheMiddleBand() {
        plot.soak(40);   // 60%
        runModule();
        assertFalse(plot.isSprinklerOn());
        assertEquals(0, watering.getRunning());
    }

    @Test
    void staysOnUntilNinetyPercentSoItDoesNotFlicker() {
        runModule();
        plot.soak(40);   // 60%: above the "on" mark but below the "off" mark
        runModule();
        assertTrue(plot.isSprinklerOn());
    }

    @Test
    void turnsOffAtNinetyPercent() {
        runModule();
        plot.soak(75);   // 95%
        runModule();
        assertFalse(plot.isSprinklerOn());
    }

    @Test
    void closedPipeKeepsTheSprinklerOff() {
        pipe.toggle();
        runModule();
        assertFalse(plot.isSprinklerOn());

        pipe.toggle();
        runModule();
        assertTrue(plot.isSprinklerOn());

        pipe.toggle();   // closing it while running cuts the water at once
        runModule();
        assertFalse(plot.isSprinklerOn());
    }

    @Test
    void deadTreesAreNotWatered() {
        plot.parasiteDamage(200, "aphids");
        runModule();
        assertFalse(plot.isSprinklerOn());
    }

    @Test
    void switchingTheModuleOffStopsEverySprinkler() {
        runModule();
        assertTrue(plot.isSprinklerOn());

        watering.setEnabled(false, garden);
        assertFalse(plot.isSprinklerOn());
        runModule();
        assertFalse(plot.isSprinklerOn());   // dry soil, but the module is off

        watering.setEnabled(true, garden);
        runModule();
        assertTrue(plot.isSprinklerOn());
    }

    @Test
    void countsTheWaterItUses() {
        runModule();
        runModule();
        assertEquals(2 * 2 * STEP, watering.getLitres(), 1e-9);   // 2 L per second while running
    }

    @Test
    void aRunningSprinklerWetsTheSoil() {
        runModule();
        plot.tick(1.0, Weather.CLEAR, GameClock.Season.SPRING, 20);
        assertEquals(20 - 2.7 + 30, plot.getMoisture(), 1e-9);   // drying, plus 30 a second from the sprinkler
    }
}

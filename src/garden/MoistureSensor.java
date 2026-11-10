package garden;

import java.util.Random;

/** A soil probe in one plot. Real probes are a little noisy, so readings wobble by about 1%. */
public class MoistureSensor implements Sensor {

    private static final double NOISE = 1.0;

    private final Plot plot;
    private final Random noise = new Random();

    public MoistureSensor(Plot plot) {
        this.plot = plot;
    }

    @Override
    public String getName() {
        return plot.getName() + " moisture sensor";
    }

    @Override
    public double read() {
        double reading = plot.getMoisture() + (noise.nextDouble() * 2 - 1) * NOISE;
        return Math.max(0, Math.min(100, reading));
    }

    @Override
    public String getUnit() {
        return "%";
    }
}

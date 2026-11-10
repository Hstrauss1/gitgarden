package garden;

import java.util.Random;

/** The garden thermometer: reads the air temperature with a little noise. */
public class TemperatureSensor implements Sensor {

    private static final double NOISE = 0.2;

    private final Garden garden;
    private final Random noise = new Random();

    public TemperatureSensor(Garden garden) {
        this.garden = garden;
    }

    @Override
    public String getName() {
        return "Air temperature sensor";
    }

    @Override
    public double read() {
        return garden.getAirTemperature() + (noise.nextDouble() * 2 - 1) * NOISE;
    }

    @Override
    public String getUnit() {
        return "°C";
    }
}

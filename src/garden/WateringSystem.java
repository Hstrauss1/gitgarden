package garden;

import java.util.HashMap;
import java.util.Map;

/**
 * Reads a moisture sensor in every plot and switches its overhead sprinkler:
 * on when the soil drops below 30%, off once it reaches 90% (the gap stops it
 * flicking on and off). A row whose spigot is closed gets no water at all.
 */
public class WateringSystem extends GardenModule {

    public static final double ON_BELOW = 30;
    public static final double OFF_ABOVE = 90;
    /** Water one sprinkler uses per simulated second, in litres. */
    private static final double LITRES_PER_SECOND = 2;

    private final Map<Plot, MoistureSensor> sensors = new HashMap<Plot, MoistureSensor>();
    private double litres;
    private int running;
    private double averageSoil;

    public WateringSystem() {
        super("Watering system", "Moisture sensors in every bed switch the overhead sprinklers on below "
                + (int) ON_BELOW + "% and off at " + (int) OFF_ABOVE + "%.", GardenLog.Category.WATERING);
    }

    @Override
    protected void run(double dt, Garden garden) {
        running = 0;
        double soilTotal = 0;
        int plots = 0;
        for (Pipe pipe : garden.getPipes()) {
            for (Plot plot : pipe.getPlots()) {
                double reading = sensorFor(plot).read();
                soilTotal += reading;
                plots++;

                if (!pipe.isOpen() || !plot.hasTree()) {
                    // no water through a closed valve, and no point watering an empty or dead bed
                    plot.setSprinkler(false);
                    continue;
                }
                if (!plot.isSprinklerOn() && reading < ON_BELOW) {
                    plot.setSprinkler(true);
                    GardenLog.info(getCategory(), "%s: sensor reads %.0f%% (below %.0f%%), sprinkler ON",
                            plot.getName(), reading, ON_BELOW);
                } else if (plot.isSprinklerOn() && reading >= OFF_ABOVE) {
                    plot.setSprinkler(false);
                    GardenLog.info(getCategory(), "%s: sensor reads %.0f%%, sprinkler OFF", plot.getName(), reading);
                }
                if (plot.isSprinklerOn()) {
                    running++;
                    litres += LITRES_PER_SECOND * dt;
                }
            }
        }
        averageSoil = plots == 0 ? 0 : soilTotal / plots;
    }

    @Override
    protected void shutDown(Garden garden) {
        for (Plot plot : garden.getPlots()) {
            plot.setSprinkler(false);
        }
        running = 0;
        GardenLog.warn(getCategory(), "All sprinklers stopped; plots will dry out unless watered by hand or by rain");
    }

    private MoistureSensor sensorFor(Plot plot) {
        MoistureSensor sensor = sensors.get(plot);
        if (sensor == null) {
            sensor = new MoistureSensor(plot);
            sensors.put(plot, sensor);
        }
        return sensor;
    }

    @Override
    public String getStatus() {
        if (!isEnabled()) return "OFF - nothing is watering the beds";
        return String.format("%d sprinklers running · average soil %.0f%% · %.0f L used", running, averageSoil, litres);
    }

    public double getLitres() { return litres; }
    public int getRunning() { return running; }
    public double getAverageSoil() { return averageSoil; }
}

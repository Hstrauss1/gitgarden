package garden.api;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import garden.Garden;
import garden.GardenLog;
import garden.GardenModule;
import garden.MoistureSensor;
import garden.Plot;

/**
 * Drain valves under every bed, for API runs. When a bed's moisture sensor reads
 * waterlogged (above 93%), its drain opens and lets water out until the soil is back
 * below 85%. The drains have a limited capacity, so the heaviest rain (more than about
 * 40 mm a day on a pine) can still waterlog the soil and rot pine roots.
 */
public final class Drainage extends GardenModule {

    public static final double OPEN_ABOVE = 93;
    public static final double CLOSE_BELOW = 85;
    /** Moisture points a drain can remove per simulated second. */
    public static final double CAPACITY = 8;

    private final ApiGarden owner;
    private final Map<Plot, MoistureSensor> sensors = new HashMap<Plot, MoistureSensor>();
    private final Set<Plot> open = new HashSet<Plot>();
    private int openings;

    Drainage(ApiGarden owner) {
        super("Drainage", "Opens a bed's drain when its soil is waterlogged (above 93%) and closes it below 85%.",
                GardenLog.Category.WATERING);
        this.owner = owner;
    }

    @Override
    protected void run(double dt, Garden garden) {
        Map<String, Integer> openedNow = new LinkedHashMap<String, Integer>();
        for (Plot plot : garden.getPlots()) {
            MoistureSensor sensor = sensors.get(plot);
            if (sensor == null) {
                sensor = new MoistureSensor(plot);
                sensors.put(plot, sensor);
            }
            double reading = sensor.read();
            if (!plot.hasTree()) {
                open.remove(plot);
            } else if (!open.contains(plot) && reading > OPEN_ABOVE) {
                open.add(plot);
                openings++;
                String kind = plot.getSpecies().displayName;
                Integer n = openedNow.get(kind);
                openedNow.put(kind, n == null ? 1 : n + 1);
            } else if (open.contains(plot) && reading < CLOSE_BELOW) {
                open.remove(plot);
            }
            if (open.contains(plot)) {
                plot.drainWater(CAPACITY * dt);
            }
        }
        if (!openedNow.isEmpty()) {
            List<String> kinds = new ArrayList<String>();
            int total = 0;
            for (Map.Entry<String, Integer> entry : openedNow.entrySet()) {
                kinds.add(entry.getValue() + " " + entry.getKey());
                total += entry.getValue();
            }
            owner.log().info(owner.currentDay(), "DRAINAGE", total + " beds", "Soil waterlogged (over "
                    + (int) OPEN_ABOVE + "%): drains opened under " + String.join(", ", kinds));
        }
    }

    @Override
    protected void shutDown(Garden garden) {
        open.clear();
    }

    @Override
    public String getStatus() {
        if (!isEnabled()) return "OFF - waterlogged beds stay waterlogged";
        return String.format("%d drains open now · opened %d times", open.size(), openings);
    }

    public boolean isOpen(Plot plot) { return open.contains(plot); }
    public int getOpenings() { return openings; }
}

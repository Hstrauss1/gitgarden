package garden;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Watches the pest sensors. When a cockroach has been feeding on a tree for a couple
 * of seconds (so a passing bug doesn't trigger it), a nozzle sprays a dose of pesticide
 * on it. The tank refills slowly, so a big infestation can outrun it; then cockroaches
 * have to be stomped by hand.
 */
public class PestControlSystem extends GardenModule {

    /** Simulated seconds a roach must be feeding before the system sprays it. */
    private static final double CONFIRM_SECONDS = 2;
    private static final double TANK_SIZE = 100;
    private static final double DOSE = 15;
    private static final double REFILL_PER_SECOND = 0.6;

    private final PestSensor sensor;
    private final Map<Cockroach, Double> watching = new HashMap<Cockroach, Double>();
    private double tank = TANK_SIZE;
    private int killed;
    private boolean warnedEmpty;

    public PestControlSystem(Garden garden) {
        super("Pest control", "Pest sensors spot cockroaches feeding on a tree and spray them with pesticide.",
                GardenLog.Category.PEST);
        sensor = new PestSensor(garden);
    }

    @Override
    protected void run(double dt, Garden garden) {
        tank = Math.min(TANK_SIZE, tank + REFILL_PER_SECOND * dt);
        if (tank >= DOSE) warnedEmpty = false;

        List<Cockroach> feeding = sensor.detect();
        // forget roaches that left, died or were stomped
        for (Iterator<Cockroach> it = watching.keySet().iterator(); it.hasNext(); ) {
            if (!feeding.contains(it.next())) it.remove();
        }

        for (Cockroach roach : feeding) {
            Double seen = watching.get(roach);
            if (seen == null) {
                watching.put(roach, 0.0);
                GardenLog.warn(getCategory(), "Pest sensor: cockroach feeding on %s (health %.0f%%)",
                        roach.getMealName(), roach.getMealHealth());
                continue;
            }
            double time = seen + dt;
            watching.put(roach, time);
            if (time < CONFIRM_SECONDS) continue;

            if (tank >= DOSE) {
                tank -= DOSE;
                killed++;
                watching.remove(roach);
                garden.getCritters().exterminate(roach);
                GardenLog.info(getCategory(), "Pesticide sprayed on %s: cockroach killed (tank %.0f%%)",
                        roach.getMealName(), tank / TANK_SIZE * 100);
            } else if (!warnedEmpty) {
                warnedEmpty = true;
                GardenLog.alert(getCategory(), "Pesticide tank empty (%.0f%%): stomp the cockroach on %s by hand",
                        tank / TANK_SIZE * 100, roach.getMealName());
            }
        }
    }

    @Override
    protected void shutDown(Garden garden) {
        watching.clear();
        GardenLog.warn(getCategory(), "Pest control off: cockroaches must be stomped by hand");
    }

    @Override
    public String getStatus() {
        int pests = (int) sensor.read();
        if (!isEnabled()) return "OFF - " + pests + " cockroaches feeding right now";
        return String.format("Tank %.0f%% · %d feeding now · %d killed", tank / TANK_SIZE * 100, pests, killed);
    }

    public int getKilled() { return killed; }
    public double getTankPercent() { return tank / TANK_SIZE * 100; }
}

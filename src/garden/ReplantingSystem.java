package garden;

import java.util.HashMap;
import java.util.Map;

/**
 * The groundskeeper: a tree that has been dead for a whole day is chopped down and
 * the bed replanted with a new bonsai, and empty beds are planted after half a day.
 * You can still chop and replant by hand sooner; switch this off to do it all yourself.
 */
public class ReplantingSystem extends GardenModule {

    private static final double DEAD_WAIT_DAYS = 1;
    private static final double EMPTY_WAIT_DAYS = 0.5;

    private final Map<Plot, Double> waitingSince = new HashMap<Plot, Double>();
    private int replaced;

    public ReplantingSystem() {
        super("Auto-replanting", "Chops down trees that have been dead for a day and replants empty beds.",
                GardenLog.Category.REPLANT);
    }

    @Override
    protected void run(double dt, Garden garden) {
        double today = garden.getClock().getDays();
        for (Plot plot : garden.getPlots()) {
            Plot.State state = plot.getState();
            if (state != Plot.State.DEAD && state != Plot.State.EMPTY) {
                waitingSince.remove(plot);
                continue;
            }
            Double since = waitingSince.get(plot);
            if (since == null) {
                waitingSince.put(plot, today);
                continue;
            }
            double wait = state == Plot.State.DEAD ? DEAD_WAIT_DAYS : EMPTY_WAIT_DAYS;
            if (today - since < wait) continue;

            String old = plot.getSpecies().displayName;
            String cause = plot.getLastDamage();
            if (state == Plot.State.DEAD) {
                while (!plot.chop()) {
                    // keep swinging until it falls
                }
            }
            garden.replant(plot);
            replaced++;
            waitingSince.remove(plot);
            GardenLog.info(getCategory(), "%s: groundskeeper %s and planted a %s",
                    plot.getName(), state == Plot.State.DEAD ? "removed the dead " + old + " (died of " + cause + ")" : "found the bed empty",
                    plot.getSpecies().displayName);
        }
    }

    @Override
    protected void shutDown(Garden garden) {
        waitingSince.clear();
    }

    @Override
    public String getStatus() {
        if (!isEnabled()) return "OFF - dead trees stay until you chop them";
        return String.format("%d trees replaced · %d beds waiting", replaced, waitingSince.size());
    }
}

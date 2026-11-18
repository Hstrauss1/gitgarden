package garden.api;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import garden.Garden;
import garden.GardenLog;
import garden.GardenModule;
import garden.Plot;

/**
 * Pest control for API runs. Leaf sensors notice an infestation after a short while,
 * then a nozzle sprays the plant at regular intervals; each spray kills part of the
 * parasites until they are gone. Spraying never restores health: the plant only heals
 * on its own, at its normal rate, once nothing is hurting it.
 */
public final class ParasiteControl extends GardenModule {

    /** Simulated seconds before the sensors notice (one day is 10 s, so about 2.4 hours). */
    public static final double DETECT_SECONDS = 1.0;
    public static final double SPRAY_EVERY = 1.0;
    /** Share of the parasites each spray kills. */
    public static final double SPRAY_KILLS = 0.4;
    /** An infestation weaker than this is gone. */
    public static final double CLEARED_BELOW = 0.15;

    /** One plant's parasites. Severity 1 is a fresh infestation; it can build up to 2. */
    static final class Infestation {
        final String parasite;
        double severity = 1;
        double age;
        boolean detected;
        double sinceSpray;
        int sprays;

        Infestation(String parasite) {
            this.parasite = parasite;
        }
    }

    private final Map<Plot, Infestation> infestations;
    private final ApiGarden owner;
    private int cleared;
    private int sprays;

    ParasiteControl(Map<Plot, Infestation> infestations, ApiGarden owner) {
        super("Pest control", "Leaf sensors spot parasites and spray the plant until they are gone; spraying does not heal it.",
                GardenLog.Category.PEST);
        this.infestations = infestations;
        this.owner = owner;
    }

    @Override
    protected void run(double dt, Garden garden) {
        // one log line per parasite per step, not one per plant
        Map<String, List<Plot>> found = new LinkedHashMap<String, List<Plot>>();
        Map<String, List<Plot>> gone = new LinkedHashMap<String, List<Plot>>();
        Map<String, Integer> spraysTaken = new LinkedHashMap<String, Integer>();
        for (Iterator<Map.Entry<Plot, Infestation>> it = infestations.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Plot, Infestation> entry = it.next();
            Plot plot = entry.getKey();
            Infestation pest = entry.getValue();
            if (!plot.hasTree()) {
                it.remove();
                continue;
            }
            pest.age += dt;
            if (!pest.detected) {
                if (pest.age < DETECT_SECONDS) continue;
                pest.detected = true;
                group(found, pest.parasite).add(plot);
                pest.sinceSpray = SPRAY_EVERY;   // first spray right away
            }
            pest.sinceSpray += dt;
            if (pest.sinceSpray < SPRAY_EVERY) continue;
            pest.sinceSpray = 0;
            pest.severity *= 1 - SPRAY_KILLS;
            pest.sprays++;
            sprays++;
            if (pest.severity < CLEARED_BELOW) {
                it.remove();
                cleared++;
                group(gone, pest.parasite).add(plot);
                Integer before = spraysTaken.get(pest.parasite);
                spraysTaken.put(pest.parasite, Math.max(before == null ? 0 : before, pest.sprays));
            }
        }
        for (Map.Entry<String, List<Plot>> entry : found.entrySet()) {
            owner.log().warn(owner.currentDay(), "PEST_CONTROL", entry.getKey(), String.format(
                    "Sensors found %s on %s; spraying", entry.getKey(), summarize(entry.getValue())));
        }
        for (Map.Entry<String, List<Plot>> entry : gone.entrySet()) {
            owner.log().info(owner.currentDay(), "PEST_CONTROL", entry.getKey(), String.format(
                    "%s cleared from %s after up to %d sprays; they heal on their own from here",
                    entry.getKey(), summarize(entry.getValue()), spraysTaken.get(entry.getKey())));
        }
    }

    private static List<Plot> group(Map<String, List<Plot>> groups, String parasite) {
        List<Plot> plots = groups.get(parasite);
        if (plots == null) {
            plots = new ArrayList<Plot>();
            groups.put(parasite, plots);
        }
        return plots;
    }

    /** "3 plants (2 Pine, 1 Juniper), health 91-97%", or the plant's name if there is just one. */
    private String summarize(List<Plot> plots) {
        double low = 100;
        double high = 0;
        Map<String, Integer> bySpecies = new LinkedHashMap<String, Integer>();
        for (Plot plot : plots) {
            low = Math.min(low, plot.getHealth());
            high = Math.max(high, plot.getHealth());
            Integer n = bySpecies.get(plot.getSpecies().displayName);
            bySpecies.put(plot.getSpecies().displayName, n == null ? 1 : n + 1);
        }
        if (plots.size() == 1) {
            return String.format("%s (health %.0f%%)", owner.describe(plots.get(0)), low);
        }
        StringBuilder kinds = new StringBuilder();
        for (Map.Entry<String, Integer> entry : bySpecies.entrySet()) {
            if (kinds.length() > 0) kinds.append(", ");
            kinds.append(entry.getValue()).append(" ").append(entry.getKey());
        }
        String health = Math.round(low) == Math.round(high) ? String.format("%.0f%%", low) : String.format("%.0f-%.0f%%", low, high);
        return String.format("%d plants (%s), health %s", plots.size(), kinds, health);
    }

    @Override
    protected void shutDown(Garden garden) {
        // undetected or half-sprayed infestations simply carry on
    }

    @Override
    public String getStatus() {
        if (!isEnabled()) return "OFF - " + infestations.size() + " plants infested and untreated";
        return String.format("%d plants infested now · %d sprays · %d infestations cleared", infestations.size(), sprays, cleared);
    }

    public int getCleared() { return cleared; }
    public int getSprays() { return sprays; }
}

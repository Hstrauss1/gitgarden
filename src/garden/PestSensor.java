package garden;

import java.util.ArrayList;
import java.util.List;

/**
 * Motion sensors along the beds: they notice cockroaches that are feeding on a tree.
 * {@link #read()} is how many are feeding right now; {@link #detect()} says which.
 */
public class PestSensor implements Sensor {

    private final Garden garden;

    public PestSensor(Garden garden) {
        this.garden = garden;
    }

    @Override
    public String getName() {
        return "Bed pest sensors";
    }

    @Override
    public double read() {
        return detect().size();
    }

    @Override
    public String getUnit() {
        return "pests";
    }

    /** Pests currently eating a tree. */
    public List<Pest> detect() {
        List<Pest> feeding = new ArrayList<Pest>();
        Wildlife wildlife = garden.getWildlife();
        if (wildlife == null) return feeding;
        for (Pest pest : wildlife.getPests()) {
            if (pest.isEating()) feeding.add(pest);
        }
        return feeding;
    }
}

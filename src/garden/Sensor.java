package garden;

/** Something that measures the garden. Modules decide what to do based on sensor readings. */
public interface Sensor {

    String getName();

    /** The current reading, in {@link #getUnit()}. */
    double read();

    String getUnit();
}

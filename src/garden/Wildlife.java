package garden;

import java.util.List;

/**
 * The creatures on the field, as the garden's model sees them. {@link PestSensor} and
 * {@link PestControlSystem} work through this interface only, so the model compiles and
 * runs without JavaFX; the GUI's {@link Critters} implements it. A garden with no wildlife
 * (such as an API run) simply has none set.
 */
public interface Wildlife {

    /** Every pest on the field right now, feeding or not. */
    List<? extends Pest> getPests();

    /** Pest control's spray: the pest dies. Does nothing if it has already gone. */
    void exterminate(Pest pest);
}

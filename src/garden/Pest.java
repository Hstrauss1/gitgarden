package garden;

/**
 * A creature that eats trees, as the garden's model sees it: whether it is feeding, and on
 * which tree. The model needs nothing more, so it has no JavaFX in it; {@link Cockroach}
 * implements it and does its own drawing.
 */
public interface Pest {

    /** True while it is feeding on a living tree. */
    boolean isEating();

    /** The tree it is eating, as "Plot 14 (Pine)", or "nothing". */
    String getMealName();

    /** The health of the tree it is eating (0 when it isn't eating). */
    double getMealHealth();
}

package garden;

/** The kinds of weather. How each species copes with them lives in {@link Species}. */
public enum Weather {
    //          label         drain  rainfall  windy
    CLEAR      ("Clear",      1.0,   0,        false),
    DROUGHT    ("Drought",    3.0,   0,        false),
    RAIN       ("Rain",       0.0,   12,       false),
    WINDSTORM  ("Windstorm",  1.5,   0,        true);

    public final String label;
    /** Multiplies how fast soil dries out. */
    public final double drainMultiplier;
    /** Moisture added to every plot per second. */
    public final double rainfall;
    public final boolean windy;

    Weather(String label, double drainMultiplier, double rainfall, boolean windy) {
        this.label = label;
        this.drainMultiplier = drainMultiplier;
        this.rainfall = rainfall;
        this.windy = windy;
    }
}

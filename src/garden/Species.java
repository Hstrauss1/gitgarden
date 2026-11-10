package garden;

/**
 * Each species tweaks the generator's inputs, its colours, and how it copes with weather.
 * Colours are web hex strings so the model has no JavaFX dependency.
 */
public enum Species {
    // Light trunks and bright leaves so they read on both dry and wet soil.
    //          name              leaves               life  mult  trunk (bright, dark)    leaf (bright, dark)
    JUNIPER("Juniper",          new String[]{"&"},       0,    0, "#f4dca8", "#dcb57a", "#8ff07f", "#4fcf5a",
            // drought  wind  rain growth  rot   frost  traits
            0.5,        1.0,  1.0,         0,    0.3,   "Drought-hardy, shrugs off frost"),
    CHERRY ("Cherry Blossom",   new String[]{"&", "*"},  -1,   1, "#f0d0b8", "#d4a68a", "#ffd0e8", "#ff8cc6",
            1.0,        2.5,  1.8,         0,    2.5,   "Loves rain, fragile in wind and frost"),
    MAPLE  ("Japanese Maple",   new String[]{"&", "%"},  1,    0, "#f4d29c", "#d8a86c", "#ffb05a", "#ff6a3d",
            1.7,        2.0,  1.2,         0,    1.5,   "Thirsty in droughts, dislikes frost"),
    PINE   ("Pine",             new String[]{"^", "&"},  2,   -1, "#ecd6a6", "#cfaa74", "#6ff0b0", "#38c98a",
            1.0,        0.0,  1.0,         1.5,  0.0,   "Wind- and frost-proof, roots rot in floods");

    public final String displayName;
    public final String[] leaves;
    /** Added to the garden-wide base life. */
    public final int lifeModifier;
    /** Added to the garden-wide base multiplier. */
    public final int multiplierModifier;
    public final String trunkBright;
    public final String trunkDark;
    public final String leafBright;
    public final String leafDark;

    /** Multiplies how fast the soil dries during a drought (below 1 = hardy). */
    public final double droughtFactor;
    /** Health lost per second during a windstorm. */
    public final double windDamage;
    /** Multiplies growth speed while it rains. */
    public final double rainGrowth;
    /** Health lost per second while it rains on waterlogged soil. */
    public final double rotDamage;
    /** Health lost per second while the air at the plot is below freezing. */
    public final double frostDamage;
    /** A short description for the tooltip. */
    public final String traits;

    Species(String displayName, String[] leaves, int lifeModifier, int multiplierModifier,
            String trunkBright, String trunkDark, String leafBright, String leafDark,
            double droughtFactor, double windDamage, double rainGrowth, double rotDamage, double frostDamage,
            String traits) {
        this.displayName = displayName;
        this.leaves = leaves;
        this.lifeModifier = lifeModifier;
        this.multiplierModifier = multiplierModifier;
        this.trunkBright = trunkBright;
        this.trunkDark = trunkDark;
        this.leafBright = leafBright;
        this.leafDark = leafDark;
        this.droughtFactor = droughtFactor;
        this.windDamage = windDamage;
        this.rainGrowth = rainGrowth;
        this.rotDamage = rotDamage;
        this.frostDamage = frostDamage;
        this.traits = traits;
    }
}

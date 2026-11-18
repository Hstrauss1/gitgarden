package garden;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Each species tweaks the generator's inputs, its colours, and how it copes with weather.
 * Colours are web hex strings so the model has no JavaFX dependency.
 */
public enum Species {
    // Light trunks and bright leaves so they read on both dry and wet soil.
    //          name              leaves               life  mult  trunk (bright, dark)    leaf (bright, dark)
    JUNIPER("Juniper",          new String[]{"&"},       0,    0, "#f4dca8", "#dcb57a", "#8ff07f", "#4fcf5a",
            // drought  wind  rain growth  rot   frost  heat   water  traits
            0.5,        1.0,  1.0,         0,    0.3,   0.5,   10,    "Drought-hardy, shrugs off frost",
            "spider mites", "scale insects"),
    CHERRY ("Cherry Blossom",   new String[]{"&", "*"},  -1,   1, "#f0d0b8", "#d4a68a", "#ffd0e8", "#ff8cc6",
            1.0,        2.5,  1.8,         0,    2.5,   1.5,   20,    "Loves rain, fragile in wind and frost",
            "aphids", "caterpillars", "spider mites"),
    MAPLE  ("Japanese Maple",   new String[]{"&", "%"},  1,    0, "#f4d29c", "#d8a86c", "#ffb05a", "#ff6a3d",
            1.7,        2.0,  1.2,         0,    1.5,   2.0,   25,    "Thirsty in droughts, dislikes frost and heat",
            "aphids", "scale insects"),
    PINE   ("Pine",             new String[]{"^", "&"},  2,   -1, "#ecd6a6", "#cfaa74", "#6ff0b0", "#38c98a",
            1.0,        0.0,  1.0,         1.5,  0.0,   0.8,   15,    "Wind- and frost-proof, roots rot in floods",
            "pine beetles", "aphids");

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
    /** Health lost per second while the bed is hotter than {@link Plot#HEAT_LIMIT}. */
    public final double heatDamage;
    /** Water the tree needs per day, in mm; rain of this much replaces a normal day's drying. */
    public final int waterRequirement;
    /** A short description for the tooltip. */
    public final String traits;
    /** Parasites this species is vulnerable to (lower case, plural). */
    private final List<String> parasites;

    Species(String displayName, String[] leaves, int lifeModifier, int multiplierModifier,
            String trunkBright, String trunkDark, String leafBright, String leafDark,
            double droughtFactor, double windDamage, double rainGrowth, double rotDamage, double frostDamage,
            double heatDamage, int waterRequirement, String traits, String... parasites) {
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
        this.heatDamage = heatDamage;
        this.waterRequirement = waterRequirement;
        this.traits = traits;
        this.parasites = Collections.unmodifiableList(new ArrayList<String>(Arrays.asList(parasites)));
    }

    /** The parasites this species is vulnerable to, as listed by the API's getPlants(). */
    public List<String> getParasites() {
        return parasites;
    }

    /** True if a parasite with this name (any case, singular or plural) attacks this species. */
    public boolean isVulnerableTo(String parasite) {
        String wanted = normalize(parasite);
        if (wanted.isEmpty()) return false;
        for (String known : parasites) {
            if (normalize(known).equals(wanted)) return true;
        }
        return false;
    }

    /**
     * Finds a species by name, ignoring case, spaces, hyphens and underscores: "Cherry Blossom",
     * "cherry_blossom", "CHERRY" and "cherry" all work.
     *
     * @return the species, or null if the name doesn't match any
     */
    public static Species fromName(String name) {
        String wanted = squash(name);
        if (wanted.isEmpty()) return null;
        for (Species species : values()) {
            if (squash(species.displayName).equals(wanted) || squash(species.name()).equals(wanted)) {
                return species;
            }
        }
        return null;
    }

    /** Lower case, words separated by single spaces, and a plural "s" dropped. */
    static String normalize(String name) {
        if (name == null) return "";
        String s = name.trim().toLowerCase(Locale.ROOT).replaceAll("[\\s_-]+", " ");
        return s.endsWith("s") ? s.substring(0, s.length() - 1) : s;
    }

    /** Lower case with every space, hyphen and underscore removed. */
    private static String squash(String name) {
        return name == null ? "" : name.toLowerCase(Locale.ROOT).replaceAll("[\\s_-]+", "");
    }
}

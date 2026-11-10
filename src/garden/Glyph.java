package garden;

/**
 * One "stroke" of a tree: a short string drawn at a grid cell.
 * The generator emits glyphs in the order they grow, so revealing
 * them one at a time animates the tree.
 */
public final class Glyph {
    public final int x;
    public final int y;
    public final String text;
    public final BranchType type;
    /** Picks the brighter or darker shade of the species colour. */
    public final boolean bright;

    public Glyph(int x, int y, String text, BranchType type, boolean bright) {
        this.x = x;
        this.y = y;
        this.text = text;
        this.type = type;
        this.bright = bright;
    }
}

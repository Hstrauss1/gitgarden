package garden;

import java.util.HashMap;
import java.util.Map;

import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;

/**
 * A small piece of pixel art written as rows of characters. Each character
 * is looked up in a palette; '.' is transparent. Every pixel is drawn as a
 * square, so the sprite keeps its chunky look at any size.
 */
public final class PixelSprite {

    private final String[] rows;
    private final Map<Character, Color> palette;
    private final int width;

    /**
     * @param palette pairs of a character and a colour, e.g. {@code "k", "#1a1a1a", "w", "#f2efe6"}
     */
    public PixelSprite(String[] rows, String... palette) {
        this.rows = rows.clone();
        this.palette = new HashMap<Character, Color>();
        for (int i = 0; i + 1 < palette.length; i += 2) {
            this.palette.put(palette[i].charAt(0), Color.web(palette[i + 1]));
        }
        int widest = 0;
        for (String row : rows) {
            widest = Math.max(widest, row.length());
        }
        this.width = widest;
    }

    public int getWidth() { return width; }
    public int getHeight() { return rows.length; }

    /**
     * Draws the sprite with its top-left corner at (left, top).
     *
     * @param pixel size of one sprite pixel on screen
     * @param flip  mirror left-to-right (for walking or riding the other way)
     */
    public void draw(GraphicsContext g, double left, double top, double pixel, boolean flip) {
        for (int r = 0; r < rows.length; r++) {
            String row = rows[r];
            for (int c = 0; c < row.length(); c++) {
                Color color = palette.get(row.charAt(c));
                if (color == null) continue;
                int col = flip ? width - 1 - c : c;
                g.setFill(color);
                // a hair of overlap hides seams between neighbouring pixels
                g.fillRect(left + col * pixel, top + r * pixel, pixel + 0.3, pixel + 0.3);
            }
        }
    }
}

package garden;

import java.util.Random;

import javafx.scene.SnapshotParameters;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.Image;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundImage;
import javafx.scene.layout.BackgroundPosition;
import javafx.scene.layout.BackgroundRepeat;
import javafx.scene.layout.BackgroundSize;
import javafx.scene.paint.Color;

/**
 * Draws a small seamless tile of grass tufts and repeats it as a background.
 * Every blade is drawn wrapped around the tile edges, so the tiles join without seams.
 */
public final class GrassTexture {

    private static final int SIZE = 96;
    private static final int TUFTS = 70;

    private GrassTexture() {
    }

    /** A repeating grass background built on the given base colour. Call on the FX thread. */
    public static Background background(Color base, long seed) {
        BackgroundImage image = new BackgroundImage(tile(base, seed),
                BackgroundRepeat.REPEAT, BackgroundRepeat.REPEAT,
                BackgroundPosition.DEFAULT, BackgroundSize.DEFAULT);
        return new Background(image);
    }

    private static Image tile(Color base, long seed) {
        Canvas canvas = new Canvas(SIZE, SIZE);
        GraphicsContext g = canvas.getGraphicsContext2D();
        g.setFill(base);
        g.fillRect(0, 0, SIZE, SIZE);

        Color dark = base.deriveColor(0, 1.05, 0.78, 1);
        Color light = base.deriveColor(-4, 0.9, 1.22, 1);
        Random rng = new Random(seed);
        g.setLineWidth(1);

        for (int i = 0; i < TUFTS; i++) {
            double x = Math.floor(rng.nextDouble() * SIZE) + 0.5;
            double y = Math.floor(rng.nextDouble() * SIZE) + 0.5;
            g.setStroke(rng.nextInt(3) == 0 ? light : dark);

            // a tuft is 2-3 blades fanning out from one root
            int blades = 2 + rng.nextInt(2);
            for (int b = 0; b < blades; b++) {
                double lean = (b - (blades - 1) / 2.0) * 1.5 + (rng.nextDouble() - 0.5);
                double height = 2.5 + rng.nextDouble() * 3;
                drawWrapped(g, x, y, x + lean, y - height);
            }
        }

        SnapshotParameters params = new SnapshotParameters();
        params.setFill(base);
        return canvas.snapshot(params, null);
    }

    /** Draws a line at every tile offset so blades crossing an edge continue on the other side. */
    private static void drawWrapped(GraphicsContext g, double x1, double y1, double x2, double y2) {
        for (int ox = -SIZE; ox <= SIZE; ox += SIZE) {
            for (int oy = -SIZE; oy <= SIZE; oy += SIZE) {
                g.strokeLine(x1 + ox, y1 + oy, x2 + ox, y2 + oy);
            }
        }
    }
}

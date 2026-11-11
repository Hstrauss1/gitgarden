package garden;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.Set;

import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;

/**
 * A rare event: a skeleton biker with a flaming skull roars across the field along
 * one row of beds, leaving a trail of fire and setting every tree he passes alight.
 * He can't be stopped. The fires can: by hand (click a burning tree to throw water
 * on it), by the sprinklers, or by rain.
 */
public class HellfireRider extends Bug {

    private static final double SPEED = 150;              // pixels per second
    private static final double PIXEL = 2;
    /** How long a tree burns (simulated seconds) if nothing puts it out. */
    public static final double FIRE_SECONDS = 30;
    private static final double TRAIL_LIFE = 0.9;

    private static final Color[] FLAME = {
        Color.web("#fff6c2"), Color.web("#ffd23f"), Color.web("#ff8c1a"), Color.web("#e8401c"), Color.web("#7a2a1a")
    };
    private static final Color TIRE = Color.web("#141414");
    private static final Color HUB = Color.web("#d8dee6");
    private static final Color SPOKE = Color.web("#8e98a3");
    private static final Color SHADOW = Color.web("#000000", 0.25);

    /** Facing right. The wheels are drawn separately so they can spin. */
    private static final PixelSprite BODY = new PixelSprite(new String[]{
        ".........wwww.........",
        "........wwwwww........",
        "........wwswws........",
        "........wwwwww........",
        ".........wkwkw........",
        "........bbbbbb........",
        ".......bBbbbbbbbbc....",
        ".......bbbbbb....c....",
        ".......bbbbkk....c....",
        "...gggg.rrrrkkg..c....",
        "..kggggggggggggGGc....",
        "....kk.gGGGGg....c....",
        ".......hhhhhh.........",
    }, "w", "#f2efe6", "s", "#1a0f0a", "k", "#1a1a1a", "b", "#3b2a22", "B", "#5a4034",
       "g", "#3c4148", "G", "#8e98a3", "c", "#d8dee6", "r", "#a3201a", "h", "#2a2a2a");

    /** Pixel offsets of a tire of radius 3 around its hub. */
    private static final int[][] TIRE_RING = {
        {-1, -3}, {0, -3}, {1, -3}, {2, -2}, {3, -1}, {3, 0}, {3, 1}, {2, 2},
        {1, 3}, {0, 3}, {-1, 3}, {-2, 2}, {-3, 1}, {-3, 0}, {-3, -1}, {-2, -2}
    };
    private static final int[][] SPOKES = {{0, -2}, {1, -1}, {2, 0}, {1, 1}, {0, 2}, {-1, 1}, {-2, 0}, {-1, -1}};
    private static final int REAR_HUB = 3;
    private static final int FRONT_HUB = 17;
    private static final int HUB_ROW = 11;
    private static final int GROUND_ROWS = 15;          // sprite rows down to the bottom of the tires

    private final List<PlotController> row;
    private final Set<PlotController> lit = new HashSet<PlotController>();
    private final List<double[]> trail = new ArrayList<double[]>();   // x, y, age
    private final double direction;                     // +1 riding right, -1 riding left
    private final double endX;
    private final String rowName;
    private double sinceTrail;
    private double wheelTurn;
    private int burned;

    /**
     * @param row   the beds along his path (one row of the field)
     * @param y     the ground line he rides along
     * @param width width of the field, so he knows where to leave
     */
    public HellfireRider(List<PlotController> row, double y, double width, boolean fromLeft, Random rng) {
        super(fromLeft ? -50 : width + 50, y, rng);
        this.row = row;
        this.direction = fromLeft ? 1 : -1;
        this.endX = fromLeft ? width + 60 : -60;
        this.rowName = row.get(0).getPlot().getName() + " to " + row.get(row.size() - 1).getPlot().getName();
        vx = SPEED * direction;
        GardenLog.alert(GardenLog.Category.INTRUDER, "A Hellfire Rider is tearing along the row from %s, setting it on fire!", rowName);
    }

    @Override
    public boolean update(double dt, Critters field) {
        x += vx * dt;
        wheelTurn += dt * 18;

        // light each bed as the bike passes its middle
        for (PlotController view : row) {
            double[] bed = field.bedOf(view);
            if (!lit.contains(view) && Math.abs(bed[0] - x) < 8) {
                lit.add(view);
                if (view.getPlot().ignite(FIRE_SECONDS)) burned++;
            }
        }

        // a trail of fire behind the back wheel
        sinceTrail += dt;
        while (sinceTrail > 0.025) {
            sinceTrail -= 0.025;
            double rear = x - direction * (BODY.getWidth() / 2.0 - REAR_HUB) * PIXEL;
            trail.add(new double[]{rear + rng.nextGaussian() * 2, y - 1, 0});
        }
        for (Iterator<double[]> it = trail.iterator(); it.hasNext(); ) {
            double[] flame = it.next();
            flame[2] += dt;
            if (flame[2] > TRAIL_LIFE) it.remove();
        }

        boolean gone = direction > 0 ? x > endX : x < endX;
        if (gone && trail.isEmpty()) {
            GardenLog.info(GardenLog.Category.INTRUDER, "The Hellfire Rider is gone. %d tree%s along %s caught fire",
                    burned, burned == 1 ? "" : "s", rowName);
            return false;
        }
        return true;
    }

    @Override
    public void draw(GraphicsContext g, double time) {
        int frame = (int) (time * 14);
        // the fire trail, rising and cooling to red
        for (double[] flame : trail) {
            double t = flame[2] / TRAIL_LIFE;
            int shade = Math.min(FLAME.length - 1, (int) (t * FLAME.length));
            g.setGlobalAlpha(1 - t * 0.7);
            g.setFill(FLAME[shade]);
            double size = PIXEL * (t < 0.5 ? 2 : 1);
            g.fillRect(snap(flame[0]), snap(flame[1] - t * 14), size, size);
        }
        g.setGlobalAlpha(1);

        boolean flip = direction < 0;
        double width = BODY.getWidth() * PIXEL;
        double left = x - width / 2;
        double top = y - GROUND_ROWS * PIXEL;

        g.setFill(SHADOW);
        g.fillOval(left + 2, y - 2, width - 4, 5);

        drawWheel(g, left, top, REAR_HUB, flip, frame);
        drawWheel(g, left, top, FRONT_HUB, flip, frame);
        BODY.draw(g, snap(left), snap(top), PIXEL, flip);
        drawSkullFire(g, left, top, flip, frame);
    }

    private void drawWheel(GraphicsContext g, double left, double top, int hubCol, boolean flip, int frame) {
        int col = flip ? BODY.getWidth() - 1 - hubCol : hubCol;
        double cx = snap(left + col * PIXEL);
        double cy = snap(top + HUB_ROW * PIXEL);
        g.setFill(TIRE);
        for (int[] p : TIRE_RING) {
            g.fillRect(cx + p[0] * PIXEL, cy + p[1] * PIXEL, PIXEL, PIXEL);
        }
        int spoke = (int) wheelTurn % SPOKES.length;
        if (flip) spoke = SPOKES.length - 1 - spoke;
        int[] s = SPOKES[spoke];
        int[] opposite = SPOKES[(spoke + 4) % SPOKES.length];
        g.setFill(SPOKE);
        g.fillRect(cx + s[0] * PIXEL, cy + s[1] * PIXEL, PIXEL, PIXEL);
        g.fillRect(cx + opposite[0] * PIXEL, cy + opposite[1] * PIXEL, PIXEL, PIXEL);
        g.setFill(HUB);
        g.fillRect(cx, cy, PIXEL, PIXEL);
        // flames licking off the back of each tire
        for (int i = 0; i < 4; i++) {
            long h = scramble(hubCol * 7 + i, frame);
            if ((h & 1) == 0) continue;
            double fx = cx - direction * (3 + (h >>> 1) % 3) * PIXEL;
            double fy = cy + (((h >>> 3) % 5) - 3) * PIXEL;
            g.setFill(FLAME[(int) ((h >>> 6) % 3)]);
            g.fillRect(fx, fy, PIXEL, PIXEL);
        }
    }

    /** Flames streaming up and back from the skull. */
    private void drawSkullFire(GraphicsContext g, double left, double top, boolean flip, int frame) {
        for (int i = 0; i < 26; i++) {
            long h = scramble(i, frame);
            int rise = 1 + (int) ((h >>> 2) % 6);              // pixels above the skull
            int col = 8 + (int) ((h >>> 6) % 6);               // across the top of the skull
            col -= (int) Math.round(rise * 0.6);               // streams back as he rides
            if (flip) col = BODY.getWidth() - 1 - col;
            int shade = Math.min(FLAME.length - 2, rise / 2 + (int) ((h >>> 9) & 1));
            g.setFill(FLAME[shade]);
            g.fillRect(snap(left + col * PIXEL), snap(top + (1 - rise) * PIXEL), PIXEL, PIXEL);
        }
    }

    private static double snap(double v) {
        return Math.round(v / PIXEL) * PIXEL;
    }

    private static long scramble(long a, long b) {
        long h = (a * 0x9E3779B97F4A7C15L) ^ (b * 0xC2B2AE3D27D4EB4FL);
        h ^= h >>> 31;
        h *= 0xBF58476D1CE4E5B9L;
        return (h ^ (h >>> 29)) & Long.MAX_VALUE;
    }
}

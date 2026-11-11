package garden;

import java.util.List;

import javafx.fxml.FXML;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Tooltip;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

/**
 * Controller for plot.fxml: draws one plot as a trapezoid soil bed seen at an angle,
 * with the tree standing up out of it. Soil colour shows moisture (light when dry,
 * dark when watered), like Stardew Valley.
 */
public class PlotController {

    private static final double CELL_W = 5;
    private static final double CELL_H = 7;
    private static final Font FONT = Font.font("Monospaced", FontWeight.BOLD, 8);

    private static final Color DRY_SOIL = Color.web("#c49565");
    private static final Color WET_SOIL = Color.web("#5a3b22");
    private static final Color BED_EDGE = Color.web("#3e2a17");
    private static final Color OUTLINE = Color.web("#1d140c", 0.85);
    private static final Color HOVER = Color.web("#fbe9c4");
        private static final Color SPARKLE = Color.web("#ffe066");
    private static final Color STUMP = Color.web("#7a5130");
    private static final Color STUMP_TOP = Color.web("#d9b07a");
    private static final Color CHIP = Color.web("#e8c48c");
    private static final Color SEED = Color.web("#9be36a");
    private static final double CHOP_SHAKE = 0.25;   // seconds the tree shakes after a chop
    private static final double WIND_SWAY = 3.5;     // pixels the treetop leans in a windstorm
    private static final Color HEALTH_HIGH = Color.web("#6fe05a");
    private static final Color HEALTH_LOW = Color.web("#ff4a3a");
    private static final Color DRY_LEAF = Color.web("#a8783c");
    private static final Color DEAD_WOOD = Color.web("#9a9088");
    private static final Color DEAD_WOOD_DARK = Color.web("#6e655e");
    private static final Color CHARCOAL = Color.web("#3a302b");
    private static final Color CHARCOAL_DARK = Color.web("#1f1916");
    /** Flame pixels from the base up: white-hot, yellow, orange, red. */
    private static final Color[] FLAME = {
        Color.web("#fff6c2"), Color.web("#ffd23f"), Color.web("#ff8c1a"), Color.web("#e8401c"), Color.web("#a8231a")
    };
    private static final Color SMOKE = Color.web("#5a5550");
    private static final Color EMBER_GLOW = Color.web("#ff6a00", 0.22);
    private static final double FIRE_PIXEL = 2;
    /** Below this health, leaves start falling off. */
    private static final double LEAF_DROP_BELOW = 60;

    // trapezoid bed geometry, in pixels
    static final double BED_TOP = (Plot.START_Y - 1) * CELL_H + 2;
    private static final double BED_DEPTH = 4;   // thickness of the bed's front face
    private static final double TOP_INSET = 16;  // the far edge is narrower, for perspective
    private static final double BOTTOM_INSET = 3;

    @FXML private StackPane root;
    @FXML private Canvas canvas;

    private final Tooltip tooltip = new Tooltip();
    // final picture of the tree: later glyphs overwrite earlier ones, like a terminal
    private final char[][] cells = new char[Plot.ROWS][Plot.COLS];
    private final Color[][] inks = new Color[Plot.ROWS][Plot.COLS];
    private Plot plot;
    private GardenController garden;
    private long lastChop = Long.MIN_VALUE / 2;

    @FXML
    private void initialize() {
        canvas.setWidth(Plot.COLS * CELL_W);
        canvas.setHeight(Plot.ROWS * CELL_H);
        Tooltip.install(root, tooltip);
    }

    public StackPane getRoot() { return root; }

    /** Jolts the tree, as if something just hit it. */
    public void shake() {
        lastChop = System.nanoTime();
    }
    public Plot getPlot() { return plot; }

    public void bind(Plot plot, GardenController garden) {
        this.plot = plot;
        this.garden = garden;
    }

    /**
     * Click a grown tree to harvest it, a dead one to chop at it, an empty bed to
     * plant a new tree, and anything else to water it.
     */
    @FXML
    private void onClicked(MouseEvent event) {
        switch (plot.getState()) {
            case MATURE:
                garden.harvestByHand(plot);
                break;
            case DEAD:
                lastChop = System.nanoTime();
                garden.chopByHand(plot);
                break;
            case EMPTY:
                garden.plantByHand(plot);
                break;
            default:
                garden.waterByHand(plot);
        }
    }

    public void render(long nanos, Weather weather) {
        Species species = plot.getSpecies();
        String about = plot.getState() == Plot.State.EMPTY ? "empty"
                : species.displayName + " (" + species.traits + ")";
        String age = plot.getState() == Plot.State.EMPTY ? "" :
                "\nAge: " + garden.getClock().describeAge(plot.getPlantedDay());
        tooltip.setText(String.format("%s - %s%nSoil moisture: %.0f%%%s%nHealth: %.0f%%%s%n%s",
                plot.getName(), about, plot.getMoisture(),
                plot.isSprinklerOn() ? " (sprinkler on)" : "", plot.getHealth(), age, plot.getStatusText()));

        GraphicsContext g = canvas.getGraphicsContext2D();
        g.clearRect(0, 0, canvas.getWidth(), canvas.getHeight());
        drawBed(g);

        double time = nanos / 1e9;
        if (plot.getState() == Plot.State.EMPTY) {
            drawStump(g, time);
            return;
        }

        // the whole tree jolts for a moment after each swing of the axe
        double sinceChop = (nanos - lastChop) / 1e9;
        double shake = sinceChop < CHOP_SHAKE ? Math.sin(sinceChop * 90) * 2 * (1 - sinceChop / CHOP_SHAKE) : 0;
        double sway = weather.windy && plot.getState() != Plot.State.DEAD ? WIND_SWAY : 0;

        layoutTree(species);
        drawTree(g, time, sway, shake);
        if (sinceChop < CHOP_SHAKE * 1.6) {
            drawWoodChips(g, sinceChop / (CHOP_SHAKE * 1.6));
        }
        if (plot.isBurning()) {
            drawFire(g, time);
        }
        if (plot.getState() == Plot.State.MATURE && !plot.isBurning()) {
            drawSparkle(g, nanos);
        }
    }

    /** What's left after chopping: a stump, plus a pulsing seed marker inviting a replant. */
    private void drawStump(GraphicsContext g, double time) {
        double cx = Plot.START_X * CELL_W + CELL_W / 2;
        double base = BED_TOP + 11;
        g.setFill(STUMP);
        g.fillRect(cx - 4, base - 4, 8, 5);
        g.setStroke(OUTLINE);
        g.setLineWidth(0.8);
        g.strokeRect(cx - 4, base - 4, 8, 5);
        g.setFill(STUMP_TOP);
        g.fillOval(cx - 4, base - 6, 8, 3.5);
        g.strokeOval(cx - 4, base - 6, 8, 3.5);
        g.strokeOval(cx - 2, base - 5.2, 4, 1.8);

        double pulse = 0.6 + 0.4 * Math.sin(time * 4);
        g.setGlobalAlpha(pulse);
        g.setFont(FONT);
        g.setFill(SEED);
        g.setStroke(OUTLINE);
        g.setLineWidth(2);
        g.strokeText("+", cx + 8, base - 6);
        g.fillText("+", cx + 8, base - 6);
        g.setGlobalAlpha(1);
    }

    /** Chips of wood flying away from the trunk after a chop; {@code t} runs 0 to 1. */
    private void drawWoodChips(GraphicsContext g, double t) {
        double cx = Plot.START_X * CELL_W + CELL_W / 2;
        double base = BED_TOP + 6;
        g.setFill(CHIP);
        g.setGlobalAlpha(1 - t);
        for (int i = 0; i < 6; i++) {
            double dir = (i % 2 == 0 ? 1 : -1) * (0.6 + i * 0.25);
            double x = cx + dir * t * 14;
            double y = base - (3 + i) * t * 4 + t * t * 18;
            g.fillRect(x, y, 1.8, 1.2);
        }
        g.setGlobalAlpha(1);
    }

    /** The soil bed: a trapezoid top face, a darker front face, and perspective furrows. */
    private void drawBed(GraphicsContext g) {
        double w = canvas.getWidth();
        double bottom = canvas.getHeight() - BED_DEPTH - 1;
        Color soil = DRY_SOIL.interpolate(WET_SOIL, plot.getMoisture() / 100.0);

        // front face, so the bed looks raised
        g.setFill(soil.deriveColor(0, 1, 0.6, 1));
        g.fillPolygon(
                new double[]{BOTTOM_INSET, w - BOTTOM_INSET, w - BOTTOM_INSET, BOTTOM_INSET},
                new double[]{bottom, bottom, bottom + BED_DEPTH, bottom + BED_DEPTH}, 4);

        // top face
        double[] xs = {TOP_INSET, w - TOP_INSET, w - BOTTOM_INSET, BOTTOM_INSET};
        double[] ys = {BED_TOP, BED_TOP, bottom, bottom};
        g.setFill(soil);
        g.fillPolygon(xs, ys, 4);

        // furrows get farther apart toward the viewer
        g.setStroke(soil.deriveColor(0, 1, 0.8, 1));
        g.setLineWidth(1);
        double[] rows = {0.18, 0.4, 0.68};
        for (double t : rows) {
            double y = BED_TOP + t * (bottom - BED_TOP);
            double inset = TOP_INSET + t * (BOTTOM_INSET - TOP_INSET) + 3;
            g.strokeLine(inset, y, w - inset, y);
        }

        // health bar across the bed's front face, once the tree has been hurt
        double health = plot.getHealth();
        if (health < 99.5 && plot.hasTree()) {
            double barW = w - 2 * BOTTOM_INSET - 6;
            g.setFill(Color.web("#2a1a0e"));
            g.fillRect(BOTTOM_INSET + 3, bottom + 0.5, barW, BED_DEPTH - 1);
            g.setFill(HEALTH_LOW.interpolate(HEALTH_HIGH, health / 100));
            g.fillRect(BOTTOM_INSET + 3, bottom + 0.5, barW * health / 100, BED_DEPTH - 1);
        }

        g.setStroke(root.isHover() ? HOVER : BED_EDGE);
        g.setLineWidth(root.isHover() ? 1.5 : 1);
        g.strokePolygon(
                new double[]{TOP_INSET, w - TOP_INSET, w - BOTTOM_INSET, w - BOTTOM_INSET, BOTTOM_INSET, BOTTOM_INSET},
                new double[]{BED_TOP, BED_TOP, bottom, bottom + BED_DEPTH, bottom + BED_DEPTH, bottom}, 6);
    }

    /** Resolves the visible glyphs into one character per cell. */
    private void layoutTree(Species species) {
        for (int row = 0; row < Plot.ROWS; row++) {
            java.util.Arrays.fill(cells[row], ' ');
        }

        Color trunkBright = Color.web(species.trunkBright);
        Color trunkDark = Color.web(species.trunkDark);
        Color leafBright = Color.web(species.leafBright);
        Color leafDark = Color.web(species.leafDark);
        boolean dead = plot.getState() == Plot.State.DEAD;
        if (dead) {
            trunkBright = plot.isCharred() ? CHARCOAL : DEAD_WOOD;
            trunkDark = plot.isCharred() ? CHARCOAL_DARK : DEAD_WOOD_DARK;
        }

        // leaves turn brown as health drops, then start falling off
        double damage = 1 - plot.getHealth() / 100;
        leafBright = leafBright.interpolate(DRY_LEAF, damage);
        leafDark = leafDark.interpolate(DRY_LEAF.darker(), damage);
        double leavesKept = Math.min(1, plot.getHealth() / LEAF_DROP_BELOW);

        List<Glyph> glyphs = plot.getGlyphs();
        int visible = plot.getVisibleCount();
        for (int i = 0; i < visible; i++) {
            Glyph glyph = glyphs.get(i);
            boolean leaf = glyph.type.isLeaf() || isLeafText(glyph.text, species);
            if (leaf && (dead || leafChance(i) > leavesKept)) {
                continue;   // this leaf has fallen
            }
            Color ink = leaf ? (glyph.bright ? leafBright : leafDark)
                             : (glyph.bright ? trunkBright : trunkDark);
            for (int k = 0; k < glyph.text.length(); k++) {
                int col = glyph.x + k;
                if (glyph.y < 0 || glyph.y >= Plot.ROWS || col < 0 || col >= Plot.COLS) continue;
                cells[glyph.y][col] = glyph.text.charAt(k);
                inks[glyph.y][col] = ink;
            }
        }
    }

    /**
     * Each character gets a dark outline so it reads on grass and on soil.
     * In wind each row leans sideways, more the higher it is, so the tree sways.
     */
    private void drawTree(GraphicsContext g, double time, double sway, double shake) {
        double phase = (plot.getSeed() & 0xFF) / 40.0;
        double[] rowShift = new double[Plot.ROWS];
        for (int row = 0; row < Plot.ROWS; row++) {
            double height = Math.max(0, (Plot.START_Y - row) / (double) Plot.START_Y);
            rowShift[row] = shake + sway * height * (Math.sin(time * 2.6 + phase) + 0.35 * Math.sin(time * 7.3 + row * 0.4));
        }

        g.setFont(FONT);
        g.setLineWidth(2);
        g.setStroke(OUTLINE);
        for (int row = 0; row < Plot.ROWS; row++) {
            for (int col = 0; col < Plot.COLS; col++) {
                if (cells[row][col] != ' ') {
                    g.strokeText(String.valueOf(cells[row][col]), col * CELL_W + rowShift[row], (row + 1) * CELL_H - 1);
                }
            }
        }
        for (int row = 0; row < Plot.ROWS; row++) {
            for (int col = 0; col < Plot.COLS; col++) {
                if (cells[row][col] != ' ') {
                    g.setFill(inks[row][col]);
                    g.fillText(String.valueOf(cells[row][col]), col * CELL_W + rowShift[row], (row + 1) * CELL_H - 1);
                }
            }
        }
    }

    /**
     * Pixel flames licking up from the tree's branches and leaves, a glow on the bed,
     * and puffs of smoke drifting up from the top. Redrawn 12 times a second so it flickers.
     */
    private void drawFire(GraphicsContext g, double time) {
        int frame = (int) (time * 12);
        double cx = Plot.START_X * CELL_W + CELL_W / 2;
        g.setFill(EMBER_GLOW);
        g.fillOval(cx - 22, BED_TOP - 4, 44, 16);

        int top = Plot.ROWS;
        for (int row = 0; row < Plot.ROWS; row++) {
            for (int col = 0; col < Plot.COLS; col++) {
                if (cells[row][col] == ' ') continue;
                top = Math.min(top, row);
                long h = mix(row * 31 + col, frame);
                if ((h & 3) != 0) continue;   // about a quarter of the cells are burning at any moment
                double x = col * CELL_W + ((h >>> 2) & 1) * FIRE_PIXEL;
                double y = (row + 1) * CELL_H - FIRE_PIXEL * 2;
                int height = 2 + (int) ((h >>> 4) % 4);
                for (int k = 0; k < height; k++) {
                    int shade = Math.min(FLAME.length - 1, k + (int) ((h >>> 8) & 1));
                    double drift = k >= 2 ? ((h >>> (10 + k)) & 1) * FIRE_PIXEL - FIRE_PIXEL / 2 : 0;
                    g.setFill(FLAME[shade]);
                    g.fillRect(x + drift, y - k * FIRE_PIXEL, FIRE_PIXEL, FIRE_PIXEL);
                }
            }
        }
        if (top == Plot.ROWS) top = Plot.START_Y - 2;   // nothing visible yet: flames at the base

        // smoke rises from the top of the tree and fades
        for (int i = 0; i < 6; i++) {
            double t = (time * 0.8 + i / 6.0) % 1.0;
            long h = mix(i, (long) (time * 0.8 + i / 6.0));
            double x = cx - 10 + (h & 15) * 1.3 + Math.sin(time * 2 + i) * 2;
            double y = top * CELL_H - 2 - t * 26;
            double size = FIRE_PIXEL * (1 + t * 2);
            g.setGlobalAlpha(0.55 * (1 - t));
            g.setFill(SMOKE);
            g.fillRect(Math.round(x / 2) * 2, Math.round(y / 2) * 2, size, size);
        }
        g.setGlobalAlpha(1);
    }

    /** A cheap, repeatable scramble of two numbers (for flicker that looks random). */
    private static long mix(long a, long b) {
        long h = (a * 0x9E3779B97F4A7C15L) ^ (b * 0xC2B2AE3D27D4EB4FL);
        h ^= h >>> 29;
        h *= 0xBF58476D1CE4E5B9L;
        return h ^ (h >>> 32);
    }

    /** A bobbing star above the tree, like a crop that's ready. */
    private void drawSparkle(GraphicsContext g, long nanos) {
        double bob = Math.sin(nanos / 2e8) * 1.5;
        g.setFont(FONT);
        g.setFill(SPARKLE);
        g.setStroke(OUTLINE);
        g.setLineWidth(2);
        double x = canvas.getWidth() - TOP_INSET + 4;
        double y = BED_TOP - 2 + bob;
        g.strokeText("*", x, y);
        g.fillText("*", x, y);
    }

    /** A fixed pseudo-random number in [0, 1) per leaf, so the same leaves fall each time. */
    private double leafChance(int index) {
        long h = (plot.getSeed() ^ (index * 0x9E3779B97F4A7C15L)) * 0xBF58476D1CE4E5B9L;
        return ((h >>> 11) & 0xFFFF) / 65536.0;
    }

    /** Trunk glyphs near the end of a branch's life turn into leaves too (as in cbonsai). */
    private static boolean isLeafText(String text, Species species) {
        for (String leaf : species.leaves) {
            if (leaf.equals(text)) return true;
        }
        return false;
    }
}

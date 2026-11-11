package garden;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

import javafx.geometry.Bounds;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;

/**
 * Every bug on the field: ladybugs and bees that fly around, and cockroaches
 * that sneak in every so often and have to be stomped (clicked) before they
 * eat the trees. Also answers the bugs' questions about the field.
 */
public class Critters {

    private static final int LADYBUGS = 4;
    private static final int BEES = 5;
    private static final int MAX_ROACHES = 6;
    private static final double FIRST_ROACH = 6;        // seconds
    private static final double ROACH_EVERY_MIN = 7;
    private static final double ROACH_EVERY_MAX = 14;
    private static final double STOMP_RADIUS = 10;
    private static final double SPLAT_SECONDS = 5;
    private static final double EDGE = 12;              // keep flyers inside the fence
    /** Rare events: seconds (of bug time) between visits, picked at random in this range. */
    private static final double RIDER_EVERY_MIN = 150;
    private static final double RIDER_EVERY_MAX = 330;
    private static final double BRUTE_EVERY_MIN = 180;
    private static final double BRUTE_EVERY_MAX = 380;

    private static final Color SPLAT = Color.web("#4a3a12");
    private static final Color SPLAT_GOO = Color.web("#9fae3c");
    private static final Color PESTICIDE = Color.web("#d8f5a2");

    private final List<Bug> bugs = new ArrayList<Bug>();
    private final List<Splat> splats = new ArrayList<Splat>();
    private final Random rng = new Random();
    private final List<PlotController> plots;
    private double width;
    private double height;
    private double roachTimer = FIRST_ROACH;
    private int stomped;
    private boolean populated;
    private double riderTimer;
    private double bruteTimer;

    public Critters(List<PlotController> plots) {
        this.plots = plots;
        riderTimer = between(RIDER_EVERY_MIN, RIDER_EVERY_MAX);
        bruteTimer = between(BRUTE_EVERY_MIN, BRUTE_EVERY_MAX);
    }

    private double between(double min, double max) {
        return min + rng.nextDouble() * (max - min);
    }

    public void update(double dt, double width, double height, GameClock.Season season) {
        this.width = width;
        this.height = height;
        if (width <= 0 || height <= 0) return;   // not laid out yet

        if (!populated) {
            for (int i = 0; i < LADYBUGS; i++) bugs.add(new Ladybug(randomX(), randomY(), rng));
            for (int i = 0; i < BEES; i++) bugs.add(new Bee(randomX(), randomY(), rng));
            populated = true;
        }

        roachTimer -= dt * season.roaches;   // more roaches in summer, hardly any in winter
        if (roachTimer <= 0) {
            roachTimer = ROACH_EVERY_MIN + rng.nextDouble() * (ROACH_EVERY_MAX - ROACH_EVERY_MIN);
            PlotController meal = randomLivingPlot();
            if (getRoachCount() < MAX_ROACHES && meal != null) {
                // squeeze in under whichever fence (left, right or bottom) is closest to its tree
                double[] bed = bedOf(meal);
                double toLeft = bed[0];
                double toRight = width - bed[0];
                double toBottom = height - bed[1];
                double x = bed[0];
                double y = height - 4;
                if (toLeft <= toRight && toLeft <= toBottom) {
                    x = 4;
                    y = bed[1];
                } else if (toRight <= toBottom) {
                    x = width - 4;
                    y = bed[1];
                }
                bugs.add(new Cockroach(x, y, rng, meal, this));
                GardenLog.warn(GardenLog.Category.WILDLIFE, "Cockroach crawled in under the fence, heading for %s",
                        meal.getPlot().getName());
            }
        }

        riderTimer -= dt;
        if (riderTimer <= 0) {
            riderTimer = between(RIDER_EVERY_MIN, RIDER_EVERY_MAX);
            summonRider();
        }
        bruteTimer -= dt;
        if (bruteTimer <= 0) {
            bruteTimer = between(BRUTE_EVERY_MIN, BRUTE_EVERY_MAX);
            summonBrute();
        }

        // copy, because a visitor may be added while the others are moving
        for (Bug bug : new ArrayList<Bug>(bugs)) {
            if (!bug.update(dt, this)) bugs.remove(bug);
        }
        for (Iterator<Splat> it = splats.iterator(); it.hasNext(); ) {
            Splat splat = it.next();
            splat.age += dt;
            if (splat.age > SPLAT_SECONDS) it.remove();
        }
    }

    public void render(Canvas canvas, long nanos) {
        GraphicsContext g = canvas.getGraphicsContext2D();
        g.clearRect(0, 0, canvas.getWidth(), canvas.getHeight());
        double time = nanos / 1e9;

        for (Splat splat : splats) {
            drawSplat(g, splat);
        }
        // crawlers on the ground first, then the big visitors, then flyers above everything
        for (Bug bug : bugs) {
            if (bug instanceof Cockroach) bug.draw(g, time);
        }
        for (Bug bug : bugs) {
            if (bug instanceof HellfireRider || bug instanceof GreenBrute) bug.draw(g, time);
        }
        for (Bug bug : bugs) {
            if (bug instanceof Ladybug || bug instanceof Bee) bug.draw(g, time);
        }
    }

    /** Stomps the nearest cockroach under the click, if any. */
    public boolean stompAt(double px, double py) {
        Bug hit = null;
        double best = STOMP_RADIUS;
        for (Bug bug : bugs) {
            double d = bug.distanceTo(px, py);
            if (bug instanceof Cockroach && d < best) {
                hit = bug;
                best = d;
            }
        }
        if (hit == null) return false;
        bugs.remove(hit);
        splats.add(new Splat(hit.x, hit.y, rng.nextLong(), false));
        stomped++;
        GardenLog.info(GardenLog.Category.USER, "Cockroach stomped by hand near %s", ((Cockroach) hit).getMealName());
        return true;
    }

    // ---- rare events ----

    /**
     * Sends a Hellfire Rider along a random row that still has living trees.
     *
     * @return false if one is already here, or there's nothing to burn
     */
    public boolean summonRider() {
        if (width <= 0 || has(HellfireRider.class)) return false;
        PlotController start = randomLivingPlot();
        if (start == null) return false;
        double rowTop = start.getRoot().getBoundsInParent().getMinY();
        List<PlotController> row = new ArrayList<PlotController>();
        for (PlotController view : plots) {
            if (Math.abs(view.getRoot().getBoundsInParent().getMinY() - rowTop) < 1) row.add(view);
        }
        double ground = bedOf(start)[1] + 12;
        bugs.add(new HellfireRider(row, ground, width, rng.nextBoolean(), rng));
        return true;
    }

    /**
     * Sends a Green Brute in from the left or right fence.
     *
     * @return false if one is already here, or there are no trees to smash
     */
    public boolean summonBrute() {
        if (width <= 0 || has(GreenBrute.class)) return false;
        PlotController near = randomLivingPlot();
        if (near == null) return false;
        double y = bedOf(near)[1] + 14;
        bugs.add(new GreenBrute(rng.nextBoolean() ? -30 : width + 30, y, width, rng));
        return true;
    }

    /** A click on the Green Brute pokes him; returns true if it hit him. */
    public boolean pokeAt(double px, double py) {
        for (Bug bug : bugs) {
            if (bug instanceof GreenBrute && ((GreenBrute) bug).isHit(px, py)) {
                ((GreenBrute) bug).poke();
                return true;
            }
        }
        return false;
    }

    private boolean has(Class<? extends Bug> kind) {
        for (Bug bug : bugs) {
            if (kind.isInstance(bug)) return true;
        }
        return false;
    }

    /** Pest control's pesticide: the roach dies in a green puff. */
    public void exterminate(Cockroach roach) {
        if (bugs.remove(roach)) {
            splats.add(new Splat(roach.x, roach.y, rng.nextLong(), true));
        }
    }

    public List<Cockroach> getRoaches() {
        List<Cockroach> roaches = new ArrayList<Cockroach>();
        for (Bug bug : bugs) {
            if (bug instanceof Cockroach) roaches.add((Cockroach) bug);
        }
        return roaches;
    }

    public boolean isRoachAt(double px, double py) {
        for (Bug bug : bugs) {
            if (bug instanceof Cockroach && bug.distanceTo(px, py) < STOMP_RADIUS) return true;
        }
        return false;
    }

    private void drawSplat(GraphicsContext g, Splat splat) {
        double fade = Math.max(0, 1 - splat.age / SPLAT_SECONDS);
        if (splat.sprayed) {
            // a spreading cloud of pesticide over a curled-up roach
            double r = 6 + Math.min(1, splat.age / 0.6) * 10;
            g.setGlobalAlpha(fade * 0.45);
            g.setFill(PESTICIDE);
            g.fillOval(splat.x - r, splat.y - r * 0.7, r * 2, r * 1.4);
            g.setGlobalAlpha(fade);
            g.setFill(SPLAT);
            g.fillOval(splat.x - 3, splat.y - 1.5, 6, 3);
            g.setGlobalAlpha(1);
            return;
        }
        g.setGlobalAlpha(fade);
        Random spatter = new Random(splat.seed);
        g.setFill(SPLAT);
        g.fillOval(splat.x - 5, splat.y - 3.5, 10, 7);
        for (int i = 0; i < 7; i++) {
            double a = spatter.nextDouble() * Math.PI * 2;
            double r = 5 + spatter.nextDouble() * 4;
            double s = 1 + spatter.nextDouble() * 1.8;
            g.fillOval(splat.x + Math.cos(a) * r - s / 2, splat.y + Math.sin(a) * r * 0.7 - s / 2, s, s);
        }
        g.setFill(SPLAT_GOO);
        g.fillOval(splat.x - 2.5, splat.y - 1.5, 4.5, 3);
        g.setGlobalAlpha(1);
    }

    // ---- questions bugs ask about the field ----

    public double randomX() {
        return EDGE + rng.nextDouble() * Math.max(1, width - 2 * EDGE);
    }

    public double randomY() {
        return EDGE + rng.nextDouble() * Math.max(1, height - 2 * EDGE);
    }

    /** A plot with leaves on its tree, for bees; null if there are none. */
    public PlotController randomLeafyPlot() {
        List<PlotController> leafy = new ArrayList<PlotController>();
        for (PlotController view : plots) {
            Plot plot = view.getPlot();
            if (plot.hasTree() && plot.getVisibleCount() > 20) leafy.add(view);
        }
        return leafy.isEmpty() ? null : leafy.get(rng.nextInt(leafy.size()));
    }

    /** A plot whose tree is still alive, for roaches; null if there are none. */
    public PlotController randomLivingPlot() {
        List<PlotController> living = new ArrayList<PlotController>();
        for (PlotController view : plots) {
            if (view.getPlot().hasTree()) living.add(view);
        }
        return living.isEmpty() ? null : living.get(rng.nextInt(living.size()));
    }

    /** Middle of the plot's soil bed, in field coordinates. */
    public double[] bedOf(PlotController view) {
        Bounds b = view.getRoot().getBoundsInParent();
        return new double[]{(b.getMinX() + b.getMaxX()) / 2, b.getMinY() + PlotController.BED_TOP + 9};
    }

    /** Roughly where the tree's leaves are, in field coordinates. */
    public double[] canopyOf(PlotController view) {
        Bounds b = view.getRoot().getBoundsInParent();
        return new double[]{(b.getMinX() + b.getMaxX()) / 2, b.getMinY() + PlotController.BED_TOP - 30};
    }

    public int getRoachCount() {
        int n = 0;
        for (Bug bug : bugs) {
            if (bug instanceof Cockroach) n++;
        }
        return n;
    }

    public int getStomped() {
        return stomped;
    }

    private static final class Splat {
        final double x;
        final double y;
        final long seed;
        final boolean sprayed;
        double age;

        Splat(double x, double y, long seed, boolean sprayed) {
            this.x = x;
            this.y = y;
            this.seed = seed;
            this.sprayed = sprayed;
        }
    }
}

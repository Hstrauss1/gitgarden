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
 * A rare event: a huge green brute stomps into the garden and smashes a few trees,
 * one after another, before stomping off. Each smash takes a big chunk of health
 * and can kill a weakened tree outright. Poke him (click him) enough times and he
 * calms down and leaves early.
 */
public class GreenBrute extends Bug {

    private static final double SPEED = 75;
    private static final double PIXEL = 2.5;
    public static final double SMASH_DAMAGE = 45;
    private static final double SMASH_TIME = 0.8;        // wind-up plus follow-through, in seconds
    private static final double IMPACT_AT = 0.4;
    public static final int POKES_TO_CALM = 5;
    private static final double HIT_RADIUS = 24;
    private static final double DUST_LIFE = 0.7;

    private static final String[] PALETTE = {
        "g", "#5fae3a", "G", "#3f7f26", "d", "#24481a", "h", "#1d1d1d", "e", "#f2f2f2", "p", "#b3261e",
        "m", "#2a1a12", "t", "#f2f2f2", "j", "#4a5a78", "J", "#34405a"
    };

    private static final PixelSprite WALK_A = new PixelSprite(new String[]{
        ".....hhhhhh.....",
        "....hhhhhhhh....",
        "....gddggddg....",
        "....gepggpeg....",
        "....gggggggg....",
        "....gmttttmg....",
        ".....gGGGGg.....",
        "..GggggggggggG..",
        ".GggdggggggdggG.",
        "Ggg.GggggggG.ggG",
        "Ggg.ggGggGgg.ggG",
        "Ggg.gggggggg.ggG",
        "GGG.jjjjjjjj.GGG",
        "GGG.jjjJJjjj.GGG",
        "....jjj..jjj....",
        "....jJj..jJj....",
        "....ggg..ggg....",
        "...dggg..gggd...",
    }, PALETTE);

    private static final PixelSprite WALK_B = new PixelSprite(new String[]{
        ".....hhhhhh.....",
        "....hhhhhhhh....",
        "....gddggddg....",
        "....gepggpeg....",
        "....gggggggg....",
        "....gmttttmg....",
        ".....gGGGGg.....",
        "..GggggggggggG..",
        ".GggdggggggdggG.",
        "Ggg.GggggggG.ggG",
        "Ggg.ggGggGgg.ggG",
        "Ggg.gggggggg.ggG",
        "GGG.jjjjjjjj.GGG",
        "GGG.jjjJJjjj.GGG",
        "....jjj..jjj....",
        "....jJj...jJj...",
        "....ggg...ggg...",
        "...dggg...gggd..",
    }, PALETTE);

    /** Both fists raised over his head, about to come down. */
    private static final PixelSprite SMASH = new PixelSprite(new String[]{
        "GGG..........GGG",
        "GGG..hhhhhh..GGG",
        "Ggg.hhhhhhhh.ggG",
        "Ggg.gddggddg.ggG",
        "Ggg.gepggpeg.ggG",
        ".gg.gggggggg.gg.",
        ".gg.gmttttmg.gg.",
        ".gggggGGGGggggg.",
        "..GggdggggdggG..",
        "...GggggggggG...",
        "....gggggggg....",
        "....gGggggGg....",
        "....jjjjjjjj....",
        "....jjjJJjjj....",
        "...jjj....jjj...",
        "...jJj....jJj...",
        "...ggg....ggg...",
        "..dggg....gggd..",
    }, PALETTE);

    private static final Color SHADOW = Color.web("#000000", 0.28);
    private static final Color DUST = Color.web("#c9a77a");
    private static final Color ANGER = Color.web("#ff3b2f");
    private static final Color CALM = Color.web("#ffffff");

    private enum Mode { WALKING, SMASHING, LEAVING }

    private final Set<Plot> smashed = new HashSet<Plot>();
    private final List<double[]> dust = new ArrayList<double[]>();   // x, y, vx, vy, age
    private final int rampage;
    private final double width;
    private Mode mode = Mode.WALKING;
    private PlotController target;
    private double smashClock;
    private boolean landed;
    private double walkClock;
    private int pokes;
    private boolean calmed;
    private boolean facingLeft;

    public GreenBrute(double x, double y, double width, Random rng) {
        super(x, y, rng);
        this.width = width;
        this.rampage = 3 + rng.nextInt(3);
        GardenLog.alert(GardenLog.Category.INTRUDER,
                "A Green Brute stomped into the garden and wants to smash %d trees! Click him %d times to calm him down",
                rampage, POKES_TO_CALM);
    }

    @Override
    public boolean update(double dt, Critters field) {
        walkClock += dt;
        for (Iterator<double[]> it = dust.iterator(); it.hasNext(); ) {
            double[] d = it.next();
            d[0] += d[2] * dt;
            d[1] += d[3] * dt;
            d[3] += 60 * dt;
            d[4] += dt;
            if (d[4] > DUST_LIFE) it.remove();
        }

        switch (mode) {
            case WALKING:
                if (target == null || !target.getPlot().hasTree()) {
                    target = pickTarget(field);
                    if (target == null) {
                        leave("no more trees to smash");
                        break;
                    }
                }
                double[] spot = standingSpot(field, target);
                walkTo(spot[0], spot[1], SPEED, dt);
                if (distanceTo(spot[0], spot[1]) < 3) {
                    mode = Mode.SMASHING;
                    smashClock = 0;
                    landed = false;
                }
                break;
            case SMASHING:
                smashClock += dt;
                if (!landed && smashClock >= IMPACT_AT) {
                    landed = true;
                    smash(field);
                }
                if (smashClock >= SMASH_TIME) {
                    target = null;
                    if (smashed.size() >= rampage) {
                        leave("smashed " + smashed.size() + " trees");
                    } else {
                        mode = Mode.WALKING;
                    }
                }
                break;
            case LEAVING:
                double exitX = x < width / 2 ? -60 : width + 60;
                walkTo(exitX, y, calmed ? SPEED * 1.6 : SPEED * 1.2, dt);
                if (Math.abs(x - exitX) < 4 && dust.isEmpty()) {
                    GardenLog.info(GardenLog.Category.INTRUDER, "The Green Brute is gone. He smashed %d tree%s",
                            smashed.size(), smashed.size() == 1 ? "" : "s");
                    return false;
                }
                break;
            default:
                break;
        }
        return true;
    }

    private PlotController pickTarget(Critters field) {
        for (int tries = 0; tries < 12; tries++) {
            PlotController view = field.randomLivingPlot();
            if (view == null) return null;
            if (!smashed.contains(view.getPlot())) return view;
        }
        return null;
    }

    /** He stands just in front of the bed, so he looms over the tree. */
    private static double[] standingSpot(Critters field, PlotController view) {
        double[] bed = field.bedOf(view);
        return new double[]{bed[0], bed[1] + 14};
    }

    private void walkTo(double tx, double ty, double speed, double dt) {
        double dx = tx - x;
        double dy = ty - y;
        double dist = Math.hypot(dx, dy);
        if (dist < 0.5) return;
        double step = Math.min(dist, speed * dt);
        x += dx / dist * step;
        y += dy / dist * step;
        if (Math.abs(dx) > 1) facingLeft = dx < 0;
    }

    private void smash(Critters field) {
        Plot plot = target.getPlot();
        if (!plot.hasTree()) return;
        smashed.add(plot);
        plot.smash(SMASH_DAMAGE);
        target.shake();
        for (int i = 0; i < 14; i++) {
            double a = Math.PI + rng.nextDouble() * Math.PI;
            double v = 30 + rng.nextDouble() * 50;
            dust.add(new double[]{x + rng.nextGaussian() * 6, y - 14, Math.cos(a) * v, Math.sin(a) * v, 0});
        }
        GardenLog.warn(GardenLog.Category.INTRUDER, "Green Brute SMASHED %s (%s): health now %.0f%%",
                plot.getName(), plot.getSpecies().displayName, plot.getHealth());
    }

    private void leave(String why) {
        if (mode == Mode.LEAVING) return;
        mode = Mode.LEAVING;
        GardenLog.info(GardenLog.Category.INTRUDER, "The Green Brute is leaving (%s)", why);
    }

    /** True if a click at (px, py) lands on him. */
    public boolean isHit(double px, double py) {
        double top = y - WALK_A.getHeight() * PIXEL;
        return Math.abs(px - x) < HIT_RADIUS && py > top - 4 && py < y + 4;
    }

    /** The gardener pokes him; enough pokes and he calms down and leaves. */
    public void poke() {
        if (calmed) return;
        pokes++;
        if (pokes >= POKES_TO_CALM) {
            calmed = true;
            GardenLog.info(GardenLog.Category.USER, "Poked the Green Brute (%d/%d): he calmed down", pokes, POKES_TO_CALM);
            leave("calmed down by the gardener");
        } else {
            GardenLog.info(GardenLog.Category.USER, "Poked the Green Brute (%d/%d)", pokes, POKES_TO_CALM);
        }
    }

    @Override
    public void draw(GraphicsContext g, double time) {
        PixelSprite sprite;
        double bob = 0;
        if (mode == Mode.SMASHING && smashClock < IMPACT_AT) {
            sprite = SMASH;
            bob = -PIXEL;   // rises onto his toes
        } else if (mode == Mode.SMASHING) {
            sprite = WALK_A;
            bob = PIXEL;    // slams down
        } else {
            boolean step = ((int) (walkClock * 4)) % 2 == 0;
            sprite = step ? WALK_A : WALK_B;
            bob = step ? 0 : -1;
        }
        double w = sprite.getWidth() * PIXEL;
        double h = sprite.getHeight() * PIXEL;

        g.setFill(SHADOW);
        g.fillOval(x - w / 2, y - 3, w, 7);
        sprite.draw(g, Math.round(x - w / 2), Math.round(y - h + bob), PIXEL, facingLeft);

        // puffing with rage, or calm once he's been talked down
        if (calmed) {
            g.setFill(CALM);
            double t = (time * 1.5) % 1;
            g.setGlobalAlpha(1 - t);
            g.fillRect(Math.round(x + w / 2 - 2), Math.round(y - h - 4 - t * 8), PIXEL, PIXEL);
            g.fillRect(Math.round(x + w / 2 + 2), Math.round(y - h - 8 - t * 8), PIXEL, PIXEL);
            g.setGlobalAlpha(1);
        } else if (((int) (time * 3)) % 2 == 0) {
            g.setFill(ANGER);
            double ax = Math.round(x + w / 2 - 1);
            double ay = Math.round(y - h - 2);
            g.fillRect(ax, ay, PIXEL, PIXEL);
            g.fillRect(ax + PIXEL * 2, ay, PIXEL, PIXEL);
            g.fillRect(ax + PIXEL, ay + PIXEL, PIXEL, PIXEL);
            g.fillRect(ax, ay + PIXEL * 2, PIXEL, PIXEL);
            g.fillRect(ax + PIXEL * 2, ay + PIXEL * 2, PIXEL, PIXEL);
        }

        for (double[] d : dust) {
            double t = d[4] / DUST_LIFE;
            g.setGlobalAlpha(0.85 * (1 - t));
            g.setFill(DUST);
            double size = PIXEL * (1 + t);
            g.fillRect(Math.round(d[0]), Math.round(d[1]), size, size);
        }
        g.setGlobalAlpha(1);
    }
}

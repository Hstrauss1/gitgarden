package garden;

import java.util.Random;

import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;

/**
 * Sneaks in from the fence, skitters to a tree and eats it, draining its health.
 * If the tree dies it moves on to another. Click it to stomp it.
 */
public class Cockroach extends Bug {

    private static final double SPEED = 55;
    private static final double EAT_RATE = 6;       // tree health eaten per second
    private static final Color SHELL = Color.web("#5b3317");
    private static final Color SHELL_SHINE = Color.web("#9a6436");
    private static final Color LEGS = Color.web("#2a1709");
    private static final Color CRUMB = Color.web("#7fd36a");

    private PlotController meal;
    private double targetX;
    private double targetY;
    private double pause;          // roaches dart, freeze, then dart again
    private boolean eating;
    private double legPhase;

    /** A roach that has already picked the tree it's going to eat. */
    public Cockroach(double x, double y, Random rng, PlotController meal, Critters field) {
        super(x, y, rng);
        goEat(meal, field);
    }

    @Override
    public boolean update(double dt, Critters field) {
        if (meal == null || !meal.getPlot().hasTree()) {
            // its tree died or was chopped: find another
            goEat(field.randomLivingPlot(), field);
            if (meal == null) return true;
        }

        if (eating) {
            meal.getPlot().chew(EAT_RATE * dt);
            legPhase += dt * 4;     // a little fidgeting while it eats
            return true;
        }

        if (pause > 0) {
            pause -= dt;
            vx *= 0.5;
            vy *= 0.5;
            return true;
        }
        if (rng.nextDouble() < dt * 0.8) {
            pause = 0.15 + rng.nextDouble() * 0.4;
        }

        double speed = SPEED * (0.7 + rng.nextDouble() * 0.6);
        steerToward(targetX, targetY, speed, 6, dt);
        legPhase += dt * 30;
        if (distanceTo(targetX, targetY) < 3) {
            eating = true;
            GardenLog.alert(GardenLog.Category.WILDLIFE, "Cockroach started eating %s", getMealName());
        }
        return true;
    }

    public boolean isEating() { return eating && meal != null && meal.getPlot().hasTree(); }

    public String getMealName() {
        return meal == null ? "nothing" : meal.getPlot().getName() + " (" + meal.getPlot().getSpecies().displayName + ")";
    }

    public double getMealHealth() { return meal == null ? 0 : meal.getPlot().getHealth(); }

    private void goEat(PlotController plot, Critters field) {
        meal = plot;
        eating = false;
        if (plot == null) return;
        double[] bed = field.bedOf(plot);
        // right up against the trunk
        targetX = bed[0] + (rng.nextBoolean() ? 1 : -1) * (4 + rng.nextDouble() * 4);
        targetY = bed[1] - 2 + rng.nextDouble() * 3;
    }

    @Override
    public void draw(GraphicsContext g, double time) {
        g.save();
        g.translate(x, y);
        g.rotate(headingDegrees());

        // six wiggling legs
        g.setStroke(LEGS);
        g.setLineWidth(0.7);
        for (int i = -1; i <= 1; i++) {
            double swing = Math.sin(legPhase + i * 2.1) * 1.5;
            g.strokeLine(i * 2.2, -1.5, i * 2.2 + swing - 0.8, -4.5);
            g.strokeLine(i * 2.2, 1.5, i * 2.2 - swing - 0.8, 4.5);
        }
        // long antennae
        g.strokeLine(5, -0.8, 9.5, -3.5 + Math.sin(legPhase * 0.5));
        g.strokeLine(5, 0.8, 9.5, 3.5 - Math.sin(legPhase * 0.5));

        g.setFill(SHELL);
        g.fillOval(-5.5, -2.6, 10, 5.2);
        g.fillOval(3.5, -1.6, 3, 3.2);          // head
        g.setFill(SHELL_SHINE);
        g.fillOval(-3.5, -1.8, 5, 1.6);
        g.setStroke(LEGS);
        g.setLineWidth(0.5);
        g.strokeLine(-5.5, 0, 2.5, 0);          // wing seam

        if (eating) {
            // bits of leaf flying out of its mouth
            g.setFill(CRUMB);
            for (int i = 0; i < 3; i++) {
                double t = (time * 2.5 + i / 3.0) % 1.0;
                double side = i == 1 ? 0 : (i == 0 ? -1 : 1);
                g.fillRect(7 + t * 4, side * t * 4 - t * (1 - t) * 6, 1.3, 1.3);
            }
        }
        g.restore();
    }
}

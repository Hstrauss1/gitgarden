package garden;

import java.util.Random;

import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;

/** Drifts lazily from one random spot in the field to another. */
public class Ladybug extends Bug {

    private static final double SPEED = 22;
    private static final double FLY_HEIGHT = 9;
    private static final Color SHELL = Color.web("#e5322a");
    private static final Color SHINE = Color.web("#ff8a7a");
    private static final Color BLACK = Color.web("#1a1a1a");

    private double targetX;
    private double targetY;
    private final double bobPhase;

    public Ladybug(double x, double y, Random rng) {
        super(x, y, rng);
        targetX = x;
        targetY = y;
        bobPhase = rng.nextDouble() * Math.PI * 2;
    }

    @Override
    public boolean update(double dt, Critters field) {
        if (distanceTo(targetX, targetY) < 6) {
            targetX = field.randomX();
            targetY = field.randomY();
        }
        steerToward(targetX, targetY, SPEED, 1.2, dt);
        return true;
    }

    @Override
    public void draw(GraphicsContext g, double time) {
        double bob = Math.sin(time * 3 + bobPhase) * 1.2;
        drawShadow(g, FLY_HEIGHT, 7);

        g.save();
        g.translate(x, y + bob);
        g.rotate(headingDegrees());
        // head
        g.setFill(BLACK);
        g.fillOval(2, -2, 3.5, 4);
        // shell, centre seam and spots
        g.setFill(SHELL);
        g.fillOval(-4, -3.5, 7, 7);
        g.setFill(SHINE);
        g.fillOval(-2.5, -2.8, 2.5, 1.5);
        g.setStroke(BLACK);
        g.setLineWidth(0.6);
        g.strokeOval(-4, -3.5, 7, 7);
        g.strokeLine(-4, 0, 3, 0);
        g.setFill(BLACK);
        g.fillOval(-2.5, -2.5, 1.6, 1.6);
        g.fillOval(-2.5, 0.9, 1.6, 1.6);
        g.fillOval(0.3, -2.0, 1.4, 1.4);
        g.fillOval(0.3, 0.6, 1.4, 1.4);
        g.restore();
    }
}

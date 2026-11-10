package garden;

import java.util.Random;

import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;

/**
 * Flies from tree to tree and hovers over the canopy for a few seconds.
 * A bee hovering over a growing tree pollinates it, which speeds up its growth.
 */
public class Bee extends Bug {

    private static final double SPEED = 48;
    private static final double FLY_HEIGHT = 14;
    private static final Color BODY = Color.web("#f7c51e");
    private static final Color STRIPE = Color.web("#1e1a12");
    private static final Color WING = Color.web("#ffffff", 0.7);

    private PlotController visiting;
    private double targetX;
    private double targetY;
    private double hoverLeft;

    public Bee(double x, double y, Random rng) {
        super(x, y, rng);
        targetX = x;
        targetY = y;
    }

    @Override
    public boolean update(double dt, Critters field) {
        if (visiting != null && !visiting.getPlot().hasTree()) {
            pickFlower(field);
        }

        if (distanceTo(targetX, targetY) < 8) {
            // hovering over the tree (or just a spot in the field): drift around it and pollinate
            hoverLeft -= dt;
            if (visiting != null) visiting.getPlot().pollinate(dt);
            steerToward(targetX + Math.sin(hoverLeft * 4) * 6, targetY + Math.cos(hoverLeft * 3) * 3, 18, 3, dt);
            if (hoverLeft <= 0) {
                pickFlower(field);
            }
        } else {
            // a slight zig-zag on the way there
            steerToward(targetX, targetY, SPEED, 2.5, dt);
            x += Math.sin(hoverLeft * 9 + y * 0.1) * 0.4;
        }
        return true;
    }

    private void pickFlower(Critters field) {
        PlotController next = field.randomLeafyPlot();
        visiting = next;
        hoverLeft = 2 + rng.nextDouble() * 3;
        if (next == null) {
            targetX = field.randomX();
            targetY = field.randomY();
        } else {
            double[] canopy = field.canopyOf(next);
            targetX = canopy[0] + (rng.nextDouble() - 0.5) * 24;
            targetY = canopy[1] + (rng.nextDouble() - 0.5) * 16;
        }
    }

    @Override
    public void draw(GraphicsContext g, double time) {
        drawShadow(g, FLY_HEIGHT, 6);
        // wings blur up and down very fast
        double flap = 1.5 + Math.abs(Math.sin(time * 60 + x)) * 2.5;

        boolean facingLeft = vx < 0;
        g.save();
        g.translate(x, y);
        if (facingLeft) g.scale(-1, 1);
        g.setFill(WING);
        g.fillOval(-2.5, -2 - flap, 4, flap + 1);
        g.fillOval(0, -1.5 - flap * 0.8, 3.5, flap * 0.8 + 1);
        // striped body
        g.setFill(BODY);
        g.fillOval(-4, -2.2, 8, 4.6);
        g.setFill(STRIPE);
        g.fillRect(-1.6, -2.0, 1.2, 4.2);
        g.fillRect(0.9, -2.0, 1.1, 4.2);
        g.fillOval(2.8, -1.4, 2.4, 2.8);       // head
        g.fillPolygon(new double[]{-4, -5.8, -4}, new double[]{-0.7, 0.1, 0.9}, 3);  // stinger
        g.setStroke(STRIPE);
        g.setLineWidth(0.5);
        g.strokeOval(-4, -2.2, 8, 4.6);
        g.restore();
    }
}

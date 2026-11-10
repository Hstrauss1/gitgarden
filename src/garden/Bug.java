package garden;

import java.util.Random;

import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;

/**
 * Something small that moves around the field. Subclasses decide where to go
 * ({@link #update}) and what they look like ({@link #draw}); this class handles
 * smooth steering and the shared drawing helpers.
 */
public abstract class Bug {

    private static final Color SHADOW = Color.web("#000000", 0.22);

    protected double x;
    protected double y;
    protected double vx;
    protected double vy;
    protected final Random rng;

    protected Bug(double x, double y, Random rng) {
        this.x = x;
        this.y = y;
        this.rng = rng;
    }

    /**
     * Moves the bug.
     *
     * @return false once the bug should be removed from the field
     */
    public abstract boolean update(double dt, Critters field);

    /** Draws the bug at (x, y); {@code time} is in seconds and drives wings and legs. */
    public abstract void draw(GraphicsContext g, double time);

    /** Turns gradually toward a point instead of snapping, so movement looks organic. */
    protected void steerToward(double tx, double ty, double speed, double agility, double dt) {
        double dx = tx - x;
        double dy = ty - y;
        double dist = Math.hypot(dx, dy);
        double wantX = dist < 0.001 ? 0 : dx / dist * speed;
        double wantY = dist < 0.001 ? 0 : dy / dist * speed;
        double blend = Math.min(1, agility * dt);
        vx += (wantX - vx) * blend;
        vy += (wantY - vy) * blend;
        x += vx * dt;
        y += vy * dt;
    }

    public double distanceTo(double px, double py) {
        return Math.hypot(px - x, py - y);
    }

    /** Direction of travel, in degrees, for rotating the sprite. */
    protected double headingDegrees() {
        return Math.toDegrees(Math.atan2(vy, vx));
    }

    /** A soft shadow on the ground under a flying bug. */
    protected void drawShadow(GraphicsContext g, double height, double size) {
        g.setFill(SHADOW);
        g.fillOval(x - size / 2 + height * 0.3, y + height - size / 4, size, size / 2);
    }
}

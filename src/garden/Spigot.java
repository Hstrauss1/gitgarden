package garden;

import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.scene.Cursor;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.RadialGradient;
import javafx.scene.paint.Stop;
import javafx.util.Duration;

/**
 * A little chrome tap with a four-armed cross handle, seen slightly from above.
 * Turning it (open/closed) animates the handle a quarter turn; the cap shows
 * blue while water flows and red when it is shut off.
 */
public final class Spigot extends Canvas {

    /** Where the stem meets the pipe, measured from the top of this canvas. */
    public static final double BASE_Y = 21;

    private static final double W = 30;
    private static final double H = 23;
    private static final double CX = W / 2;
    private static final double HUB_Y = 7;
    private static final double ARM = 10.5;        // arm length
    private static final double TILT = 0.42;       // how flat the handle looks (perspective)

    private static final double OPEN_ANGLE = 45;   // arms make an X, like the reference tap
    private static final double CLOSED_ANGLE = 0;  // arms make a +

    private static final Color OUTLINE = Color.web("#2b3136");
    private static final Color OPEN_CAP = Color.web("#3b8cff");
    private static final Color CLOSED_CAP = Color.web("#e0453a");

    private static final LinearGradient STEM = new LinearGradient(0, 0, 1, 0, true, CycleMethod.NO_CYCLE,
            new Stop(0, Color.web("#50585e")),
            new Stop(0.3, Color.web("#f7f9fa")),
            new Stop(0.55, Color.web("#a9b1b7")),
            new Stop(1, Color.web("#3f464b")));
    private static final LinearGradient ARM_STEEL = new LinearGradient(0, 0, 0, 1, true, CycleMethod.NO_CYCLE,
            new Stop(0, Color.web("#ffffff")),
            new Stop(0.45, Color.web("#b8c0c6")),
            new Stop(1, Color.web("#4a5258")));
    private static final RadialGradient BULB = new RadialGradient(0, 0, 0.35, 0.3, 0.75, true, CycleMethod.NO_CYCLE,
            new Stop(0, Color.web("#ffffff")),
            new Stop(0.45, Color.web("#bcc4ca")),
            new Stop(1, Color.web("#454c52")));

    private final DoubleProperty turn = new SimpleDoubleProperty(OPEN_ANGLE);
    private boolean open = true;

    public Spigot() {
        super(W, H);
        setCursor(Cursor.HAND);
        turn.addListener(o -> draw());
        hoverProperty().addListener(o -> draw());
        draw();
    }

    public void setOpen(boolean open) {
        if (this.open == open) return;
        this.open = open;
        new Timeline(new KeyFrame(Duration.millis(260),
                new KeyValue(turn, open ? OPEN_ANGLE : CLOSED_ANGLE))).play();
    }

    private void draw() {
        GraphicsContext g = getGraphicsContext2D();
        g.clearRect(0, 0, W, H);
        g.setLineWidth(0.6);
        g.setStroke(OUTLINE);

        // stem down to the pipe, with a collar ring
        g.setFill(STEM);
        g.fillRect(CX - 3.5, HUB_Y + 2, 7, BASE_Y - HUB_Y - 2);
        g.strokeRect(CX - 3.5, HUB_Y + 2, 7, BASE_Y - HUB_Y - 2);
        g.fillOval(CX - 5.5, BASE_Y - 7, 11, 4);
        g.strokeOval(CX - 5.5, BASE_Y - 7, 11, 4);

        // arms pointing away from the viewer first, then the hub, then the near arms
        double base = turn.get();
        for (int pass = 0; pass < 2; pass++) {
            for (int k = 0; k < 4; k++) {
                double a = Math.toRadians(base + k * 90);
                boolean near = Math.sin(a) > 0.001;
                if (near == (pass == 1)) {
                    drawArm(g, a);
                }
            }
            if (pass == 0) {
                drawHub(g);
            }
        }
    }

    /** A teardrop arm: a tapered shaft ending in a round bulb. */
    private void drawArm(GraphicsContext g, double a) {
        double dx = Math.cos(a);
        double dy = Math.sin(a) * TILT;
        double ex = CX + dx * ARM;
        double ey = HUB_Y + dy * ARM;
        // perpendicular for the shaft's width
        double len = Math.hypot(dx, dy);
        double px = -dy / len;
        double py = dx / len;

        g.setFill(ARM_STEEL);
        g.fillPolygon(
                new double[]{CX + px * 1.2, ex + px * 2.2, ex - px * 2.2, CX - px * 1.2},
                new double[]{HUB_Y + py * 1.2, ey + py * 2.2, ey - py * 2.2, HUB_Y - py * 1.2}, 4);
        g.strokePolygon(
                new double[]{CX + px * 1.2, ex + px * 2.2, ex - px * 2.2, CX - px * 1.2},
                new double[]{HUB_Y + py * 1.2, ey + py * 2.2, ey - py * 2.2, HUB_Y - py * 1.2}, 4);

        double r = 2.9;
        g.setFill(BULB);
        g.fillOval(ex - r, ey - r, r * 2, r * 2);
        g.strokeOval(ex - r, ey - r, r * 2, r * 2);
    }

    private void drawHub(GraphicsContext g) {
        g.setFill(BULB);
        g.fillOval(CX - 4, HUB_Y - 4, 8, 7);
        g.strokeOval(CX - 4, HUB_Y - 4, 8, 7);
        // hot/cold style cap dot
        Color cap = open ? OPEN_CAP : CLOSED_CAP;
        g.setFill(isHover() ? cap.brighter() : cap);
        g.fillOval(CX - 2, HUB_Y - 3, 4, 2.6);
    }
}

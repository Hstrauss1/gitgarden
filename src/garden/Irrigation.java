package garden;

import java.util.List;

import javafx.geometry.Bounds;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Stop;

/**
 * Overhead irrigation drawn on a canvas laid over the field: one straight metal pipe
 * runs fully across the top of each row of plots, through the fence on both sides,
 * with a nozzle over every plot that sprays while that plot's sprinkler is on.
 */
public final class Irrigation {

    public static final double PIPE = 6;           // pipe thickness
    private static final double ROW_OFFSET = 2;    // pipe top, below the top of the row
    private static final double FENCE = 6;         // fence border width on the field

    private static final Color OUTLINE = Color.web("#2b3136");
    private static final Color NOZZLE_OFF = Color.web("#4a535a");
    private static final Color NOZZLE_ON = Color.web("#5ab4ff");
    private static final Color WATER = Color.web("#9bdcff");
    private static final Color HEAT_GLOW = Color.web("#ff7a1a");
    private static final Color HEAT_WIRE = Color.web("#ffd27a");
    private static final Color MIST = Color.web("#9bdcff", 0.14);

    // brushed steel: highlight along the top edge, shadow underneath
    private static final LinearGradient STEEL = new LinearGradient(0, 0, 0, 1, true, CycleMethod.NO_CYCLE,
            new Stop(0, Color.web("#f2f5f7")),
            new Stop(0.35, Color.web("#b9c2c9")),
            new Stop(0.7, Color.web("#7f8a93")),
            new Stop(1, Color.web("#4d565d")));

    private static final int DROPLETS = 14;
    private static final double SPRAY_CYCLES_PER_SECOND = 1.8;

    private Irrigation() {
    }

    /** Where a row's pipe runs (its top edge), in field coordinates. */
    public static double pipeY(List<PlotController> row) {
        return row.get(0).getRoot().getBoundsInParent().getMinY() + ROW_OFFSET;
    }

    /**
     * @param pipes one pipe per row, in the same order as {@code rows}
     * @param rows  the plot views along each pipe
     */
    public static void render(Canvas canvas, List<Pipe> pipes, List<List<PlotController>> rows, long nanos,
                              boolean heating) {
        GraphicsContext g = canvas.getGraphicsContext2D();
        double width = canvas.getWidth();
        g.clearRect(0, 0, width, canvas.getHeight());

        for (int r = 0; r < rows.size(); r++) {
            List<PlotController> row = rows.get(r);
            if (row.isEmpty()) continue;
            double pipeY = pipeY(row);
            boolean open = pipes.get(r).isOpen();

            if (heating) {
                // heating cables buried along the beds glow orange while the heaters run
                double pulse = 0.55 + 0.25 * Math.sin(nanos / 1e9 * 3 + r);
                for (PlotController view : row) {
                    if (!view.getPlot().hasTree()) continue;
                    Bounds b = view.getRoot().getBoundsInParent();
                    double y = b.getMinY() + PlotController.BED_TOP + 9;
                    g.setStroke(HEAT_GLOW);
                    g.setGlobalAlpha(pulse * 0.5);
                    g.setLineWidth(5);
                    g.strokeLine(b.getMinX() + 10, y, b.getMaxX() - 10, y);
                    g.setGlobalAlpha(pulse);
                    g.setStroke(HEAT_WIRE);
                    g.setLineWidth(1.5);
                    g.strokeLine(b.getMinX() + 10, y, b.getMaxX() - 10, y);
                    g.setGlobalAlpha(1);
                }
            }

            // spray first, so it comes out from under the pipe
            for (PlotController view : row) {
                if (open && view.getPlot().isSprinklerOn()) {
                    drawSpray(g, view.getRoot().getBoundsInParent(), pipeY, nanos);
                }
            }

            // straight across, fence to fence and through it
            steelRect(g, 0, pipeY, width, PIPE);
            // flanges where the pipe passes through the fence on each side
            steelRect(g, FENCE - 2, pipeY - 2, 4, PIPE + 4);
            steelRect(g, width - FENCE - 2, pipeY - 2, 4, PIPE + 4);

            for (int i = 0; i < row.size(); i++) {
                Bounds b = row.get(i).getRoot().getBoundsInParent();
                if (i > 0) {
                    // a coupling in the gap between neighbouring plots
                    steelRect(g, b.getMinX() - 4.5, pipeY - 1, 3, PIPE + 2);
                }
                boolean spraying = open && row.get(i).getPlot().isSprinklerOn();
                drawNozzle(g, (b.getMinX() + b.getMaxX()) / 2, pipeY + PIPE, spraying);
            }
        }
    }

    private static void drawNozzle(GraphicsContext g, double cx, double y, boolean on) {
        steelRect(g, cx - 1.5, y, 3, 3);
        g.setFill(on ? NOZZLE_ON : NOZZLE_OFF);
        g.fillRect(cx - 2.5, y + 3, 5, 2);
        g.setStroke(OUTLINE);
        g.setLineWidth(0.75);
        g.strokeRect(cx - 2.5, y + 3, 5, 2);
    }

    /** A cone of mist plus droplets that fall from the nozzle and fan out onto the bed. */
    private static void drawSpray(GraphicsContext g, Bounds plot, double pipeY, long nanos) {
        double cx = (plot.getMinX() + plot.getMaxX()) / 2;
        double tipY = pipeY + PIPE + 5;
        double bedY = plot.getMinY() + PlotController.BED_TOP + 8;
        double spread = plot.getWidth() * 0.36;

        g.setFill(MIST);
        g.fillPolygon(new double[]{cx - 1.5, cx + 1.5, cx + spread, cx - spread},
                      new double[]{tipY, tipY, bedY, bedY}, 4);

        double time = nanos / 1e9 * SPRAY_CYCLES_PER_SECOND;
        g.setFill(WATER);
        for (int k = 0; k < DROPLETS; k++) {
            double t = (time + (double) k / DROPLETS) % 1.0;
            // a fixed sideways direction per droplet, spread evenly across the cone
            double side = ((k * 7) % DROPLETS) / (double) (DROPLETS - 1) * 2 - 1;
            double x = cx + side * spread * t;
            double y = tipY + t * (bedY - tipY);
            g.setGlobalAlpha(0.95 - 0.5 * t);
            g.fillOval(x - 0.75, y, 1.5, 3);
        }
        g.setGlobalAlpha(1);
    }

    private static void steelRect(GraphicsContext g, double x, double y, double w, double h) {
        g.setFill(STEEL);
        g.fillRect(x, y, w, h);
        g.setStroke(OUTLINE);
        g.setLineWidth(0.75);
        g.strokeRect(x, y, w, h);
    }
}

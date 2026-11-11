package garden;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import javafx.animation.AnimationTimer;
import javafx.application.HostServices;
import javafx.collections.ListChangeListener;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.Slider;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.TilePane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Window;

/**
 * Controller for garden.fxml: builds the garden, draws it every frame, and turns
 * clicks into actions. The simulation itself lives in {@link Garden}.
 */
public class GardenController {

    private static final int PLOT_COUNT = 40;
    /** Plots per row; must match prefColumns on the TilePane in garden.fxml. */
    private static final int COLUMNS = 8;
    /** Speed presets; at 256x a year (360 days of 10 s) takes about 14 seconds. */
    private static final int[] SPEEDS = {1, 4, 16, 64, 256};
    /** The simulation advances in steps no longer than this, so high speeds stay accurate. */
    private static final double MAX_STEP = 0.25;
    /** Bugs move at most this much faster than real time, so they don't teleport. */
    private static final double MAX_BUG_SPEED = 4;
    /** Above this speed days flash by too fast to show night. */
    private static final int MAX_SPEED_FOR_NIGHT = 4;
    /** Seconds between refreshes of the text in the sidebar and status bar. */
    private static final double TEXT_REFRESH = 0.25;

    @FXML private StackPane lawn;
    @FXML private TilePane plotPane;
    @FXML private Canvas pipeCanvas;
    @FXML private Pane valveLayer;
    @FXML private StackPane fieldStack;
    @FXML private Canvas bugCanvas;
    @FXML private Canvas weatherCanvas;
    @FXML private Button pauseButton;
    @FXML private ChoiceBox<String> speedChoice;
    @FXML private Slider lifeSlider;
    @FXML private Slider multiplierSlider;
    @FXML private Label lifeValue;
    @FXML private Label multiplierValue;
    @FXML private Label statusLabel;
    @FXML private Label dateLabel;
    @FXML private Label weatherLabel;
    @FXML private Label sensorLabel;
    @FXML private Label totalsLabel;
    @FXML private VBox modulesBox;
    @FXML private ChoiceBox<String> logCategory;
    @FXML private ChoiceBox<String> logLevel;
    @FXML private CheckBox logFollow;
    @FXML private TextField logSearch;
    @FXML private ListView<GardenLog.Entry> logList;
    @FXML private Label logCountLabel;
    @FXML private Label logFileLabel;

    private final Garden garden = new Garden();
    private final List<PlotController> plotViews = new ArrayList<PlotController>();
    private final List<List<PlotController>> pipeRows = new ArrayList<List<PlotController>>();
    private final List<Spigot> valves = new ArrayList<Spigot>();
    private final List<Label> moduleStatus = new ArrayList<Label>();
    private final Critters critters = new Critters(plotViews);
    private PestSensor pestSensor;
    private FilteredList<GardenLog.Entry> filteredLog;
    private HostServices hostServices;

    private AnimationTimer timer;
    private long lastFrame;
    private double sinceText = TEXT_REFRESH;
    private double sinceFlush;
    private boolean paused;
    private boolean logChanged;

    @FXML
    private void initialize() {
        GardenLog.start(garden.getClock());
        garden.setCritters(critters);
        pestSensor = new PestSensor(garden);

        for (int speed : SPEEDS) {
            speedChoice.getItems().add(speed + "x");
        }
        speedChoice.getSelectionModel().selectFirst();
        speedChoice.getSelectionModel().selectedItemProperty().addListener((o, was, now) ->
                GardenLog.info(GardenLog.Category.USER, "Speed changed from %s to %s", was, now));

        lifeValue.textProperty().bind(lifeSlider.valueProperty().asString("%.0f"));
        multiplierValue.textProperty().bind(multiplierSlider.valueProperty().asString("%.0f"));
        lifeSlider.valueProperty().addListener((o, was, now) -> {
            if (Math.round(was.doubleValue()) == Math.round(now.doubleValue())) return;
            garden.setBaseLife((int) Math.round(now.doubleValue()));
            GardenLog.info(GardenLog.Category.USER, "Life (tree size) set to %d for new trees", Math.round(now.doubleValue()));
        });
        multiplierSlider.valueProperty().addListener((o, was, now) -> {
            if (Math.round(was.doubleValue()) == Math.round(now.doubleValue())) return;
            garden.setBaseMultiplier((int) Math.round(now.doubleValue()));
            GardenLog.info(GardenLog.Category.USER, "Multiplier (branching) set to %d for new trees", Math.round(now.doubleValue()));
        });

        // textured grass: a lighter lawn outside the fence, a darker field inside it
        lawn.setBackground(GrassTexture.background(Color.web("#6aa84f"), 1));
        plotPane.setBackground(GrassTexture.background(Color.web("#4f8a3a"), 2));

        // the overlays cover the field exactly; the weather covers the whole lawn
        pipeCanvas.widthProperty().bind(plotPane.widthProperty());
        pipeCanvas.heightProperty().bind(plotPane.heightProperty());
        bugCanvas.widthProperty().bind(plotPane.widthProperty());
        bugCanvas.heightProperty().bind(plotPane.heightProperty());
        weatherCanvas.widthProperty().bind(lawn.widthProperty());
        weatherCanvas.heightProperty().bind(lawn.heightProperty());

        // a click on a cockroach stomps it instead of watering the plot underneath
        fieldStack.addEventFilter(MouseEvent.MOUSE_CLICKED, e -> {
            if (paused) return;
            if (critters.pokeAt(e.getX(), e.getY())) {
                e.consume();
            } else if (critters.stompAt(e.getX(), e.getY())) {
                garden.noteStomped();
                e.consume();
            }
        });

        buildField();
        buildModuleCards();
        buildLogView();
        GardenLog.info(GardenLog.Category.SYSTEM, "Garden ready: %d plots in %d rows, %d automated modules",
                garden.getPlots().size(), garden.getPipes().size(), garden.getModules().size());

        timer = new AnimationTimer() {
            @Override
            public void handle(long now) {
                // nothing that goes wrong in one frame is allowed to stop the garden
                try {
                    double dt = lastFrame == 0 ? 0 : (now - lastFrame) / 1e9;
                    lastFrame = now;
                    update(Math.min(dt, 0.1), now);
                } catch (Throwable problem) {
                    GardenLog.error(GardenLog.Category.SYSTEM, "Frame update", problem);
                }
            }
        };
        timer.start();
    }

    public void setHostServices(HostServices hostServices) {
        this.hostServices = hostServices;
    }

    // ---- building the window ----

    private void buildField() {
        Random random = new Random();
        Pipe pipe = null;
        for (int i = 0; i < PLOT_COUNT; i++) {
            if (i % COLUMNS == 0) {
                if (pipe != null) garden.addPipe(pipe);
                pipe = addPipe("Row " + (pipeRows.size() + 1));
            }
            // stagger starting moisture so the sprinklers don't all fire together
            Plot plot = new Plot("Plot " + (i + 1), 40 + random.nextInt(55));
            garden.replant(plot);
            pipe.add(plot);
            plotPane.getChildren().add(loadPlotView(plot));
            pipeRows.get(pipeRows.size() - 1).add(plotViews.get(i));
        }
        garden.addPipe(pipe);
    }

    /** A pipe plus its spigot; clicking the spigot opens or closes the pipe. */
    private Pipe addPipe(String name) {
        final Pipe pipe = new Pipe(name);
        final Spigot valve = new Spigot();
        final Tooltip tip = new Tooltip();
        Tooltip.install(valve, tip);
        valve.getStyleClass().add("valve");
        valve.setOnMouseClicked(e -> {
            pipe.toggle();
            updateValve(pipe, valve, tip);
            GardenLog.info(GardenLog.Category.USER, "%s spigot turned %s: that row's sprinklers %s",
                    pipe.getName(), pipe.isOpen() ? "ON" : "OFF", pipe.isOpen() ? "can run again" : "are cut off");
        });
        updateValve(pipe, valve, tip);

        pipeRows.add(new ArrayList<PlotController>());
        valves.add(valve);
        valveLayer.getChildren().add(valve);
        return pipe;
    }

    private static void updateValve(Pipe pipe, Spigot valve, Tooltip tip) {
        valve.setOpen(pipe.isOpen());
        tip.setText(pipe.getName() + " pipe: " + (pipe.isOpen() ? "open" : "closed") + ". Click to "
                + (pipe.isOpen() ? "shut it off" : "turn it on"));
    }

    /** Each plot has its own small FXML view (plot.fxml) and controller. */
    private Node loadPlotView(Plot plot) {
        try {
            FXMLLoader loader = new FXMLLoader(GardenController.class.getResource("plot.fxml"));
            Node node = loader.load();
            PlotController view = loader.getController();
            view.bind(plot, this);
            plotViews.add(view);
            return node;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** One card per module: an on/off checkbox, what it does, and a live status line. */
    private void buildModuleCards() {
        for (final GardenModule module : garden.getModules()) {
            CheckBox toggle = new CheckBox(module.getName());
            toggle.setSelected(module.isEnabled());
            toggle.getStyleClass().add("module-name");
            toggle.selectedProperty().addListener((o, was, on) -> module.setEnabled(on, garden));

            Label description = new Label(module.getDescription());
            description.setWrapText(true);
            description.getStyleClass().add("module-description");

            Label status = new Label();
            status.setWrapText(true);
            status.getStyleClass().add("module-status");
            moduleStatus.add(status);

            VBox card = new VBox(4, toggle, description, status);
            card.getStyleClass().add("card");
            modulesBox.getChildren().add(card);
        }
    }

    private void buildLogView() {
        logCategory.getItems().add("All categories");
        for (GardenLog.Category category : GardenLog.Category.values()) {
            logCategory.getItems().add(category.name());
        }
        logCategory.getSelectionModel().selectFirst();
        logLevel.getItems().addAll("All levels", "WARN and worse", "ALERT and worse", "ERROR only");
        logLevel.getSelectionModel().selectFirst();

        filteredLog = new FilteredList<GardenLog.Entry>(GardenLog.getRecent());
        logList.setItems(filteredLog);
        // a fixed row height keeps the list fast while thousands of entries stream in
        logList.setFixedCellSize(52);
        logList.setCellFactory(list -> new LogCell(list));
        logCategory.valueProperty().addListener(o -> refilterLog());
        logLevel.valueProperty().addListener(o -> refilterLog());
        logSearch.textProperty().addListener(o -> refilterLog());
        // scrolling on every new entry floods the list at high speed; refreshText scrolls a few times a second
        filteredLog.addListener((ListChangeListener<GardenLog.Entry>) change -> logChanged = true);
        logFileLabel.setText(GardenLog.getCurrentFile() == null ? "Log file unavailable (memory only)"
                : "Full log: " + GardenLog.getCurrentFile().getAbsolutePath());
    }

    private void refilterLog() {
        final String category = logCategory.getValue();
        final int minLevel = Math.max(0, logLevel.getSelectionModel().getSelectedIndex());
        final String search = logSearch.getText() == null ? "" : logSearch.getText().trim().toLowerCase();
        filteredLog.setPredicate(entry ->
                (category == null || category.startsWith("All") || entry.category.name().equals(category))
                        && entry.level.ordinal() >= minLevel
                        && (search.isEmpty() || entry.toLine().toLowerCase().contains(search)));
    }

    /** Log rows coloured by level: when and what on top, the message wrapped below it. The real time is in the file. */
    private static final class LogCell extends ListCell<GardenLog.Entry> {
        private final Label header = new Label();
        private final Label message = new Label();
        private final VBox box = new VBox(1, header, message);

        LogCell(ListView<GardenLog.Entry> list) {
            header.getStyleClass().add("log-header");
            message.getStyleClass().add("log-message");
            message.setWrapText(true);
            message.setMaxHeight(30);
            message.prefWidthProperty().bind(list.widthProperty().subtract(32));
            setContentDisplay(javafx.scene.control.ContentDisplay.GRAPHIC_ONLY);
        }

        @Override
        protected void updateItem(GardenLog.Entry entry, boolean empty) {
            super.updateItem(entry, empty);
            getStyleClass().removeAll("log-warn", "log-alert", "log-error");
            if (empty || entry == null) {
                setGraphic(null);
                setTooltip(null);
                return;
            }
            header.setText(entry.gameTime + "   " + entry.level + "   " + entry.category);
            message.setText(entry.message);
            setGraphic(box);
            setTooltip(new Tooltip(entry.toLine()));
            if (entry.level == GardenLog.Level.WARN) getStyleClass().add("log-warn");
            if (entry.level == GardenLog.Level.ALERT) getStyleClass().add("log-alert");
            if (entry.level == GardenLog.Level.ERROR) getStyleClass().add("log-error");
        }
    }

    // ---- the frame loop ----

    private int getSpeed() {
        return SPEEDS[Math.max(0, speedChoice.getSelectionModel().getSelectedIndex())];
    }

    private void update(double dt, long now) {
        int speed = getSpeed();
        if (!paused) {
            // advance the farm in small steps so 256x behaves like 1x, just faster
            double remaining = dt * speed;
            while (remaining > 0) {
                double step = Math.min(MAX_STEP, remaining);
                remaining -= step;
                garden.step(step);
            }
            try {
                critters.update(dt * Math.min(speed, MAX_BUG_SPEED), bugCanvas.getWidth(), bugCanvas.getHeight(),
                        garden.getClock().getSeason());
            } catch (RuntimeException e) {
                GardenLog.error(GardenLog.Category.WILDLIFE, "Bug movement", e);
            }
        }

        Weather sky = garden.getWeather().getCurrent();
        for (PlotController view : plotViews) {
            try {
                view.render(now, sky);
            } catch (RuntimeException e) {
                GardenLog.error(GardenLog.Category.SYSTEM, "Drawing " + view.getPlot().getName(), e);
            }
        }
        Irrigation.render(pipeCanvas, garden.getPipes(), pipeRows, now, garden.getHeating().isHeating());
        critters.render(bugCanvas, now);
        WeatherView.render(weatherCanvas, sky, garden.getClock(), now, speed <= MAX_SPEED_FOR_NIGHT);
        // a spigot stands on each pipe just inside the left fence
        for (int r = 0; r < valves.size(); r++) {
            double pipeMiddle = Irrigation.pipeY(pipeRows.get(r)) + Irrigation.PIPE / 2;
            valves.get(r).relocate(0, pipeMiddle - Spigot.BASE_Y);
        }

        sinceText += dt;
        if (sinceText >= TEXT_REFRESH) {
            sinceText = 0;
            refreshText(sky);
        }
        sinceFlush += dt;
        if (sinceFlush >= 1) {
            sinceFlush = 0;
            GardenLog.flush();
        }
    }

    private void refreshText(Weather sky) {
        int growing = 0;
        int wilting = 0;
        int dead = 0;
        int empty = 0;
        for (Plot plot : garden.getPlots()) {
            switch (plot.getState()) {
                case WILTING: wilting++; break;
                case DEAD: dead++; break;
                case EMPTY: empty++; break;
                default: growing++;
            }
        }
        int alive = growing + wilting;
        statusLabel.setText(String.format("%s   |   Alive: %d / %d   |   Wilting: %d   |   Dead: %d   |   Empty: %d   |   Harvested: %d   |   Cockroaches: %d   |   Errors recovered: %d",
                paused ? "PAUSED" : "Running", alive, garden.getPlots().size(), wilting, dead, empty,
                garden.getHarvested(), critters.getRoachCount(), GardenLog.getErrorCount()));

        GameClock clock = garden.getClock();
        dateLabel.setText(clock.format());
        weatherLabel.setText(sky == Weather.CLEAR
                ? "Weather: Clear"
                : String.format("Weather: %s (%.1f days left)", sky.label, garden.getWeather().getTimeLeft() / GameClock.SECONDS_PER_DAY));

        double air = garden.getAirTemperature();
        sensorLabel.setText(String.format(
                "Air temperature:  %.1f°C%s%nAverage soil moisture:  %.0f%%%nPests feeding:  %d   (on the field: %d)%nSeason:  %s",
                air, air < 0 ? "  (freezing!)" : "",
                garden.getWatering().getAverageSoil(), (int) pestSensor.read(), critters.getRoachCount(),
                clock.getSeason().label));
        for (int i = 0; i < moduleStatus.size(); i++) {
            moduleStatus.get(i).setText(garden.getModules().get(i).getStatus());
        }
        totalsLabel.setText(String.format(
                "Harvested:  %d%nTrees died:  %d%nDead trees chopped by hand:  %d%nCockroaches stomped by hand:  %d%nCockroaches killed by pest control:  %d%nWater used:  %.0f L",
                garden.getHarvested(), garden.getDeaths(), garden.getFelled(), garden.getStomped(),
                garden.getPestControl().getKilled(), garden.getWatering().getLitres()));
        if (logChanged && logFollow.isSelected() && !filteredLog.isEmpty()) {
            logList.scrollTo(filteredLog.size() - 1);
        }
        logChanged = false;
        logCountLabel.setText(String.format("Showing %d of the last %d entries", filteredLog.size(), GardenLog.getRecent().size()));
    }

    // ---- what clicking on a plot does ----

    public void waterByHand(Plot plot) {
        if (plot.water()) {
            GardenLog.info(GardenLog.Category.USER, "%s: threw water on the fire and put it out (health %.0f%%)",
                    plot.getName(), plot.getHealth());
        } else {
            GardenLog.info(GardenLog.Category.USER, "%s watered by hand (soil now %.0f%%)", plot.getName(), plot.getMoisture());
        }
    }

    public void harvestByHand(Plot plot) {
        garden.harvest(plot, true);
    }

    public void chopByHand(Plot plot) {
        boolean felled = plot.chop();
        GardenLog.info(GardenLog.Category.USER, "%s: chopped at the dead tree (%d/%d)%s", plot.getName(),
                Math.min(plot.getChops(), Plot.CHOPS_TO_FELL), Plot.CHOPS_TO_FELL, felled ? ", it fell" : "");
        if (felled) garden.noteFelled();
    }

    public void plantByHand(Plot plot) {
        GardenLog.info(GardenLog.Category.USER, "%s: planting a new bonsai by hand", plot.getName());
        garden.replant(plot);
    }

    public GameClock getClock() {
        return garden.getClock();
    }

    // ---- toolbar and menus ----

    @FXML
    private void onTogglePause() {
        paused = !paused;
        pauseButton.setText(paused ? "Resume" : "Pause");
        GardenLog.info(GardenLog.Category.USER, "Simulation %s", paused ? "PAUSED" : "resumed");
    }

    @FXML
    private void onReplantAll() {
        GardenLog.info(GardenLog.Category.USER, "Replant All: every plot gets a new bonsai");
        for (Plot plot : garden.getPlots()) {
            garden.replant(plot);
        }
    }

    @FXML private void onClear() { setWeather(Weather.CLEAR); }
    @FXML private void onRain() { setWeather(Weather.RAIN); }
    @FXML private void onDrought() { setWeather(Weather.DROUGHT); }
    @FXML private void onWindstorm() { setWeather(Weather.WINDSTORM); }

    private void setWeather(Weather weather) {
        GardenLog.info(GardenLog.Category.USER, "Weather changed by hand to %s", weather.label);
        garden.getWeather().set(weather);
    }

    @FXML
    private void onSummonRider() {
        GardenLog.info(GardenLog.Category.USER, "Summoned a Hellfire Rider");
        if (!critters.summonRider()) {
            GardenLog.info(GardenLog.Category.USER, "No Hellfire Rider came (one is already here, or there are no trees)");
        }
    }

    @FXML
    private void onSummonBrute() {
        GardenLog.info(GardenLog.Category.USER, "Summoned a Green Brute");
        if (!critters.summonBrute()) {
            GardenLog.info(GardenLog.Category.USER, "No Green Brute came (one is already here, or there are no trees)");
        }
    }

    @FXML
    private void onHelp() {
        Window owner = lawn.getScene() == null ? null : lawn.getScene().getWindow();
        HelpWindow.show(owner);
        GardenLog.info(GardenLog.Category.USER, "Opened the help manual");
    }

    @FXML
    private void onOpenLogFolder() {
        if (hostServices != null && GardenLog.getDirectory() != null) {
            hostServices.showDocument(GardenLog.getDirectory().toURI().toString());
        }
    }

    public void shutdown() {
        if (timer != null) timer.stop();
        GardenLog.info(GardenLog.Category.SYSTEM, "Garden closed");
        GardenLog.stop();
    }
}

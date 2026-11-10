package garden;

/**
 * A standalone automated subsystem of the garden (watering, heating, pest control,
 * replanting). Each one reads its own sensors and acts on its own; any of them can be
 * switched off from the Systems tab to see how the garden copes without it.
 */
public abstract class GardenModule {

    private final String name;
    private final String description;
    private final GardenLog.Category category;
    private boolean enabled = true;

    protected GardenModule(String name, String description, GardenLog.Category category) {
        this.name = name;
        this.description = description;
        this.category = category;
    }

    /** Runs one step if the module is switched on. */
    public final void update(double dt, Garden garden) {
        if (enabled) {
            run(dt, garden);
        }
    }

    /** What the module does each simulation step. */
    protected abstract void run(double dt, Garden garden);

    /** Called once when the module is switched off, to shut its hardware down. */
    protected void shutDown(Garden garden) {
    }

    /** One line for the Systems tab. */
    public abstract String getStatus();

    public void setEnabled(boolean on, Garden garden) {
        if (on == enabled) return;
        enabled = on;
        GardenLog.info(GardenLog.Category.USER, "%s switched %s", name, on ? "ON" : "OFF");
        if (!on) shutDown(garden);
    }

    public boolean isEnabled() { return enabled; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    protected GardenLog.Category getCategory() { return category; }
}

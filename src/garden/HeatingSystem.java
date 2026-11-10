package garden;

/**
 * Heating cables along every row of beds. A thermometer switches them on when the air
 * drops below 3°C and off above 6°C; while on they keep the beds 8°C warmer than the
 * air, which saves frost-sensitive trees on winter nights.
 */
public class HeatingSystem extends GardenModule {

    public static final double ON_BELOW = 3;
    public static final double OFF_ABOVE = 6;
    /** How much warmer the beds are than the air while the heaters run. */
    public static final double BOOST = 8;
    /** Power of all the heating cables together. */
    private static final double KILOWATTS = 6;

    private final TemperatureSensor sensor;
    private boolean heatersOn;
    private double kilowattHours;
    private double lastReading;
    private boolean warnedFrost;

    public HeatingSystem(Garden garden) {
        super("Heating system", "A thermometer turns the bed heaters on below " + (int) ON_BELOW
                + "°C and off above " + (int) OFF_ABOVE + "°C to protect trees from frost.", GardenLog.Category.HEATING);
        sensor = new TemperatureSensor(garden);
    }

    @Override
    protected void run(double dt, Garden garden) {
        lastReading = sensor.read();
        if (!heatersOn && lastReading < ON_BELOW) {
            heatersOn = true;
            GardenLog.info(getCategory(), "Air %.1f°C (below %.0f°C): bed heaters ON", lastReading, ON_BELOW);
        } else if (heatersOn && lastReading > OFF_ABOVE) {
            heatersOn = false;
            GardenLog.info(getCategory(), "Air %.1f°C: bed heaters OFF (used %.1f kWh so far)", lastReading, kilowattHours);
        }
        if (heatersOn) {
            kilowattHours += KILOWATTS * GameClock.hoursIn(dt);
        }
    }

    /** Called every step even when switched off, to warn about unprotected frost. */
    public void checkFrost(Garden garden) {
        double air = garden.getAirTemperature();
        if (air < 0 && !heatersOn && !warnedFrost) {
            warnedFrost = true;
            GardenLog.alert(getCategory(), "FROST: air is %.1f°C and the heaters are off; cherry and maple trees are freezing", air);
        } else if (air > 2) {
            warnedFrost = false;
        }
    }

    @Override
    protected void shutDown(Garden garden) {
        if (heatersOn) {
            heatersOn = false;
            GardenLog.warn(getCategory(), "Heating switched off: bed heaters OFF");
        }
    }

    /** Temperature at the beds: the air, plus the heaters' boost while they run. */
    public double bedTemperature(double air) {
        return heatersOn ? air + BOOST : air;
    }

    @Override
    public String getStatus() {
        if (!isEnabled()) return "OFF - beds get no frost protection";
        return String.format("Air %.1f°C · heaters %s · %.1f kWh used", lastReading, heatersOn ? "ON" : "off", kilowattHours);
    }

    public boolean isHeating() { return heatersOn; }
}

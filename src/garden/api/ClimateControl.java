package garden.api;

import garden.Garden;
import garden.GardenLog;
import garden.GardenModule;

/**
 * The temperature response module for API runs. A thermometer reads the day's air
 * temperature: below 3°C (37°F) heating cables warm the beds by 8°C, and above 35°C
 * (95°F) a shade cloth and misting cool them by 6°C. Very hot days (above about 115°F)
 * are still too much, and trees suffer heat stress.
 */
public final class ClimateControl extends GardenModule {

    public static final double HEAT_BELOW_C = 3;
    public static final double HEATING_BOOST = 8;
    public static final double SHADE_ABOVE_C = 35;
    public static final double SHADE_COOLING = 6;

    private final ApiGarden owner;
    private double air = Double.NaN;
    private boolean heating;
    private boolean shading;

    ClimateControl(ApiGarden owner) {
        super("Climate control", "Heats the beds below 37°F and shades and mists them above 95°F.", GardenLog.Category.HEATING);
        this.owner = owner;
    }

    /** The air temperature the thermometer reads this step, in °C. */
    void setAirTemperature(double celsius) {
        air = celsius;
    }

    @Override
    protected void run(double dt, Garden garden) {
        if (Double.isNaN(air)) return;
        boolean wantHeat = air < HEAT_BELOW_C;
        boolean wantShade = air > SHADE_ABOVE_C;
        if (wantHeat != heating) {
            heating = wantHeat;
            owner.log().info(owner.currentDay(), "CLIMATE", heating ? "heating ON" : "heating off",
                    String.format("Air %.0f°F: bed heating cables %s", ApiGarden.toFahrenheit(air), heating ? "on (+8°C at the beds)" : "off"));
        }
        if (wantShade != shading) {
            shading = wantShade;
            owner.log().info(owner.currentDay(), "CLIMATE", shading ? "shade ON" : "shade off",
                    String.format("Air %.0f°F: shade cloth and misting %s", ApiGarden.toFahrenheit(air), shading ? "on (-6°C at the beds)" : "off"));
        }
    }

    @Override
    protected void shutDown(Garden garden) {
        heating = false;
        shading = false;
    }

    /** Temperature at the beds in °C, after heating or shading. */
    double bedTemperature(double airCelsius) {
        if (!isEnabled()) return airCelsius;
        if (heating) return airCelsius + HEATING_BOOST;
        if (shading) return airCelsius - SHADE_COOLING;
        return airCelsius;
    }

    @Override
    public String getStatus() {
        if (!isEnabled()) return "OFF - no frost or heat protection";
        return String.format("Air %.0f°F · heating %s · shade %s", Double.isNaN(air) ? Double.NaN : ApiGarden.toFahrenheit(air),
                heating ? "ON" : "off", shading ? "ON" : "off");
    }

    public boolean isHeating() { return heating; }
    public boolean isShading() { return shading; }
}

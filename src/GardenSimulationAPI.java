/**
 * The garden API in the default package, so a test script with no package and no imports
 * (like the example in "Gardening System APIs") can write {@code new GardenSimulationAPI()}.
 * Everything is implemented in {@link garden.api.GardenSimulationAPI}; a script inside a
 * package can import that class instead.
 */
public class GardenSimulationAPI extends garden.api.GardenSimulationAPI {

    public GardenSimulationAPI() {
        super();
    }

    /** @param dayMillis real milliseconds per simulated day (the default is one hour) */
    public GardenSimulationAPI(long dayMillis) {
        super(dayMillis);
    }

    public GardenSimulationAPI(Options options) {
        super(options);
    }
}

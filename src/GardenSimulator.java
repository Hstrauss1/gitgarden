import java.util.HashMap;
import java.util.List;
import java.util.Random;

/**
 * A test run of the garden API, shaped like the example script in "Gardening System APIs":
 * initialize, then every day randomly call rain(), temperature() or parasite(), sleep one
 * day, and call getState() at the end.
 *
 * <pre>
 * java -cp out GardenSimulator                 24 days of one real hour each (the graders' run)
 * java -cp out GardenSimulator 24 5000         24 days of 5 seconds each (a quick check)
 * java -cp out GardenSimulator 24 5000 42      ... with random seed 42, so the run can be repeated
 * </pre>
 *
 * Everything is written to log.txt in the working directory.
 */
public class GardenSimulator {

    public static void main(String[] args) throws InterruptedException {
        int days = args.length > 0 ? Integer.parseInt(args[0]) : 24;
        long dayMillis = args.length > 1 ? Long.parseLong(args[1]) : garden.api.GardenSimulationAPI.REAL_DAY_MILLIS;
        Random random = args.length > 2 ? new Random(Long.parseLong(args[2])) : new Random();

        GardenSimulationAPI gardenAPI = new GardenSimulationAPI(dayMillis);

        // beginning of the simulation
        gardenAPI.initializeGarden();   // this marks the beginning of the clock
        HashMap<String, Object> initialPlantDetails = gardenAPI.getPlants();
        @SuppressWarnings("unchecked")
        List<Integer> water = (List<Integer>) initialPlantDetails.get("waterRequirement");
        @SuppressWarnings("unchecked")
        List<List<String>> parasites = (List<List<String>>) initialPlantDetails.get("parasites");
        int maxWater = 0;
        for (int need : water) maxWater = Math.max(maxWater, need);

        long start = System.currentTimeMillis();
        for (int day = 1; day <= days; day++) {
            // one or two random events a day, like the graders' script
            int events = 1 + random.nextInt(2);
            for (int e = 0; e < events; e++) {
                switch (random.nextInt(3)) {
                    case 0:
                        gardenAPI.rain(random.nextInt(2 * maxWater + 1));
                        break;
                    case 1:
                        gardenAPI.temperature(40 + random.nextInt(81));
                        break;
                    default:
                        List<String> options = parasites.get(random.nextInt(parasites.size()));
                        gardenAPI.parasite(options.get(random.nextInt(options.size())));
                }
            }
            // sleep until the end of this day (measured from the start, so the days don't drift)
            long wake = start + day * dayMillis;
            long now;
            while ((now = System.currentTimeMillis()) < wake) {
                Thread.sleep(Math.min(wake - now, 60_000));
            }
        }

        // after the last day
        gardenAPI.getState();
        gardenAPI.shutdown();
    }
}

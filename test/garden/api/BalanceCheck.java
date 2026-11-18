package garden.api;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicLong;

import garden.Plot;

/**
 * Not a unit test: plays many random 24-day API runs on a fake clock, like the graders'
 * script, and reports how much damage is still visible at the end for several healing
 * rates. Run it with:
 *
 * <pre>
 * java -cp out:out-test garden.api.BalanceCheck [runs] [heal per day ...]
 * </pre>
 */
public final class BalanceCheck {

    private static final long DAY = 1000;
    /** Healing per simulated day to compare; 20 is the current default. */
    private static final double[] HEAL_PER_DAY = {20, 10, 6, 4, 2};

    public static void main(String[] args) throws IOException {
        int runs = args.length > 0 ? Integer.parseInt(args[0]) : 200;
        double[] rates = HEAL_PER_DAY;
        if (args.length > 1) {
            rates = new double[args.length - 1];
            for (int i = 1; i < args.length; i++) rates[i - 1] = Double.parseDouble(args[i]);
        }
        File folder = Files.createTempDirectory("balance").toFile();
        System.out.printf("%d random 24-day runs per healing rate (1-2 random events a day, like the graders' script)%n%n", runs);
        System.out.println("heal/day | plants alive | avg final health | plants < 100% | plants < 90% | lowest final | lowest ever | runs with a death");
        for (double perDay : rates) {
            Stats stats = new Stats();
            for (int run = 0; run < runs; run++) {
                play(folder, perDay, run, stats);
            }
            System.out.printf("%8.0f | %11.1f%% | %15.1f%% | %12.1f%% | %11.1f%% | %11.0f%% | %10.0f%% | %d of %d%n",
                    perDay, 100.0 * stats.alive / stats.plants, stats.healthTotal / Math.max(1, stats.alive),
                    100.0 * stats.below100 / stats.plants, 100.0 * stats.below90 / stats.plants,
                    stats.lowestFinal, stats.lowestEver, stats.runsWithDeath, runs);
            if (!stats.causes.isEmpty()) System.out.println("         deaths by cause and species: " + stats.causes);
        }
    }

    private static void play(File folder, double healPerDay, int seed, Stats stats) {
        AtomicLong now = new AtomicLong();
        GardenSimulationAPI api = new GardenSimulationAPI(new GardenSimulationAPI.Options()
                .dayMillis(DAY).clock(now::get).directory(folder).background(false).echo(false).seed(seed)
                .recoveryRate(healPerDay / garden.GameClock.SECONDS_PER_DAY));
        api.initializeGarden();
        Random random = new Random(seed * 31L + 7);
        @SuppressWarnings("unchecked")
        List<List<String>> parasites = (List<List<String>>) api.getPlants().get("parasites");
        double lowestEver = 100;
        for (int day = 1; day <= 24; day++) {
            int events = 1 + random.nextInt(2);
            for (int e = 0; e < events; e++) {
                switch (random.nextInt(3)) {
                    case 0: api.rain(random.nextInt(51)); break;
                    case 1: api.temperature(40 + random.nextInt(81)); break;
                    default:
                        List<String> options = parasites.get(random.nextInt(parasites.size()));
                        api.parasite(options.get(random.nextInt(options.size())));
                }
            }
            for (int tick = 0; tick < ApiGarden.TICKS_PER_DAY; tick++) {
                now.addAndGet(DAY / ApiGarden.TICKS_PER_DAY);
                api.catchUp();
                for (Plot plot : api.garden().getPlots()) {
                    lowestEver = Math.min(lowestEver, plot.getHealth());
                }
            }
        }
        boolean death = false;
        double lowestFinal = 100;
        for (Plot plot : api.garden().getPlots()) {
            stats.plants++;
            if (!plot.hasTree()) {
                death = true;
                String cause = plot.getSpecies().displayName + ": " + plot.getLastDamage().replace(" infestation", "");
                Integer n = stats.causes.get(cause);
                stats.causes.put(cause, n == null ? 1 : n + 1);
                continue;
            }
            stats.alive++;
            stats.healthTotal += plot.getHealth();
            if (plot.getHealth() < 99.5) stats.below100++;
            if (plot.getHealth() < 90) stats.below90++;
            lowestFinal = Math.min(lowestFinal, plot.getHealth());
        }
        if (death) stats.runsWithDeath++;
        stats.lowestFinal = Math.min(stats.lowestFinal, lowestFinal);
        stats.lowestEver = Math.min(stats.lowestEver, lowestEver);
        api.shutdown();
    }

    private static final class Stats {
        int plants;
        int alive;
        double healthTotal;
        int below100;
        int below90;
        int runsWithDeath;
        double lowestFinal = 100;
        double lowestEver = 100;
        final Map<String, Integer> causes = new TreeMap<String, Integer>();
    }
}

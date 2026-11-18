# Garden Simulation API

The scripted, GUI-free part of the project, as described in *Gardening System APIs*. A test
script creates `GardenSimulationAPI`, calls `initializeGarden()`, then calls `rain()`,
`temperature()` or `parasite()` once a day (one real hour), and calls `getState()` after 24 days.
Everything is logged to `log.txt`.

## Running it

From the project folder (the JDK needs JavaFX only to *compile* the GUI classes; the API runs without it):

```bash
mkdir -p out/garden && cp src/garden/*.fxml src/garden/*.css src/garden/*.md out/garden/
javac -d out $(find src -name '*.java')
java -cp out GardenSimulator                # 24 days of one real hour each: the real test
java -cp out GardenSimulator 24 5000        # 24 days of 5 seconds each: a quick check
java -cp out GardenSimulator 24 5000 42     # ...with random seed 42, to repeat a run exactly
```

`GardenSimulator` follows the example script in the API document: each day it makes one or two random
calls (rain 0–50 mm, temperature 40–120°F, or a parasite picked from `getPlants()`), sleeps until the day
ends, and calls `getState()` at the end. `log.txt` and `garden-config.json` are read and written in the
working directory (relative paths only).

A grader's own script works with no imports: `GardenSimulationAPI` is in the default package (it extends
`garden.api.GardenSimulationAPI`, which holds the code). The example script's names `parasites(String)` and
`getStatus()` work as well as the specified `parasite(String)` and `getState()`. `getPlants()` returns a
`HashMap<String, Object>`, so it fits both `Map` and `HashMap` variables.

## The methods

| Method | What it does |
|---|---|
| `initializeGarden()` | Starts the clock (day 1) and plants the garden from `garden-config.json`. Missing or broken config → the built-in 40 trees, with a warning in the log. Always plants every variety and at least 10 plants. Calling it again starts a fresh run and a fresh `log.txt`. |
| `getPlants()` | `"plants"` (names), `"waterRequirement"` (mm/day) and `"parasites"` (lists of names), one entry per **plant type that still has a living plant**. |
| `rain(int mm)` | Rain falls evenly over the rest of the day; the soil doesn't dry while it rains. Each bed gains 30 moisture points per *daily water requirement* of rain, so 10 mm is a full day's water for a juniper but less than half for a maple. Back to dry weather when the day ends unless it rains again. |
| `temperature(int °F)` | Sets the day's temperature; back to 68°F when the day ends. 95°F and up dries the soil 3x faster (sprinklers keep up). Beds above 40°C (104°F) cause heat stress. |
| `parasite(String name)` | Infests every living plant vulnerable to that parasite. Names match ignoring case, extra spaces, `-`/`_` and a plural "s" (`"Aphid"` = `"aphids"`). A name no plant is vulnerable to (e.g. `"insects"`) infests nothing and is logged as a warning. |
| `getState()` | Writes the full report to `log.txt` (and the console): plants alive and dead, health now and the lowest it fell to (when and why), soil moisture, infestations, and each module's status. |

No method ever throws. A problem is logged as an `ERROR` line with its stack trace and the garden keeps
running. Calling any method before `initializeGarden()` initializes the garden first.

### Plants

| Plant | Water (mm/day) | Parasites | Weak spots |
|---|---|---|---|
| Juniper | 10 | spider mites, scale insects | hardy all round |
| Cherry Blossom | 20 | aphids, caterpillars, spider mites | heat (1.5/s), frost |
| Japanese Maple | 25 | aphids, scale insects | heat (2/s), thirsty in heatwaves |
| Pine | 15 | pine beetles, aphids | roots rot when waterlogged in rain |

## Time

Each real hour after `initializeGarden()` is one simulated day (`-Dgarden.dayMillis=5000` makes days 5 s
long). A day is 10 simulated seconds, cut into 40 steps of 0.25 s, the same model as the GUI. A background
thread keeps the simulation and `log.txt` up to date between calls, and every call first catches up with
the clock, so events always land on the right day.

Health is never reset: damage carries over from day to day, and a plant heals 2 health per simulated second
(20 a day) only while nothing is hurting it.

## Modules (all four run every step)

| Module | Sensor | Response |
|---|---|---|
| Watering system (shared with the GUI) | moisture probe per bed | sprinkler on below 30%, off at 90% |
| Drainage | moisture probe per bed | drain opens above 93%, closes below 85%; removes up to 8 points/s, so only rain over ~40 mm/day can still waterlog a pine |
| Climate control | thermometer | heating cables below 37°F (+8°C), shade cloth and misting above 95°F (−6°C) |
| Pest control | leaf sensors | notices an infestation after ~2.4 hours (1 s), sprays every 1 s, each spray kills 40%; gone in about 4 sprays. **Spraying never heals**: the plant only heals on its own afterwards |

Switch the modules off (as the unit tests do) and plants die: untreated parasites eat 30 health a day, so a
plant dies in about 3½ days. Nothing is hard-coded to keep plants alive.

## log.txt

```
2026-11-17 14:00:00 | DAY 02 | WARN  | PARASITE          | aphids               | 40/40 | 30 plants infested (Cherry Blossom, Japanese Maple, Pine are vulnerable to aphids)
```

Columns: real time, simulated day, level, event, event value, plants alive/total, details: the
`DAY, EVENT, EVENT_VALUE, PLANTS_ALIVE` format the API document suggests, plus a time, a level and a
plain-English explanation. The file begins with a legend. Levels: `INFO` normal, `WARN` needs attention,
`ALERT` a plant died, `ERROR` a caught software problem. A clean run has no `| ERROR` lines:

```bash
grep -c '| ERROR' log.txt     # 0 on a clean run
grep END_OF_DAY log.txt       # one summary per day
```

## Tests

```bash
scripts/test.sh               # builds everything and runs the 64 JUnit tests
```

| Test class | Covers |
|---|---|
| `GardenConfigTest` | config parsing: the documented format, loose names, bad entries, minimums, broken files |
| `SpeciesTest` | `Species.fromName`, parasite name matching |
| `PlotTest` | every kind of damage, healing rate, no healing while hurt, death |
| `WateringSystemTest` | sprinkler on/off switching, hysteresis, closed pipes, dead trees, module off |
| `GardenSimulationAPITest` | the API rules: day length, rain and temperature resets, parasites, pest control not healing, dead types leaving `getPlants()`, bad input, a full 24-day run with no errors |

`garden.api.BalanceCheck` (in `test/`) plays many random 24-day runs on a fake clock and reports survival and
final health for several healing rates:

```bash
java -cp out:out-test garden.api.BalanceCheck 1000 20 10 8 6
```

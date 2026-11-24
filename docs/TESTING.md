# Testing and Verification

This document covers how the backend was checked:

1. **Unit tests:** JUnit 5 tests of the model and the Garden Simulation API.
2. **Real-time runs:** the API driven by a script for 24 real hours, the way the graders will test it.
3. **Balance check:** thousands of simulated 24-day runs to tune healing and damage.

How to run the API and read `log.txt` is in [API.md](API.md) and [LOG_GUIDE.md](LOG_GUIDE.md).

---

## 1. Unit tests

```bash
scripts/test.sh
```

The script builds the whole project and runs all **64 tests**. It also compiles the API with only the plain JDK visible, so if the model or the API ever starts to depend on JavaFX, the build fails. (The API document asks for a standalone implementation.)

| Test class | Tests | What it checks |
|---|---|---|
| `GardenConfigTest` | 12 | Config parsing:<br>• the format from the API document;<br>• loose plant names (`"cherry"`, `"Japanese-Maple"`);<br>• whitespace and key order;<br>• duplicate entries are added together;<br>• bad entries are skipped with a warning, and huge amounts are capped;<br>• every variety and at least 10 plants are always planted;<br>• a missing or broken file falls back to the defaults. |
| `SpeciesTest` | 7 | `Species.fromName`:<br>• display and enum names, ignoring case, spaces, `-` and `_`;<br>• unknown names give `null`.<br>Parasite matching:<br>• ignores case, plurals and spacing;<br>• never matches another species' parasite or part of a name;<br>• the parasite list can't be changed from outside. |
| `PlotTest` | 17 | Damage:<br>• each kind of damage: drought (5/s), wind, frost, heat stress above 40°C, root rot, parasites;<br>• the biggest damage is named as the cause.<br>Healing:<br>• 2 health/s, which is 20% a day;<br>• never above 100;<br>• **no healing while anything still hurts the plant**;<br>• the healing rate can be changed for API runs.<br>Death:<br>• a plant dies at 0 and remembers why;<br>• a dead plant neither heals nor takes damage. |
| `WateringSystemTest` | 9 | On/off switching:<br>• the sprinkler turns on below 30%, stays off in the middle band, and stays on until 90%, so it doesn't flicker;<br>• a closed pipe or a dead tree keeps it off;<br>• switching the module off stops every sprinkler.<br>Also: water use is counted, and a running sprinkler wets the soil. |
| `GardenSimulationAPITest` | 19 | Each rule in the API document, on a fake clock (one day = 1 second):<br>• the config and relative paths;<br>• day length;<br>• health carrying over from day to day;<br>• rain sized by each plant's water need, resetting after one day;<br>• temperature resetting at day end;<br>• the shade cloth;<br>• parasites infesting only vulnerable plants;<br>• **pest control never healing**;<br>• dead types leaving `getPlants()`;<br>• calls before `initializeGarden()`;<br>• bad input logged instead of thrown;<br>• the example script's names `parasites()` and `getStatus()`;<br>• a full random run with no errors. |

Two tests switch a module off on purpose and check that plants then suffer:
- `untreatedParasitesKillAndDeadTypesLeaveGetPlants`: with pest control off, an infestation kills its plants.
- `withoutClimateControlA105DegreeDayHurtsMaples`: with climate control off, a 105°F day hurts the maples.

This is our evidence that survival comes from the modules and is not hard-coded.

---

## 2. Real-time runs (24 real hours)

`ApiDemo` copies the example script in the API document. (It was called `GardenSimulator` during the runs below, and was renamed so it can't clash with a grader's script of that name.)
1. It calls `initializeGarden()` and `getPlants()`.
2. Every real hour it makes one or two random calls:
   - rain, from 0 up to twice a plant's daily water need;
   - a temperature from 40 to 120°F;
   - or a parasite picked from `getPlants()`.
3. After 24 days it calls `getState()`.

```bash
java -cp out ApiDemo 24 3600000 <seed>
```

A run passes when:
- `log.txt` has no `| ERROR` lines;
- there are 24 `END_OF_DAY` lines, each about an hour apart;
- the final `getState()` report matches the log.

The Mac was kept awake with `caffeinate`, and its power log (`pmset -g log`) was checked afterwards to confirm the run was continuous.

| Run | Code | Result | Continuous? |
|---|---|---|---|
| Nov 17-18 | `831342e` | 0 errors, 40/40 alive | **No.** The Mac went to sleep with the lid closed and was awake for only about 7 of the 24 hours. Not counted. |
| **Nov 20-21** (seed 1120) | `831342e` | **0 errors, 24 days, 40/40 alive, 33 warnings, 0 alerts** | **Yes.** No sleep in the power log; days ended exactly an hour apart. |
| Nov 22-23 | `jay-api` | 0 errors, 24 days, 40/40 alive, 31 warnings, 0 alerts | **No.** The Mac slept 243 times, and some days ended up to 28 minutes late. Every call still landed on the right day, because the API catches up with the clock. |
| Nov 23-24 (seed 1123) | `0f80f70` | *in progress; ends about 18:52 on Nov 24* | |

### The Nov 20-21 run

The script's calls hurt every species, and the modules responded each time:

| Species | Final health | Lowest during the run | When and why |
|---|---|---|---|
| Juniper | 100% | 87% | day 13, scale insects |
| Cherry Blossom | 100% | 79% | day 22, spider mites |
| Japanese Maple | 100% | 80% | day 21, heat stress (the script set 115°F; the shade cloth went up, but the beds still passed 40°C) |
| Pine | 100% | 76% | day 16, root rot after heavy rain |

Over the 24 days:
- the drains opened 370 times;
- pest control found and cleared 270 infestations with 1,140 sprays;
- the sprinklers used 1,306 L.

The full report is at the end of that run's `log.txt`.

---

## 3. Balance check

```bash
java -cp out:out-test garden.api.BalanceCheck 1000 20 8
```

This plays 1,000 random 24-day runs for each healing rate on a fake clock, with events like the graders' script, and takes about 12 seconds.

| Healing per day | Plants alive | Avg final health | Plants below 100% | Plants below 90% | Lowest final | Lowest ever | Runs with a death |
|---|---|---|---|---|---|---|---|
| **20% (current)** | 100% | 98.7% | 11.1% | 5.4% | 39% | 27% | 0 of 1000 |
| 8% | 100% | 97.0% | 38.8% | 11.8% | 32% | 10% | 0 of 1000 |

At 20% a day, almost all damage from the first ~20 days has healed by day 24, so the final state can look as if nothing happened. We handle that in the report instead: `getState()` lists each plant's **lowest health during the run, with the day and the cause**.

Slowing healing to 8% a day for API runs only would leave more damage visible at the end, with still no deaths. Slower healing is not used in the GUI, because there it let cherry trees die in windstorms.

**Decision:** *(to be agreed by the team: keep 20% a day, or use 8% a day for API runs)*

The balance check also fixed a real problem during development. Pines died of root rot in 17% of random runs, for two reasons:
- rain arrived all at once;
- the soil couldn't dry during a rainy day.

Spreading the rain over the day brought that to 9%. Adding the Drainage module brought it to 0.

---

## 4. What is not covered

- **The GUI has no automated tests.** It was checked by hand. Its Log tab was also checked by a small program that opened the real window, confirmed the tab filled with entries, and closed it.
- **The GUI's 24-hour run at 1x speed is in progress.** It started Nov 23 at 20:18, on `0f80f70`, as the graders will run it. A monitor records every 10 minutes whether the program is alive, plus its memory, CPU, log size and caught errors. Before this, its long test ran only at 256x speed.

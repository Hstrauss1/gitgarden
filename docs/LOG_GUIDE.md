# Log Guide

There are two logs. The **GUI log** is described first. The **API's `log.txt`**, written when a test script runs the garden with no window, is described in [its own section](#the-apis-logtxt) below.

The GUI garden writes every event to two places:

- the **Log** tab in the sidebar, which keeps the last 5,000 entries and has filters and a search box, and
- a plain-text file in the `logs/` folder of the directory the app was started from. It is named `garden_<date>_<time>.log`. When a file reaches 20 MB, the log continues in `..._part2.log`, then `part3`, and so on.

The file is flushed to disk about once a second, and again when the window closes. Every file starts with a short header that explains the format.

## Line format

```
[real date & time]    [in-game date]         LEVEL CATEGORY  message
[2026-11-05 18:59:25] [Y1 Win M10 D11 19:58] INFO  HEATING   Air 2.4°C (below 3°C): bed heaters ON
```

- **Real date and time:** the wall-clock time the event was logged.
- **In-game date:** `Y<year> <season> M<month> D<day> <hh:mm>`. Seasons are Spr, Sum, Aut and Win. One in-game day is 10 seconds at 1x speed.
- **LEVEL:** how serious the event is (see below).
- **CATEGORY:** which part of the garden the event came from (see below).
- **Message:** a plain-English description. It always names the plot (for example `Plot 14`) when one is involved.

## Levels

| Level | Meaning | Examples |
|---|---|---|
| INFO | Normal activity | sprinkler on/off, planting, harvest, heaters on/off, weather ended |
| WARN | Needs attention | a tree started wilting, a cockroach arrived, a drought or windstorm began |
| ALERT | Something bad happened | a tree DIED (with its cause), a tree is ON FIRE, a Hellfire Rider or Green Brute arrived, a cockroach is eating a tree, frost with the heaters off, pesticide tank empty |
| ERROR | A software problem was caught | includes the stack trace; the garden kept running |

The Log tab's level filter shows the chosen level and everything more serious.

## Categories

| Category | What it covers |
|---|---|
| SYSTEM | start and stop, the garden being set up, caught errors |
| TIME | new seasons and years, and the daily report |
| WEATHER | rain, drought and windstorm beginning and ending |
| WATERING | moisture-sensor readings that switch a sprinkler on or off |
| HEATING | temperature-sensor readings that switch the heaters, and frost warnings |
| PEST | pest-sensor detections, pesticide sprays and the tank level |
| REPLANT | the groundskeeper removing dead trees and replanting beds |
| PLANT | planting, wilting and recovery, death and its cause, automatic harvests |
| WILDLIFE | cockroaches crawling in and starting to eat |
| INTRUDER | the Hellfire Rider and Green Brute events: arriving, each smash, leaving |
| USER | everything a person did: watering, harvesting, chopping, planting, stomping, spigots, speed, sliders, weather, modules switched on or off, pause |

## Daily report

At the end of each in-game day the log writes a divider and a summary:

```
==================== DAILY REPORT  day 12  (Year 1 · Spring · Month 1, Day 13 · 00:00) ====================
[...] INFO  TIME      Day 12 report: 39 alive (1 wilting, average health 97%), 1 dead, 0 empty | today: 4 harvested, 1 died, 2 pests sprayed, 0 stomped, 310 L water | weather Rain, air 9.8°C
```

## Finding things

- **Why did a tree die?** Search for `DIED`. Each death line names the cause: drought (dry soil), windstorm, root rot, frost, cockroach, fire, or being smashed by the Green Brute. In an API run there are two more causes: heat stress, and a parasite infestation (for example `spider mites infestation`).
- **One plot's history:** search for `Plot 14:` (include the colon, so `Plot 1` doesn't also match Plot 10 to Plot 19).
- **Problems only:** set the level filter to "ALERT and worse".
- **What a person did:** set the category filter to USER.
- **Jump between days in the file:** search for `====`.
- **Software errors:** search for `] ERROR` (with the bracket, so the explanation in the file's header doesn't match). There should be none; each one includes a stack trace.

In a terminal you can also filter the file directly, for example:

```bash
grep ALERT logs/garden_*.log
grep "Plot 14:" logs/garden_*.log
```

---

## The API's log.txt

When a test script drives the garden through the Garden Simulation API (no window), every event goes to `log.txt` in the folder the script runs from. Each call to `initializeGarden()` starts a fresh file. The file opens with a legend that repeats the key points below.

### Line format

```
real time           | DAY    | LEVEL | EVENT             | EVENT_VALUE          | ALIVE | details
2026-11-23 00:37:37 | DAY 01 | INFO  | INIT              | -                    |     - | initializeGarden() called: the clock starts now, day 1 begins. One day = 3.0 real seconds
```

- **Real time:** the wall-clock time the event was logged.
- **DAY:** the simulated day. Day 1 starts when `initializeGarden()` is called, and each real hour is one day.
- **LEVEL:** INFO, WARN, ALERT or ERROR, as in the GUI log.
- **EVENT:** what happened (see the table below).
- **EVENT_VALUE:** the value the script passed, or the key number, such as `18 mm`, `104 F`, `aphids` or `40 alive`.
- **ALIVE:** plants alive out of the total at that moment, such as `40/40`.
- **Details:** a plain-English explanation.

These are the `DAY, EVENT, EVENT_VALUE, PLANTS_ALIVE` columns the API document suggests, plus the time, the level and the explanation.

### Levels in log.txt

| Level | Meaning |
|---|---|
| INFO | Normal activity: the script's calls, modules switching, daily summaries |
| WARN | Needs attention: an infestation, pest control finding parasites, or odd input from the script (negative rain, an unknown parasite name, a call before `initializeGarden()`) |
| ALERT | A plant died. The line names the plant and the cause |
| ERROR | A software problem was caught and the garden kept running. The stack trace follows on the next lines. A clean run has none |

### Events

| Event | Written when | EVENT_VALUE |
|---|---|---|
| `INIT` | `initializeGarden()` starts the clock and the modules, or a method was called before it | `-`, `4 modules`, or the method name |
| `CONFIG` | the garden was planted from `garden-config.json` (or the built-in list), or the file needed fixing | `40 plants`, or `warning` |
| `GET_PLANTS` | `getPlants()` was called; the details repeat what it returned | `4 types` |
| `RAIN` | `rain()` was called; the details give the moisture each species gets | `18 mm` |
| `TEMPERATURE` | `temperature()` was called; the details say what it means for the plants | `104 F` |
| `PARASITE` | `parasite()` was called; the details say how many plants of which species were infested | `aphids` |
| `PEST_CONTROL` | parasite control found an infestation (WARN) or cleared it (INFO) | the parasite |
| `CLIMATE` | the bed heating or the shade cloth switched on or off | `heating ON`, `shade off` |
| `DRAINAGE` | waterlogged beds had their drains opened | `3 beds` |
| `PLANT` | a plant started wilting, recovered, or died (ALERT, with the cause) | `-` |
| `END_OF_DAY` | the last step of every day: average and lowest health, infestations, sprinkler starts, water, sprays and deaths | `40 alive` |
| `RAIN_RESET` | a rainy day ended; the next day is dry unless the script makes it rain | `dry` |
| `TEMPERATURE_RESET` | a day whose temperature was set ended; it goes back to 68°F | `68 F` |
| `STATE` | `getState()` was called; the full report follows (see below) | `24 days done` |
| `ERROR` | a caught software problem | where it happened |

### Example lines

These are real lines from 24-day test runs:

```
2026-11-23 00:37:37 | DAY 01 | INFO  | INIT              | -                    |     - | initializeGarden() called: the clock starts now, day 1 begins. One day = 3.0 real seconds
2026-11-23 00:37:37 | DAY 01 | INFO  | CONFIG            | 40 plants            | 40/40 | Planted from garden-config.json: 10 Juniper, 10 Cherry Blossom, 10 Japanese Maple, 10 Pine
2026-11-23 00:37:37 | DAY 01 | INFO  | TEMPERATURE       | 104 F                | 40/40 | Today's temperature is 104°F (40.0°C): heatwave: soil dries 3x faster today, the shade cloth goes on
2026-11-23 00:37:37 | DAY 01 | INFO  | CLIMATE           | shade ON             | 40/40 | Air 104°F: shade cloth and misting on (-6°C at the beds)
2026-11-23 00:37:37 | DAY 01 | WARN  | PARASITE          | aphids               | 40/40 | 30 plants infested (Cherry Blossom, Japanese Maple, Pine are vulnerable to aphids)
2026-11-23 00:37:37 | DAY 01 | WARN  | PEST_CONTROL      | aphids               | 40/40 | Sensors found aphids on 30 plants (10 Cherry Blossom, 10 Japanese Maple, 10 Pine), health 98%; spraying
2026-11-23 00:37:38 | DAY 01 | INFO  | PEST_CONTROL      | aphids               | 40/40 | aphids cleared from 30 plants (10 Cherry Blossom, 10 Japanese Maple, 10 Pine), health 94% after up to 4 sprays; they heal on their own from here
2026-11-23 00:37:40 | DAY 01 | INFO  | END_OF_DAY        | 40 alive             | 40/40 | Day 1 over (104°F): average health 100%, lowest Plot 01 (Juniper) at 100%, 0 infested, 34 sprinkler starts, 213 L water, 120 sprays, 0 died today
2026-11-23 00:37:40 | DAY 01 | INFO  | TEMPERATURE_RESET | 68 F                 | 40/40 | The day is over; the temperature goes back to 68°F
2026-11-23 00:37:40 | DAY 02 | INFO  | RAIN              | 18 mm                | 40/40 | Rain falling for the rest of the day; soil moisture points it adds: Juniper +54, Cherry Blossom +27, Japanese Maple +22, Pine +36; soil doesn't dry while it rains
2026-11-23 00:37:43 | DAY 02 | INFO  | RAIN_RESET        | dry                  | 40/40 | The rain has passed; the soil dries normally again from tomorrow
2026-11-23 00:41:58 | DAY 22 | ALERT | PLANT             | -                    | 39/40 | Plot 01 (Juniper): DIED from spider mites infestation
```

The last line comes from a run in which the script released every parasite every day, which the garden cannot survive.

### The getState() report

`getState()` writes a `STATE` line followed by a report on every species, every plant and every module. From the end of a healthy 24-day run:

```
2026-11-23 00:38:49 | DAY 25 | INFO  | STATE             | 24 days done         | 40/40 | 40 of 40 plants alive, 0 dead; 0 infested; 0 errors, 19 warnings, 0 alerts logged so far
    BY SPECIES
      Juniper         10/10 alive   average health now 100%   lowest during the run  94%   needs 10 mm water/day   parasites: spider mites, scale insects
      Cherry Blossom  10/10 alive   average health now 100%   lowest during the run  87%   needs 20 mm water/day   parasites: aphids, caterpillars, spider mites
      Japanese Maple  10/10 alive   average health now 100%   lowest during the run  87%   needs 25 mm water/day   parasites: aphids, scale insects
      Pine            10/10 alive   average health now 100%   lowest during the run  80%   needs 15 mm water/day   parasites: pine beetles, aphids
    EVERY PLANT
      Plot 01  Juniper         ALIVE  health 100%  soil 100%  lowest 94% (day 2, scale insects infestation)
      Plot 02  Juniper         ALIVE  health 100%  soil 100%  lowest 94% (day 2, scale insects infestation)
      ... (one line for each of the 40 plants)
    MODULES
      Watering system  0 sprinklers running · average soil 91% · 599 L used
      Drainage         31 drains open now · opened 730 times
      Climate control  Air 62°F · heating off · shade off
      Pest control     0 plants infested now · 920 sprays · 210 infestations cleared
    Weather now: dry, 68°F
```

For a dead plant, the EVERY PLANT row reads, for example, `DEAD   since day 22, spider mites infestation`.

### Answering common questions

On macOS or Linux:

- **Did any plant die, and why?** `grep '| ALERT' log.txt`
- **How did one day go?** `grep '| DAY 05 |' log.txt`
- **One summary per day:** `grep END_OF_DAY log.txt`
- **What did the script do?** `grep -E '\| (RAIN|TEMPERATURE|PARASITE) ' log.txt`
- **How did pest control respond?** `grep PEST_CONTROL log.txt`
- **Any software problems?** `grep -c '| ERROR' log.txt` prints 0 on a clean run.
- **One plant:** `grep 'Plot 14' log.txt`

On Windows, PowerShell's `Select-String` does the same, for example `Select-String -Path log.txt -Pattern '| ALERT' -SimpleMatch`. Searching for plain `ALERT` would also match the legend at the top of the file, so include the `|` before it.

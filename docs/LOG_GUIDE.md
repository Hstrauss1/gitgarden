# Log Guide

The garden writes every event to two places:

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

- **Why did a tree die?** Search for `DIED`. Each death line names the cause: drought (dry soil), windstorm, root rot, frost, cockroach, fire, or being smashed by the Green Brute.
- **One plot's history:** search for `Plot 14:` (include the colon, so `Plot 1` doesn't also match Plot 10 to Plot 19).
- **Problems only:** set the level filter to "ALERT and worse".
- **What a person did:** set the category filter to USER.
- **Jump between days in the file:** search for `====`.
- **Software errors:** search for `ERROR`. There should be none; each one includes a stack trace.

In a terminal you can also filter the file directly, for example:

```bash
grep ALERT logs/garden_*.log
grep "Plot 14:" logs/garden_*.log
```

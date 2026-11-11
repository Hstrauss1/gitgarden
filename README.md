# Computerized Bonsai Garden (JavaFX + FXML)

CSEN 275 garden project. A Stardew Valley-style farm of 40 small plots. Each plot grows a tree with a Java port of
**cbonsai**'s recursive branching algorithm, drawn as coloured ASCII on a `Canvas`.
Keep your bonsai alive through droughts, storms and cockroaches, across days, months and years.

## Playing

| Do this | What happens |
|---|---|
| Click a plot | Water it |
| Click a grown tree (gold star) | Harvest it; a new one is planted (grown trees also auto-harvest after 10s) |
| Click a cockroach | Stomp it before it eats your tree |
| Click a dead tree 3 times | Chop it down, leaving a stump |
| Click an empty bed (stump with a green +) | Plant a new bonsai |
| Click a spigot | Shut off (or turn on) that row's overhead pipe |
| Change weather menu | Start rain, a drought or a windstorm right now |
| Trigger event menu | Bring in a Hellfire Rider (sets a row of trees on fire) or a Green Brute (smashes trees) |
| Click a burning tree | Throw water on it and put the fire out |
| Click the Green Brute 5 times | Calm him down so he leaves early |
| Speed | 1x up to 256x (a full year in about 14 seconds) |

Hover a plot for its species, traits, moisture, health and age. A health bar appears on a bed's
front edge once its tree is hurt. Trees lose health from dry soil, cockroaches, wind, frost and (for pines)
waterlogged roots; at 0 they die. The auto-replanting module clears a tree that has been dead for a day,
or you can chop and replant it sooner yourself.

The **Help** button opens the full user manual ([docs/USER_MANUAL.md](docs/USER_MANUAL.md)).

Requirements, use cases, user stories and scenarios are in [docs/ANALYSIS.md](docs/ANALYSIS.md). The UML (use case, class, state, sequence and activity diagrams) is in [docs/UML.md](docs/UML.md).

## Automated modules

Four standalone modules (each a `GardenModule` subclass) run every simulation step. Each has a card in the
**Systems** tab with a checkbox to switch it off. Nothing is hardcoded to keep the garden alive: switch the
modules off and the trees die of drought, frost and pests.

| Module | Sensor | What it does |
|---|---|---|
| `WateringSystem` | `MoistureSensor` per bed | Sprinkler on below 30% soil moisture, off at 90%; a closed spigot overrides it |
| `HeatingSystem` | `TemperatureSensor` | Bed heating cables on below 3°C, off above 6°C (+8°C to the beds); they glow orange |
| `PestControlSystem` | `PestSensor` | Confirms a feeding cockroach for 2 s, then sprays it (15% of a slowly refilling tank) |
| `ReplantingSystem` | plot states | Chops trees dead for a day, replants beds empty for half a day |

## The log

`GardenLog` records every event (weather, sensors, sprinklers, heaters, pests, deaths, harvests, every user
click, and a daily report) in the **Log** tab (filter by category or level, and search) and in
`logs/garden_<start time>.log`. See [docs/LOG_GUIDE.md](docs/LOG_GUIDE.md).

## Robustness

Each module, each plot's update, each plot's drawing and each whole frame runs inside its own `try/catch`.
A problem is logged as `ERROR` with its stack trace and counted in the status bar, and the garden carries on.
There is also a default uncaught-exception handler. An unattended test at 256x (about 9 simulated years, the
equivalent of over 26 hours at 1x) finished with 0 errors and 0 tree deaths with the modules on. Then, with
the modules switched off, all 40 trees died within 3 simulated years.

**Life** sets tree size and **Multiplier** sets how often it branches, just like `cbonsai -L` and `-M`.

### Time, seasons and weather

One day is 10 simulated seconds; a month is 30 days and a year is 12 months.
Months 1-3 are spring, 4-6 summer, 7-9 autumn and 10-12 winter.

| Season | Weather odds | Growth | Soil drying | Cockroaches |
|---|---|---|---|---|
| Spring | Mostly rain | 115% | 90% | normal |
| Summer | Mostly drought | 100% | 125% | 160% |
| Autumn | Mostly wind | 85% | 100% | normal |
| Winter | Wind or rain, no drought | 45% | 60% | 30% |

| Species | Drought | Windstorm | Rain | Frost (bed below 0°C) |
|---|---|---|---|---|
| Juniper | Hardy: soil dries at half speed | -1 health/s | normal | -0.3 health/s |
| Cherry Blossom | normal | Fragile: -2.5 health/s | Grows 80% faster | -2.5 health/s |
| Japanese Maple | Thirsty: soil dries 1.7x faster | -2 health/s | Grows 20% faster | -1.5 health/s |
| Pine | normal | Wind-proof | Roots rot (-1.5 health/s) when soil is over 95% wet | immune |

## How it works

| Class | Role |
|---|---|
| `BonsaiGenerator` | cbonsai's `branch()` / `setDeltas()` / `chooseString()`: weighted dice rolls pick each step's `dx, dy`; branches spawn trunk splits, left/right shoots, and leafy `DYING`/`DEAD` sprays as their life runs out. |
| `Plot` | One plot: tree glyphs, soil moisture, health, and the simulation step (drying, sprinklers, growth, damage from drought, wind and rot, dying, chopping, empty beds). |
| `Species` | Juniper, Cherry Blossom, Japanese Maple, Pine: leaf characters, colours, life/multiplier tweaks, and weather reactions. |
| `GameClock` | Turns simulated seconds into hours, days, months, seasons and years; each `Season` changes weather odds, growth, drying and pests. |
| `Weather` / `WeatherSystem` | Clear, drought, rain and windstorm, and the random event roller (odds set by the season). |
| `WeatherView` | Draws rain streaks, drought haze and sun glow, wind gusts and blowing leaves, season tints and night over the whole lawn. |
| `Bug` | Base class for anything that moves on the field: smooth steering plus shared drawing helpers. |
| `Ladybug`, `Bee`, `Cockroach` | Ladybugs drift around; bees visit leafy trees and pollinate them (faster growth); cockroaches sneak in under the nearest fence, eat a tree's health, and must be stomped. |
| `HellfireRider`, `GreenBrute` | Rare events: a pixel-art biker who rides along a row setting trees on fire, and a pixel-art brute who smashes trees until he is calmed down. |
| `PixelSprite` | Draws pixel art written as rows of characters, mirrored for the other direction. |
| `Critters` | Owns every bug and splat, spawns cockroaches, handles stomping, and answers bugs' questions about the field. |
| `Pipe` | One overhead pipe per row of plots, with an open/closed valve. A closed pipe can't water its plots. |
| `Irrigation` | Draws the metal pipes across the field (through the fence on both sides), couplings, nozzles, and the falling spray. |
| `Spigot` | The chrome cross-handle tap on each pipe. Click to turn it a quarter turn: blue cap = open, red cap = shut off. |
| `GrassTexture` | Draws a small seamless tile of grass tufts (2-3 blades each) and repeats it as the lawn and field background. |
| `Garden` | The model: plots, pipes, clock, weather and modules; `step()` advances everything, each part guarded by `try/catch`; air temperature; daily reports. |
| `GardenModule` | Base class of the four automated modules: name, description, on/off, `run()` and a status line. |
| `WateringSystem`, `HeatingSystem`, `PestControlSystem`, `ReplantingSystem` | The automated modules (see above). |
| `Sensor`, `MoistureSensor`, `TemperatureSensor`, `PestSensor` | Noisy sensor readings that the modules act on. |
| `GardenLog` | The event log: file plus in-memory list, levels, categories, rotation at 20 MB; never throws. |
| `HelpWindow` + `help.md` | The in-app user manual. |
| `GardenController` + `garden.fxml` | Main window, toolbar, calendar/weather bar, the Systems and Log sidebar, and the `AnimationTimer` loop (sub-stepped so high speeds stay accurate). |
| `PlotController` + `plot.fxml` | One plot, loaded once per plot: a trapezoid soil bed with the tree standing out of it; soil colour shows moisture, leaves brown and fall as health drops, trees sway in wind, shake when chopped, and leave a stump. |

## Running

JavaFX is not bundled with Amazon Corretto 8. A JDK with JavaFX built in
(BellSoft Liberica JDK 21 "Full") is installed at `~/.jdks/liberica-full-21`. From this folder:

```bash
mkdir -p out/garden && cp src/garden/*.fxml src/garden/*.css src/garden/*.md out/garden/
~/.jdks/liberica-full-21/bin/javac -d out src/garden/*.java
~/.jdks/liberica-full-21/bin/java -cp out garden.GardenApp
```

In IntelliJ: Project Structure → SDKs → add `~/.jdks/liberica-full-21` and use it for this project.
No extra VM options are needed with that JDK.

## References

- **cbonsai** by John Allbritten, https://gitlab.com/jallbrit/cbonsai (GPL-3.0). `BonsaiGenerator` is a Java
  port of its tree-growing algorithm (`branch`, `setDeltas`, `chooseString`).
- JavaFX 21 (OpenJFX) API documentation, https://openjfx.io/javadoc/21/.
- Parts of this code were written with an AI assistant (Claude, by Anthropic). Cite this as your course's
  academic-integrity policy requires.

## License

Copyright (C) 2026 Hudson Strauss and the CSEN 275 team.

This program is free software: you can redistribute it and/or modify it under the terms of the
GNU General Public License as published by the Free Software Foundation, either version 3 of the
License, or (at your option) any later version. It is distributed WITHOUT ANY WARRANTY; see
[LICENSE](LICENSE) for the full terms.

The project is GPL-3.0 because `BonsaiGenerator` is a port of cbonsai, which is GPL-3.0 licensed.

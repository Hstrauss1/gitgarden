# Computerized Bonsai Garden: Requirements and Object-Oriented Analysis

CSEN 275 garden project. This document covers:

1. the problem statement,
2. the feature list (functional and non-functional requirements),
3. actors and use cases,
4. user stories with acceptance criteria,
5. scenarios,
6. the candidate classes found by analysis, and
7. a requirement-to-class traceability table.

The UML diagrams are in [UML.md](UML.md).

> **Units:** all times are *simulated* seconds unless stated otherwise. 10 simulated seconds make one in-game day. At 1x speed, a simulated second is a real second.

---

## 1. Problem statement

A gardener wants a computerized garden that looks after a large bed of bonsai trees with as little human help as possible. The garden must:

- keep many plants of several species alive through changing weather and seasons, while the trees face real threats: drying soil, windstorms, waterlogged roots, frost and cockroaches;
- detect those threats with **sensors** and respond through **independent automated subsystems** (watering, heating, pest control and replanting). Each subsystem can be switched off to show that the garden really depends on it;
- let a person step in at any time: water, harvest, chop down dead trees, plant, stomp pests, shut off water lines, force weather, change speed;
- record **every** event and interaction in a detailed, readable, searchable log;
- run unattended for at least 24 hours without crashing, handling every exception;
- explain itself through an in-app help manual and a guide to the log.

The garden is not hardcoded to survive. If its subsystems are switched off, or overwhelmed, trees die and the log records why.

---

## 2. Feature list

### 2.1 Functional requirements

| ID | Requirement |
|---|---|
| F1 | The garden has 40 plots (8 per row × 5 rows) inside a fence. Each plot grows one tree. |
| F2 | Trees are generated procedurally with the cbonsai branching algorithm, seeded per plot, so every tree is different. **Life** and **Multiplier** settings change the size and branching of new trees. |
| F3 | Four species (Juniper, Cherry Blossom, Japanese Maple, Pine) react differently to drought, wind, rain, root rot and frost. |
| F4 | Each plot tracks soil moisture (0-100%), health (0-100) and a state: GROWING, WILTING, MATURE, DEAD or EMPTY. |
| F5 | A game clock turns time into hours, days (10 s), 30-day months, seasons and 12-month years. Seasons change the weather odds, growth rate, soil drying and pest numbers. |
| F6 | A weather system produces random Clear, Rain, Drought and Windstorm events. Their odds depend on the season. |
| F7 | Air temperature follows the season, the time of day and the weather. Winter nights fall below 0°C. |
| F8 | Ladybugs and bees fly around the field. Bees pollinate growing trees, which speeds up their growth. |
| F9 | Cockroaches crawl in under the fence and eat trees (6 health/s). The user can stomp them. |
| F10 | One overhead pipe per row feeds a sprinkler over each bed. A spigot on each pipe opens or closes the whole row. |
| F11 | **Watering module:** a moisture sensor in each bed turns that bed's sprinkler on below 30% and off at 90%. |
| F12 | **Heating module:** a temperature sensor turns bed heating cables on below 3°C and off above 6°C. They add 8°C to the bed temperature. |
| F13 | **Pest control module:** pest sensors confirm a feeding cockroach for 2 s, then spray it. Each spray uses 15 units of a 100-unit tank that refills at 0.6 units/s. |
| F14 | **Replanting module:** removes trees that have been dead for 1 day and replants beds that have been empty for half a day. |
| F15 | Each module can be switched on or off at run time, and shows a live status line. |
| F16 | User actions: water a plot by hand (+35% moisture), harvest a mature tree, chop a dead tree (3 chops), plant an empty bed, stomp a cockroach, toggle a spigot, force the weather, pause, change speed (1x-256x), Replant All, change Life and Multiplier. |
| F17 | A sensors panel shows air temperature, average soil moisture and the number of pests feeding. A totals panel shows harvests, deaths, chops, stomps, sprays and water used. |
| F18 | The log records every event with real time, game time, level, category and a plain-English message, both in the app and in a file. |
| F19 | The in-app log can be filtered by category and minimum level, searched by text, and set to follow new entries. |
| F20 | The log writes a daily report at the end of every in-game day. |
| F21 | An in-app help window shows the user manual. |
| F22 | Trees left at MATURE are harvested automatically after 10 s, and a new tree is planted. |

### 2.2 Non-functional requirements

| ID | Requirement |
|---|---|
| NF1 | **Robustness:** the garden runs unattended for 24+ hours without crashing. Every module, plot update, plot drawing and frame is guarded. Caught exceptions are logged at ERROR level with a stack trace, and the garden continues. |
| NF2 | **Honesty:** nothing is hardcoded to keep plants alive. Switching modules off must lead to deaths. |
| NF3 | **Log size:** a log file starts a new part at 20 MB. The in-app list keeps the last 5,000 entries. |
| NF4 | **Log safety:** logging never throws. If the log file can't be written, logging continues in memory. |
| NF5 | **Accuracy at speed:** the simulation advances in steps of at most 0.25 s, so 256x gives the same results as 1x, just faster. |
| NF6 | **Usability:** a JavaFX UI defined in FXML, with a consistent visual style, tooltips on plots and spigots, and readable log rows coloured by level. |
| NF7 | **Extensibility:** modules share an abstract `GardenModule` base and sensors share a `Sensor` interface, so new subsystems plug in without changing the main loop. This will also support the monitoring API the course will provide. |
| NF8 | **Attribution:** all external material is credited (cbonsai, JavaFX, AI assistance). |

---

## 3. Actors

| Actor | Kind | Description |
|---|---|---|
| **Gardener** | Primary, human | Watches the garden and steps in: watering, harvesting, chopping, planting, stomping, spigots, weather, speed, modules. |
| **Evaluator** | Secondary, human | Reads the log and help, switches modules off to test them, and leaves the garden running unattended. In practice this is the gardener with a testing goal. |
| **Game clock** | Secondary, system | Drives time forward. It starts the automatic use cases on every simulation step. |
| **Environment** | Secondary, system | The weather and temperature, which act on the plots. |
| **Wildlife** | Secondary, system | Bees, ladybugs and cockroaches moving on the field. |
| **Monitoring API** | Future, external | Will read the garden's state. It is to be specified by the course. |

---

## 4. Use cases

### 4.1 Summary

| ID | Use case | Primary actor |
|---|---|---|
| UC1 | Observe the garden | Gardener |
| UC2 | Water a plot by hand | Gardener |
| UC3 | Harvest a mature tree | Gardener |
| UC4 | Chop down a dead tree | Gardener |
| UC5 | Plant an empty bed | Gardener |
| UC6 | Stomp a cockroach | Gardener |
| UC7 | Open or close a row's water | Gardener |
| UC8 | Switch a module on or off | Gardener / Evaluator |
| UC9 | Force the weather | Gardener / Evaluator |
| UC10 | Control the simulation (pause, speed, Replant All, Life and Multiplier) | Gardener |
| UC11 | Review the log | Gardener / Evaluator |
| UC12 | Read the help manual | Gardener |
| UC13 | Water automatically | Game clock (Watering module) |
| UC14 | Protect from frost | Game clock (Heating module) |
| UC15 | Control pests | Game clock (Pest control module) |
| UC16 | Replant automatically | Game clock (Replanting module) |
| UC17 | Simulate time, weather and growth | Game clock |
| UC18 | Recover from an error | System |

### 4.2 Detailed use cases

#### UC2: Water a plot by hand

- **Actor:** Gardener
- **Precondition:** the plot has a growing or wilting tree.
- **Main flow:**
  1. The gardener clicks the plot.
  2. The system adds 35% to the plot's soil moisture, up to 100%.
  3. The soil colour darkens.
  4. The system logs `USER  Plot N watered by hand (soil now X%)`.
- **Alternatives:**
  - 1a. A cockroach is under the pointer: UC6 runs instead.
  - 1b. The tree is MATURE: UC3 runs instead.
  - 1c. The tree is DEAD: UC4 runs instead.
  - 1d. The bed is EMPTY: UC5 runs instead.
- **Postcondition:** the moisture is higher. If it rises above 15%, a wilting tree starts to recover.

#### UC3: Harvest a mature tree

- **Actor:** Gardener
- **Precondition:** the tree is MATURE (fully grown, shown with sparkles).
- **Main flow:**
  1. The gardener clicks the tree.
  2. The system counts a harvest and logs `USER  Plot N: <species> harvested by hand (age …)`.
  3. The system plants a new random species using the current Life and Multiplier (UC5, steps 2-4).
- **Alternative:** 1a. The gardener does nothing. After 10 s the tree is harvested automatically and the log entry is in the PLANT category.

#### UC4: Chop down a dead tree

- **Actor:** Gardener
- **Precondition:** the plot is DEAD.
- **Main flow:**
  1. The gardener clicks the dead tree.
  2. The tree shakes, wood chips fly, and the system logs `chopped at the dead tree (1/3)`.
  3. Steps 1-2 repeat. On the third chop the tree falls, leaving a stump.
  4. The plot becomes EMPTY, and the felled-tree count goes up.
- **Alternative:** 1a. The gardener leaves the tree. After 1 day, UC16 removes it.

#### UC5: Plant an empty bed

- **Actor:** Gardener
- **Precondition:** the plot is EMPTY.
- **Main flow:**
  1. The gardener clicks the bed (stump with a green +).
  2. The system picks a random species and seed.
  3. The system generates the tree's glyphs with the cbonsai algorithm.
  4. The plot resets to health 100 and starts GROWING.
  5. The system logs a USER entry and a PLANT entry naming the species.

#### UC6: Stomp a cockroach

- **Actor:** Gardener
- **Precondition:** the simulation is not paused, and a cockroach is within 10 px of the click.
- **Main flow:**
  1. The gardener clicks the cockroach.
  2. The system removes it and leaves a splat for 5 s.
  3. The system counts the stomp and logs a USER entry.
  4. The click is consumed, so the plot underneath is not watered.
- **Alternative:** 1a. No cockroach is near the click: the click goes to the plot (UC2-UC5).

#### UC7: Open or close a row's water

- **Actor:** Gardener
- **Main flow:**
  1. The gardener clicks a row's spigot.
  2. The handle turns a quarter turn, and the cap turns red (closed) or blue (open).
  3. While the pipe is closed, the Watering module cannot run any sprinkler on that row.
  4. The system logs `USER  Row N spigot turned OFF/ON`.
- **Postcondition:** if the pipe is closed, the beds on that row dry out and their trees eventually wilt (an intentional way to test drought damage).

#### UC8: Switch a module on or off

- **Actor:** Evaluator
- **Main flow:**
  1. The evaluator unticks a module's card in the Systems tab.
  2. The module stops running, and its `shutDown` releases whatever it controls (for example, sprinklers and heaters are turned off).
  3. The card's status shows `OFF`, and the system logs a USER entry.
  4. Ticking the card again restarts the module.
- **Postcondition:** with the module off, the threat it handled now harms the trees unchecked.

#### UC9: Force the weather

- **Actor:** Gardener / Evaluator
- **Main flow:**
  1. The gardener picks Clear, Rain, Drought or Windstorm from **Change weather**.
  2. The weather changes at once and runs for its normal random length.
  3. The visuals change: rain streaks, drought haze, or wind gusts and blowing leaves.
  4. The log records the user action and a WEATHER entry describing the effects.

#### UC11: Review the log

- **Actor:** Evaluator
- **Main flow:**
  1. The evaluator opens the Log tab.
  2. They choose a category and/or minimum level, and type a search term.
  3. The list shows only the matching entries. Hovering an entry shows its full line.
  4. **Open log folder** opens the folder that holds the full log files.
- **Alternative:** 2a. With **Follow** unticked, the list stays where it is while new entries arrive.

#### UC13: Water automatically (Watering module)

- **Actor:** Game clock
- **Precondition:** the Watering module is on.
- **Main flow, every step, for each plot:**
  1. The module reads the plot's moisture sensor (±1% noise).
  2. If the pipe is closed or the bed has no living tree, the sprinkler is off.
  3. If the reading is below 30% and the sprinkler is off, it turns the sprinkler on and logs `sensor reads X% (below 30%), sprinkler ON`.
  4. If the reading is at least 90% and the sprinkler is on, it turns the sprinkler off and logs it.
  5. It adds 2 L/s for each running sprinkler to the water total.
- **Postcondition:** soil moisture stays between about 30% and 90%, so trees never wilt from drought, unless a pipe is closed or the module is off.

#### UC14: Protect from frost (Heating module)

- **Actor:** Game clock
- **Main flow:**
  1. The module reads the air temperature sensor (±0.2°C noise).
  2. Below 3°C it turns the heaters on and logs it. Above 6°C it turns them off and logs it.
  3. While the heaters are on, the bed temperature is the air temperature + 8°C, and energy use builds up (6 kW).
- **Alternative:** 1a. The module is off and the air is below 0°C: the system logs an ALERT frost warning once per cold spell, and frost damages trees that are frost-sensitive.

#### UC15: Control pests (Pest control module)

- **Actor:** Game clock
- **Main flow:**
  1. Pest sensors report the cockroaches that are feeding.
  2. A newly detected cockroach is watched, and the system logs a WARN.
  3. Once it has fed for 2 s, the module sprays it if the tank holds at least one dose (15).
  4. The cockroach dies in a pesticide puff, and the system logs an INFO with the tank level.
  5. The tank refills at 0.6 units/s.
- **Alternative:** 3a. The tank is empty: the system logs an ALERT once, and the cockroaches keep eating until the gardener stomps them (UC6) or the tank refills.

#### UC16: Replant automatically (Replanting module)

- **Main flow:**
  1. For each plot, the module notes when it became DEAD or EMPTY.
  2. A plot that has been DEAD for 1 day is chopped down and replanted in the same step. A plot that has been EMPTY for half a day is replanted.
  3. The system logs the removal with the original cause of death, and the new species.

#### UC17: Simulate time, weather and growth

- **Actor:** Game clock
- **Main flow, every frame:**
  1. The frame time is multiplied by the speed and split into steps of at most 0.25 s.
  2. For each step:
     1. The clock advances. A new day writes a daily report, and a new season or year is logged.
     2. The weather counts down. When it runs out, a new event is picked using the season's odds, and the change is logged.
     3. Each module runs (UC13-UC16), each inside its own guard.
     4. Each plot updates:
        - the soil dries (by weather, season and species) or is watered (sprinkler, rain);
        - the tree grows (by moisture, season and pollination);
        - damage is applied from drought, wind, rot, frost and pests, or the tree recovers;
        - the state changes and is logged.
     5. Mature trees past 10 s are harvested.
  3. Insects move (capped at 4x speed).
  4. Everything is redrawn, and the sidebar text is refreshed 4 times a second.
  5. The log file is flushed once a second.

#### UC18: Recover from an error

- **Actor:** System
- **Main flow:**
  1. An exception is thrown inside a module, a plot update, a plot drawing or a frame.
  2. The guard around that part catches it.
  3. The system logs an ERROR with the location and stack trace, and increments "Errors recovered" in the status bar.
  4. The rest of the step continues. The next frame runs normally.
- **Alternative:** 1a. The exception happens outside any guard (for example, on another thread): the default uncaught-exception handler logs it the same way.

---

## 5. User stories

| ID | As a… | I want… | So that… | Acceptance criteria |
|---|---|---|---|---|
| US1 | gardener | trees to be watered automatically when their soil is dry | I don't have to click every bed | A bed below 30% has its sprinkler on within one step; it turns off at 90%; each change is logged. |
| US2 | gardener | to shut off a row's water | I can save water or test drought | Clicking the spigot turns it red; that row's sprinklers stop and don't restart while it is closed. |
| US3 | gardener | to stomp cockroaches | I can save a tree the pest system hasn't reached yet | Clicking a cockroach removes it and leaves a splat; the plot isn't watered; the stomp count goes up. |
| US4 | gardener | dead trees to be cleared and replanted | the garden refills itself | A tree dead for 1 day is replaced; the log names the cause of death and the new species. |
| US5 | gardener | to chop and replant a dead tree myself | I don't have to wait a day | Three clicks fell it; one more click plants a new tree. |
| US6 | gardener | to see sensor readings and module status at a glance | I know whether the garden is healthy | The Systems tab updates 4 times a second with temperature, moisture, pests, and each module's status. |
| US7 | evaluator | to switch each module off | I can check that it really keeps the garden alive | Unticking a module stops it; with Watering off, trees die of drought within days. |
| US8 | evaluator | a log of every event and interaction | I can reconstruct what happened | Every user click, sensor-triggered action, weather change, death (with its cause) and daily summary is in the log file and the Log tab. |
| US9 | evaluator | to filter and search the log | I can find a specific event quickly | Category, level and text filters combine; searching `Plot 14:` shows only that plot. |
| US10 | evaluator | the garden to run for 24 hours on its own | I can check its stability | Running unattended produces no crash; any caught error appears as an ERROR line, and the garden continues. |
| US11 | gardener | to speed up time | I can watch seasons and years pass | Speeds of 1x to 256x are available; at 256x a year takes about 14 real seconds. |
| US12 | gardener | different species with different strengths | my choices and the weather matter | In a windstorm cherry and maple lose health while pine doesn't; in frost, pine and juniper hardly suffer. |
| US13 | new user | an in-app manual | I can learn the controls without leaving the app | The Help button opens a window that lists every control, module and log feature. |
| US14 | gardener | to force the weather | I can see how the garden copes | Choosing an event starts it at once and logs it. |

---

## 6. Scenarios

**S1: A summer drought, with the modules on.**
1. In month 5 a drought begins, and the log warns: *soil dries 3x faster, maples are thirstiest*.
2. Moisture readings drop quickly, and the Watering module switches sprinklers on all over the field (the WATERING log fills with ON lines).
3. The Japanese maples dry fastest and get watered most often.
4. No tree reaches 15%, so none wilt.
5. When the drought ends, the daily report shows a high water total for those days.

**S2: The same drought with Row 3's spigot closed.**
1. The gardener closes Row 3.
2. The module can no longer water that row's beds.
3. Their moisture falls below 15%, and the log warns as each tree starts wilting.
4. The health bars fall at 5 per second.
5. The gardener reopens the spigot. The sprinklers come back on, and the trees recover at 2 per second.
6. If the gardener waits too long, the trees die. The log shows `DIED from drought (dry soil)`, and the Replanting module replaces each tree a day later.

**S3: A winter night.**
1. At 03:00 in month 11, the air reaches -3°C.
2. The temperature sensor reads below 3°C, so the heaters switch on (logged), and the beds glow orange.
3. The beds sit at about +5°C, so no frost damage happens.
4. At 10:00 the air passes 6°C and the heaters switch off.
5. If an evaluator unticks Heating first, the log raises `FROST … heaters are off`, and the cherry trees lose 2.5 health per second until morning.

**S4: A cockroach attack in summer.**
1. Cockroaches arrive more often in summer.
2. One crawls under the fence toward Plot 22 (logged WARN WILDLIFE), and starts eating (logged ALERT).
3. The pest sensors detect it. After 2 s the module sprays it (logged INFO PEST).
4. Three more cockroaches arrive close together, and the tank runs dry (logged ALERT PEST).
5. The gardener stomps the rest by hand. Each stomp is logged as USER.

**S5: An unattended long run.**
1. The evaluator sets the speed to 256x and leaves.
2. Many in-game years later, the status bar shows `Errors recovered: 0`, and every module status is still updating.
3. The log files have split into 20 MB parts, and each part opens with the format header.
4. The daily reports show trees being planted, harvested and occasionally lost to pests, with every loss replaced.

**S6: Recovering from an error.**
1. A rendering problem throws an exception while one plot is being drawn.
2. That plot's guard catches it and logs `ERROR SYSTEM Drawing Plot 7` with the stack trace.
3. The other 39 plots draw normally, and the garden continues. The status bar shows `Errors recovered: 1`.

---

## 7. Candidate classes (noun analysis)

The nouns in the problem statement and use cases became these classes. The verbs became their responsibilities.

| Noun (problem domain) | Class | Responsibilities | Collaborators |
|---|---|---|---|
| garden | `Garden` | Owns the plots, pipes, clock, weather and modules; advances the simulation one step at a time; harvests and replants; calculates air temperature; writes daily reports | Plot, Pipe, GameClock, WeatherSystem, GardenModule, Critters, GardenLog |
| plot / bed | `Plot` | Soil moisture, health, growth and state; reacting to weather, temperature and pests; planting, chopping and watering | Species, BonsaiGenerator, Glyph, Weather, GameClock.Season |
| tree shape | `BonsaiGenerator`, `Glyph`, `BranchType` | Grows a tree as a list of positioned characters (cbonsai algorithm) | — |
| species | `Species` (enum) | Leaves, colours, and weather and frost sensitivity | — |
| pipe, spigot | `Pipe`, `Spigot` | A row's water line that can be opened or closed; the chrome tap control | Plot |
| time, season | `GameClock`, `GameClock.Season` | Calendar; season modifiers | — |
| weather | `Weather` (enum), `WeatherSystem` | Event types and their effects; choosing random events | GameClock.Season |
| subsystem / module | `GardenModule` (abstract) | Name, on/off, `run` each step, `shutDown`, status | Garden, GardenLog |
| watering system | `WateringSystem` | Sprinkler control with hysteresis; water totals | MoistureSensor, Plot, Pipe |
| heating system | `HeatingSystem` | Heater control; bed temperature; frost alerts | TemperatureSensor |
| pest control | `PestControlSystem` | Confirming and spraying pests; the tank | PestSensor, Critters |
| replanting | `ReplantingSystem` | Clearing dead trees and replanting | Plot, Garden |
| sensor | `Sensor` (interface), `MoistureSensor`, `TemperatureSensor`, `PestSensor` | Noisy readings | Plot, Garden, Critters |
| insect | `Bug` (abstract), `Ladybug`, `Bee`, `Cockroach` | Movement, drawing, pollinating and eating | Critters, PlotController |
| wildlife | `Critters` | Spawning, updating and drawing insects; stomping and spraying | Bug, PlotController |
| log | `GardenLog`, `GardenLog.Entry`, `Level`, `Category` | Recording, file output, rotation and the in-memory list | GameClock |
| user interface | `GardenApp`, `GardenController`, `PlotController`, `HelpWindow`, `Irrigation`, `WeatherView`, `GrassTexture` | Window, input, drawing | Garden and the model classes |

### Key design decisions

- **Model and view are separated.** `Garden`, `Plot`, the modules, the sensors, the clock and the weather import no JavaFX. `GardenController` and `PlotController` read the model and forward the gardener's clicks to it, which lets the coming monitoring API read the model directly. There are two deliberate exceptions:
  - `GardenLog` keeps its recent entries in a JavaFX `ObservableList`, so the Log tab updates by itself.
  - `Critters` draws the insects and finds trees through `PlotController`s. Moving insect positions into the model would be the next refactoring step if the monitoring API needs them.
- **Template method.** `GardenModule.update()` is `final`. It checks whether the module is enabled, then calls the subclass's `run()`. Switching a module off calls `shutDown()`. New modules only implement `run`, `shutDown` and `getStatus`.
- **Strategy-like sensors.** Modules depend on the `Sensor` interface, not on the plots directly. This puts noise and failure handling in one place, and lets a real or mock sensor be swapped in.
- **Polymorphic insects.** `Critters` updates and draws every `Bug` through the abstract `update` and `draw` methods. Each subclass supplies its own behaviour.
- **Static logger.** `GardenLog` is a utility class, so any class can log without being handed a reference. Its methods are `synchronized` and never throw.

---

## 8. Traceability

| Requirement | Satisfied by |
|---|---|
| F1, F4, F22 | `Plot`, `Garden`, `GardenController.buildField` |
| F2 | `BonsaiGenerator`, `Glyph`, `BranchType` |
| F3 | `Species` |
| F5 | `GameClock`, `GameClock.Season` |
| F6, F7 | `WeatherSystem`, `Weather`, `Garden.getAirTemperature` |
| F8, F9 | `Critters`, `Bug`, `Ladybug`, `Bee`, `Cockroach` |
| F10 | `Pipe`, `Spigot`, `Irrigation` |
| F11 | `WateringSystem`, `MoistureSensor` |
| F12 | `HeatingSystem`, `TemperatureSensor` |
| F13 | `PestControlSystem`, `PestSensor` |
| F14 | `ReplantingSystem` |
| F15, F17 | `GardenModule`, `GardenController.buildModuleCards` / `refreshText` |
| F16 | `GardenController`, `PlotController.onClicked` |
| F18, F20, NF3, NF4 | `GardenLog`, `Garden.dailyReport` |
| F19 | `GardenController.buildLogView` / `refilterLog` |
| F21 | `HelpWindow`, `help.md` |
| NF1 | `Garden.safely`, guarded frame loop in `GardenController`, uncaught-exception handler in `GardenApp` |
| NF2 | Modules switchable at run time; verified by test (all 40 trees died with the modules off) |
| NF5 | Sub-stepping in `GardenController.update` |
| NF7 | `GardenModule`, `Sensor` |
| NF8 | README "References" section |

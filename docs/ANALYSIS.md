# Computerized Bonsai Garden: Requirements and Object-Oriented Analysis

CSEN 275 garden project. This document covers:

1. the problem statement,
2. the feature list (functional and non-functional requirements),
3. actors and use cases,
4. user stories with acceptance criteria,
5. scenarios,
6. the candidate classes found by analysis, and
7. a requirement-to-class traceability table.

It covers both ways the garden runs: the JavaFX GUI, and the scripted **Garden Simulation API** that *Gardening System APIs* asks for (no GUI, everything logged to `log.txt`; see [API.md](API.md)).

The UML diagrams are in [UML.md](UML.md).

> **Units:** all times are *simulated* seconds unless stated otherwise. 10 simulated seconds make one in-game day. At 1x speed, a simulated second is a real second. In an API run, one simulated day (10 simulated seconds) lasts one real hour.

---

## 1. Problem statement

A gardener wants a computerized garden that looks after a large bed of bonsai trees with as little human help as possible. The garden must:

- keep many plants of several species alive through changing weather and seasons, while the trees face real threats: drying soil, windstorms, waterlogged roots, frost and cockroaches;
- detect those threats with **sensors** and respond through **independent automated subsystems** (watering, heating, pest control and replanting). Each subsystem can be switched off to show that the garden really depends on it;
- let a person step in at any time: water, harvest, chop down dead trees, plant, stomp pests, shut off water lines, force weather, change speed;
- record **every** event and interaction in a detailed, readable, searchable log;
- run unattended for at least 24 hours without crashing, handling every exception;
- explain itself through an in-app help manual and a guide to the log;
- offer a **scripted API** that a grader's program can drive with no GUI: plant the garden from a config file, then make it rain, set the temperature or release parasites once a simulated day (one real hour), and report the garden's state after 24 days, all recorded in `log.txt`.

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
| F23 | **Rare events:** a Hellfire Rider occasionally rides along one row and sets its trees on fire. A burning tree loses 7 health/s and its soil dries fast; sprinklers, rain or a click of water put the fire out. |
| F24 | **Rare events:** a Green Brute occasionally smashes 3-5 trees (45 health each). The gardener can calm him with 5 clicks. Both events can also be triggered from the menu. |
| F25 | **Garden Simulation API:** `GardenSimulationAPI` has `initializeGarden()`, `getPlants()`, `rain(int)`, `temperature(int)`, `parasite(String)` and `getState()` exactly as the API document specifies, plus `parasites(String)` and `getStatus()`, the names its example script uses. It runs with no GUI, and a copy in the default package means a script needs no imports. |
| F26 | **Config file:** `initializeGarden()` plants the garden from `garden-config.json` (`{"plants": [{"name": ..., "amount": ...}]}`). Every variety is always planted, and there are always at least 10 plants. A missing or broken file falls back to 10 of each species; unknown names and bad amounts are skipped. Each fix is logged as a warning. |
| F27 | **API time:** `initializeGarden()` starts day 1. Each real hour is one simulated day of 40 steps (0.25 simulated seconds each). A background clock keeps the garden and the log moving between calls, and every call first catches up with the clock. Health carries over from day to day. |
| F28 | **Rain:** `rain(mm)` falls evenly over the rest of the day. Each bed gains 30 moisture points per daily water requirement of rain (Juniper 10 mm, Pine 15, Cherry Blossom 20, Japanese Maple 25), and the soil doesn't dry while it rains. The next day is dry unless it rains again. |
| F29 | **Temperature:** `temperature(°F)` sets the day's temperature (40-120°F expected), and it goes back to 68°F when the day ends. At 95°F and above the soil dries 3x faster. Beds above 40°C (104°F) cause heat stress, which hurts each species differently. |
| F30 | **Parasites:** `parasite(name)` infests every living plant whose species is vulnerable to it. Names match ignoring case, spaces, `-`/`_` and a plural "s". An infestation eats 3 health/s × severity (30 a day), and the plant can't heal while it feeds. A repeat infestation raises the severity, up to 2. |
| F31 | **API modules:** four modules run every step: Watering (shared with the GUI), **Drainage** (a bed's drain opens above 93% moisture and closes below 85%, removing up to 8 points/s), **Climate control** (heating cables below 37°F add 8°C; a shade cloth and misting above 95°F take off 6°C) and **Parasite control** (finds an infestation after 1 s, then sprays every 1 s, each spray killing 40%). Spraying never restores health. |
| F32 | **API log:** `log.txt` opens with a legend, then has one line per event: real time, simulated day, level, event, event value, plants alive/total and a plain-English explanation (the `DAY, EVENT, EVENT_VALUE, PLANTS_ALIVE` format the API document suggests). Each day ends with an `END_OF_DAY` summary, and `getState()` adds a report by species, by plant and by module. |

### 2.2 Non-functional requirements

| ID | Requirement |
|---|---|
| NF1 | **Robustness:** the garden runs unattended for 24+ hours without crashing. Every module, plot update, plot drawing and frame is guarded. Caught exceptions are logged at ERROR level with a stack trace, and the garden continues. |
| NF2 | **Honesty:** nothing is hardcoded to keep plants alive. Switching modules off must lead to deaths. |
| NF3 | **Log size:** a log file starts a new part at 20 MB. The in-app list keeps the last 5,000 entries. |
| NF4 | **Log safety:** logging never throws. If the log file can't be written, logging continues in memory. |
| NF5 | **Accuracy at speed:** the simulation advances in steps of at most 0.25 s, so 256x gives the same results as 1x, just faster. |
| NF6 | **Usability:** a JavaFX UI defined in FXML, with a consistent visual style, tooltips on plots and spigots, and readable log rows coloured by level. |
| NF7 | **Extensibility:** modules share an abstract `GardenModule` base and sensors share a `Sensor` interface, so new subsystems plug in without changing the main loop. The scripted API proves it: it adds three modules of its own without changing `GardenModule` (F31). |
| NF8 | **Attribution:** all external material is credited (cbonsai, JavaFX, AI assistance). |
| NF9 | **API robustness:** no API method ever throws. Odd input is logged as WARN and handled (negative rain counts as 0 mm, an unknown parasite infests nothing), caught exceptions are logged as ERROR, and a call made before `initializeGarden()` initializes the garden first. |
| NF10 | **Relative paths:** `log.txt`, `garden-config.json` and the GUI's `logs/` folder are all found relative to the working directory. No path is absolute. |
| NF11 | **API honesty:** with the API's modules switched off, plants die: untreated parasites kill a plant in about 3½ days (checked by the unit tests). |

---

## 3. Actors

| Actor | Kind | Description |
|---|---|---|
| **Gardener** | Primary, human | Watches the garden and steps in: watering, harvesting, chopping, planting, stomping, spigots, weather, speed, modules. |
| **Evaluator** | Secondary, human | Reads the log and help, switches modules off to test them, and leaves the garden running unattended. After an API run, reads `log.txt`. In practice this is the gardener with a testing goal. |
| **Game clock** | Secondary, system | Drives time forward. It starts the automatic use cases on every simulation step. |
| **Environment** | Secondary, system | The weather and temperature, which act on the plots. |
| **Wildlife** | Secondary, system | Bees, ladybugs and cockroaches moving on the field. |
| **Intruders** | Secondary, system | The Hellfire Rider and the Green Brute, who turn up now and then and damage trees. |
| **Test script** | Primary, external program | The graders' Java program. It drives the Garden Simulation API: `initializeGarden()`, then `rain()`, `temperature()` or `parasite()` once a simulated day, and `getState()` at the end. |
| **Background clock** | Secondary, system | In an API run, the `garden-clock` thread that advances the simulation 40 times a simulated day. |

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
| UC19 | Fight a fire (Hellfire Rider) | Gardener / Watering module |
| UC20 | Calm the Green Brute | Gardener |
| UC21 | Initialize the garden from the config file | Test script |
| UC22 | Get the living plant types | Test script |
| UC23 | Make it rain | Test script |
| UC24 | Set the day's temperature | Test script |
| UC25 | Trigger a parasite infestation | Test script |
| UC26 | Report the garden's state | Test script |
| UC27 | Advance the simulation and end each day | Background clock |
| UC28 | Respond automatically (watering, drainage, climate, parasite control) | Background clock (API modules) |

UC1-UC20 belong to the JavaFX GUI and UC21-UC28 to the scripted API. Their use case diagrams are sections 1 and 2 of [UML.md](UML.md).

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
- **Alternatives:**
  - 1a. After an API run there is no Log tab: the evaluator opens `log.txt`, which starts with a legend. Searching `END_OF_DAY` steps through the days, and `ALERT` finds deaths.
  - 2a. With **Follow** unticked, the list stays where it is while new entries arrive.

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
- **Alternatives:**
  - 1a. The exception happens outside any guard (for example, on another thread): the default uncaught-exception handler logs it the same way.
  - 1b. In an API run, every API method, the background clock, and each module and plot step have their own guard. The ERROR goes to `log.txt`, and the method returns normally, so the test script never sees an exception.

#### UC19: Fight a fire

- **Actors:** Hellfire Rider (trigger), Watering module, Gardener
- **Main flow:**
  1. The rider enters at one end of a row and rides across. Each tree he passes catches fire (logged ALERT PLANT `ON FIRE`).
  2. Each burning tree loses 7 health/s, and its soil dries 10%/s.
  3. When the soil drops below 30%, the Watering module turns that bed's sprinkler on, which makes the fire burn out five times faster.
  4. The fire goes out, and the system logs `the fire is out thanks to the sprinkler`.
- **Alternatives:**
  - 3a. The gardener clicks the burning tree: the water puts the fire out at once (logged USER).
  - 3b. It is raining: the fire burns out seven times faster.
  - 3c. The Watering module is off and nobody helps: the tree burns to death (`DIED from fire`) and is drawn as charcoal.

#### UC20: Calm the Green Brute

- **Actors:** Green Brute (trigger), Gardener
- **Main flow:**
  1. The brute enters from the side (logged ALERT INTRUDER), walks to a living tree and smashes it, taking 45 health (logged WARN INTRUDER).
  2. He repeats this for 3-5 trees, then leaves.
  3. The gardener clicks him. Each poke is logged as USER. On the fifth poke he calms down and leaves early.
- **Postcondition:** smashed trees heal at the normal recovery rate if nothing else is hurting them.

#### UC21: Initialize the garden from the config file

- **Actor:** Test script
- **Precondition:** none. Calling it again starts a fresh run.
- **Main flow:**
  1. The script calls `initializeGarden()`.
  2. The system stops any earlier run, starts a new `log.txt` with its legend, and logs `INIT`: day 1 begins now.
  3. The system reads `garden-config.json` from the working directory.
  4. It plants each listed species, 8 beds to a water pipe, with starting soil moisture staggered between 55% and 90% so the sprinklers don't all start together.
  5. It logs a `CONFIG` line listing what was planted, and an `INIT` line listing the four modules.
  6. It starts the background clock (UC27).
- **Alternatives:**
  - 3a. The file is missing or isn't valid JSON: the system plants 10 of each species and logs a `CONFIG` warning.
  - 3b. An entry has an unknown name, no name, or a bad amount: that entry is skipped with a warning.
  - 3c. A species is missing, or there are fewer than 10 plants: the system adds plants until every variety is present and there are at least 10, and logs a warning.
- **Postcondition:** at least 10 plants are alive, every variety among them, and the clock is running.

#### UC22: Get the living plant types

- **Actor:** Test script
- **Main flow:**
  1. The script calls `getPlants()`.
  2. The system catches up with the clock (UC27).
  3. It returns a map of three parallel lists, one entry per species that still has a living plant: `plants` (names), `waterRequirement` (mm a day) and `parasites` (what each species is vulnerable to).
  4. It logs a `GET_PLANTS` line with the same values.
- **Alternative:** 3a. Every plant of a species has died: that species is left out of all three lists.

#### UC23: Make it rain

- **Actor:** Test script
- **Main flow:**
  1. The script calls `rain(mm)`.
  2. The system catches up with the clock (UC27).
  3. For each species it works out the moisture the rain adds: 30 points per daily water requirement, so 20 mm adds 60 points to a juniper's bed but 24 to a maple's.
  4. The rain falls evenly over the rest of the day, and the soil doesn't dry while it rains.
  5. It logs `RAIN <mm> mm` with the points each species gets.
- **Alternatives:**
  - 1a. The amount is negative: the system logs a warning and treats it as 0 mm.
  - 1b. It already rained today: the amounts add up.
  - 4a. A bed becomes waterlogged: Drainage opens its drain (UC28).
- **Postcondition:** at the end of the day the rain stops (`RAIN_RESET`), unless the script makes it rain again the next day.

#### UC24: Set the day's temperature

- **Actor:** Test script
- **Main flow:**
  1. The script calls `temperature(°F)`.
  2. The system catches up with the clock (UC27).
  3. It sets the air temperature for the rest of the day, and logs `TEMPERATURE` with the value in °F and °C and what it means for the plants.
  4. Climate control reacts on the next step (UC28): bed heating below 37°F, shade and misting above 95°F.
- **Alternatives:**
  - 1a. The value is outside 40-120°F: the system logs a warning and uses it anyway.
  - 3a. At 95°F and above, the soil dries 3x faster, so the sprinklers run more often.
  - 3b. From about 115°F the beds pass 40°C even under the shade cloth: the trees suffer heat stress, maples and cherries most.
- **Postcondition:** at the end of the day the temperature goes back to 68°F (`TEMPERATURE_RESET`).

#### UC25: Trigger a parasite infestation

- **Actor:** Test script
- **Main flow:**
  1. The script calls `parasite(name)`.
  2. The system catches up with the clock (UC27).
  3. It finds every species vulnerable to that parasite, ignoring case, spaces, `-`/`_` and a plural "s".
  4. Every living plant of those species is infested at severity 1, and the system logs `PARASITE` with how many plants and which species.
  5. Each step, the infestation eats 3 health/s × severity, and the plant cannot heal while it feeds.
  6. Parasite control finds and sprays it (UC28).
- **Alternatives:**
  - 1a. The name is empty or missing: the system logs a warning and nothing happens.
  - 3a. No species is vulnerable (for example `"insects"`): nothing is infested, and the warning lists the parasites the garden knows.
  - 4a. A plant is already infested: its severity rises by 1, up to 2.
- **Postcondition:** the infestation clears after about 4 sprays. The plant keeps the damage and heals on its own afterwards.

#### UC26: Report the garden's state

- **Actor:** Test script
- **Main flow:**
  1. The script calls `getState()`, usually after 24 days.
  2. The system catches up with the clock (UC27).
  3. It logs a `STATE` line: days done, plants alive and dead, infestations, and how many errors, warnings and alerts were logged.
  4. Under it, it writes:
     - **BY SPECIES:** alive/total, average health now, the lowest health during the run, water needs and parasites;
     - **EVERY PLANT:** alive or dead, health, soil moisture, the lowest health with when and why, and any running sprinkler or infestation; for a dead plant, the day and cause of death;
     - **MODULES:** each module's status line;
     - the weather now.
- **Postcondition:** an evaluator can judge the run from `log.txt` alone (UC11).

#### UC27: Advance the simulation and end each day

- **Actor:** Background clock
- **Main flow, 40 times per simulated day:**
  1. The clock works out how many steps should have happened since `initializeGarden()`, and runs any that are missing.
  2. For each step:
     1. Simulated time advances 0.25 s. The day counts as rainy if it rained, as a drought at 95°F and above, and otherwise as clear.
     2. Each module runs (UC28), each inside its own guard.
     3. Each plot takes parasite damage if it is infested, then updates: soil drying or rain, sprinklers and drains, heat or frost damage, and healing of 2 health/s while nothing hurts it.
     4. Each death is logged as ALERT with its cause.
  3. On the 40th step of a day, the system logs `END_OF_DAY` (average and lowest health, infestations, sprinkler starts, water used, sprays and deaths), then resets the rain (`RAIN_RESET`) and the temperature (`TEMPERATURE_RESET`) if they were set.
- **Alternative:** 1a. A script call arrives between steps: it runs this catch-up first, so each event lands on the right day.

#### UC28: Respond automatically (API modules)

- **Actor:** Background clock
- **Precondition:** the modules are on (tests can switch them off).
- **Main flow, every step:**
  1. **Watering:** a moisture sensor in each bed turns its sprinkler on below 30% and off at 90%.
  2. **Drainage:** a bed reading above 93% has its drain opened, which removes up to 8 moisture points/s until the bed is below 85%. Openings are logged as `DRAINAGE`.
  3. **Climate control:** below 3°C (37°F), bed heating cables add 8°C; above 35°C (95°F), a shade cloth and misting take off 6°C. Each switch is logged as `CLIMATE`.
  4. **Parasite control:** 1 s after an infestation starts, the sensors find it (logged WARN `PEST_CONTROL`). It is sprayed every 1 s, each spray killing 40%, and once it is below 15% it is cleared (logged INFO).
- **Alternatives:**
  - 2a. More than about 40 mm of rain a day: the drains can't keep up, and pines can get root rot.
  - 4a. Parasite control is off: the infestation is never sprayed, and the plant dies in about 3½ days.
- **Postcondition:** spraying never restores health. Plants only heal on their own (F30).

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
| US15 | gardener | surprise disasters | the garden stays exciting and I have to react | At 1x speed a Hellfire Rider or Green Brute turns up every 15-35 in-game days; both can be triggered from the menu. |
| US16 | gardener | to put out fires | I can save a row the rider set alight | Clicking a burning tree puts the fire out; sprinklers and rain also put fires out. |
| US17 | grader | to seed the garden from a config file | every test run starts from a known garden | `initializeGarden()` plants what `garden-config.json` lists; with no file, 10 of each species; always at least 10 plants and every variety. |
| US18 | grader | `getPlants()` to tell me each living type's water needs and parasites | I can pick realistic rain amounts and parasites | The three lists line up by index, and a species leaves them when its last plant dies. |
| US19 | grader | rain and temperature to last one simulated day | each day's call is a fresh condition | Rain stops and the temperature returns to 68°F at the end of the day, and both resets are logged. |
| US20 | grader | pest control that doesn't heal plants | the garden's survival is honest | When the parasites are cleared, health has not gone up; it recovers at 2 health/s only afterwards (checked by a unit test). |
| US21 | grader | the API never to throw or crash | my 24-hour script runs to the end | Negative rain, out-of-range temperatures, unknown or empty parasite names and calls before `initializeGarden()` are logged as WARN; caught exceptions as ERROR; the script carries on. |
| US22 | grader | a `log.txt` I can read without help | I can grade the run from the log alone | The file opens with a legend; every line has the day, level, event, value and plants alive; each day ends with `END_OF_DAY`; `getState()` lists every plant. |

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

**S7: The graders' 24-day API run.**
1. The script calls `initializeGarden()`. `log.txt` shows 40 plants planted and the four modules running.
2. On day 1 it calls `rain(25)`. The junipers' beds gain 75 moisture points and the maples' 30, and drains open under the wettest beds.
3. On day 2 it calls `temperature(104)` and `parasite("aphids")`. The shade cloth goes on, the soil dries 3x faster and the sprinklers run more often. 30 Cherry Blossom, Japanese Maple and Pine plants are infested.
4. About 2.4 simulated hours later, parasite control finds the aphids and starts spraying. After about 4 sprays they are cleared, leaving the plants around 94% health, and they heal over the rest of the day.
5. Each day ends with an `END_OF_DAY` line, and the rain and temperature reset.
6. After 24 days, `getState()` lists every plant alive, the lowest health each one reached, and 0 errors.

**S8: Parasites with pest control switched off.**
1. A test switches parasite control off and calls `parasite("pine beetles")`.
2. All 10 pines are infested. Each loses 30 health a day and cannot heal.
3. Early on day 4 each pine dies, logged as ALERT with the infestation as the cause.
4. `getPlants()` no longer lists Pine, and `getState()` shows each pine as DEAD with the day and cause.

**S9: Bad input from a script.**
1. A script calls `rain(-50)` before `initializeGarden()`.
2. The system initializes the garden first and logs a warning, then logs that negative rain counts as 0 mm.
3. Calls to `temperature(9999)`, `parasite("")` and `parasite("insects")` each log a WARN explaining what happened.
4. The garden keeps running, and `grep -c '| ERROR' log.txt` prints 0.

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
| intruder | `HellfireRider`, `GreenBrute` (both extend `Bug`) | Riding a row and igniting it; walking to trees and smashing them; being poked | Critters, Plot, PlotController |
| pixel art | `PixelSprite` | Drawing a character grid as square pixels, optionally mirrored | — |
| wildlife | `Critters` | Spawning, updating and drawing insects; stomping and spraying | Bug, PlotController |
| log | `GardenLog`, `GardenLog.Entry`, `Level`, `Category` | Recording, file output, rotation and the in-memory list | GameClock |
| user interface | `GardenApp`, `GardenController`, `PlotController`, `HelpWindow`, `Irrigation`, `WeatherView`, `GrassTexture` | Window, input, drawing | Garden and the model classes |
| scripted API | `GardenSimulationAPI` (in `garden.api` and the default package), `GardenSimulationAPI.Options` | The six API methods and their aliases; the background clock; catching up; never throwing | ApiGarden, ApiLog, GardenConfig, GardenLog |
| API garden | `ApiGarden` | The plants, infestations, and today's rain and temperature; the 40 steps a day; end-of-day resets; the `getState()` report | Garden, Plot, Species, the API modules, ApiLog |
| config file | `GardenConfig`, `Json` | Reading `garden-config.json`; enforcing the minimums; the built-in defaults | Species |
| drain | `Drainage` | Opening and closing each bed's drain | MoistureSensor, Plot |
| climate control | `ClimateControl` | Heating cables and shade cloth; bed temperature | ApiGarden |
| infestation, parasite control | `ParasiteControl`, `ParasiteControl.Infestation` | Severity, detection and spraying | Plot, ApiGarden |
| API log | `ApiLog` | `log.txt`: the legend, aligned columns, and counts of warnings, alerts and errors | ApiGarden |
| test script | `GardenSimulator` | An example 24-day run shaped like the graders' script | GardenSimulationAPI |

### Key design decisions

- **Model and view are separated.** `Garden`, `Plot`, the modules, the sensors, the clock and the weather import no JavaFX. `GardenController` and `PlotController` read the model and forward the gardener's clicks to it, which lets the scripted API reuse the model directly. `GardenLog` has no JavaFX code either: it hands each entry to a `Listener`. The GUI's listener fills the Log tab, and the API's copies plant events into `log.txt`. One exception remains:
  - `Critters` draws the insects and finds trees through `PlotController`s, and `Garden`, `PestControlSystem` and `PestSensor` refer to it. Because of this link, compiling the API still needs JavaFX. Moving the insects' drawing out of `Critters` is the next refactoring step.
- **Template method.** `GardenModule.update()` is `final`. It checks whether the module is enabled, then calls the subclass's `run()`. Switching a module off calls `shutDown()`. New modules only implement `run`, `shutDown` and `getStatus`.
- **Strategy-like sensors.** Modules depend on the `Sensor` interface, not on the plots directly. This puts noise and failure handling in one place, and lets a real or mock sensor be swapped in.
- **Polymorphic insects.** `Critters` updates and draws every `Bug` through the abstract `update` and `draw` methods. Each subclass supplies its own behaviour.
- **Facade.** `GardenSimulationAPI` hides the whole simulation behind the six methods the API document specifies. A subclass in the default package lets a script use it with no imports.
- **Reuse over copying.** `ApiGarden` builds on the same `Garden`, `Plot`, `Species`, `WateringSystem` and `GardenModule` as the GUI, so both behave the same. The API adds only what the script controls (rain, temperature, parasites) and three modules.
- **Catch-up clock.** Instead of trusting a timer, the API works out from the real time how many steps should have happened and runs the missing ones, both on the background thread and at the start of every call. Days stay exact even when the machine is busy.
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
| F23, F24 | `HellfireRider`, `GreenBrute`, `PixelSprite`, `Plot.ignite` / `smash`, `Critters.summonRider` / `summonBrute` / `pokeAt` |
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
| F25 | `GardenSimulationAPI` (both copies) |
| F26 | `GardenConfig`, `Json`, `garden-config.json` |
| F27 | `GardenSimulationAPI.catchUp` / `startTicker`, `ApiGarden.tick` / `endOfDay` |
| F28, F29 | `ApiGarden.rain` / `temperature`, `Plot.tick`, `ClimateControl` |
| F30 | `ApiGarden.parasite`, `ParasiteControl.Infestation`, `Plot.parasiteDamage`, `Species.isVulnerableTo` |
| F31 | `WateringSystem`, `Drainage`, `ClimateControl`, `ParasiteControl` |
| F32 | `ApiLog`, `ApiGarden.report` |
| NF9 | try/catch in every `GardenSimulationAPI` method, `ApiGarden.safely`, the guarded background clock |
| NF10 | `GardenSimulationAPI.Options.directory`, `GardenConfig.load`, `GardenLog.start` |
| NF11 | `GardenSimulationAPITest` (modules switched off) |

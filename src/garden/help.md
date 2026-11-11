# Computerized Bonsai Garden: User Manual

The garden is a fenced field of 40 bonsai beds in 5 rows of 8. Each tree grows with the cbonsai branching algorithm. Water pipes run above every row, and four automated modules keep the garden alive: watering, heating, pest control and replanting. You can step in by hand at any time, or switch any module off and see what happens.

## Quick start

- Run the app. The garden starts at 8:00 on day 1 of spring, Year 1.
- Watch the right-hand sidebar. The Systems tab shows sensor readings and the four modules. The Log tab shows everything that happens.
- Use the Speed box in the toolbar to go faster. At 256x a year passes in about 14 seconds.
- Hover over a bed to see its species, age, health, soil moisture and state.

## The toolbar

- **Pause / Resume** freezes the simulation. Drawing continues.
- **Replant All** replaces every tree with a new random bonsai.
- **Speed** sets the simulation speed: 1x, 4x, 16x, 64x or 256x. Insects move at most 4x so you can still catch them.
- **Life** sets how big new trees grow (8 to 24).
- **Multiplier** sets how much new trees branch (2 to 8).
- **Help** opens this manual.
- **Change weather** (in the bar below the toolbar) forces Clear, Rain, Drought or Windstorm.
- **Trigger event** brings in a Hellfire Rider or a Green Brute right away (see Rare events).

## Clicking in the garden

- **Growing or wilting tree:** click to water it by hand (+35% soil moisture). If it's on fire, the water puts the fire out.
- **Green Brute:** click him 5 times to calm him down; he leaves without smashing any more trees.
- **Mature tree** (sparkles): click to harvest it. A new tree is planted.
- **Dead tree** (grey): click 3 times to chop it down.
- **Empty bed** (stump with a green +): click to plant a new bonsai.
- **Cockroach:** click it to stomp it.
- **Spigot** (the chrome tap at the left end of each pipe): click to shut off or reopen that row's water. A blue cap means open; red means closed.

## Time and seasons

- 10 simulated seconds make one day. A month is 30 days, and a year is 12 months.
- Spring (months 1-3) brings lots of rain and fast growth.
- Summer (months 4-6) brings droughts, fast-drying soil and many cockroaches.
- Autumn (months 7-9) brings windstorms and slower growth.
- Winter (months 10-12) is cold. Nights fall below freezing, growth is slow and there are few pests.
- Night is drawn at 1x and 4x speed only.

## Weather

- **Clear:** normal conditions.
- **Rain:** waters every bed and makes cherry trees grow faster. Pine roots rot in waterlogged soil.
- **Drought:** soil dries 3 times faster and the air is about 7°C hotter.
- **Windstorm:** damages cherry and maple trees badly and juniper slightly. Pines are unharmed.

## Rare events

Every so often one of two troublemakers shows up: roughly every 15 to 35 in-game days at 1x speed. At higher speeds they come less often in game time, because visitors move at most 4x. You can also bring them in with **Trigger event**.

- **Hellfire Rider:** a skeleton biker with a flaming skull roars along one row of beds and sets every tree in that row on fire, leaving a trail of flames. He can't be stopped, but the fires can.
  - A burning tree loses 7 health per second. The fire also bakes the soil dry, which makes the Watering module switch that bed's sprinkler on.
  - A running sprinkler or rain makes the fire burn out much faster. Clicking a burning tree throws water on it and puts the fire out at once.
  - With the Watering module off, a whole row can burn to the ground. A tree that burns to death is left as black charcoal.
- **Green Brute:** a huge green brute stomps in from the side and smashes 3 to 5 trees, one after another. Each smash takes 45 health, so a tree that is already hurt can die from one blow. Click him 5 times to calm him down and he leaves early.

## The four species

- **Juniper:** drought-hardy and barely bothered by frost.
- **Cherry:** loves rain, but is fragile in wind and frost.
- **Maple:** thirsty in a drought and hurt by wind.
- **Pine:** ignores wind and frost, but its roots rot in waterlogged soil.

## Health and death

Every tree has a health bar. It loses health when:

- the soil is below 15% moisture (the tree wilts),
- a windstorm blows,
- rain falls on waterlogged soil (pines only),
- the bed is below 0°C (frost),
- or a cockroach is eating it.

When nothing is hurting a tree, it slowly recovers. At 0 health the tree dies, and the log records the cause.

## The automated modules (Systems tab)

Each module has a checkbox. Untick it to switch the module off. The subsystems really run; nothing keeps the garden alive artificially. With a module off, trees will die of that cause.

- **Watering:** moisture sensors in each bed turn that bed's sprinkler on below 30% and off at 90%. A closed spigot cuts off the whole row, whatever the module wants.
- **Heating:** a temperature sensor turns on heating cables in the beds below 3°C and off above 6°C. They warm the beds by 8°C. The cables glow orange while on.
- **Pest control:** bed sensors confirm a feeding cockroach for 2 seconds, then spray it. Each spray uses 15% of the tank, which refills slowly. When the tank is empty you have to stomp them yourself.
- **Auto-replanting:** chops down trees that have been dead for a full day and replants beds left empty for half a day.

## The log

Every event is recorded twice: in the Log tab, and in a file in the `logs` folder next to where the app was started. See the separate Log Guide for the full format.

- Filter by **category** (WEATHER, PEST, USER and so on) or by **level** (INFO, WARN, ALERT, ERROR).
- Type in the search box to find, for example, `Plot 14`, `DIED` or `frost`.
- Untick **Follow** to stop the list jumping to the newest entry.
- **Open log folder** shows the log files.
- A daily report is written at the end of every simulated day.

## Robustness

The garden is built to run unattended for 24 hours or more. Each module, each plot and each frame runs inside its own error handler. If something goes wrong it is logged at ERROR level and counted in the status bar ("Errors recovered"), and the garden carries on.

## Credits

The tree growth algorithm is a Java port of cbonsai by John Allbritten (https://gitlab.com/jallbrit/cbonsai, GPL-3.0).

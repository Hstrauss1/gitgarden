# Computerized Bonsai Garden: UML

These diagrams are written in [Mermaid](https://mermaid.js.org/), which GitHub renders directly. To export images for slides or the report, paste a block into the [Mermaid Live Editor](https://mermaid.live) and download it as PNG or SVG.

The requirements, use cases and scenarios these diagrams refer to (UC1-UC18, F1-F22) are in [ANALYSIS.md](ANALYSIS.md).

**Contents**

1. [Use case diagram](#1-use-case-diagram)
2. [Class diagram: the garden model](#2-class-diagram-the-garden-model)
3. [Class diagram: modules and sensors](#3-class-diagram-modules-and-sensors)
4. [Class diagram: wildlife](#4-class-diagram-wildlife)
5. [Class diagram: user interface and logging](#5-class-diagram-user-interface-and-logging)
6. [State diagram: a plot](#6-state-diagram-a-plot)
7. [Sequence diagram: one simulation step](#7-sequence-diagram-one-simulation-step)
8. [Sequence diagram: a cockroach attack](#8-sequence-diagram-a-cockroach-attack)
9. [Sequence diagram: the gardener clicks a plot](#9-sequence-diagram-the-gardener-clicks-a-plot)
10. [Activity diagram: watering decision](#10-activity-diagram-watering-decision)
11. [Activity diagram: frame loop with error recovery](#11-activity-diagram-frame-loop-with-error-recovery)

---

## 1. Use case diagram

Mermaid has no native use case diagram, so this is drawn as a flowchart: actors are on the outside, and use cases are the rounded shapes inside the system boundary.

```mermaid
flowchart LR
    Gardener(["👤 Gardener"])
    Evaluator(["👤 Evaluator"])
    Clock(["⏱ Game clock"])

    subgraph System["Computerized Bonsai Garden"]
        UC1(["UC1 Observe the garden"])
        UC2(["UC2 Water a plot by hand"])
        UC3(["UC3 Harvest a mature tree"])
        UC4(["UC4 Chop down a dead tree"])
        UC5(["UC5 Plant an empty bed"])
        UC6(["UC6 Stomp a cockroach"])
        UC7(["UC7 Open/close a row's water"])
        UC8(["UC8 Switch a module on/off"])
        UC9(["UC9 Force the weather"])
        UC10(["UC10 Control the simulation"])
        UC11(["UC11 Review the log"])
        UC12(["UC12 Read the help manual"])
        UC13(["UC13 Water automatically"])
        UC14(["UC14 Protect from frost"])
        UC15(["UC15 Control pests"])
        UC16(["UC16 Replant automatically"])
        UC17(["UC17 Simulate time, weather, growth"])
        UC18(["UC18 Recover from an error"])
    end

    Gardener --- UC1 & UC2 & UC3 & UC4 & UC5 & UC6 & UC7 & UC9 & UC10 & UC12
    Evaluator --- UC8 & UC9 & UC11
    Clock --- UC17
    UC17 -. "«include»" .-> UC13 & UC14 & UC15 & UC16
    UC3 -. "«include»" .-> UC5
    UC16 -. "«include»" .-> UC4
    UC16 -. "«include»" .-> UC5
    UC18 -. "«extend»" .-> UC17
```

---

## 2. Class diagram: the garden model

`Garden` is the root of the model. It owns everything and advances it one step at a time.

```mermaid
classDiagram
    direction LR

    class Garden {
        -List~Plot~ plots
        -List~Pipe~ pipes
        -GameClock clock
        -WeatherSystem weather
        -List~GardenModule~ modules
        -Critters critters
        -int baseLife
        -int baseMultiplier
        -int harvested
        -int deaths
        +step(dt double)
        +addPipe(pipe Pipe)
        +replant(plot Plot)
        +harvest(plot Plot, byHand boolean)
        +getAirTemperature() double
        -safely(what String, work Runnable)
        -dailyReport(day int)
    }

    class Plot {
        -String name
        -Species species
        -List~Glyph~ glyphs
        -double moisture
        -double health
        -boolean sprinklerOn
        -boolean dead
        -boolean empty
        -int chops
        -String lastDamage
        +plant(species, seed, life, multiplier, today)
        +tick(dt, weather, season, temperature) boolean
        +setSprinkler(on boolean)
        +water()
        +chew(damage double)
        +pollinate(dt double)
        +chop() boolean
        +getState() State
        +hasTree() boolean
    }

    class State {
        <<enumeration>>
        GROWING
        WILTING
        MATURE
        DEAD
        EMPTY
    }

    class Species {
        <<enumeration>>
        JUNIPER
        CHERRY
        MAPLE
        PINE
        +displayName String
        +droughtFactor double
        +windDamage double
        +rainGrowth double
        +rotDamage double
        +frostDamage double
    }

    class Pipe {
        -String name
        -boolean open
        +add(plot Plot)
        +toggle()
        +isOpen() boolean
    }

    class GameClock {
        -double days
        +advance(seconds double)
        +getYear() int
        +getMonth() int
        +getDayOfMonth() int
        +getHour() double
        +getSeason() Season
        +format() String
    }

    class Season {
        <<enumeration>>
        SPRING
        SUMMER
        AUTUMN
        WINTER
        +growth double
        +drain double
        +roaches double
    }

    class WeatherSystem {
        -Weather current
        -double timeLeft
        +update(dt double, season Season)
        +set(weather Weather)
        +getCurrent() Weather
    }

    class Weather {
        <<enumeration>>
        CLEAR
        DROUGHT
        RAIN
        WINDSTORM
        +drainMultiplier double
        +rainfall double
        +windy boolean
    }

    class BonsaiGenerator {
        -Random rng
        -int lifeStart
        -int multiplier
        +grow(startX int, startY int) List~Glyph~
    }

    class Glyph {
        +int x
        +int y
        +String text
        +BranchType type
    }

    class BranchType {
        <<enumeration>>
        TRUNK
        SHOOT_LEFT
        SHOOT_RIGHT
        DYING
        DEAD
        +isLeaf() boolean
    }

    Garden "1" *-- "40" Plot : plots
    Garden "1" *-- "5" Pipe : pipes
    Garden "1" *-- "1" GameClock
    Garden "1" *-- "1" WeatherSystem
    Garden "1" *-- "4" GardenModule : modules
    Pipe "1" o-- "8" Plot : feeds
    Plot --> Species
    Plot ..> State
    Plot "1" *-- "*" Glyph
    Plot ..> BonsaiGenerator : creates
    BonsaiGenerator ..> Glyph : creates
    Glyph --> BranchType
    GameClock ..> Season
    WeatherSystem --> Weather
    WeatherSystem ..> Season
    Plot ..> Weather
```

---

## 3. Class diagram: modules and sensors

Each automated subsystem extends `GardenModule`. `update()` is a **template method**: it is final, checks whether the module is enabled, then calls the subclass's `run()`. Modules read the world only through `Sensor`s.

```mermaid
classDiagram
    direction TB

    class GardenModule {
        <<abstract>>
        -String name
        -String description
        -Category category
        -boolean enabled
        +update(dt double, garden Garden)
        #run(dt double, garden Garden)*
        #shutDown(garden Garden)
        +getStatus()* String
        +setEnabled(on boolean, garden Garden)
        +isEnabled() boolean
    }

    class WateringSystem {
        +ON_BELOW = 30$
        +OFF_ABOVE = 90$
        -Map~Plot,MoistureSensor~ sensors
        -double litres
        -int running
        #run(dt, garden)
        #shutDown(garden)
        +getStatus() String
        +getAverageSoil() double
    }

    class HeatingSystem {
        +ON_BELOW = 3$
        +OFF_ABOVE = 6$
        +BOOST = 8$
        -TemperatureSensor sensor
        -boolean heatersOn
        -double kilowattHours
        #run(dt, garden)
        #shutDown(garden)
        +checkFrost(garden Garden)
        +bedTemperature(air double) double
        +isHeating() boolean
    }

    class PestControlSystem {
        -PestSensor sensor
        -Map~Cockroach,Double~ watching
        -double tank
        -int killed
        #run(dt, garden)
        #shutDown(garden)
        +getTankPercent() double
    }

    class ReplantingSystem {
        -Map~Plot,Double~ waitingSince
        -int replaced
        #run(dt, garden)
        #shutDown(garden)
    }

    class Sensor {
        <<interface>>
        +getName() String
        +read() double
        +getUnit() String
    }

    class MoistureSensor {
        -Plot plot
        +read() double
    }

    class TemperatureSensor {
        -Garden garden
        +read() double
    }

    class PestSensor {
        -Garden garden
        +read() double
        +detect() List~Cockroach~
    }

    GardenModule <|-- WateringSystem
    GardenModule <|-- HeatingSystem
    GardenModule <|-- PestControlSystem
    GardenModule <|-- ReplantingSystem

    Sensor <|.. MoistureSensor
    Sensor <|.. TemperatureSensor
    Sensor <|.. PestSensor

    WateringSystem "1" *-- "40" MoistureSensor
    HeatingSystem "1" *-- "1" TemperatureSensor
    PestControlSystem "1" *-- "1" PestSensor

    MoistureSensor --> Plot
    TemperatureSensor --> Garden
    PestSensor --> Garden
    PestControlSystem ..> Critters : exterminate()
    ReplantingSystem ..> Plot : chop() and replant
    WateringSystem ..> Pipe : checks isOpen()
```

---

## 4. Class diagram: wildlife

`Critters` updates and draws every insect **polymorphically** through the abstract `Bug` methods.

```mermaid
classDiagram
    direction TB

    class Critters {
        -List~Bug~ bugs
        -List~Splat~ splats
        -List~PlotController~ plots
        -double roachTimer
        +update(dt, width, height, season)
        +render(canvas Canvas, nanos long)
        +stompAt(x double, y double) boolean
        +exterminate(roach Cockroach)
        +getRoaches() List~Cockroach~
        +randomLivingPlot() PlotController
        +randomLeafyPlot() PlotController
    }

    class Bug {
        <<abstract>>
        #double x
        #double y
        #double vx
        #double vy
        +update(dt double, field Critters)* boolean
        +draw(g GraphicsContext, time double)*
        #steerToward(tx, ty, speed, agility, dt)
        +distanceTo(px double, py double) double
    }

    class Ladybug {
        +update(dt, field) boolean
        +draw(g, time)
    }

    class Bee {
        -PlotController visiting
        +update(dt, field) boolean
        +draw(g, time)
    }

    class Cockroach {
        -PlotController meal
        -boolean eating
        +update(dt, field) boolean
        +draw(g, time)
        +isEating() boolean
        +getMealName() String
    }

    class Splat {
        -double x
        -double y
        -double age
        -boolean sprayed
    }

    Bug <|-- Ladybug
    Bug <|-- Bee
    Bug <|-- Cockroach
    Critters "1" *-- "*" Bug
    Critters "1" *-- "*" Splat
    Bee ..> Plot : pollinate()
    Cockroach ..> Plot : chew()
    Critters --> "40" PlotController
```

---

## 5. Class diagram: user interface and logging

```mermaid
classDiagram
    direction LR

    class Application {
        <<JavaFX>>
    }

    class GardenApp {
        +start(stage Stage)
        +main(args String[])$
    }

    class GardenController {
        <<FXML controller>>
        -Garden garden
        -List~PlotController~ plotViews
        -List~Spigot~ valves
        -Critters critters
        -AnimationTimer timer
        -FilteredList~Entry~ filteredLog
        -update(dt double, now long)
        -refreshText(sky Weather)
        +waterByHand(plot Plot)
        +harvestByHand(plot Plot)
        +chopByHand(plot Plot)
        +plantByHand(plot Plot)
        +shutdown()
    }

    class PlotController {
        <<FXML controller>>
        -Plot plot
        -Canvas canvas
        +bind(plot Plot, garden GardenController)
        +render(nanos long, weather Weather)
        -onClicked(e MouseEvent)
    }

    class Spigot {
        -boolean open
        +setOpen(open boolean)
    }

    class Irrigation {
        <<utility>>
        +render(canvas, pipes, rows, nanos, heating)$
        +pipeY(row)$ double
    }

    class WeatherView {
        <<utility>>
        +render(canvas, weather, clock, nanos, showNight)$
    }

    class HelpWindow {
        <<utility>>
        +show(owner Window)$
    }

    class GardenLog {
        <<utility>>
        -ObservableList~Entry~ RECENT$
        -BufferedWriter out$
        -int errorCount$
        +start(clock GameClock)$
        +info(category, format, args)$
        +warn(category, format, args)$
        +alert(category, format, args)$
        +error(category, where, problem)$
        +flush()$
        +stop()$
    }

    class Entry {
        +String realTime
        +String gameTime
        +Level level
        +Category category
        +String message
        +toLine() String
    }

    class Level {
        <<enumeration>>
        INFO
        WARN
        ALERT
        ERROR
    }

    class Category {
        <<enumeration>>
        SYSTEM
        TIME
        WEATHER
        WATERING
        HEATING
        PEST
        REPLANT
        PLANT
        WILDLIFE
        USER
    }

    Application <|-- GardenApp
    GardenApp ..> GardenController : loads garden.fxml
    GardenController "1" *-- "1" Garden
    GardenController "1" *-- "40" PlotController : loads plot.fxml
    GardenController "1" *-- "5" Spigot
    GardenController "1" *-- "1" Critters
    PlotController --> Plot
    PlotController --> GardenController : forwards clicks
    GardenController ..> Irrigation
    GardenController ..> WeatherView
    GardenController ..> HelpWindow
    GardenController ..> GardenLog : shows getRecent()
    GardenLog "1" *-- "*" Entry
    Entry --> Level
    Entry --> Category
```

---

## 6. State diagram: a plot

```mermaid
stateDiagram-v2
    [*] --> Growing : plant()

    Growing --> Wilting : soil below 15%
    Wilting --> Growing : soil watered above 15%
    Growing --> Mature : all glyphs grown
    Mature --> Growing : harvest (by hand, or after 10 s) and replant

    Growing --> Dead : health reaches 0 (wind, rot, frost, pests)
    Wilting --> Dead : health reaches 0 (drought or other damage)

    Dead --> Empty : third chop() (by hand, or auto-replanting after 1 day)
    Empty --> Growing : plant() (by hand, or auto-replanting after 0.5 day)

    state Growing {
        [*] --> Healthy
        Healthy --> Hurt : damage this step
        Hurt --> Healthy : recovers 2 health/s
    }

    note right of Wilting
        Loses 5 health/s, leaves
        brown and drop
    end note
    note right of Dead
        Logged as ALERT with
        the cause of death
    end note
```

---

## 7. Sequence diagram: one simulation step

`GardenController`'s `AnimationTimer` calls `Garden.step(dt)` many times per frame (dt ≤ 0.25 s). Every module and plot runs inside `safely(...)`, so one failure can't stop the rest.

```mermaid
sequenceDiagram
    autonumber
    participant Timer as AnimationTimer
    participant GC as GardenController
    participant G as Garden
    participant Clock as GameClock
    participant WS as WeatherSystem
    participant M as GardenModule (x4)
    participant H as HeatingSystem
    participant P as Plot (x40)
    participant Log as GardenLog

    Timer->>GC: handle(now)
    loop until frame time x speed is used up
        GC->>G: step(dt)
        G->>Clock: advance(dt)
        opt new day
            G->>Log: daily report
        end
        G->>WS: update(dt, season)
        opt weather changed
            G->>Log: WEATHER began / ended
        end
        loop each module
            G->>M: update(dt, garden)
            alt enabled
                M->>M: run(dt, garden)
            end
        end
        G->>H: bedTemperature(air)
        H-->>G: air + 8°C if heating
        loop each plot
            G->>P: tick(dt, weather, season, bedTemp)
            P-->>G: true if ready to harvest
            opt ready to harvest
                G->>G: harvest(plot) and replant(plot)
            end
        end
    end
    GC->>GC: render plots, pipes, bugs, weather
    GC->>Log: flush() about once a second
```

---

## 8. Sequence diagram: a cockroach attack

```mermaid
sequenceDiagram
    autonumber
    participant C as Critters
    participant R as Cockroach
    participant P as Plot
    participant PC as PestControlSystem
    participant S as PestSensor
    participant Log as GardenLog
    actor Gardener

    C->>R: new Cockroach(edge of fence, target plot)
    C->>Log: WARN WILDLIFE crawled in, heading for Plot N
    loop each frame
        C->>R: update(dt, critters)
        R->>R: steer toward the bed
    end
    R->>Log: ALERT WILDLIFE started eating Plot N
    loop while eating
        R->>P: chew(6 x dt)
    end

    PC->>S: detect()
    S-->>PC: [cockroach]
    PC->>Log: WARN PEST cockroach detected on Plot N
    Note over PC: watches it for 2 s
    alt tank holds at least 15
        PC->>C: exterminate(cockroach)
        C->>C: remove bug, add pesticide splat
        PC->>Log: INFO PEST pesticide sprayed (tank X%)
    else tank empty
        PC->>Log: ALERT PEST tank empty, stomp by hand
        Gardener->>C: click on cockroach, stompAt(x, y)
        C->>Log: USER cockroach stomped
    end
```

---

## 9. Sequence diagram: the gardener clicks a plot

The field's click filter tries a stomp first. Only if no cockroach is hit does the click reach the plot, where the plot's state chooses the action.

```mermaid
sequenceDiagram
    autonumber
    actor Gardener
    participant Field as fieldStack (event filter)
    participant C as Critters
    participant PV as PlotController
    participant GC as GardenController
    participant G as Garden
    participant P as Plot
    participant Log as GardenLog

    Gardener->>Field: mouse click
    Field->>C: stompAt(x, y)
    alt cockroach hit
        C-->>Field: true
        Field->>G: noteStomped()
        Note over Field: event consumed
    else nothing hit
        C-->>Field: false
        Field->>PV: onClicked(e)
        PV->>P: getState()
        alt MATURE
            PV->>GC: harvestByHand(plot)
            GC->>G: harvest(plot, true)
            G->>Log: USER harvested by hand
            G->>P: plant(new species)
        else DEAD
            PV->>GC: chopByHand(plot)
            GC->>P: chop()
            GC->>Log: USER chopped (n/3)
        else EMPTY
            PV->>GC: plantByHand(plot)
            GC->>G: replant(plot)
        else GROWING or WILTING
            PV->>GC: waterByHand(plot)
            GC->>P: water()
            GC->>Log: USER watered by hand
        end
    end
```

---

## 10. Activity diagram: watering decision

This is what `WateringSystem.run` decides for each plot on every step.

```mermaid
flowchart TD
    A([For each plot]) --> B{Pipe open and tree alive?}
    B -- no --> C[Sprinkler OFF] --> Z([Next plot])
    B -- yes --> D[Read MoistureSensor ±1%]
    D --> E{Reading below 30% and sprinkler off?}
    E -- yes --> F[Sprinkler ON] --> F2[/Log: sensor reads X%, sprinkler ON/] --> W
    E -- no --> G{Reading at least 90% and sprinkler on?}
    G -- yes --> H[Sprinkler OFF] --> H2[/Log: sensor reads X%, sprinkler OFF/] --> W
    G -- no --> W{Sprinkler on?}
    W -- yes --> L[Add 2 L/s x dt to water used] --> Z
    W -- no --> Z
```

---

## 11. Activity diagram: frame loop with error recovery

```mermaid
flowchart TD
    S([AnimationTimer frame]) --> T[dt = time since last frame, max 0.1 s]
    T --> P{Paused?}
    P -- yes --> R
    P -- no --> Q[remaining = dt x speed]
    Q --> L{remaining > 0?}
    L -- yes --> St[Garden.step of min 0.25 s and remaining]
    St --> Safe{Exception inside a module or plot?}
    Safe -- yes --> E1[/Log ERROR with stack trace, count it/] --> L
    Safe -- no --> L
    L -- no --> B[Move insects at up to 4x]
    B --> R[Draw plots, pipes, insects, weather]
    R --> X{Any other exception in this frame?}
    X -- yes --> E2[/Log ERROR 'Frame update'/] --> N
    X -- no --> U[Refresh sidebar text 4x per second, flush log 1x per second]
    U --> N([Wait for next frame])
```

# KLANG-00389: The Spine: one data layer under the UI

> **Status (2026-10-29): NOT SCHEDULED.** Asked for by the maintainer (2026-10-29: "we need to do a sweep through the
> whole ui and check how data streams are flowing. The whole data storage and data flow must all reside in a layer
> independent from the ui... the SPINE ... the ui can send data there and receives from there").

## Why this exists

Today the app's state and its data flow are spread through the UI. A count on 2026-10-29:

- `src/jsMain`: 40 `StreamSource`s in 16 files, 107 `subscribingTo`; `klangui`: 8 sources in 4 files.
- Global objects that hold state or make streams: `Player` (`player.kt`: the player, its diagnostics, `nowPlaying`,
  the output volume), the nine stores under `storage/` (`LookStorage`, `EditorSettings`, `SeedStorage`, `SongStorage`,
  ...), `KlangGuiDance`, `WorkbenchSlots`, and since today `MotorReadings`, `KlangampDeck`, and `VisualsScreen`'s
  `shown`, `open`, `klangampTiles` and `rainSpeed`.
- Components that make their own streams from those (the gauges' smoothing, `MotorReadings` per visual), and parts that
  pull data themselves every frame (`Spectrumeter` reads the analyser in its own loop).
- Cross-cutting wiring passed by hand: the Motor's pause while a visual is open goes through `WorkbenchLayout` into
  each part (14964a0f2); the editor's highlight pause subscribes to `VisualsScreen.open` itself.

Every new screen (the visuals, the Motor window, KLANG-00386's player of elements) finds its data somewhere else, and
the same reading is smoothed or paused in several places. The maintainer's trace of the same day showed parts of a
hidden page still drawing (fixed for the Motor, not as a rule).

## The aim

**The Spine**: one layer, independent of the UI, that holds the app's data and its flows. The UI only:

- **sends** to it: intents and changes (play this song, set the volume, wear this look, a visual opened);
- **receives** from it: streams to show (what plays, the Motor's readings, the settings, whether a visual covers the
  page).

What a stream needs (smoothing, a rate, a pause while nothing shows it, persistence) is decided in the Spine, once, not
in each component.

## Decide before implementing

- **Plain, not clever** (the stone rule against complexity): plain Kotlin objects and Ultra streams, no new framework,
  no generated code. Consult the maintainer before anything heavier.
- **Where it lives:** a module of its own (commonMain where it can, so its flows can be tested on the JVM) or a package
  in the app first. What of `Player` belongs in it.
- **Shape:** one entry point or a few areas (playback, Motor, settings and storage, looks, visuals, Gui-dance); how the
  UI reaches it (a Kraft attribute set at the app's root, as `addons` and `keys` are, rather than globals).
- **Sending:** plain functions on the Spine, or commands as data. Who may write what.
- **Persistence:** the stores under `storage/` move behind the Spine; what is saved, when, and how a saved value is
  read back on start.
- **Lifetimes:** per-playback and per-page state is freed when its owner ends (the standing rule: nothing allocates
  without a way to clean it up).
- **Ultra:** what the Spine needs from Ultra's streams (a sampling operator, ULTRA-00267; one frame clock, KLANG-00388)
  goes to Ultra as requests.

## The work

1. **The sweep:** an inventory of every source of state and every stream in the UI modules (`src/jsMain`, `klangui`,
   `klangscript-ui`, `sprudel`'s UI): what it holds, who writes it, who reads it, how long it lives, whether it is
   saved. As a table in this ticket, with the cases where the same data is derived twice or a hidden part keeps working.
2. **The design:** the Spine's areas, its entry point, the rules for sending and receiving, written here and agreed
   with the maintainer before code.
3. **The move, one area at a time,** each with its specs and a measurement where performance is the reason: playback
   and `nowPlaying` first, then the Motor's readings (one smoothed source for all gauges, paused when nothing shows
   them), then settings and storage, then the visuals' state.

## Acceptance

- No component makes a `StreamSource` of app state or reads storage itself; components hold only their own view
  state (a hover, an open menu).
- Each reading is derived once (one smoothing of the Motor's heat for every gauge that shows it).
- What nobody shows is not computed: a hidden page's parts rest because their streams are not asked for, not because
  each part was told.
- The Spine's flows have specs that run without a browser where they can.

## History

- 2026-10-29: created from the maintainer's request, after the performance work on the visuals (the Motor's pause,
  14964a0f2; the gauges drawn once, d329990b9; KLANG-00388).

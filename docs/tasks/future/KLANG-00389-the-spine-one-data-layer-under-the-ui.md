# KLANG-00389: The Spine: one data layer under the UI

> **Status (2026-10-29): NOT SCHEDULED.** Asked for by the maintainer (2026-10-29: "we need to do a sweep through the
> whole ui and check how data streams are flowing. The whole data storage and data flow must all reside in a layer
> independent from the ui... the SPINE ... the ui can send data there and receives from there").
>
> **Progress (2026-10-29):** the package `io.peekandpoke.klang.spine` exists with its first parts: `VisualState` (the
> lamp, the song open on a page) and `UiWork` (the UI rests while the window has no attention, from Kraft's
> `WindowController`, which the Spine hands to the app; and the one animation frame stream, KLANG-00388). The sweep (work step 1) is done, below. Next: the design (step 2)
> with the maintainer, starting with `Player` (the maintainer, 2026-10-29: "I think the global Player object should also
> move to the spine").

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

## The sweep (2026-10-29)

Read at `cfdd14142`, checked again after `85f2986cf`. Paths below are under `src/jsMain/kotlin` unless they name
`klangui`. Two Kraft facts everything here depends on: `subscribingTo` redraws its component on every value; a
component's `stream.subscribe { }` (or `stream { }`) ends with the component, a raw `subscribeToStream` does not. An
Ultra `map` or `smooth` is one shared upstream subscription per instance: each call makes a new instance with its own
work.

### Who holds state today

| Holder | Holds | Saved (key) | Note |
|---|---|---|---|
| `Player` (`player.kt`) | engine status, the player, diagnostics, samples, output volume, `nowPlaying` | `klang-master-volume` | see "Player" below |
| `KlangGuiDance` | the walkthroughs (`dance`, `lit`), a mute timer | `klang-gui-dance-*` | written from 9 files, read from 9; passed as a prop to `KlangWorkbench` too |
| `WorkbenchSlots` | the workbench's column, tools, bottom panel; a ref to the workbench | no | pages fill and clear their own entries; the fading copy of a page is kept out by an `[inert]` check |
| `VisualsScreen` companion | `shown`, `open`, `klangampTiles`, `rainSpeed` | no | `open` read directly by the editor (highlights pause) and the layout (Motor pause) |
| `VisualState` (Spine) | the lamp and its light, the song open on a page | lamp seed via `SeedStorage` | the light runs only while subscribed and while the UI does not rest |
| `MotorReadings` | **factories** for the smoothed cylinders, heat, voices, signal | no | each caller builds its own chains (see "Derived twice") |
| `KlangampDeck` | Klang Amp's playback ctrl, the start time of what plays, the last song | no | the start time is fed by two components; with neither mounted it is not kept; the ctrl is never released |
| `EditorSettings`, `TutorialStorage`, `SeedStorage` | settings, finished tutorials, the plate's seed | `klang-editor-*`, `klang-tutorial-completed-*`, `klang-seed-metal` | each saves its own way (see "Persistence") |
| `KlangTheme`, `KlangLookShelf`, `LookStorage` (klangui and app) | the worn look, the shelf | `klang-look`, `klang-looks` | the shelf saves itself; the worn look is saved by hand at 6 call sites next to `KlangTheme.choose` |
| `KlangToolAutoUpdate` (klangui) | the tools' auto update | `klang-tool-auto-update` | **not in `StorageKeys`**: the Dev Storage page cannot reset it |
| `keysManager`, `fs`, `version` (`index.kt`) | keys and layers, fullscreen, build info | `klang-keymap` | `keys` is a Kraft attribute; `fs` and `version` are globals; listeners never removed |
| `LearnSearch`, `motorBackgroundRef`, `Drafts` (LooksPage), `MotorBackground.surfaceCache` | search text, a global ref to the start page's plate, look drafts, the last plate's pixels | no | the pixel cache is a full-screen buffer kept for the app's life |
| `SongStorage` (a class per `CodeSongPage`), `MidiInstrumentStorage`, `WorkbenchLayoutStorage` | a song's code and settings, MIDI picks, the layout | `song-<version>-<id>-*`, ... | `SongStorage`'s keys use the version read once when the page is built: "n/a" if it arrives later |

How components reach them: mostly as globals. Kraft attributes carry `keys`, `addons`, `modals`, `popups`,
`windowCtrl` and `router`. Props carry the playback ctrls, `paused`, `fs`, `guiDance`, the player as a `Stream`
(Oscilloscope) or as `() -> KlangPlayer?` (Spectrumeter), `light`, the rain's speed.

### Player

What it offers: `status`, `player`, `diagnostics` (stream and history), `samples`, `outputVolume` with
`setOutputVolume`/`rememberOutputVolume`, `nowPlaying` with `publishNowPlaying(handle: Any, ...)`,
`requestExclusivePlayback(self: Any, stopOther: (Any) -> Unit)`, `get()`, `ensure()` (with a retry), and
`createEngine(player, outputHandler)`, whose `player` is unused. Read from 19 files: the pages (start, samples, MIDI,
looks, kitchen sink), the playback ctrls, the Motor's parts, the volume knob, the visuals.

Already free of the UI: the engine's lifecycle, the samples, the volume and its saving, the diagnostics, `nowPlaying`,
`createEngine`. Tied to the browser: `window.__KLANG_SAMPLE_BASE__`, Kraft's `launch`, and `ensure` needing a user
gesture to start audio (the start page's key is that gesture).

What makes the move more than a move:

1. **Ownership by `Any`.** Exclusivity casts `Any` to `KlangCodePlaybackCtrl` at the call sites (the ctrl,
   `KlangampDeck`). `KlangampDeck.stop()` stops whatever plays by claiming exclusivity.
2. **`nowPlaying` misses playbacks.** Only `KlangCodePlaybackCtrl` publishes. The start page's jingle, the sample
   previews and MIDI play past it, so the visuals do not see them.
3. **Two writers of the output volume.** The start page's song fade sets the engine's volume every 100 ms for up to
   15 s; the volume knob turned meanwhile is overwritten at the next step.
4. **The playback model lives in `comp/`.** `KlangCodePlaybackCtrl` holds playback, errors and signals, is made by
   pages and components (song page, start page, code examples, Klang Amp) and handed down as a prop. Some are
   `.exclusive()`, some are not: who stops whom depends on who starts.
5. **Pull and push for the analyser.** The spectrum pulls the FFT each frame through `() -> KlangPlayer?`; the scope
   and the Motor's readings subscribe to `analyzer.waveform`.
6. **The diagnostics' history ticks forever.** `KlangPlayer` keeps it on a ticker from start to `shutdown`, which is
   never called; it runs with no Motor window open.

### Derived twice (or more)

| Derivation | Where |
|---|---|
| cylinders, heat, voices smoothed from `Player.diagnostics.stream` | `PlayerMiniStats`, and `MotorReadings` once per caller: `VisualsBoard` and `KlangampPlayer` each run three chains, both mounted in the glass visual; `VisualsBoard` smooths them in compositions with no gauges too |
| the same smoothing on the dyno | `StartPage` |
| the waveform (`player.map { analyzer }.switchMapNotNull { waveform }`) | `Oscilloscope`, `MotorReadings.signal()` (one per `ScopeGlow`) |
| a playback's signals | `KlangCodePlaybackCtrl`, `VoiceRain` (through `nowPlaying`) |
| elapsed time of what plays | two 250 ms intervals: `KlangampPlayer`, `KlangampPlaylist` |
| scheduled voices to highlighted code | three pipelines: the editor's highlight buffer (Pixi ticker), `MnEditorBase` (timeouts), the tutorials' ADSR curves |
| a look chosen and saved | `KlangTheme.choose` plus `LookStorage.save` by hand at 6 sites |
| the window's focus | four ways: Kraft's `windowCtrl.hasFocus`, the highlight buffer's own listener, `KeysManager`'s blur, the Spine's `WindowFocus` (gone: `UiWork` uses Kraft's, the same instance as the app's) |

### Per-frame and timer work (after `85f2986cf`)

| Loop | Rests when |
|---|---|
| `UiWork.frames` (Spine): the lamp's light, the rain, the spectrum, the start page | the UI rests, or (spectrum, Motor) its `paused` |
| three.js's render loop (the plate, start page and each visual) | only on unmount; a pause asked of Ultra |
| `smooth()` (the gauges) and `analyzer.waveform` (`animTicker`) | when settled or unsubscribed; each asks for its **own** animation frame (Ultra's `AnimationFrameClock` requests one per `next()`), so they are not yet in `UiWork`'s one |
| `KlangAdsrCurve` (klangui), the highlight buffer's Pixi ticker | idle or unmount; the Pixi ticker also skips without focus |
| `RouterWithTransitions` 100 ms ticker | never (the app's root) |
| `KlangampPlayer` 250 ms interval (redraws the whole player), `KlangampPlaylist` | unmount |
| the diagnostics' history ticker | never |

### Problems found

- **Leaks:** every `KlangCodePlaybackCtrl` subscribes to `Player.status` with a raw `subscribeToStream` and has no
  dispose; one is made per song page visit, per start page visit, and per code example mount (the docs' examples
  remount as they scroll into view). The same in `RealtimePlaybackCtrl` (per MIDI page visit), and a dropped raw
  subscription in `MidiPlaygroundPage`. Listeners never removed: `FullscreenController`'s resize, `KeysManager`
  (its detach is never called), `LookStorage`'s shelf subscription.
- **Hidden parts still working:** the Motor's `ScopeGlow` under a visual (it has no `paused`), `PlayerWarehouseStats`,
  the three.js loop, `VisualsBoard`'s gauges in compositions without gauges, the diagnostics' history.
- **Persistence done four ways:** on subscription (the shelf, `SongStorage`, `KlangToolAutoUpdate`), in setters
  (`EditorSettings`, `TutorialStorage`), at the call sites (`LookStorage.save`, the volume, the layout, MIDI), on first
  read (`SeedStorage`). The Dev Storage page's reset clears localStorage but not the holders in memory, and the stores
  that save on change write the old values back.
- **Data held by the UI:** Klang Amp's start time is kept by components; the start page's state machine drives the
  lamp and the engine's volume directly.

## Player in the Spine: a first plan

For the design step, to agree with the maintainer. One area at a time, each a commit with its specs:

1. **Move it as it is** into `spine/` (the object, its name kept or `Playback`), callers adjusted. No behaviour changes.
2. **Typed ownership:** a small interface for what can play and be stopped, in place of `Any` and the casts;
   `requestExclusivePlayback` and `publishNowPlaying` take it. Every playback goes through it: the jingle, the sample
   previews, MIDI. One rule for exclusivity: a new playback stops the one before.
3. **One writer of the volume:** the Spine owns the output gain and offers a fade (the start page asks for it, the knob
   sets the target); a turn of the knob during a fade wins.
4. **The Motor's readings derived once:** `MotorReadings`' factories become values in the Spine, each smoothed once and
   cut off while nothing shows them; `PlayerMiniStats`, `VisualsBoard`, `KlangampPlayer` and `ScopeGlow` read them.
   The waveform and the spectrum as Spine streams too (decide: push the FFT on `Frames`, or keep the pull).
5. **The playback model out of `comp/`:** `KlangCodePlaybackCtrl` to the Spine with a lifetime (made and released by
   its owner), the `Player.status` leak fixed; `KlangampDeck`'s start time moves with it.
6. **The diagnostics' history** kept only while someone reads it, or kept cheaply (decide with the Motor window).

Questions for the maintainer: the name (`Player` or `Playback` in the Spine); whether the Spine reaches the UI as a
Kraft attribute or as globals (today almost everything is a global); whether `ensure()` stays a call from the key's
click (it must run inside a user gesture).

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
- 2026-10-29: the first parts in `spine/` (`VisualState`, `WindowFocus`, `UiWork` d0a4372ee, `Frames` 85f2986cf); the
  sweep (an Explore agent's read of the frontend, checked against the code) and a first plan for `Player`, on the
  maintainer's request: "map out how things are wired up in the frontend and enrich the spine ticket with your
  findings. this is a good point in time to get these things nailed and under control."
- 2026-10-29: `Frames` merged into `UiWork`, and `WindowFocus` replaced by Kraft's `WindowController` (the maintainer:
  "I think we can merge Frame and UiWork into one object? Also there already is WindowController in Kraft for detect
  focus").

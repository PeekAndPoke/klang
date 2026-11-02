# KLANG-00402: MIDI playground design: a laptop keyboard always on screen, and a control layer between the devices and what they do

> **Status (2026-11-02): IN PROGRESS.** D1 to D8 decided, all as proposed (the maintainer, 2026-11-01); D9 built as proposed; steps 0 to 3 done; step 4 begun: 4a, the rig in the Spine, done; next 4b, the MIDI tool on every page.
>
> **Part of:** KLANG-00363 (Live play: MIDI and realtime)

## Why this exists

The maintainer, 2026-11-01: "Please make a design for the midi playground. Also add a fallback keyboard visualization
that can be played with the real keys on the laptop. Y x c v b ... And s d g h for the black keys. Let other keys be
extra functions like on the arturia key lab. So the user will always at least see this keyboard. Plan for an
intermediate layer so keys can be routed to 'functionality'. So basically each keyboard impl (arturia, laptop keys)
will no longer publish midi events but will have translated them into the intermediate format which is then used to
make things happen."

How the page works today (`MidiPlaygroundPage`, `MidiConnector`, `MidiDevicePanel`, KLANG-00228, KLANG-00159):

- Nothing to play without hardware. Safari has no Web MIDI, Firefox only with an add-on, a snap Chromium never; a phone
  has no keyboard attached. The page shows only "Start Engine" until the engine runs, then a list of segments.
- The page speaks MIDI. `handle()` takes `MidiConnector.Event` (NoteOn, NoteOff, ControlChange, Other with raw status
  bytes) and unpicks the KeyLab itself: the pad notes on channel 11, the Bank button (CC 118), the CC to control-id
  lookup, the 14-bit pitch bend.
- Every note-on becomes a voice, the pads' too: a pad plays the instrument at MIDI note 36 to 51.
- The transport buttons, the knobs and the faders light up on the drawing and do nothing else.
- `MidiConnector` owns the held notes and mints the liveIds, so a second input (laptop keys) would need its own copy of
  the retrigger, release and panic bookkeeping.

## The design

### 1. Three layers

```
  KeyLab, any MIDI keyboard       Laptop keys              Screen (mouse, touch)
  MidiConnector + device profile  LaptopKeys               the drawn panels
             \                        |                        /
              \_______________________|_______________________/
                                      |
                        ControlEvent  (the intermediate format)
                                      |
                        Routing       (which control does what: data)
                                      |
             Functions: the instrument (NoteVoicing), pads, sustain, panic, sound browse, ...
                                      |
                        RealtimePlaybackCtrl  (unchanged)
```

A source never publishes MIDI. It translates its own input (bytes, key codes, pointer positions) into `ControlEvent`s
and knows nothing of what they will do. The routing decides that, and it is data, so a control can be pointed at
another function without touching a source. The drawn panels light up from the same `ControlEvent`s, so what you see is
what the playground understood, not what the device sent.

### 2. The intermediate format

Plain data classes, common code (testable on the JVM):

```kotlin
/** Which input said it: "keylab-49-mk3", "midi:<port name>", "laptop", "screen". */
data class SourceId(val id: String)

sealed interface ControlEvent {
    val source: SourceId
    val tsMs: Double

    /** A key of a keybed. [velocity] is 0..1. [channel] keeps a multi-channel MIDI device's notes apart (laptop: 1). */
    data class NoteDown(source, channel: Int, note: Int, velocity: Double, tsMs) : ControlEvent
    data class NoteUp(source, channel: Int, note: Int, tsMs) : ControlEvent

    /** A momentary control went down / up: a pad, a button, a function key. */
    data class Press(source, control: ControlId, velocity: Double, tsMs) : ControlEvent
    data class Release(source, control: ControlId, tsMs) : ControlEvent

    /** A control with a position moved: knob, fader, mod wheel 0..1; pitch bend -1..1. */
    data class Move(source, control: ControlId, value: Double, tsMs) : ControlEvent

    /** An endless control turned by [steps] (+ clockwise): the KeyLab's main encoder. */
    data class Nudge(source, control: ControlId, steps: Int, tsMs) : ControlEvent

    /** Everything this source holds is up at once: device unplugged, window lost focus, a panic CC, teardown. */
    data class AllUp(source, reason: Reason, tsMs) : ControlEvent
}
```

`ControlId` names what a control IS, not what it sends: the ids `MidiDevicePanel` already uses ("pad-1", "knob-3",
"fader-2", "play", "stop", "record", "tap", "mod-wheel", "pitch-bend", "main-encoder"), plus "sustain" and "panic".
The same id on two devices means the same role, which is what lets the KeyLab's Play button and the laptop's Space
share one default route.

Notes stay separate from controls: a keybed routes as a whole (to the instrument), and a note number is already its
own name. Routing a single key elsewhere (a key switch, a split) is a later route kind on top, not a change here.

### 3. The sources

**MIDI devices.** `MidiConnector` shrinks to ports and bytes: hot-plug, parsing, and one raw message per port. The
liveIds, the held map and the retrigger move out (section 5). A **device profile** translates, and grows out of
today's `MidiBindings`:

- KeyLab Essential 49 mk3: the pad notes on channel 11 become `Press("pad-n", velocity)` / `Release`; the Bank
  inference (`padBankOf`) moves into the profile, it is device knowledge; the button CCs become Press (value > 0) and
  Release (value 0); the main encoder becomes `Nudge`; the 14-bit bend becomes `Move("pitch-bend", -1..1)`; CC 64
  becomes "sustain". Which port plays is the profile's call too (the DAW port encodes buttons as notes).
- Any other device: a generic profile. Notes are notes, CC 64 is sustain, the bend is the bend, any other CC n is
  `Move("cc-n")`. Every MIDI keyboard plays without a profile of its own.
- The controller monitor keeps showing the raw messages: it is the tool for writing a new profile.

**Laptop keys.** `LaptopKeys` listens to `keydown` / `keyup` while the MIDI tool has the focus (section 6) and maps
the physical key
(`KeyboardEvent.code`), not the printed letter, so the shape is the same on QWERTZ, QWERTY and AZERTY. The labels drawn
on the keys come from `navigator.keyboard.getLayoutMap()` where the browser has it (Chromium), else from `event.key`
as each key is first pressed, else QWERTY. The German labels:

```
  Number row   [1][2][3][4]  pads 1-4
  Top row      [Q][W][E][R]  pads 5-8   (2 x 4, as the KeyLab's pads)   [Ü][+]  sound -/+  (BracketLeft, BracketRight)
  Home row        [S][D]   [G][H][J]   [L][Ö]          black keys: C# D#   F# G# A#   C# D#
  Bottom row   [Y][X][C][V][B][N][M][,][.][-]          white keys: C D E F G A B C D E
  Arrows       [<-][->] octave -/+    [v][^] velocity -/+
  Others       [Space] play/stop   [Tab] sustain while held   [Esc] panic
```

- One octave and a third, C to E. A, F, K and Ä are the gaps of the keybed and play nothing, so a slipped finger is
  silent.
- Octave and velocity are the laptop's own state, the way the KeyLab's Oct buttons stay inside the KeyLab: the source
  shifts its notes and sends `NoteDown` with its velocity. Unlike the KeyLab, the laptop knows its octave, so its
  drawing is always aligned (no "keybed alignment" buttons).
- A key plays only while the MIDI tool has the focus, so an editor, a page's shortcut or a song's Space never meets
  it. A key's auto-repeat (`event.repeat`) is ignored; a key with Ctrl, Alt or Meta held is the browser's. Only a key
  the source acts on gets `preventDefault` (Space scrolls, Tab moves the focus, `'` and `/` open Firefox's quick find).
- The tool losing the focus, the window losing it or the tab hiding sends `AllUp`: the `keyup` of a key held then
  never arrives.
- Many laptop keyboards drop the third or fourth key of some chords (rollover, ghosting). Nothing to fix; one line of
  help under the keyboard says so.

**Screen.** Every drawn control takes pointer input (mouse, finger), on the laptop keyboard and on a device's
drawing alike (section 6). Source "screen". A phone or a tablet gets something to play, and the knobs and faders,
which a laptop has no keys for, get a way to move.

### 4. The routing

```kotlin
/** Which control does what. Looked up by (source, control) first, then by control for any source. */
data class Routing(val routes: Map<ControlRef, PlayFunction>, val notes: Map<SourceId?, NoteTarget>)
```

The functions, v1 (each one small, each one a branch of an exhaustive `when`):

| Function | Does | Default from |
|---|---|---|
| Play the instrument | a note through the play code (section 6; today's `eventsFor`) | every keybed |
| Hit pad n | a drum sound per pad: bd sd hh oh / cp rim lt cr (D2) | KeyLab pads, 1-4 Q-R |
| Sustain | held notes keep sounding after their key is up, until sustain is up | Tab, CC 64 |
| Panic | `AllUp` for every source, every voice released | Esc; CC 120 / 123 as today |
| Instrument -/+ | the previous / next instrument of the loadout (section 6) | main encoder, Ü + |
| Knob / fader value | a value the next note reads at its onset; a ringing note follows after KLANG-00160 | knobs, faders, mod wheel |
| Play / stop | nothing in v1 (the light only); later a backing loop or a metronome | Space, the KeyLab's Play and Stop |

v1 keeps the routing in code, as defaults. Editing it on the page (and a "learn" button: click a function, then
touch a control) is a later ticket; the data shape is ready for it and stores like `MidiInstrumentStorage`.

### 5. NoteVoicing

The held-note bookkeeping moves out of `MidiConnector` into one place every source shares: the held map keyed by
(source, channel, note), minting the liveId, a retrigger releasing the old voice first, `AllUp` releasing one source's
notes only, and sustain. Pure Kotlin, common code, specs for each case. The same note on the laptop and on the KeyLab
is two voices, as two channels are today.

### 6. The MIDI tool and the MIDI page

The maintainer, 2026-11-01: "the midi controller must live at any time, so the user can run a beat and play along".

```
+- any page, a song running ---------------------------------------------------------------------+
|  the page's own content                                                                        |
+= bottom panel: [the page's tabs ...] [MIDI] ===================================================+
| MIDI  [o keys play]  octave C4  vel 96  warmSaw (1/4)  sustain  C4 E4 G4               [Panic] |
|  KeyLab drawing (while one is connected; every control clickable)                              |
|  Laptop keys: pads [1][2][3][4] [Q][W][E][R] | functions | keybed with the letters on the keys |
+------------------------------------------------------------------------------------------------+

+- the MIDI page: configuration ------------------------------------------------------------------+
| devices: [KeyLab 49 mk3 o]                                                             [Panic] |
+-----------------------------------------------+------------------------------------------------+
| PLAY CODE                           [Apply]   | INSTRUMENTS                    [New instrument] |
|  let warmSaw = Ign.saw()...                   |  Loadout: [1 warmSaw x] [2 pad x] [3 supersaw x] |
|  let pad = Ign.supersaw(...)                  |  Yours (the lets of the play code):              |
|                                               |    warmSaw  pad   (drag, click)                  |
|  x => x.sound(warmSaw).reverb(wet = 0.2)      |  Built-in: supersaw sine sawtooth ... pluck      |
+-----------------------------------------------+------------------------------------------------+
| [Events] [Routing] [MIDI monitor]                                                              |
+------------------------------------------------------------------------------------------------+
```

- **The rig lives with the app**, not with a page: `MidiRig` (D6) in the Spine (section 7) holds the connector, the
  sources, the routing, `NoteVoicing`, the applied play code, the loadout and the realtime playback
  (`RealtimePlaybackCtrl`, which today the page makes and tears down). A KeyLab plays on every page, shown or not; a
  song keeps running and you play along. Moving to another page mid-chord releases nothing.
- **The MIDI tool** is a tab of the workbench's bottom panel on every page, beside the page's own tabs (a page
  without a bottom panel gets one with the MIDI tab alone). It is the play surface: the KeyLab drawing while one is
  connected, the laptop keyboard (folded to a strip while a KeyLab is connected, D5), and a line with the octave,
  the velocity, the instrument, sustain and what is sounding. The bottom panel already docks, opens, resizes and
  keeps its layout over a reload (`KlangWorkbench.Layout`): that is the dock the maintainer asked for ("fixed to the
  bottom of the screen and does not scroll").
- **The laptop keys play only while the MIDI tool has the focus** (the maintainer: "Only the laptop keys would need
  some way to focus the midi tool, so that keys presses get routed through it"). A click into the tool gives it the
  focus (D8: also a key that gives it from anywhere); while it has it, the tool wears a lit edge and says "keys
  play". With the focus anywhere else every key is the page's again.
- **Every drawn control takes clicks and touches** (the maintainer: "Clicks on the inputs should also work, not just
  for the keys but for any midi vizualization"), on the laptop keyboard and on a device's drawing alike: keys, pads
  and buttons press and release; knobs, faders and the mod wheel follow a vertical drag (pointer capture); the pitch
  bend drags and springs back to the middle on release; the endless main encoder turns a drag into `Nudge` steps.
  All of it is source "screen", so a click does what the control does on the hardware, through the same routing.
- **The MIDI page configures**: the play code with the instruments, the loadout, the devices, the routing, the event
  log and the MIDI monitor. The bottom panel is there too, so a change is heard at once.
- **One input holds everything** (D7, the maintainer's proposal, 2026-11-01: `x => x.sound(myInst).kat()`, and "the
  input with 'let warmSaw' should not exist, should all be in the one input"): the left half of the page, written
  like a song. The instruments are `let`s of Ignitors at the top (`let pad = Ign.supersaw(...)`), and the script
  ends in a function from the key's one-note pattern to what sounds (`x => x.sound(pad).reverb(wet = 0.2)`); a
  built-in is a name in quotes, `.sound("supersaw")`. It replaces today's two inputs (Ignitor and mapper).
  `startEvents` already announces an inline Ignitor before its voice; the registry finds one it knows, so only the
  frontend builds it again per note.
- **The instruments**, the right half, a view of the code, not a second editor: yours are the play code's top-level
  `let`s whose value is an Ignitor, then the built-ins. After Apply, the names come from the parsed script's
  top-level `let`s (a small AST walker, in `klangscript` where walkers live) and each value from
  `KlangScriptEngine.getVariable(name)`; the engine has no call that lists its bindings. A click plays one: it
  rewrites the first `.sound(...)` of the play code.
  "New instrument" adds a `let` at the top.
- **The loadout** (the maintainer, 2026-11-01: "We would also need a loadout again, so the user can configure the
  instruments that will be switched through by the change instrument inputs"): the instruments that
  "instrument -/+" steps through (Ü +, the KeyLab's main encoder). Filled by drag and drop from the list, as the
  Looks page equips; a slot takes a drop to replace it, the row's end a drop to add, × removes. An empty loadout
  steps through all of them.
- Saved in localStorage like `MidiInstrumentStorage` today: the play code and the loadout. An old save (an Ignitor
  and a mapper) loads once as one play code: the Ignitor as a `let`, the mapper with `.sound(<it>)` in front.
- The tool is there before the Motor: the first key or click in it is the user gesture that starts it (`keydown`
  already wakes the AudioContext, `_audio_context_wake.kt`). D4 decides whether that first note plays once the Motor
  is up. A KeyLab note is no gesture: before the Motor runs, the tool says "click to start".
- The event log shows `ControlEvent`s ("laptop  NoteDown C4 vel 0.75", "keylab  Press pad-3"); the MIDI monitor keeps
  the raw bytes; the routing tab shows the table read-only.

### 7. Where the code lives

The rule of the Spine (KLANG-00389): the app's data and its flow live in a layer apart from the UI; the UI sends
there and reads from there. The rig is such data, so it lives in the Spine; the parts it is built from and the UI that
shows it live in one MIDI package (the maintainer, 2026-11-01: "We should have a dedicated midi package for all ui and
data in the frontend"; then "So where in the 'spine' will midi live?").

**In the Spine** (`src/jsMain/kotlin/spine/`), the collection point for all the app's global state (the maintainer,
2026-11-01, KLANG-00389), beside `Player`: `MidiRig`, one object for the app's life, as `VisualState` hosts the lamp.
It owns the live instances and the state:

- the `MidiConnector` (Web MIDI is the browser, but it is input and output, not UI, as `Player` is), the device
  profiles in use, the `LaptopKeys` and screen sources, the `Routing`, `NoteVoicing`, the realtime playback;
- the applied play code, its instruments and the loadout, and their saving (`MidiInstrumentStorage` today, which
  moves with the other stores, KLANG-00389);
- what the UI reads, as streams: the devices, what is held and sounding, each control's value (to light the
  drawings), the octave, velocity and instrument, sustain, the event log, the Motor-not-started state.

The UI hands it input and never holds notes: the MIDI tool, while it has the focus, passes each key's code to the
rig's `LaptopKeys` and calls `AllUp` on blur; a drawn control's press, drag and release go to the rig's screen source.
So `LaptopKeys` is a translation from key codes to `ControlEvent`s, with no DOM in it, and tests on the JVM.

Two points of the Spine's plan for `Player` touch it:

- **Not exclusive.** "A new playback stops the one before" (KLANG-00389, Player step 2) must not hold for live play:
  a song plays, and the rig plays along. The rig registers as a playback that stops nothing and is stopped by nothing
  but a panic.
- **Seen by `nowPlaying`.** The sweep found that MIDI plays past it, so the visuals miss it; the rig publishes there.
  It also ends the leak the sweep found in `RealtimePlaybackCtrl` (one `Player.status` subscription per MIDI page
  visit): one rig, one subscription.

**In the MIDI package** (`io.peekandpoke.klang.midi`, flat):

- `src/commonMain/kotlin/midi/`: the parts with no browser in them, `ControlEvent`, `Routing`, `NoteVoicing`,
  `LaptopKeys`, the device profiles (bytes to events) and the panels' data (`MidiDevicePanel`, the KeyLab's, the
  laptop's), so their specs run on the JVM.
- `src/jsMain/kotlin/midi/`: `MidiConnector` and `WebMidi`; the UI: `MidiTool` (the bottom tab), `MidiPanel` (the
  drawing, from `comp/`), `MidiPlaygroundPage` (the configuration, from `pages/`). `RealtimePlaybackCtrl` (from
  `comp/`) is global state and goes to the Spine with the rig.
- The page leaves `pages/`, where every other page lives: a feature folder keeps its page with it. Outside the
  package only the Spine (the rig), `nav.kt` (the page) and `WorkbenchLayout` (the tab) use it.

## Decide before implementing

**Decided (the maintainer, 2026-11-01), all as proposed:** D2 drum hits on the pads; D3 the octave stays in each
source; D5 the laptop keyboard folds to a strip while a KeyLab is connected; D1, D4, D6 and D8 as written ("Yes, all
as proposed"). D7 was the maintainer's own proposal.

- **D1, the laptop map** as drawn in section 3: Y to - white, S D G H J L Ö black, the upper rows functions (no
  second octave on the Q row, as asked). Arrows for octave and velocity, Space, Tab, Esc.
- **D2, the pads**: drum hits by default (proposal), or notes as today. Drum hits need the realtime path to load
  samples, which it does not yet (`KlangRealtimeVoicePlayback` starts voices only); the kit preloads with the rig.
- **D3, octave stays in each source** (proposal: as the hardware does it) or one playground-wide transpose for all.
- **D4, the first key**: starts the Motor and plays once it is up (proposal), or only starts it.
- **D5, the laptop keyboard in the tool while a KeyLab is connected**: folded to a strip that opens (proposal), or
  always full.
- **D6, the names**: `ControlEvent`, `Routing`, `PlayFunction`, `NoteVoicing`, `LaptopKeys`, `MidiRig`, `MidiTool`.
- **D7, one play-code input** with the instruments by name and a loadout (section 6): the maintainer's proposal;
  open only in its detail (the built-in picker becomes part of the instrument list; old saves converted once).
- **D8, giving the tool the focus**: a click into it (v1), and a key from anywhere. Proposal: the MIDI tab's key in
  the strip opens the bottom panel on the MIDI tab and focuses it; a keyboard shortcut waits until one is free
  of the browser's and the pages' own.

- **D9, the keyboard layout** (the maintainer, 2026-11-01: "The user must be able to switch keyboard layouts. I f.e.
  have English keyboards usually. Can we detect this, probably not..."). What plays never depends on the layout: the
  keys are read by position (`KeyboardEvent.code`), and every key of the map sits where ANSI (US) and ISO (German,
  UK, French) boards both have one, so the German Y X C V is the English Z X C V, the same keys. Only the letters
  printed on the drawn keys differ. Detecting: Chrome and Edge tell the layout (`navigator.keyboard.getLayoutMap()`);
  Firefox and Safari do not, but each key's own letter arrives with its first press (`event.key`). Proposal: a
  layout choice in the MIDI settings, "Automatic" by default (the map where the browser has it, else learned key by
  key), and English (QWERTY), German (QWERTZ), French (AZERTY) to set it by hand, kept with the MIDI settings.

## The work

0. The move of section 7, alone and with no other change, so the history keeps each file's line.
   **Done (2026-11-01):** `MidiDevicePanel` to `src/commonMain/kotlin/midi/` (no browser in it); `MidiPanel` (from
   `comp/`) and `MidiPlaygroundPage` (from `pages/`) to `src/jsMain/kotlin/midi/`, beside `MidiConnector` and
   `WebMidi`. Only the package lines, two imports and the KDoc's em dashes changed (the commit hook
   reads a moved file as new; the code comments' and UI strings' dashes go with step 1). `RealtimePlaybackCtrl` and `MidiInstrumentStorage` stay
   until the rig and the stores move into the Spine (step 4, KLANG-00389).
1. `ControlEvent`, `NoteVoicing`, `Routing` in common code with specs; `MidiConnector` down to ports and bytes; the
   KeyLab and the generic profile; the page's `handle` speaks `ControlEvent` only. Audible change: the pads (D2).
   **Done (2026-11-01):**
   - Common code (`src/commonMain/kotlin/midi/`): `ControlEvent` with `SourceId` and `ControlId`; `NoteVoicing` (the
     held map by source, channel and note, the retrigger, sustain per source, one source's `AllUp`, the panic);
     `Routing` with `PlayFunction` and the v1 defaults. Notes are not routed in v1 (every keybed plays the
     instrument), so the design's `notes` map waits for a split or a key switch. `DeviceProfile`: bytes to events,
     the control's drawn kind saying how (a button presses, the relative encoder nudges, a knob, fader or wheel
     moves); the KeyLab's by its port name, else the generic one; the bank guess (A), turned by the Bank button,
     corrected by every pad note.
   - `MidiConnector` down to ports and bytes (`Message`, `PortGone`); the page makes a profile per port and handles
     `ControlEvent`s only; the pads hit the kit (D2) as one-shots with a 1 s gate, the kit loaded when the engine is
     up. The main encoder and Ü + browse the built-in sounds until the loadout (step 5).
   - The realtime path loads samples (`klang`, handed over by Klang-Main-Dev): `KlangRealtimeVoicePlayback.preload`
     (awaitable, the kit), and the backend's `RequestSample` answered as the cyclic path answers it, for any voice
     started without its sample (a fallback: that first hit plays silent).
   - Not yet: which port of a device plays (the KeyLab's DAW port sends buttons as notes; all ports play, as before).
   - Specs: `NoteVoicingSpec`, `RoutingSpec`, `DeviceProfileSpec` (JVM), `KlangRealtimeSamplesSpec` (klang). 16
     mutants killed (3 of them at the mandatory tier, in klang).
   - Review: round 1 (level 1) clean; its MINORs applied as one batch: the realtime path answers the backend's
     sample requests instead of asking for every event's sound (no growth per note or per Apply, and `startVoice`
     covered too); a MIDI panic releases every source, as before; the bank set only by a pad or the Bank button; the
     encoder drawn again; the lights of held controls cleared on an all-up; the routed functions in exhaustive
     `when`s; browsing a sound drops the applied Ignitor, so the sound browsed is heard; the scaffolding nothing used
     yet removed (it comes with steps 2 and 3); wrong comments corrected.
2. `LaptopKeys` and its drawing: `MidiPanel` learns key labels, the laptop keyboard is a panel value like the KeyLab's.
   **Done (2026-11-01):**
   - `LaptopKeys` (common): key positions to `ControlEvent`s, the map of D1; the octave (C1 to C7) and the velocity
     (32 to 127 in seven steps) its own; a key's up stops the note its down started; repeat and modified keys; the
     focus lost lets everything go (`AllUp`, `FocusLost`).
   - D9: `KeyLayout` (English, German, French: only the letters the drawing prints); "Automatic" takes the browser's
     map (the Keyboard Map API, Chrome and Edge), else learns each key's letter from its first press, else English.
     The choice is kept by a new Spine area, `MidiSettings` (store `MidiSettingsStorage`, `klang-midi-key-layout`).
   - The drawing: `laptopKeyboardPanel` (common), a `MidiDevicePanel` value like the KeyLab's; `MidiPanel` prints
     each key's letter (`keyLabels`), lets a panel place its name, sizes button text by the button and darkens the
     text on a lit control. On the MIDI page: the laptop keys under the KeyLab's drawing, playing while focused (a
     click into it, D8), with the octave, the velocity and the layout choice; folded while a KeyLab is connected (D5).
     In the kitchen sink: a MIDI section with the laptop keyboard (German letters with a chord, a pad and sustain
     lit; French letters an octave up).
   - Held notes and pressed controls are kept per source, so each drawing lights its own.
   - Specs: `LaptopKeysSpec`, `KeyLayoutSpec` (JVM).
   - Seen only in the kitchen sink: the MIDI page's engine does not start in headless Chrome, so the page itself
     (focus, keys, the strip) is checked by hand, not by a screenshot.
   - Review: round 1 (level 1) one MAJOR, fixed: the keybed's gaps were not the source's, so `'` opened Firefox's
     quick find on an English board; the gaps (A, F, K, `'`, the key left of Y) are ours and silent. Its MINORs
     fixed: Meta's up lets everything go (macOS sends no up under Meta); folding, and a KeyLab plugged in while
     folded, let the keys go (a removed element sends no blur); the lights per source by reason; no letter learned
     with Shift; the keyboard map's refusal caught; dark letters on a lit black key; one helper for the letters by
     octave. Round 2 (level 2) clean; its MINORs applied: Shift+Tab leaves the keys (the keyboard's way out, named
     under the drawing), the diagram's row of Ü and +, two members private. Not taken: a spec for the page's light
     logic; it moves into the rig in the Spine with step 4, where its specs come. 11 mutants killed in all.
3. Screen input on every drawn control (keys, pads, buttons, knobs, faders, wheels, the encoder).
   **Done (2026-11-01):**
   - `ScreenControls` (common): a pointer's gesture to `ControlEvent`s, one state per pointer so several fingers play:
     a key and a pad go down and up; a fader and a wheel follow the finger's height (the pitch wheel -1 to 1, back to
     the middle on the up); a knob turns as the finger drags (150 panel units from 0 to 1); the encoder steps every 12.
     `gripAt` tells what a point of a panel offers; `KeyBedLayout`, the keybed's one geometry, which `MidiPanel` now
     paints from too.
   - `MidiPanel` takes pointers when given a source and an `onInput` (capture for the drag, no `touch-action`, the
     click's focus kept for the laptop keys). On the MIDI page each drawing is a source of its own ("screen:device",
     "screen:laptop"), lit on its own drawing. In the kitchen sink a live cell: both drawings played by pointer,
     lit, no sound.
   - Specs: `ScreenControlsSpec` (JVM). 13 mutants killed.
   - Review: round 1 (level 1) two MAJOR, fixed: a key held on the laptop drawing while it folded rang on (a drawing
     taken away sends no up; `MidiPanel` now lets its pointers go on unmount); the encoder's steps rounded down, so a
     click's wobble of a pixel changed the instrument (now a whole step each way). MINORs fixed: a down on a pointer
     still holding lets the old hold go, and a mouse move with no button down is the up that never came; two fingers
     on one key or button hold it as one; the kitchen sink's drawings each their own state. Recorded as a limit, not
     changed: the laptop drawing's buttons act for the drawing's own source, so its sustain holds the notes clicked
     there, not the laptop keys', its octave and velocity are labels, and a click plays at velocity 100; the MIDI
     tool (step 4) brings the laptop's state and its drawing together. Round 2 (level 2) clean; its MINORs applied: a
     fader and a wheel map the range their cap is drawn in (a click on the cap moves it nowhere); the pitch wheel
     springs back only when its last finger lets go; two fingers on one button tested.
4. `MidiRig` in the Spine, the MIDI tool in the bottom panel of every page, the laptop keys behind its focus, the
   Motor started by the first key. After `Player` has moved into the Spine (KLANG-00389, its plan's steps 1 and 2: the
   move, then typed ownership, which the rig's "not exclusive" needs).
   **4a done (2026-11-02):** `MidiRig` in the Spine (`spine/MidiRig.kt`), the app's one `Playbacks.live`: the ports,
   the profiles, the routing, the voicing, the laptop's keys, the realtime playback (`RealtimePlaybackCtrl`, moved to
   `spine/`), the translation and the applied instrument, the lights, the log and the monitor, published as one
   `State`. Brought up by the page (`start()`, once for the app's life) and never torn down: leaving the page releases
   nothing but the laptop's keys, whose drawing goes with it; one `Player.status` subscription in all, which ends the
   leak of one per visit. The page keeps the editors, the saved instruments, the keybed's alignment and the laptop
   drawing's focus and letters. Live play stops nothing and nothing stops it: it holds no stage. `MidiRigSpec` (the
   lights by side, the tints, a source letting go, a panic, the laptop keys); 8 mutants killed. Review round 1 (level
   1) clean; its MINORs applied: the tint rules bound by the spec, the unused `ignitorApplied` and teardowns removed.
   Next: 4b, the MIDI tool in every page's bottom panel; 4c, the first key starts the Motor.
5. The MIDI page as configuration (section 6): the one play-code input, the instruments and the loadout, the
   devices, the routing, the event log and the monitor.
6. Later, own tickets: editing the routing and learning a control; key switches and splits; a backing loop for
   play / stop.

Specs per step: a profile turns bytes into the right events (pads, bank, buttons, encoder, bend, sustain); the laptop
map turns codes into events (repeat, modifiers, an editor focused, blur); `NoteVoicing` (retrigger, sustain, one
source's `AllUp`, the same note from two sources); the routing lookup order. New tests are mutation-checked.

## Acceptance

- On any page, a song running, the KeyLab and the laptop keys (the MIDI tool focused) play along; moving to another
  page mid-chord leaves the notes as they were.
- Without MIDI hardware (Firefox, Safari, a phone) every page's bottom panel has a keyboard that plays from the laptop
  keys and by touch.
- A click or a touch on any drawn control does what that control does on the hardware.
- The KeyLab plays as today; its pads do what the routing says; its drawing lights from the translated events.
- With the MIDI tool not focused no key plays a note; switching the tab while holding a key leaves no note hanging.
- No MIDI type reaches a page or the tool: they handle `ControlEvent`s only.

## History

- 2026-11-01: created. Designed from the maintainer's ask; the code read: `MidiPlaygroundPage`, `MidiConnector`,
  `MidiDevicePanel`, `MidiPanel`, `RealtimePlaybackCtrl`, KLANG-00159, KLANG-00160, KLANG-00228. The same day, the
  maintainer's ideas: one package for the frontend's MIDI code (section 7, step 0); the one play-code input, the
  instruments with a loadout, the play surface docked; then the rig app-wide, the MIDI tool in the bottom panel of
  every page, the keys behind its focus, every drawn control clickable, the MIDI page for configuring (section 6);
  the rig in the Spine (section 7).
- 2026-11-01: D1 to D8 decided, all as proposed (D2 drum hits, D3 the octave per source, D5 folded to a strip).
- 2026-11-01: IN PROGRESS.
- 2026-11-02: step 4a, the MIDI rig in the Spine (`Playbacks.live`); the page draws from it.

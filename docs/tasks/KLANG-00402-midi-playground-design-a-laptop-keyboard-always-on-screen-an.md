# KLANG-00402: MIDI playground design: a laptop keyboard always on screen, and a control layer between the devices and what they do

> **Status (2026-11-01): DESIGNED.** Waiting for the maintainer's decisions D1 to D6; nothing built.
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

**Laptop keys.** `LaptopKeys` listens to `keydown` / `keyup` on the window and maps the physical key
(`KeyboardEvent.code`), not the printed letter, so the shape is the same on QWERTZ, QWERTY and AZERTY. The labels drawn
on the keys come from `navigator.keyboard.getLayoutMap()` where the browser has it (Chromium), else from `event.key`
as each key is first pressed, else QWERTY. The German labels:

```
  Number row   [1][2][3][4]  pads 1-4                  [Ü][+]  sound -/+     (BracketLeft, BracketRight)
  Top row      [Q][W][E][R]  pads 5-8   (2 x 4, as the KeyLab's pads)
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
- A key's auto-repeat (`event.repeat`) is ignored; a key with Ctrl, Alt or Meta held is the browser's; a key typed into
  an input or an editor (the page has two CodeMirror editors) plays nothing. Only a key the source acts on gets
  `preventDefault` (Space scrolls, Tab moves the focus, `'` and `/` open Firefox's quick find).
- The window losing focus or the tab hiding sends `AllUp`: the `keyup` of a key held then never arrives.
- Many laptop keyboards drop the third or fourth key of some chords (rollover, ghosting). Nothing to fix; one line of
  help under the keyboard says so.

**Screen.** The drawn panels take pointer input (mouse, finger): a key, a pad, a button, a fader dragged. Source
"screen". A phone or a tablet gets something to play, and the knobs and faders, which a laptop has no keys for, get a
way to move.

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

### 6. The page

```
+- Midi Playground ------------------------------------------------------------------------------+
| [o Motor running]   inputs: [KeyLab 49 mk3 o] [Laptop keys o] [Screen o]     [Panic]           |
+-----------------------------------------------+------------------------------------------------+
| PLAY CODE                           [Apply]   | INSTRUMENTS                    [New instrument] |
|  let warmSaw = Ign.saw()...                   |  Loadout: [1 warmSaw x] [2 pad x] [3 supersaw x] |
|  let pad = Ign.supersaw(...)                  |  Yours (the lets of the play code):              |
|                                               |    warmSaw  pad   (drag, click)                  |
|  x => x.sound(warmSaw).reverb(wet = 0.2)      |  Built-in: supersaw sine sawtooth ... pluck      |
+-----------------------------------------------+------------------------------------------------+
| NOW PLAYING   C4 96  E4 80  G4 88   pad 3   sustain held                                       |
| UNDER THE HOOD (folded)   [Events] [Routing] [MIDI monitor]                                    |
+================================================================================================+
| PLAY SURFACE, docked to the bottom of the window, never scrolls away                           |
|  KeyLab drawing (only while one is connected; lit from ControlEvents)                          |
|  Laptop keys: pads [1][2][3][4] [Q][W][E][R] | functions | keybed with the letters on the keys |
+------------------------------------------------------------------------------------------------+
```

- **The play surface is docked** to the bottom of the window (the maintainer, 2026-11-01: "the input device it fixed
  to the bottom of the screen and does not scroll"). It is kept low: pads, functions and keybed side by side; the
  page above scrolls and ends with room for the dock, so nothing hides under it.
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
- The page renders at once. The keyboard is there before the Motor: the first key or click is the user gesture that
  starts it (`keydown` already wakes the AudioContext, `_audio_context_wake.kt`). D4 decides whether that first note
  plays once the Motor is up.
- The event log shows `ControlEvent`s ("laptop  NoteDown C4 vel 0.75", "keylab  Press pad-3"); the MIDI monitor keeps
  the raw bytes; the routing tab shows the table read-only.

### 7. Where the code lives

One package for everything MIDI and live play in the frontend, its UI and its data (the maintainer, 2026-11-01: "We
should have a dedicated midi package for all ui and data in the frontend"): `io.peekandpoke.klang.midi`, flat.

- `src/jsMain/kotlin/midi/`: what is there today (`MidiConnector`, `MidiDevicePanel`, `WebMidi`), plus the page
  (`MidiPlaygroundPage`, from `pages/`), the drawing (`MidiPanel`, from `comp/`), the playback controller
  (`RealtimePlaybackCtrl`, from `comp/`; only the playground uses it), the instrument storage
  (`MidiInstrumentStorage`, from `storage/`), and the new `LaptopKeys` and the device profiles.
- `src/commonMain/kotlin/midi/`, the same package: the parts with no browser in them (`ControlEvent`, `Routing`,
  `NoteVoicing`), so their specs run on the JVM.
- The page leaves `pages/`, where every other page lives: a feature folder keeps its page with it. `nav.kt` is the
  only file outside that imports any of it.

## Decide before implementing

- **D1, the laptop map** as drawn in section 3: Y to - white, S D G H J L Ö black, the upper rows functions (no
  second octave on the Q row, as asked). Arrows for octave and velocity, Space, Tab, Esc.
- **D2, the pads**: drum hits by default (proposal), or notes as today. Drum hits need the realtime path to load
  samples, which it does not yet (`KlangRealtimeVoicePlayback` starts voices only); the kit preloads on mount.
- **D3, octave stays in each source** (proposal: as the hardware does it) or one playground-wide transpose for all.
- **D4, the first key**: starts the Motor and plays once it is up (proposal), or only starts it.
- **D5, the laptop keyboard while a KeyLab is connected**: folded to a strip that opens (proposal), or always full.
- **D6, the names**: `ControlEvent`, `Routing`, `PlayFunction`, `NoteVoicing`, `LaptopKeys`.
- **D7, one play-code input** with the instruments by name and a loadout (section 6): the maintainer's proposal;
  open only in its detail (the built-in picker becomes part of the instrument list; old saves converted once).

## The work

0. The move of section 7, alone and with no other change, so the history keeps each file's line.
1. `ControlEvent`, `NoteVoicing`, `Routing` in common code with specs; `MidiConnector` down to ports and bytes; the
   KeyLab and the generic profile; the page's `handle` speaks `ControlEvent` only. Audible change: the pads (D2).
2. `LaptopKeys` and its drawing: `MidiPanel` learns key labels, the laptop keyboard is a panel value like the KeyLab's.
3. Screen input on the drawn panels.
4. The page layout of section 6: the docked play surface, the one play-code input, the instruments and the
   loadout, the Motor started by the first key.
5. Later, own tickets: editing the routing and learning a control; key switches and splits; a backing loop for
   play / stop.

Specs per step: a profile turns bytes into the right events (pads, bank, buttons, encoder, bend, sustain); the laptop
map turns codes into events (repeat, modifiers, an editor focused, blur); `NoteVoicing` (retrigger, sustain, one
source's `AllUp`, the same note from two sources); the routing lookup order. New tests are mutation-checked.

## Acceptance

- Without MIDI hardware (Firefox, Safari, a phone) the page shows a keyboard that plays from the laptop keys and by
  touch.
- The KeyLab plays as today; its pads do what the routing says; its drawing lights from the translated events.
- Typing in an editor never plays a note; switching the tab while holding a key leaves no note hanging.
- No MIDI type reaches the page: it handles `ControlEvent`s only.

## History

- 2026-11-01: created. Designed from the maintainer's ask; the code read: `MidiPlaygroundPage`, `MidiConnector`,
  `MidiDevicePanel`, `MidiPanel`, `RealtimePlaybackCtrl`, KLANG-00159, KLANG-00160, KLANG-00228. The same day: the
  one package for the frontend's MIDI code (section 7, step 0), the maintainer's word. Then the one play-code input, the
  instruments with a loadout, and the play surface docked to the bottom (section 6), the maintainer's ideas.

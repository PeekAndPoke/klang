# Bugfix: solo rests play a sine, and the solo amount never mutes

Status: **implemented 2026-10-07, awaiting review and the coordinator's corpus render.** Two bugs the maintainer
reported on 2026-10-07. Two decisions taken the same day (below, "Decided"); the open ones run on today's behaviour,
"decided by default, maintainer to confirm" (below, "Open decisions"). What was built: "Done" at the end.

All numbers below come from a probe that rendered KlangScript through the real engine the way the corpus harness
does (`KlangAudioRenderer`, 48 kHz, 128-frame blocks, `cps = 0.5`, raw double mix). The probe is deleted.

## Bug 1: solo rests play a sine

### Reproduction

`let myInst = Ign.saw()`, one note and one rest per cycle, RMS of the mix inside the note half and inside the rest
half (cycles 1 to 3), and the rest's pitch from zero crossings:

| Chain                                                                     | Note        | Rest                   |
|---------------------------------------------------------------------------|-------------|------------------------|
| `note("a ~").sound(myInst)`                                               | -7.8 dB     | silent                 |
| `note("a ~").sound(myInst).solo()`                                        | -7.8 dB     | silent (-174 dB)       |
| `... .solo().gain(0.5)`                                                   | -13.8 dB    | silent (0 Hz sine)     |
| `... .solo().transpose(12)` / `.scale("C4:major")` / `.note("c4")`         | -7.8 dB     | -126 dB, 220 or 262 Hz |
| `note("c4 ~").sound(myInst).solo().gain(0.5).transpose(0)`                | -13.8 dB    | **-12.0 dB, 220 Hz**   |
| `note("c4 ~").sound(myInst).solo().transpose(3).gain(0.5)`                | -13.8 dB    | **-12.1 dB, 131 Hz**   |
| `n("0 ~").apply(x => x.sound(myInst).solo()).apply(x => x.orbit(1).scale("e3:minor").gain(0.18))` | -22.7 dB | **-20.9 dB, 220 Hz** |

The last row is how Der Schmetterling is written (`*_shape` carries the `// .solo()`, `*_arrange` adds
`.scale(...).gain(...)`, and the whole stack gets `.transpose(transposition)`). There the rest is LOUDER than the
note: a plain sine at A3.

### Cause

`SoloPattern` fills every gap with a real, sounding event (`sprudel/src/commonMain/kotlin/pattern/SoloPattern.kt:65-79`):
`note = "a"`, `freqHz = 0.0`, `sound = "sine"`, `gain = 0.000001`, plus the solo amount and the pattern id. It is
silent only as long as nothing after the `.solo()` touches it, and two ordinary chain ops each undo one half of the
disguise:

1. **The pitch.** A 0 Hz sine is flat DC, so the filler is silent at any gain. But every pitch op after the solo
   recomputes `freqHz` from the filler's `note = "a"`: `transpose` (`lang_tonal_scale.kt:138-142`, even
   `transpose(0)`), `scale` (`lang_tonal_scale.kt:293-296`), `note(...)` (`lang_tonal_note.kt`). The filler becomes a
   220 Hz sine (A3, the default octave), or wherever the op moves "a".
2. **The level.** `gain(x)` REPLACES the gain (`lang_dynamics_level.kt:21`, "a later `gain` replaces an earlier
   one"), so the 1e-6 becomes the part's mix level. `velocity` multiplies and is harmless.

Either alone stays inaudible (a 0 Hz sine at gain 0.5; a 220 Hz sine at -126 dB). Both together, in either order,
play a sine at the part's level in every rest. Not involved: the voice factory (it plays what it is given,
`freqHz ?: 0.0`), the gain floor, and silence culling (a filler never exceeds the floor, so it is "not heard yet"
and never culled; it runs its whole gate).

Two smaller faults in the same file: the fillers reach the UI as phantom voices (`KlangPatternScheduler.kt:399-410`
only hides `control` events), and `SoloPattern` keeps a mutable `patternId` field and a global counter, which breaks
the stone rule that a DSL value is immutable at construction.

## Bug 2: `solo(0.7)` seems ignored, `solo(1.0)` never mutes

### The amount, end to end

| Step                                  | Where                                     | What happens                                          |
|---------------------------------------|-------------------------------------------|-------------------------------------------------------|
| door                                  | `lang_structural_mute.kt:241-242`         | `solo()` passes an empty list, `solo(0.7)` passes 0.7 |
| default                               | `lang_structural_mute.kt:205`             | `args.ifEmpty { 0.97 }`                               |
| pattern                               | `SoloPattern.kt:75, 97`                   | samples the control, `coerceIn(0.0, 1.0)`             |
| merge                                 | `SprudelVoiceData.kt:761`                 | `solo = other.solo ?: solo`                           |
| wire                                  | `SprudelVoiceData.kt:939-940`             | `solo`, `sourceId = patternId`                        |
| voice                                 | `VoiceScheduler.kt:676-678`               | `ActiveVoice.soloAmount`, fixed at activation         |
| duck                                  | `VoiceScheduler.kt:477-492`               | `targetGain = 1.0 - maxSoloAmount * 0.95`, ramp 1.5 s |

Measured on the wire: `solo()` gives 0.97, `solo(0.7)` gives 0.7, `solo(1.0)` gives 1.0. **0.7 is not lost
anywhere.** Measured background level through the engine (a saw bed against a soloed pattern at gain 1e-6, cycles
4 to 6, after the ramp):

| Call          | Background (ratio) | dB       | `1 - a * 0.95` |
|---------------|--------------------|----------|----------------|
| `solo()`      | 0.0785             | -22.1 dB | 0.0785         |
| `solo(0.95)`  | 0.0975             | -20.2 dB | 0.0975         |
| `solo(0.7)`   | 0.3350             | -9.5 dB  | 0.335          |
| `solo(0.5)`   | 0.5250             | -5.6 dB  | 0.525          |
| `solo(1.0)`   | 0.0500             | -26.0 dB | 0.05           |
| `solo(0)`     | 1.0000             | 0 dB     | 1.0            |
| `solo(0.7)` and another `solo()` | 0.0785 | -22.1 dB | max wins       |

Identical with and without rests in the soloed pattern. So:

- **`solo(0.7)` works as coded** (-9.5 dB). It is overridden by any other solo in the playback, because the strongest
  solo wins (`maxSoloAmount`, a deliberate rule with a spec row, "the strongest solo wins"). A `.solo()` left in
  another part, or in a shape that two parts share, makes every weaker amount look ignored. That is the only path
  in the engine that ignores 0.7; whether it is what the maintainer heard we cannot tell from here.
- **`solo(1.0)` cannot mute by construction**: the `* 0.95` caps the duck at 0.05 (-26 dB).

### Where 0.95 and 0.97 come from

- Before 2026-02-11 solo was a frontend boolean: any soloed event in a query batch set the other events' gain to
  0.05 (`StrudelPlayback.applySoloLogicSmoothed`).
- `aa12d420` (2026-02-11, "moving solo to audio-backend, smooth solo handling",
  `docs/tasks-archive/2026-02/20260211-solo-logic-in-audio-backend.md`) moved it into `VoiceScheduler` and wrote
  `1.0 - maxSoloAmount * 0.95` so the old boolean (sent as 0.95, "Frontend defaults to 0.95") kept roughly its 0.05
  floor. No decision about a floor was recorded; the 0.95 is the old 0.05 carried over.
- `docs/tasks-archive/2026-02/20260225-strudel-numeric-solo.md` made the amount numeric ("0 .. no solo, 1 .. full
  solo, `solo()` defaults to 0.97"). "Full solo" was the intent; the factor was never revisited.

### A third fault on the way

The 2 s `SoloSourceTracker` hold (`VoiceScheduler.kt:99-145`) keeps a source in the solo set after its last voice
ends, but the amount comes from ACTIVE voices only (`:479-484`). In a gap with no voice the set is non-empty and
`maxSoloAmount` is 0, so `targetGain` is 1.0 and the background ramps back up. The fillers exist to paper over
exactly this. The tracker also allocates per block (audit item B4.2 in
`docs/audio-audit/2026-10-07-engine-tidy-audit.md`).

## Decided (maintainer, 2026-10-07)

- **The default is `solo(0.95)`.** `solo()` and `solo(null)` mean 0.95 (today 0.97).
- **`solo(1.0)` silences everything else for real**: exact zero for the non-soloed patterns, not -26 dB.
- Hence the mapping **background gain = `1 - amount`**, and the `* 0.95` goes. `solo(0.95)` keeps the others at
  5 %, exactly today's `solo(1.0)`; `solo(0.7)` gives 0.3 (-10.5 dB); `solo(1.0)` gives 0.

## The proposed design

### Sprudel: the keep-alive becomes a control event

`master(...)` and `katalyst(...)` already carry engine state on events marked `control = true`;
`VoiceScheduler.promoteScheduled` applies their state and then drops them before voice creation
(`VoiceScheduler.kt:600-620`). The solo keep-alive is the same kind of thing: engine state with no sound.

`SoloPattern(source, soloControl, soloId)`:

- **One id per `solo` call**, made once at construction from the call's source location
  (`generateSourceId(callInfo location)`, a counter only when there is no location). It is stamped as `patternId` on
  every source event and every control event. This replaces "the first source event's patternId" and the mutable
  field: the pattern becomes pure, the id stays stable across live re-evaluation, and
  `stack(p.solo(), p.transpose(7))` no longer solos the unsoloed copy (today both share `p`'s atom id).
- **Source events** keep the `solo` amount sampled at their onset, as today (the scheduler reads it too, see below).
- **Control events**: for each event of `soloControl` that overlaps the query window, one event whose part and whole
  are the overlap (`whole == part`, so it is an onset in every query chunk, the same trick the filler uses today; the
  claim "a solo then engages within one chunk after a live edit" did not hold, see "Review round 1": the events are
  now also cut on a 1/8-cycle grid). Its data is `control = true`, `solo = amount`,
  `patternId = soloId`, and NOTHING else: no note, no freq, no sound, no gain. The control events cover the whole
  window, with or without rests, so there is no gap arithmetic any more.
- `control` survives every later op: `merge` never takes it from the other side (`SprudelVoiceData.kt:765-768`), and
  the field setters (`gain`, `scale`, `transpose`, `note`, `sound`) copy it along. Whatever a later op writes into a
  control event, the scheduler never builds a voice from it, so bug 1 cannot come back through any op.
- `solo(0)`, a non-finite amount and `solo("<1 0>")` cycles at 0 emit control events with amount 0, which engage
  nothing.

### Engine: the scheduler records "source X is soloed at a until t"

The scheduler owns WHO is ducked; the voice decides WHAT that means (it renders with the multiplier it is given;
the multiplier is an input, not lifecycle, per the lifecycle doc's inventory).

- **Record at promotion, from any event.** In `promoteScheduled`, next to the master and katalyst lines and BEFORE
  the `control` drop and the late-voice guard (late state still takes effect, the master's rule):
  if `solo > 0` (NaN-guard: a NaN fails the compare) and `sourceId != null`, record
  `(sourceId, amount, untilSec = epoch + gateEndTime)`. One path for control events and sounding notes, so a
  frontend without control events (a future MIDI solo) still works.
- **The tracker** holds per source: amount, `untilSec`, and a protection end `untilSec + hold`. A re-recorded source
  overwrites its entry (the last writer per source wins, so `solo("<1 0.5>")` follows the pattern).
  - `maxSoloAmount` = the max amount over entries with `untilSec + grace > now`. The grace is a few blocks so two
    back-to-back control events never leave a one-block hole at a chunk seam that would restart the ramp.
  - A voice is protected (multiplier 1.0) when its `sourceId` has an entry within its protection end. The hold must
    be at least the ramp time, so a soloed source's tail never dips while the background comes back (today 2.0 s
    against 1.5 s; keep that invariant and test it).
  - `targetGain = if (any live entry) 1.0 - maxSoloAmount else 1.0` (the decided mapping).
  - Fixed-capacity arrays (id, amount, until, protectUntil), linear scan, no per-block allocation; this closes audit
    B4.2. Per playback: the scheduler belongs to the playback's engine, so the state dies with it.
- **`ActiveVoice.soloAmount` goes**: the amount lives in the tracker, not on the voice.

### What a gain of exactly 0 means

Read from the code; each point gets a test below.

- **The ramp.** `ValueRamp` ends on the exact target (`current = targetValue` at `progress >= 1`), so after 1.5 s of
  `Ease.InOut.cubic` the multiplier is exactly 0.0 and every background voice adds `signal * 0.0` to its orbit. No
  denormal risk: the values on the way down stay far above the subnormal range. Unchanged class: a voice that has
  already blown up (Inf or NaN) still puts NaN into the mix (`Inf * 0`), as it does at 0.05 today.
- **Silence culling.** The cull reads the voice's own output BEFORE the multiplier (`SendRenderer.kt:127`,
  `voiceOutputPeak = peak * abs(voice.gain)`; `VoiceCullingDefaults.kt` says so). A muted voice therefore keeps
  measuring its real level: it is not culled for being muted, it stays `Sounding`, keeps its phase and envelope, and
  is heard again mid-note when the solo ends and the ramp comes back. Its own release culls as usual. The cost: muted
  voices render in full (CPU as without solo). Skipping their rendering would break the "comes back mid-note"
  behaviour and is not proposed.
- **Orbit tails already ringing.** The multiplier sits between a voice and its orbit mix. The orbit's chain (reverb,
  delay) keeps processing and its tail decays naturally; only new input stops. An orbit never deactivates while a
  voice plays on it (`Cylinder.kt`, decided 2026-09-19), so a muted orbit with playing voices stays active and
  processes zeros. So `solo(1.0)` is exact silence for the others after the ramp PLUS their orbit tails (a
  `reverb(size = 6)` rings for seconds). An orbit shared by a soloed and a muted pattern runs on the soloed input
  only.
- **Side effects inside the mix.** A `duck` stage listening to a muted orbit hears zeros, so the soloed part stops
  pumping while soloed. A muted voice is still `Sounding`, so it can still own an orbit's bus settings (step 5's
  ownership rule) when it shares the orbit with the soloed part. Both exist today at 0.05; at 0 they are easier to
  notice.

### Is `control` the right name for "data only"?

It fits: the wire KDoc says "carries engine/bus configuration and is never synthesized", and solo state is engine
state of the same kind as a master or an orbit chain. One wrinkle: sprudel also says "control pattern" for a
pattern that feeds a parameter (`solo("<1 0>")`, `SoloPattern.soloControl`), so "control event" and "control
pattern" sit side by side. The KDoc on `VoiceData.control` and `SprudelVoiceData.control` must widen from
"master/katalyst" to "engine state (master, katalyst, solo)".

### Other fake voices

A grep over sprudel, klang, the app, audio_fe, audio_bridge and klangscript for near-zero gains, placeholder
sounds and "filler"/"keep-alive" finds only `SoloPattern`. `master(...)` and `katalyst(...)` already use
`control = true`. Nothing else fakes a silent voice to carry data.

### Songs, tutorials and docs that change

- **No song changes sound.** Every `solo(` in `src/commonMain/kotlin/builtinsongs/`, `FrozenSongs.kt`,
  `FrozenPieces.kt` and `SongBenchmarkCases.kt` is commented out (`// .solo()`); the corpus stays bit-identical.
  Verify with the corpus ladder anyway.
- No tutorial uses `solo` (`tut_SpaceAndDirt.kt:41` only says "mute/solo still scale gain").
- Text to update: the four KDocs in `lang_structural_mute.kt` (0.97 to 0.95; `solo(1)` is "full solo, the others
  are silent"), the `SoloPattern` KDoc, `VoiceData.solo` and `VoiceData.control`, `SprudelVoiceData.solo` and
  `.control`, `audio/ref/data-model.md:40`, and a History line in `audio/MEMORY.md` and `sprudel/MEMORY.md`.
- Tests that move by decision: `LangSoloSpec` (0.97 rows to 0.95) and `VoiceSchedulerSoloCutSpec` "the floor is an
  attenuation (0.05), not a mute" (becomes "solo(1.0) is exact silence").

## Open decisions for the maintainer

Each one ships with today's behaviour, **decided by default, maintainer to confirm** (coordinator, 2026-10-07):

- (1) ramp times: 1.5 s in and out, 2 s hold, kept (`VoiceScheduler.SOLO_RAMP_SEC`, `SOLO_HOLD_SEC`);
- (2) orbit tails under `solo(1.0)` decay naturally; no extra mute of an orbit without a soloed voice;
- (3) several solos: the strongest wins;
- (4) one id per `solo` call: the call's full source location (see "Done" for why);
- (5) the word `control` stays.

1. **The ramp times.** Today: duck 1.5 s in, 1.5 s back out, cubic, 2 s protection hold. A full mute that takes
   1.5 s to arrive may feel slow when soloing while editing. Keep, or shorten the way in (for example 0.3 s) and keep
   the way back gentle?
2. **Orbit tails under `solo(1.0)`.** Accept that ringing reverb and delay tails of muted parts decay naturally
   (proposed), or also silence the OUTPUT of an orbit that carries no soloed voice (instant true silence, needs a
   per-orbit multiplier in `cylinders/`)?
3. **Several solos.** Keep "the strongest wins" (today, and the reason `solo(0.7)` beside a `solo()` does nothing)?
4. **The solo id.** One id per `solo` call (proposed), instead of the source pattern's atom id. Related:
   `generateSourceId` hashes only line and column (`lang_helpers.kt:43-45`), so two patterns at the same position
   in different modules share an id. Add the module name?
5. **The word.** Keep `control` for "data-only event" (proposed), or rename now while it has three users?

## The tests the fix needs

Engine and wire changes: mutation-check at the mandatory tier (`/review-loop`).

**Sprudel (`LangSoloSpec`, a new `SoloPatternSpec`)**

- `solo()` and `solo(null)` give 0.95 on both doors (KlangScript and Kotlin); `solo(0.7)` gives 0.7.
- A rest in `note("a ~").solo()` yields control events only: `control == true`, `solo == 0.95`, the solo id, and
  `note`, `freqHz`, `sound`, `gain` all null.
- `control` survives the bug-1 chains: `.solo().gain(0.5).transpose(0)`, `.solo().transpose(3).gain(0.5)`,
  `.solo().scale(...)`, `.solo().note(...)`, `.solo().sound(...)`, and the shape-then-arrange form.
- The control events cover each query window without a gap, also when the window is queried in chunks.
- `solo("<1 0>")` gives control events at 1, then 0.
- Purity: querying the same `SoloPattern` twice gives equal results; two compiles of the same code give the same id;
  two `solo` calls give different ids; in `stack(p.solo(), p.transpose(7))` the second keeps `p`'s own id.
- `KlangPatternScheduler` does not report a solo control event as a voice in `VoicesScheduled`.

**Engine (`VoiceSchedulerSoloCutSpec`)**

- Background after the ramp is `1 - amount`: `solo(1.0)` exactly 0.0, `solo(0.95)` 0.05, `solo(0.7)` 0.3.
- A control-only solo event, with no voice anywhere for its source, ducks the background, and builds no voice.
- The duck holds through a gap between two soloed notes that is covered only by control events: the ramp never
  rises.
- After the last control event, the background returns to 1.0, and the soloed source's voices keep multiplier 1.0
  for the whole ramp back (hold at least ramp).
- A LATE solo control event (start already rendered past) still records its state, like a late master.
- Amount 0, a NaN amount, or a null `sourceId` engages nothing.
- The strongest solo wins (keep the existing row).
- A voice muted to multiplier 0 is not culled while it sounds and is heard again mid-note when the solo ends (in or
  beside `VoiceCullingSpec`, after step 5).
- No per-block allocation in the tracker, if the suite has an allocation guard for the render loop.

**Render (`:klang` jvmTest, through `KlangAudioRenderer`)**

- The bug-1 song-shaped chain: the rest window is silent (below 1e-9 RMS).
- The background ratio for `solo()`, `solo(0.7)` and `solo(1.0)`: 0.05, 0.3, 0.
- The corpus ladder: bit-identical (no song uses `solo`).

## Done (2026-10-07)

Implemented as designed above, with one change to the tracker's rule found by the render test (below, "One
deviation"). No song changes sound: every `solo(` in the songs is commented out, so the corpus is expected
bit-identical (the coordinator renders it).

### Sprudel

- `SoloPattern(source, soloControl, soloId)` is pure: no mutable field, no counter. Source events keep the amount
  sampled at their onset and get `patternId = soloId`. For every `soloControl` event overlapping the window, one
  control event with `whole == part ==` the overlap and only `control = true`, `solo`, `patternId`. A NaN amount
  reads as 0.0, a non-number as unset (no control event).
- `solo()` / `solo(null)` mean 0.95 (`SOLO_DEFAULT_AMOUNT` in `lang_structural_mute.kt`); the four KDocs say
  `1 - amount` and "`solo(1)`: the others are silent".
- **The solo id** (`soloIdOf`): `"solo@" + callInfo.callLocation`, the location's full text: module name (the
  `source` a library import parses under; null for the main script), line, column span. Why this one: it is the
  plainest value that already exists, it is stable across a live re-evaluation and across two compiles of the same
  code, and it cannot collide between modules, which the atom ids can (`generateSourceId` hashes line and column
  only). Not hashed, so it cannot collide by hash either. Every pattern ONE call site solos shares the id (a mapper
  `solo()` applied to two patterns, a user function that calls `.solo()`): one source to the engine, which is what
  the same call means. Known corner: such a shared call site with DIFFERENT amounts per invocation shares one
  entry, and the last recorded amount wins. A call without a location (the Kotlin door) takes
  `generateSourceId`'s counter (`"solo@id_N"`), unique per call. `generateSourceId` itself is unchanged: the atom ids
  no longer take part in solo.

### Engine

- `SoloTracker` (`audio_be/.../voices/SoloTracker.kt`): fixed arrays (id, amount, end), capacity 32, a source
  beyond it takes the slot of the entry that ends first; linear scan; no allocation after construction. Closes
  audit B4.2. A field of `VoiceScheduler`, which is per playback, so it dies with the playback's engine.
- Recorded in `promoteScheduled` next to master and katalyst, before the control drop and the late guard, from any
  event with `solo` and `sourceId`: end = `epoch + gateEndTime`. An amount that is not positive and finite (0, NaN,
  an infinity) or an end that is not finite records nothing. An amount above 1.0 is kept raw (the Motor stays raw;
  sprudel coerces).
- Live while `end + grace > now` (grace 4 blocks, `SOLO_GRACE_BLOCKS`); protected while `end + hold > now`
  (`SOLO_HOLD_SEC` 2.0 s, at least `SOLO_RAMP_SEC` 1.5 s). `targetGain = 1 - max(live amounts)`, else 1.0.
  `ActiveVoice.soloAmount` and the old `SoloSourceTracker` are gone.
- Realtime path (since review round 2): every realtime voice whose gate is open at a block's start records its
  source until that block's end (`VoiceScheduler.recordRealtimeSolo`, the amount on `VoiceOrigin.Realtime`). The
  solo lasts while ANY voice of the source is held and ends the grace after the last gate closes (one block more only for a fixed gate that closes inside a block; a note-off lands on a block boundary)
  (a note-off, a cut, a voice that ended on its own); a note that made no voice records nothing. Control events
  stay ignored on the realtime path, as before.

### One deviation from the design: the later end wins

The design said a re-recorded source overwrites its entry. The render test showed why that cannot hold for the end:
a soloed note (gate 1 s) and the control event over its cycle (2 s) start at the same time, the heap pops equal
starts in no fixed order, and when the note popped last its shorter gate ended the solo a second early (the
background ramped up in the last rest of the render). Now the AMOUNT is the last writer (so `solo("<1 0.5>")`
follows its pattern) and the END is the later of the two. Consequence, same as before the fix: a soloed note with a
long gate keeps its source soloed until its gate ends, also after a live edit removed the `.solo()`.

### Review round 1 (2026-10-07): what changed

- **The control events are cut on a 1/8-cycle grid** (`SoloPattern.CONTROL_GRID_PER_CYCLE`). A live edit resends
  only the events that start after its cutoff (now plus 0.2 s, `KlangPatternScheduler.resyncCurrentCycle`); with
  one control event per cycle, reviewer B measured an added `.solo(1.0)` taking effect 2 s after the edit (3.7 to
  3.85 s until silence at cps 0.5) and a removed one 2 s after the edit (bed at 99 % 3.3 to 3.4 s after it). With the
  grid both directions take effect within 1/8 cycle after the cutoff, then the ramp runs. Pattern-level guard in
  `SoloPatternSpec`; no engine or frontend change.
- **The per-voice multiplier is ramped across each block** (`SendRenderer.mixRamped`, `Voice.gainMultiplierFrom`):
  linearly over the 128 frames from the value the voice ended the last block on to the new one. Before, a voice
  entering or leaving protection while another solo was live stepped within one sample (reviewer B: a 0.086 step
  against 0.005, and 0.285 against 0.0005 in the pre-existing `solo("<1 0>")` case). When the value does not change
  (1.0 to 1.0, every song without solo) the old constant multiply runs, bit for bit. A voice's first block starts at
  its multiplier (no ramp in from 1.0). The background's own 1.5 s ramp is now also smooth inside each block.
- **A realtime voice that leaves the list ends its source's solo** (a cut or a one-shot sample, without a note-off),
  as well as its note-off. Superseded in round 2: it ended the solo while another voice of the source was held.

### Review round 2 (2026-10-07): what changed

- **The realtime solo is refreshed per block instead of recorded to the held horizon and ended by `endAt`.** Round
  1's "end on leave" ended a source's solo while another of its voices was still held (reviewer, measured: legato
  with key 2 held, the bed back at 0.99996; a mono `cut` line, back at 1.0 on every second note). Now each realtime
  voice with its gate open records its source until the block's end, so the solo follows the held gates. This also
  fixes "two held keys: the first note-off ends the solo" (it was a known difference) and a held note whose voice was
  never made (a sample still loading) keeping its source soloed for hours (it records nothing now).
  `SoloTracker.endAt` is gone. Timeline voices are unchanged: their solo comes from the events.
- **Recorded, no change (NIT 3): the 1/8 grid is in the soloed pattern's own time.** A tempo op after the solo
  scales it with everything else: `.solo().slow(8)` makes one piece per cycle again (up to a cycle of live-edit
  delay comes back), `.solo().fast(16)` sends 128 control events per cycle. Each piece is a heap push, a `record`
  (a scan of a few entries) and one small wire event the frontend filters out of `VoicesScheduled`; at the common
  shape (`.solo()` last, or followed by gain or pitch ops) it is 8 events per cycle per soloed pattern.

### Known differences from the old behaviour (maintainer question)

- **A soloed release tail is ducked after the hold if another solo is live.** Protection now ends 2 s after the
  source's last solo EVENT, not when its last voice leaves. Reviewer B measured it with a pad (release 5 s) at
  `solo("<1 0 0 0>")` beside drums at `.solo()`: the pad's tail plays at full level until 4.0 s, then drops from
  -23.6 dB to -40.8 dB. Before round 1 that drop was a step inside one sample (largest sample step 0.086 against 0.005
  half a second earlier, a click); since the block ramp it is a 128-frame fade (2.7 ms). Alone (no other solo
  live), the background is back at 1.0 by then and nothing changes. The old code kept such a tail at full level.
  Reviewer B's option if the maintainer wants the old tail back: protect a voice whose own activation happened
  inside its source's live window (one flag on `ActiveVoice`), and drop the hold for voices activated later; that
  also stops the pre-existing case below.
- **Pre-existing, kept:** with `solo("<1 0>")` beside another solo, the pattern's voices stay at full level for the
  2 s hold into its 0 cycle, then drop (now a 128-frame fade, before a click: step 0.285).
- **Accepted (review round 1, A MINOR 3): solo state survives a quick stop and resume.** A stopped engine that is
  not yet releasing is resumed by `PlaybackEngineDispatcher.engineFor`, scheduler and all, so the tracker's entries
  and the ramp's level carry over: a restart within about a cycle plus 2 s of stopping a soloed song starts with the
  background ducked and ramps it back up. It expires by itself, and the old tracker and ramp carried over the same
  way. Not reset in `cleanup`, which would lift the background under the tails ringing out.

### Tests (all mutation-checked; the list is in `tmp/reviews/solo-fix-report.md`)

- `sprudel/.../pattern/SoloPatternSpec.kt` (new): rests are control only; control survives the bug-1 chains and the
  Der Schmetterling form; gapless coverage in chunks; the 1/8-cycle grid and the live-edit resend bound; `solo("<1 0>")`; NaN; purity; ids (same code twice, two
  calls, `stack(p.solo(), p.transpose(7))`, two modules at the same line and column).
- `sprudel/.../lang/LangSoloSpec.kt`: 0.97 rows to 0.95, `solo(0.7)` on both doors, control events filtered out.
- `sprudel/src/jvmTest/.../SoloRenderSpec.kt` (new, through `KlangOfflineRenderer`): bug 1, the rest silent and the
  whole render identical to the unsoloed one, both chain forms; bug 2, background 0.05, 0.3 and exact 0. In sprudel,
  not `:klang`, because `:klang` cannot compile sprudel.
- `audio_be/.../voices/VoiceSchedulerSoloCutSpec.kt`: the mapping rows, exact silence, max wins in both orders,
  control-only duck with no voice, the rest held by control events, same-start note and control in both orders, the
  grace, the hold through the ramp back, late control, 0/NaN/Inf/null id, a 0 or NaN source is not protected, held
  realtime solo and its note-off, legato, a mono cut line, two held keys with one released, a held note that made
  no voice, a held realtime voice cut by another source, a muted voice back mid-note, a muted
  voice in release not culled, entering and leaving protection without a sample step (the block ramp), a voice
  that starts muted is silent from its first sample.
- `audio_be/.../voices/SoloTrackerSpec.kt` (new): live and protected windows, the later end, last-writer amount,
  capacity eviction.
- Not added: a `KlangPatternScheduler` row for "control events are not phantom voices": the filter is the existing
  `control != true` line that already serves `master(...)`, unchanged here.


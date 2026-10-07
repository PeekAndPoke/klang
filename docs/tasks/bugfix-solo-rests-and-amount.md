# Bugfix: solo rests play a sine, and the solo amount never mutes

Status: **diagnosed 2026-10-07, not started.** Two bugs the maintainer reported on 2026-10-07. Two decisions taken
the same day (below, "Decided"). The fix touches `VoiceScheduler.kt` and sprudel's `SoloPattern`; schedule it after
voice lifecycle step 5 has landed and been reviewed (`../tasks-archive/2026-10/20261007-voice-lifecycle-state-machine.md`, done 2026-10-07), because the culling row it
needs lives in `VoiceCullingSpec`, which step 5 is editing.

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
  are the overlap (`whole == part`, so it is an onset in every query chunk, the same trick the filler uses today; a
  solo then engages within one chunk after a live edit). Its data is `control = true`, `solo = amount`,
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

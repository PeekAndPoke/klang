# Fractional pitch input: `n("0.5")` scale degrees and cents on note names

**Status:** ❌ WON'T IMPLEMENT (archived 2026-08-24, kept for revision) · **Opened:** 2026-08-21

## 0. Decision: won't implement (2026-08-24)

Closed by the maintainer after a source trace and two throwaway probe runs. Nothing was changed. The
capability we thought was missing turns out to be half present already, and the missing half has no single
defensible meaning.

**The load-bearing reason: "half a rung" is not a fixed size, so it cannot be taught.**

Measured on `c3:minor` (real probe output, `:sprudel:jvmTest`, 2026-08-24):

| rung | -4 | -3 | -2 | -1 | 0 | 1 | 2 | 3 | 4 |
|------|-----|-----|-----|-----|-----|-----|-----|-----|-----|
| note | F2 | G2 | Ab2 | Bb2 | C3 | D3 | Eb3 | F3 | G3 |
| Hz | 87.31 | 98.00 | 103.83 | 116.54 | 130.81 | 146.83 | 155.56 | 174.61 | 196.00 |

The gaps alternate between 1 and 2 semitones. So the midpoint reading of `n("-1.5")` lands between Ab2 and
Bb2, a 2-semitone gap, giving exactly **A2 at 110 Hz**: an ordinary piano key that is not even a member of C
minor. The midpoint reading of `n("1.5")` lands between D3 and Eb3, a 1-semitone gap, giving a **quartertone
near 151.1 Hz** that exists on no keyboard. Same `.5`, two entirely different musical objects, decided only by
where on the ladder you happen to be standing. A learner cannot predict what they will hear, which collides
with the "non-moving learnable target" principle the rest of the sound design follows.

**Supporting reasons:**

1. **The useful half already exists.** `note("60.5")` is genuinely microtonal end to end and has been for a
   while, pinned by an existing test.
2. **A second, independent wall.** Even with a `Double` degree there is nowhere to put it: the scale layer
   resolves a degree to a note *name*, not to a number.
3. **The clean alternative is a different feature.** If per-event cents are ever wanted, a separate
   `.detune(semitones)` control beats overloading `n()`: one meaning everywhere, and it works on note names,
   MIDI numbers, scale degrees and chords alike instead of degrees only.

---

## 1. The question (user, 2026-08-21)

> About the scales, when we have `n("0 1 2 3").scale("c3:major")` for example. Is it also possible to use
> partial tones like `n("0 0.5 1")`? Where `.5` is 50 cents above `0`?
> Would this also be possible for `note("a.2 b c")`? Or do we already have this concept?

And on 2026-08-24, the question that closed it:

> One interesting thing! What does `n("-1.5")` mean?

---

## 2. What was verified

Source-traced and then confirmed empirically with a throwaway probe spec (written, run, deleted; no
production source touched).

### Works today, microtonal, do not "fix" away

- **`note("60.5")`** resolves to a true microtone. Probe: `note("60 60.5 61")` gives 261.63 / **269.29** /
  277.18 Hz, and 269.29 Hz is exactly 50 cents above middle C. Pinned by
  `sprudel/src/commonTest/kotlin/lang/LangNoteSpec.kt` (`note("60 72.5")` asserts `Tones.midiToFreq(72.5)`).
- The door is `Tones.noteNameToMidi`'s raw-number branch, `s.toDoubleOrNull()?.let { return it }`.
- Everything downstream is continuous: `Midi.midiToFreq(midi: Double)`, `SprudelVoiceData.freqHz: Double?`,
  `audio_bridge/VoiceData.freqHz: Double?`, and the ignitor `generate(buffer, freqHz: Double, ctx)` signature.
  **The backend was never the limitation.**
- **`freq(hz)`** is the other working door; its KDoc already advertises microtonal use.
- **Ignitor `detune(semitones: Double)`** is continuous, but per-instrument, not per-event.

### Does not work

- **`n("0 0.5").scale("c3:minor")` emits C3 twice, bit-identical.** The fraction dies at `nMutation`
  (`sprudel/src/commonMain/kotlin/lang/lang_tonal.kt:270`) via `asIntOrNull` =
  `toDoubleOrNull()?.toInt()`, *before* `scale()` ever runs, and `value = null` on the next line destroys the
  raw copy. Same result through the KlangScript door and through `seq(...)`.
- **Second wall:** `Scale.steps(name)` is `(Int) -> String` (`tones/src/commonMain/kotlin/scale/Scale.kt`),
  backed by `Distance.tonicIntervalsTransposer`, whose lookup is `intervals[index]` plus octave math. It
  returns a note *name*. No interpolation, and no place for a fraction to live.
- **`transpose(0.5)` is a silent no-op** (`amount.toInt()`, `lang_tonal.kt:1327`). Probe confirms C3 and D3
  come back unchanged.
- **The scale tables are 12-TET only.** A scale's `chroma` is a 12-bit pitch-class string. `pelog`,
  `hirajoshi`, `iwato`, `in-sen`, `persian` and the raga entries are all genuinely microtonal in their source
  traditions and are stored here as 12-TET approximations, because `Distance.transpose` works on
  fifths-and-octaves pitch coordinates that cannot express a non-12-TET step. Microtonal scales would be a
  second pitch representation, not a table addition.

### Open footgun, deliberately NOT fixed

**`note("a3.5")` silently renders 440 Hz (A4)** while the event's `note` field still reads `"a3.5"`.
`Note.parse` rejects any leftover text after the octave (`tokens[3] != ""` returns `NoNote`,
`tones/src/commonMain/kotlin/note/Note.kt:313`) and `noteNameToMidi` then falls back to `69.0`. Same for
`note("c4+50")`. No error, no warning, and the data disagrees with the sound. If this area is ever reopened,
close this first: it is a bug, not a missing feature.

---

## 3. If this is ever revived: two traps

1. **The fix is `floor`, never `toInt()`.** Truncation toward zero is not monotonic. `-1.5` and `-1` both
   truncate to `-1` while `-2` drops properly, so writing a *lower* number would produce a *higher* pitch.
   Probe confirms today's behaviour: `n("0 -0.5 -1").scale("c3:minor")` gives C3, C3, Bb2.
2. **Prefer a separate `.detune(semitones)` over overloading `n()`.** The word `detune` is already reserved
   for pitch shifting in this codebase, recorded verbatim in `spread()`'s KDoc
   (`sprudel/src/commonMain/kotlin/lang/lang_dynamics.kt`): *"Renamed from `detune()`: in Klang, `detune`
   shifts an oscillator's pitch, this fans the unison stack apart, so it is `spread`."* Ignitor `detune`
   already takes `Double` semitones, so a sprudel-level `.detune()` in semitones satisfies parameter parity
   with no unit divergence.

Three candidate semantics were considered for a fractional degree, and their disagreement is the reason this
is closed rather than merely deferred:

| Reading | `n("-1.5")` on `c3:minor` | Verdict |
|---------|---------------------------|---------|
| Midpoint between neighbouring rungs | A2, 110 Hz (a plain piano key) | Non-uniform: `1.5` would be a quartertone instead |
| Fraction means semitones above the lower rung | Ab2 + 50 cents, ~106.8 Hz | Uniform, but the `.5` then has nothing to do with the ladder |
| Truncate toward zero (today, by accident) | Bb2 | Broken: non-monotonic |

---

## 4. Related

- `audio_bridge/src/commonMain/kotlin/VoiceData.kt` carries a pre-existing TODO naming exactly this gap:
  `// TODO: note can also be numbers -> Midi and detune, f.e. 50.3`. The `note("50.3")` half of it is in fact
  already implemented and tested; only the detune field is absent. The TODO is left in place.
- Naming history: `docs/tasks/…` detune→spread rename, and the "one word per concept" principle.

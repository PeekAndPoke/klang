# Synthsturm — a Darude "Sandstorm" homage (built-in song)

Status: **DONE 2026-07-08** (uncommitted at time of writing).

## Goal

Mimic the iconic sound of Darude's *Sandstorm* — specifically the relentless staccato
"du-du-du" lead — as a new built-in song, using **custom `Osc` ignitors** for the whole kit
(so it also renders offline, where sample drums do not). Faithful-recreation intent, tuned by ear.

## The key finding (sound design)

The popular assumption "the Sandstorm lead is a fat supersaw" is **wrong for the lead**.
Per the producer interviews (Darude / JS16) and the widely-cited patch recipe:

- **The "du-du-du" lead is a distorted saw, not a supersaw.** Darude made the original by
  exporting a simple tracker pattern to WAV and running it through a **Cubase distortion plugin**
  (overdriving a cheap mixer preamp) — later re-recorded on hardware (Nord Lead / Nord Rack 2 /
  JP-8080). The concrete patch: **one sawtooth + a square/pulse layered +31 semitones up**
  (piercing top), a **resonant lowpass**, **heavy distortion (~drive 68%)**, mono, long-decay /
  zero-sustain envelope, light reverb (~10% wet). The distortion is the signature — not detuning.
- **The famous JP-8080 "Sandstorm" boot preset is the fizzy supersaw PAD** under the lead — the
  preset the whole track is *named after* — not the lead itself.

So the correct mapping is the inverse of the naïve one: **lead = distorted saw+square**,
**pad = supersaw**.

### Sources

- MusicRadar — "How Darude created Sandstorm…" (the producer's own account of the WAV-through-a-
  distortion-plugin origin and the JP-8080 "Sandstorm" preset = the chords):
  <https://www.musicradar.com/artists/when-you-turn-on-the-roland-jp-8080-the-first-sound-that-comes-up-is-called-sandstorm-how-darude-created-the-era-defining-trance-anthem-thats-named-after-a-synth-preset>
- Syntorial — "Darude – Sandstorm Lead" preset recipe (concrete patch: saw + pulse +31 semis,
  LP cutoff/res, drive ~68%, long-decay/zero-sustain env, mono, ~10% reverb):
  <https://www.syntorial.com/preset-recipe/darude-sandstorm-lead/>

## What we built

`Synthsturm` — B minor, ~136 BPM (`rpm = 34`, since cps = rpm/60 and 1 cycle = 1 bar of 4 beats).
Entire kit assembled from raw `Osc` primitives (no samples), which doubles as a nice
"how each 90s-techno ingredient is made" teaching artifact:

| Voice                      | Recipe (final)                                                                                                                |
|----------------------------|-------------------------------------------------------------------------------------------------------------------------------|
| **lead** ("du-du-du")      | `Osc.saw().mul(0.5).plus(Osc.square().detune(31).mul(0.5)).lowpass(4200, 2.5).distort(1.0,"soft",4).adsr(0.001,0.7,0.0,0.04)` |
| **pad** (JP-8080 role)     | `Osc.supersaw(Osc.freq(),9,0.3).analog(0.25)` + LFO lowpass; stabbed on a 3-3-2 tresillo `struct("x ~ ~ x ~ ~ x ~")`          |
| **bass**                   | `Osc.saw()` + fast filter env; off-beat 8ths, sidechain pump via `gain(saw.fast(4).range(...))`                               |
| **kick**                   | `Osc.sine().pitchEnvelope(48,...)`                                                                                            |
| **hats / open-hat / clap** | filtered white noise + envelope                                                                                               |
| **riser**                  | `Osc.pinknoise()` + rising `lpf(saw.range(300,6000).slow(8))`                                                                 |

Structure via `arrange([n, section], …)`: intro → groove → build+riser → **drop** (lead lands) →
repeat, over a master `compressor` + light `room`. The 16th "gate" of the lead is the note stream
itself (mono-style `legato`).

Final ear-tuning (by the user): distortion pushed to **1.0** (full send); lead pitched **three
octaves down** (`transpose(-36)`) into the classic mid-low register; lead `gain 0.4 / postgain 0.5`,
pad `gain 0.20`; renamed **Wüstensturm → Synthsturm** (id `builtin-song-synthsturm`).
Verified: peak stayed < 1.0 FS, drop lifts ~+3 dB over the groove.

## Copyright

No issue: all sounds are our own synthesis from primitives, over an **original** B-minor riff
(a best-effort transcription of the contour, not a lifted melody). It's an homage/demo, not a copy.

## Gotchas hit (captured as memories)

- **Ignitor super-osc constructors need `freq` positionally.** `Osc.supersaw(voices = 9, spread = 0.4)`
  fails at runtime (`parameter 'freq' has a complex Kotlin default and was omitted in the middle`);
  use `Osc.supersaw(Osc.freq(), 9, 0.4)`. The skill doc `ref/ignitor-reference.md` currently
  *recommends the broken form* — fix when next touched.
- **Never run two Gradle builds concurrently** on this repo — a `:jvmTest` and a `record.sh`
  (`:runCli`) fired in parallel race on and corrupt the sprudel KSP cache
  (`kspCaches/.../symbols` / `generated/ksp/metadata/commonMain`). Recover with `:sprudel:clean`.

## Verification

- Offline render loop: `./console/record.sh --file <song>.sprudel --cycles 72 --rpm 34 -o out.wav`
  (all-synth kit → drums audible offline), iterated by ear.
- `./gradlew :jvmTest --tests io.peekandpoke.klang.BuiltInSongsSmokeTest` — the
  "every BuiltInSongs entry compiles to a SprudelPattern" case now exercises Synthsturm in place.

## Residual / minor

- The in-code header comment still reads "Wüstensturm … gated supersaw lead" — stale after the
  rename and the lead redesign (it's a distorted saw now). Cosmetic; left as-is per "keep it like this".
- Riff notes remain a best-effort transcription; deliberately kept as an original contour.

## Key files

`src/commonMain/kotlin/builtinsongs/Sandsturm.kt` (the song), `src/commonMain/kotlin/BuiltInSongs.kt`
(registration: import + `val sandsturm` + `songs` list entry), `src/jvmTest/kotlin/BuiltInSongsSmokeTest.kt`
(coverage). Reference docs used: `.claude/skills/klang-music-writing/ref/{sprudel,ignitor}-reference.md`.

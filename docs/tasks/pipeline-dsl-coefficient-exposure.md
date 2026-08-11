# Pipeline DSL — give every engine coefficient a home

**Status:** planned (inventory done 2026-08-11) · **Precursor:** ✅ `docs/tasks/audio-bridge-constants.md`

**Goal (user, 2026-08-11):**

> expose all the surface that we currently have to the pipeline dsl … so all the coefficients that
> have factory defaults need a home in the dsl.

`audio-bridge-constants.md` gave 5 coefficients a DSL home. This tracker covers the rest: the survey below found **~35
more tunable coefficients** that are still compile-time only, plus ~30 that are authorable but from the wrong surface
*and* declared twice.

The sub-tasks are independent unless a dependency is stated. Take them in any order — S2 and S4 are the cheap ones, S1
is the one that unblocks a by-ear question.

---

## 0. The standing rule for every sub-task here

> **Check parameter parity before naming anything.** (user, 2026-08-11)

A coefficient exposed here may already exist on sprudel, `IgnitorDsl`, the cylinder bus or the master. Before adding a
field:

1. **Grep the other surfaces for the concept**, not for the name — the same idea often ships under a different word (see
   S3: the phaser's mix amount is `phaserdepth` in sprudel and `blend` on
   `IgnitorDsl`, and they are not even the same mix law).
2. **Match the name where the surface conventions allow.** They differ deliberately:
   sprudel is lowercase-jammed strudel-style (`phasercenter`, `tremolodepth`, `bodyFloor`, `distos`), the pipeline DSL /
   KlangScript is camelCase methods (`cutoffOffset`, `drivePerAnalog`, `expK`). Parity means the *stem* matches
   (`center` ↔ `phasercenter`), not that the casing does. When a stem has to differ, say why in the KDoc on both sides.
3. **Match the scale exactly.** Same unit, same range, same reference point — no surface may apply a conversion the
   other does not. Shared conversions live in ONE function. This is the rule the
   `roomSize` 10× bug was born from; `driftRelToOsc` is the same shape of hazard (it is a *ratio to another
   coefficient*, so its meaning moves when the other one does).
4. **A wrong parity claim in a KDoc is worse than no claim.** If you write "sprudel twin: `x()`", verify the twin
   behaves the same first.

See memory `feedback_parameter_parity.md` and `docs/tasks/master-dsl-followups.md` §1.

---

## Sub-tasks

### S0 — Close the ignitor/pipeline filter-drive split (**parity defect, already open**)

**This is a bug, not an exposure gap** — do it before or with S2, since both touch `StageDsl.Filter`.

`IgnitorFilters.kt:119` reads the bare `FILTER_DRIVE_PER_ANALOG` while the voice strip threads
`stage.drivePerAnalog`. The 08-11 move unified the *default*; the moment anyone calls
`Stage.filter().drive(x)` the two paths diverge again — `Pipeline.of(Stage.filter().drive(1.0))` on a patch that also
uses an ignitor-level `lowpass(analog = 3)` gives the strip filter `driveScale = 3.0`
and the ignitor filter `0.75`.

Needs either an ignitor-side drive param or a way to thread the active pipeline's `StageDsl.Filter`
into `IgnitorFilters`. Full write-up: `docs/tasks/audio-bridge-constants.md` §6.1.

**Parity check:** this sub-task *is* the parity check — same param, same meaning, one conversion site.

---

### S1 — Oscillator analog-drift depths → an engine tuning object

**The only sub-task that blocks a stated by-ear question.** Owned in detail by
`docs/tasks/analog-drift-ratio-tuning.md` §4 — that doc is authoritative for scope, guard and the measurements that
already constrain the answer. Listed here so the tracker is complete.

| coefficient                                               | default         | note                                                         |
|-----------------------------------------------------------|-----------------|--------------------------------------------------------------|
| `ANALOG_FAST_PEAK_CENTS`                                  | `0.2`           | in scope                                                     |
| `ANALOG_SLOW_PEAK_CENTS`                                  | `0.8`           | in scope                                                     |
| `ANALOG_MEAN_REVERSION_RATIO`                             | `0.5`           | **not listed in either existing doc** — decide in or out     |
| `ANALOG_FAST_TAU_SEC` / `ANALOG_SLOW_TAU_SEC`             | `0.05` / `10.0` | deliberately OUT (06-17: audibility is depth, not timescale) |
| `ANALOG_PEAK_SIGMAS`                                      | `3.0`           | leave — a calibration convention, redundant with the depths  |
| `ANALOG_CENT_PER_MUL`, `ANALOG_SIGMA_X`, `ANALOG_INT_INV` |                 | leave — derived math                                         |

The oscillator is **not** a pipeline stage, so there is no existing slot: this sub-task creates the
`EngineTuning` object on `PipelineDsl`, which is the first real slice of
`docs/tasks/engine-tuning-profile.md` Part B. Everything else in this tracker hangs off stages that already exist.

**Parity check:** `analog` (the *amount*) is already at parity — sprudel `analog()`, `IgnitorDsl`
`Slots.analog`, `StageDsl.Filter.driftRelToOsc` scaled by it. The depths have no counterpart on any surface, so this
sub-task sets the precedent: name them for what they are (cents per unit `analog`)
and keep `driftRelToOsc`'s KDoc in step — it is expressed *relative to* the number you are about to make authorable, so
exposing the depths silently changes what a filter-drift setting means.

---

### S2 — Filter-stage leftovers (3 fields onto the existing `StageDsl.Filter`)

Lowest-risk item on the list: the stage exists, is wired, and has KlangScript extensions.

| coefficient              | default  | declared in                      | proposed field  |
|--------------------------|----------|----------------------------------|-----------------|
| `SAT_STATE_SCALE`        | `0.0876` | `LowPassHighPassFilters.kt:152`  | `satStateScale` |
| `FILTER_SMOOTH_SAMPLES`  | `32`     | `FilterHumanizationCoeffs.kt:36` | `smoothSamples` |
| `DEFAULT_DC_BLOCK_COEFF` | `0.995`  | `LowPassHighPassFilters.kt:159`  | `dcBlockCoeff`  |

Notes that matter:

- `SAT_STATE_SCALE` is read by **both** `SvfLPF`/`SvfHPF` and `IgnitorFilters.kt:182,211` — the exact S0 shape. Do not
  expose it on the stage without deciding what the ignitor path reads.
- `FILTER_SMOOTH_SAMPLES` is an `Int` and `FILTER_INV_SMOOTH_SAMPLES` is derived from it — expose the one, keep deriving
  the other. Its KDoc already documents the "drop to 8 or 16" tuning direction and the click test that must accompany
  it; carry that KDoc onto the field.
- `DEFAULT_DC_BLOCK_COEFF` already has a constructor param (`DcBlocker(coefficient)`) with a guarded fallback — this is
  a wiring job, not a design one.

**Parity check:** none of the three exist on sprudel. `Ignitor.dcBlock(coefficient)` takes the same value at the same
scale (raw IIR pole) — match that name and scale, do not invent a "damping" or
"Hz" restatement of it on the stage.

---

### S3 — `StageDsl.Phaser`: marker object → data class (4 coefficients + a parity decision)

`StageDsl.Phaser` is a bare `data object` while its renderer hardcodes everything:

| coefficient                           | default             | declared in                                                                              |
|---------------------------------------|---------------------|------------------------------------------------------------------------------------------|
| stage count                           | `4`                 | `PhaserCore.DEFAULT_STAGES`                                                              |
| feedback                              | `0.5`               | `PhaserCore.feedback` — `StripPhaserRenderer` comments that it is deliberately unexposed |
| `MIN_MOD_FREQ_HZ` / `MAX_MOD_FREQ_HZ` | `100` / `18000`     | `PhaserCore.kt:170,173`                                                                  |
| center / sweep fallback               | `1000.0` / `1000.0` | `FilterPipelineBuilder.kt:78-79`, inline literals                                        |
| `MAX_FEEDBACK`                        | `0.95`              | stability clamp — **leave alone**                                                        |

⚠️ **This sub-task carries the worst parity situation in the codebase**, and it should be resolved before adding fields,
not after. Three surfaces share `PhaserCore` and disagree:

| surface             | mix param     | mix law                                   |
|---------------------|---------------|-------------------------------------------|
| sprudel             | `phaserdepth` | additive — `dry + wet·depth`              |
| cylinder bus        | `depth`       | additive                                  |
| `IgnitorDsl.Phaser` | `blend`       | **crossfade** — `0` = dry, `1` = wet only |

Same concept, two names, two different maths. Also: `IgnitorDsl.Phaser` defaults `center = 1000.0`
and `sweep = 1000.0` — the same two literals `FilterPipelineBuilder` hardcodes as its fallback, in a second place.

Decide first whether `depth`/`blend` converge, then add `stages` + `feedback`, and decide whether those two also belong
on sprudel (`phaserfeedback`) or stay engine-only character.

---

### S4 — Distortion drive exponent (1 coefficient)

`DistortionRenderer.kt:44` — `private val drive = 10.0.pow(amount * 1.2)`. The `1.2` is a bare inline literal: no name,
no KDoc, no home. It sets how fast the perceptual drive curve climbs, so it is squarely a per-engine character knob (a
"pedal" engine plausibly wants a different curve than
"modern" — that is the whole point of having two engines).

Steps: name it in `audio_bridge/constants/`, add `StageDsl.Distort(driveExponent = …)` — which turns another marker
object into a data class — thread it, add the KlangScript method.

**Parity check:** sprudel has `distort(amount)` / the compound `distort("1:tube:4")` and `distos()`
for oversampling. `amount` stays per-note; the exponent is per-engine. Make sure the KDoc on both sides states the
relationship (`drive = 10^(amount × exponent)`) so nobody re-derives the scale wrongly — an author changing the exponent
changes what every existing `distort(0.8)` in every song sounds like, which is worth a warning in the KDoc.

---

### S5 — Osc-tuning constants → `audio_bridge/constants/` (~30 duplicated literals, no new fields)

**Pure de-duplication. No new DSL fields, no sound change, no wire change.** This is the same defect
`audio-bridge-constants.md` §1.1 fixed for the filter five, still live across a 6× larger surface.

`IgnitorDsl` already exposes most of `OscillatorTuning.kt` per-oscillator-instance — but as **literals**, not
references, because `audio_be` is invisible from `audio_bridge`:

| `IgnitorDsl`                                           | `OscillatorTuning`                                                                                                   | value                 |
|--------------------------------------------------------|----------------------------------------------------------------------------------------------------------------------|-----------------------|
| `Sawtooth.resetSamples` / `Ramp.resetSamples`          | `SAW_RESET_SAMPLES` / `RAMP_RESET_SAMPLES`                                                                           | `2.0`                 |
| `Sawtooth.shapeMax` / `Ramp.shapeMax`                  | `SAW_SHAPE_MAX` / `RAMP_SHAPE_MAX`                                                                                   | `0.5`                 |
| `Super{Saw,Ramp,Square,Tri,Sine}.spreadPower`          | `SUPER*_SPREAD_POWER`                                                                                                | `1.2` (×5)            |
| `…​.sideAtten`                                          | `SUPER*_SIDE_ATTEN`                                                                                                  | `0.1` (×5)            |
| `…​.gainJitter`                                         | `SUPER*_GAIN_JITTER`                                                                                                 | `0.15` (×5)           |
| `…​.centerJitterScale`                                  | `SUPER*_CENTER_JITTER_SCALE`                                                                                         | `0.4` (×5)            |
| `Pulze.flankSamples` / `riseFlank` / `fallFlank`       | `PULSE_MIN_FLANK_SAMPLES` / `PULSE_RISE_FLANK` / `PULSE_FALL_FLANK`                                                  | `2.0` / `0.0` / `0.0` |
| `Slots.chaos` / `color` / `depth` / `tail` / `bipolar` | `CRACKLE_CHAOS_DEFAULT` / `NOISE_TILT_DEFAULT` / `BROWN_LEAK_DEFAULT` / `DUST_TAIL_DEFAULT` / `DUST_BIPOLAR_DEFAULT` |                       |

`Slots.expK` is the **only** one that references its constant (`ADSR_EXP_K`) — it is the model to follow for all the
others.

Do this before S1 lands, or at least before Phase 3 flips any defaults: Phase 3 resolves *instance → engine profile →
field default*, and a field default that is a stale literal poisons the bottom of that cascade silently.

**Parity check:** these names are already parity-clean between `IgnitorDsl` and `OscillatorTuning`
(`spreadPower` ↔ `SUPERSAW_SPREAD_POWER`). Keep it that way — name the moved constants after the DSL field, not the
other way round, and do not rename anything in the same commit as the move (the 08-11 task's "no renames" rule earned
its place; the one rename it did make needed a written justification).

---

### S6 — Oscillator micro-coefficients (6, low value)

Listed for completeness. Each is a genuine tuning knob with no home; none is likely to be reached for.

| coefficient              | default | declared in                                                 |
|--------------------------|---------|-------------------------------------------------------------|
| `CRACKLE_C`              | `0.05`  | `OscillatorTuning.kt:151`                                   |
| `CRACKLE_DC_POLE`        | `0.995` | `OscillatorTuning.kt:154`                                   |
| `NOISE_TILT_LP_COEF`     | `0.15`  | `OscillatorTuning.kt:165` — the tilt pivot (≈1 kHz @ 44.1k) |
| `PERLIN_STEP`            | `0.003` | `Ignitors.kt:1241`                                          |
| `PERLIN_FBM_MAX_OCTAVES` | `8`     | `Ignitors.kt:1244` — a cost cap; arguably leave             |
| `CRACKLE_CHAOS_MAX`      | `2.0`   | divergence clamp — **leave alone**                          |

`NOISE_TILT_LP_COEF` is the one with real musical reach (it moves the pivot the whole `color` tilt rotates around, so it
changes what every `color` value means) — do that one first if any.

**Parity check:** `chaos`, `color`, `depth`, `tail`, `bipolar` are already at parity between sprudel's
`snd*` family and `IgnitorDsl`. These six have no counterpart anywhere; if `NOISE_TILT_LP_COEF` is exposed, decide
whether sprudel's `sndNoise` compound grows a field — and note that doing so runs straight into the compound-param
blocker (`docs/tasks/sprudel-sound-function-surface.md`).

---

### S7 — Resonator floors (boundary note — belongs to the Katalyst DSL)

| coefficient   | default | reachable today                                                                            |
|---------------|---------|--------------------------------------------------------------------------------------------|
| `BODY_FLOOR`  | `0.4`   | per-note `bodyFloor()` (sprudel) → `FilterDef.Body.floor`; **no engine-level default**     |
| `VOWEL_FLOOR` | `0.2`   | per-note `vowelFloor()` (sprudel) → `FilterDef.Formant.floor`; **no engine-level default** |
| `VOWEL_TAME`  | `0.05`  | ❌ nowhere                                                                                 |

Body/vowel run as orbit-level Katalyst effects (`KatalystBodyEffect` / `KatalystFormantEffect`), not as voice-strip
stages, so they are **out of scope for `PipelineDsl`** — they belong to
`docs/tasks/katalyst-dsl.md`. Recorded here only so the survey is complete and `VOWEL_TAME` is not lost: it is the one
resonator coefficient with no authoring path at all, and its KDoc says it is tuned by ear.

---

## Explicitly out of scope

- **Master limiter / master chain constants** — already have their home on `MasterStageDsl`
  (`audio_bridge/constants/MasterLimiterDefaults.kt`); the house-limiter timing is house-only *on purpose*, guarded by
  `MasterDefaultsSyncSpec`.
- **Reverb / delay / compressor / ducking internals** — cylinder-bus effects, not the voice engine.
- **Numerical guards** — `SAFE_MIN`/`SAFE_MAX`, `DENORMAL_THRESHOLD`, `ANTI_DENORMAL`,
  `MAX_CYLINDERS`, `RENDER_QUANTUM_FRAMES`. Not character; see `audio/ref/numerical-safety.md` and memory
  `project_reverb_denormal_handling` (the `+ ANTI_DENORMAL` choice is deliberate — do not
  "fix" it).
- **Derived values** — anything computed from another constant (`FILTER_INV_SMOOTH_SAMPLES`,
  `ADSR_EXP_NORM`, the `AnalogDriftCoeffs` α/β/σ block). Expose the source, keep deriving.

## Guards each sub-task owes

- Extend the `*DefaultsSyncSpec` family: the DSL default must equal the engine constant.
- Wire round-trip (`WireCodecRoundTripSpec`, jvm + js) for every new field.
- A render-effect guard: the field must actually reach the audio (the 08-11 loop caught a pin that passed only
  transitively, against the wrong path).
- Mutation-check every new test, with a no-mutation sanity run on both sides — per `/review-loop`.
- **A parity spec where a twin exists**, asserting the two surfaces produce the same value for the same input. A KDoc
  claim is not a guard.

## Links

- `docs/tasks/audio-bridge-constants.md` — the precursor (§6 = what was still missing; this doc is the answer)
- `docs/tasks/analog-drift-ratio-tuning.md` — owns S1 in detail
- `docs/tasks/engine-tuning-profile.md` — Part B `EngineTuning`, which S1 starts and S5 unblocks
- `docs/tasks/sprudel-sound-function-surface.md` — the compound-param blocker S6 would hit
- `docs/tasks/katalyst-dsl.md` — where S7 lives
- `docs/tasks/master-dsl-followups.md` §1 — the cross-surface parity audit brief

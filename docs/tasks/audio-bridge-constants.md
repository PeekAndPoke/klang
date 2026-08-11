# Engine tuning constants → `audio_bridge` (a single place to reach for)

**Status:** planned · **Branch:** `master-dsl` · **Opened:** 2026-08-11

**Goal (user, 2026-08-11):**

> let us move the engine constants into the audio_bridge and use them where they should be used as
> the defaults of the dsls. Then when we have everything in place, i could create an engine in the
> code and play with params in the live editor for tuning.

**Why now.** The immediate driver is a tuning question that cannot currently be answered:

> Ok all the analog stuff comes from the "plastic pipe" hunt some time ago. So maybe we get it the
> exactly wrong way currently … The pitch drift is high and the filter drift is lower. Maybe it
> should be the opposite?

To test that hypothesis by ear, the drift depths have to be reachable from an authored engine. Today they are
compile-time `internal const val`s in `audio_be`, so answering it means an edit + rebuild per trial. This task removes
the duplication that stands in the way, and §6 records exactly what is *still* missing afterwards.

---

## 1. The defect this fixes

`audio_be` cannot be seen from `audio_bridge` (the dependency runs `audio_be → api(audio_bridge)`), so every constant
that is *also* a DSL default exists **twice** — once as the engine constant, once as a literal in the DSL — held
together by a comment. `IgnitorDsl.kt:111` says so outright:

```kotlin
// expK default mirrors audio_be ADSR_EXP_K (audio_be consts aren't visible from audio_bridge).
```

That convention has now failed, in two distinct ways.

### 1.1 The twins have diverged in source

| constant                          | `audio_be` | DSL default |              |
|-----------------------------------|------------|-------------|--------------|
| `FILTER_CUTOFF_OFFSET_PER_ANALOG` | `0.001`    | `0.0002`    | **diverged** |
| `FILTER_DRIVE_PER_ANALOG`         | `0.5`      | `0.25`      | **diverged** |
| `FILTER_DRIFT_RELATIVE_TO_OSC`    | `2.5`      | `0.25`      | **diverged** |
| `ADSR_EXP_K`                      | `3.0`      | `3.0`       | in sync      |
| `ENV_DECLICK_SECONDS`             | `0.001`    | `0.001`     | in sync      |

The three filter constants carry `Keep in sync with PipelineDsl.Filter.…` in their KDoc. They were not kept in sync —
retuning during the phasiness investigation touched the DSL side only.

### 1.2 The divergence is audible, not cosmetic

`FILTER_DRIVE_PER_ANALOG` is **not** only a DSL default. `IgnitorFilters.kt:119` reads it directly:

```kotlin
val driveScale = analogVal * FILTER_DRIVE_PER_ANALOG
```

and `LowPassHighPassFilters.kt` uses it as a parameter default at four sites (240, 252, 566, 636). The pipeline path,
meanwhile, threads `stage.drivePerAnalog` from the DSL. So **an ignitor-level
`lpf` and a pipeline-level `lpf` currently humanize differently** — 0.5 vs 0.25 per unit `analog`.

This is exactly the failure the parameter-parity rule exists to prevent: same parameter, same meaning, one conversion
site.

`FILTER_CUTOFF_OFFSET_PER_ANALOG` has decayed further still — after the pipeline refactor its only remaining reader is
`AnalogDriftSpec`. Production reads `stage.cutoffOffsetPerAnalog`. It is a mirror that no longer reflects anything.

---

## 2. The rule for what moves

A constant belongs in `audio_bridge` **iff it is a wire default** — i.e. it is (or should be) the default value of a
field on a `@WireFormat` DSL type.

That is the whole criterion, and it is deliberately narrow:

- **Moves** — `FILTER_CUTOFF_OFFSET_PER_ANALOG`, `FILTER_DRIVE_PER_ANALOG`,
  `FILTER_DRIFT_RELATIVE_TO_OSC` (→ `StageDsl.Filter`), `ADSR_EXP_K`, `ENV_DECLICK_SECONDS`
  (→ `StageDsl.Vca`, `IgnitorDsl`), and the master limiter defaults (→ `MasterStageDsl.Limiter`).
- **Stays** — `OscillatorTuning.kt` (36 constants), `AnalogDriftCoeffs.kt`, `FILTER_SMOOTH_SAMPLES`
  / `FILTER_INV_SMOOTH_SAMPLES`. These are engine-internal DSP tuning with no DSL field. Moving them would make
  `audio_bridge` a junk drawer and put implementation detail on the wire contract.

⚠️ **`AnalogDriftCoeffs` stays even though it is what we most want to tune.** See §6 — exposing it is a *separate* piece
of work with a real design question, and folding it in here would conflate a mechanical de-duplication with a new wire
field.

### Value to unify at

The DSL side wins: **`0.0002` / `0.25` / `0.25`**. Those are the values deliberately set while listening on 2026-08-11;
the `audio_be` values are simply stale.

**Accepted consequence:** ignitor-level filter drive changes `0.5 → 0.25`. This is audible on any patch using an ignitor
`lpf`/`hpf` with `analog > 0`. It is the correct direction (it makes the two paths agree, at the value chosen by ear),
but it must be listed as a sound change, not slipped in as a refactor.

---

## 3. Layout

```
audio_bridge/src/commonMain/kotlin/constants/
  FilterHumanizationDefaults.kt   FILTER_CUTOFF_OFFSET_PER_ANALOG, FILTER_DRIVE_PER_ANALOG,
                                  FILTER_DRIFT_RELATIVE_TO_OSC
  EnvelopeDefaults.kt             ADSR_EXP_K, ENV_DECLICK_SECONDS
  MasterLimiterDefaults.kt        LIMITER_THRESHOLD_DB, LIMITER_RATIO, LIMITER_KNEE_DB,
                                  LIMITER_RELEASE_SECONDS, LIMITER_ATTACK_SECONDS,
                                  LIMITER_LOOKAHEAD_SECONDS, AUTHORED_LIMITER_ATTACK_SECONDS,
                                  AUTHORED_LIMITER_LOOKAHEAD_SECONDS
```

Package `io.peekandpoke.klang.audio_bridge.constants`. A subdirectory, not the module root — matching the module's
existing `analyzer/` and `infra/`, and satisfying "a dedicated constants folder … so we always know where to reach".

**Names are kept verbatim.** No renaming in this change: greps, KDoc cross-references and the existing specs all keep
working, and the diff stays reviewable as a pure move.

**KDoc travels with the constant.** Several of these carry hard-won calibration notes (the per-note re-pitch warning on
the cutoff offset; the `analog = 1 / 3 / 10` reference ladder). That prose is the reason the constants are tunable at
all — it must not be left behind in a deleted file.

---

## 4. Phases

### Phase 1 — create the constants files

Move the five filter/envelope constants with their KDoc. `audio_bridge` gains no dependency; these are plain `const val`
s.

### Phase 2 — DSL defaults reference them

`StageDsl.Filter` and `StageDsl.Vca` swap literals for the constants; `IgnitorDsl.kt:111` drops its apology comment and
references `ADSR_EXP_K` directly. `IgnitorDsl.kt:953`'s KDoc stops saying
"mirrors".

### Phase 3 — delete the `audio_be` twins, repoint consumers

Delete from `FilterHumanizationCoeffs.kt` and `AdsrCurveMath.kt`, then repoint:

| site                                        | reads                                                        |
|---------------------------------------------|--------------------------------------------------------------|
| `LowPassHighPassFilters.kt:240,252,566,636` | `FILTER_DRIVE_PER_ANALOG` (param default)                    |
| `IgnitorFilters.kt:119`                     | `FILTER_DRIVE_PER_ANALOG` (**direct use** — the audible one) |
| `EnvelopeRenderer.kt:36,37`                 | `ADSR_EXP_K`, `ENV_DECLICK_SECONDS` (param defaults)         |
| `IgnitorEnvelopes.kt:39,183`                | `ADSR_EXP_K` (param defaults)                                |
| `AdsrCurveMath.kt:29,45`                    | `ADSR_EXP_K` (`ADSR_EXP_NORM`, `adsrExpShape`)               |
| `AnalogDriftSpec.kt:12,13,88,89,99,100`     | both filter constants (test)                                 |

`FilterHumanizationCoeffs.kt` keeps `FILTER_SMOOTH_SAMPLES` + `FILTER_INV_SMOOTH_SAMPLES` and its file header; it does
not disappear.

### Phase 4 — master limiter defaults

Same treatment for the `MasterDsl` literals that mirror `MasterStage` constants.

⚠️ **`MasterDefaultsSyncSpec` needs care, not deletion.** It currently makes two different claims:

1. *"the opt-in Limiter shares the house CHARACTER"* — threshold/ratio/knee/release. Once both sides read one constant
   this becomes a tautology (`X shouldBe X`) and should be **removed**, since a tautological assertion that looks like a
   guard is worse than no assertion.
2. *"the TIMING deliberately differs"* — the authored limiter's attack/lookahead vs the house limiter's. This is **not**
   a tautology: it asserts that two *distinct* constants hold their intended relationship, and its KDoc explains why
   they legitimately diverge (uniform delay on the summed mix vs. per-playback desync). **Keep it, and keep the KDoc.**
3. *"`MasterDsl.default` is EMPTY"* — untouched by this change. Keep.

### Phase 5 — verify

- Whole-project `./gradlew build` under the build lock (never two Gradle invocations at once).
- `AnalogDriftSpec` derives its expectations *from* the constants, so it should follow the new values — confirm it does
  rather than assuming, since its assertions mix them with
  `ANALOG_*_PEAK_CENTS`, which are **not** changing.
- No benchmark needed: this is a compile-time move, identical machine code.
- **By-ear:** one A/B on an ignitor-`lpf` patch with `analog > 0`, for the 0.5 → 0.25 drive change.

---

## 5. What this deliberately does not do

- **No renames.** `driftRelToOsc` vs `FILTER_DRIFT_RELATIVE_TO_OSC` stays as-is.
- **No new wire fields.** The set of DSL parameters is unchanged; only where their defaults live.
- **No retuning.** Except the forced 0.5 → 0.25 unification in §2, no value changes. Tuning happens afterwards, in the
  live editor, which is the point of the exercise.

---

## 6. ⚠️ What is still missing afterwards — the drift-ratio experiment

This task is a precursor. Completing it **does not yet** enable the experiment that motivated it.

The hypothesis to test is that the pitch/filter drift ratio is inverted. Current depths:

| layer                       | per unit `analog` | source                                  | reachable from an authored engine? |
|-----------------------------|-------------------|-----------------------------------------|------------------------------------|
| osc pitch, fast             | 0.2 cents         | `ANALOG_FAST_PEAK_CENTS`                | ❌ no DSL field                    |
| osc pitch, slow             | 0.8 cents         | `ANALOG_SLOW_PEAK_CENTS`                | ❌ no DSL field                    |
| filter cutoff drift         | × 0.25 of osc     | `StageDsl.Filter.driftRelToOsc`         | ✅ after this task                 |
| filter cutoff frozen offset | 0.0002            | `StageDsl.Filter.cutoffOffsetPerAnalog` | ✅ after this task                 |
| filter drive                | 0.25              | `StageDsl.Filter.drivePerAnalog`        | ✅ after this task                 |

So pitch currently runs at **1.0 cent per unit `analog`** against a filter drift of **0.25** — a 4:1 lead for pitch. The
hypothesis is that real hardware is the other way round (VCO pitch is fairly stable; VCF cutoff wanders much more with
temperature and between units).

After this task the *filter* side can be raised live, but the *pitch* side cannot be lowered without a rebuild — so the
ratio can only be explored from one end. Closing that needs a decision:

- **Where does an osc-drift-depth field live?** `AnalogDrift` is constructed per-ignitor (`Ignitors.kt:1233`,
  `initAnalogDrift`), so the natural home is `IgnitorDsl` — but the drift is conceptually a property of the *engine's
  analog character*, alongside the filter humanization on
  `StageDsl.Filter`. Those two readings put the field in different places.
- **One field or two?** `ANALOG_FAST_PEAK_CENTS` and `ANALOG_SLOW_PEAK_CENTS` are separately meaningful (micro-shimmer
  vs. lazy wander), and the 06-17 findings note the slow layer's audibility is *depth, not timescale* — which argues for
  exposing depth per layer rather than one combined scalar.

Tracked here so the precursor is not mistaken for the whole job.

---

## 7. Prior findings that constrain this

- `docs/tasks-archive/2026-06/20260617-analog-drift-coefficient-tuning.md` — where the current filter values came from,
  and why the slow osc layer was **left at 0.8 and never tuned** (listed under "Deferred (intentional)", with "revisit
  only if held/unison patches beat too much" as the stated trigger — a condition that has now been met).
- **Do not add a frequency taper** to the pitch drift (rejected 06-17; drift is already cents-constant, and measurement
  on 2026-08-11 confirmed the fundamental deviates by ~1.2 cents RMS at both c4 and c6 for `analog=12`, against an OU
  ground truth of 1.32).
- Measured 2026-08-11: below ~640 Hz two superimposed voices stay effectively mono; the top three octaves decorrelate
  (0.23 at 5–10 kHz). Depth cannot fix the top end — at 12 cents the phase slips a full cycle every 14 ms at 10 kHz, so
  any depth that still sounds analog is far past the wrap point. The lever there, if wanted, is a **shared drift seed
  between superimposed copies**, not a smaller depth.

---

## 8. Links

- Findings ledger entry: `docs/audio-audit/FINDINGS.md` §F1
- `docs/tasks/master-dsl-followups.md` §1 — cross-surface parameter parity
- `docs/tasks/katalyst-dsl.md` — per-orbit effect chains (where filter cost moves next)

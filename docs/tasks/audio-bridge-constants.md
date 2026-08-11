# Engine tuning constants → `audio_bridge` (a single place to reach for)

**Status:** ✅ DONE 2026-08-11 · **Branch:** `master-dsl` · **Opened:** 2026-08-11

**Three deviations from the plan as written, all decided during implementation:**

1. **Only 6 of the 8 master limiter constants moved.** §3 listed all eight, but applying §2's own rule strictly excludes
   two: `LIMITER_LOOKAHEAD_SECONDS` and `LIMITER_ATTACK_SECONDS` are **house-only**
   — no DSL field carries them, because the house limiter is not authorable — so they are not wire defaults and stay in
   `audio_be`.
2. **One rename, against §5's "no renames".** With the split in place, `MasterStage.LIMITER_ATTACK_SECONDS`
   no longer said anything about being house-only while its bridge counterpart shouted
   `AUTHORED_`. Renamed the two house constants to `HOUSE_LIMITER_*`, giving a rule instead of an accident: **no
   prefix = shared, `HOUSE_` = safety limiter on the summed mix, `AUTHORED_` = the opt-in per-playback stage.** (Raised
   by the user: *"Why has this constant AUTHORED_LIMITER_ATTACK_SECONDS the prefix AUTHORED … seems strange compared to
   the rest."*)
3. **`AnalogDriftSpec`'s ceilings were retightened.** Not in the plan, but the values had been lowered (0.001→0.0002,
   2.5→0.25) without the bounds following, leaving 6× and 12× of slack — the guard could no longer fail. Now `< 2.0` /
   `< 1.5` against actual 1.04 / 0.75, and mutation-checked:
   drift 0.25→0.6 and offset 0.0002→0.0005 each turn it RED, with a clean no-mutation sanity run on both sides.

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

**Accepted consequence:** ignitor-level filter drive changes `0.5 → 0.25`. It is the correct direction (it makes the two
paths agree, at the value chosen by ear), but it is a sound change and must be listed as one, not slipped in as a
refactor. ⚠️ **The size of it was measured after the fact and is far smaller than this section originally claimed — ≤
0.06 dB on every shipped builtin. See §6.2.**

---

## 3. Layout

```
audio_bridge/src/commonMain/kotlin/constants/
  FilterHumanizationDefaults.kt   FILTER_CUTOFF_OFFSET_PER_ANALOG, FILTER_DRIVE_PER_ANALOG,
                                  FILTER_DRIFT_RELATIVE_TO_OSC
  EnvelopeDefaults.kt             ADSR_EXP_K, ENV_DECLICK_SECONDS
  MasterLimiterDefaults.kt        LIMITER_THRESHOLD_DB, LIMITER_RATIO, LIMITER_KNEE_DB,
                                  LIMITER_RELEASE_SECONDS, AUTHORED_LIMITER_ATTACK_SECONDS,
                                  AUTHORED_LIMITER_LOOKAHEAD_SECONDS
```

⚠️ **Amended during implementation** — this originally also listed `LIMITER_ATTACK_SECONDS` and
`LIMITER_LOOKAHEAD_SECONDS`. They are house-only (no DSL field carries them), so §2's rule excludes them; they stay in
`MasterStage`, renamed `HOUSE_LIMITER_*`. See deviation 1 at the top.

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
| filter drive                | 0.25              | `StageDsl.Filter.drivePerAnalog`        | ⚠️ pipeline path only — see 6.1    |

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

### 6.1 ⚠️ Filter-drive parity is restored at the DEFAULT only

Raised independently by both reviewers, 2026-08-11. `IgnitorFilters.kt:119` reads the bare constant:

```kotlin
val driveScale = analogVal * FILTER_DRIVE_PER_ANALOG
```

while the pipeline path threads `stage.drivePerAnalog`, which **is** authorable — `Stage.filter().drive(x)` exists in
KlangScript today. So the moment anyone sets it, the two paths diverge again by exactly the §1.2 defect:
`Pipeline.of(Stage.filter().drive(1.0))` on a patch that also uses an ignitor-level `lowpass(analog = 3)` gives the
sprudel filter a driveScale of 3.0 and the ignitor filter 0.75 — a 4:1 split.

The move fixed the *stale duplicate*; it cannot fix this, because the ignitor filter has no pipeline stage to carry the
value. Closing it needs either an ignitor-side drive param or a way to thread the active pipeline's `StageDsl.Filter`
into `IgnitorFilters`. Same shape of question as the osc-drift-depth fork above, and probably wants the same answer.

### 6.2 The 0.5 → 0.25 drive change is far smaller than §2 claims

Measured by the audio reviewer against the real `SvfLPF`/`SvfHPF` loop, not estimated:

| patch                              | level change at the resonance peak |
|------------------------------------|------------------------------------|
| Q=5, analog=5                      | +0.72 dB                           |
| Q=10, analog=5                     | +1.25 dB                           |
| shipped builtins at unit amplitude | **≤ 0.06 dB**                      |

Two builtins use the ignitor filter path with `analog > 0`: `Sakura.kt:27,33,34,57` (q 0.707–2.0 at analog=8) and
`ATruthWorthLyingFor.kt:48-49` (q 1.8 / 0.7, running at **analog=10** — its `Osc.param("analog", 3.50)` is overridden by
the stack-level `.analog(10)`). Measured: −0.06 … +0.06 dB across all six taps. At those Q values the change is
inaudible, so §2's "audible on any patch using an ignitor `lpf`/`hpf` with `analog > 0`" **overstates it** — audibility
needs Q ≳ 5.

⚠️ **The ≤ 0.06 dB bound is amplitude-conditional, not absolute.** `tCfb` is driven by `ic1eq`, so the delta grows with
level: the same Sakura q=2 tap measures +0.06 dB at amplitude 1.0, **+0.32 dB at 2.0, +0.69 dB at 4.0**. Shipped content
stays in the safe region, but **not by any single mechanism** — every shipped tap happens to be fed either by explicitly
weighted `.mul()` sums ≤ 1 or by a hard-bounded waveshaper:

- `Sakura.kt:27` (koto) — `Osc.pluck()` + `sine.mul(0.1)`, no super-osc in the chain at all.
- `Sakura.kt:33,34` (shaku) — hand-written weights `0.6 + 0.25 + 0.05 + 0.10` summing to exactly 1.0.
- `Sakura.kt:57` (pad) — the one tap the super-osc gain renormalisation (`Ignitors.kt:742`, Σ|gain| = 1) actually
  bounds.
- `ATruthWorthLyingFor.kt:48,49` — bounded by `.distort(≈3.6–4.0, "tube", 8)` sitting immediately upstream of the
  filter; `ClippingFuncs.tube` ≤ 1.0 plus the `softCap` at `IgnitorEffects.kt:109` (guarded by
  `ClippingFuncsBoundsSpec.kt:148`). Voice-gain renormalisation is a waveshaper away and contributes nothing here.

⚠️ **Nothing in the engine enforces any of this.** An added `.mul(3)` or `.drive()` ahead of an ignitor lowpass walks
straight into the +0.3…+0.7 dB region while leaving every sentence above still true. An earlier draft credited the
super-osc renormalisation alone, which reads as a guarantee and is not one.

⚠️ **The first survey of this missed `ATruthWorthLyingFor` entirely** (its `Osc.freq()` nests a paren, defeating a regex
sweep) — and that is the song sitting at the top of the analog range. The conclusion survived, but a sound change was
justified on a one-song survey. Grep for `.lowpass(`/`.highpass(` with a third positional argument, not for a literal
`analog =`.

Stability also moves the *safe* way. The diode-pair polynomial dips negative (`tCfb = -0.0034` at `ic1eq ≈ -1.55`), so
the **instantaneous** `kEff` can go negative once `k < 2·driveScale·0.0034`. Halving the drive **doubles** the Q at
which that is reachable: at `analog=10` the threshold moves from `Q > 29.4` to `Q > 58.9`. The criterion is monotone in
`driveScale`, so the old 0.5 was strictly the riskier value and no patch class relied on drive compression to stay tame.

**This is not self-oscillation, and an earlier draft of this section wrongly called it that.** The polynomial is
asymmetric — `tCfb ≥ 0` for `ic1eq ≥ 0`, and grows ~8× faster there — so net per-cycle damping stays positive even when
individual samples go negative. Measured at q=200 / analog=10 / drive 0.25: 9722 of 144k samples have `kEff < 0`, and
the tail still decays to 2.3e-11 within 2 s. Nothing self-oscillates at any reachable Q (the SVF clamps Q at 200). Read
the thresholds as "where the nonlinearity starts fighting the damping", not as a ringing hazard.

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

## 7b. Review record (`/review-loop`, 2026-08-11)

Five rounds, fresh coding + audio reviewers each round. **Terminated on the safety valve, not on a clean round** — round
5 still returned 4 findings, all comment/doc accuracy. Findings by round: 11 / 9 / 11 / 8 / 4.

⚠️ **Round 5's fixes are themselves unreviewed** — no round 6 was run. They were: the inverted ratio KDoc (below), one
redundant assertion dropped, and two comment-accuracy corrections.

**Zero defects were found in engine behaviour.** Every finding was in a test, a comment, or a doc. Both reviewers
independently re-derived the DSP numerics from source each round and confirmed the audio is identical to pre-change
except the intended `FILTER_DRIVE_PER_ANALOG` 0.5 → 0.25 on the ignitor path.

What the loop actually caught, worth keeping:

1. **Two constants had no value coverage at all.** `ADSR_EXP_K` could be changed to anything with the whole suite
   green — every envelope test is either symmetric in K or checks endpoints, which are K-invariant by construction. Now
   pinned by an attack-midpoint golden. And `FILTER_DRIVE_PER_ANALOG` had no *upper*
   bound: `IgnitorCombinatorsSpec`'s compression guard is one-sided, so raising the drive made it greener.
2. **A fix aimed at the wrong path.** The first drive pin read `StageDsl.Filter().drivePerAnalog` — the pipeline path —
   while the path this change alters is `IgnitorFilters.kt:119`, which reads the constant directly. It passed only
   transitively. Both are pinned now; the two-file mutation (DSL literal + retuned constant, silently reinstating the
   §1.2 split) is verified RED.
3. **`AnalogDriftSpec` was reopening audit F2 on itself** — it still read the bare constants, so replacing a DSL default
   with a literal was invisible to it. It reads the wire model now.
4. **The canonical KDoc for `FILTER_DRIFT_RELATIVE_TO_OSC` named the ratio backwards** ("pitch-to-filter"),
   contradicting the two other descriptions this same change added. Survived four rounds. This is the constant §6's
   entire open question is about, so a reader taking it at face value would have retuned in the wrong direction.
5. **Three of §6.2's own claims were wrong**: the builtin survey missed `ATruthWorthLyingFor` (a regex sweep defeated by
   a nested paren, on the song that sits highest in the analog range); the ≤ 0.06 dB bound is amplitude-conditional; and
   "self-oscillation" was the wrong label for negative instantaneous `kEff`.
6. **A cited test harness does not exist.** `AdsrPlopAnalysisTest` is referenced as the provenance of the de-click
   calibration and is nowhere in the repo — a dangling reference that got copied verbatim into the new constants file
   before being caught.

Every test added or changed was mutation-checked with a no-mutation sanity run on both sides and a verified clean
restore.

## 8. Links

- Findings ledger entry: `docs/audio-audit/FINDINGS.md` §F1
- `docs/tasks/master-dsl-followups.md` §1 — cross-surface parameter parity
- `docs/tasks/katalyst-dsl.md` — per-orbit effect chains (where filter cost moves next)

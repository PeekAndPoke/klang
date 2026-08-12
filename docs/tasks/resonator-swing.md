# Resonator swing — make body/vowel move with the sound

**Status:** planned (designed 2026-08-12, not started) · **Opened:** 2026-08-12

**The question (user, 2026-08-12):**

> currently our body/vowel filters are static? Can we make them breath / resonate with the sound …
> can we measure the amount of resonance per comb and "excite" these, make them swing, when there
> energy accumulates there. Currently these combs feel hard, they need some movement.

Answer: yes, static in three independent ways — and the measurement is already sitting in the filter, unused.

---

## 1. What is static today

| # | where                                     | why it cannot move                                                                                                                               |
|---|-------------------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------|
| 1 | `BodyFilter.kt` / `FormantFilter.kt`      | each band is an `SvfBPF` built with fixed freq/Q at construction; `BaseSvf.q` is a `private val` — *"q is fixed at construction"* is in its KDoc |
| 2 | `LowPassHighPassFilters.SvfBPF.process()` | pure linear TPT loop. `SvfLPF`/`SvfHPF` carry a second, **nonlinear** branch; the bandpass never got one                                         |
| 3 | `ParallelMixFilter.kt:48`                 | `dryGain` is precomputed at construction — even the wet/dry ratio is frozen                                                                      |

The result is a fixed EQ curve: every note, every dynamic, the identical bank. That is the "hard".

**Terminology:** these are parallel **bandpass banks**, not combs — no delay line, so there is nothing to feed back
into. That is why the fix is coefficient modulation and not a feedback path.

## 2. The measurement is already there

Every `SvfBPF` carries `ic1eq` — the bandpass integrator state, i.e. that mode's instantaneous resonant energy.
`SvfLPF`/`SvfHPF` already read it **every sample** and modulate their damping from it:

```kotlin
val tCfb = diodePairResistanceApprox(ic1eq * SAT_STATE_SCALE) - 1.0
val kEff = k + 2.0 * drv * tCfb
```

`SAT_STATE_SCALE` exists for exactly this purpose. So "measure the resonance per band and change the filter's behaviour
from it" is a shipped, working technique here — it was simply never applied to the bandpass.

⚠️ **A stale note that would otherwise read as a blocker.** Memory `project_filter_saturation_dead_end`
(2026-05) says the filter is *"intentionally linear at all `analog` values"* and that the infrastructure is *"preserved
but unused"*. That is **out of date**: the dead-end was specifically *tanh inside the feedback signal*; the
state-dependent **damping** approach shipped afterwards and works (see the `SvfLPF` KDoc, *"Why this works, where prior
tanh attempts failed"*). Update that memory as part of this task.

## 3. The model — swing

**One rule:** *each band's centre frequency pulls off-centre as energy accumulates in that band, and relaxes back when
it drains.*

Per band, once per block:

```
env   = one-pole(peak |band output| over the previous block)      // attack / release smoothed
fMod  = 1 + swingCents * ANALOG_CENT_PER_MUL * (env / (1 + env))
svf.setCutoff(mode.freq * fMod)
```

Three choices worth stating, because each one is load-bearing:

- **`setCutoff` is reused verbatim — no filter changes at all.** `BaseSvf.setCutoff` already recomputes every
  coefficient *and* ramps them over `FILTER_SMOOTH_SAMPLES` (32) to mask the discontinuity. That is precisely the
  pattern `FilterModRenderer` already uses to drive the voice filter's cutoff once per block. Nothing in `BaseSvf`,
  `SvfBPF` or any filter class needs to change.
- **Strength is in cents**, converted only through `ANALOG_CENT_PER_MUL` (`5.7780e-4`) — the same constant
  `AnalogDriftCoeffs` uses. One conversion site, and swing lands on the same scale as every other detune in the engine.
  **Signed**: negative = the mode goes flat under load. Do not clamp the direction (Motör stays raw).
- **`env / (1 + env)` soft-saturates the drive.** The orbit mix runs well above unity before the limiter; without this,
  hot material throws the detune arbitrarily far. With it, `swing` reads directly as *"asymptotic maximum detune in
  cents at full drive"* — a learnable number.

**Energy source, for free:** `BodyFilter.process()` already loops over each band's output in
`bandBuffer` before summing it. A peak accumulation there is ~1 op/sample. The envelope is measured over block N and
applied at block N+1 — one block of latency (2.67 ms at 128 frames / 48 kHz), inaudible for this.

**Cost:** one `tan()` per band per block plus the peak accumulation. 8 bands × 2 channels, **once per orbit** —
body/vowel have been orbit-level Katalyst effects since July, and `VoiceFactory:391` now
`error()`s on a per-voice `FilterDef.Body`. Nothing is paid per voice.

## 4. Rejected / deferred alternatives

| approach                                                                                     | verdict                                                                                                                                                                                                                                                                                                                                                                                                                      |
|----------------------------------------------------------------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **Damping ← energy** (`k` modulated, the `SvfLPF` diode-pair trick transplanted to `SvfBPF`) | **Deferred, not rejected.** Gives *give / bloom* rather than movement. The diode polynomial is fitted for LPF resonance at Q ≈ 0.7–10; body reaches Q 60 and vowel Q 140, and `ic1eq` scales with Q, so the curve lands far outside its fit — needs re-scaling plus a stability sweep. Costs ~4× per band (measured on `SvfHPF`: 0.000167 → 0.000701 JVM). Revisit as a second strength knob **after** swing has been heard. |
| **Both at once**                                                                             | Rejected as a first step: it merges a cheap safe change with an expensive risky one, and you cannot tell by ear which half is working while tuning. Same end state is reachable incrementally.                                                                                                                                                                                                                               |
| **Slow autonomous drift per band** (reuse `AnalogDrift`, no energy coupling)                 | Not what was asked — it ignores what you play. Cheap (~free) and orthogonal; keep as a possible later addition, not part of this task.                                                                                                                                                                                                                                                                                       |
| **Feedback / comb topology**                                                                 | Not applicable — these are parallel bandpasses with no delay line.                                                                                                                                                                                                                                                                                                                                                           |

## 5. Opt in / opt out — the strength rule

> *"there must be a way to opt in or out depending on the default and to set the strength where
> str = 0 basically means opted out"* — user, 2026-08-12

- `swing: Double? = null` on the wire → resolves to the **engine/material default**, which ships as
  `0.0` for every material. This mirrors the existing `floor: Double? = null` idiom on
  `FilterDef.Body` / `FilterDef.Formant` exactly, and leaves room for per-material defaults later (glass plausibly wants
  a different swing than wood) without a second wire change.
- Explicit `0.0` = **opted out**, whatever the default later becomes.
- Any non-zero value = opted in, and *is* the strength.

**At `swing == 0` the code short-circuits before any work** — no peak accumulation, no `setCutoff`, no envelope state.
The branch sits outside the sample loop, so the cost is one comparison per block and the output is **bit-identical to
today**. This is a guarded requirement (§8), not an aspiration.

## 6. Scope

| file                                                       | change                                                                                                  |
|------------------------------------------------------------|---------------------------------------------------------------------------------------------------------|
| `audio_bridge/.../FilterDef.kt`                            | `swing: Double? = null` on `Body` and `Formant`, beside the existing `floor`                            |
| `audio_bridge/.../constants/`                              | new file for the two envelope time constants                                                            |
| `audio_be/.../filters/BandSwing.kt`                        | **new** — per-band envelope follower + `fMod`                                                           |
| `audio_be/.../filters/BodyFilter.kt`                       | own a `BandSwing` per band; accumulate peak in the band loop that already exists; `setCutoff` per block |
| `audio_be/.../filters/FormantFilter.kt`                    | same                                                                                                    |
| `audio_be/.../filters/LowPassHighPassFilters.kt`           | `createBody` / `createFormant` take `swing`                                                             |
| `audio_be/.../cylinders/katalyst/KatalystBodyEffect.kt`    | thread `swing`; add it to the rebuild-trigger comparison (`body.bands != curBands                       || …`) |
| `audio_be/.../cylinders/katalyst/KatalystFormantEffect.kt` | same                                                                                                    |
| `sprudel/.../lang/lang_body.kt`                            | `bodySwing(cents)`                                                                                      |
| `sprudel/.../lang/lang_vowel.kt`                           | `vowelSwing(cents)`                                                                                     |
| `sprudel/.../SprudelVoiceData.kt`                          | plumb both into `FilterDef` (near the existing `floor` handling, ~`:940`)                               |

⚠️ **Do not dedupe `BodyFilter` / `FormantFilter` in this task.** They are near-verbatim twins, and
`KatalystBodyEffect`'s KDoc says so deliberately: *"left un-deduped on purpose — both will fold into a single generic
resonator once the Katalyst DSL lands"*. Put the new logic in the shared `BandSwing`
class so the *feature* is written once, and leave the two banks alone.

**Also update in the same change:** memory `project_filter_saturation_dead_end` (§2), and the
`katalyst-dsl.md` superposition sentence (§7.4).

## 7. Risks

1. **FM of a ringing high-Q resonator.** Moving a band's centre while it is mid-ring *is* frequency modulation of a
   resonator — too fast a release and it warbles or throws sidebands instead of swinging. The 32-sample coefficient ramp
   helps but does not solve it; the **release time constant is the real control**. Start slow (release ≫ attack) and
   tune by ear on percussive material, which is where a too-fast release shows up first.
2. **Vowel identity.** Formants at Q up to 140 *are* what makes a vowel that vowel. Moving them changes which vowel is
   heard, not just its colour. **Ship the field on vowel but leave its default at 0 and tune body first.**
3. **The caricature rule.** Per `feedback_caricature_sound_model`, the target must stay learnable —
   `wood` must still sound like `wood`. Argues for small values, and against per-material defaults until there has been
   real listening.
4. **A doc claim goes stale.** `docs/tasks/katalyst-dsl.md` states *"`SvfBPF` has no `analog` parameter at all, so every
   bandpass is in this class"* — i.e. linear, therefore movable per-voice → per-orbit losslessly. That holds only while
   `swing == 0`: a swinging bank is time-varying and no longer obeys superposition. No actual regression (body/vowel are
   already orbit-only), but the sentence needs a qualifier.

## 8. Parameter parity

Per `docs/tasks/pipeline-dsl-coefficient-exposure.md` §0:

- **Names follow the existing pattern exactly.** sprudel already has `bodyFloor(floor)` /
  `vowelFloor(floor)` → `bodySwing(cents)` / `vowelSwing(cents)`: same stem, same casing convention, same `PatternLike?`
  signature, same `null` = engine-default semantic.
- **Scale is cents on every surface**, converted only via `ANALOG_CENT_PER_MUL`. Do not restate it as a fraction, a
  percentage or a ratio anywhere.
- **The two envelope time constants get a DSL home from day one** — do not add two new orphan
  `internal const val`s; that is precisely the debt the coefficient-exposure tracker exists to stop.
- **Katalyst DSL** (`docs/tasks/katalyst-dsl.md`) is not built yet; note `swing` there so the authoring surface carries
  it when it lands.

## 9. Guards

Build lock: `.claude/BUILD-LOCK.md` — **never two Gradle invocations at once** (parallel builds corrupt the sprudel KSP
cache; recover with `:sprudel:clean`).

```bash
./gradlew :audio_bridge:jvmTest :audio_be:jvmTest :sprudel:jvmTest
```

1. **`swing = 0` → bit-identical.** Render a body-processed buffer before and after and compare exactly. This is the
   opt-out contract — a golden, not an approximate bound.
2. **Render-effect guard.** With `swing > 0`, drive one band hard and assert its resonant peak shifts relative to a
   quiet drive (FFT peak, or the ringing period of an impulse response). Must go RED if the `setCutoff` call is deleted.
3. **Envelope follower:** attack/release monotonicity, and `env/(1+env) ∈ [0, 1)` for a deliberately absurd input level.
4. **Sign:** negative `swing` moves the peak the other way.
5. **Wire round-trip** for both fields, jvm + js (`WireCodecRoundTripSpec`).
6. **Sprudel DSL** in all four mapper forms — **including the chained `PatternMapperFn.` form**, the codebase-wide test
   gap (memory `sprudel_dsl_test_coverage`).
7. **Mutation-check every new test**, with a no-mutation sanity run on both sides, per `/review-loop`.

**By ear is the actual acceptance test:** offline-render a body-heavy patch at several `swing` values via
`/klang-music-recording`, on sustained *and* percussive material.

## 10. Links

- `docs/tasks/katalyst-dsl.md` — where body/vowel authoring lands; carries the superposition sentence to fix
- `docs/tasks/pipeline-dsl-coefficient-exposure.md` — §0 parity rule; S7 lists the resonator floors
- `audio/ref/effects-mixing.md` — the orbit/Katalyst pipeline
- `docs/tasks-archive/2026-07/20260703-body-vowel-orbit-katalyst.md` — why body/vowel moved to orbit level

# Numerical Safety in Audio DSP

Reference for choosing safety bounds in real-time audio arithmetic — what to do
about NaN, Inf, subnormals, and runaway feedback. Grounded in conventions from
SuperCollider, JUCE, Faust, ChucK/STK, Pure Data, and CSound, not invented.

## The three failure modes

1. **NaN poisoning** — `0/0`, `log(-x)`, `sqrt(-x)`, `0^negative`, `Inf - Inf`. One NaN
   sample, one filter cycle later, the entire voice is silent (NaN propagates
   through filter state forever).
2. **Inf overflow** — `1/0`, `pow(big, big)`, `exp(huge)`. Same as NaN once it
   feeds into a multiplication.
3. **Subnormal stalls** — Float values below `~1.18e-38` (or Double below `~2.23e-308`)
   are computed in microcode on x86 and can be 10–100× slower than normal arithmetic.
   Audible as CPU spikes when a filter's tail decays through the subnormal range.

## Three industry strategies

| Strategy                               | Used by                               | Mechanism                                                                                                                               |
|----------------------------------------|---------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------|
| Hardware FTZ + per-filter snap-to-zero | JUCE, modern plugins                  | Set MXCSR FTZ/DAZ flag once; snap tiny residuals (`< 1e-8`) inside IIR state. **No upper bound** — overflow is the algorithm's problem. |
| Per-UGen `zap-to-zero` bounds          | SuperCollider, ChucK/STK, Pure Data   | Apply explicit `[lo, hi]` clamp at both ends; values outside the range → `0`. Opt-in per algorithm.                                     |
| DC-offset noise injection              | CSound, Pirkle textbook, KVR practice | Add a tiny constant (`~1e-25`) to keep state out of the subnormal zone; rely on algorithm dynamics for upper bound.                     |

There is **no AES standard** for "internal arithmetic safety bounds." Each
framework picks its own point on the curve.

## The de facto numerical convention

When a framework picks an explicit zap-to-zero range, it almost always lands at
**`1e-15` (lower) / `1e15` (upper)** for single precision — and Klang follows
this convention.

| Framework                     | Lower              | Upper     | Source                                                             |
|-------------------------------|--------------------|-----------|--------------------------------------------------------------------|
| SuperCollider `zapgremlins`   | `1e-15f`           | `1e15f`   | `include/plugin_interface/SC_InlineUnaryOp.h:58-70`                |
| ChucK / STK `CK_DDN_*`        | `1e-15`            | `1e15`    | `chuck/src/core/chuck_def.h:201-206` (identical to SC)             |
| Pure Data `PD_BIGORSMALL`     | `~5.4e-20`         | `~1.8e19` | `pd/src/m_pd.h:940-945` (exponent-range test, slightly looser)     |
| JUCE filter design floor      | `1e-15`            | —         | `juce_dsp/filter_design/juce_FilterDesign.cpp:411-412` (`-300 dB`) |
| JUCE IIR snap-to-zero         | `1e-8f`            | —         | `juce_FloatVectorOperations.h:38-44` (lower only, no upper)        |
| Faust `EPSILON` (single)      | `1.19e-7f`         | —         | `maths.lib` (machine epsilon, used for "is-zero" guards)           |
| Faust `MIN` (FTZ)             | `1.175e-38f`       | —         | `maths.lib` (subnormal threshold)                                  |
| CSound denormal flush         | `1e-30f`           | —         | `csound/include/sysdep.h:432-449` (DC-offset trick)                |
| KVR community noise injection | `1e-15` to `1e-25` | —         | square-wave or DC-offset, not a clamp                              |

**`1e-15 / 1e15` corresponds to ±300 dBFS** — well below any audible signal (24-bit
audio dynamic range is ~144 dB; 32-bit float audio ~192 dB), well above the
subnormal cliff (`1.18e-38`).

**Tiers without precedent for arithmetic safety:**

- `1e-5 / 1e5` (±100 dB) — JUCE uses `-100 dB` only as a *display* floor (`Decibels::defaultMinusInfinitydB`), never
  internal arithmetic.
- `1e-9 / 1e9` (±180 dB) — **no framework uses this**. Closest is JUCE's `1e-8f` snap-to-zero, but JUCE only applies it
  in IIR state and has no upper bound at all. Tighter than needed for denormal protection (real subnormals start at ~
  1e-38) and risks clipping legitimate ultra-quiet tails (long-time-constant compressor sidechains, reverb decays).

## Klang's choice

Klang uses **`SAFE_MIN = 1e-15f` / `SAFE_MAX = 1e15f`** for arithmetic safety,
matching SuperCollider/ChucK convention exactly.

The constants live in `audio_be/src/commonMain/kotlin/ignitor/Ignitor.kt`.

### Where guards apply

- **Divisor-class ops** (`Div`, `Mod`, `Recip`) — divisor magnitude is clamped
  to `≥ SAFE_MIN`, sign preserved. Prevents `1/0` or `1/subnormal` overflow.
- **Output-clamp ops** (`Times`, `Pow`, `Exp`, `Sq`) — output magnitude is
  clamped to `≤ SAFE_MAX`, sign preserved. Prevents runaway products from
  overflowing Float.
- **Naturally bounded ops** — `Plus`, `Minus`, `Lerp`, `Range`, `Min`, `Max`,
  `Clamp`, `Bipolar`, `Unipolar`, `Tanh`, `Abs`, `Neg`, `Sign`, `Floor`,
  `Ceil`, `Round`, `Frac`, `Sqrt`, `Log` — output bounded by their inputs
  or by their algebraic properties; no extra guard needed.

### Round-trip guarantees

- `1 / SAFE_MIN = SAFE_MAX` ✓ (reciprocal of the smallest allowed divisor lands
  exactly at the largest allowed output — no further clamp needed).
- `SAFE_MAX² = 1e30` is still finite Float (`Float.MAX_VALUE ≈ 3.4e38`), so
  even an unclamped square between two max-safe values doesn't overflow.

## Closed gaps

All known gaps as of 2026-04-27 have been addressed. Kept here for
historical reference and to make the regression-test surface obvious.

| File:fn                                         | Issue                                                                                                                                                                | Closed by (date / fix)                                                                                              |
|-------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------------------------------|
| `PitchModFactories.kt::vibratoModIgnitor`       | `2.0.pow(...)` overflowed Float for large `depthSemitones`, poisoning phase accumulators.                                                                            | 2026-04-27 — wrapped in `safeOut(...)`.                                                                             |
| `PitchModFactories.kt::accelerateModIgnitor`    | `2.0.pow(amountVal * progress)` accumulated multiplicatively past `Float.MAX_VALUE`.                                                                                 | 2026-04-27 — `safeOut(ratio.toFloat())` per sample.                                                                 |
| `PitchModFactories.kt::pitchEnvelopeModIgnitor` | `2.0.pow(amountVal * envLevel / 12.0)` overflowed for `                                                                                                              | amount                                                                                                              | > ~440`.                                               | 2026-04-27 — wrapped in `safeOut(...)`.                             |
| `PitchModFactories.kt::fmModIgnitor`            | `effectiveDepth / freqHz` only guarded `freqHz <= 0`, not sub-Hz pitches from heavy detune.                                                                          | 2026-04-27 — `safeDiv(freqHz.toFloat())` + `safeOut` on output.                                                     |
| `DspUtil.kt::wrapPhase`                         | O(N) `while (p >= period) p -= period` loop hung the audio thread when given any extreme phase (Inf/NaN, or huge phase increments from upstream pitch-mod overflow). | 2026-04-27 — O(1) modulo fallback for out-of-range; recover `0.0` for `Inf`/`NaN`. Common-case fast path preserved. |

The filter, envelope, and effect runtimes were all verified correctly guarded
in the same review (cutoffs clamped before `tan()`, IIR state has
`flushDenormal`, feedback paths bounded by explicit clamps). No outstanding
gaps in the audio-rate path as of this commit.

## Why this matters — the lesson

The arithmetic safety contract sets a per-op invariant: "your output magnitude
is in `±SAFE_MAX`, never `NaN`/`Inf`." That's necessary but not sufficient.
**Downstream consumers also have to handle the bounded-but-huge case in O(1)
time.** This is the trap we walked into:

1. The arithmetic batch added `safeOut`/`safeDiv` to the new ops.
2. A code review found four pitch-mod factories (`vibrato`, `accelerate`,
   `pitchEnvelope`, FM) doing unguarded `2.0.pow(...)` and `/ freqHz`. We
   patched those — applying the same `safeOut`/`safeDiv` discipline.
3. We added a stress test: extreme `depth=10000` vibrato feeding into a sine
   oscillator — to verify the safety clamp prevents voice silencing.
4. The test **hung the build**. Cause: the per-op contract correctly clamped
   the pitch ratio at `SAFE_MAX = 1e15`. But that "safe" ratio fed into
   `wrapPhase`, which used `while (p >= period) p -= period` — O(N) where N
   is the overshoot count. With a phase increment of `~6e13`, the loop
   iterated `~10¹³` times **per sample**.
5. Fix: make `wrapPhase` O(1) for any input via a modulo fallback when the
   phase is way out of range, plus an `Inf`/`NaN` recovery path. Common case
   (overshoot by ≤1 period) keeps the fast subtraction path.

**Generalised principle for any new audio-rate code:**

> "Safe" means **finite** and within `±SAFE_MAX` — it does NOT mean **small**.
> Any code that consumes a value bounded by the safety contract must run in
> O(1) regardless of the value's magnitude. Subtraction loops, retry loops,
> fixed-point Newton iterations, and any state mutation that scales with
> input magnitude are landmines. Use modulo, branchless arithmetic, or
> single-step bounded iteration.

When adding a new oscillator, filter, or modulator: ask **"what does my
algorithm do if the upstream signal is `+SAFE_MAX`?"** If the answer is
"loops a billion times" or "overflows my own state", add a guard. The
`wrapPhase` fix is the canonical pattern.

## Why not also do hardware FTZ?

We could (and probably should, eventually) also enable FTZ/DAZ on JVM/JS where
available. But it's a *complement* to the explicit bounds, not a replacement —
hardware FTZ catches subnormals only, not Inf/NaN. The explicit bound is the
contract; FTZ would just remove the per-sample branch on the lower side.

JS Web Audio worklets don't expose MXCSR; we have to use explicit bounds.
JVM has `-XX:UseFTZForFloats` but it's not portable across vendors.

## Reading list

- **Julius O. Smith**, *Spectral Audio Signal Processing* and *Filters* — `ccrma.stanford.edu/~jos/`. Stability via
  coefficient choice; no specific epsilon thresholds prescribed.
- **JUCE source** — `JUCE_SNAP_TO_ZERO` macro, `juce_Decibels.h`, `juce_FilterDesign.cpp`.
- **SuperCollider source** — `SC_InlineUnaryOp.h::zapgremlins`.
- **Faust libraries** — `maths.lib` for epsilon constants and `FTZ()` helper.
- **Will Pirkle**, *Designing Audio Effect Plugins in C++* — recommends DC-offset noise injection (the KVR-canonical
  approach).
- **Intel Optimization Reference Manual** — FTZ/DAZ section on subnormal handling.
- **AES17** — measurement standard for digital audio (defines dB references but not safety bounds).

## History

- 2026-04-27 (initial): research surveyed across JUCE, SuperCollider, Faust,
  ChucK/STK, Pure Data, CSound, Web Audio. `SAFE_MIN = 1e-15f` /
  `SAFE_MAX = 1e15f` adopted (matching `zapgremlins`). Contract applied to the
  new arithmetic ops shipped in the IgnitorDsl arithmetic batch. Distort path
  switched to unconditional DC-block.
- 2026-04-27 (follow-up): code review identified four pre-existing pitch-mod
  factories with the same hazard class. Closed all four with
  `safeOut`/`safeDiv`. Stress test exposed `wrapPhase` as a downstream O(N)
  consumer of bounded-but-huge inputs; rewritten as O(1) modulo with
  `Inf`/`NaN` recovery. New runtime tests for arithmetic ops + pitch-mod
  factories landed (60 + 12 tests).

See `docs/agent-tasks-archive/2026-04/20260427-ignitor-dsl-arithmetic-batch.md`
and the original research scratch file at `tmp/audio-safety-bounds-research.md`.

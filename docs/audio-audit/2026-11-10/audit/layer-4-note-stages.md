# Layer 4: the per-note stages

**What I read** (paths under `audio_be/src/commonMain/kotlin/` unless said otherwise)

- In full: `audio_bridge/.../IgnitorDslClassic.kt`, `ignitor/IgnitorFilters.kt`, `filters/LowPassHighPassFilters.kt`,
  `filters/SvfCoeffSweep.kt`, `filters/WetDryMix.kt`, `EnvelopeCore.kt`, `AdsrCurveMath.kt`,
  `ignitor/IgnitorEnvelopes.kt`, `CrushCore.kt`, `DistortionCore.kt`, `DistortionShape.kt`, `ShapingFuncs.kt`,
  `ignitor/PitchModFactories.kt`, `Oversampler.kt`, `ignitor/OversampleRegion.kt`, `ignitor/IgnitorEffects.kt`,
  `ignitor/FilterHumanization.kt`, `utils/numerical_safety.kt`, `utils/fast_math.kt`, `utils/time_constant.kt`.
- In part: `ignitor/IgnitorDslRuntime.kt` (the pitch arms 455 to 600, `filterEnvDef` 943 to 1020, `tremoloGain` 1085 to
  1160, the stage arms 1740 to 2098), `ignitor/Ignitor.kt` (`TimesIgnitor`, `AffineIgnitor`, 150 to 330),
  `ignitor/EqIgnitor.kt` and `effects/PhaserCore.kt` (code only, comments stripped).
- Docs: `audio/CLAUDE.md`, `audio-constraints.md`, `audio/ref/numerical-safety.md` (all), `audio/ref/off-values.md`
  (all rows), the leads in KLANG-00420, KLANG-00156 (the E3 row and the P0/P2 text), the legends and my layer's rows
  of `stage-probe.txt`, `stage-cost.txt`, `aliasing.txt`.

**What I did not**

- `audio/MEMORY.md`, `audio/ref/voice-synthesis.md` and `audio/ref/performance.md` only by keyword (declick, denormal,
  Nyquist, saturate, softCap, E3, the elvis rule), not front to back.
- `filters/EqCore.kt`, `ResonatorBank.kt`, `ResonatorTable.kt`, `ResonatorConfig.kt` (the body and vowel are bus
  stages; `IgnitorEffects.kt` holds neither). `utils/phase_wrap.kt` and the small helpers (`fade_to_zero`,
  `linear_crossfade`, `ramp_step`, `retain_in_order`, `same_knob`, `ease_in_out_cubic`, `buffer_copy`).
- The specs: I read their case titles (the list is under Test truth), not their bodies, except where noted.
- `framing.txt` and `clicks.txt` row by row: I worked from the leads' summaries of them.
- Nothing was run. Every number below that is not quoted from a probe table is arithmetic done by hand from the
  code's constants, and is marked "by hand".

## The probes' leads in this layer

| Lead | Verdict | Cause |
|---|---|---|
| Stage probe 2: the analog SVF latches at a huge DC after one sample of 1e30 | CONFIRMED | `IgnitorFilters.kt:243-250` and `:272-279`. The damping grows with the fourth power of the state and the trapezoidal integrator's pole goes to z = -1 under infinite damping, so the state flips sign every sample and its decay rate falls as the damping rises. Finding L4-01. Not only at 1e30: by hand, a state of 3000 holds about 4 s. |
| Stage probe 5: lowpass and highpass at 23999 Hz ring for seconds | CONFIRMED, the math of the clamp | `LowPassHighPassFilters.kt:144` clamps at `0.5 * sampleRate - 1`. There `g = tan(pi fc / fs)` is 15279 and the pole radius is about `1 - k / g`, a time constant of `Q * g` samples: 0.225 s at q 0.707, 64 s at q 200 (by hand; the probe's 0.55 to 0.65 s and "over 10 s" agree). The pre-warping itself is right. Finding L4-10. |
| Stage probe 6: an infinity leaves the tremolo as one sample of 1e15, a NaN as 0 | BY DESIGN, as documented | The tremolo is `signal * gain` (`IgnitorDslRuntime.kt:2066`), a `TimesIgnitor`, whose law is `safeOut(a * b)` (`_arithmetic_laws.kt:51`, `numerical_safety.kt:154`). `numerical-safety.md` promises exactly this for the output-clamp ops: magnitude clamped to `SAFE_MAX`, NaN scrubbed to 0. What the page does not have is a rule for a bad SAMPLE in the stages that are not arithmetic; they give five different answers. Finding L4-08. |
| Stage probe 8: shimmer lets a NaN out a second time 0.183 s later | CONFIRMED | `IgnitorEffects.kt:689-691`: the ring write is bounded by two compares that a NaN fails, so the NaN is stored and a later grain reads it. Finding L4-06. |
| Stage probe 9: `coarse` holds an infinity for its whole hold and passes no NaN | CONFIRMED | `IgnitorEffects.kt:374`: `lastValue = work[i].nanGuard()`. `nanGuard` catches NaN only (`numerical_safety.kt:28`), so a NaN is held as 0 and an infinity is held as itself for `factor` samples. Finding L4-07. |
| Stage probe 10: q 200 gives +34 dB on a sweep, a 20 Hz tail past 10 s | BY DESIGN | The linear SVF at Q 200: time constant `Q / (pi f)`, 3.2 s at 20 Hz. Raw Motor; the clamp `[0.1, 200]` is `clampSvfQ`. |
| Stage probe 11: shimmer and the eq band at 5 Hz q 200 keep DC | BY DESIGN | Shimmer: the loop gain at DC is the feedback (0.95) times the mean of the overlapping Hann windows (0.9 by hand: 12 grains per second of 0.15 s), about 0.855 per pass of 0.15 to 0.3 s. The eq band: a bell at 5 Hz with `q * A` near 800, time constant about 50 s. |
| Stage probe 13: 16 passes start near 1e-38 | BY DESIGN (the math) | The first-sample gain of a lowpass stage is `a3 = g^2 a1`, 0.0039 at 1 kHz; to the 16th power 3e-39. Note: every stage's state is flushed below 1e-15 (`flushState`), so the first samples of the late stages are computed from flushed states. The loss is under -300 dB. |
| Framing 1: the filter envelope depends on the block borders; the ledger's "latent" is out of date | CONFIRMED on both counts | `IgnitorFilters.kt:354-362`. Finding L4-02. |
| Framing 3: accelerate differs by rounding only (1e-12 against the ledger's 5.3e-15) | CONFIRMED, by design | `PitchModFactories.kt:189-195`: each block seeds its first ratio with a `pow` and then multiplies by `step` per sample, so the product is reassociated wherever a block begins. The node's KDoc bounds it at 1e-11; the ledger's E7 number is the stale one. |
| Clicks 5: `adsr.on=0` does not fade the start | BY DESIGN here, NOT MINE beyond that | An off envelope is not built (`IgnitorDslRuntime.kt:1886-1889`); nothing in this layer shapes an onset then. The end is the voice's teardown fade. |
| Clicks 6 and 8: a kink at the last frame, the 3.3e-3 the KDoc names | NOT MINE to settle, one note | By hand the de-click lags a falling ramp by slope times 48 frames: about 3.1e-3 on the last frame of a 50 ms exponential release from 1.0. `AdsrCurveMath.kt:95-101` says the same. Whether the voice renders that frame is the voice layer's question. Listed under Suspicions. |
| Aliasing 4: shapers alias at 1x | BY DESIGN | No oversampling at the base rate; accepted in `audio-constraints.md`. |
| Aliasing 5: smooth shapes bottom out at -120 to -130 dB, 16x no better than 8x | CONFIRMED, the converters' own floor | The rows that sit there are the high pitches: the strongest alias of the 7044.4 Hz row is 12778 Hz, which is 48000 - 5 * 7044.4, and of the 4186 Hz row 18698 Hz, 48000 - 7 * 4186. A harmonic between 24 and 48 kHz is removed only by the LAST step down, the base-rate half-band (`Oversampler.kt:385-388`, 8 coefficients, about 100 dB). That step is the same at every factor, so more stages cannot lower it. |
| Aliasing 5: rectify at amount 2 never gets clean | BY DESIGN | Drive 251 (`10^2.4`) turns `abs(x)` clipped at 1 into a constant with a notch at each zero crossing about 0.13 percent of a cycle wide (by hand). At 1244.5 Hz that is 0.05 samples at 1x and 0.8 samples at 16x: narrower than a sample at every factor on offer. `rectify()`'s hard clip is an accepted item. |
| Aliasing 6: the doors' `Shape(Drive)` aliases 45 to 100 dB more than the fused node | CONFIRMED, two laws by decision D2, the cause is the soft cap | `IgnitorEffects.kt:190-192`. Finding L4-05. |
| Cost 1: `exp` and `stompbox` cost 3.4 times more at amount 3 | CONFIRMED | `fast_math.kt:134-136`: `fastExp2` is `2.0.pow(x)` outside (-32, 32). Finding L4-04. |
| Cost 2: vibrato adds 8.5 ns | CONFIRMED | Four buffer passes for one LFO. Performance table. |
| Cost 3: the adsr costs 4.1 to 4.5 ns on a held constant | CONFIRMED | The cost row is `classic()`'s envelope, whose de-click is always on: `EnvelopeCore.kt:193-201`, a one-pole per sample through an object field, a loop-carried chain of subtract, multiply, add. Performance table, and finding L4-03 for what the same smoother does at sustain 0. |
| Cost 4: `analog` costs 4.3 times the filter | CONFIRMED | `IgnitorFilters.kt:243-246`: the state feeds a four-step Horner polynomial, two more multiply-adds and a DIVISION, and the result feeds the state again. The linear loop's carried chain is one multiply-add and one subtract. Performance table. |

Also checked in this layer and found sound: the half-band's branch order against the HIIR reference (even
coefficients to the even phase up, the later sample through the even chain down); the Butterworth ladder of `passes`
(`q_k = 1 / (2 cos((2k+1) pi / 4n))`, product of the relative factors `2^((n-1)/2)`, so the cascade is -3 dB at fc at
the default q); the envelope law at zero-length stages, at a gate inside the attack, at a release under two frames
and at an infinite release; `fastTanh`'s zero slope at 3; the stability of the linearly interpolated coefficient
triple of the swept SVF (by Cauchy-Schwarz `a2^2 <= a1 a3` for any mix of valid triples, so the determinant of the
state matrix stays below 1; I did not prove the lower bound in general, only at the extreme pair 1 kHz and 23999 Hz).

## Findings

### L4-01 The saturating SVF stops decaying once its state is large: more damping means a slower decay

- **Severity**: major
- **Tag**: bug, safety
- **Where**: `ignitor/IgnitorFilters.kt:243-250` (lowpass), `:272-279` (highpass); `filters/LowPassHighPassFilters.kt:203-205`
  (`diodePairResistanceApprox`), `:213` (`SAT_STATE_SCALE`); the claim it breaks is in the same files:
  `IgnitorFilters.kt:234-236` ("the resonance compresses and the filter stays bounded") and
  `LowPassHighPassFilters.kt:191-193` ("increasing signal, more damping, bounded resonance").
- **What**: per sample the loop computes `kEff = k + 2 * driveScale * (P(0.0876 * ic1eq) - 1)` with P a quartic whose
  leading term is `0.0103592 x^4`, then `vHp = (v0 - (kEff + g) ic1eq - ic2eq) / (1 + g (kEff + g))`,
  `vBp = g vHp + ic1eq`, `ic1eq = 2 vBp - ic1eq`. With `v0 = 0` this is
  `vBp = (ic1eq - g ic2eq) / (1 + g (kEff + g))`. When `g * kEff` is much larger than 1, `vBp` is about 0, so
  `ic1eq` becomes `-ic1eq`: the state keeps its size and flips its sign. A trapezoidal integrator with infinite
  damping has its pole at z = -1, not at 0. The size falls only by the factor `1 - 2 / (1 + g kEff)` per sample, and
  `kEff` grows with the fourth power of the state, so the larger the state, the slower it falls. While it lasts,
  `vBp` is about 0, so `ic2eq` does not move (`vLp = g vBp + ic2eq`) and the input is divided by `g kEff`: the
  lowpass outputs the DC it held at that moment, the highpass outputs `-ic1eq / g` alternating at Nyquist, and the
  signal does not pass.
  The probe's numbers follow exactly. Input 1e30 for one sample at 1 kHz (g = 0.06554, a1 = 0.9116): `vBp` = 5.97e28,
  `ic1eq` = 1.19e29, `ic2eq` = 2 g vBp = 7.83e27 (the lowpass row's stuck value), and the highpass outputs
  1.19e29 / 0.06554 = 1.82e30 alternating (its row: peak 1.82e30, mean 5.86e10, never finite-small again).
  It is not a 1e30 effect. By hand, in the regime where the quartic dominates (state above about 100 at analog 1):
  `d(s^4)/dn = -4 / (g * driveScale * 0.0103592 * 0.0876^4) = -6.56e6 / (g * driveScale)` per sample. At 1 kHz and
  analog 1 that is 4.0e8 per sample, so a state of 1000 releases after about 2500 samples (52 ms), 3000 after about
  2.0e5 samples (4.2 s), 10000 after about 2.5e7 samples (8.7 minutes). A higher cutoff or a higher `analog` makes it
  slower (analog 15 at 12 kHz: a state of 1000 holds about 12 s).
- **Reach**: any lowpass or highpass with `analog > 0` (every `classic()` filter reads the voice's `analog` slot) fed
  a signal some hundred times full scale. Inside `classic()` nothing ahead of the filters makes that from a normal
  source (the fused distort's shapes are bounded, bandpass and notch are unity). It takes gain ahead of the filter:
  an authored tree with the bare `drive` door (amount 2.5 is a gain of 1000) or a `mul`, or one non-finite-turned-huge
  sample from upstream (a `Times` turns an infinity into 1e15, which this filter then holds for the note's life). The
  latch dies with the voice, but until then it feeds a DC or a Nyquist square at the state's level into the cylinder.
- **Settle it**: a spec: `lowpass(cutoffHz = 1000.0, q = 0.707, analog = 1.0)` fed 10 ms of a DC step of 1e4, then
  silence; assert the output is under 1e-3 after 1 s. Expected by this analysis: red, the output still near its held
  DC. The same row with `analog = 0.0` is green. The probe case exists already for 1e30; add 1e3 and 1e4.
- **Confidence**: high for the mechanism (the probe's three numbers come out of it to three digits), medium for the
  hold times (asymptotic, by hand).

### L4-02 The filter envelope is read at a block's two ends, and every sprudel filter envelope reaches it

- **Severity**: major
- **Tag**: bug, sound
- **Where**: `ignitor/IgnitorFilters.kt:354-362` (`prepareEnvelopeSweep`: `filterEnvCutoff` at `pos` and at
  `pos + length`), `filters/SvfCoeffSweep.kt:65-91` (the linear coefficient steps between the two),
  `IgnitorFilters.kt:252`, `:263` and siblings (the per-sample adds). The reach: `IgnitorDslClassic.kt:448-509`
  (`classic()` hands `hpf.env`, `bpf.env`, `notch.env`, `lpf.env`), `IgnitorDslRuntime.kt:943-979` (`filterEnvDef`).
  The stale statement: `docs/tasks/KLANG-00156-block-framing-invariance.md:268` ("latent: no production door passes
  an env here").
- **What**: with an envelope, each block computes the cutoff at its first frame and at the frame after its last,
  computes two coefficient sets and steps `a1, a2, a3` (and `g` on the saturated path) linearly between them. The
  envelope between the two frames is never evaluated. So (a) a breakpoint inside a block (the attack's end, the
  decay's end, the gate) is replaced by a straight chord, (b) a stage shorter than a block can vanish (attack 1 ms =
  48 frames: start level 0, end level already in the decay, the peak is never produced), and (c) which frames are
  "ends" depends on where the engine's block grid falls relative to the note's onset. In the gate's block the chord
  starts at the block's first frame, so the cutoff starts to fall BEFORE the gate frame and, for a release shorter
  than the block, arrives late. That is the framing probe's row: up to -15.6 dB re the peak at the default times,
  0.18 off and 6 to 14 frames late after the gate.
  The ledger's "latent" was true when it was written. It stopped being true when `classic()` slotted the four filter
  envelopes (phase 3 step 5, 2026-09-25): sprudel's `lpf(env = ...)`, and any of `attack`, `decay`, `sustain`,
  `release` alone through the slot-layer fill, build this path. Built-in songs use it:
  `src/commonMain/kotlin/builtinsongs/IrishLamentTechno.kt:49` (attack 0.005 s, 240 frames, under two blocks), `:55`,
  `:62`, `:74`, `:102`, `:138`, `StrangerThings.kt:42`.
  A smaller inconsistency on the same path: the linear loops interpolate `a1, a2, a3`, the saturated loops ignore
  them and interpolate `g` and `k` (`IgnitorFilters.kt:244-248`), so between the two ends an `analog > 0` filter
  follows a different cutoff curve than the same filter at `analog = 0`. Negligible for small `g`; at a sweep into
  the Nyquist clamp `g` jumps to thousands after one sample.
- **What a correct evaluation needs, and what it costs**:
  1. Per sample, exactly: the envelope (`EnvelopeCore.at`, with one `fastExp` on the default curve), `2^x`, a `tan`
     and a divide per sample. By hand about +15 ns per sample with a polynomial `tan` and `fastExp2`, +40 ns or more
     with the library `tan`, against 3.9 ns for the whole filter today. Bit-invariant to the framing.
  2. Splitting the chord at the breakpoints inside the block (the ledger's proposed fix shape): at most four extra
     coefficient sets per note, nothing per sample. It removes (a) and (b) but NOT (c): between breakpoints the chord
     still approximates a curved segment from framing-dependent frames. By hand, on the default exponential attack
     of 480 frames the chord over one block is off by up to 0.08 of the level, 0.6 semitones at depth 7, and where it
     is off depends on the offset.
  3. A grid anchored to the NOTE: knots at note frames 0, N, 2N, ... plus the breakpoints (the attack's end, the
     decay's end, the gate frame, the release's end), coefficients stepped linearly between knots, the running
     coefficients and steps kept in fields across block borders. The knots are then a function of the note alone, the
     adds happen in the same order at every framing, and the output is bit-identical at every offset. Cost at N = 128:
     one coefficient set per 128 samples plus four per note, which is LESS than today (today every block computes
     two; its start set repeats the previous block's end set). At N = 32 it is about twice today's setup cost, by hand
     about 2 ns per sample while the envelope moves and nothing in the sustain (the equal-cutoff shortcut of
     `SvfCoeffSweep.prepare` stays). The loops gain one split per knot inside a block.
     Limits of 3: a held realtime voice learns its gate at a block border, so its gate knot is where the engine put
     it; the analog drift still steps on the absolute block grid (by design, the framing probe's item 2); a cutoff
     that is itself a signal is still read once per block (a separate, documented class).
- **Reach**: every `classic()` voice whose pattern writes a filter envelope knob; any authored `lowpass(env = ...)`.
- **Settle it**: the pinned tripwire `BlockFramingInvarianceSpec.kt:428` ("E3 KNOWN DEFECT") already reproduces it;
  flipping it to the correct assertion written above the pin is the test. `EnvelopeLawSpec.kt:328` pins the
  two-end sampling as the host's law and would have to move with a fix.
- **Confidence**: high.

### L4-03 The de-click smoother has no flush: an envelope at sustain 0 turns the voice's gain into a subnormal

- **Severity**: major
- **Tag**: performance, safety
- **Where**: `EnvelopeCore.kt:193-201` (`EnvelopeDeclick.next`, `smoothed += coeff * (level - smoothed)`, no
  `flushState`), `ignitor/IgnitorEnvelopes.kt:143-150` (the gain multiplies every sample),
  `IgnitorDslClassic.kt:526` (`classic()` always switches the de-click on, 1 ms).
- **What**: take `adsr(attack = 0.01, decay = 0.1, sustain = 0, release = 0.05)` under `classic()` and a gate of 2 s.
  From frame 5280 on `EnvelopeCore.adsAt` returns exactly `sustain` = 0.0 (`EnvelopeCore.kt:153`). The smoother then
  computes `smoothed = smoothed * (1 - c)` with `c = 1 - exp(-1/48) = 0.02062` and never reaches 0: it falls by a
  factor e per millisecond, passes 2.2e-308 (the smallest normal double) after `708.4 / 0.02083 = 34000` samples
  (0.71 s after the decay's end, by hand), and from about 35500 samples on it stops moving at a subnormal near 1e-322,
  because `c * smoothed` rounds to 0. For the rest of the note every sample is `input[i] * subnormal`: a subnormal
  operand and a subnormal or zero result, the slow path of the FPU that `flushState` exists to avoid
  (`numerical_safety.kt:64-68` names 10 to 100 times). The output buffer then carries subnormals into the cylinder
  mix, and if the voice is alone on its cylinder, into every multiply of the bus chain.
  Every other IIR state of this layer is flushed; this one-pole is the exception. The stage probe could not see it:
  its adsr rows are sustain 1, and its input is silent after the gate.
- **Reach**: ordinary. Any plucked envelope (`sustain` 0, or negative, which floors to 0) on a note longer than
  attack + decay + 0.71 s: `note("c2").sound("saw").adsr("0.01:0.1:0:0.1")` at a slow tempo, a long `legato`.
- **Settle it**: one stage-probe case, `adsr` with `adsr.sustain = 0` on the 1 s step: the `subnorm` column should
  read a count above 0 with the first at about 0.82 s. One stage-cost row for the same voice held 3 s gives the
  price on this machine. Mutant for a later guard spec: remove a `flushState()` added to `next`.
- **Confidence**: high for the arithmetic (it is the textbook case), medium for the size of the penalty (it depends on
  the CPU; not measured here).

### L4-04 `exp` and `stompbox` call `pow` per sample once the drive pushes the argument past 22

- **Severity**: minor
- **Tag**: performance
- **Where**: `utils/fast_math.kt:134-136` (the fallback), `:169` (`fastExp`), `ShapingFuncs.kt:162-168` (`expClip`),
  `:306-312` (`stompBox`).
- **What**: `expClip(x) = 1 - fastExp(-x)` and `fastExp(y) = fastExp2(y * 1.4427)`; `fastExp2` leaves its fast path
  outside (-32, 32), that is for `|x| > 22.18` (`stompBox`: above 14.8 on the positive side, 7.4 on the negative).
  At amount 3 the drive is `10^3.6 = 3981`, so a 0.5 sine is past 22 for all but about 0.7 percent of its cycle and
  nearly every sample takes `2.0.pow(x)`. Measured: Ignitor 36.64 ns against 10.92 at amount 0.5, Katalyst 72.89
  against 21.1. Every other shape gets cheaper at high drive because its clamp branch wins.
  Past `x = 38` the result is exactly 1.0 in a double (`e^-38` is under half an ulp of 1), so an early return there
  is bit-identical; between 22.18 and 38 a wider table would differ from `pow` by at most 2e-20 absolute before the
  subtraction from 1, which rounds away in all but rare ties.
- **Reach**: `distort(amount, "exp")` or `"stompbox"` at an amount above about 1.5, on a voice or on a bus.
- **Settle it**: the two cost rows again after an early `if (x > 38.0) return 1.0`; `DoorDistortionLawSpec`'s
  value-by-value row for the two shapes must stay green with exact equality at large `x`.
- **Confidence**: high.

### L4-05 The doors' soft cap is the alias source: a near-hard knee from 0.95 to 1.10 after every `Shape`

- **Severity**: minor
- **Tag**: sound, consistency
- **Where**: `ignitor/IgnitorEffects.kt:190-192` (`ShapeIgnitor` applies `softCap` after the core),
  `ShapingFuncs.kt:333-339` (`softCap`, threshold 0.95), against `:246-248` (the fused node copies the core's output).
- **What**: the two nodes are two laws by decision D2 and `audio-constraints.md` lists the cap, so the difference is
  not a defect. Its size is new. `softCap` is the identity up to 0.95 and then
  `0.95 + 0.05 * fastTanh((|x| - 0.95) / 0.05)`, which is flat from `|x| = 1.10` on (`fastTanh` clamps at 3): the
  whole bend happens inside 0.15 of input, with a jump in the third derivative at 0.95 and in the second at 1.10. A
  0.5 sine at amount 0.5 (drive 3.98) leaves `tanh` at 0.963 and `sin` at 1.0, both above 0.95, so the cap bends
  every peak. The aliasing rows: soft -92.5 against the fused node's -138.4, fold -67.9 against -167.3, at 1244.5 Hz;
  `hard` is the same on both (-44.5, -44.3) because its own corner dominates. So a door `distort` of a smooth shape
  at moderate drive carries the alias level of a much harder shape, from the stage meant only to catch the DC
  blocker's overshoot.
- **Reach**: every Ignitor `distort()` and `shape()` door call whose shaped signal passes 0.95 (three built-in songs
  render through it, per the note at `IgnitorEffects.kt:71-72`). Not `classic()`'s distort, not the bus stage.
- **Settle it**: one aliasing row: the door chain with the cap's threshold at 0.5 (a wider knee), or with the cap
  removed, at soft 0.5 and 1244.5 Hz; the row should fall toward -138.
- **Confidence**: high for the cause (the three shapes behave as the knee predicts), medium that a wider knee is the
  right cure (a sound decision).

### L4-06 Shimmer stores a NaN in its grain ring and plays it again

- **Severity**: minor
- **Tag**: safety
- **Where**: `ignitor/IgnitorEffects.kt:689-691`.
- **What**: `var write = dry + feedbackTap * fbVal; if (write > 2.0) write = 2.0 else if (write < -2.0) write = -2.0;
  ring[writePos] = write`. A NaN fails both compares and is stored. An infinity is clamped to 2. Each grain that
  later reads that cell (lookback 7200 to 14400 frames at the default pitches) makes `wetSample` NaN for the samples
  it interpolates across it, so the NaN leaves the node again up to 0.3 s later, possibly more than once. The feedback
  path is safe (`lpfState` goes through `flushState`, line 755), so it does not recirculate. `nanGuard`'s own KDoc
  (`numerical_safety.kt:16-18`) says it exists for exactly this write.
- **Reach**: an Ignitor `shimmer` behind anything that can emit one NaN (an unguarded `drive`, a `pitchMod` signal, a
  Karplus string with `decay > 1`).
- **Settle it**: the stage probe's shimmer NaN row already shows it (finite again only after 0.183 s). A spec: one
  NaN into `shimmer(wet = 1.0)`, assert no NaN after the poison sample. Mutant after a fix: drop the guard.
- **Confidence**: high.

### L4-07 `coarse` turns one infinite sample into `factor` infinite samples

- **Severity**: minor
- **Tag**: safety, consistency
- **Where**: `ignitor/IgnitorEffects.kt:374`.
- **What**: `lastValue = work[i].nanGuard()` then `buffer[i] = lastValue` for the hold. A NaN is held as 0, an
  infinity as itself: the probe's `factor=4800` row has 4800 non-finite output samples from one. The comment above
  the line gives the reason for the guard ("a NaN input must not latch into the held value for `factor` frames"); the
  same reason holds for an infinity. `finiteOrZero` is the helper whose KDoc names this case ("a sample that is about
  to be ... stored").
- **Reach**: `coarse(n)` behind a source of an infinity. Rare; the multiplier is what matters (a hold of 0.1 s of
  infinity into the cylinder mix).
- **Settle it**: the probe row exists. A spec row in `CoarseLawSpec`: one `+Infinity`, assert at most one non-finite
  output sample.
- **Confidence**: high.

### L4-08 Five answers to one bad sample: the layer has no rule for a non-finite SAMPLE outside the arithmetic nodes

- **Severity**: minor
- **Tag**: consistency, doc
- **Where** (what one NaN and one +Infinity sample become, each read from the code):
  - `Times` and so the tremolo, `Affine` (`_arithmetic_laws.kt:51`, `Ignitor.kt:287`): NaN to 0, infinity to 1e15.
  - `DriveIgnitor` (`IgnitorEffects.kt:121-123`): both pass raw.
  - `AdsrIgnitor` (`IgnitorEnvelopes.kt:150`): both pass raw; an infinity at a gain of exactly 0 becomes NaN.
  - The SVF (`IgnitorFilters.kt:251`, `:262` and siblings): the output is written before the state is flushed, so
    both leave as one non-finite sample, then clean (probe: `nonfin 1`).
  - `OnePoleLowpassIgnitor` (`IgnitorFilters.kt:579-581`): the output is written AFTER the flush, so both leave as 0.
  - `DcBlocker` (`LowPassHighPassFilters.kt:468-471`): output before the flush, two non-finite samples.
  - `CrushCore` (`CrushCore.kt:59-60`): NaN to 0, infinity to 1.0 (the clamp).
  - `coarse`: NaN to 0 held, infinity held (L4-07).
  - `DistortionCore` (`DistortionCore.kt:205`): NaN to 0; infinity to 1 for the clipping shapes and to 0 for
    `gentle`, `softsat`, `fold`, `linearfold`, `sineshaper` (their `Inf / Inf` or `sin(Inf)` is a NaN, then guarded).
  - Shimmer: infinity to 2 in the ring, NaN stored (L4-06); the phaser's wet path `finiteOrZero`, its dry path raw.
  - `Oversampler` (`Oversampler.kt:228`, `:262-263`): both to 0 at the input of every stage.
  - `SemitonesToRatioIgnitor` (`PitchModFactories.kt:75`): NaN and -Infinity to ratio 0, +Infinity to 1e15;
    `DeviationToRatioIgnitor` (`:52`): raw.
- **What**: `audio/ref/numerical-safety.md` states two contracts. For the arithmetic ops: output within
  `SAFE_MAX`, NaN scrubbed (so "infinity leaves as 1e15, NaN as 0" is the promised behaviour of `safeOut`, lead 6).
  For knobs: a non-finite value reads as unset. For a bad sample arriving at a stage that is not arithmetic it has no
  rule, only `nanGuard`'s KDoc ("an Inf passing through a sample path is the raw engine behaving as designed"). The
  result is the list above: the same infinity is 0, 1, 2, 1e15, itself, or a NaN, depending on the stage it meets, and
  two neighbours of one family differ (the one-pole swallows what the SVF passes). The page's closing statement
  (`numerical-safety.md:147-150`, "No outstanding gaps in the audio-rate path") dates from 2026-04-27 and names a
  function, `flushDenormal`, that no longer exists (it is `flushState`; `audio-constraints.md:14` uses the old name
  too).
- **Reach**: internal; it decides how far one bad sample travels and at what level.
- **Settle it**: a decision, then one table in the ref page and one probe assertion per stage (the stage probe's
  `nonfin` and `peak` columns on the `nan` and `+inf` rows are that table already).
- **Confidence**: high (each line read; five of them match the probe's rows).

### L4-09 Shimmer allocates its 96000-sample ring at every voice build, on the audio thread

- **Severity**: major
- **Tag**: realtime
- **Where**: `ignitor/IgnitorEffects.kt:601-602` (`private val ring = AudioBuffer(ringSize)`), `:584`
  (`SHIMMER_RING_FRAMES = 96_000`), `IgnitorDslRuntime.kt:2084-2087` (`ringFrames = SHIMMER_RING_FRAMES shl
  cache.regionStages`); the build runs in `VoiceScheduler.process` (`voices/VoiceScheduler.kt:423-427`, `:639`).
- **What**: per note. One `DoubleArray(96000)` is 768 KB, allocated and zeroed when the voice is built, which
  happens inside the audio thread's block. Inside a 16x region it is 12.3 MB per note. The hot-path lint does not
  see it (it reads `generate`, this is a field initializer), and the cost table's `build_us` for shimmer (34.8 to
  39.8 us against about 15 for a filter) is this allocation. A chord of eight shimmer voices is 6 MB of fresh
  garbage in one block on the worklet thread. The file marks the node "WIP" (`:586-588`).
- **Reach**: any instrument with `.shimmer(...)`. No built-in sound uses it.
- **Settle it**: the cost table's `build_us` and a per-note allocation reading (`audio/ref/performance.md`,
  "Measuring allocations") for a shimmer voice. The rule question the lint raised (is once per note allowed) decides
  the class; this is its largest member by three orders of magnitude.
- **Confidence**: high.

### L4-10 The cutoff clamp sits 1 Hz under Nyquist, where the filter is a resonator at Nyquist

- **Severity**: note
- **Tag**: sound
- **Where**: `filters/LowPassHighPassFilters.kt:144`.
- **What**: at `fc = fs/2 - 1` the bilinear map puts both poles at radius about `1 - k / g` next to z = -1 with
  `g = 15279`. The response in the audible band is flat, as asked, but the state rings at 23999 Hz with a time
  constant of `Q * g` samples (0.225 s at Butterworth, 64 s at q 200). The ring is small (the probe's impulse row
  implies about -75 dB at its start) and inaudible. It is reached in ordinary use by any envelope sweep that
  overshoots Nyquist (`lpf(freq = 8000, env = 24)` asks for 32 kHz and parks at the clamp). A clamp at `0.49 * fs`
  gives `g = 31.8`: 22 samples at Butterworth, 0.13 s at q 200, and changes a lowpass by under 0.001 dB at 20 kHz
  (by hand). This is an existing clamp's value, so a decision, not a missing guard.
- **Reach**: `lpf`, `hpf` with a cutoff or a swept cutoff at or above Nyquist.
- **Settle it**: the probe's `freq=23999` rows with the clamp at `0.49 * fs`.
- **Confidence**: high for the formula, medium for "inaudible" (not listened to).

### L4-11 The bare `drive` node emits infinity and NaN from finite input

- **Severity**: minor
- **Tag**: safety
- **Where**: `ignitor/IgnitorEffects.kt:119-123`, `DistortionCore.kt:240`.
- **What**: `driveGain = 10^(amount * 1.2)` is infinite from `amount` 257 on; `work[i] * Inf` is an infinity, and
  NaN for a sample of exactly 0. Nothing follows inside the node. Behind a `Shape` the core's guard catches both (NaN
  to 0, infinity to the rail), and the fused node does the same, so only the bare door is open. It is the multiply by
  a constant that `numerical_safety.kt:103-105` lists among the output-clamp ops, without the clamp. The off-value
  table's own rule: "a NEW node on the audio spine that can emit a non-finite sample from finite input owes a
  substitution at its own read"; this node is old, so the rule does not bind it, but it is the case the rule names.
- **Reach**: an authored `x.drive(300)` without a shaper. Unlikely by hand, possible from a modulated amount.
- **Settle it**: a spec row: `drive(ParamIgnitor("amount", 300.0))` over a sine that crosses 0, assert finite.
- **Confidence**: high.

### L4-12 Comments that say what the code does not do

- **Severity**: minor
- **Tag**: doc
- **Where and what**:
  - `docs/tasks/KLANG-00156-block-framing-invariance.md:268`: E3 "latent: no production door passes an env here".
    False since 2026-09-25 (L4-02). Its file reference `IgnitorFilters:140-171` is stale too.
  - `ignitor/IgnitorFilters.kt:558-559` and `:608`: both one-pole nodes "snapshot state into a local for the hot
    loop". The loops read and write the fields `y` and `xPrev` directly (`:579-581`, `:633-635`).
  - `filters/LowPassHighPassFilters.kt:187`: the diode polynomial's "output is at least about 1.0 for all real x". Its
    minimum is 0.9966 at `x = -0.135` (by hand), so `kEff` is BELOW `k` for a state between -3.1 and 0, by up to
    `0.0068 * driveScale`. At q 200 (`k` = 0.005) and `analog` 3 or more the instantaneous damping is negative there.
    Averaged over a cycle the even terms win, so I do not claim an instability; the sentence is still not true.
  - `ignitor/IgnitorEffects.kt:402` and `:505-506`: the Ignitor phaser is described as a plain all-pass cascade,
    "unit-magnitude and fully correlated". `PhaserIgnitor` never sets `PhaserCore.feedback`, which stays at its
    default 0.5 (`effects/PhaserCore.kt`, `var feedback: Double = 0.5`), so the wet path peaks at +6 dB and dips to
    -3.5 dB, and the node has a feedback no door can reach or switch off.
  - `filters/LowPassHighPassFilters.kt:109-110`: "kept the per-sample `when(mode)` in `Ignitor.svf`". The `when` is
    hoisted to one loop per tap (`IgnitorFilters.kt:238`), as the node's own KDoc says.
  - `audio/ref/numerical-safety.md:147-150` and `.claude/skills/review-loop/audio-constraints.md:14`: `flushDenormal`
    is a name that no longer exists.
- **Reach**: readers and reviewers.
- **Settle it**: reading the cited lines.
- **Confidence**: high.

## Performance candidates

| Where | What | Rate | Measured | Expected gain (rough) | Risk to the sound |
|---|---|---|---|---|---|
| `EnvelopeCore.kt:193-201`, `IgnitorEnvelopes.kt:141-152` | The de-click one-pole runs per sample through an object field while the level is constant. A block that lies wholly in the sustain and whose one step leaves `smoothed` unchanged (`smoothed + coeff * (level - smoothed) == smoothed`, true about 37 ms after the sustain is reached) can multiply by the constant, or copy at exactly 1.0 | per sample, every `classic()` voice | 4.13 ns at 0/0/1/0, 4.38 default, 4.49 release 5 | about 3 ns per sample per held voice | none: the guard makes it bit-identical |
| `fast_math.kt:134-136`, `ShapingFuncs.kt:162-168`, `:306-312` | `pow` per sample for `exp` and `stompbox` at high drive (L4-04) | per sample | 36.64 against 10.92 (Ignitor), 72.89 against 21.1 (Katalyst) | back to about 8 to 11 ns | none with the exact early return at 38; a wider table is a rounding-level change |
| `PitchModFactories.kt:112-120`, `:69-77` | Vibrato is four nodes and four buffer passes: the sine, `max`, `Times` with `safeOut`, then a divide by 12, `fastExp2` and `safeOut` per sample, for a signal that moves at 5 Hz | per sample | 8.47 ns on a saw voice (9.76 against 1.29) | one fused loop: about 3 to 4 ns | the op order must be kept for identical bits, and the sine's three seed draws must stay; evaluating the ratio per block instead would be a SOUND decision and would bring a framing dependence back |
| `IgnitorFilters.kt:243-250`, `:272-279` | The saturated loop's carried chain: `ic1eq * SAT_STATE_SCALE`, a four-step Horner polynomial, two multiply-adds, a division, then back into `ic1eq`. Estrin form and the scale folded into the coefficients shorten the chain by two multiplies | per sample | 16.65 against 3.86 | 10 to 25 percent of the 16.6 | bits change at rounding level (a sound decision in name only, about -300 dB); the divide stays |
| `PitchModFactories.kt:486`, `:509` | FM divides by the block-constant `safeFreq` per sample | per sample | fm adds 4.64 ns to a saw voice | under 1 ns | one ulp per sample (the comment at `:484` keeps the order on purpose) |
| `IgnitorEffects.kt:98-126`, `:177-195` | The doors' `distort` is two nodes: `Drive` (scratch, a multiply pass), `Shape` (scratch, the core, a soft cap pass) | per sample | 13.28 (drive+shape soft 0.5) against 5.19 (fused soft 0.5) | about 5 ns per door distort | none if the fused form multiplies the same way (`x * gain`, then the shape) |
| `IgnitorFilters.kt:211` | The static latch tests `cutoffHz !is ParamIgnitor`, so a filter authored with a literal (`ConstantIgnitor`) and every stage of a `passes` cascade (its q is a `Times` or `Affine` of a leaf) computes `tan` and three divides every block. `EqIgnitor.Section.isVoiceConstant` already looks through those shapes | per block per filter | 16 passes: 62.95, about 1.2 ns over 16 single stages | 0.1 to 0.5 ns per sample per such filter | none |
| `IgnitorFilters.kt:359-362`, `SvfCoeffSweep.kt:70` | With an envelope every block computes the start set again although it equals the previous block's end set when the drift and q did not move | per block | not measured | one `tan`, one `pow`, three divides per block per env filter | none; subsumed by a note-anchored grid (L4-02) |
| `PitchModFactories.kt:333-341` and the sources | The pitch envelope writes one constant ratio for a settled block, but the reader still takes its per-sample modulated path | per sample | penv adds 4.29 ns to a saw voice, most of whose render is settled | up to the 4 ns if the mod could say "constant this block" | the sources' auditor owns the reader; bits depend on the multiply order |
| `ignitor/EqIgnitor.kt:120`, `:137` | `s.db?.let { ... } ?: 0.0` per block on a non-static section: the `x ?: field` shape `performance.md:411` says boxes on V8 | per block (JS) | not measured | one or two heap numbers per block per modulated section | none; a suspicion until measured |

## Suspicions

- **The analog filter at q 200 near Nyquist decays slower than the linear one.** Probe: `lowpass freq=23999 q=200`
  impulse, `dc_end` 6.91e-9; the same with `analog=1`, 1.54e-7, 22 times more after 10 s. The polynomial cannot do
  that at such a state (its odd term adds 0.0022 times the state to `k` = 0.005). The other difference is the drift:
  0.02 percent of 23999 Hz is 4.8 Hz, which next to the clamp moves `g` between about 15279 and 2600 from block to
  block with the state held. I did not trace which way that pumps. To settle: the same row with `humanize` off.
- **The de-click leaves about 3e-3 of gain on a voice's last frame** (by hand, 50 ms default release; larger for a
  short one: the lag is the release's final slope times 48 frames). `AdsrCurveMath.kt:95-101` says so and declines
  to fix it; the click detector found the sine clean and the supersine not. To settle: print the gain on the last
  rendered frame of a `classic()` sine at release 0.05 and 0.005.
- **`EqIgnitor` boxes per block on V8** for a modulated section (the row above). To settle: the allocation check of
  `performance.md` on a voice with an LFO on an eq band's `db`.
- **A swept filter between very different cutoffs is not a filter of an intermediate cutoff** in between (the
  interpolated triple breaks `a2 = g a1`, `a3 = g a2`). Stable by my argument above; whether the path it takes is the
  wanted sweep is covered by L4-02's fix or by ear.
- **The crush floor law on a decaying tail.** `floor(x * hl) / hl` maps every sample just below zero to `-1 / hl`, so
  a sample whose tail hovers around zero at -100 dB becomes a pulse train at one full step (-18 dB at 4 bits, full
  scale at 1 bit) for as long as the gate is open. The floor law is decision D1; I did not render it.

## Decisions worth revisiting

- **The soft cap's knee (0.95, flat from 1.10)** on the doors' `Shape`: it costs 45 to 100 dB of alias rejection on
  smooth shapes (L4-05). A wider knee, or the cap only where the DC blocker's overshoot can occur, keeps the bound.
- **The cutoff clamp at `fs/2 - 1`** (L4-10): `0.49 * fs` is inaudible in the passband and ends the Nyquist ring.
- **One rule for a bad sample** (L4-08): `finiteOrZero` wherever a sample is stored for more than one frame (the
  coarse hold, the shimmer ring), and a stated choice between "write before the flush" (the SVF, the DC blocker) and
  "write after" (the one-pole) for the filters.
- **The saturated SVF's damping law has no ceiling** (L4-01). The Motor rule forbids a clamp on a PARAMETER; this is
  the node's own internal curve. A damping that stops growing past some state (the polynomial evaluated on a bounded
  argument) keeps the character in the range it was tuned in and removes the latch. The two reverted tanh attempts
  capped the feedback signal, which is a different thing.
- **Two distort laws** (D2): the reason was bit identity with a strip that has retired. The doors now cost 2.6 times
  the fused node and alias more. Worth asking again whether three songs still need the old door law.

## Listening tasks

- The analog filter under a hot signal: a drum sample or a saw at 30 to 300 times full scale into
  `lpf(1000).analog(5)`. Listen for the filter "ducking" after loud hits and for how long it takes to open again; the
  release time grows with the fourth power of the level (L4-01).
- A filter envelope with an attack under 3 ms (`lpf(400, env = 24, attack = 0.001)`) on repeated notes at a tempo
  that does not divide the block: does the bite of the sweep change from note to note (L4-02).
- The doors' `distort(0.5, "soft")` against `classic()`'s `distort(0.5)` on a high sine (2 to 7 kHz): the cap's
  edge and its aliases (L4-05).
- `crush(2)` to `crush(4)` on a sample with a long quiet tail: the tail's buzz at one quantizer step.
- The Ignitor `phaser(wet = 1, floor = 0)`: a resonant phaser (feedback 0.5), not the flat cascade its text describes.
- The vibrato and the tremolo need no listening from this audit.

## Test truth

- **`SvfNodeLawSpec`**: pins the linear loops bit for bit against a kernel written in the test, the swept
  coefficient steps, the polynomial at literal values, and four behaviours of the saturated path under a normal hot
  signal (peak compressed, peak under the linear filter's at q 10, no DC, near-linear at low level). Nothing pins the
  saturated path's RECOVERY from a large state: a mutant that multiplies the polynomial's quartic term by 100 very
  likely stays green on the four behaviour rows and red only on the literal-value row.
- **`EnvelopeLawSpec`**: the law's edges are well covered (fractional attack, release from the level at the gate,
  floor(N), under two frames, non-positive, NaN and infinite times, raw sustain, every curve, a gate at or before
  the onset). Its filter host row (`:328`) pins the two-end sampling as correct, so it defends L4-02's defect.
- **`BlockFramingInvarianceSpec:428`**: pins E3 in its wrong shape on purpose, as a tripwire.
- **`EnvelopeDeclickSpec`, `AdsrIgnitorKnobsSpec`**: the de-click's slew bound and corner rounding. Nothing renders
  an envelope at sustain 0 for longer than 0.7 s; adding or removing a `flushState` in `EnvelopeDeclick.next`
  changes no test today.
- **`CoarseLawSpec`**: the hold law at integer and fractional factors. No non-finite row; replacing `nanGuard` by
  `finiteOrZero` or by nothing at `IgnitorEffects.kt:374` stays green (the NaN rule is pinned only for the crush, in
  `StripLawCoresSpec:160`).
- **`ShimmerSchedulerSpec`**: the scheduler, the bypass, huge and non-finite PITCHES. No non-finite SAMPLE; a guard
  on the ring write, present or absent, changes nothing.
- **`FastExp2Spec`**: the fast range, the integers, the boundary, and that the fallback IS `pow`. That last row pins
  the slow path of L4-04 as a contract; an early return in `expClip` does not touch it.
- **`DoorDistortionLawSpec`, `StripLawCoresSpec`, `ShapingFuncsBoundsSpec`**: both distort laws and every shape's
  curve and bound. No spec holds an aliasing number for either law; the aliasing meter is the only witness of L4-05.
- **`OversamplerBenchSpec`, `OversamplerSpec`, `OversamplerGroupDelaySpec`, `OversamplerDecimatorParitySpec`**: flat
  round trip, about 100 dB rejection, the transition band, no image, the declared delay, exact zeros after an
  impulse, parity with a plain reference. Good coverage; the floor the aliasing meter found is inside what they pin.
- **`PassesCascadeSpec`, `PassesLadderParitySpec`**: the ladder and its bit parity with the bridge. Not read beyond
  the titles.
- **Nothing pins**: the clamp's value at Nyquist as a behaviour (ring time), the `drive` node at an overflowing
  amount, the Ignitor phaser's fixed feedback.

## For the audit ruleset

- The probe tables were the best lever: three findings were settled by matching a hand derivation to a probe row to
  three digits (7.83e27, 1.82e30, the alias at 48000 - 5f). A lead with its raw row beside it is worth more than its
  summary sentence.
- The brief should say whether an auditor may run a stateless calculation (a one-line script, no files). I read the
  allowed list as closed and did the arithmetic by hand, which cost time and leaves "by hand" on numbers a
  thirty-line simulation would have made exact (the latch's hold times, the subnormal onset).
- The reading list is larger than one pass allows for a layer of this size (about 7000 lines of code plus 3800 of
  docs). Naming which doc sections bind a layer would have let me read `MEMORY.md` and `performance.md` properly
  instead of by keyword.
- What the probes cannot see came from asking "which input was never fed": the adsr rows were all sustain 1 and
  silent after the gate, so the one unflushed IIR of the layer stayed invisible. A rule for the next audit: for every
  stateful stage, list the probe's inputs and name the state it never drives to its resting value.
- "Find every place the two are treated differently" (question 4) worked well as a table exercise; a standing
  per-stage table of what a NaN and an infinity become would make that a diff instead of a search.
- The by-design lists held: none of the listed exceptions had to be argued with, and the one decision I question
  (D2) could be put in its own section once.

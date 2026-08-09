# Mutation-Check Plan — W3 Pitch (PitchModulationTest, FmSynthesisTest)

Production files exercised by both specs:

- `audio_be/src/commonMain/kotlin/voices/strip/pitch/VibratoRenderer.kt`
- `audio_be/src/commonMain/kotlin/voices/strip/pitch/AccelerateRenderer.kt`
- `audio_be/src/commonMain/kotlin/voices/strip/pitch/PitchEnvelopeRenderer.kt`
- `audio_be/src/commonMain/kotlin/voices/strip/pitch/FmRenderer.kt`
- `audio_be/src/commonMain/kotlin/voices/strip/pitch/PitchPipelineBuilder.kt`
- `audio_be/src/commonMain/kotlin/voices/strip/EnvelopeCalc.kt` (FM envelope only)
- `audio_be/src/commonMain/kotlin/ignitor/Ignitors.kt` (`SineIgnitor`, phaseMod consumer)
- `audio_be/src/commonMain/kotlin/ignitor/SampleIgnitor.kt` (phaseMod consumer, SampleVoice tests)

Pipeline order is fixed in `PitchPipelineBuilder.buildPitchPipeline`: **Vibrato → Accelerate → PitchEnvelope → FM**.
Since `BlockContext.freqModBufferWritten` is reset to `false` once per `Voice.render()` call (`Voice.kt:116`) and stages
run in that fixed order, whichever stage is *first active* takes the "write" branch (`=`) and every later active stage
takes the "multiply" branch (`*=`). This matters a lot for which branch each test actually reaches — noted per-test
below.

---

## io.peekandpoke.klang.audio_be.voices.PitchModulationTest

Production files it exercises: VibratoRenderer.kt, AccelerateRenderer.kt, PitchEnvelopeRenderer.kt, FmRenderer.kt,
PitchPipelineBuilder.kt, SampleIgnitor.kt, Ignitors.kt (SineIgnitor)

### 1. "vibrato with depth 0 produces no modulation"

- CLAIM: When vibrato depth is exactly 0.0, the rendered output is bit-identical to a voice with no vibrato configured
  at all.
- MUTATION: `audio_be/src/commonMain/kotlin/voices/strip/pitch/PitchPipelineBuilder.kt:34`
    - OLD: `    if (vibrato.depth > 0.0) {`
    - NEW: `    if (vibrato.depth >= 0.0) {`
- SUSPICION: SUSPECT — both `voiceWith` (depth=0.0) and `voiceWithout` (depth=0.0, default) already fail the *current*
  gate identically, so `VibratoRenderer` never runs for either voice today. Even if the gate is relaxed to admit
  depth=0.0, `VibratoRenderer`'s math (`2.0.pow(sin(phase) * 0.0 / 12.0) == 1.0`) is a mathematical identity at depth
  0 — running the renderer or not produces the *same* output either way. No compilable single-line production mutation
  can flip this test red; it's toothless by construction (degenerate-safe boundary value).

### 2. "vibrato with rate and depth modulates pitch"

- CLAIM: Vibrato with rate=5.0 Hz and depth=0.25 semitones produces output that measurably differs (diffRms > 1e-4) from
  a non-vibrato voice.
- MUTATION: `audio_be/src/commonMain/kotlin/voices/strip/pitch/VibratoRenderer.kt:42`
    - OLD: `                buf[idx] = 2.0.pow(sin(phase) * depthSemitones / 12.0)`
    - NEW: `                buf[idx] = 2.0.pow(0.0 * depthSemitones / 12.0)`
- SUSPICION: LIKELY-RED — vibrato is always the first pitch stage when active, so this is the branch actually executed.
  Dropping the `sin(phase)` term collapses the multiplier to a constant 1.0, making `voiceWith` render identically to
  `voiceWithout`; diff → ~0, failing `> 1e-4`. Note the 512-sample block only covers ~21° of one 5 Hz LFO cycle (phase
  0→0.365 rad), but `sin(phase)` is non-zero across most of that range so the block length is not a blocking issue here.

### 3. "vibrato with high rate produces fast modulation"

- CLAIM: Vibrato at rate=20 Hz produces output that differs from vibrato at rate=2 Hz (same depth=0.5).
- MUTATION: `audio_be/src/commonMain/kotlin/voices/strip/pitch/VibratoRenderer.kt:26`
    - OLD: `        val phaseInc = (TWO_PI * vibrato.rate) / sampleRate`
    - NEW: `        val phaseInc = (TWO_PI * 1.0) / sampleRate`
- SUSPICION: LIKELY-RED — collapses `phaseInc` to a fixed 1 Hz-equivalent for both voices regardless of the configured
  rate; since depth is equal (0.5) in both, the two renders become identical and `diffRms > 1e-4` fails.

### 4. "vibrato with high depth produces wide pitch swings"

- CLAIM: Vibrato depth=0.5 produces strictly more deviation from a no-vibrato baseline than depth=0.01.
- MUTATION: `audio_be/src/commonMain/kotlin/voices/strip/pitch/VibratoRenderer.kt:42`
    - OLD: `                buf[idx] = 2.0.pow(sin(phase) * depthSemitones / 12.0)`
    - NEW: `                buf[idx] = 2.0.pow(sin(phase) * 1.0 / 12.0)`
- SUSPICION: LIKELY-RED — hardcoding the depth term makes both the "wide" and "narrow" voices use the same fixed
  modulation, so `diffWide` and `diffNarrow` become equal and the strict `diffWide > diffNarrow` fails.

### 5. "accelerate with rate 0 produces no pitch change"

- CLAIM: Accelerate amount=0.0 produces output identical to a voice without accelerate.
- MUTATION: `audio_be/src/commonMain/kotlin/voices/strip/pitch/PitchPipelineBuilder.kt:38`
    - OLD: `    if (accelerate.amount != 0.0 && endFrame > startFrame) {`
    - NEW: `    if (true && endFrame > startFrame) {`
- SUSPICION: SUSPECT — same degenerate-boundary pattern as #1. Both compared voices already have `amount = 0.0`, so
  `AccelerateRenderer` never runs today for either. Even forcing it to run for amount=0.0, the math
  (`step = 2.0.pow(0.0/totalFrames) = 1.0`, `ratio` seed also `= 1.0`) is an identity — the mutation cannot produce a
  detectable difference. No mutation flips this test red.

### 6. "accelerate with positive amount increases pitch over time"

- CLAIM: Accelerate amount=2.0 makes the pitch multiplier trajectory change over the voice's lifetime, so a block
  rendered later differs from an earlier block, and differs from a non-accelerated reference at the same position.
- MUTATION: `audio_be/src/commonMain/kotlin/voices/strip/pitch/AccelerateRenderer.kt:37`
    - OLD: `        var ratio = 2.0.pow(accelerate.amount * blockRelStart.toDouble() / totalFrames)`
    - NEW: `        var ratio = 2.0.pow(-accelerate.amount * blockRelStart.toDouble() / totalFrames)`
- SUSPICION: SUSPECT — the test name says "increases pitch" but **neither assertion checks direction**, only magnitude
  of difference (`diffFirstSecond > 1e-4`, `diffFromRef > 1e-4`). Flipping the sign inverts the glide direction
  (increase → decrease) while leaving both magnitudes intact, so this mutation likely survives (stays green).
  Additionally, `diffFirstSecond` alone is weak: a plain **non**-accelerated 440 Hz sine sampled at frame-0 vs frame-512
  windows already differs (512 samples ≈ 5.11 cycles of a ~100.23-sample period — non-integer, so the two windows are
  never phase-aligned), so that half of the assertion would pass even for `amount = 0.0`.

### 7. "accelerate with negative rate decreases pitch over time"

- CLAIM: Accelerate amount=-0.5 produces output that differs from a non-accelerated voice.
- MUTATION: `audio_be/src/commonMain/kotlin/voices/strip/pitch/AccelerateRenderer.kt:37` (same site as #6, applied to
  the negative-amount case)
    - OLD: `        var ratio = 2.0.pow(accelerate.amount * blockRelStart.toDouble() / totalFrames)`
    - NEW: `        var ratio = 2.0.pow(-accelerate.amount * blockRelStart.toDouble() / totalFrames)`
- SUSPICION: SUSPECT — same reasoning as #6: only one assertion (`diff > 1e-4`, no direction check), so sign inversion
  (turning "decrease" into "increase") is not caught. The test can't tell the pitch glide went the wrong way.

### 8. "pitch envelope with null is disabled"

- CLAIM: A voice with `pitchEnvelope = null` produces output identical to a voice built without specifying a pitch
  envelope.
- MUTATION: N/A — could not map to any compilable production mutation.
- SUSPICION: SUSPECT (vacuous/tautological) — `voiceWith` passes `pitchEnvelope = null` explicitly and `voiceWithout`
  uses the *same* default (`pitchEnvelope: Voice.PitchEnvelope? = null`). Both configurations are literally identical;
  `PitchEnvelopeRenderer` is unreachable in both branches (`PitchPipelineBuilder.kt:42`, `if (pitchEnvelope != null)`).
  No mutation inside `PitchEnvelopeRenderer.kt` can be reached by this test, and the gate itself can't be usefully
  mutated (`PitchEnvelopeRenderer`'s constructor parameter is non-nullable, so passing a null through would not
  compile).

### 9. "pitch envelope with attack phase"

- CLAIM: A pitch envelope with `attackFrames=100, amount=2.0, anchor=0.0` causes the output (rendered over the first 128
  frames, i.e. mostly inside the attack ramp) to differ measurably from a voice without a pitch envelope.
- MUTATION: `audio_be/src/commonMain/kotlin/voices/strip/pitch/PitchEnvelopeRenderer.kt:52`
    - OLD: `            envLevel = pEnv.anchor + (1.0 - pEnv.anchor) * progress`
    - NEW: `            envLevel = pEnv.anchor`
- SUSPICION: LIKELY-RED — dropping the ramp term pins `envLevel` at the anchor (0.0) throughout the attack window,
  making `2.0.pow(amount * 0.0 / 12.0) == 1.0` for the whole render; diff → ~0, failing `> 1e-4`.

### 10. "pitch envelope with decay phase"

- CLAIM: A pitch envelope with `attackFrames=0, decayFrames=100, amount=-1.0, anchor=0.0` causes the output to differ
  measurably from a voice without a pitch envelope during the decay ramp.
- MUTATION: `audio_be/src/commonMain/kotlin/voices/strip/pitch/PitchEnvelopeRenderer.kt:55`
    - OLD: `            envLevel = 1.0 - (1.0 - pEnv.anchor) * decayProgress`
    - NEW: `            envLevel = pEnv.anchor`
- SUSPICION: LIKELY-RED — same style of kill as #9 but on the decay branch; envLevel pinned at anchor (0.0) collapses
  the multiplier to 1.0 throughout, failing `> 1e-4`.

### 11. "pitch envelope with both attack and decay"

- CLAIM: A pitch envelope with `attackFrames=50, decayFrames=50, amount=1.0` produces a pitch transient that differs
  measurably from a voice without a pitch envelope.
- MUTATION: `audio_be/src/commonMain/kotlin/voices/strip/pitch/PitchEnvelopeRenderer.kt:58`
    - OLD: `        return 2.0.pow((pEnv.amount * envLevel) / 12.0)`
    - NEW: `        return 2.0.pow((pEnv.amount * 0.0) / 12.0)`
- SUSPICION: LIKELY-RED — this is the shared final conversion used by both the attack and decay branches; zeroing
  `envLevel` here collapses the multiplier to 1.0 for the entire render regardless of phase, failing `> 1e-4`. (This
  mutation would also independently kill #9, #10, #20 — listed separately above because each targets the branch-specific
  formula most directly tied to its own claim.)

### 12. "vibrato and accelerate combine correctly"

- CLAIM: A voice with both vibrato (rate=5, depth=0.25) and accelerate (amount=1.0) produces output that differs from
  vibrato-only and from accelerate-only.
- MUTATION: `audio_be/src/commonMain/kotlin/voices/strip/pitch/AccelerateRenderer.kt:41`
    - OLD: `                buf[ctx.offset + i] *= ratio`
    - NEW: `                buf[ctx.offset + i] += ratio`
- SUSPICION: SUSPECT — vibrato is first (write branch), so this exercises `AccelerateRenderer`'s genuine
  **multiply-into-existing** branch, which is good coverage in principle. But the assertions only check "combined
  differs from each solo variant" — they never check that the combination is mathematically *correct* (multiplicative
  layering of pitch ratios). Swapping `*=` for `+=` produces a structurally wrong but still *different* signal, so
  `diffFromVib > 1e-4` and `diffFromAcc > 1e-4` are both very likely to still hold. The mutation probably survives.

### 13. "vibrato and pitch envelope combine correctly"

- CLAIM: A voice with both vibrato and a pitch envelope (attack=100, amount=2.0) produces output that differs from
  vibrato-only.
- MUTATION: `audio_be/src/commonMain/kotlin/voices/strip/pitch/PitchEnvelopeRenderer.kt:34`
    - OLD: `                buf[idx] *= calculatePitchMod(blockRelStart + i, pEnv)`
    - NEW: `                buf[idx] += calculatePitchMod(blockRelStart + i, pEnv)`
- SUSPICION: SUSPECT — same "combine correctly" gap as #12: an additive instead of multiplicative merge is still
  *different* from vibrato-only, so the single `diff > 1e-4` assertion likely still passes despite the combination being
  wrong.

### 14. "accelerate and pitch envelope combine correctly"

- CLAIM: A voice with both accelerate (amount=0.5) and a pitch envelope (attack=50, decay=50, amount=1.0) produces
  output that differs from accelerate-only.
- MUTATION: `audio_be/src/commonMain/kotlin/voices/strip/pitch/AccelerateRenderer.kt:46`
    - OLD: `                buf[ctx.offset + i] = ratio`
    - NEW: `                buf[ctx.offset + i] = 1.0`
- SUSPICION: SUSPECT — accelerate is first here (write branch, since no vibrato). Killing accelerate's own contribution
  to the shared buffer neutralizes it for **both** `voiceBoth` and `voiceAccelOnly` (same production code, not
  voice-specific), so `voiceAccelOnly` degrades to a plain clean sine while `voiceBoth` degrades to "pitch-envelope
  only." The comparison (`diffFromAcc`) then just measures the pitch-envelope's own effect vs a neutered accelerate-only
  voice — still `> 1e-4`. The test can't tell "accelerate is broken" from "accelerate works," since only the *aggregate*
  difference from ONE reference is checked, not each contributor's own presence.

### 15. "all three pitch modulations combine correctly"

- CLAIM: Vibrato + accelerate + pitch envelope together produce output that deviates from a fully clean voice by more
  than 1e-3.
- MUTATION: `audio_be/src/commonMain/kotlin/voices/strip/pitch/PitchEnvelopeRenderer.kt:58` (same as claim #11)
    - OLD: `        return 2.0.pow((pEnv.amount * envLevel) / 12.0)`
    - NEW: `        return 2.0.pow((pEnv.amount * 0.0) / 12.0)`
- SUSPICION: SUSPECT — killing *only* the pitch-envelope contribution while vibrato (depth 0.25) and accelerate (amount
  0.5) remain intact very likely still clears the 1e-3 bar on its own (test #2's much looser 1e-4 threshold already
  showed vibrato depth 0.25 alone produces a substantial diff). This "kitchen sink vs clean" comparison can't isolate a
  regression in any single one of the three contributors.

### 16. "pitch modulation works with SampleVoice"

- CLAIM: Vibrato (rate=5, depth=0.5) also modulates sample-playback pitch (SampleIgnitor), not just synthesized
  oscillators — output differs from a non-vibrato sample voice.
- MUTATION: `audio_be/src/commonMain/kotlin/ignitor/SampleIgnitor.kt:95`
    - OLD: `                ph += if (phaseMod != null) rate * phaseMod[idxOut] else rate`
    - NEW: `                ph += rate`
- SUSPICION: LIKELY-RED — this is a genuinely distinct code path from the synth-oscillator tests (SampleIgnitor's
  clean-digital-path playhead advance). Dropping the `phaseMod` term makes both compared voices advance the playhead
  identically regardless of vibrato, collapsing diff to ~0 and failing `> 1e-4`.

### 17. "pitch modulation affects FM modulator frequency"

- CLAIM (as named): Enabling vibrato changes the frequency of the FM modulator oscillator itself.
- MUTATION: `audio_be/src/commonMain/kotlin/voices/strip/pitch/FmRenderer.kt:38`
    - OLD: `        val modFreq = freqHz * fm.ratio`
    - NEW: `        val modFreq = freqHz * fm.ratio * 2.0`
- SUSPICION: SUSPECT — `FmRenderer.modFreq`/`modInc` are computed purely from the constructor's static `freqHz` and
  `fm.ratio`; they never read `ctx.freqModBuffer`, so vibrato genuinely does **not** affect the FM modulator's own
  frequency. Both `voiceWithVib` and `voiceNoVib` use the identical FM config, so this modFreq mutation affects them
  identically — the vibrato-driven `diff` persists either way, and the mutation is not caught. The test's real (and
  only) claim actually verified is "vibrato's own independent multiplicative contribution still shows up when FM is
  concurrently active" — functionally the same as claim #2, not what the name says. This is a **test-name mismatch**: no
  mutation to the FM-modulator-frequency calculation is detectable via this test.

### 18. "vibrato with very small depth produces subtle modulation"

- CLAIM: Vibrato depth=1.0 produces strictly more deviation from baseline than depth=0.01.
- MUTATION: `audio_be/src/commonMain/kotlin/voices/strip/pitch/VibratoRenderer.kt:42` (same site as #4)
    - OLD: `                buf[idx] = 2.0.pow(sin(phase) * depthSemitones / 12.0)`
    - NEW: `                buf[idx] = 2.0.pow(sin(phase) * 1.0 / 12.0)`
- SUSPICION: LIKELY-RED — hardcoding depth collapses `diffLarge` and `diffSubtle` to the same value, breaking the strict
  `diffLarge > diffSubtle`.

### 19. "accelerate with very high rate produces extreme pitch sweep"

- CLAIM: Accelerate amount=10.0 produces strictly more deviation from baseline than amount=1.0.
- MUTATION: `audio_be/src/commonMain/kotlin/voices/strip/pitch/AccelerateRenderer.kt:30`
    - OLD: `    private val step = 2.0.pow(accelerate.amount / totalFrames)`
    - NEW: `    private val step = 1.0`
- SUSPICION: LIKELY-RED — for this test, `startFrame=0` and the single render call starts at `blockStart=0`, so the seed
  `ratio` (line 37) always evaluates to `2.0.pow(amount * 0 / totalFrames) == 1.0` regardless of `amount`; **all** of
  the accelerate effect in this specific test comes from `step` alone. Hardcoding `step = 1.0` collapses both "extreme"
  and "moderate" voices to zero pitch change (identical to "none"), turning the assertion into `0 > 0`, which is false.

### 20. "pitch envelope with zero attack/decay time"

- CLAIM: A pitch envelope with `attackFrames=0, decayFrames=0, amount=1.0` renders successfully and produces non-zero
  output.
- MUTATION: `audio_be/src/commonMain/kotlin/voices/strip/pitch/PitchEnvelopeRenderer.kt:58` (same site as #11/#15)
    - OLD: `        return 2.0.pow((pEnv.amount * envLevel) / 12.0)`
    - NEW: `        return 2.0.pow((pEnv.amount * 0.0) / 12.0)`
- SUSPICION: SUSPECT — the sole assertion (`outputRms > 0.0`) is a structural non-silence check that a plain sine
  already satisfies with or without any pitch modulation. Worse: with `attackFrames = decayFrames = 0`,
  `calculatePitchMod`'s `if`/`else if` conditions (`relPosD < 0` and `relPosD < 0`) are **both** false for every
  `relPosD >= 0`, so `envLevel` stays pinned at `anchor` (0.0) the entire render — `PitchEnvelopeRenderer` already has
  zero audible effect in this exact configuration even with correct code. The test would pass identically whether the
  renderer is correct, broken, or removed.

### 21. "negative vibrato depth is treated as no modulation"

- CLAIM: A vibrato with depth=-0.25 is treated identically to no vibrato (pipeline gate skips it for any non-positive
  depth).
- MUTATION: `audio_be/src/commonMain/kotlin/voices/strip/pitch/PitchPipelineBuilder.kt:34`
    - OLD: `    if (vibrato.depth > 0.0) {`
    - NEW: `    if (vibrato.depth >= -1.0) {`
- SUSPICION: LIKELY-RED — unlike #1 (depth exactly 0.0, a degenerate identity), depth=-0.25 is **not** mathematically
  degenerate: `2.0.pow(sin(phase) * -0.25 / 12.0)` is a real, non-trivial oscillating multiplier. If the gate wrongly
  admitted this negative depth, `VibratoRenderer` would apply genuine (inverted-phase) pitch modulation, producing a
  real difference from the truly-unmodulated `voiceNone` and failing `diff < 1e-6`. Good contrast with #1: this boundary
  case is actually testable.

---

## io.peekandpoke.klang.audio_be.voices.FmSynthesisTest

Production files it exercises: FmRenderer.kt, PitchPipelineBuilder.kt, EnvelopeCalc.kt, SampleIgnitor.kt,
VibratoRenderer.kt (combined test only)

### 1. "FM with depth 0 produces no modulation"

- CLAIM: FM with depth=0.0 produces output identical to no FM at all.
- MUTATION: `audio_be/src/commonMain/kotlin/voices/strip/pitch/PitchPipelineBuilder.kt:46`
    - OLD: `    if (fm != null && fm.depth != 0.0) {`
    - NEW: `    if (fm != null) {`
- SUSPICION: SUSPECT — same degenerate-boundary pattern as PitchModulationTest #1/#5. Even if the gate is relaxed to
  include `depth=0.0`, `FmRenderer`'s math (`effectiveDepth = 0.0 * envLevel = 0.0` ⇒ `fmMult ≡ 1.0`) is an identity
  regardless. No mutation flips this red.

### 2. "FM with null is disabled"

- CLAIM: A voice with `fm = null` renders a normal non-silent signal.
- MUTATION: N/A — could not map to a compilable production mutation.
- SUSPICION: SUSPECT (vacuous) — `fm = null` guarantees `FmRenderer` is never constructed (gate `fm != null`), so no
  mutation inside `FmRenderer.kt` is reachable by this test. The sole assertion (`outputRms > 0.0`) is satisfied by any
  nonzero-amplitude Ignitor output, independent of FM correctness.

### 3. "FM modulator ratio affects modulation frequency"

- CLAIM: FM modulator ratio=2.0 produces audibly different output from ratio=0.5, at the same depth.
- MUTATION: `audio_be/src/commonMain/kotlin/voices/strip/pitch/FmRenderer.kt:38`
    - OLD: `        val modFreq = freqHz * fm.ratio`
    - NEW: `        val modFreq = freqHz * 1.0`
- SUSPICION: LIKELY-RED — this is a genuine differential test (compares two configured voices, not vs. a clean
  baseline). Dropping the `fm.ratio` term makes both voices use the same `modFreq`, collapsing their outputs to be
  identical; `diff > 1e-3` fails.

### 4. "FM depth controls modulation intensity"

- CLAIM: FM depth=200 produces strictly more deviation from clean than depth=10.
- MUTATION: `audio_be/src/commonMain/kotlin/voices/strip/pitch/FmRenderer.kt:43`
    - OLD: `        val effectiveDepth = fm.depth * envLevel`
    - NEW: `        val effectiveDepth = 50.0 * envLevel`
- SUSPICION: LIKELY-RED — hardcoding `effectiveDepth` removes the actual depth value's influence entirely; both "high"
  and "low" depth voices produce (approximately) the same output, breaking the strict `diffHigh > diffLow`.

### 5. "FM envelope modulates FM depth over time"

- CLAIM: With an attack-only FM envelope (attackFrames=256), a late block (near full envelope) deviates more from a
  clean sine than an early block (near zero envelope).
- MUTATION: `audio_be/src/commonMain/kotlin/voices/strip/EnvelopeCalc.kt:65`
    - OLD: `            AdsrCurve.Exponential -> adsrExpShape(p)`
    - NEW: `            AdsrCurve.Exponential -> 1.0`
- SUSPICION: LIKELY-RED — pins the attack-phase envelope value to full (1.0) immediately regardless of progress `p`,
  removing the "ramps up over time" behavior; `diffEarly` and `diffLate` become comparable, breaking
  `diffLate > diffEarly`. Note: `EnvelopeCalc.kt` has no dedicated spec of its own — this is its only coverage for the
  attack/Exponential branch via the pitch suite (also shared with amp/filter envelopes elsewhere, out of scope here).

### 6. "FM envelope with decay phase"

- CLAIM: During the FM envelope's decay phase (attack=100, decay=100, sustain=0.5, sampled at frame 150), FM modulation
  is still active — output differs from clean.
- MUTATION: `audio_be/src/commonMain/kotlin/voices/strip/EnvelopeCalc.kt:82`
    - OLD: `        env.sustainLevel + (1.0 - env.sustainLevel) * shape`
    - NEW: `        env.sustainLevel`
- SUSPICION: SUSPECT — the single-point assertion (`diff > 1e-3`) only checks "FM is still somewhat active," not the
  interpolation shape. Freezing the decay branch to flat `sustainLevel` (0.5) instead of interpolating from 1.0→0.5
  still leaves FM meaningfully active at frame 150 (effectiveDepth = 100 × 0.5 either way, numerically close to the
  correct value at the midpoint), so this mutation likely survives.

### 7. "FM works with SampleVoice"

- CLAIM: FM modulates sample-playback pitch rate (SampleIgnitor), not just synthesized oscillators.
- MUTATION: `audio_be/src/commonMain/kotlin/ignitor/SampleIgnitor.kt:95` (same site as PitchModulationTest claim #16)
    - OLD: `                ph += if (phaseMod != null) rate * phaseMod[idxOut] else rate`
    - NEW: `                ph += rate`
- SUSPICION: LIKELY-RED — dropping `phaseMod` collapses FM and clean sample playback to identical playhead advance;
  `diff > 1e-4` fails.

### 8. "FM modulator phase advances correctly"

- CLAIM: After rendering a block, the FM modulator's internal phase accumulator (`fm.modPhase`) has advanced at the
  correct rate.
- MUTATION: `audio_be/src/commonMain/kotlin/voices/strip/pitch/FmRenderer.kt:39`
    - OLD: `        val modInc = (TWO_PI * modFreq) / sampleRate`
    - NEW: `        val modInc = (TWO_PI * modFreq) / (sampleRate * 1000.0)`
- SUSPICION: SUSPECT — the assertion (`afterPhase > initialPhase`) only checks the phase moved by *some* positive
  amount, not by the *correct* amount. This mutation makes `modPhase` advance ~1000× too slowly (over 100 frames, ~0.006
  rad instead of ~6.3 rad) — a drastic, clearly audible bug — yet `afterPhase` is still `> initialPhase (0.0)`, so the
  test still passes. Classic pass-through/holder-field, magnitude-blind pattern: the test reads a bookkeeping field
  rather than anything the DSP actually consumes downstream to produce audio.

### 9. "FM with very high ratio produces complex spectrum"

- CLAIM: FM ratio=10.0 + depth=500 (freqHz=100) produces output that deviates substantially (>0.01 RMS) from a clean
  sine.
- MUTATION: `audio_be/src/commonMain/kotlin/voices/strip/pitch/FmRenderer.kt:48`
    - OLD: `            val fmMult = 1.0 + (modSignal / freqHz)`
    - NEW: `            val fmMult = 1.0 + (modSignal / (freqHz * 1000.0))`
- SUSPICION: LIKELY-RED — scales the FM contribution down by 1000×; given the original config already implies large
  `fmMult` swings (several hundred percent), this order-of-magnitude-plus reduction should collapse the deviation well
  below the (comparatively tight) 0.01 threshold.

### 10. "FM with fractional ratio works correctly"

- CLAIM: Sub-harmonic FM (ratio=0.25) produces output that differs from clean sine, verifying fractional ratios work.
- MUTATION: `audio_be/src/commonMain/kotlin/voices/strip/pitch/FmRenderer.kt:38`
    - OLD: `        val modFreq = freqHz * fm.ratio`
    - NEW: `        val modFreq = freqHz`
- SUSPICION: SUSPECT — unlike claim #3 (which compares two different ratios directly), this test only compares against a
  `fm = null` clean baseline. Forcing `modFreq` to ignore `fm.ratio` entirely (pinning it to 1:1) still leaves FM active
  with nonzero depth, so the output still differs from clean and `diff > 1e-3` still passes. The test verifies "FM is
  on," not anything specific to the ratio being fractional.

### 11. "FM combined with vibrato"

- CLAIM: Adding vibrato (rate=5, depth=0.25) to an FM-active voice changes the output relative to FM-only.
- MUTATION: `audio_be/src/commonMain/kotlin/voices/strip/pitch/VibratoRenderer.kt:42` (same site as PitchModulationTest
  claim #2)
    - OLD: `                buf[idx] = 2.0.pow(sin(phase) * depthSemitones / 12.0)`
    - NEW: `                buf[idx] = 2.0.pow(0.0 * depthSemitones / 12.0)`
- SUSPICION: LIKELY-RED — FM config is identical in both compared voices; only vibrato differs. Killing vibrato's
  write-branch contribution collapses the two voices to identical output, correctly failing `diff > 1e-4`. (Unlike the
  "combine correctly" tests in PitchModulationTest, this one genuinely isolates vibrato's marginal contribution against
  a fixed FM baseline.)

### 12. "FM envelope at sustain level"

- CLAIM: FM envelope sustain=0.3 produces strictly less deviation from clean than sustain=1.0 (both sampled at frame
  200, well past attack+decay for both configs).
- MUTATION: `audio_be/src/commonMain/kotlin/voices/strip/EnvelopeCalc.kt:85`
    - OLD: `    else -> env.sustainLevel`
    - NEW: `    else -> 1.0`
- SUSPICION: LIKELY-RED — hardcodes the pure-sustain envelope value to 1.0 regardless of the actual `sustainLevel`
  field, so both "sustain 0.3" and "sustain 1.0" voices use the same effective FM depth (100 × 1.0) at frame 200;
  `diffFull` and `diffSustain` become equal, breaking the strict `diffFull > diffSustain`.

### 13. "FM envelope release phase"

- CLAIM: During the FM envelope's release phase (after gate end, sampled mid-release at frame 150), FM modulation
  remains active — output still differs from clean.
- MUTATION: `audio_be/src/commonMain/kotlin/voices/strip/EnvelopeCalc.kt:46`
    - OLD: `        levelAtGateEnd * shape`
    - NEW: `        0.0`
- SUSPICION: LIKELY-RED — forces the FM envelope to zero for the entire release phase; at frame 150 (well into release)
  FM becomes fully inactive, diff → ~0, failing `> 1e-4`.

### 14. "FM with negative depth works"

- CLAIM: Negative FM depth (-100.0) still produces active modulation (differs from clean), rather than being treated as
  off.
- MUTATION: `audio_be/src/commonMain/kotlin/voices/strip/pitch/PitchPipelineBuilder.kt:46`
    - OLD: `    if (fm != null && fm.depth != 0.0) {`
    - NEW: `    if (fm != null && fm.depth > 0.0) {`
- SUSPICION: LIKELY-RED — this is exactly the gate-boundary bug the test is positioned to catch: mutating `!= 0.0` to
  `> 0.0` wrongly excludes negative depths (mirroring vibrato's stricter `> 0.0` gate), collapsing `voiceNeg` to match
  `voiceClean`; `diff > 1e-3` fails. Good, deliberate coverage of the asymmetry between FM's `!=` gate and vibrato's `>`
  gate.

### 15. "FM ratio of 1.0 produces harmonic sidebands"

- CLAIM (as named): FM at ratio=1.0 produces harmonic sidebands (implies spectral structure).
- MUTATION: `audio_be/src/commonMain/kotlin/voices/strip/pitch/FmRenderer.kt:46`
    - OLD: `            val modSignal = sin(modPhase) * effectiveDepth`
    - NEW: `            val modSignal = sin(modPhase) * 0.0`
- SUSPICION: SUSPECT — the mutation itself would be caught (LIKELY-RED: `fmMult ≡ 1.0`, diff → 0, failing `> 1e-3`), but
  the *claim as named* is not actually verified by any assertion in this suite — there is no FFT/spectral analysis
  anywhere in either spec. This test has identical rigor to a generic "FM is active" check (same shape as claims #1, #9,
  #10, #14's baseline), just with ratio pinned to 1.0 for flavor. The name promises harmonic/spectral verification the
  assertions don't perform.

---

## Notes

**Per-renderer: is it genuinely exercised, and by which test?**

- **VibratoRenderer** — genuinely exercised. Its "write" branch (`VibratoRenderer.kt:42`) is real end-to-end coverage
  (verified via `phaseMod` consumption in `Ignitors.kt`'s `SineIgnitor` and `SampleIgnitor.kt`), exercised by
  PitchModulationTest #2, #3, #4, #12, #13, #15, #16, #18, #21 and FmSynthesisTest #11. Its "multiply" branch
  (`VibratoRenderer.kt:35`, `*=`) is **dead code**: `PitchPipelineBuilder` always adds Vibrato first when active, so
  `ctx.freqModBufferWritten` is always `false` when `VibratoRenderer.render()` runs — line 35 can never execute under
  the current pipeline ordering, by any test in this repo, not just these two specs. Worth flagging to the author:
  either simplify the renderer to drop the dead branch, or note the pipeline-ordering assumption is load-bearing and
  untested.

- **AccelerateRenderer** — genuinely exercised, both branches. Write branch (line 46, when Accelerate is first — no
  vibrato before it) exercised by #5, #6, #7, #14, #19. Multiply branch (line 41, when Vibrato precedes it) exercised by
  #12, #15. Partial gap: the **seed formula** (line 37, `2.0.pow(accelerate.amount * blockRelStart / totalFrames)`) only
  meaningfully differs from an identity when a render call starts at a non-zero `blockRelStart`; that only happens in
  test #6 (`blockStart=512`). Every other accelerate test renders from `blockStart=0`, where the seed always evaluates
  to `2^0=1` and `step` (line 30) alone drives the observed effect — demonstrated concretely by mutation #19. A bug
  isolated to just the seed formula (wrong sign, wrong denominator) would only be caught by #6, and #6's own assertions
  don't check direction (see #6/#7 SUSPICION).

- **PitchEnvelopeRenderer** — genuinely exercised for the attack and decay branches and the final pow conversion (#9,
  #10, #11, #13, #14, #15). The `curve` field on `Voice.PitchEnvelope` (constructed with `curve = 0.0` in every single
  test in this suite) is **never read** by `PitchEnvelopeRenderer.calculatePitchMod` at all — it's a dead/unused field
  in the production code (or an unimplemented feature), so no mutation involving curve-shaping could ever be written for
  it; this isn't a test gap so much as a schema/implementation mismatch worth flagging. Also: every
  `Voice.PitchEnvelope` in both specs uses `anchor = 0.0`, so the `(1.0 - pEnv.anchor)` scaling terms in the
  attack/decay formulas are only ever exercised in their degenerate form; a bug specifically in that scaling factor
  would be invisible to this suite.

- **FmRenderer** — genuinely exercised, extensively (all 15 FmSynthesisTest cases + PitchModulationTest #17). Always
  takes the `*=` merge loop; the "ensure buffer initialized" fill-with-1.0 block (lines 33–36) is exercised whenever FM
  is the sole/first active pitch stage (most FmSynthesisTest cases), and the true multiply-into-existing-modulation case
  is exercised when FM follows another active stage (PitchModulationTest #15, #17, FmSynthesisTest #11). The FM
  envelope's `AdsrCurve` variants other than the default `Exponential` are never exercised (no test constructs an FM
  envelope with a non-default curve).

**Production behavior with no apparent test claim (coverage holes):**

- `VibratoRenderer.kt:35` (multiply branch) — dead code under current pipeline ordering; no test could exercise it even
  if desired.
- `AccelerateRenderer`'s seed formula in isolation from `step` (see above) — only test #6 touches it, and weakly (no
  direction check).
- `PitchEnvelope.curve` — unused field, no behavior to test.
- `PitchEnvelope.anchor` at nonzero values — never tested (always 0.0 across both specs).
- FM envelope `AdsrCurve` variants beyond default `Exponential` — untested.
- `SineIgnitor`'s analog-drift + phaseMod combined path (`Ignitors.kt` line ~93,
  `phase += phaseInc * phaseMod[i] * d.nextMultiplier()`) — neither spec ever passes a nonzero `analog` to
  `Ignitors.sine()`, so this combined branch (drift jitter stacked with pitch modulation) is entirely untested by this
  suite (may be covered elsewhere, out of scope here).

**Tests whose name doesn't match what they do:**

- PitchModulationTest "pitch modulation affects FM modulator frequency" (#17) — FM's own modulator frequency is static
  (derived only from `freqHz`/`fm.ratio`), never reads `ctx.freqModBuffer`; the test actually just re-verifies vibrato's
  independent contribution persists when FM is concurrently configured (same rigor as claim #2).
- PitchModulationTest "accelerate with positive amount increases pitch over time" (#6) and "...negative rate decreases
  pitch..." (#7) — neither checks the sign/direction of the pitch shift, only that some difference exists.
- FmSynthesisTest "FM ratio of 1.0 produces harmonic sidebands" (#15) — no spectral/harmonic analysis anywhere in the
  suite; equivalent to a generic "FM is active" check.
- FmSynthesisTest "FM with fractional ratio works correctly" (#10) — doesn't verify anything specific to the ratio being
  fractional (no comparison against a different ratio), just that FM is active vs. clean.
- The four "...combine correctly" tests (PitchModulationTest #12, #13, #14; FmSynthesisTest #11) — "correctly" is not
  verified; only that the combined output differs from each solo variant, which a structurally wrong (e.g. additive
  instead of multiplicative) combination would also satisfy. (FmSynthesisTest #11 is the partial exception — it isolates
  vibrato's marginal contribution against an identical fixed FM baseline, so it's less exposed to this gap than the
  three PitchModulationTest cases.)

**Could not map to a mutation (vacuous/tautological tests):**

- PitchModulationTest "pitch envelope with null is disabled" (#8) — both compared voices are configured identically
  (`pitchEnvelope = null` in both); `PitchEnvelopeRenderer` unreachable in either branch.
- FmSynthesisTest "FM with null is disabled" (#2) — same pattern; `fm = null`, only a structural `RMS > 0` smoke check,
  `FmRenderer` unreachable.

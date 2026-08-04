# Mutation-Check Plan — W1: Envelope (audio_be/voices)

Repo root: `/opt/dev/peekandpoke/klang`

Production files in scope:

- `/opt/dev/peekandpoke/klang/audio_be/src/commonMain/kotlin/voices/Voice.kt` (`Voice.Envelope` data holder)
- `/opt/dev/peekandpoke/klang/audio_be/src/commonMain/kotlin/voices/strip/filter/EnvelopeRenderer.kt` (VCA per-sample
  renderer, the de-click smoother)
- `/opt/dev/peekandpoke/klang/audio_be/src/commonMain/kotlin/voices/strip/EnvelopeCalc.kt` (`envelopeLevelAtPosition`,
  `calculateControlRateEnvelope` — control-rate envelope math, no de-click)
- `/opt/dev/peekandpoke/klang/audio_be/src/commonMain/kotlin/AdsrCurveMath.kt` (`ADSR_EXP_K`, `ENV_DECLICK_SECONDS`,
  `adsrExpShape`, `adsrExpNorm`, `envDeclickCoeff`)
- `/opt/dev/peekandpoke/klang/audio_be/src/commonMain/kotlin/voices/strip/filter/FilterPipelineBuilder.kt` (wires
  `EnvelopeRenderer` into the pipeline from `StageDsl.Vca`)
- `/opt/dev/peekandpoke/klang/audio_bridge/src/commonMain/kotlin/PipelineDsl.kt` (`StageDsl.Vca` defaults: `expK=3.0`,
  `declickSeconds=0.001` — matches the globals, used by `PipelinePreset.Modern`)
- `/opt/dev/peekandpoke/klang/audio_be/src/commonTest/kotlin/voices/VoiceTestHelpers.kt` (test harness —
  `createSynthVoice`/`createVoice` build the real pipeline via `buildFilterPipeline(PipelinePreset.Modern.dsl, ...)`, so
  `EnvelopeTest`/`EnvelopeDeclickSpec` exercise the real `EnvelopeRenderer`, not a stub)

Key structural fact used throughout: `EnvelopeRenderer` seeds its de-click smoother to the **exact raw curve value** on
the very first sample it ever renders for a given `Voice.Envelope` (`smoothPrimed` gate, `EnvelopeRenderer.kt:130-133`).
So for any test that calls `voice.render()` exactly once (a fresh envelope), `buffer[0]` equals the raw (unsmoothed)
curve value — no de-click lag. This matters for several mutations below.

Another structural fact: every `AdsrCurve` shape function is normalized so `g(0)=0` and `g(1)=1` (attack) / the mirrored
form for decay-from-1/release-from-startLevel. That means the **start and end of any single-phase test are
curve-invariant** — a same-family formula swap (e.g. `Square` substituted for `Linear`) cannot be caught by "value at
t=0" or "value at t=end" assertions, only by a mid-phase golden value (which is exactly what `EnvelopeShapeTest` is
for). This shows up as a recurring caveat below.

---

## io.peekandpoke.klang.audio_be.voices.EnvelopeTest

Production files it exercises: `Voice.kt` (Envelope class), `EnvelopeRenderer.kt`, `FilterPipelineBuilder.kt`,
`PipelineDsl.kt` (Vca stage defaults), `AdsrCurveMath.kt`, `VoiceTestHelpers.kt`

### 1. "attack phase increases linearly from 0 to 1"

- CLAIM: With a 100-frame Linear attack and no prior render history, the VCA gain starts at ~0, rises monotonically, and
  reaches above 0.6 by the end of the attack window (attack actually ramps up, it doesn't stay flat or fall).
- MUTATION: `audio_be/src/commonMain/kotlin/voices/strip/filter/EnvelopeRenderer.kt:98`
    - OLD: `                            AdsrCurve.Linear -> p`
    - NEW: `                            AdsrCurve.Linear -> 1.0 - p`
- SUSPICION: LIKELY-RED — this is the very first sample ever rendered for this envelope (`smoothPrimed` is false), so
  `buffer[0]` equals the raw curve value unlagged: mutated it becomes ~1.0 instead of ~0.0, failing
  `ctx.voiceBuffer[0] shouldBe (0.0 plusOrMinus 0.02)` immediately. Caveat: a subtler same-family swap (e.g. `Square`
  substituted for `Linear`, still `0→1` monotonic) would NOT be caught — all the assertions here are direction/threshold
  checks, not a golden mid-ramp value (that precision is deliberately deferred to `EnvelopeShapeTest`, per the file's
  own docstring).

### 2. "decay phase decreases from 1 to sustain level"

- CLAIM: With a 100-frame Linear decay (sustain=0.5) rendered from the moment decay starts, the gain begins at ~1.0,
  falls monotonically, and settles at exactly the sustain level once the decay window has passed.
- MUTATION: `audio_be/src/commonMain/kotlin/voices/strip/filter/EnvelopeRenderer.kt:109-110`
    - OLD:
      ```
                          val p = decPos * decRate
                          val omp = 1.0 - p
      ```
    - NEW:
      ```
                          val p = decPos * decRate
                          val omp = p
      ```
- SUSPICION: LIKELY-RED — this render is also the first-ever sample for this envelope (single `render()` call), so
  `buffer[0]` is unlagged: mutated, decay would *start* at sustain (0.5) instead of ~1.0, failing
  `ctx.voiceBuffer[0] shouldBe (1.0 plusOrMinus 0.03)` outright and reversing the fall direction. Caveat, verified by
  hand: a formula-only swap (e.g. `Square` substituted for `Linear` inside the decay `when`) is **not** caught by any of
  this test's four assertions — `buffer[0]` is curve-invariant (both give `omp=1→shape=1`), the two monotonic-direction
  checks still hold for any monotonically-falling curve, and `buffer[550]` settles to the plateau (`else -> sustain`)
  which doesn't depend on the decay curve at all. That's a real coverage gap for decay-curve-shape correctness in this
  test specifically (golden coverage lives in `EnvelopeShapeTest`).

### 3. "sustain phase holds at sustain level"

- CLAIM: Once attack+decay have completed and the gate is still open, the gain holds flat at exactly `sustainLevel` for
  the whole sustain window (no further movement).
- MUTATION: `audio_be/src/commonMain/kotlin/voices/strip/filter/EnvelopeRenderer.kt:122`
    - OLD: `                    else -> sustain`
    - NEW: `                    else -> sustain + 0.05`
- SUSPICION: LIKELY-RED — sustain=0.6, tolerance ±0.01; +0.05 pushes every sample to 0.65, clearly outside all three
  assertions.

### 4. "release phase decays from sustain to zero"

- CLAIM: Once the gate closes, a 100-frame Linear release starts at the current level (~1.0) and falls monotonically
  to ~0 by the end of the release window.
- MUTATION: `audio_be/src/commonMain/kotlin/voices/strip/filter/EnvelopeRenderer.kt:80-81`
    - OLD:
      ```
                  val p = (relPos / relRateDen).coerceAtMost(1.0)
                  val omp = 1.0 - p
      ```
    - NEW:
      ```
                  val p = (relPos / relRateDen).coerceAtMost(1.0)
                  val omp = p
      ```
- SUSPICION: LIKELY-RED — the release-phase render is the second `render()` call on this envelope, but the first sample
  of the release block is still the first sample where `absPos >= gateEndPos`, and `env.level` carried over is exactly
  1.0 with the smoother already converged (100 frames of steady sustain beforehand), so `buffer[0]` reads essentially
  the raw release value: mutated, it jumps to 0.0 immediately instead of ~1.0, failing
  `ctx.voiceBuffer[0] shouldBe (1.0 plusOrMinus 0.03)` and reversing the direction checks. Same
  curve-invariant-endpoints caveat as claim #2 applies to a same-family curve swap.

### 5. "zero attack time produces immediate full amplitude"

- CLAIM: When `attackFrames=0`, the VCA gain is already at full amplitude on the very first sample — the de-click "seed
  to current value" priming means an instant attack is NOT faded in.
- MUTATION: `audio_be/src/commonMain/kotlin/voices/strip/filter/EnvelopeRenderer.kt:131`
    - OLD: `                smoothed = currentEnv`
    - NEW: `                smoothed = 0.0`
- SUSPICION: LIKELY-RED — with the seed forced to 0.0 instead of `currentEnv` (1.0), the very first de-click step only
  moves `smoothed` by one `declick` increment (~0.0224 of the gap), so `buffer[0]` ≈ 0.02, far outside
  `1.0 plusOrMinus 0.01`.

### 6. "zero decay time transitions immediately to sustain"

- CLAIM: When `decayFrames=0`, the envelope skips the decay ramp entirely — at the exact frame decay would have started,
  the gain is already at `sustainLevel`, not still at the attack peak.
- MUTATION: `audio_be/src/commonMain/kotlin/voices/strip/filter/EnvelopeRenderer.kt:107`
    - OLD: `                    absPos < attDecFrames -> {`
    - NEW: `                    absPos <= attDecFrames -> {`
- SUSPICION: LIKELY-RED — at `absPos=100=attDecFrames`, the off-by-one now routes into the decay branch
  (`decPos=0, p=0, omp=1, shape=1`) instead of the `else` plateau, producing
  `currentEnv = sustain + (1-sustain)*1 = 1.0` instead of `0.5`, failing
  `ctx.voiceBuffer[0] shouldBe (0.5 plusOrMinus 0.02)`.

### 7. "zero release time produces very fast decay"

- CLAIM: When `releaseFrames=0`, the release is still de-clicked (fades over ~1ms) rather than dropping to silence in a
  single sample — `buffer[1]` should still be well above 0.5, not already gone.
- MUTATION: `audio_be/src/commonMain/kotlin/voices/strip/filter/EnvelopeRenderer.kt:134`
    - OLD: `            smoothed += declick * (currentEnv - smoothed)`
    - NEW: `            smoothed = currentEnv`
- SUSPICION: LIKELY-RED — bypassing the one-pole restores the raw hard cutoff: with `releaseFrames=0`, `relRateDen`
  falls back to `1.0`, so at `relPos=1`, `p=1.0 (coerced), omp=0, currentEnv=0.0` — `buffer[1]` becomes exactly `0.0`,
  failing `(ctx.voiceBuffer[1] > 0.5) shouldBe true`.

### 8. "full ADSR cycle works correctly"

- CLAIM: In one contiguous 1000-frame render spanning attack→decay→sustain→release, all four phases behave correctly in
  sequence within a single `render()` call (not just across separate calls).
- MUTATION: same mutation as claim #3 (`EnvelopeRenderer.kt:122`, `else -> sustain` → `else -> sustain + 0.05`).
- SUSPICION: SUSPECT — the chosen mutation does turn it red (`ctx.voiceBuffer[700] shouldBe (0.5 plusOrMinus 0.02)`
  fails, and the `buffer[90] > buffer[700]` comparison flips since 0.55 ends up above the still-rising attack sample at
  90). But despite the name "full ADSR cycle," **no assertion in this test samples inside the decay window** (frames
  100–199; the checked indices are 20, 80, 90, 700, 850, 899). A decay-curve-shape bug (verified by hand with the
  claim-#2-style `omp=p` mutation) produces no failing assertion here at all — none of the six checked samples fall in
  `[100,200)`, and the post-decay plateau at 700 is curve-independent. So "full ADSR cycle" tests attack-direction,
  sustain-value, and release-direction, but **not decay** — the name promises more than the assertions check.

### 9. "envelope state is preserved across multiple renders"

- CLAIM: The de-click smoother's state (`smoothedLevel`/`smoothPrimed`) is a per-envelope-instance field, not reset per
  `render()` call — a second render continues the gain ramp from where the first left off, without a fresh fade-in.
- MUTATION: `audio_be/src/commonMain/kotlin/voices/strip/filter/EnvelopeRenderer.kt:142`
    - OLD: `        env.smoothedLevel = smoothed`
    - NEW: `        env.smoothedLevel = 0.0`
- SUSPICION: LIKELY-RED — `smoothPrimed` stays `true` (that field is untouched), so the second `render()` call does NOT
  re-prime, but `smoothed` now starts from the mutated `0.0` instead of the real carried-over value (~0.27, given the ~
  44-sample de-click time constant over a 100-frame Linear 0→1 attack lagging behind), producing a large jump at
  `ctx2.voiceBuffer[0]` that fails `secondHalfStart shouldBe (firstHalfValue plusOrMinus 0.02)`.

### 10. "envelope clamps negative values to zero"

- CLAIM: If the envelope math would otherwise go negative (e.g. well past the release window), the VCA gain is clamped
  at 0, not left negative.
- MUTATION: `audio_be/src/commonMain/kotlin/voices/strip/filter/EnvelopeRenderer.kt:73`
    - OLD: `            if (absPos >= gateEndPos) {`
    - NEW: `            if (absPos < gateEndPos) {`
- SUSPICION: SUSPECT — this test never actually exercises the literal clamp line (`EnvelopeRenderer.kt:126`,
  `if (currentEnv < 0.0) currentEnv = 0.0`). Traced by hand: this is a single, first-ever `render()` call at
  `blockStart=200` with no priming render, so `env.releaseStartLevel` is still its default `0.0` when the release branch
  primes it (`env.releaseStartLevel = currentEnv` where `currentEnv == env.level == 0.0`, the field default). Every
  release-phase output is `releaseStartLevel * shape`, i.e. `0.0 * shape`, which is exactly `0.0` for *any* value of
  `shape` — including if `p` were allowed to exceed 1 and go negative (I checked: removing the `.coerceAtMost(1.0)` on
  `p`, or negating the clamp condition, still yields `0.0` because the multiplier is zero). So the clamp is provably
  dead code as far as this test's scenario goes; I could not find any mutation to the clamp itself, the release shape
  math, or the `p`-clamp that this test would catch. The mutation given above instead targets branch *selection*
  (release vs. sustain-plateau) — a different, real bug — and does turn the test red, but it does not touch "clamps
  negative to zero" at all.

### 11. "envelope with very small attack works correctly"

- CLAIM: With `attackFrames=1` and the default Exponential curve, the raw curve completes in a single sample, but the
  de-click smoother spreads the rise out — gain is still visibly increasing 9 samples in, not already flat at 1.0.
- MUTATION: same mutation as claim #7 (`EnvelopeRenderer.kt:134`, bypass the one-pole:
  `smoothed += declick * (currentEnv - smoothed)` → `smoothed = currentEnv`).
- SUSPICION: LIKELY-RED — with the smoother bypassed, `currentEnv` reaches `1.0` at `absPos=1` (attack completes in 1
  frame) and stays there; `buffer[1]` and `buffer[9]` are both exactly `1.0`, failing
  `(ctx.voiceBuffer[9] > ctx.voiceBuffer[1]) shouldBe true` (equal, not greater).

### 12. "envelope with sustain level of 0 produces silence after decay"

- CLAIM: A `sustainLevel=0.0` patch is genuinely silent once decay completes (0.0 is a valid, correctly-plumbed sustain
  value, not accidentally floored/offset elsewhere).
- MUTATION: same mutation as claim #3 (`EnvelopeRenderer.kt:122`, `else -> sustain` → `else -> sustain + 0.05`).
- SUSPICION: LIKELY-RED — `sustain=0.0` becomes `0.05` under the mutation, outside `0.0 plusOrMinus 0.01` for both
  checked samples. (Note: a *multiplicative* mutation like `sustain * 0.9` would be invisible here since
  `0.0 * 0.9 = 0.0` — worth knowing if a reviewer picks a different mutation for this line.)

### 13. "envelope respects gate end frame for release timing"

- CLAIM: Release begins exactly at the voice's configured `gateEndFrame`, not earlier and not later — sustain holds
  right up to the gate, and only then does the release ramp start.
- MUTATION: `audio_be/src/commonMain/kotlin/voices/strip/filter/EnvelopeRenderer.kt:40`
    - OLD: `    private val gateEndPos: Int = gateEndFrame - startFrame`
    - NEW: `    private val gateEndPos: Int = gateEndFrame - startFrame + 50`
- SUSPICION: LIKELY-RED — release now starts 50 frames late (at absolute frame 750 instead of 700).
  `ctx.voiceBuffer[750]` is now still (near-)sustain rather than mid-release, so
  `(ctx.voiceBuffer[750] < ctx.voiceBuffer[700]) shouldBe true` fails (both ≈1.0).

---

## io.peekandpoke.klang.audio_be.voices.EnvelopeShapeTest

Production files it exercises: `EnvelopeCalc.kt` (`envelopeLevelAtPosition`, `calculateControlRateEnvelope`), `Voice.kt`
(Envelope class). This spec calls the pure control-rate functions directly — no `Voice`/`EnvelopeRenderer`, no de-click
smoothing, tight `0.001` tolerance. It is the dedicated golden-value layer that `EnvelopeTest` explicitly defers
curve-shape precision to.

### 1. "attack midpoint — Linear = 0.5"

- CLAIM: At the exact midpoint of a Linear attack, the envelope level is precisely `p = 0.5`.
- MUTATION: `audio_be/src/commonMain/kotlin/voices/strip/EnvelopeCalc.kt:60`
    - OLD: `            AdsrCurve.Linear -> p`
    - NEW: `            AdsrCurve.Linear -> p * p`
- SUSPICION: LIKELY-RED — `0.5² = 0.25 ≠ 0.5 ± 0.001`. (Note: a pure `1.0 - p` flip mutation would be invisible here,
  since `1-0.5=0.5` — the midpoint is the fixed point of that particular flip; a branch-swap mutation like this one is
  required to actually discriminate.)

### 2. "attack midpoint — Square = 0.25"

- CLAIM: At the exact midpoint of a Square attack, the envelope level is precisely `p² = 0.25`.
- MUTATION: `audio_be/src/commonMain/kotlin/voices/strip/EnvelopeCalc.kt:61`
    - OLD: `            AdsrCurve.Square -> p * p`
    - NEW: `            AdsrCurve.Square -> p * p * p`
- SUSPICION: LIKELY-RED — `0.5³ = 0.125 ≠ 0.25 ± 0.001`.

### 3. "attack midpoint — Cube = 0.125"

- CLAIM: At the exact midpoint of a Cube attack, the envelope level is precisely `p³ = 0.125`.
- MUTATION: `audio_be/src/commonMain/kotlin/voices/strip/EnvelopeCalc.kt:62`
    - OLD: `            AdsrCurve.Cube -> p * p * p`
    - NEW: `            AdsrCurve.Cube -> p * p`
- SUSPICION: LIKELY-RED — `0.5² = 0.25 ≠ 0.125 ± 0.001`.

### 4. "decay midpoint, sustain=0 — Linear = 0.5"

- CLAIM: With `sustainLevel=0` isolating pure curve shape, the midpoint of a Linear decay is precisely `0.5`.
- MUTATION: `audio_be/src/commonMain/kotlin/voices/strip/EnvelopeCalc.kt:75`
    - OLD: `            AdsrCurve.Linear -> omp`
    - NEW: `            AdsrCurve.Linear -> omp * omp`
- SUSPICION: LIKELY-RED — `0.5² = 0.25 ≠ 0.5 ± 0.001`.

### 5. "decay midpoint, sustain=0 — Square = 0.25"

- CLAIM: With `sustainLevel=0`, the midpoint of a Square decay is precisely `0.25`.
- MUTATION: `audio_be/src/commonMain/kotlin/voices/strip/EnvelopeCalc.kt:76`
    - OLD: `            AdsrCurve.Square -> omp * omp`
    - NEW: `            AdsrCurve.Square -> omp * omp * omp`
- SUSPICION: LIKELY-RED — `0.5³ = 0.125 ≠ 0.25 ± 0.001`.

### 6. "decay midpoint, sustain=0 — Cube = 0.125"

- CLAIM: With `sustainLevel=0`, the midpoint of a Cube decay is precisely `0.125`.
- MUTATION: `audio_be/src/commonMain/kotlin/voices/strip/EnvelopeCalc.kt:77`
    - OLD: `            AdsrCurve.Cube -> omp * omp * omp`
    - NEW: `            AdsrCurve.Cube -> omp * omp`
- SUSPICION: LIKELY-RED — `0.5² = 0.25 ≠ 0.125 ± 0.001`.

### 7. "decay midpoint with sustain=0.5 — Square gives sustain + (1-sustain)*0.25 = 0.625"

- CLAIM: The decay-to-nonzero-sustain blend formula (`sustain + (1-sustain)*shape`) is wired correctly, not just the raw
  curve shape in isolation — i.e. this is a dedicated check that the sustain-blend arithmetic itself (not only the shape
  function) is correct.
- MUTATION: `audio_be/src/commonMain/kotlin/voices/strip/EnvelopeCalc.kt:82`
    - OLD: `        env.sustainLevel + (1.0 - env.sustainLevel) * shape`
    - NEW: `        env.sustainLevel - (1.0 - env.sustainLevel) * shape`
- SUSPICION: LIKELY-RED — `0.5 - 0.5*0.25 = 0.375 ≠ 0.625 ± 0.001`.

### 8. "decay/sustain boundary — all curves arrive at sustain"

- CLAIM: At the exact frame where decay ends and sustain begins (`absPos = attackFrames + decayFrames`), the envelope
  level equals `sustainLevel` for every `AdsrCurve` variant.
- MUTATION: `audio_be/src/commonMain/kotlin/voices/strip/EnvelopeCalc.kt:85`
    - OLD: `    else -> env.sustainLevel`
    - NEW: `    else -> env.sustainLevel + 0.05`
- SUSPICION: SUSPECT — the mutation above does turn it red (`0.35 ≠ 0.3 ± 0.001` for all six curves), but the test's
  premise is hollow: at `absPos=200=attackFrames+decayFrames`, the guard `absPos < env.attackFrames + env.decayFrames`
  (`200 < 200`) is **false for every curve**, so the loop never actually enters any `decayCurve`-dependent branch — it
  hits the single curve-agnostic `else` line six times. The `for (curve in AdsrCurve.entries)` loop can never
  distinguish a curve-specific decay-boundary bug (e.g. one curve's `omp<0.5`-branch split being off-by-one at the
  boundary) because that code is structurally unreachable from this test's chosen position. The name "all curves arrive
  at sustain" implies per-curve boundary coverage that isn't actually exercised.

### 9. "release midpoint via calculateControlRateEnvelope — Square = 0.25 of startLevel"

- CLAIM: `calculateControlRateEnvelope`'s release-phase shape math (a separate code path from `envelopeLevelAtPosition`)
  correctly computes `startLevel * shape(1-p)` at the midpoint for a Square release.
- MUTATION: `audio_be/src/commonMain/kotlin/voices/strip/EnvelopeCalc.kt:40`
    - OLD: `            AdsrCurve.Square -> omp * omp`
    - NEW: `            AdsrCurve.Square -> omp * omp * omp`
- SUSPICION: LIKELY-RED — `1.0 * 0.5³ = 0.125 ≠ 0.25 ± 0.001`.

### 10. "release endpoint reaches 0 for all curves"

- CLAIM: At the exact end of the release window, `calculateControlRateEnvelope` returns `0.0` for every `AdsrCurve`
  variant (all six shape formulas individually satisfy `g(0)=0` at `omp=0`).
- MUTATION: `audio_be/src/commonMain/kotlin/voices/strip/EnvelopeCalc.kt:43`
    - OLD: `            AdsrCurve.InvSquare -> omp * (2.0 - omp)`
    - NEW: `            AdsrCurve.InvSquare -> (2.0 - omp)`
- SUSPICION: LIKELY-RED — unlike claim #8, this loop genuinely reaches every curve's formula (`omp=0` is reachable
  through the real `when` for all six branches, since it's driven by `p` reaching `1.0`, not a structurally-skipped
  position). Dropping the `omp *` multiply leaves `2.0 - 0 = 2.0 ≠ 0.0 ± 0.001` for the `InvSquare` iteration, failing
  the loop. This test is a genuine, non-vacuous per-curve endpoint check — the opposite of claim #8.

---

## io.peekandpoke.klang.audio_be.voices.EnvelopeDeclickSpec

Production files it exercises: `EnvelopeRenderer.kt` (the one-pole de-click smoother specifically), `AdsrCurveMath.kt`
(`envDeclickCoeff`, `ENV_DECLICK_SECONDS`, `adsrExpShape` for the exponential-curve case), `Voice.kt`,
`VoiceTestHelpers.kt` (via `TestIgnitors.constant` — a DC=1.0 carrier so the rendered buffer equals the envelope gain
directly).

### 1. "de-click bounds the gain slew at a hard cutoff (releaseFrames=0)"

- CLAIM: For a `releaseFrames=0` gate-off (which, unsmoothed, would drop 1.0→0.0 in a single sample), the actual
  per-sample gain change (slew) never exceeds 0.1, and the fade still fully converges to near-silence within the
  412-frame post-gate window.
- MUTATION: `audio_be/src/commonMain/kotlin/voices/strip/filter/EnvelopeRenderer.kt:134`
    - OLD: `            smoothed += declick * (currentEnv - smoothed)`
    - NEW: `            smoothed = currentEnv`
- SUSPICION: LIKELY-RED — bypassing the one-pole restores the literal 1-sample cutoff the smoother exists to prevent:
  `maxSlew` becomes `1.0` (the full 1.0→0.0 step), failing `(maxSlew(ctx.voiceBuffer, 512) < 0.1) shouldBe true`
  decisively. This is exactly the behaviour the test's docstring describes as the pre-fix bug, so the mutation is a
  faithful "revert the fix."

### 2. "de-click also bounds the slew across an exp attack→decay→release voice"

- CLAIM: The same slew bound holds even for the steepest realistic segment joins (Exponential curves at the attack→decay
  and decay→release corners), not just the pathological `releaseFrames=0` case.
- MUTATION: same mutation as claim #1 (`EnvelopeRenderer.kt:134`, bypass the one-pole:
  `smoothed += declick * (currentEnv - smoothed)` → `smoothed = currentEnv`).
- SUSPICION: LIKELY-RED — with the smoother bypassed, `currentEnv` itself has a genuine slope discontinuity at the
  attack→decay corner (Exponential attack ends near its steepest instantaneous rate, decay begins at its steepest
  instantaneous fall rate); `maxSlew` over 600 samples picks up that corner directly and should exceed `0.1`. Slightly
  less certain in magnitude than claim #1 (this test doesn't force a literal 1.0→0.0 single-sample step, just a slope
  kink), but the docstring explicitly states the manual 2nd-difference analysis found the corner ratio to be `525→21` at
  the standard 0.5ms declick, i.e. the un-declicked corner is ~25x steeper — comfortably over the `0.1` bound for a
  `[0,1]`-normalized gain signal.

---

## Notes

**Coverage holes (no test in these 3 specs guards this production behaviour):**

1. `Voice.Envelope.of(adsr: AdsrDef.Resolved, sampleRate)` (`Voice.kt:189-197`) — the seconds→frames conversion factory
   used by real voice construction — is never called by any test here; all three specs build `Voice.Envelope` directly
   via the primary constructor with frame counts already in hand. A bug in `adsr.attack * sampleRate` etc. would not be
   caught by this trio.
2. `env.releaseStarted = false` reset (`EnvelopeRenderer.kt:92`) — the "re-enter attack/decay/sustain after a release
   had started" reset path has no test; no spec here retriggers an envelope after release.
3. `SCurve` and `InvSquare` curve *shapes* are only ever exercised at the endpoints (claim #10 of `EnvelopeShapeTest`,
   and the vacuous boundary claim #8) — no test checks their interior midpoint value (the `if (p < 0.5)` branch split of
   `SCurve`, or the exact `p*(2-p)` value of `InvSquare`, at an actual midpoint). A bug in the `SCurve` branch boundary
   (e.g. `<` vs `<=` at `p=0.5`) would slip through everywhere.
4. `Exponential` curve shape (`adsrExpShape`/`adsrExpNorm`/`ADSR_EXP_K`, the *default* curve for every `Voice.Envelope`
   field when unspecified) has zero golden-value coverage in `EnvelopeShapeTest` — only qualitative slew-bound coverage
   in `EnvelopeDeclickSpec`. A bug in the normalization (e.g. `adsrExpNorm` off by a constant, breaking `g(1)=1`) would
   not be caught by any of these 3 specs.
5. The `expK`/`declickSeconds` per-engine parameterization of `EnvelopeRenderer` (constructor params,
   `EnvelopeRenderer.kt:35-36`, driven by `StageDsl.Vca`) is always exercised at its default values
   (`PipelinePreset.Modern`, `expK=3.0`, `declickSeconds=0.001`) — no test constructs a `Voice`/`EnvelopeRenderer` with
   a non-default engine character, so the "per-engine VCA character" feature itself (the whole point of those
   constructor params, per the class doc) is untested here.
6. `calculateControlRateEnvelope`'s **pre-gate** branch (`EnvelopeCalc.kt:48`, `envelopeLevelAtPosition(env, absPos)`
   when `absPos < gateEndPos`) is never reached by any `EnvelopeShapeTest` call — both calls that use
   `calculateControlRateEnvelope` pass `gateEndFrame=0`, i.e. always already in release. The function's own
   branch-selection logic (`if (absPos >= gateEndPos)`) and its "not yet gated" path are entirely untested through this
   entry point (though `envelopeLevelAtPosition` itself is well covered directly).
7. The final `.coerceIn(0.0, 1.0)` clamp on `calculateControlRateEnvelope`'s return (`EnvelopeCalc.kt:51`) has no test
   driving an out-of-range pre-clamp value — same toothless-clamp pattern as `EnvelopeTest` claim #10.

**Test names that promise more than their assertions check:**

- `EnvelopeTest` claim #8 "full ADSR cycle works correctly" — decay phase is never sampled (see claim #8 above).
- `EnvelopeTest` claim #10 "envelope clamps negative values to zero" — the configured scenario can never produce a
  negative pre-clamp value; the clamp line is provably unreachable-as-negative here (see claim #10 above).
- `EnvelopeShapeTest` claim #8 "decay/sustain boundary — all curves arrive at sustain" — the per-curve loop is vacuous;
  the position tested never enters any curve-specific branch (see claim #8 above).

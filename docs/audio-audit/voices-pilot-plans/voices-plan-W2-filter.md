# Mutation-Check Plan — W2: Filter render strip (FilterModulationTest, VoiceFactoryFilterOrderSpec)

## io.peekandpoke.klang.audio_be.voices.FilterModulationTest

Production files it exercises:

- `audio_be/src/commonMain/kotlin/voices/Voice.kt` (`render()` lifecycle gate, `FilterModulator`)
- `audio_be/src/commonMain/kotlin/voices/strip/filter/FilterModRenderer.kt` (the modulation math + control-rate call)
- `audio_be/src/commonMain/kotlin/voices/strip/EnvelopeCalc.kt` (`calculateControlRateEnvelope` /
  `envelopeLevelAtPosition`)
- `audio_be/src/commonMain/kotlin/voices/strip/filter/FilterPipelineBuilder.kt` (`StageDsl.FilterMod` guard)
- `audio_be/src/commonMain/kotlin/voices/strip/filter/AudioFilterRenderer.kt` (main-filter `process()`-only wrapper —
  confirms it never touches cutoff)
- `audio_be/src/commonTest/kotlin/voices/VoiceTestHelpers.kt` (`createSynthVoice`/`createSampleVoice` — wires
  `PipelinePreset.Modern.dsl` through `buildFilterPipeline`)

Setup-density note: all 10 cases route through the same ~15-line `createSynthVoice(...)`/`createSampleVoice(...)` call
with ~20 named args, repeated near-verbatim each time (this is why the file is 642 lines for 10 cases) — it is
boilerplate, not hidden logic. The only things that vary case-to-case are: the `FilterModulator`'s
envelope/depth/baseCutoff, the voice's own timing (`startFrame`/`gateEndFrame`/`endFrame`), and the `blockStart` passed
to `render()`. None of the 10 cases set `drift` on a `FilterModulator` (it defaults to `null`), so `FilterModRenderer`'s
`driftMul` branch (`drift != null && drift.active`) is never exercised by this file — see Notes.

### 1. "filter without modulator is not modified"

- CLAIM: When a voice is built with an empty `filterModulators` list, rendering it never calls `setCutoff` on any
  filter — the filter-modulation stage is a strict no-op in the absence of modulators.
- MUTATION: `audio_be/src/commonMain/kotlin/voices/strip/filter/FilterPipelineBuilder.kt:43`
    - OLD:
      ```
              StageDsl.FilterMod ->
                  if (modulators.isNotEmpty()) {
                      add(FilterModRenderer(modulators, startFrame, gateEndFrame))
                  }
      ```
    - NEW:
      ```
              StageDsl.FilterMod ->
                  add(FilterModRenderer(modulators, startFrame, gateEndFrame))
      ```
- SUSPICION: SUSPECT — this mutation is provably inert for this test. `FilterModRenderer.render()` is
  `for (mod in modulators) { ... }`; with `modulators = emptyList()`, the loop body never runs whether or not the
  renderer is added to the pipeline. The only other place `setCutoff` could be called is `AudioFilterRenderer.render()`,
  which calls `filter.process(...)` only (verified — no `Tunable` cast, no `setCutoff` anywhere in that file). So the
  "no calls" guarantee this test asserts is actually guaranteed by the *shape of the input data* (an empty list), not by
  any single removable line of production code — I could not construct a one-line production mutation that flips this
  test red. This is a structural/tautological assertion in disguise.

### 2. "filter with modulator - envelope at attack peak"

- CLAIM: At the exact frame where the modulator's attack envelope reaches its peak (envelope value = 1.0), the filter's
  cutoff is set to `baseCutoff × (1 + depth × 1.0)`, called exactly once for that render.
- MUTATION: `audio_be/src/commonMain/kotlin/voices/strip/filter/FilterModRenderer.kt:33`
    - OLD: `            val newCutoff = mod.baseCutoff * (1.0 + mod.depth * envValue) * driftMul`
    - NEW: `            val newCutoff = mod.baseCutoff * (1.0 + mod.depth + envValue) * driftMul`
- SUSPICION: LIKELY-RED — with `depth=1.0, envValue=1.0`: original `1000×(1+1×1)=2000`; mutated `1000×(1+1+1)=3000`.
  Assertion tolerance is `plusOrMinus 0.1`, so `3000` vs. expected `2000` fails clearly.

### 3. "filter with modulator - envelope at start (attack beginning)"

- CLAIM: At the very first frame of a (long, 1000-frame) attack ramp, the envelope value is exactly 0.0, so the
  modulated cutoff equals `baseCutoff` unchanged.
- MUTATION: `audio_be/src/commonMain/kotlin/voices/strip/EnvelopeCalc.kt:58`
    - OLD: `        val p = absPos * attRate`
    - NEW: `        val p = (absPos + 1) * attRate`
- SUSPICION: LIKELY-RED — with `absPos=0, attackFrames=1000`, original `p=0` → exponential shape `g(0)=0` exactly →
  cutoff `1000.0`. Mutated `p=0.001` → `g(0.001)=(exp(3×0.001)-1)/(exp(3)-1)≈1.57e-4` → cutoff `≈1000.157`, which is
  outside the `plusOrMinus 0.1` tolerance against the expected `1000.0`.

### 4. "filter with modulator - envelope at sustain"

- CLAIM: Once the modulator's envelope is past attack+decay (in the flat sustain region), the envelope value equals
  `sustainLevel` and the cutoff reflects that constant value: `baseCutoff × (1 + depth × sustainLevel)`.
- MUTATION: `audio_be/src/commonMain/kotlin/voices/strip/EnvelopeCalc.kt:85`
    - OLD: `    else -> env.sustainLevel`
    - NEW: `    else -> env.sustainLevel * 0.5`
- SUSPICION: LIKELY-RED — this test's block position (300) lands in the `else` (sustain) branch of
  `envelopeLevelAtPosition` (attack=100, decay=100 → attDecFrames=200 < 300). Original: `sustainLevel=0.5` →
  `envValue=0.5` → cutoff `1000×(1+0.5×0.5)=1250`. Mutated: effective sustain `0.25` → cutoff `1000×(1+0.5×0.25)=1125`,
  vs. expected `1250 ± 0.1` → fails.

### 5. "multiple modulators apply independently"

- CLAIM: Two independent `FilterModulator`s attached to two different filters are each updated with their own
  `baseCutoff`/`depth`/envelope, and neither modulator's cutoff computation leaks into the other's.
- MUTATION: same mutation as claim #2 (`FilterModRenderer.kt:33`, `mod.depth * envValue` → `mod.depth + envValue`).
- SUSPICION: LIKELY-RED — filter 1 (`baseCutoff=1000, depth=1.0, env=1.0`): original `2000`, mutated `1000×(1+1+1)=3000`
  vs. expected `2000±0.1`. Filter 2 (`baseCutoff=2000, depth=0.5, env=1.0`): original `3000`, mutated
  `2000×(1+0.5+1)=5000` vs. expected `3000±0.1`. Both assertions fail.

### 6. "modulation works with sample signal too"

- CLAIM: Filter modulation is wired identically regardless of which `Ignitor` drives the voice — a sample-backed voice
  (`SampleIgnitor` via `createSampleVoice`) gets the same control-rate cutoff update as an oscillator voice.
- MUTATION: same mutation as claim #2 (`FilterModRenderer.kt:33`).
- SUSPICION: LIKELY-RED — `baseCutoff=500, depth=1.0, env=1.0`: original `500×(1+1×1)=1000`; mutated `500×(1+1+1)=1500`
  vs. expected `1000±0.1` → fails. (Note: this test only proves the *filter pipeline* is signal-agnostic; it does not
  exercise anything specific to `SampleIgnitor` itself beyond using it as the signal — the modulation math is identical
  to test #2's path.)

### 7. "modulation called once per render (control rate)"

- CLAIM: `setCutoff` is called exactly once per `Voice.render()` call, not once per sample in the block (control-rate,
  not audio-rate) — and this holds across repeated `render()` calls on the same voice.
- MUTATION: `audio_be/src/commonMain/kotlin/voices/strip/filter/FilterModRenderer.kt:33-34`
    - OLD:
      ```
                  val newCutoff = mod.baseCutoff * (1.0 + mod.depth * envValue) * driftMul
                  mod.filter.setCutoff(newCutoff)
      ```
    - NEW:
      ```
                  val newCutoff = mod.baseCutoff * (1.0 + mod.depth * envValue) * driftMul
                  mod.filter.setCutoff(newCutoff)
                  mod.filter.setCutoff(newCutoff)
      ```
- SUSPICION: LIKELY-RED — `cutoffHistory.size` becomes `2` on both the first and the repeated render, vs. expected `1`
  each time. Caveat: because `FilterModRenderer` is a single `BlockRenderer` invoked once per `Voice.render()` call by
  construction (see `Voice.render()`'s `for (renderer in pipeline) { renderer.render(blockCtx) }`), the "once per
  render" guarantee is largely structural — the only realistic way to break it is to literally duplicate/loop the call,
  as above. There's no plausible *existing*-code operator/constant mutation (of the kind PIT-style tools generate) that
  would make this fire twice; it mainly guards against a future refactor that pushes the modulation into a per-sample
  loop.

### 8. "voice starting mid-block handles envelope correctly"

- CLAIM: When a render block starts before the voice's `startFrame` (voice starts mid-block), the envelope position used
  for filter modulation is clamped to the voice's own start (position 0), not computed from the raw (earlier) block
  start — i.e., it must not go negative.
- MUTATION: `audio_be/src/commonMain/kotlin/voices/strip/EnvelopeCalc.kt:28`
    - OLD: `    val currentFrame = maxOf(blockStart, startFrame)`
    - NEW: `    val currentFrame = blockStart`
- SUSPICION: SUSPECT — I traced this through by hand and the mutation is inert for this test's chosen parameters. With
  the clamp removed, `currentFrame=blockStart=50`, `absPos=50-100=-50`. That lands in the *attack* branch of
  `envelopeLevelAtPosition` (`-50 < attackFrames=100`), giving `p=-50×attRate=-0.5`. The attack curve is
  `AdsrCurve.Exponential` (the modulator's envelope default), and `adsrExpShape` is monotonic increasing with `g(0)=0`,
  so `g(-0.5)≈-0.041 < 0`. But `calculateControlRateEnvelope`'s final line is `return envValue.coerceIn(0.0, 1.0)` — a
  floor clamp that forces any negative result back to exactly `0.0`. So `envValue = 0.0` either way, and the asserted
  cutoff (`1000.0 ± 0.1`) is identical whether or not the `maxOf` clamp exists. This holds generally for every built-in
  curve shape at these parameters (all are monotonic increasing with `shape(0)=0`, so `shape(negative p) ≤ 0`, which the
  trailing `coerceIn` always floors to 0). The test's own numbers can never distinguish "no clamp" from "clamped" — it
  only would if the block started far enough before voice-start to land in the *release* branch's arithmetic instead
  (not the case here), or if `coerceIn`'s lower bound were also removed in the same mutation.

### 9. "envelope at release phase"

- CLAIM: During the modulator envelope's release phase, the cutoff interpolates linearly from
  `baseCutoff×(1+depth×sustainLevel)` at release-start down to `baseCutoff` at release-end, and stays at `baseCutoff`
  for any block after the release has fully finished (best-effort, no re-triggering).
- MUTATION: `audio_be/src/commonMain/kotlin/voices/strip/EnvelopeCalc.kt:36-37`
    - OLD:
      ```
          val p = (relPos / relDenom).coerceAtMost(1.0)
          val omp = 1.0 - p
      ```
    - NEW:
      ```
          val p = (relPos / relDenom).coerceAtMost(1.0)
          val omp = p
      ```
- SUSPICION: LIKELY-RED — this inverts the release ramp direction. At release-start (`relPos=0, p=0`): original
  `omp=1` → `envValue=sustainLevel×1=0.5` → cutoff `1500`; mutated `omp=0` → `envValue=0` → cutoff `1000`, vs. expected
  `1500±0.1` — fails on the very first assertion in the test, well before the later (halfway/end/past-release) checks
  even run. (The halfway check at `p=0.5` happens to be numerically symmetric under this particular flip — `omp` is
  `0.5` either way — so that one assertion alone would not have caught it; the release-start and release-end assertions
  do.)

### 10. "modulator envelope does NOT keep voice alive past its endFrame"

- CLAIM: A voice's lifetime is governed solely by its own `endFrame` (amp-envelope-derived); an attached filter
  modulator with a much longer release does not extend the voice's active window — once `blockStart >= endFrame`, the
  whole render pipeline (including `FilterModRenderer`) is skipped, so `setCutoff` cannot be called again after that
  point.
- MUTATION: `audio_be/src/commonMain/kotlin/voices/Voice.kt:102`
    - OLD: `        if (ctx.blockStart >= endFrame) return false`
    - NEW: `        if (ctx.blockStart >= endFrame + 1000) return false`
- SUSPICION: LIKELY-RED — voice has `endFrame=300`. First assertion (`render(blockStart=200)` → expect
  `cutoffHistory.size==1`) is unaffected by the mutation (`200 >= 1300` is false either way, pipeline runs normally).
  But the test's actual claim is checked next: `render(blockStart=500)` should now be a no-op (voice inactive) — with
  the mutation `500 >= 1300` is false, so the pipeline still runs and `FilterModRenderer` still calls `setCutoff` (the
  modulator's own envelope, at `absPos=500`, is still inside its 4000-frame release and hasn't reached 0), so
  `cutoffHistory.size` becomes `1` instead of the expected `0` → fails. This is a precisely targeted mutation: it
  simulates exactly the regression the test's docstring warns about ("if a future change extends voice lifetime to cover
  modulator releases") without corrupting the earlier, unrelated "still active" assertion.

---

## io.peekandpoke.klang.audio_be.voices.VoiceFactoryFilterOrderSpec

Production files it exercises:

- `audio_be/src/commonMain/kotlin/voices/VoiceFactory.kt` (`makeVoice()` → `voiceFilterDefs.map { it.toFilter(...) }` →
  `filters.combine()`)
- `audio_be/src/commonMain/kotlin/filters/AudioFilter.kt` (`List<AudioFilter>.combine()`)
- `audio_be/src/commonMain/kotlin/filters/ChainAudioFilter.kt` (`filters` property exposed `internal` for this test)
- `audio_be/src/commonMain/kotlin/filters/LowPassHighPassFilters.kt` (`SvfLPF`/`SvfHPF`/`SvfBPF`, `createLPF`/
  `createHPF`/`createBPF`)

### 1. "VoiceFactory bakes the chain in the exact order received — it does NOT reorder"

- CLAIM: Given `FilterDefs` in a deliberately non-canonical order (LowPass, HighPass, BandPass),
  `VoiceFactory.makeVoice()` bakes the main filter chain in that exact same order — it performs no sorting/reordering of
  filter defs before instantiating and combining them.
- MUTATION: `audio_be/src/commonMain/kotlin/voices/VoiceFactory.kt:109`
    - OLD: `        val filters = voiceFilterDefs.map { it.toFilter(analog, filterStage) }`
    - NEW: `        val filters = voiceFilterDefs.map { it.toFilter(analog, filterStage) }.reversed()`
- SUSPICION: LIKELY-RED — input order is `[LowPass, HighPass, BandPass]`; reversed bake order is
  `[BandPass, HighPass, LowPass]`. `chain[0]` is asserted to be `SvfLPF` but would actually be `SvfBPF` → fails
  immediately.

### 2. "VoiceFactory preserves an already-canonical order too"

- CLAIM: Given `FilterDefs` already in the "canonical" order (HighPass before LowPass), `VoiceFactory` still doesn't
  reorder them — i.e., it isn't silently relying on (or reintroducing) a canonical sort that happens to produce the same
  result.
- MUTATION: `audio_be/src/commonMain/kotlin/voices/VoiceFactory.kt:109`
    - OLD: `        val filters = voiceFilterDefs.map { it.toFilter(analog, filterStage) }`
    - NEW:
      `        val filters = voiceFilterDefs.sortedBy { if (it is FilterDef.HighPass) 0 else 1 }.map { it.toFilter(analog, filterStage) }`
- SUSPICION: SUSPECT — this is the *exact* regression the class's docstring calls out ("If anyone re-adds a sort inside
  VoiceFactory, these tests fail"), reintroducing a highpass-first canonical sort. Traced by hand: this test's input is
  already `[HighPass, LowPass]`. `sortedBy` is stable and HighPass already ranks first, so the sort is a complete no-op
  on this specific input — `chain[0]==SvfHPF`, `chain[1]==SvfLPF` still hold, and the test stays GREEN even with the
  canonical sort reinstated. The only reason the suite catches this regression at all is test #1's deliberately *non*
  -canonical input ordering — test #2 adds no additional mutation-kill power against the realistic regression, only
  against a cruder bug like a full list reversal (which test #2's own assertions would catch, since
  `[HighPass, LowPass].reversed() = [LowPass, HighPass]` does change the observed order). In short: test #2's name
  promises to guard the "no silent reordering" contract in general, but for the one regression the surrounding code
  explicitly worries about, it is redundant with test #1 at best, and blind to it in isolation.

---

## Notes

- **Coverage hole — `AnalogDrift` / `driftMul` path in `FilterModRenderer`:** none of `FilterModulationTest`'s 10 cases
  ever construct a `Voice.FilterModulator` with a non-null `drift`. The branch
  `val driftMul = if (drift != null && drift.active) drift.nextMultiplier() else 1.0` (`FilterModRenderer.kt:32`) and
  the entire `AnalogDrift.nextMultiplier()` per-block advance are untested by this file. Given `drift` is the thing set
  when `analog > 0` (per `VoiceFactory.toModulator`), and other memory notes flag analog-drift tuning as
  sensitive/by-ear-calibrated, this seems like a real gap for this spec file specifically (it may be covered elsewhere,
  e.g. an `AnalogDrift`-focused spec, but not here).

- **Coverage hole — `VoiceFactory.toModulator()` is entirely untested by these two files.** `FilterModulationTest`
  constructs `Voice.FilterModulator` directly, bypassing `VoiceFactory` altogether, so none of the following
  `VoiceFactory.kt` logic (lines ~384–451) is exercised by either spec in this plan:
    - the early-return when a filter isn't `AudioFilter.Tunable` (e.g. `Formant`),
    - the early-return when a filter def has neither an envelope nor `analog`-driven drift
      (`envData == null && drift == null`),
    - the "degenerate depth=0 envelope" construction used when only drift (no envelope) is active,
    - resolving `FilterDef`'s own envelope (`envData.resolve()`) into frame counts.

  `VoiceFactoryFilterOrderSpec` does exercise `VoiceFactory.makeVoice()`, but its `FilterDef`s (`LowPass`/`HighPass`/
  `BandPass` with no `envelope`) never populate `filterModulators`, so it doesn't touch `toModulator()` either — the two
  specs are complementary but leave `toModulator()` itself as a blind spot.

- **Coverage hole — `VoiceFactory`'s Body/Formant exclusion from the per-voice chain is untested by
  `VoiceFactoryFilterOrderSpec`.** `VoiceFactory.kt:108` filters out `FilterDef.Body`/`FilterDef.Formant` before baking
  (`voiceFilterDefs = data.filters.filters.filter { it !is FilterDef.Body && it !is FilterDef.Formant }`) — routing them
  to the orbit-level Katalyst instead. Neither test in this spec includes a `Body`/`Formant` def alongside LP/HP/BP to
  confirm they're excluded from the baked chain (and still appear on `Voice.body`/`Voice.vowel`). Might be covered by a
  different spec (e.g. a body/vowel-specific one) but not here.

- **Test #7 ("modulation called once per render") is largely structure-guaranteed, not logic-guaranteed** — see its
  SUSPICION entry. It's a legitimate regression guard against a future refactor (e.g. someone moving the modulation
  update into a per-sample loop for "smoother" cutoff automation) but has no realistic single-operator/constant mutation
  of *current* code that breaks it other than literally duplicating the call.

- **Test #1 of `FilterModulationTest` ("filter without modulator is not modified") and test #8 ("voice starting
  mid-block...") are the two strongest SUSPECT flags in this file** — both traced by hand to be either fully guaranteed
  by data shape (test #1) or masked by a downstream `coerceIn(0.0, 1.0)` floor clamp that makes the specific numbers
  chosen unable to distinguish clamped from unclamped behavior (test #8). Test #8's name promises to test the
  `maxOf(blockStart, startFrame)` clamp specifically, but as written it cannot falsify the clamp's absence for any
  monotonic attack-curve shape with `shape(0)=0` — which is all of them.

- **Test #2 of `VoiceFactoryFilterOrderSpec` is the other notable SUSPECT** — see its entry. Its name ("preserves an
  already-canonical order too") suggests it adds coverage beyond test #1, but for the one realistic regression the class
  docstring warns about (reintroducing the highpass-first canonical sort), it provides none — that specific mutation
  sails through as a no-op on this test's already-sorted input.

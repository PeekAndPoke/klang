# audio_be Module Review Findings

Full module review conducted 2026-03-24 by three perspectives:
Software Engineer, QA Engineer, Audio/DSP Engineer.

## Overall Verdict

Solid, production-quality audio engine with clean architecture, correct DSP fundamentals,
and good pipeline-level test coverage. Two systemic issues: legacy/exciter code duplication
and `Long` usage in per-sample strip renderer loops (Kotlin/JS boxing penalty).

## Signal Flow (correct)

```
[Strip: Pitch → Excite → Filter → Send] → [Bus: Delay → Reverb → Phaser → Compressor] → Ducking → [Master: Limiter → Clip → Int16]
```

---

## Issue Categories

### A. ~~Long in Per-Sample Loops (Kotlin/JS Performance)~~ FIXED

**Severity: HIGH — affects every voice on every block on JS target** → **FIXED 2026-03-24**

`BlockContext` stores `startFrame`, `endFrame`, `gateEndFrame`, `blockStart` as `Long`.
These feed into per-sample arithmetic in:

- `EnvelopeRenderer` (absPos incremented per sample)
- `AccelerateRenderer`
- `PitchEnvelopeRenderer`
- `FmRenderer`
- `FilterModRenderer`

On Kotlin/JS, every `Long` operation in those loops boxes. `ExciteContext` correctly uses `Int`
for voice-relative frames. The strip renderers should adopt the same approach: compute
voice-relative `Int` offsets at the block boundary.

**Files:**

- `voices/strip/BlockContext.kt` — Long fields
- `voices/strip/filter/EnvelopeRenderer.kt` — per-sample Long arithmetic
- `voices/strip/pitch/AccelerateRenderer.kt` — per-sample Long arithmetic
- `voices/strip/pitch/PitchEnvelopeRenderer.kt` — per-sample Long arithmetic
- `voices/strip/pitch/FmRenderer.kt` — per-block Long arithmetic
- `voices/strip/filter/FilterModRenderer.kt` — per-block Long arithmetic
- `voices/Voice.kt` — `RenderContext.blockStart: Long`
- `KlangAudioRenderer.kt` — `renderBlock(cursorFrame: Long)`

### B. Legacy / Exciter Code Duplication

**Severity: MEDIUM — doubles maintenance surface, inconsistent quality**

The `filters/` package (class-based `AudioFilter`) and `exciter/` combinators implement
the same DSP twice. The exciter versions are consistently better:

| DSP                | Legacy (filters/)            | Exciter (exciter/)            | Difference                           |
|--------------------|------------------------------|-------------------------------|--------------------------------------|
| SVF LP/HP/BP/Notch | `LowPassHighPassFilters.kt`  | `ExciterFilters.kt svf()`     | Exciter flushes denormals            |
| Distortion         | `DistortionFilter.kt`        | `ExciterEffects.kt distort()` | Exciter flushes DC blocker denormals |
| BitCrush           | `BitCrushFilter.kt`          | `ExciterEffects.kt crush()`   | Equivalent                           |
| SampleRateReducer  | `SampleRateReducerFilter.kt` | `ExciterEffects.kt coarse()`  | Equivalent                           |
| Phaser             | `PhaserFilter.kt`            | `ExciterEffects.kt phaser()`  | Exciter flushes denormals            |
| Tremolo            | `TremoloFilter.kt`           | `ExciterEffects.kt tremolo()` | Equivalent                           |
| Formant            | `FormantFilter.kt`           | `ExciterFilters.kt formant()` | Legacy allocates per block           |
| ADSR               | `EnvelopeRenderer.kt`        | `ExciterEnvelopes.kt adsr()`  | Different architecture               |

Also duplicated:

- `DENORMAL_THRESHOLD` / `flushDenormal()` — defined identically in `ExciterEffects.kt` and `ExciterFilters.kt`
- `ResolvedShape` / shape resolution — duplicated between `DistortionFilter.kt` and `ExciterEffects.kt`

### C. DSP Quality Issues

#### ~~C1. Pulze oscillator not anti-aliased~~ FIXED

- **File:** `exciter/Exciters.kt` (pulze function)
- **Severity:** 🟡
- Hard-edged variable-duty pulse with no PolyBLEP, unlike `square()` which applies dual BLEP.
  Audible aliasing at high frequencies. Should apply PolyBLEP at both transitions (0 and duty).

#### ~~C2. Freeverb comb tunings not sample-rate scaled~~ FIXED

- **File:** `effects/Reverb.kt` lines 36-37
- **Severity:** 🟡
- Hardcoded for 44.1kHz. At 48kHz (~8.8% shift), room sounds slightly smaller and
  coloration frequencies shift. Standard fix: `tuning[i] * sampleRate / 44100`.

#### ~~C3. Legacy SVF filters lack denormal flushing~~ FIXED

- **File:** `filters/LowPassHighPassFilters.kt`
- **Severity:** 🟡
- `ic1eq`/`ic2eq` not flushed. Can cause CPU spikes when processing near-silence.
  Exciter versions do flush. Same gap in `PhaserFilter.filterState` and `DistortionFilter.dcBlockY1`.

#### ~~C4. FormantFilter allocates per block~~ FIXED

- **File:** `filters/FormantFilter.kt` line 47
- **Severity:** 🟡
- `FloatArray(length)` allocated per band per block in `process()`. GC pressure on audio thread.
  Exciter version uses `ScratchBuffers` correctly.

#### ~~C5. AccelerateRenderer per-sample `pow()`~~ FIXED (AccelerateRenderer only)

- **Files:** `voices/strip/pitch/AccelerateRenderer.kt`
- **Severity:** 🟢
- Exciter versions already optimize with multiplicative stepping. Strip renderers could adopt same.

#### ~~C6. VibratoRenderer phase not wrapped~~ FIXED

- **File:** `voices/strip/pitch/VibratoRenderer.kt` line 40
- **Severity:** 🟢
- Phase accumulates unbounded, loses `sin()` precision over long voices. Exciter version wraps.

#### C7. Triangle oscillator not band-limited

- **File:** `exciter/Exciters.kt` (triangle function)
- **Severity:** 🟢
- Aliasing at -12dB/oct from derivative discontinuity. Acceptable for most musical use.

#### ~~C8. Ducking "attackSeconds" name is misleading~~ DOCUMENTED

- **File:** `effects/Ducking.kt`
- **Severity:** 🟢
- Duck-down is instantaneous; only recovery is smoothed. This is a release parameter, not attack.

#### ~~C9. Reverb.mix is val = 1.0~~ FIXED

- **File:** `effects/Reverb.kt` line 64
- **Severity:** 🟢
- Public `val` that's effectively a constant. Either make `var` or make private.

### D. VoiceScheduler Complexity

- **File:** `voices/VoiceScheduler.kt` (~1000 lines)
- **Severity:** 🟡
- Mixes scheduling, sample management, solo/mute ramping, voice factory, and diagnostics.
- `makeVoice` + `buildVoice` (~400 lines) could be a separate `VoiceFactory`.
- Sample voice `voiceDurationFrames` computed differently from oscillator voice (line 881 vs 759).

### E. Orbit "Last Writer Wins"

- **File:** `orbits/Orbits.kt` line 113-115
- **Severity:** 🟢 (by design, matches strudel convention)
- `updateFromVoice()` called for every voice on the orbit. Last voice's effect params win.
  Can cause parameter "flickering" when overlapping voices have different settings.

---

## Test Coverage Gaps

Ranked by risk:

| Priority | Area                                                                                       | Status              | Risk                           |
|----------|--------------------------------------------------------------------------------------------|---------------------|--------------------------------|
| 1        | **LowPassHighPassFilters** (SVF, one-pole)                                                 | ❌ UNTESTED          | HIGH — used on every voice     |
| 2        | **Exciter combinators** (svf, adsr, fm, vibrato, distort, crush, phaser, tremolo, formant) | ❌ UNTESTED          | HIGH — 16 combinators, 0 tests |
| 3        | **KlangAudioRenderer** (limiter + clip + interleave)                                       | ❌ UNTESTED          | HIGH — output stage            |
| 4        | **ScratchBuffers** (pool/stack semantics)                                                  | ❌ UNTESTED          | MEDIUM                         |
| 5        | **DelayLine** (interpolation, feedback, chunking)                                          | ⚠️ Indirect only    | MEDIUM                         |
| 6        | **BitCrush/SampleRateReducer/Tremolo/PhaserFilter**                                        | ❌ UNTESTED          | MEDIUM                         |
| 7        | **VoiceScheduler** (scheduling, cut groups, solo/mute)                                     | ⚠️ Only diagnostics | MEDIUM                         |
| 8        | **AnalogDrift**                                                                            | ❌ UNTESTED          | LOW                            |
| 9        | **FormantFilter**                                                                          | ❌ UNTESTED          | LOW                            |

### Test Quality Issues

- Pitch modulation tests have no output assertions (smoke tests only)
- FM tests don't verify modulation actually changes output
- `DuckingSpec` "Attack time" test reads from unprocessed array copies (passes by accident)
- Commented-out tests in `SampleVoiceRenderTest`

### Top 5 Tests to Add

1. **LowPassHighPassFilters** — frequency response, setCutoff, edge cases, all SVF modes
2. **Exciter combinators** — each combinator in isolation with behavioral assertions
3. **KlangAudioRenderer** — clipping boundaries, interleaving, limiter compression
4. **ScratchBuffers** — acquire/release stack, nesting, pool growth, exception safety
5. **DelayLine** — impulse timing, interpolation, feedback decay, hasTail, wrap-around

---

## Praised Design Decisions

All three reviewers converged on these as well-engineered:

- PolyBLEP anti-aliasing on saw/square/ramp
- Cytomic SVF filter topology
- Freeverb structure-of-arrays optimization + anti-denormal
- Karplus-Strong with interpolated delay, LP feedback, allpass stiffness, pick position
- FM-as-phase-modulation (correct, matches classic synth behavior)
- Equal-power cos/sin panning law
- ScratchBuffers allocation-free pool design
- Block-based delay line chunking with safety limiter
- Two-phase tail detection for orbit cleanup
- Master limiter at -1dB before hard clip
- ExciteContext Int-only design (avoids Kotlin/JS Long boxing)
- Pink noise via Paul Kellet cascade
- Analog drift via Perlin noise

---

## Recommended Action Order

1. ~~**Long → Int migration in strip renderers**~~ **DONE (2026-03-24)**
2. ~~**Pulze PolyBLEP**~~ **DONE (2026-03-24)**
3. ~~**Freeverb sample-rate scaling**~~ **DONE (2026-03-24)**
4. ~~**Denormal flushing in legacy filters**~~ **DONE (2026-03-24)**
5. **Test coverage: LowPassHighPassFilters + KlangAudioRenderer** (risk reduction)
6. **Unify legacy/exciter duplication** (maintenance, longer-term)
7. **VoiceScheduler extraction** (code quality, longer-term)

---

## Fixes Applied (2026-03-24) — Software Engineer Issues

### 1. Long → Int migration in strip renderers (DONE)

All per-sample arithmetic in strip renderers now uses `Int` instead of `Long`:

- `EnvelopeRenderer` — `absPos` and `gateEndPos` are now `Int`, computed once at block boundary
- `AccelerateRenderer` — `blockRelStart` computed as `Int`, per-sample loop uses Int offset
- `PitchEnvelopeRenderer` — `blockRelStart` and `relPos` are `Int`, `calculatePitchMod` takes `Int`
- `EnvelopeCalc.kt` — `calculateControlRateEnvelope` and `envelopeLevelAtPosition` now use `Int`
  for `absPos`/`gateEndPos`. Long-to-Int conversion at block boundary only.
- `VibratoRenderer` — phase now wrapped to `[0, TWO_PI)` to prevent precision loss

`BlockContext` still stores `Long` for `startFrame`/`endFrame`/`gateEndFrame`/`blockStart` since
these are only read once per block (at the boundary), not per sample. The conversion to `Int`
happens in each renderer's `render()` method.

### 2. Extract flushDenormal to shared location (DONE)

Created `exciter/DspUtil.kt` with shared `DENORMAL_THRESHOLD` and `flushDenormal()`.
Removed duplicate definitions from `ExciterEffects.kt` and `ExciterFilters.kt`.

### 3. FormantFilter per-block allocation (DONE)

`FormantFilter` now pre-allocates `bandBuffer` alongside `scratchBuffer`. Both resize once
on first call (or block size change), then reuse across all subsequent blocks. No per-band
allocation in the audio thread.

### 4. Reverb.mix made private (DONE)

`Reverb.mix` was a public `val = 1.0` (effectively a constant). Now `private val` with a
comment explaining that wet/dry is controlled by the send amount.

### 5. "Vibrator" typo fixed (DONE)

`VoiceScheduler.kt` line 639: "Vibrator" → "Vibrato".

---

## Fixes Applied (2026-03-24) — Audio Engineer + QA Issues

### 6. Pulze PolyBLEP anti-aliasing (DONE)

`Exciters.pulze()` now applies dual PolyBLEP at both transitions (0 and duty),
matching the pattern used by `square()`. Duty clamped to 0.01–0.99 to ensure
valid BLEP offsets. All 4 code paths (clean/analog × phaseMod/no-phaseMod) updated.

### 7. Freeverb sample-rate scaling (DONE)

`Reverb.kt` now scales comb and allpass tunings proportionally to the actual sample rate:
`tuning * (sampleRate / 44100.0)`. Stereo spread also scaled. This corrects the ~8.8%
pitch/size error when running at 48kHz.

### 8. Denormal flushing in legacy filters (DONE)

Added `flushDenormal()` to all IIR state variables in:

- `LowPassHighPassFilters.kt` — `OnePoleLPF.y`, `OnePoleHPF.y`, `BaseSvf.ic1eq/ic2eq` (all 4 SVF subclasses)
- `PhaserFilter.kt` — `filterState[s]` and `lastOutput`
- `DistortionFilter.kt` — `dcBlockY1`

### 9. AccelerateRenderer multiplicative stepping (DONE)

Replaced per-sample `pow()` with one `pow()` per block + per-sample multiply.
Pre-computed `step = 2^(amount / totalFrames)`, then `ratio *= step` per sample.

### 10. Ducking "attackSeconds" documentation (DONE)

Added explicit KDoc clarifying that `attackSeconds` controls recovery/release time
(not the duck-down, which is instantaneous). Named for strudel compatibility.

### 11. DuckingSpec broken "Attack time" test (DONE)

Replaced `sliceArray` pattern (which creates copies, leaving originals unprocessed)
with separate in-place buffers for active and recovery phases.

### 12. SampleVoiceRenderTest stale commented-out tests (DONE)

Removed two commented-out test blocks that had been superseded by `SampleVoiceSpecificTest`.

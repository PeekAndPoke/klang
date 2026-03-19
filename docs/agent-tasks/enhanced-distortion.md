# Enhanced Distortion Types

## Problem

Currently only one distortion algorithm (soft clipping via `fastTanh`) is available. The `DistortionFilter` is
hardcoded.
Multiple waveshaper functions already exist in `clipping_functions.kt` but are unused. Users have no way to select
distortion character.

## Current Architecture

```
DSL: pattern.distort(0.5)
  -> StrudelVoiceData.distort: Double?           (strudel)
  -> VoiceData.distort: Double?                  (audio_bridge)
  -> Voice.Distort(amount: Double)               (audio_be)
  -> DistortionFilter(amount) -> fastTanh only   (audio_be)
```

Existing waveshapers in `clipping_functions.kt`: `fastTanh`, `hardClip`, `softClip`, `cubicClip`, `sineFold`.

### Key Files

| File                                                                 | Role                                  |
|----------------------------------------------------------------------|---------------------------------------|
| `strudel/src/commonMain/kotlin/lang/lang_effects.kt`                 | DSL: `distort()` function             |
| `strudel/src/commonMain/kotlin/StrudelVoiceData.kt`                  | `distort: Double?` field              |
| `audio_bridge/src/commonMain/kotlin/VoiceData.kt`                    | `distort: Double?` field              |
| `audio_be/src/commonMain/kotlin/voices/Voice.kt`                     | `Voice.Distort(amount)` class         |
| `audio_be/src/commonMain/kotlin/voices/VoiceScheduler.kt`            | Instantiates `Voice.Distort`          |
| `audio_be/src/commonMain/kotlin/voices/AbstractVoice.kt`             | Creates `DistortionFilter` as post-fx |
| `audio_be/src/commonMain/kotlin/filters/effects/DistortionFilter.kt` | Applies `fastTanh` per-sample         |
| `audio_be/src/commonMain/kotlin/clipping_functions.kt`               | Waveshaper functions (5 exist)        |

---

## Distortion Types

### Transfer Functions

Each type applies `f(x)` where `x = input * drive`.

| Type        | Formula                                                       | Character                           | Aliasing Risk |
|-------------|---------------------------------------------------------------|-------------------------------------|---------------|
| `soft`      | `fastTanh(x)`                                                 | Warm analog saturation (default)    | Safe          |
| `hard`      | `clamp(x, -1, 1)`                                             | Aggressive digital clipping         | High          |
| `gentle`    | `x / (1 + abs(x))`                                            | Wider knee, warmer than tanh        | Safe          |
| `cubic`     | `x - x^3/3` (clamped), output * 1.5 for normalization         | Tube-like, 3rd harmonic, musical    | Safe          |
| `diode`     | `tanh(x)` pos / `tanh(x * 0.75)` neg, then DC-block           | Asymmetric, even harmonics, thicker | Safe          |
| `fold`      | `sin(x)` (default, safe) or triangle fold (with oversampling) | Metallic, sci-fi, FM-like           | Medium        |
| `chebyshev` | `4x^3 - 3x` with input clamped to [-1, 1]                     | Pure 3rd harmonic, tape saturation  | Safe          |
| `rectify`   | `abs(x)` (full-wave) or `max(0, x)` (half-wave)               | Octave-up effect, gnarly buzz       | Medium        |
| `exp`       | `sign(x) * (1 - exp(-abs(x)))`                                | Tight knee, transistor character    | Safe          |

**Dropped from original plan:**

- `scurve` — mathematically near-identical to `tanh`, replaced by `gentle` (existing `softClip`)
- `asym` — broken output range, crude design. Use `diode` for asymmetric distortion.
- `sinefold` — merged into `fold` (sine fold is the default fold behavior)

### Audio Engineering Notes

These are critical for production quality. Address during implementation.

#### Drive Curve

The linear formula `drive = 1.0 + (amount * 10.0)` crams useful range into the first 10-20% of the knob.
Use exponential scaling for perceptually even spacing:

```kotlin
val drive = 10.0.pow(amount * 1.2)  // amount=0 -> 1x, amount=1 -> ~15.8x
```

#### DC Blocking (Required for asymmetric types)

Asymmetric types (`diode`, `rectify`, even-order `chebyshev`) generate DC offset that causes speaker excursion,
headroom loss, and clicks on enable/disable. Apply a one-pole highpass after these types:

```kotlin
// DC blocker: y[n] = x[n] - x[n-1] + 0.995 * y[n-1]  (~5 Hz at 48kHz)
```

#### Per-Type Output Normalization

Different types produce different peak levels at the same drive. Normalize so switching types does not cause
volume jumps:

| Type      | Raw Peak | Normalization Factor |
|-----------|----------|----------------------|
| soft      | 1.0      | 1.0                  |
| hard      | 1.0      | 1.0                  |
| gentle    | ~0.5     | ~2.0                 |
| cubic     | 0.667    | 1.5                  |
| diode     | ~1.0     | 1.0                  |
| fold      | 1.0      | 1.0                  |
| chebyshev | 1.0      | 1.0 (after clamping) |
| rectify   | 1.0      | 1.0                  |
| exp       | 1.0      | 1.0                  |

#### Parameter Smoothing

Drive changes must be smoothed to avoid clicks (~5ms exponential smoothing on the drive parameter).

#### Aliasing Mitigation

- **Safe at 48kHz, no oversampling needed:** soft, gentle, cubic, diode, chebyshev, exp
- **Medium risk (limit drive range or document as lofi):** fold, rectify
- **High risk (consider optional 2x oversampling):** hard
- Triangle fold (non-sine) requires 4x oversampling — defer to later or gate behind a quality setting.

---

## Implementation Steps

### Phase 1: Data Model — Add `distortShape` field

Thread a `distortShape: String?` field through the chain:

1. `StrudelVoiceData` — add `distortShape: String?` field, pass through in `merge()` and `toVoiceData()`
2. `VoiceData` — add `distortShape: String?` field
3. `Voice.Distort` — add `shape: String` field (default `"soft"`)
4. `VoiceScheduler` — read `data.distortShape` when constructing `Voice.Distort`

### Phase 2: DistortionFilter — Waveshaper dispatch

1. Add new waveshapers to `clipping_functions.kt`: `diodeClip`, `chebyshevT3`, `rectifyFull`, `expClip`
2. Add DC blocker state to `DistortionFilter` (one-pole highpass)
3. Add drive smoothing state (one-pole lowpass on drive parameter)
4. Update `DistortionFilter` constructor to accept `shape: String`
5. Dispatch to correct waveshaper in `process()` via a function reference (avoid `when` per-sample)
6. Apply DC blocker for asymmetric types
7. Apply per-type output normalization
8. Update exponential drive formula

### Phase 3: DSL — Named distortion functions

Following the strudel.cc pattern, add named functions that set both amount and shape:

```kotlin
// Each named type sets distort amount AND distortShape
// pattern.hard(0.5)  ->  distort=0.5, distortShape="hard"
// pattern.cubic(0.8) ->  distort=0.8, distortShape="cubic"
```

Add DSL functions for: `soft`, `hard`, `gentle`, `cubic`, `diode`, `fold`, `chebyshev`, `rectify`, `exp`.
The existing `distort()` continues to work (defaults to `"soft"`).

### Phase 4: Tests

1. Unit test each waveshaper function (output range, symmetry, DC offset)
2. Test that drive smoothing prevents discontinuities
3. Test DC blocker removes offset for asymmetric types
4. Verify DSL functions set correct shape and amount

---

## Verification

```bash
./gradlew :audio_be:jvmTest          # Waveshaper + filter tests
./gradlew :strudel:jvmTest           # DSL tests
./gradlew jvmTest                    # Full suite
```

Manual: play `sound("bd").hard(0.5)` vs `sound("bd").soft(0.5)` — should sound distinctly different
without volume jumps.

---

## Strudel.cc JS Reference

The original strudel.cc implements distortion types as named aliases:

```javascript
const distAlgoNames = ['scurve', 'soft', 'hard', 'cubic', 'diode', 'asym', 'fold', 'sinefold', 'chebyshev'];
for (const name of distAlgoNames) {
    Pattern.prototype[name] = function (args) {
        const argsPat = reify(args).fmap((v) => (Array.isArray(v) ? [...v, name] : [v, 1, name]));
        return this.distort(argsPat);
    };
}
```

Each named function packs `[amount, volume, shapeName]` into the distort pattern value.

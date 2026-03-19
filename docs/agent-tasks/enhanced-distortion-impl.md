# Enhanced Distortion — Implementation Notes

## What was implemented

9 selectable distortion shapes, replacing the single hardcoded `fastTanh` waveshaper.

### Distortion shapes

| Shape       | Function      | Character                           |
|-------------|---------------|-------------------------------------|
| `soft`      | `fastTanh`    | Warm analog saturation (default)    |
| `hard`      | `hardClip`    | Aggressive digital clipping         |
| `gentle`    | `softClip`    | Wider knee, warmer than tanh        |
| `cubic`     | `cubicClip`   | Tube-like, 3rd harmonic, musical    |
| `diode`     | `diodeClip`   | Asymmetric, even harmonics, thicker |
| `fold`      | `sineFold`    | Metallic, sci-fi, FM-like           |
| `chebyshev` | `chebyshevT3` | Pure 3rd harmonic, tape saturation  |
| `rectify`   | `rectify`     | Octave-up effect, gnarly buzz       |
| `exp`       | `expClip`     | Tight knee, transistor character    |

### DSL usage

Each shape is available as a named function:

```klangscript
note("c2 eb2 g2").s("sawtooth").soft(0.5)      // warm analog
note("c2 eb2 g2").s("sawtooth").hard(0.5)      // aggressive digital
note("c2 eb2 g2").s("sawtooth").gentle(0.5)    // smooth, gradual
note("c2 eb2 g2").s("sawtooth").cubic(0.5)     // tube warmth
note("c2 eb2 g2").s("sawtooth").diode(0.5)     // thick, even harmonics
note("c2 eb2 g2").s("sawtooth").fold(0.5)      // metallic wavefold
note("c2 eb2 g2").s("sawtooth").chebyshev(0.5) // tape saturation
note("c2 eb2 g2").s("sawtooth").rectify(0.5)   // octave-up buzz
note("c2 eb2 g2").s("sawtooth").expClip(0.5)   // transistor punch
```

The existing `distort()` / `dist()` continue to work, defaulting to `"soft"`.

### Audio engineering improvements

1. **Exponential drive curve**: `drive = 10^(amount * 1.2)` — perceptually even spacing instead of linear.
2. **DC blocking filter**: One-pole highpass (~5 Hz) applied automatically for asymmetric shapes (`diode`, `rectify`).
3. **Per-shape output normalization**: `gentle` gets 2x gain to match other shapes' output levels.
4. **Function reference dispatch**: Shape is resolved once at construction (not per-sample `when`).

---

## Files changed

### New files

| File                                                       | Description                                           |
|------------------------------------------------------------|-------------------------------------------------------|
| `audio_be/src/commonTest/kotlin/effects/DistortionSpec.kt` | 18 tests covering all waveshapers and filter behavior |

### Modified files

| File                                                                 | Change                                                                                                 |
|----------------------------------------------------------------------|--------------------------------------------------------------------------------------------------------|
| `audio_be/src/commonMain/kotlin/clipping_functions.kt`               | Added 5 new waveshapers: `diodeClip`, `chebyshevT3`, `rectify`, `expClip` + docs                       |
| `audio_be/src/commonMain/kotlin/filters/effects/DistortionFilter.kt` | Full rewrite: shape dispatch, DC blocker, exponential drive, normalization                             |
| `audio_be/src/commonMain/kotlin/voices/Voice.kt`                     | Added `shape: String` to `Distort` class                                                               |
| `audio_be/src/commonMain/kotlin/voices/AbstractVoice.kt`             | Passes `distort.shape` to `DistortionFilter`                                                           |
| `audio_be/src/commonMain/kotlin/voices/VoiceScheduler.kt`            | Reads `data.distortShape` when constructing `Voice.Distort`                                            |
| `audio_bridge/src/commonMain/kotlin/VoiceData.kt`                    | Added `distortShape: String?` field                                                                    |
| `strudel/src/commonMain/kotlin/StrudelVoiceData.kt`                  | Added `distortShape: String?` field + merge/toVoiceData                                                |
| `strudel/src/commonMain/kotlin/lang/lang_effects.kt`                 | 9 named distortion DSL functions (soft, hard, gentle, cubic, diode, fold, chebyshev, rectify, expClip) |
| `strudel/src/jvmMain/kotlin/graal/GraalStrudelPattern.kt`            | Added `distortShape = null` to Graal voice data construction                                           |

---

## Testing

### Automated tests (`DistortionSpec.kt`)

```bash
./gradlew :audio_be:jvmTest    # runs all audio_be tests including DistortionSpec
./gradlew jvmTest               # full suite (117 tasks)
```

The test suite covers:

**Waveshaper functions (per-function)**:

- Output boundedness (all shapes produce output within expected range)
- Symmetry (symmetric shapes: `fastTanh`, `softClip`, `cubicClip`, `expClip`, `sineFold`)
- Asymmetry verification (`diodeClip` positive compresses more than negative)
- Chebyshev T3 harmonic property (T3(0.5) = -1.0)
- Rectify non-negativity

**DistortionFilter integration**:

- `amount=0` passthrough (no processing)
- All shapes produce bounded output at moderate drive
- Unknown shape falls back to `"soft"`
- DC blocker convergence for `diode` and `rectify` (constant/alternating input converges toward zero)
- Offset parameter respected (only specified buffer range is processed)

### Manual testing

Play these in the browser and verify audible differences:

```klangscript
// Compare shapes on the same pattern
note("c2 eb2 g2 c3").s("sawtooth").soft(0.6)
note("c2 eb2 g2 c3").s("sawtooth").hard(0.6)
note("c2 eb2 g2 c3").s("sawtooth").cubic(0.6)
note("c2 eb2 g2 c3").s("sawtooth").diode(0.6)
note("c2 eb2 g2 c3").s("sawtooth").fold(0.6)

// Verify no volume jumps when switching shapes
// Verify no clicks/pops when pattern changes

// Verify existing distort() still works
note("c2 eb2 g2 c3").s("sawtooth").distort(0.5)
```

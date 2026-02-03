Here is a detailed implementation plan designed to be handed off to a coding agent.

***

# Implementation Plan: Audio Master Configuration & Analog Saturation

## 1. Objective

Enhance the `KlangAudioRenderer` to support configurable mastering chains. Currently, the renderer uses hardcoded "
brick-wall" limiting and simple safety clipping. We want to transition to a dynamic system that allows for different
sound profiles (e.g., "Transparent" vs. "Analog Warmth") by exposing parameters for compression, saturation drive, and
asymmetric bias.

## 2. Target Files

* **Primary:** `audio_be/src/commonMain/kotlin/KlangAudioRenderer.kt`
* **Dependency:** `audio_be/src/commonMain/kotlin/effects/Compressor.kt` (Assumed to be already fixed with correct
  envelope math).

## 3. Implementation Steps

### Step 1: Define `MasteringSettings` Data Structure

Create a data class to encapsulate all DSP parameters for the output stage. This should likely be nested within
`KlangAudioRenderer` or placed in `audio_be/src/commonMain/kotlin/KlangAudioRenderer.kt`.

**Requirements:**

* **Name:** `MasteringSettings`
* **Fields:**
    * `thresholdDb` (Double): Compressor threshold.
    * `ratio` (Double): Compression ratio.
    * `kneeDb` (Double): Soft knee width.
    * `attackSeconds` (Double): Attack time.
    * `releaseSeconds` (Double): Release time.
    * `saturationDrive` (Double): Input gain multiplier for the saturator (1.0 = clean).
    * `saturationBias` (Double): DC offset for asymmetric distortion (0.0 = symmetrical/tape, >0.0 = asymmetrical/tube).
* **Presets (Companion Object):**
    * `TRANSPARENT`: Fast attack (1ms), High ratio (20:1), Hard knee (0db), High threshold (-1dB), Drive 1.0, Bias 0.0.
    * `ANALOG_WARMTH`: Slow attack (30ms), Musical ratio (4:1), Soft knee (12dB), Lower threshold (-3dB), Drive 1.2,
      Bias 0.15.

### Step 2: Refactor `KlangAudioRenderer` Architecture

Update the renderer class to accept these settings and react to changes.

**Requirements:**

* **Constructor:** Add `initialSettings: MasteringSettings = MasteringSettings.TRANSPARENT`.
* **Property:** Create a mutable public property `var settings: MasteringSettings`.
* **Setter Logic:** When `settings` is updated, immediately propagate the compressor-related fields (threshold, ratio,
  knee, attack, release) to the internal `limiter` instance.

### Step 3: Implement Asymmetric Soft Clipping

Replace the existing `fastSoftClip(x)` function with a version that supports bias (asymmetry).

**Math Specification:**
The function signature should be `private fun fastSoftClip(input: Double, bias: Double): Double`.

1. **Offset:** `x = input + bias`
2. **Rational Pade Approximation:**
    * If `x < -3.0`, result is `-1.0`.
    * If `x > 3.0`, result is `1.0`.
    * Otherwise: `y = x * (27 + x^2) / (27 + 9x^2)`
3. **Remove Offset:** Return `y - bias`.
    * *Note:* The clamping in step 2 should also respect bias (e.g., return `-1.0 - bias` to ensure the final result
      after step 3 is strictly -1.0).

### Step 4: Implement the Render Loop (DSP Chain)

Update `renderBlock` to implement the specific signal flow order to achieve the desired "Warmth".

**Signal Flow:**

1. **Dynamics Processing:** Apply the `limiter.process(...)` to the raw mix buffers first. This controls the dynamics
   before saturation.
2. **Drive Calculation:** Cache `drive = settings.saturationDrive`.
3. **Makeup Calculation:** Calculate `makeup = 1.0 / drive` (if drive > 0). This ensures that increasing drive adds
   texture/harmonics without significantly increasing the peak volume.
4. **Per-Sample Loop:**
    * **Input Stage:** Multiply sample by `drive`.
    * **Saturation Stage:** Pass driven sample + `settings.saturationBias` into `fastSoftClip`.
    * **Output Stage:** Multiply result by `makeup`.
    * **Safety Clamp:** Hard clamp result to `[-1.0, 1.0]` to strictly prevent wrapping.
    * **Conversion:** Convert to Short (PCM 16-bit).

### Step 5: Verification Checklist

* Does the renderer default to `TRANSPARENT` if no settings are provided?
* Does changing `renderer.settings = MasteringSettings.ANALOG_WARMTH` immediately alter the sound?
* Does the compressor receive the new attack/release times when settings are changed?
* Is the makeup gain mathematically correct (inverse of drive) to maintain unity gain?

## 4. Example Code Reference (For Logic)

```kotlin
// Asymmetric clipper logic
private fun fastSoftClip(input: Double, bias: Double): Double {
    val x = input + bias
    if (x < -3.0) return -1.0 - bias
    if (x > 3.0) return 1.0 - bias
    val x2 = x * x
    val y = x * (27.0 + x2) / (27.0 + 9.0 * x2)
    return y - bias
}

// Inside render loop
val driven = input * settings.saturationDrive
val saturated = fastSoftClip(driven, settings.saturationBias)
val output = saturated * (1.0 / settings.saturationDrive)
```

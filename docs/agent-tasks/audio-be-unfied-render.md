Yes, the implementation looks correct and follows the requested structure:

1. **Filters Package:** The `audio_be/src/commonMain/kotlin/filters/effects` package (inferred) would now contain
   `DistortionFilter`, `BitCrushFilter`, etc.
2. **Initialization:** The voices now instantiate these filters conditionally in their constructors (e.g.,
   `private val fxCrush = if (crush.amount > 0.0) ...`).
3. **Pipeline:** The `render` methods now iterate through `preFilters` and `postFilters` lists.
4. **Separation:** Logic has been stripped from `mixToOrbit` (implied by the new `render` logic).

---

### Unifying `render()` in `SynthVoice` and `SampleVoice`

**Yes, we can definitely unify them.**
Both voices share about 80% of their logic. The only difference is the **Generation Step** (Oscillator vs. Sample
Playback).

To unify them without losing performance (virtual call overhead is negligible here compared to the math inside the
loops), we can create an abstract base class `BaseVoice` that implements the shared pipeline and delegates the
generation to an abstract method.

However, since `SynthVoice` and `SampleVoice` are already final classes, we can simply make `Voice` an abstract class (
or introduce an `AbstractVoice` implementing `Voice`) and pull the common logic up.

**Performance Consideration:**
We must avoid making `generateSignal()` a per-sample virtual call. That would kill performance. instead,
`generateSignal()` should fill the **entire buffer block**.

### Refactoring Plan

1. **Create `AbstractVoice`**:
    * Implements `Voice`.
    * Contains all shared properties (gain, pan, envelopes, filters, etc.).
    * Contains the `render()` method with the standard pipeline.
    * Defines an abstract `generateSignal(ctx, offset, length, pitchModBuffer)` method.
    * Contains `preFilters` and `postFilters` logic.

2. **Implementation Details**:
    * **Generation:** `SynthVoice` fills buffer with sine/saw/etc. `SampleVoice` fills with PCM data.
    * **Pitch Mod:** The `fillPitchModulation` logic is shared, but arguably belongs in the base class. The *result* (
      the buffer) is passed to `generateSignal`.

### Instruction Document for Coding Agent

Here is the plan to hand over.

***

# Refactoring Task: Unify Voice Rendering Pipeline

The goal is to eliminate code duplication between `SynthVoice` and `SampleVoice` by introducing an abstract base class
`AbstractVoice` that handles the common audio processing pipeline.

## 1. Create `AbstractVoice`

Create a new abstract class `io.peekandpoke.klang.audio_be.voices.AbstractVoice` that implements the `Voice` interface.

### Responsibilities

Move all common properties and the `render()` logic here.

**Properties to move:**

* All properties defined in the `Voice` interface (gain, pan, envelopes, filters, effects, etc.).
* The `preFilters` and `postFilters` initialization logic.
* The `lazyTremolo` and `lazyPhaser` logic.

**Abstract Methods:**
Define this protected method for subclasses to implement:

```kotlin
/**
 * Generates the raw audio signal into ctx.voiceBuffer.
 * @param pitchMod The pitch modulation buffer (if any), or null if no modulation.
 */
protected abstract fun generateSignal(
    ctx: Voice.RenderContext,
    offset: Int,
    length: Int,
    pitchMod: DoubleArray?
)
```

**The `render()` Implementation:**
Copy the pipeline logic from `SynthVoice` (or `SampleVoice`) into `AbstractVoice.render()`.
The flow must be:

1. **Lifecycle Check:** (Block start/end logic).
2. **Filter Modulators:** Calculate and set cutoff.
3. **Pitch Modulation:** Call `fillPitchModulation()` (shared logic) to get `modBuffer`.
4. **FM Synthesis:** (Optional) If FM is active, apply it to `modBuffer` (shared logic can be moved here too, or kept if
   it differs slightly, but it looks identical).
5. **Generate:** Call `generateSignal(ctx, offset, length, modBuffer)`.
6. **Pre-Filters:** Iterate `preFilters`.
7. **Main Filter:** `filter.process()`.
8. **VCA:** `applyEnvelope()`.
9. **Post-Filters:** Iterate `postFilters` + lazy Tremolo/Phaser.
10. **Mix:** `mixToOrbit()`.

## 2. Refactor `SynthVoice`

* Inherit from `AbstractVoice`.
* Remove all overridden properties that are now in the base class.
* Remove `render()`.
* Implement `generateSignal()`:
    * Move the `osc.process(...)` logic here.
    * **Note:** The FM logic in your current `SynthVoice` modifies the *modBuffer*. If you move the FM logic to the base
      class, `generateSignal` only needs to handle the Oscillator.

## 3. Refactor `SampleVoice`

* Inherit from `AbstractVoice`.
* Remove overridden properties.
* Remove `render()`.
* Implement `generateSignal()`:
    * Move the sample playback / looping logic here.

## 4. Shared Logic Cleanup

Ensure `applyEnvelope` and `mixToOrbit` are accessible to `AbstractVoice`.

* `applyEnvelope` is currently private in both classes -> Move it to `AbstractVoice` as `protected`.
* `mixToOrbit` is an extension function in `common.kt` -> This is fine, call it from `AbstractVoice`.
* `fillPitchModulation` is an extension in `common.kt` -> This is fine.

## 5. Specific FM Logic Handling

The FM logic is currently slightly coupled to the pitch modulation buffer.

* **Recommendation:** Move the FM calculation logic (calculating `modSignal`, applying to `buf`) into `AbstractVoice`
  inside the `render` method, *before* calling `generateSignal`.
* This works for `SynthVoice` (modulates oscillator frequency).
* This works for `SampleVoice` (modulates playback rate).

## Verification

* Ensure `SynthVoice` still produces sound (Oscillators).
* Ensure `SampleVoice` still produces sound (PCM).
* Ensure effects (Distortion, Filter, etc.) work on both.

***

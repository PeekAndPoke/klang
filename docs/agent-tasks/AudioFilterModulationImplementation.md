# Audio Filter Envelope Implementation

## Context

We have added envelope data (`FilterEnvelope`) to `FilterDef` in the data model. Now we need to implement the actual
audio processing logic to modulate filter cutoff frequencies based on these envelopes in the audio backend (`audio_be`).

## Analysis

Currently, `AudioFilter` implementations in `LowPassHighPassFilters` are static; their cutoff frequency is set at
initialization and cannot be changed. To support envelopes:

1. Filters must become "tunable" at runtime.
2. The `Voice` interface needs a way to hold the modulation state (envelope progress and target filter).
3. The `VoiceScheduler` needs to initialize this modulation state when creating a voice.
4. The voice render loops (`SynthVoice` and `SampleVoice`) need to calculate the envelope value per block and update the
   filter's cutoff.

## Implementation Plan

### 1. Make Filters Tunable

**File:** `audio_be/src/commonMain/kotlin/filters/AudioFilter.kt`

* Add a `Tunable` interface inside `AudioFilter`.
  ```kotlin
  interface Tunable {
      fun setCutoff(cutoffHz: Double)
  }
  ```

**File:** `audio_be/src/commonMain/kotlin/filters/LowPassHighPassFilters.kt`

* Update `OnePoleLPF`, `OnePoleHPF`, and `BaseSvf` (and its subclasses if necessary) to implement `AudioFilter.Tunable`.
* Refactor their `init` blocks to use the `setCutoff` method so initialization logic isn't duplicated.
* Ensure `BaseSvf` stores `cutoffHz` or recalculates coefficients (`a1`, `a2`, `k`, etc.) when `setCutoff` is called.

### 2. Define Filter Modulator and Update Voice Interface

**File:** `audio_be/src/commonMain/kotlin/voices/Voice.kt`

* Define a `FilterModulator` class inside `Voice` to hold the runtime state.
  ```kotlin
  class FilterModulator(
      val filter: AudioFilter.Tunable,
      val envelope: Envelope,
      val depth: Double,
      val baseCutoff: Double,
  )
  ```
* Add a `filterModulators` property to the `Voice` interface.
  ```kotlin
  val filterModulators: List<FilterModulator>
  ```

### 3. Wire up VoiceScheduler

**File:** `audio_be/src/commonMain/kotlin/voices/VoiceScheduler.kt`

* Create a helper extension `FilterDef.toModulator(filter: AudioFilter, sampleRate: Int): Voice.FilterModulator?`.
    * It should check if the `filter` is `Tunable`.
    * It should check if the `FilterDef` has an `envelope`.
    * It should resolve the envelope (using `FilterEnvelope.resolve()`) and create the `Voice.Envelope` and
      `Voice.FilterModulator`.
* In `makeVoice`, during filter creation:
    * Map the `FilterDef`s to both their `AudioFilter` and potential `FilterModulator`.
    * Pass the list of `FilterModulator`s to the `SynthVoice` and `SampleVoice` constructors.

### 4. Implement Modulation Logic in Voices

**File:** `audio_be/src/commonMain/kotlin/voices/SynthVoice.kt` & `audio_be/src/commonMain/kotlin/voices/SampleVoice.kt`

* Update constructors to override `filterModulators`.
* In the `render()` method, before the audio generation loop:
    * Iterate over `filterModulators`.
    * Calculate the envelope value (0.0 to 1.0) based on the voice's age (`framesSinceStart`, `isReleased`, etc.).
      *Note: You can reuse or adapt the logic used for the volume envelope, or extract a shared `calculateEnvelope`
      helper.*
    * Calculate the new cutoff frequency. A simple linear modulation model is sufficient for now:
      `baseCutoff * (1.0 + depth * envelopeValue)`.
    * Call `mod.filter.setCutoff(newCutoff)`.

## Specific Instructions for Agent

* **Performance:** Calculate the filter envelope *once per block* (Control Rate) inside `render()`, not per sample. This
  is standard practice and saves CPU.
* **SampleVoice:** Ensure `SampleVoice` gets the same treatment as `SynthVoice`.
* **Validation:** Ensure that if no envelope is defined in `FilterDef`, the list of modulators is empty and the filter
  behaves statically as before.

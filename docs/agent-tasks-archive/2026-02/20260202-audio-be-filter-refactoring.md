Here is a detailed, step-by-step plan to refactor the audio engine. This plan transitions the `Voice` architecture from
a monolithic, hard-coded effect chain into a modular `AudioFilter` pipeline.

### Objective

Decouple audio effects from the `Voice` implementations and the mixing logic. Introduce a 3-stage processing pipeline:

1. **Pre-Filters** (Destructive: Crush, Coarse)
2. **Main Filter** (Tonal: LowPass/HighPass/etc)
3. **Post-Filters** (Modulation/Saturation: Distortion, Tremolo, Phaser)

---

### Phase 1: Create Effect Filter Implementations

Create a new package/folder `audio_be/src/commonMain/kotlin/filters/effects` to keep things organized. Create the
following classes implementing `AudioFilter`.

**1. `DistortionFilter.kt`**

* **Properties:** `amount: Double`.
* **Logic:** Implement a `tanh` (hyperbolic tangent) or hard-clipping function.
* **Formula:** `output = tanh(input * (1.0 + amount * 10.0))` (scales drive based on amount).

**2. `BitCrushFilter.kt`**

* **Properties:** `amount: Double`.
* **Logic:** Quantize the amplitude.
* **Optimization:** If `amount <= 0`, return early. Calculate levels once: `levels = 2.0.pow(amount)`.

**3. `SampleRateReducerFilter.kt` (was Coarse)**

* **Properties:** `amount: Double`.
* **State:** Needs `var lastValue: Double` and `var counter: Double`.
* **Logic:** Hold the previous sample for `N` frames based on the `amount`.

**4. `TremoloFilter.kt`**

* **Properties:** `rate: Double`, `depth: Double`, `sampleRate: Int`.
* **State:** Needs `var phase: Double`.
* **Logic:** Amplitude modulation using a Sine wave LFO. Move the logic currently in `common.kt` here.

**5. `PhaserFilter.kt`**

* **Properties:** `rate: Double`, `depth: Double`, `center: Double`, `sweep: Double`, `sampleRate: Int`.
* **State:** Needs `var phase: Double` and arrays for All-Pass Filter history (usually 2-6 stages).
* **Logic:** Implement a basic 2 or 4-stage All-Pass filter chain modulated by an LFO.

---

### Phase 2: Refactor Voice Implementation (`SynthVoice.kt` & `SampleVoice.kt`)

Modify `SynthVoice` and `SampleVoice` to initialize these filters instead of just holding the raw data objects.

**1. Update Class Properties**
Currently, `SynthVoice` overrides `val distort: Voice.Distort`.

* Keep the override (to satisfy the interface).
* Add **new private properties** that initialize the filters:

```kotlin
// Example conceptual code
private val fxDistortion = DistortionFilter(distort.amount)
private val fxCrush = BitCrushFilter(crush.amount)
private val fxTremolo = TremoloFilter(tremolo.rate, tremolo.depth, /*...*/)
```

**2. Create the Pipelines**
Define the processing chains as lists within the class:

```kotlin
private val preFilters: List<AudioFilter> = listOfNotNull(
    if (crush.amount > 0) fxCrush else null,
    if (coarse.amount > 0) fxCoarse else null
)

private val postFilters: List<AudioFilter> = listOfNotNull(
    if (distort.amount > 0) fxDistortion else null,
    if (tremolo.depth > 0) fxTremolo else null,
    // Add Phaser here
)
```

**3. Update `render()` Method**
Completely rewrite the audio generation flow inside `render`:

1. **Generate:** (Oscillator / Sample playback) -> `ctx.voiceBuffer`.
2. **Pre-Process:** Iterate `preFilters` and call `.process(ctx.voiceBuffer...)`.
3. **Main Filter:** Call `filter.process(...)` (This already exists).
4. **Envelope:** Apply the ADSR Envelope (This remains as the VCA stage).
5. **Post-Process:** Iterate `postFilters` and call `.process(...)`.
6. **Mix:** Call the refactored `mixToOrbit`.

---

### Phase 3: Clean up Shared Logic (`common.kt`)

The `mixToOrbit` function in `common.kt` is currently doing too much (calculating distortion, tremolo, etc.).

**1. Strip `mixToOrbit`**
Remove all logic related to:

* Crush / Coarse
* Distortion
* Tremolo

**2. Simplify `mixToOrbit` Responsibilities**
The function should now ONLY do:

1. **Post-Gain:** Apply `voice.postGain`.
2. **Panning:** Calculate Left/Right volumes based on `voice.pan`.
3. **Summing:** Add the processed `voiceBuffer` to the Orbit's `mixBuffer`.
4. **Sends:** If `voice.delay.amount > 0` or `voice.reverb.room > 0`, mix into the respective Send buffers.

---

### Summary Checklist for the Agent

1.  [ ] **Create** `audio_be/src/commonMain/kotlin/filters/effects/` directory.
2.  [ ] **Implement** `DistortionFilter`, `BitCrushFilter`, `SampleRateReducerFilter`, `TremoloFilter`, `PhaserFilter`.
3.  [ ] **Modify** `SynthVoice.kt`:
    * Initialize specific filters based on constructor params.
    * Create `preFilters` and `postFilters` lists.
    * Update `render()` pipeline: Gen -> Pre -> MainFilter -> Env -> Post -> Mix.
4.  [ ] **Modify** `SampleVoice.kt`:
    * Apply the same pattern as `SynthVoice`.
5.  [ ] **Refactor** `audio_be/src/commonMain/kotlin/voices/common.kt`:
    * Remove effect processing logic from `mixToOrbit`.
    * Keep only Gain, Pan, and Buffer Mixing logic.

This plan ensures that if we want to add a "Chorus" or "Flanger" later, we simply write a new Filter class and add it to
the `postFilters` list, without touching the core render loop or mixing logic.

Here is the logic for the execution order. It follows standard audio signal flow practices (Source $\to$ Scupt $\to$
Dynamics $\to$ Color).

### The "FM Filter" Clarification

First, it is important to distinguish between two things in your `Voice` class that sound similar:

1. **`val fm: Fm?` (Frequency Modulation):**
   This is **not a filter**. This is **Synthesis** (the Source). It modulates the oscillator's frequency *while* the
   sound is being generated.

* **Position:** **Phase 0 (Source Generation)**.
* It happens inside `osc.process()` or immediately before it. It must occur before *any* audio filters touch the buffer.

2. **`val filterModulators: List<FilterModulator>`:**
   This controls the **Main Filter**.

* **Position:** **Start of Phase 2**.
* It calculates the new cutoff frequency for the Main Filter before the filter runs.

---

### Recommended Execution Order

Here is the exact order for your pipeline.

#### Phase 1: Pre-Filters (Destructive / Lo-Fi)

These effects add harsh digital artifacts (aliasing, quantization). We place them **before** the main filter so the main
filter can smooth them out (e.g., a Low Pass filter removing the high-frequency sizzle from the Bitcrusher).

1. **BitCrush (`Crush`)**: Quantizes the amplitude (voltage).
2. **SampleRateReducer (`Coarse`)**: Quantizes the time.

* *Note:* Doing this before the Main Filter mimics the sound of vintage hardware samplers (like the SP-1200 or MPC60),
  which had low sampling rates but analog filters at the output to warm up the sound.

#### Phase 2: The Main Filter (Subtractive)

This is the "Sculpting" stage.

1. **Apply Filter Modulators**: Calculate the Envelope/LFO values and call `filter.setCutoff()`.
2. **Run Main Filter**: Process the buffer with the selected HighPass/LowPass/etc.

#### Phase 3: VCA (Dynamics)

This is the "Heart" of the voice lifecycle.

1. **Envelope (ADSR)**: Apply the volume envelope. This defines the note's start and end.

#### Phase 4: Post-Filters (Color & Modulation)

These effects work best on the "finished" sound.

1. **Distortion**:

* **Position:** Immediately after VCA.
* *Why?* Because it is placed after the envelope, it reacts to the volume. Loud attacks will be more distorted, and the
  tail of the sound will clean up as it fades out (like a tube amp).

2. **Phaser**:

* **Position:** After Distortion.
* *Why?* Distortion generates rich harmonics. The Phaser needs those harmonics to have something to "sweep." If you
  phase a sine wave, you hear nothing. If you phase a distorted sine wave, you hear the "woosh."

3. **Tremolo**:

* **Position:** Last.
* *Why?* Tremolo modulates volume. If you put it before distortion, the distortion amount would fluctuate (which is
  cool, but usually not what's expected). If you put it last, you get a clean rhythmic volume dip.

4. **Compressor / Ducking** (If moved to filters):

* **Position:** Very last. These are final dynamic controls to glue the sound or pump it.

---

### Visual Pipeline

```plain text
[ OSCILLATOR / FM ]  <-- Source Generation
       |
       v
[ PRE-FILTERS ]      <-- 1. BitCrush -> 2. Coarse
       |
       v
[ MAIN FILTER ]      <-- 3. Cutoff Modulation -> 4. Filter Process
       |
       v
[ VCA / ENVELOPE ]   <-- 5. Volume Shape
       |
       v
[ POST-FILTERS ]     <-- 6. Distortion -> 7. Phaser -> 8. Tremolo -> 9. Comp
       |
       v
[ MIXER ]            <-- Pan / Delay Send / Reverb Send
```

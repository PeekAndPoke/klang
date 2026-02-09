You asked for an explanation of FM synthesis and how to wire it up in the `audio_be` backend.

### What is FM Synthesis?

**FM (Frequency Modulation)** synthesis is a technique where the timbre of a waveform is changed by modulating its
frequency with a modulator frequency that is also in the audio range.

In its classic form (like the Yamaha DX7), it's technically **Phase Modulation**, but the parameters usually behave as
follows:

* **Carrier**: The main oscillator you hear (defined by the note pitch).
* **Modulator**: An oscillator that modulates the carrier.
* **Ratio (`fmh`)**: The frequency relationship between Modulator and Carrier (`ModFreq = CarrierFreq * Ratio`).
  Integers (1, 2) produce harmonic sounds; decimals (1.41) produce metallic/bell-like inharmonic sounds.
* **Index/Depth (`fmenv`)**: How "strong" the modulation is. Higher values create brighter, noisier, or more complex
  sounds.
* **Envelope (`fmattack`, `fmdecay`, ...)**: Controls how the modulation depth changes over time (e.g., a bright attack
  that fades to a duller sustain).

### Wiring it up in the Audio Backend

The `lang_synthesis.kt` file defines the DSL, but the backend (`audio_be`) currently ignores these parameters. We need
to pipe them through `Voice.kt`, `VoiceScheduler.kt`, and finally implement the math in `SynthVoice.kt`.

Here is the plan to wire it up:

1. **Update `Voice`**: Add a data structure to hold FM parameters.
2. **Update `VoiceScheduler`**: extract FM settings from `VoiceData` and pass them to the `Voice`.
3. **Update `SynthVoice`**: Calculate the modulator signal and apply it to the carrier.

#### 1. Add FM Data Class to `Voice.kt`

We need a container for the FM state.

```kotlin
// ... existing code ...
class Coarse(
    val amount: Double,
    var lastCoarseValue: Double = 0.0,
    var coarseCounter: Double = 0.0,
)

class Fm(
    val ratio: Double,
    val depth: Double, // Hz amount
    val envelope: Envelope,
    // State
    var modPhase: Double = 0.0
)

// Timing
val startFrame: Long
// ... existing code ...
// global effects
val distort: Distort
val crush: Crush
val coarse: Coarse

// Synthesis
val fm: Fm?

/**
 * Renders the voice into the context's buffers.
// ... existing code ...
```

#### 2. Update `VoiceScheduler.kt` to Map Parameters

We need to read `fmh`, `fmenv` (depth), and the envelope settings from `VoiceData` and create the `Voice.Fm` object.

```kotlin
// ... existing code ...
val coarse = Voice.Coarse(amount = data.coarse ?: 0.0)

// FM Synthesis
val fm = if (data.fmh != null || (data.fmEnv ?: 0.0) != 0.0) {
    val ratio = data.fmh ?: 1.0
    val depth = data.fmEnv ?: 0.0

    // FM Envelope (similar to Filter Modulator envelope logic)
    val fmEnv = Voice.Envelope(
        attackFrames = (data.fmAttack ?: 0.0) * sampleRate,
        decayFrames = (data.fmDecay ?: 0.0) * sampleRate,
        sustainLevel = data.fmSustain ?: 1.0,
        // If release is not set, use default or match sustain logic? 
        // Usually synth envelopes default to some release
        releaseFrames = 0.0 // Simplified, or use data.fmRelease if it existed
    )

    Voice.Fm(ratio, depth, fmEnv)
} else null

// Decision
val freqHz = data.freqHz
// ... existing code ...
distort = distort,
crush = crush,
coarse = coarse,
osc = osc,
fm = fm,
freqHz = freqHz,
phaseInc = phaseInc,
)
}

// /////////////////////////////////////////////////////////////////////////////////////////////////////////
isSample -> {
// ... existing code ...
distort = distort,
crush = crush,
coarse = coarse,
fm = null, // Samples don't support FM in this path currently
samplePlayback = samplePlayback,
sample = sample,
// ... existing code ...
```

#### 3. Implement Synthesis in `SynthVoice.kt`

Currently, `fillPitchModulation` calculates Vibrato and Pitch Envelopes and returns a buffer of frequency multipliers (
ratios). We can implement "True FM" (Frequency Modulation) by calculating the modulator output and multiplying the
carrier's frequency by `(1 + modSignal/carrierFreq)`.

```kotlin
// ... existing code ...
override val compressor: Voice.Compressor?,
override val distort: Voice.Distort,
override val crush: Voice.Crush,
override val coarse: Voice.Coarse,
val osc: OscFn,
override val fm: Voice.Fm?,
val freqHz: Double,
val phaseInc: Double,
var phase: Double = 0.0,
) : Voice {

    override fun render(ctx: Voice.RenderContext): Boolean {
// ... existing code ...
        val offset = (vStart - ctx.blockStart).toInt()
        val length = (vEnd - vStart).toInt()

        // Apply filter modulation (control rate - once per block)
        for (mod in filterModulators) {
// ... existing code ...
            mod.filter.setCutoff(newCutoff)
        }

        // 1. Calculate base pitch modulation (Vibrato, Pitch Env, Slide)
        // returns a reusable buffer with frequency multipliers (nominal 1.0)
        // If null, it means no pitch modulation is active.
        var modBuffer = fillPitchModulation(ctx, offset, length)

        // 2. Apply FM (Frequency Modulation)
        // We inject this into the modBuffer (creating it if necessary)
        if (fm != null && fm.depth != 0.0) {
            val buf = modBuffer ?: ctx.freqModBuffer
            // If modBuffer was null, we must initialize it to 1.0s before multiplying FM
            if (modBuffer == null) {
                for (i in 0 until length) buf[offset + i] = 1.0
                modBuffer = buf
            }

            // Calculate Modulator Parameters
            val modFreq = freqHz * fm.ratio
            val modInc = (io.peekandpoke.klang.audio_be.TWO_PI * modFreq) / ctx.sampleRate
            var modPhase = fm.modPhase

            // Calculate FM Envelope for this block
            val env = fm.envelope
            val currentFrame = maxOf(ctx.blockStart, startFrame)
            val absPos = currentFrame - startFrame
            // We can reuse calculateFilterEnvelope or manually step it. 
            // For per-sample accuracy with audio-rate modulation, stepping is better, 
            // but control-rate envelope is usually fine for the depth.
            val envLevel = calculateFilterEnvelope(env, ctx)
            val effectiveDepth = fm.depth * envLevel

            for (i in 0 until length) {
                // Modulator Oscillator (Sine)
                val modSignal = kotlin.math.sin(modPhase) * effectiveDepth
                modPhase += modInc

                // Apply modulation: F_new = F_carrier + modSignal
                // Multiplier = (F_carrier + modSignal) / F_carrier = 1 + modSignal / F_carrier
                val fmMult = 1.0 + (modSignal / freqHz)

                // Apply to existing pitch modulation
                buf[offset + i] *= fmMult
            }
            // Save state
            fm.modPhase = modPhase
        }

        // Generate
        phase = osc.process(
            buffer = ctx.voiceBuffer,
            offset = offset,
            length = length,
            phase = phase,
            phaseInc = phaseInc,
            phaseMod = modBuffer
        )

        // Filter
        filter.process(ctx.voiceBuffer, offset, length)
// ... existing code ...
```

**Note on `calculateFilterEnvelope`**: I assumed this helper function is available as seen in `voices/common.kt`. If
`fm` modulation depth requires extremely fast envelope attacks (percussive FM), you might want to move the envelope
calculation inside the loop, similar to how `fillPitchModulation` handles the pitch envelope. But for general use,
control-rate updating of the depth (once per block or linearly interpolated) is efficient and sufficient.

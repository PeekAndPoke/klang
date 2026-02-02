To test the FM synthesis implementation, I recommend creating a unit test in the `audio_be` module. Since audio
generation involves complex signal processing, a unit test that verifies the *logic* and *effects* of parameters (like
ratio and depth) is more reliable than trying to verify the exact waveform manually.

Here is a new test file `audio_be/src/commonTest/kotlin/voices/FmSynthesisTest.kt`. It sets up a `SynthVoice` in a
controlled environment and verifies that:

1. **Silence (Depth 0)** produces a clean unmodulated signal.
2. **Modulation (Depth > 0)** alters the waveform.
3. **Ratio** changes the timbre (waveform shape).
4. **Envelopes** dynamically change the modulation amount over time.

```kotlin
package io.peekandpoke.klang.audio_be.voices

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_be.TWO_PI
import io.peekandpoke.klang.audio_be.filters.AudioFilter
import io.peekandpoke.klang.audio_be.orbits.Orbits
import io.peekandpoke.klang.audio_be.osci.OscFn
import kotlin.math.abs
import kotlin.math.sin

class FmSynthesisTest : StringSpec({

    val sampleRate = 44100
    val blockFrames = 100

    // Helper to create a dummy context
    fun createCtx(blockStart: Long = 0): Voice.RenderContext {
        return Voice.RenderContext(
            orbits = Orbits(blockFrames = blockFrames, sampleRate = sampleRate),
            sampleRate = sampleRate,
            blockFrames = blockFrames,
            voiceBuffer = DoubleArray(blockFrames),
            freqModBuffer = DoubleArray(blockFrames)
        ).apply {
            this.blockStart = blockStart
        }
    }

    // Dummy filter (No-op)
    val dummyFilter = object : AudioFilter {
        override fun process(buffer: DoubleArray, offset: Int, length: Int) {}
    }

    // Simple Sine Oscillator for testing
    val sineOsc: OscFn = { buffer, offset, length, phase, phaseInc, phaseMod ->
        var p = phase
        for (i in 0 until length) {
            val mod = phaseMod?.get(offset + i) ?: 1.0
            // Apply phase modulation (frequency modulation in this context)
            val currentInc = phaseInc * mod
            buffer[offset + i] = sin(p)
            p += currentInc
        }
        p // return new phase
    }

    // Helper to instantiate a SynthVoice with FM parameters
    fun createVoice(
        fm: Voice.Fm?,
        freqHz: Double = 440.0
    ): SynthVoice {
        return SynthVoice(
            orbitId = 0,
            startFrame = 0,
            endFrame = 1000,
            gateEndFrame = 1000,
            gain = 1.0,
            pan = 0.5,
            accelerate = Voice.Accelerate(0.0),
            vibrato = Voice.Vibrato(0.0, 0.0),
            pitchEnvelope = null,
            filter = dummyFilter,
            envelope = Voice.Envelope(0.0, 0.0, 1.0, 0.0), // instant attack, full sustain
            filterModulators = emptyList(),
            delay = Voice.Delay(0.0, 0.0, 0.0),
            reverb = Voice.Reverb(0.0, 0.0),
            phaser = Voice.Phaser(0.0, 0.0, 0.0, 0.0),
            tremolo = Voice.Tremolo(0.0, 0.0, 0.0, 0.0, null),
            ducking = null,
            postGain = 1.0,
            compressor = null,
            distort = Voice.Distort(0.0),
            crush = Voice.Crush(0.0),
            coarse = Voice.Coarse(0.0),
            osc = sineOsc,
            fm = fm,
            freqHz = freqHz,
            phaseInc = (TWO_PI * freqHz) / sampleRate
        )
    }

    "fm silence (depth 0) produces standard sine wave" {
        // 1. Render without FM object (Standard Sine)
        val ctx1 = createCtx()
        val voice1 = createVoice(fm = null)
        voice1.render(ctx1)
        val output1 = ctx1.voiceBuffer.clone()

        // 2. Render with FM object but depth 0.0
        val ctx2 = createCtx()
        val voice2 = createVoice(
            fm = Voice.Fm(
                ratio = 1.0, 
                depth = 0.0, 
                envelope = Voice.Envelope(0.0, 0.0, 1.0, 0.0)
            )
        )
        voice2.render(ctx2)
        val output2 = ctx2.voiceBuffer

        // Signals should be identical
        for (i in 0 until blockFrames) {
            output1[i] shouldBe (output2[i] plusOrMinus 1e-9)
        }
    }

    "fm modulation changes output waveform" {
        // 1. Reference: Clean Sine
        val ctxRef = createCtx()
        createVoice(fm = null).render(ctxRef)
        val refOutput = ctxRef.voiceBuffer.clone()

        // 2. FM: Ratio 1, Depth 100Hz
        val ctxFm = createCtx()
        createVoice(
            fm = Voice.Fm(
                ratio = 1.0, 
                depth = 100.0, // Significant modulation
                envelope = Voice.Envelope(0.0, 0.0, 1.0, 0.0)
            )
        ).render(ctxFm)
        val fmOutput = ctxFm.voiceBuffer

        // Signals should be significantly different
        var different = false
        for (i in 0 until blockFrames) {
            if (abs(refOutput[i] - fmOutput[i]) > 1e-5) {
                different = true
                break
            }
        }
        different shouldBe true
    }

    "fm ratio changes modulation character" {
        // 1. FM with Ratio 1.0 (Harmonic)
        val ctx1 = createCtx()
        createVoice(
            fm = Voice.Fm(
                ratio = 1.0, 
                depth = 200.0, 
                envelope = Voice.Envelope(0.0, 0.0, 1.0, 0.0)
            )
        ).render(ctx1)
        val out1 = ctx1.voiceBuffer.clone()

        // 2. FM with Ratio 2.0 (Octave up)
        val ctx2 = createCtx()
        createVoice(
            fm = Voice.Fm(
                ratio = 2.0, 
                depth = 200.0, 
                envelope = Voice.Envelope(0.0, 0.0, 1.0, 0.0)
            )
        ).render(ctx2)
        val out2 = ctx2.voiceBuffer

        // Signals should be different (different timbre)
        var different = false
        for (i in 0 until blockFrames) {
            if (abs(out1[i] - out2[i]) > 1e-5) {
                different = true
                break
            }
        }
        different shouldBe true
    }

    "fm envelope modulates depth over time" {
         // Create a voice with a slow attack on the FM depth
         val fmEnv = Voice.Envelope(
             attackFrames = 200.0, // 2 blocks long
             decayFrames = 0.0,
             sustainLevel = 1.0,
             releaseFrames = 0.0
         )
         
         val voice = createVoice(
            fm = Voice.Fm(ratio = 1.0, depth = 500.0, envelope = fmEnv)
         )
         
         // Measure variance in the frequency modulation buffer
         // Variance = measure of how much the pitch is wobbling
         fun variance(arr: DoubleArray): Double {
             val avg = arr.average()
             return arr.map { val d = it - avg; d * d }.average()
         }

         // Block 1 (Starts at 0, ramps up slightly)
         val ctx1 = createCtx(blockStart = 0)
         voice.render(ctx1)
         val var1 = variance(ctx1.freqModBuffer)

         // Block 3 (Fully sustained, maximum modulation)
         val ctx2 = createCtx(blockStart = 300)
         voice.render(ctx2)
         val var2 = variance(ctx2.freqModBuffer)
         
         // Modulation variance should increase significantly as envelope attacks
         (var2 > var1) shouldBe true
         // And verify we actually have modulation in the second block
         (var2 > 0.001) shouldBe true
    }
})
```

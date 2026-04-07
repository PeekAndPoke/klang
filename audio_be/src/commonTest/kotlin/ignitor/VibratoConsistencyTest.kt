package io.peekandpoke.klang.audio_be.ignitor

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_be.voices.Voice
import io.peekandpoke.klang.audio_be.voices.VoiceTestHelpers.createContext
import io.peekandpoke.klang.audio_be.voices.VoiceTestHelpers.createSynthVoice
import kotlin.math.sqrt

/**
 * Verifies that vibrato depth is interpreted identically across the Sprudel path
 * (VoiceData → VoiceFactory → VibratoRenderer) and the Ignitor DSL path
 * (Ignitor.vibrato(rate, depth)). Both should treat depth as semitones.
 */
class VibratoConsistencyTest : StringSpec({

    val bf = 512
    val sr = 48_000
    val rate = 5.0

    fun diffRms(a: FloatArray, b: FloatArray): Double {
        var sum = 0.0
        for (i in a.indices) {
            val d = a[i].toDouble() - b[i].toDouble()
            sum += d * d
        }
        return sqrt(sum / a.size)
    }

    /**
     * Render through the Sprudel path: VoiceFactory passes vibratoMod (semitones) directly
     * to Voice.Vibrato(depth), and VibratoRenderer applies the ET conversion.
     */
    fun renderSprudelPath(depthSemitones: Double): FloatArray {
        val voice = createSynthVoice(
            blockFrames = bf,
            sampleRate = sr,
            signal = Ignitors.sine(),
            vibrato = Voice.Vibrato(rate = rate, depth = depthSemitones),
        )

        val ctx = createContext(blockFrames = bf, sampleRate = sr)
        voice.render(ctx)
        return ctx.voiceBuffer.copyOf()
    }

    /**
     * Render through the Ignitor DSL path: Ignitor.vibrato(rate, depth) where
     * depth is in semitones (converted internally to ratio via / 12.0).
     */
    fun renderIgnitorDslPath(depthSemitones: Double): FloatArray {
        val signal = Ignitors.sine().vibrato(rate, depthSemitones)

        val voice = createSynthVoice(
            blockFrames = bf,
            sampleRate = sr,
            signal = signal,
            // No voice-level vibrato — it's baked into the ignitor
            vibrato = Voice.Vibrato(rate = 0.0, depth = 0.0),
        )

        val ctx = createContext(blockFrames = bf, sampleRate = sr)
        voice.render(ctx)
        return ctx.voiceBuffer.copyOf()
    }

    "sprudel and ignitor DSL vibrato produce the same output for 0.5 semitones" {
        val sprudel = renderSprudelPath(0.5)
        val ignitor = renderIgnitorDslPath(0.5)

        val diff = diffRms(sprudel, ignitor)
        diff shouldBeLessThan 1e-6
    }

    "sprudel and ignitor DSL vibrato produce the same output for 1.0 semitone" {
        val sprudel = renderSprudelPath(1.0)
        val ignitor = renderIgnitorDslPath(1.0)

        val diff = diffRms(sprudel, ignitor)
        diff shouldBeLessThan 1e-6
    }

    "sprudel and ignitor DSL vibrato produce the same output for 2.0 semitones" {
        val sprudel = renderSprudelPath(2.0)
        val ignitor = renderIgnitorDslPath(2.0)

        val diff = diffRms(sprudel, ignitor)
        diff shouldBeLessThan 1e-6
    }

    "both paths produce no modulation for depth 0" {
        val sprudel = renderSprudelPath(0.0)
        val ignitor = renderIgnitorDslPath(0.0)

        val diff = diffRms(sprudel, ignitor)
        diff shouldBeLessThan 1e-6
    }

    "ignitor DSL vibrato with depth 1 semitone does NOT produce ±100% frequency swing" {
        // Before the fix, depth=1.0 in the ignitor DSL meant ±100% frequency ratio.
        // After the fix, depth=1.0 means ±1 semitone (±8.3% frequency ratio).
        // Verify the modulation is reasonable (not extreme).
        val signal = Ignitors.sine().vibrato(rate, 1.0)

        // Render two blocks and check the output isn't wildly different from unmodulated
        val voiceWith = createSynthVoice(
            blockFrames = bf, sampleRate = sr,
            signal = signal,
            vibrato = Voice.Vibrato(rate = 0.0, depth = 0.0),
        )
        val voiceWithout = createSynthVoice(
            blockFrames = bf, sampleRate = sr,
            signal = Ignitors.sine(),
            vibrato = Voice.Vibrato(rate = 0.0, depth = 0.0),
        )

        val ctxWith = createContext(blockFrames = bf, sampleRate = sr)
        val ctxWithout = createContext(blockFrames = bf, sampleRate = sr)
        voiceWith.render(ctxWith)
        voiceWithout.render(ctxWithout)

        // The difference should be audible but not extreme
        val diff = diffRms(ctxWith.voiceBuffer, ctxWithout.voiceBuffer)
        // ±1 semitone = ±8.3% frequency deviation — noticeable but not destructive
        (diff > 1e-4) shouldBe true   // should be audibly different
        (diff < 0.5) shouldBe true    // but not insanely different
    }
})

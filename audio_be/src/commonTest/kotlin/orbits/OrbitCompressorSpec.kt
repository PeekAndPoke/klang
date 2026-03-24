package io.peekandpoke.klang.audio_be.orbits

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.peekandpoke.klang.audio_be.voices.Voice
import io.peekandpoke.klang.audio_be.voices.VoiceTestHelpers
import kotlin.math.abs

class OrbitCompressorSpec : StringSpec({

    val sampleRate = 44100
    val blockFrames = 128

    fun createOrbit() = Orbit(id = 0, blockFrames = blockFrames, sampleRate = sampleRate)

    fun voiceWithCompressor(
        thresholdDb: Double = -20.0,
        ratio: Double = 4.0,
        kneeDb: Double = 6.0,
        attackSeconds: Double = 0.003,
        releaseSeconds: Double = 0.1,
    ) = VoiceTestHelpers.createSynthVoice(
        compressor = Voice.Compressor(
            thresholdDb = thresholdDb,
            ratio = ratio,
            kneeDb = kneeDb,
            attackSeconds = attackSeconds,
            releaseSeconds = releaseSeconds,
        )
    )

    "orbit has no compressor by default" {
        val orbit = createOrbit()

        orbit.compressor.compressor shouldBe null
    }

    "compressor is created when first voice has compressor settings" {
        val orbit = createOrbit()
        orbit.updateFromVoice(voiceWithCompressor())

        orbit.compressor.compressor shouldNotBe null
    }

    "compressor parameters are set correctly on first voice" {
        val orbit = createOrbit()
        orbit.updateFromVoice(
            voiceWithCompressor(
                thresholdDb = -15.0,
                ratio = 3.0,
                kneeDb = 4.0,
                attackSeconds = 0.005,
                releaseSeconds = 0.2
            )
        )

        val c = orbit.compressor.compressor!!
        c.thresholdDb shouldBe -15.0
        c.ratio shouldBe 3.0
        c.kneeDb shouldBe 4.0
        c.attackSeconds shouldBe 0.005
        c.releaseSeconds shouldBe 0.2
    }

    "no compressor when voice has no compressor settings" {
        val orbit = createOrbit()
        orbit.updateFromVoice(VoiceTestHelpers.createSynthVoice())

        orbit.compressor.compressor shouldBe null
    }

    "compressor instance is reused on subsequent voices (not recreated)" {
        val orbit = createOrbit()
        orbit.updateFromVoice(voiceWithCompressor())
        val firstInstance = orbit.compressor.compressor

        orbit.updateFromVoice(voiceWithCompressor())

        orbit.compressor.compressor shouldBe firstInstance  // same reference, not a new object
    }

    "compressor parameters are updated without recreating the instance" {
        val orbit = createOrbit()
        orbit.updateFromVoice(voiceWithCompressor(thresholdDb = -20.0, ratio = 4.0))
        val firstInstance = orbit.compressor.compressor!!

        orbit.updateFromVoice(
            voiceWithCompressor(
                thresholdDb = -10.0,
                ratio = 8.0,
                kneeDb = 2.0,
                attackSeconds = 0.001,
                releaseSeconds = 0.05
            )
        )

        orbit.compressor.compressor shouldBe firstInstance   // same instance
        firstInstance.thresholdDb shouldBe -10.0  // parameters updated
        firstInstance.ratio shouldBe 8.0
        firstInstance.kneeDb shouldBe 2.0
        firstInstance.attackSeconds shouldBe 0.001
        firstInstance.releaseSeconds shouldBe 0.05
    }

    "envelope state is preserved across voice updates (not reset)" {
        val orbit = createOrbit()
        orbit.updateFromVoice(
            voiceWithCompressor(
                thresholdDb = -20.0,
                ratio = 4.0,
                kneeDb = 0.0,
                attackSeconds = 0.001,
                releaseSeconds = 0.1
            )
        )
        val compressor = orbit.compressor.compressor!!

        // Warm up the envelope follower with many blocks of loud signal (~-6 dB, well above threshold)
        repeat(50) {
            val l = FloatArray(blockFrames) { 0.5f }
            val r = FloatArray(blockFrames) { 0.5f }
            compressor.process(l, r, blockFrames)
        }

        // Measure steady-state compression level
        val steadyLeft = FloatArray(blockFrames) { 0.5f }
        val steadyRight = FloatArray(blockFrames) { 0.5f }
        compressor.process(steadyLeft, steadyRight, blockFrames)
        val steadyLevel = steadyLeft.map { abs(it) }.average()

        // Simulate next note — same settings, envelope must NOT be reset
        orbit.updateFromVoice(
            voiceWithCompressor(
                thresholdDb = -20.0,
                ratio = 4.0,
                kneeDb = 0.0,
                attackSeconds = 0.001,
                releaseSeconds = 0.1
            )
        )

        // Process immediately after — should be at roughly the same compression level
        val afterLeft = FloatArray(blockFrames) { 0.5f }
        val afterRight = FloatArray(blockFrames) { 0.5f }
        orbit.compressor.compressor!!.process(afterLeft, afterRight, blockFrames)
        val afterLevel = afterLeft.map { abs(it) }.average()

        afterLevel shouldBe (steadyLevel plusOrMinus 0.02)
    }
})

package io.peekandpoke.klang.audio_be.cylinders

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.peekandpoke.klang.audio_be.AudioBuffer
import io.peekandpoke.klang.audio_be.voices.Voice
import io.peekandpoke.klang.audio_be.voices.VoiceTestHelpers
import kotlin.math.abs

class OrbitCompressorSpec : StringSpec({

    val sampleRate = 44100
    val blockFrames = 128

    fun createOrbit() = Cylinder(id = 0, blockFrames = blockFrames, sampleRate = sampleRate)

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

    "cylinder has no compressor by default" {
        val cylinder = createOrbit()

        cylinder.compressor.compressor shouldBe null
    }

    "compressor is created when first voice has compressor settings" {
        val cylinder = createOrbit()
        cylinder.updateFromVoice(voiceWithCompressor())

        cylinder.compressor.compressor shouldNotBe null
    }

    "compressor parameters are set correctly on first voice" {
        val cylinder = createOrbit()
        cylinder.updateFromVoice(
            voiceWithCompressor(
                thresholdDb = -15.0,
                ratio = 3.0,
                kneeDb = 4.0,
                attackSeconds = 0.005,
                releaseSeconds = 0.2
            )
        )

        val c = cylinder.compressor.compressor!!
        c.thresholdDb shouldBe -15.0
        c.ratio shouldBe 3.0
        c.kneeDb shouldBe 4.0
        c.attackSeconds shouldBe 0.005
        c.releaseSeconds shouldBe 0.2
    }

    "no compressor when voice has no compressor settings" {
        val cylinder = createOrbit()
        cylinder.updateFromVoice(VoiceTestHelpers.createSynthVoice())

        cylinder.compressor.compressor shouldBe null
    }

    "compressor instance is reused on subsequent voices (not recreated)" {
        val cylinder = createOrbit()
        cylinder.updateFromVoice(voiceWithCompressor())
        val firstInstance = cylinder.compressor.compressor

        cylinder.updateFromVoice(voiceWithCompressor())

        cylinder.compressor.compressor shouldBe firstInstance  // same reference, not a new object
    }

    "compressor parameters are updated without recreating the instance" {
        val cylinder = createOrbit()
        cylinder.updateFromVoice(voiceWithCompressor(thresholdDb = -20.0, ratio = 4.0))
        val firstInstance = cylinder.compressor.compressor!!

        cylinder.updateFromVoice(
            voiceWithCompressor(
                thresholdDb = -10.0,
                ratio = 8.0,
                kneeDb = 2.0,
                attackSeconds = 0.001,
                releaseSeconds = 0.05
            )
        )

        cylinder.compressor.compressor shouldBe firstInstance   // same instance
        firstInstance.thresholdDb shouldBe -10.0  // parameters updated
        firstInstance.ratio shouldBe 8.0
        firstInstance.kneeDb shouldBe 2.0
        firstInstance.attackSeconds shouldBe 0.001
        firstInstance.releaseSeconds shouldBe 0.05
    }

    "envelope state is preserved across voice updates (not reset)" {
        val cylinder = createOrbit()
        cylinder.updateFromVoice(
            voiceWithCompressor(
                thresholdDb = -20.0,
                ratio = 4.0,
                kneeDb = 0.0,
                attackSeconds = 0.001,
                releaseSeconds = 0.1
            )
        )
        val compressor = cylinder.compressor.compressor!!

        // Warm up the envelope follower with many blocks of loud signal (~-6 dB, well above threshold)
        repeat(50) {
            val l = AudioBuffer(blockFrames) { 0.5 }
            val r = AudioBuffer(blockFrames) { 0.5 }
            compressor.process(l, r, blockFrames)
        }

        // Measure steady-state compression level
        val steadyLeft = AudioBuffer(blockFrames) { 0.5 }
        val steadyRight = AudioBuffer(blockFrames) { 0.5 }
        compressor.process(steadyLeft, steadyRight, blockFrames)
        val steadyLevel = steadyLeft.map { abs(it) }.average()

        // Simulate next note — same settings, envelope must NOT be reset
        cylinder.updateFromVoice(
            voiceWithCompressor(
                thresholdDb = -20.0,
                ratio = 4.0,
                kneeDb = 0.0,
                attackSeconds = 0.001,
                releaseSeconds = 0.1
            )
        )

        // Process immediately after — should be at roughly the same compression level
        val afterLeft = AudioBuffer(blockFrames) { 0.5 }
        val afterRight = AudioBuffer(blockFrames) { 0.5 }
        cylinder.compressor.compressor!!.process(afterLeft, afterRight, blockFrames)
        val afterLevel = afterLeft.map { abs(it) }.average()

        afterLevel shouldBe (steadyLevel plusOrMinus 0.02)
    }
})

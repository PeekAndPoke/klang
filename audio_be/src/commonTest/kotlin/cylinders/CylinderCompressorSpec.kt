/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.cylinders

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.peekandpoke.klang.audio_be.AudioBuffer
import io.peekandpoke.klang.audio_be.voices.Voice
import io.peekandpoke.klang.audio_be.voices.VoiceTestHelpers
import io.peekandpoke.klang.audio_bridge.constants.KNOB_GLIDE_SECONDS
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
        // The bus compressor reads the orbit's SLOT state since Katalyst step 5b-1; every knob is
        // named here, which is what a `compressor(...)` call writes through the door's fill rule.
        katalystParams = mapOf(
            "compressor.threshold" to thresholdDb,
            "compressor.ratio" to ratio,
            "compressor.knee" to kneeDb,
            "compressor.attack" to attackSeconds,
            "compressor.release" to releaseSeconds,
        )
    )

    "cylinder has no compressor by default" {
        val cylinder = createOrbit()

        cylinder.compressor!!.compressor shouldBe null
    }

    "compressor is created when first voice has compressor settings" {
        val cylinder = createOrbit()
        cylinder.updateFromVoice(voiceWithCompressor(), blockStart = 0.0)

        cylinder.compressor!!.compressor shouldNotBe null
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
            ),
            blockStart = 0.0,
        )

        val c = cylinder.compressor!!.compressor!!
        c.thresholdDb shouldBe -15.0
        c.ratio shouldBe 3.0
        c.kneeDb shouldBe 4.0
        c.attackSeconds shouldBe 0.005
        c.releaseSeconds shouldBe 0.2
    }

    "no compressor when voice has no compressor settings" {
        val cylinder = createOrbit()
        cylinder.updateFromVoice(VoiceTestHelpers.createSynthVoice(), blockStart = 0.0)

        cylinder.compressor!!.compressor shouldBe null
    }

    "compressor instance is reused on subsequent voices (not recreated)" {
        val cylinder = createOrbit()
        cylinder.updateFromVoice(voiceWithCompressor(), blockStart = 0.0)
        val firstInstance = cylinder.compressor!!.compressor

        cylinder.updateFromVoice(voiceWithCompressor(), blockStart = 0.0)

        cylinder.compressor!!.compressor shouldBe firstInstance  // same reference, not a new object
    }

    "a second voice does NOT change the owner's compressor while the owner is alive (first-writer-wins)" {
        val cylinder = createOrbit()
        cylinder.updateFromVoice(voiceWithCompressor(thresholdDb = -20.0, ratio = 4.0), blockStart = 0.0)
        val firstInstance = cylinder.compressor!!.compressor!!

        // Different voice, same block → denied by the single orbit lease.
        cylinder.updateFromVoice(voiceWithCompressor(thresholdDb = -10.0, ratio = 8.0), blockStart = 0.0)

        cylinder.compressor!!.compressor shouldBe firstInstance  // same instance, untouched
        firstInstance.thresholdDb shouldBe -20.0               // owner's params, NOT the second voice's
        firstInstance.ratio shouldBe 4.0
    }

    "when the compressor owner ends, a later voice takes over and its compressor params apply" {
        val cylinder = createOrbit()
        cylinder.updateFromVoice(voiceWithCompressor(thresholdDb = -20.0), blockStart = 0.0)
        cylinder.compressor!!.compressor!!.thresholdDb shouldBe -20.0

        // Owner stops checking in; a new compressor voice claims after the one-block grace.
        cylinder.updateFromVoice(voiceWithCompressor(thresholdDb = -8.0), blockStart = 2.0 * blockFrames)
        cylinder.compressor!!.compressor!!.thresholdDb shouldBe -8.0 // new owner's params (instance reused)
    }

    "when a non-compressor voice takes over the orbit before anything sounded, the compressor is cleared at once" {
        val cylinder = createOrbit()
        cylinder.updateFromVoice(voiceWithCompressor(), blockStart = 0.0)
        cylinder.compressor!!.compressor shouldNotBe null

        // Compressor owner ends; a plain voice (no compressor) becomes the owner → compressor cleared.
        cylinder.updateFromVoice(VoiceTestHelpers.createSynthVoice(), blockStart = 2.0 * blockFrames)
        cylinder.compressor!!.compressor shouldBe null
    }

    "when a non-compressor voice takes over a SOUNDING orbit, the compressor glides out and only then goes" {
        val cylinder = createOrbit()
        val compressing = voiceWithCompressor()
        val plain = VoiceTestHelpers.createSynthVoice()
        // 2205 samples at 44.1 kHz: the fade lands inside its 18th block.
        val fadeBlocks = (sampleRate * KNOB_GLIDE_SECONDS / blockFrames).toInt() + 1

        fun block(b: Int, voice: Voice) {
            cylinder.updateFromVoice(voice, blockStart = b * blockFrames.toDouble())
            cylinder.mixBuffer.left.fill(0.5)
            cylinder.mixBuffer.right.fill(0.5)
            cylinder.processEffects()
        }

        block(0, compressing)
        block(1, compressing)
        val instance = cylinder.compressor!!.compressor.shouldNotBeNull()

        // The owner lapses; the plain voice claims the lease on block 3 (the one-block grace).
        block(2, plain)
        cylinder.compressor!!.compressor shouldBeSameInstanceAs instance

        for (b in 3 until 3 + fadeBlocks - 1) {
            block(b, plain)

            withClue("block $b: still gliding out, the instance is kept") {
                cylinder.compressor!!.compressor.shouldNotBeNull() shouldBeSameInstanceAs instance
            }
        }

        block(3 + fadeBlocks - 1, plain)

        withClue("the gain reduction has landed on 0 dB: the stage is off") {
            cylinder.compressor!!.compressor shouldBe null
        }
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
            ),
            blockStart = 0.0,
        )
        val compressor = cylinder.compressor!!.compressor!!

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
            ),
            blockStart = 0.0,
        )

        // Process immediately after — should be at roughly the same compression level
        val afterLeft = AudioBuffer(blockFrames) { 0.5 }
        val afterRight = AudioBuffer(blockFrames) { 0.5 }
        cylinder.compressor!!.compressor!!.process(afterLeft, afterRight, blockFrames)
        val afterLevel = afterLeft.map { abs(it) }.average()

        afterLevel shouldBe (steadyLevel plusOrMinus 0.02)
    }
})

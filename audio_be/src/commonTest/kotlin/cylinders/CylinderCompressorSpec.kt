/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.cylinders

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.peekandpoke.klang.audio_be.voices.Voice
import io.peekandpoke.klang.audio_be.voices.VoiceTestHelpers
import io.peekandpoke.klang.audio_bridge.constants.KNOB_GLIDE_SECONDS

/**
 * The orbit compressor's SWITCHING on the cylinder: an untouched orbit has none, and a takeover by
 * a voice that names none clears it at once or glides it out on a sounding orbit.
 *
 * What the slots resolve to is `KatalystSlotResolverSpec`'s, the host wiring of all five knobs is
 * `OrbitBusPipelineSpec`'s one updateFromVoice row, who may write them (the lease) is
 * `CylinderKatalystParamsSpec`'s, and the reused instance with its envelope is
 * `KatalystCompressorEffectSpec`'s knob rows (a bare-compressor oracle run across the change).
 */
class OrbitCompressorSpec : StringSpec({

    val sampleRate = 44100
    val blockFrames = 128

    fun createOrbit() = Cylinder(id = 0, blockFrames = blockFrames, sampleRate = sampleRate)

    fun voiceWithCompressor() = VoiceTestHelpers.createSynthVoice(
        // The bus compressor reads the orbit's SLOT state since Katalyst step 5b-1; every knob is
        // named here, which is what a `compressor(...)` call writes through the door's fill rule.
        katalystParams = mapOf(
            "compressor.threshold" to -20.0,
            "compressor.ratio" to 4.0,
            "compressor.knee" to 6.0,
            "compressor.attack" to 0.003,
            "compressor.release" to 0.1,
        )
    )

    "an untouched orbit has no compressor, before any voice and after a voice that names none" {
        val cylinder = createOrbit()

        cylinder.compressor!!.compressor shouldBe null

        cylinder.updateFromVoice(VoiceTestHelpers.createSynthVoice(), blockStart = 0.0)

        cylinder.compressor!!.compressor shouldBe null
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
})

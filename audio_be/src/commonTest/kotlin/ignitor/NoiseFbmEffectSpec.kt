/*
 * Copyright (C) 2025-2026 The Klang Audio Motör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.ignitor

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.peekandpoke.klang.audio_be.AudioBuffer
import kotlin.random.Random

/**
 * Render-effect guard for the fBm knobs (`octaves` / `persistence`) on perlin / berlin noise.
 *
 * Perlin/Berlin draw a random start position, so we seed the rng identically per pair to make the
 * render deterministic — then any difference isolates the knob. If a future edit drops the octaves
 * or persistence threading in [Ignitors.perlinNoise]/[Ignitors.berlinNoise], these go red.
 *
 * The default (`octaves = 1`) must be byte-identical to the pre-fBm single-octave call — that is the
 * perf-neutral-by-default guarantee, checked here against a plain rate-only construction.
 */
class NoiseFbmEffectSpec : StringSpec({

    val sampleRate = 44100
    val blockFrames = 256

    fun createCtx(): IgniteContext = IgniteContext(
        sampleRate = sampleRate,
        voiceDurationFrames = sampleRate,
        gateEndFrame = sampleRate,
        releaseFrames = (0.1 * sampleRate).toInt(),
        voiceEndFrame = sampleRate + (0.1 * sampleRate).toInt(),
        scratchBuffers = ScratchBuffers(blockFrames),
    ).apply {
        offset = 0
        length = blockFrames
        voiceElapsedFrames = 0
    }

    fun render(ign: Ignitor): List<Double> {
        val buffer = AudioBuffer(blockFrames)
        ign.generate(buffer, 0.0, createCtx())
        return buffer.toList()
    }

    "perlin: octaves reaches the audio (seeded → deterministic)" {
        render(Ignitors.perlinNoise(Random(7), octaves = ConstantIgnitor(1.0))) shouldNotBe
                render(Ignitors.perlinNoise(Random(7), octaves = ConstantIgnitor(4.0)))
    }

    "perlin: octaves=1 default is byte-identical to the plain rate-only call (perf-neutral default)" {
        render(Ignitors.perlinNoise(Random(7))) shouldBe
                render(Ignitors.perlinNoise(Random(7), octaves = ConstantIgnitor(1.0)))
    }

    "perlin: persistence reaches the audio (with octaves > 1)" {
        render(Ignitors.perlinNoise(Random(7), octaves = ConstantIgnitor(4.0), persistence = ConstantIgnitor(0.3))) shouldNotBe
                render(Ignitors.perlinNoise(Random(7), octaves = ConstantIgnitor(4.0), persistence = ConstantIgnitor(0.8)))
    }

    "berlin: octaves reaches the audio (seeded → deterministic)" {
        render(Ignitors.berlinNoise(Random(11), octaves = ConstantIgnitor(1.0))) shouldNotBe
                render(Ignitors.berlinNoise(Random(11), octaves = ConstantIgnitor(4.0)))
    }

    "berlin: persistence reaches the audio (with octaves > 1)" {
        render(Ignitors.berlinNoise(Random(11), octaves = ConstantIgnitor(4.0), persistence = ConstantIgnitor(0.3))) shouldNotBe
                render(Ignitors.berlinNoise(Random(11), octaves = ConstantIgnitor(4.0), persistence = ConstantIgnitor(0.8)))
    }
})

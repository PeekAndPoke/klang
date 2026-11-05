/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.ignitor

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.peekandpoke.klang.audio_be.AudioBuffer
import kotlin.random.Random

/** This file's one seeded stream: every run draws the same, and successive builds still draw
 *  differently (as they did from the process-wide stream these calls used before). */
private val testRandom = Random(0x5EED)

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
        scratchBuffers = ScratchBuffers(blockFrames),
        random = testRandom,
    ).apply {
        updateOffsetAndLength(offset = 0, length = blockFrames)
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

    "perlin and berlin walk per second: one second lands on the same noise at 44.1, 48 and 96 kHz" {
        // `rate` is a speed per second (maintainer, 2026-11-05, KLANG-00290 (engine follow-ups) item 37, option A:
        // rate 1 is 144 noise units per second, the old per-sample walk's speed at 48 kHz). Before, the walk took a
        // fixed step per sample: 8 % slower at 44.1 kHz, twice as fast on a 96 kHz host.
        fun lastOfOneSecond(rate: Int, make: (Random) -> Ignitor): Double {
            val ign = make(Random(7))
            val buffer = AudioBuffer(blockFrames)
            val ctx = IgniteContext(
                sampleRate = rate,
                voiceDurationFrames = rate,
                gateEndFrame = rate,
                scratchBuffers = ScratchBuffers(blockFrames),
                random = testRandom,
            )
            var done = 0
            var last = 0.0

            // rate + 1 samples: the last one is the walk at exactly one second (sample 0 is at 0 s)
            while (done < rate + 1) {
                val n = minOf(blockFrames, rate + 1 - done)
                ctx.updateOffsetAndLength(offset = 0, length = n)
                ctx.voiceElapsedFrames = done
                ign.generate(buffer, 0.0, ctx)
                last = buffer[n - 1]
                done += n
            }

            return last
        }

        val noises = listOf<Pair<String, (Random) -> Ignitor>>(
            "perlin" to { rng -> Ignitors.perlinNoise(rng, rate = ConstantIgnitor(0.7)) },
            "berlin" to { rng -> Ignitors.berlinNoise(rng, rate = ConstantIgnitor(0.7)) },
        )

        for ((name, make) in noises) {
            val at48 = lastOfOneSecond(48000, make)

            withClue("$name at 44.1 kHz") { lastOfOneSecond(44100, make) shouldBe (at48 plusOrMinus 1e-6) }
            withClue("$name at 96 kHz") { lastOfOneSecond(96000, make) shouldBe (at48 plusOrMinus 1e-6) }
        }
    }
})

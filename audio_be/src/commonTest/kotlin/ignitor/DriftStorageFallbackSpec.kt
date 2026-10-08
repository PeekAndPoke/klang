/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.ignitor

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_be.AudioBuffer
import kotlin.random.Random

/**
 * Drift storage is built with the node only when the build reads `analog` above 0 (or cannot read it), and the shared
 * lane only when `analogSpread` may drop below 1 (tidy-up step 10, review round 1). The build reads those knobs at
 * frequency 0, so a knob that reads the frequency can look like "no drift" at build and drift at the first block.
 * Then the storage is made at render, as before the step. These rows pin that such a voice renders and draws exactly
 * like one whose build saw the same values: a knob that reads the frequency against a [Hold] of the same number,
 * which the build cannot read and so always builds for.
 */
class DriftStorageFallbackSpec : StringSpec({

    val blockFrames = 128
    val freqHz = 220.0

    /** A constant the build cannot read (not block-constant), so a node built with it builds its drift storage. */
    class Hold(private val value: Double) : Ignitor {
        override fun generate(buffer: AudioBuffer, freqHz: Double, ctx: IgniteContext) {
            for (i in ctx.offset until ctx.windowEnd) {
                buffer[i] = value
            }
        }
    }

    /** 32 blocks of [build]'s node at [freqHz], then the stream's next draw (the draws must match too). */
    fun render(build: (random: Random) -> Ignitor): Pair<DoubleArray, Int> {
        val random = Random(17)
        val node = build(random)
        val ctx = IgniteContext(
            sampleRate = 48000, voiceDurationFrames = blockFrames * 64, gateEndFrame = blockFrames * 64,
            scratchBuffers = ScratchBuffers(blockFrames), random = random,
        )
        val buf = AudioBuffer(blockFrames)
        val out = DoubleArray(blockFrames * 32)

        for (b in 0 until 32) {
            ctx.updateOffsetAndLength(offset = 0, length = blockFrames)
            node.generate(buf, freqHz, ctx)

            for (i in 0 until blockFrames) {
                out[b * blockFrames + i] = buf[i]
            }

            ctx.voiceElapsedFrames += blockFrames
        }

        return out to random.nextInt()
    }

    fun sameRender(name: String, guessedWrong: (Random) -> Ignitor, seen: (Random) -> Ignitor) {
        val (a, aNext) = render(guessedWrong)
        val (b, bNext) = render(seen)

        withClue("$name: the stream after the render") { aNext shouldBe bNext }

        for (i in a.indices) {
            withClue("$name: sample $i") { a[i].toRawBits() shouldBe b[i].toRawBits() }
        }
    }

    // `analog = freq * 0.002`: 0 at build (no drift storage built), 0.44 at the first block.
    val analogFromFreq = FreqIgnitor * ConstantIgnitor(0.002)
    val analogSeen = Hold(freqHz * 0.002)

    // `analogSpread = 1 - freq * 0.001`: 1 at build (no shared lane built), 0.78 at the first block.
    val spreadFromFreq = ConstantIgnitor(1.0).minus(FreqIgnitor * ConstantIgnitor(0.001))
    val spreadSeen = Hold(1.0 - freqHz * 0.001)

    "a wave whose build read analog 0 still drifts, from a lane made at its first block" {
        sameRender(
            name = "saw",
            guessedWrong = { Ignitors.saw(analog = analogFromFreq) },
            seen = { Ignitors.saw(analog = analogSeen) },
        )
    }

    "a stack whose build read analog 0 still drifts, from a container made at its first block" {
        sameRender(
            name = "supersaw",
            guessedWrong = { r -> Ignitors.superSaw(voices = ConstantIgnitor(5.0), analog = analogFromFreq, rng = r) },
            seen = { r -> Ignitors.superSaw(voices = ConstantIgnitor(5.0), analog = analogSeen, rng = r) },
        )
    }

    "a stack whose build read spread 1 still blends the shared walk, from a lane made below spread 1" {
        sameRender(
            name = "supersaw",
            guessedWrong = { r -> Ignitors.superSaw(voices = ConstantIgnitor(5.0), analog = ConstantIgnitor(0.5), analogSpread = spreadFromFreq, rng = r) },
            seen = { r -> Ignitors.superSaw(voices = ConstantIgnitor(5.0), analog = ConstantIgnitor(0.5), analogSpread = spreadSeen, rng = r) },
        )
    }

    "a partial bank and a superpluck whose build read analog 0 still drift" {
        sameRender(
            name = "sine partials",
            guessedWrong = { Ignitors.sinePartials(analog = analogFromFreq, harmonics = ConstantIgnitor(3.0)) },
            seen = { Ignitors.sinePartials(analog = analogSeen, harmonics = ConstantIgnitor(3.0)) },
        )
        sameRender(
            name = "superpluck",
            guessedWrong = { r -> Ignitors.superKarplusStrong(voices = ConstantIgnitor(3.0), analog = analogFromFreq, rng = r) },
            seen = { r -> Ignitors.superKarplusStrong(voices = ConstantIgnitor(3.0), analog = analogSeen, rng = r) },
        )
    }
})

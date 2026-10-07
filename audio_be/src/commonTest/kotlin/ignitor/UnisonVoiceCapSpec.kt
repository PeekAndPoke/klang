/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.ignitor

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_be.AudioBuffer
import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import io.peekandpoke.klang.audio_bridge.UNISON_MAX_VOICES
import kotlin.math.abs
import kotlin.random.Random

/** This file's one seeded stream: every run draws the same, and successive builds still draw
 *  differently (as they did from the process-wide stream these calls used before). */
private val testRandom = Random(0x5EED)

/**
 * A unison voice count is a RESOURCE count: it sizes arrays at note-on on the render thread. User input
 * (`voices(1e9)`, an infinite signal) used to reach `Array(v)` unbounded. `coerceUnisonVoices` caps it at
 * [UNISON_MAX_VOICES] and reads a non-finite count as no voices, in the super oscillators, the super pluck and the
 * graph census alike (`/code-style` §21: counts are coerced, never asserted).
 */
class UnisonVoiceCapSpec : StringSpec({

    val sampleRate = 44100
    val blockFrames = 128
    val blocks = 8

    fun c(v: Double) = IgnitorDsl.Constant(v)

    fun render(dsl: IgnitorDsl): DoubleArray {
        val ig = dsl.toExciter(random = Random(7))
        val ctx = IgniteContext(
            sampleRate = sampleRate, voiceDurationFrames = 8192, gateEndFrame = 8192,
            scratchBuffers = ScratchBuffers(blockFrames),
            random = testRandom,
        )
        val tmp = AudioBuffer(blockFrames)
        val out = DoubleArray(blocks * blockFrames)

        for (block in 0 until blocks) {
            ctx.updateOffsetAndLength(offset = 0, length = blockFrames)
            ctx.voiceElapsedFrames = block * blockFrames
            ig.generate(tmp, 110.0, ctx)
            tmp.copyInto(destination = out, destinationOffset = block * blockFrames, startIndex = 0, endIndex = blockFrames)
        }

        return out
    }

    fun peak(x: DoubleArray): Double = x.maxOf { abs(it) }

    fun maxDiff(a: DoubleArray, b: DoubleArray): Double = a.indices.maxOf { abs(a[it] - b[it]) }

    /** Both runtime sites: the super oscillators share one generate, the super pluck has its own. */
    val sources: List<Pair<String, (IgnitorDsl) -> IgnitorDsl>> = listOf(
        "supersaw" to { voices -> IgnitorDsl.SuperSaw(voices = voices) },
        "superpluck" to { voices -> IgnitorDsl.SuperPluck(voices = voices) },
    )

    "a huge count, voices(1e9), plays exactly as the cap and does not throw" {
        for ((name, make) in sources) {
            withClue(name) {
                val capped = render(make(c(UNISON_MAX_VOICES.toDouble())))
                val huge = render(make(c(1e9)))

                peak(capped) shouldBeGreaterThan 0.01
                maxDiff(a = huge, b = capped) shouldBe 0.0
            }
        }
    }

    "an infinite count plays as no voices, silence, and does not throw" {
        for ((name, make) in sources) {
            withClue(name) {
                peak(render(make(c(Double.POSITIVE_INFINITY)))) shouldBe 0.0
            }
        }
    }

    "the graph census counts what renders: a huge count as the cap, an infinite one as none" {
        GraphCensus.of(IgnitorDsl.SuperSaw(voices = c(1e9))) shouldBe GraphCensus.of(IgnitorDsl.SuperSaw(voices = c(UNISON_MAX_VOICES.toDouble())))
        GraphCensus.of(IgnitorDsl.SuperPluck(voices = c(1e9))) shouldBe GraphCensus.of(IgnitorDsl.SuperPluck(voices = c(UNISON_MAX_VOICES.toDouble())))
        GraphCensus.of(IgnitorDsl.SuperSaw(voices = c(Double.POSITIVE_INFINITY))) shouldBe GraphCensus.of(IgnitorDsl.SuperSaw(voices = c(0.0)))
        // The pattern-level path, `unison(1e9)`: the count arrives in the voice's params and is read through the Param arm.
        GraphCensus.of(IgnitorDsl.SuperSaw(), params = mapOf("voices" to 1e9)) shouldBe
            GraphCensus.of(IgnitorDsl.SuperSaw(), params = mapOf("voices" to UNISON_MAX_VOICES.toDouble()))
    }
})

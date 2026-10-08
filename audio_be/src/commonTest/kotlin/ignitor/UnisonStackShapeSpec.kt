/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.ignitor

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_be.AudioBuffer
import kotlin.math.abs
import kotlin.random.Random

/**
 * The unison stack's voice shape per kind (engine tidy-up step 11 made the five stacks one node with a kind). A NaN
 * frequency is where the kinds part ways: the increment is NaN, the wrap scrubs every voice's phase to 0 after its
 * first sample, and what the stack then holds is its shape at phase 0, which each kind configures its own way.
 *
 *  - The saw and the ramp: the flyback is NaN and the NaN guard replaces it by `shapeMax`, so the voice holds the
 *    bottom of the rise, -1 (the ramp's negative polarity makes it +1).
 *  - The square and the triangle: the flank floor is NaN and has no guard, and needs none, because
 *    `coerceAtLeast(NaN)` keeps the flank: the floor drops out. The square's rise flank is 0, so without the floor
 *    its edge is instant and phase 0 is already on the high plateau, +1 (at a real frequency it is -1, the foot of
 *    the edge); the triangle's flanks are open, so phase 0 is its lowest point, -1.
 *  - The sine carries no shape: sin(0), 0.
 *
 * And the phased loops wrap as the plain ones do, at an increment of a cycle or more per sample too.
 */
class UnisonStackShapeSpec : StringSpec({

    val blockFrames = 128

    /** Renders [blocks] blocks from a mid-block start and returns every sample but the first (the voices' start phases). */
    fun held(ignitor: Ignitor, blocks: Int = 6): DoubleArray {
        val ctx = IgniteContext(
            sampleRate = 48000, voiceDurationFrames = 48000, gateEndFrame = 48000,
            scratchBuffers = ScratchBuffers(blockFrames), random = Random(11),
        )
        val buf = AudioBuffer(blockFrames)
        val out = ArrayList<Double>()

        for (b in 0 until blocks) {
            val offset = if (b == 0) 37 else 0

            ctx.updateOffsetAndLength(offset = offset, length = blockFrames - offset)
            ignitor.generate(buf, 220.0, ctx)

            for (i in offset until blockFrames) {
                out.add(buf[i])
            }

            ctx.voiceElapsedFrames += blockFrames - offset
        }

        return out.subList(1, out.size).toDoubleArray()
    }

    "at a NaN frequency each kind holds its shape at phase 0: saw -1, ramp +1, square +1, triangle -1, sine 0" {
        val nan = ConstantIgnitor(Double.NaN)
        val five = ConstantIgnitor(5.0)
        val kinds: List<Triple<String, Ignitor, Double>> = listOf(
            Triple("supersaw", Ignitors.superSawRaw(freq = nan, voices = five, rng = Random(3)), -1.0),
            Triple("superramp", Ignitors.superRamp(freq = nan, voices = five, rng = Random(3)), 1.0),
            Triple("supersquare", Ignitors.superSquare(freq = nan, voices = five, rng = Random(3)), 1.0),
            Triple("supertri", Ignitors.superTri(freq = nan, voices = five, rng = Random(3)), -1.0),
            Triple("supersine", Ignitors.superSine(freq = nan, voices = five, rng = Random(3)), 0.0),
        )

        for ((name, ignitor, level) in kinds) {
            withClue(name) {
                val x = held(ignitor)

                // Held: every sample the same double (the voice gains sum to 1 up to rounding).
                for (v in x) {
                    v.toRawBits() shouldBe x[0].toRawBits()
                }

                abs(x[0] - level) shouldBeLessThan 1e-12
            }
        }
    }

    "at |dt| >= 1 a moving phase that is 0 everywhere renders the plain stack bit for bit, every kind (the safe wrap)" {
        // At +-60 kHz and 48 kHz the increment is 1.25 cycles per sample: only the safe wrap keeps the phase in
        // [0, 1). The phased loops read `phase + 0`, so with the same wrap they render exactly the plain loops' bits.
        fun stack(kind: String, freq: Double, phase: Ignitor?): Ignitor {
            val f = ConstantIgnitor(freq)
            val v = ConstantIgnitor(5.0)

            return when (kind) {
                "saw" -> Ignitors.superSawRaw(freq = f, voices = v, rng = Random(3), phase = phase)
                "ramp" -> Ignitors.superRamp(freq = f, voices = v, rng = Random(3), phase = phase)
                "square" -> Ignitors.superSquare(freq = f, voices = v, rng = Random(3), phase = phase)
                "tri" -> Ignitors.superTri(freq = f, voices = v, rng = Random(3), phase = phase)
                else -> Ignitors.superSine(freq = f, voices = v, rng = Random(3), phase = phase)
            }
        }

        for (kind in listOf("saw", "ramp", "square", "tri", "sine")) {
            for (freq in listOf(60000.0, -60000.0)) {
                withClue("$kind at $freq Hz") {
                    val plain = held(stack(kind = kind, freq = freq, phase = null), blocks = 24)
                    val phased = held(stack(kind = kind, freq = freq, phase = ArrayIgnitor(DoubleArray(blockFrames * 24))), blocks = 24)

                    phased.map { it.toRawBits() } shouldBe plain.map { it.toRawBits() }
                }
            }
        }
    }
})

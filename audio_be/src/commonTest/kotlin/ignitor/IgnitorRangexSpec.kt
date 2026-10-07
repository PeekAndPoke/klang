/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.ignitor

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_be.AudioBuffer
import io.peekandpoke.klang.audio_be.utils.FAST_EXP2_MAX_REL_ERROR
import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import io.peekandpoke.klang.audio_bridge.optimize
import io.peekandpoke.klang.audio_bridge.range
import io.peekandpoke.klang.audio_bridge.rangex
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * The Ignitor `rangex(from, to)` rendered by the engine (`docs/tasks-archive/2026-10/20261005-sprudel-signals-range-cleanup.md` decision 16).
 * It has no node of its own: the door composes `exp(range(ln(max(from, floor)), ln(max(to, floor))))`, so these rows
 * check what the composition renders, `from · (to / from)^((x + 1) / 2)`:
 *
 *  - the three anchor points: `from` at `x = -1`, the geometric mean at `x = 0`, `to` at `x = 1`;
 *  - the same upside down (`rangex(1600, 100)`);
 *  - the law per sample over a moving signal;
 *  - the coercion of a value at or below 0 to the floor (0.0001): finite, never `ln(0)`;
 *  - a SIGNAL bound that dips below 0, per sample (the scratch loop of `Range`, `Max` and `Log` per sample);
 *  - a NaN bound reads as the floor: the reason the floor is `Max`'s right operand.
 *
 * The tolerance is relative, [FAST_EXP2_MAX_REL_ERROR] with a margin for the rounding of the logarithms and the
 * product: the engine's `exp` is `fastExp` (a polynomial, relative error under 1e-10), not the platform `exp`, so a
 * few ulp would be the wrong promise.
 */
class IgnitorRangexSpec : StringSpec({

    val blockFrames = 128
    val sampleRate = 48_000
    val tolerance = 10 * FAST_EXP2_MAX_REL_ERROR

    fun c(v: Double) = IgnitorDsl.Constant(v)

    /** One block of [dsl], optimized and built the way the registry builds a voice, then rendered. */
    fun render(dsl: IgnitorDsl): DoubleArray {
        val ignitor = dsl.optimize().buildExciter(random = Random(7), freqHz = 220.0, sampleRate = sampleRate).ignitor
        val ctx = IgniteContext(
            sampleRate = sampleRate,
            voiceDurationFrames = blockFrames * 4,
            gateEndFrame = blockFrames * 4,
            scratchBuffers = ScratchBuffers(blockFrames),
            random = Random(7),
        ).apply {
            updateOffsetAndLength(offset = 0, length = blockFrames)
            voiceElapsedFrames = 0
        }
        val buffer = AudioBuffer(blockFrames)
        ignitor.generate(buffer, 220.0, ctx)

        return DoubleArray(blockFrames) { buffer[it] }
    }

    fun close(actual: Double, expected: Double) {
        withClue("actual $actual, expected $expected") {
            (abs(actual - expected) <= tolerance * abs(expected)) shouldBe true
        }
    }

    fun rendered(x: Double, from: Double, to: Double): Double = render(c(x).rangex(from = c(from), to = c(to))).first()

    "rangex(100, 1600): from at -1, the geometric mean at 0, to at 1" {
        close(actual = rendered(x = -1.0, from = 100.0, to = 1600.0), expected = 100.0)
        close(actual = rendered(x = 0.0, from = 100.0, to = 1600.0), expected = 400.0)
        close(actual = rendered(x = 1.0, from = 100.0, to = 1600.0), expected = 1600.0)
        // and equal ratios between: a quarter of the swing is one octave of the four
        close(actual = rendered(x = -0.5, from = 100.0, to = 1600.0), expected = 200.0)
        close(actual = rendered(x = 0.5, from = 100.0, to = 1600.0), expected = 800.0)
    }

    "rangex(1600, 100) turns the swing upside down" {
        close(actual = rendered(x = -1.0, from = 1600.0, to = 100.0), expected = 1600.0)
        close(actual = rendered(x = 0.0, from = 1600.0, to = 100.0), expected = 400.0)
        close(actual = rendered(x = 1.0, from = 1600.0, to = 100.0), expected = 100.0)
    }

    "the law holds per sample over a moving signal" {
        val lfo = IgnitorDsl.Sine(freq = c(300.0), analog = c(0.0))
        val x = render(lfo)
        val y = render(lfo.rangex(from = c(200.0), to = c(3200.0)))

        // Not vacuous: the signal sweeps most of its swing in this block
        (x.max() - x.min()) shouldBeGreaterThan 1.5

        for (i in 0 until blockFrames) {
            withClue("sample $i, x = ${x[i]}") {
                close(actual = y[i], expected = 200.0 * (3200.0 / 200.0).pow((x[i] + 1.0) / 2.0))
            }
        }
    }

    "a value at or below 0 is coerced to the floor 0.0001: finite, never ln(0)" {
        close(actual = rendered(x = -1.0, from = 0.0, to = 1000.0), expected = 0.0001)
        close(actual = rendered(x = 1.0, from = 0.0, to = 1000.0), expected = 1000.0)
        close(actual = rendered(x = 0.0, from = 0.0, to = 1000.0), expected = sqrt(0.0001 * 1000.0))
        // a negative value is the same floor, not a mirrored logarithm
        close(actual = rendered(x = -1.0, from = -5.0, to = 1000.0), expected = 0.0001)
        close(actual = rendered(x = 1.0, from = 1000.0, to = 0.0), expected = 0.0001)

        val sweep = render(IgnitorDsl.Sine(freq = c(300.0), analog = c(0.0)).rangex(from = c(0.0), to = c(1000.0)))
        sweep.forEach { it.isFinite() shouldBe true }
        sweep.min() shouldBeGreaterThan 0.0
        sweep.max() shouldBeLessThan 1000.0 * (1.0 + tolerance)
    }

    "a signal bound that dips below 0 is floored per sample, and the law holds per sample" {
        val lfo = IgnitorDsl.Sine(freq = c(300.0), analog = c(0.0))
        // A bound that swings from -50 to 400 Hz: below 0 for part of the block, so the floor works per sample
        val fromSignal = IgnitorDsl.Sine(freq = c(400.0), analog = c(0.0)).range(from = c(-50.0), to = c(400.0))
        val x = render(lfo)
        val f = render(fromSignal)
        val y = render(lfo.rangex(from = fromSignal, to = c(3200.0)))

        // Not vacuous: the bound is below 0 for some samples and well above the floor for others
        f.min() shouldBeLessThan 0.0
        f.max() shouldBeGreaterThan 100.0

        for (i in 0 until blockFrames) {
            withClue("sample $i, x = ${x[i]}, from = ${f[i]}") {
                val from = if (f[i] > 0.0001) f[i] else 0.0001
                close(actual = y[i], expected = from * (3200.0 / from).pow((x[i] + 1.0) / 2.0))
            }
        }
    }

    "a NaN bound reads as the floor" {
        // With the floor on the other side of Max, a NaN would pass through and the engine's Log maps NaN to 0: the
        // first two rows would render 1, not the floor. A constant (or block-constant) NaN is the case that reaches
        // the bound; the arithmetic nodes end in `safeOut`, which maps NaN to 0, so a signal does not carry one.
        close(actual = rendered(x = -1.0, from = Double.NaN, to = 1000.0), expected = 0.0001)
        close(actual = rendered(x = 1.0, from = 1000.0, to = Double.NaN), expected = 0.0001)
        close(actual = rendered(x = 0.0, from = Double.NaN, to = 1000.0), expected = sqrt(0.0001 * 1000.0))
    }
})

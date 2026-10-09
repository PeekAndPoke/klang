/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.utils

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

/**
 * [linearFadeWeight] and [crossfadeLinear], the one fade law of the bank swap (`KatalystFilterSwap`) and the
 * compressor's switch (`KatalystCompressorEffect`): `w = weightTo + (weightFrom - weightTo) * (remaining * invLength)`
 * counted down to the landing, and `out = base + w * (other - base)`.
 *
 * Every row compares RAW BITS against the law written here from scratch, in that operand order: a reassociated
 * blend or weight renders a different double and goes red. A NaN compares by being NaN (its payload is no stable
 * observable on the JVM, engine tidy-up step 11 (d1)).
 */
class LinearCrossfadeSpec : StringSpec({

    /** The bits, or one marker for every NaN. */
    fun bits(x: Double): Long = if (x != x) 0x7ff8000000000000L else x.toRawBits() // NaN-guard

    fun lawWeight(from: Double, to: Double, remaining: Int, inv: Double): Double = to + (from - to) * (remaining * inv)

    fun lawSample(base: Double, other: Double, w: Double): Double = base + w * (other - base)

    /** A deterministic source with the hostile values woven in. */
    fun source(count: Int, seed: Int): DoubleArray {
        var s = seed

        return DoubleArray(count) { i ->
            s = s * 1103515245 + 12345

            when ((i + seed) % 23) {
                3 -> Double.NaN
                7 -> Double.POSITIVE_INFINITY
                11 -> Double.NEGATIVE_INFINITY
                13 -> 1e300
                17 -> 4.9e-324
                19 -> -0.0
                else -> ((s ushr 8) and 0xFFFF) / 32768.0 - 1.0
            }
        }
    }

    /** (weightFrom, weightTo, length, remaining at the window's first sample) */
    val fades = listOf(
        doubleArrayOf(1.0, 0.0, 882.0, 882.0),
        doubleArrayOf(0.0, 1.0, 2205.0, 2205.0),
        doubleArrayOf(0.37, 1.0, 2400.0, 1000.0),
        doubleArrayOf(0.8125, 0.0, 960.0, 300.0),
        doubleArrayOf(0.6, 0.0, 882.0, 64.0),
        doubleArrayOf(0.25, 1.0, 49.0, 49.0),
    )

    "the blend and the weight follow the law bit for bit, on a hostile source" {
        for ((f, fade) in fades.withIndex()) {
            val from = fade[0]
            val to = fade[1]
            val inv = 1.0 / fade[2].toInt()
            val remaining = fade[3].toInt()
            val count = minOf(200, remaining)
            val base = source(count = count, seed = 3 + f)
            val other = source(count = count, seed = 101 + f)
            val target = DoubleArray(count)

            crossfadeLinear(
                target = target,
                base = base,
                other = other,
                count = count,
                weightFrom = from,
                weightTo = to,
                remaining = remaining,
                invLength = inv,
            )

            for (i in 0 until count) {
                val w = lawWeight(from = from, to = to, remaining = remaining - i, inv = inv)

                withClue("fade $f, sample $i") {
                    bits(linearFadeWeight(weightFrom = from, weightTo = to, remaining = remaining - i, invLength = inv)) shouldBe bits(w)
                    bits(target[i]) shouldBe bits(lawSample(base = base[i], other = other[i], w = w))
                }
            }
        }
    }

    "each aliasing form renders the same bits as separate arrays" {
        val count = 128
        val remaining = 700
        val inv = 1.0 / 882
        val base = source(count = count, seed = 5)
        val other = source(count = count, seed = 77)
        val separate = DoubleArray(count)

        crossfadeLinear(
            target = separate,
            base = base,
            other = other,
            count = count,
            weightFrom = 0.9,
            weightTo = 0.0,
            remaining = remaining,
            invLength = inv,
        )

        // The bank swap's form: the target is the base.
        val swapForm = base.copyOf()

        crossfadeLinear(
            target = swapForm,
            base = swapForm,
            other = other,
            count = count,
            weightFrom = 0.9,
            weightTo = 0.0,
            remaining = remaining,
            invLength = inv,
        )

        // The compressor's form: the target is the other.
        val compressorForm = other.copyOf()

        crossfadeLinear(
            target = compressorForm,
            base = base,
            other = compressorForm,
            count = count,
            weightFrom = 0.9,
            weightTo = 0.0,
            remaining = remaining,
            invLength = inv,
        )

        for (i in 0 until count) {
            withClue("sample $i") {
                bits(swapForm[i]) shouldBe bits(separate[i])
                bits(compressorForm[i]) shouldBe bits(separate[i])
            }
        }
    }

    "the first weight is weightFrom exactly at the engine's fade lengths, and the landing is weightTo exactly" {
        // 20 ms and 50 ms at 44.1 and 48 kHz.
        for (length in listOf(882, 960, 2205, 2400)) {
            val inv = 1.0 / length

            for (from in listOf(0.0, 0.37, 0.8125, 1.0)) {
                for (to in listOf(0.0, 1.0)) {
                    withClue("length $length, from $from, to $to") {
                        bits(linearFadeWeight(weightFrom = from, weightTo = to, remaining = length, invLength = inv)) shouldBe bits(from)
                        bits(linearFadeWeight(weightFrom = from, weightTo = to, remaining = 0, invLength = inv)) shouldBe bits(to)
                    }
                }
            }
        }

        // Not for every length: 49 * (1.0 / 49) is one ulp below 1, and the weight follows the law, not the end.
        linearFadeWeight(weightFrom = 1.0, weightTo = 0.0, remaining = 49, invLength = 1.0 / 49) shouldBe 0.9999999999999999
    }

    "the window is count samples from index 0, the weight steps down by one remaining per sample" {
        val target = DoubleArray(8) { -7.0 }
        val base = DoubleArray(8) { 0.0 }
        val other = DoubleArray(8) { 1.0 }

        // remaining 4 and invLength 0.25: weights 1, 0.75, 0.5, 0.25 on the four samples; the sentinels stay.
        crossfadeLinear(
            target = target,
            base = base,
            other = other,
            count = 4,
            weightFrom = 1.0,
            weightTo = 0.0,
            remaining = 4,
            invLength = 0.25,
        )

        target.toList() shouldBe listOf(1.0, 0.75, 0.5, 0.25, -7.0, -7.0, -7.0, -7.0)
    }

    "weightTo 0 is weightFrom * (remaining * invLength) bit for bit over a sweep of weightFrom, but for -0.0" {
        val inv = 1.0 / 882
        val sweep = buildList {
            add(0.0)
            add(4.9e-324)
            add(1e-300)
            add(1.0)

            for (k in 0..1000) {
                add(k / 1000.0)
                add(1.0 - (k * 0.000731))
            }
        }

        for (w0 in sweep) {
            for (r in listOf(882, 881, 500, 37, 1)) {
                withClue("w0 $w0, remaining $r") {
                    linearFadeWeight(weightFrom = w0, weightTo = 0.0, remaining = r, invLength = inv).toRawBits() shouldBe
                        (w0 * (r * inv)).toRawBits()
                }
            }
        }

        // The one double where the forms part: 0.0 + -0.0 is +0.0. The swap never fades from -0.0.
        linearFadeWeight(weightFrom = -0.0, weightTo = 0.0, remaining = 10, invLength = inv).toRawBits() shouldBe 0.0.toRawBits()
        (-0.0 * (10 * inv)).toRawBits() shouldBe (-0.0).toRawBits()
    }

    "NaN, infinities and -0.0 pass through the arithmetic unguarded" {
        val base = doubleArrayOf(Double.NaN, 1.0, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, -0.0, -0.0, 2.0, -1e308)
        val other = doubleArrayOf(1.0, Double.NaN, 1.0, Double.NEGATIVE_INFINITY, -0.0, 0.0, Double.POSITIVE_INFINITY, 1e308)
        val target = DoubleArray(base.size)

        // remaining 8 at an eighth: the weights are 1, 0.875, 0.75, ..., 0.125, each exact.
        crossfadeLinear(
            target = target,
            base = base,
            other = other,
            count = base.size,
            weightFrom = 1.0,
            weightTo = 0.0,
            remaining = 8,
            invLength = 0.125,
        )

        target[0].isNaN() shouldBe true // a NaN base
        target[1].isNaN() shouldBe true // a NaN other
        target[2].isNaN() shouldBe true // Inf + 0.75 * (1 - Inf): a fade out of an infinite base is NaN, not Inf
        target[3].isNaN() shouldBe true // Inf + 0.625 * (-Inf - Inf)
        target[4].toRawBits() shouldBe 0.0.toRawBits() // -0.0 + 0.5 * 0.0: the law does not keep -0.0
        target[5].toRawBits() shouldBe 0.0.toRawBits() // -0.0 + 0.375 * 0.0
        target[6] shouldBe Double.POSITIVE_INFINITY // 2 + 0.25 * Inf
        target[7] shouldBe Double.POSITIVE_INFINITY // the difference overflows: -1e308 + 0.125 * Inf

        // At the landing (weight exactly 0) an infinite other still poisons the sample: 0.0 * Inf is NaN.
        val landed = DoubleArray(1)

        crossfadeLinear(
            target = landed,
            base = doubleArrayOf(2.0),
            other = doubleArrayOf(Double.POSITIVE_INFINITY),
            count = 1,
            weightFrom = 1.0,
            weightTo = 0.0,
            remaining = 0,
            invLength = 0.125,
        )

        landed[0].isNaN() shouldBe true

        // A NaN or infinite end: the weight is non-finite.
        linearFadeWeight(weightFrom = Double.NaN, weightTo = 0.0, remaining = 3, invLength = 0.125).isNaN() shouldBe true
        linearFadeWeight(weightFrom = Double.POSITIVE_INFINITY, weightTo = 0.0, remaining = 0, invLength = 0.125).isNaN() shouldBe true
        linearFadeWeight(weightFrom = 1.0, weightTo = 0.0, remaining = 3, invLength = Double.POSITIVE_INFINITY) shouldBe
            Double.POSITIVE_INFINITY
    }
})

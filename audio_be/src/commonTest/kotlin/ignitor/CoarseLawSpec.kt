/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.ignitor

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.ints.shouldBeInRange
import io.kotest.matchers.shouldBe

/**
 * **The coarse law (the sample-and-hold inside `CoarseIgnitor`), against an oracle written here** (test consolidation
 * gap, 2026-09-28). Coarse has no core of its own: the law lives in the node's loop, and until this spec only the
 * baseline's whole-voice rows pinned it in exact bits (`ModulationClockSpec` pins the grid's anchoring and the
 * bypass arms at integer factors).
 *
 * The documented law: a hold clock that starts at 1 ("take a sample NOW", ledger W1), takes the input and subtracts 1
 * whenever it has reached 1, and advances by `1 / max(factor, 1)` per sample. A FRACTIONAL factor therefore holds
 * alternately `ceil(factor)` and `floor(factor)` samples (7.5 holds 8, 7, 8, 7, ...); a factor in (0, 1] takes
 * every sample.
 */
class CoarseLawSpec : StringSpec({

    val sampleRate = 48000

    /** A ramp with no repeated value, so every output sample names the input sample it holds. */
    fun ramp(n: Int): DoubleArray = DoubleArray(n) { i -> 0.001 * (i + 1) }

    fun oracle(input: DoubleArray, factor: Double): DoubleArray {
        val increment = 1.0 / maxOf(factor, 1.0)
        val out = DoubleArray(input.size)
        var clock = 1.0
        var held = 0.0

        for (i in input.indices) {
            if (clock >= 1.0) {
                held = input[i]
                clock -= 1.0
            }

            out[i] = held
            clock += increment
        }

        return out
    }

    "the hold law, bit for bit, at integer and fractional factors, over blocks and ragged windows" {
        val total = 1024
        val input = ramp(total)
        val framings = listOf(List(8) { 128 }, listOf(37, 91, 128, 5, 251, 128, 384))

        for (factor in listOf(0.5, 1.0, 2.0, 3.0, 7.5, 2.7)) {
            val expected = oracle(input, factor)

            for (windows in framings) {
                val out = renderNodeWindows(ArrayIgnitor(input).coarse(ParamIgnitor("factor", factor)), windows, sampleRate)

                withClue("factor $factor, windows $windows: first mismatching frame") {
                    input.indices.firstOrNull { out[it].toRawBits() != expected[it].toRawBits() } shouldBe null
                }
            }
        }
    }

    "a fractional factor holds 7 or 8 samples, 7.5 on average (200 takes in 1500 samples), not a steady 7" {
        // The oracle row above covers this in bits; this row states the audible consequence of `1 / factor` (not
        // `1 / floor(factor)`) in the terms of the law. The clock accumulates `1 / 7.5` in floats, so a hold lands a
        // sample early or late now and then (the KDoc's "give or take one"); the count of takes is the invariant.
        val n = 1500
        val input = ramp(n)
        val out = renderNodeWindows(ArrayIgnitor(input).coarse(ParamIgnitor("factor", 7.5)), List(12) { 125 }, sampleRate)
        val holds = mutableListOf<Int>()
        var run = 1

        for (i in 1 until n) {
            if (out[i] == out[i - 1]) {
                run++
            } else {
                holds.add(run)
                run = 1
            }
        }

        holds.add(run)

        withClue("holds $holds") {
            holds.toSet() shouldBe setOf(7, 8)
            holds.size shouldBeInRange 200..201
        }
    }
})

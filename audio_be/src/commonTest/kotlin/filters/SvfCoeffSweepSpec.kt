/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.filters

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_be.EnvelopeCore
import io.peekandpoke.klang.audio_bridge.AdsrCurve
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.tan

/**
 * The shared interpolation of the engine's modulated SVFs ([SvfCoeffSweep]) and the shared endpoint
 * mapping ([filterEnvCutoff]), each against an oracle written here. Both hosts (the Ignitor filter node
 * and the voice strip's `BaseSvf`) call this code, so a strip-against-node parity row cannot see a
 * mistake inside it; these rows can.
 */
class SvfCoeffSweepSpec : StringSpec({

    val sr = 48000.0

    /** The textbook TPT SVF coefficients (Zavalishin), in the order a1, a2, a3, k, g. */
    fun oracle(fc: Double, q: Double): DoubleArray {
        val g = tan(PI * fc / sr)
        val k = 1.0 / q
        val a1 = 1.0 / (1.0 + g * (g + k))
        val a2 = g * a1
        val a3 = g * a2

        return doubleArrayOf(a1, a2, a3, k, g)
    }

    fun startOf(s: SvfCoeffSweep) = doubleArrayOf(s.start.a1, s.start.a2, s.start.a3, s.start.k, s.start.g)
    fun stepsOf(s: SvfCoeffSweep) = doubleArrayOf(s.a1Step, s.a2Step, s.a3Step, s.kStep, s.gStep)

    fun relClose(actual: Double, expected: Double, tol: Double) =
        abs(actual - expected) <= tol * maxOf(abs(expected), 1e-300)

    "the start coefficients are the start cutoff's, and each step is (end - start) / frames" {
        val sweep = SvfCoeffSweep()

        sweep.prepare(800.0, 3200.0, 2.0, sr, 128)

        val s = oracle(800.0, 2.0)
        val e = oracle(3200.0, 2.0)
        val start = startOf(sweep)
        val steps = stepsOf(sweep)

        for (i in 0 until 5) {
            relClose(start[i], s[i], 1e-14) shouldBe true
        }

        // a1, a2, a3 and g move; k does not (one q at both ends).
        for (i in listOf(0, 1, 2, 4)) {
            relClose(steps[i], (e[i] - s[i]) / 128.0, 1e-9) shouldBe true
        }

        sweep.kStep shouldBe 0.0
    }

    "the steps carry the start coefficients to the end cutoff's in exactly `frames` samples" {
        val sweep = SvfCoeffSweep()

        sweep.prepare(12000.0, 150.0, 0.707, sr, 91)

        val acc = startOf(sweep)
        val steps = stepsOf(sweep)

        repeat(91) {
            for (i in 0 until 5) {
                acc[i] += steps[i]
            }
        }

        val e = oracle(150.0, 0.707)

        for (i in 0 until 5) {
            acc[i] shouldBe (e[i] plusOrMinus 1e-12)
        }
    }

    "equal ends step nothing, even right after a sweep that moved" {
        val sweep = SvfCoeffSweep()

        sweep.prepare(500.0, 4000.0, 1.0, sr, 128)
        sweep.prepare(900.0, 900.0, 1.0, sr, 128)

        val s = oracle(900.0, 1.0)

        stepsOf(sweep).toList() shouldBe listOf(0.0, 0.0, 0.0, 0.0, 0.0)
        relClose(sweep.start.a1, s[0], 1e-14) shouldBe true
    }

    "a sweep of no frames is a snap to the start cutoff" {
        val sweep = SvfCoeffSweep()

        sweep.prepare(500.0, 4000.0, 1.0, sr, 0)

        stepsOf(sweep).toList() shouldBe listOf(0.0, 0.0, 0.0, 0.0, 0.0)
        relClose(sweep.start.g, oracle(500.0, 1.0)[4], 1e-14) shouldBe true
    }

    "filterEnvCutoff: base * 2^(depth/12 * level), the level clamped to [0, 1]" {
        // A linear attack of 100 frames into a RAW sustain of 1.5 (the core does not clamp it).
        val core = EnvelopeCore()

        core.prepare(
            attackFrames = 100.0, decayFrames = 100.0, sustainLevel = 1.5, releaseFrames = 0.0, gateEndPos = 100_000,
            attackCurve = AdsrCurve.Linear, decayCurve = AdsrCurve.Linear, releaseCurve = AdsrCurve.Linear,
        )

        // Frame 25 of the attack: level 0.25.
        core.filterEnvCutoff(25, 600.0, 24.0) shouldBe (600.0 * 2.0.pow(24.0 / 12.0 * 0.25) plusOrMinus 1e-9)
        // The sustain: level 1.5, clamped to 1.
        core.filterEnvCutoff(5000, 600.0, 24.0) shouldBe (600.0 * 4.0 plusOrMinus 1e-9)
        // A negative depth sweeps down.
        core.filterEnvCutoff(5000, 600.0, -12.0) shouldBe (300.0 plusOrMinus 1e-9)
    }
})

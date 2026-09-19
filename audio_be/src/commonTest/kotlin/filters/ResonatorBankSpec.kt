/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.filters

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.peekandpoke.klang.audio_be.AudioBuffer
import io.peekandpoke.klang.audio_bridge.FilterDef
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sin

/**
 * The resonator bank behind `body(...)` and `vowel(...)`, and the two mappings that turn a table
 * row into a band.
 *
 * The bank rows use an INDEPENDENT oracle: bare [LowPassHighPassFilters.SvfBPF] instances run on
 * the same input, block for block, scaled and summed here. The mapping rows pin each gain rule
 * against its literal expression, operand order included, because the shipped tables were tuned
 * against exactly those doubles.
 */
class ResonatorBankSpec : StringSpec({

    val sampleRate = 48000.0
    val frames = 128
    val blocks = 24

    // A sweep plus a deterministic rattle, so every band rings and the state carries across blocks.
    fun input(): AudioBuffer = AudioBuffer(frames * blocks) { i ->
        val t = i / sampleRate
        0.5 * sin(2.0 * PI * (80.0 + 20000.0 * t) * t) + 0.3 * sin(i * 0.731) * sin(i * 0.0137)
    }

    // Runs [filter] over [signal] in 128-frame blocks, as the orbit does, and returns the output.
    fun run(filter: AudioFilter, signal: AudioBuffer): AudioBuffer {
        val buf = signal.copyOf()

        for (b in 0 until blocks) {
            filter.process(buf, b * frames, frames)
        }

        return buf
    }

    "a one-band bank is the bare SvfBPF times the band's gain, bit for bit" {
        val signal = input()
        val bank = ResonatorBank(listOf(ResonatorBank.Band(freq = 700.0, q = 8.0, gain = 1.7)), sampleRate)
        val bare = run(LowPassHighPassFilters.SvfBPF(700.0, 8.0, sampleRate), signal)

        val actual = run(bank, signal)
        val expected = AudioBuffer(bare.size) { 0.0 + bare[it] * 1.7 }

        actual shouldBe expected
        // Not two silences agreeing.
        expected.maxOf { abs(it) } shouldBeGreaterThan 0.05
    }

    "a three-band bank is the sum of three bare SvfBPFs, each times its gain, in band order" {
        val signal = input()
        val bank = ResonatorBank(
            listOf(
                ResonatorBank.Band(freq = 300.0, q = 12.0, gain = 900.0),
                ResonatorBank.Band(freq = 1900.0, q = 60.0, gain = 0.35),
                ResonatorBank.Band(freq = 5200.0, q = 4.0, gain = 0.0007),
            ),
            sampleRate,
        )
        val a = run(LowPassHighPassFilters.SvfBPF(300.0, 12.0, sampleRate), signal)
        val b = run(LowPassHighPassFilters.SvfBPF(1900.0, 60.0, sampleRate), signal)
        val c = run(LowPassHighPassFilters.SvfBPF(5200.0, 4.0, sampleRate), signal)

        val actual = run(bank, signal)
        val expected = AudioBuffer(signal.size) { ((0.0 + a[it] * 900.0) + b[it] * 0.35) + c[it] * 0.0007 }
        val reversed = AudioBuffer(signal.size) { ((0.0 + c[it] * 0.0007) + b[it] * 0.35) + a[it] * 900.0 }

        actual shouldBe expected
        expected.maxOf { abs(it) } shouldBeGreaterThan 0.05
        // The order is observable here: with gains this far apart the reversed sum rounds
        // differently in some samples, so a bank that summed back to front would fail above.
        expected shouldNotBe reversed
    }

    "the body rule: the gain is the plain dB factor, a non-finite dB is 0 dB, freq and q pass raw" {
        val plain = LowPassHighPassFilters.bodyBand(FilterDef.Body.Mode(freq = 230.0, db = -3.0, q = 10.0))

        plain.gain shouldBe 10.0.pow(-3.0 / 20.0)
        plain.freq shouldBe 230.0
        plain.q shouldBe 10.0

        LowPassHighPassFilters.bodyBand(FilterDef.Body.Mode(230.0, Double.NaN, 10.0)).gain shouldBe 1.0
        LowPassHighPassFilters.bodyBand(FilterDef.Body.Mode(230.0, Double.NEGATIVE_INFINITY, 10.0)).gain shouldBe 1.0

        // The SVF clamps q itself; the body folds nothing, so an out-of-range q reaches it unchanged.
        LowPassHighPassFilters.bodyBand(FilterDef.Body.Mode(230.0, 0.0, 500.0)).q shouldBe 500.0
    }

    "the vowel rule: the dB factor times the clamped q times 0.05, in that order; the SVF gets the raw q" {
        // (-3 dB, q 110) is a pair where the three associations of the product are three different
        // doubles, so a regrouped fold is a different number here, not just a different spelling.
        val band = LowPassHighPassFilters.vowelBand(FilterDef.Formant.Band(freq = 730.0, db = -3.0, q = 110.0))

        band.gain shouldBe 10.0.pow(-3.0 / 20.0) * 110.0 * 0.05
        band.gain shouldNotBe 10.0.pow(-3.0 / 20.0) * (110.0 * 0.05)
        band.freq shouldBe 730.0
        band.q shouldBe 110.0

        // The fold uses the SVF's own clamp, [0.1, 200], and its own fallback for a non-finite q,
        // while the band hands the SVF the raw value.
        val high = LowPassHighPassFilters.vowelBand(FilterDef.Formant.Band(730.0, 0.0, 500.0))
        high.gain shouldBe 1.0 * 200.0 * 0.05
        high.q shouldBe 500.0

        LowPassHighPassFilters.vowelBand(FilterDef.Formant.Band(730.0, 0.0, 0.01)).gain shouldBe 1.0 * 0.1 * 0.05
        LowPassHighPassFilters.vowelBand(FilterDef.Formant.Band(730.0, 0.0, Double.NaN)).gain shouldBe
            1.0 * 0.7071067811865475 * 0.05

        // A non-finite dB is 0 dB.
        LowPassHighPassFilters.vowelBand(FilterDef.Formant.Band(730.0, Double.NaN, 80.0)).gain shouldBe 1.0 * 80.0 * 0.05
    }
})

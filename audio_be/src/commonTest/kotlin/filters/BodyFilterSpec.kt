/*
 * Copyright (C) 2025-2026 The Klang Audio Motör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.filters

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_be.AudioBuffer
import io.peekandpoke.klang.audio_bridge.FilterDef
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.sqrt

class BodyFilterSpec : StringSpec({

    val sampleRate = 44100.0
    val blockFrames = 4096

    fun sine(freq: Double, length: Int, amplitude: Double = 1.0): AudioBuffer =
        AudioBuffer(length) { i -> amplitude * sin(2.0 * PI * freq * i / sampleRate) }

    fun rms(buf: AudioBuffer): Double {
        if (buf.isEmpty()) return 0.0
        return sqrt(buf.fold(0.0) { acc, v -> acc + v * v } / buf.size)
    }

    fun mode(freq: Double, db: Double, q: Double) = FilterDef.Body.Mode(freq, db, q)

    fun woodModes() = listOf(
        mode(110.0, 2.0, 12.0),
        mode(230.0, 1.0, 10.0),
        mode(430.0, 0.0, 9.0),
        mode(820.0, -2.0, 7.0),
        mode(1500.0, -4.0, 5.0),
    )

    // High-Q → long ring, for the tail-stability test.
    fun glassModes() = listOf(
        mode(1050.0, 0.0, 50.0),
        mode(2100.0, -3.0, 60.0),
        mode(3300.0, -6.0, 45.0),
    )

    // BodyFilter is WET-ONLY (same API as lpf/formant). The dry/wet blend lives in
    // ParallelMixFilter — see ParallelMixFilterSpec. Per-band gain is normalized by 1/Q so `db`
    // is the actual peak emphasis, independent of Q.

    "BodyFilter - 1/Q normalization: a db=0 mode peaks at ~unity regardless of Q" {
        val freq = 1000.0
        val input = sine(freq, blockFrames)
        val inRms = rms(input)

        val lowQ = AudioBuffer(blockFrames) { input[it] }
        val highQ = AudioBuffer(blockFrames) { input[it] }
        BodyFilter(listOf(mode(freq, 0.0, 5.0)), sampleRate).process(lowQ, 0, lowQ.size)
        BodyFilter(listOf(mode(freq, 0.0, 50.0)), sampleRate).process(highQ, 0, highQ.size)

        // Without 1/Q normalization a Q=50 mode would peak ~50× the input. Normalized, BOTH the
        // Q=5 and Q=50 modes land near unity (db=0) — that's the whole point of the /Q fix.
        rms(lowQ) shouldBeGreaterThan (inRms * 0.4)
        rms(lowQ) shouldBeLessThan (inRms * 2.0)
        rms(highQ) shouldBeGreaterThan (inRms * 0.4)
        rms(highQ) shouldBeLessThan (inRms * 2.0)
    }

    "BodyFilter - wet-only: rejects a tone far from every mode" {
        val offBand = sine(12000.0, blockFrames) // far above every wood mode
        val inOff = rms(offBand)

        BodyFilter(woodModes(), sampleRate).process(offBand, 0, offBand.size)

        // Wet-only: nothing near 12 kHz → near silence. The dry is re-added by the mix wrapper.
        rms(offBand) shouldBeLessThan (inOff * 0.2)
    }

    "BodyFilter - stays finite and the ring decays over a long tail" {
        val filter = BodyFilter(glassModes(), sampleRate)

        val first = AudioBuffer(blockFrames) { if (it == 0) 1.0 else 0.0 }
        filter.process(first, 0, first.size)
        rms(first).isFinite() shouldBe true

        var lastRms = 0.0
        repeat(200) {
            val silent = AudioBuffer(blockFrames) { 0.0 }
            filter.process(silent, 0, silent.size)
            lastRms = rms(silent)
            lastRms.isFinite() shouldBe true
            lastRms shouldBeLessThan 10.0  // bounded — never blows up
        }
        lastRms shouldBeLessThan 1e-3
    }

    "BodyFilter - non-finite mode params still produce finite output" {
        val bands = listOf(
            mode(freq = Double.NaN, db = 0.0, q = 10.0),
            mode(freq = 500.0, db = Double.NaN, q = Double.POSITIVE_INFINITY),
        )
        val buf = sine(500.0, blockFrames)

        BodyFilter(bands, sampleRate).process(buf, 0, buf.size)

        buf.all { it.isFinite() } shouldBe true
    }
})

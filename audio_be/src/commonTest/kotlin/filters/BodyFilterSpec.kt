/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.filters

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_be.AudioBuffer
import io.peekandpoke.klang.audio_bridge.BodyMaterials
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

    fun mode(freq: Double, db: Double, q: Double) = BodyMaterials.Mode(freq = freq, db = db, q = q)

    // The body bank WET-ONLY: full mix, no floor, so the dry coefficient is cos(pi/2)^2, about 4e-33, and the output
    // is the resonance (the blend lives in the bank since engine tidy-up step 12 (a)).
    fun bodyBank(modes: List<BodyMaterials.Mode>) = ResonatorBank(capacity = modes.size, sampleRate = sampleRate, blockFrames = blockFrames)
        .also { it.install(ResonatorConfig(table = ResonatorTable.ofBody(modes), mix = 1.0, floor = 0.0)) }

    fun woodModes() = listOf(
        mode(freq = 110.0, db = 2.0, q = 12.0),
        mode(freq = 230.0, db = 1.0, q = 10.0),
        mode(freq = 430.0, db = 0.0, q = 9.0),
        mode(freq = 820.0, db = -2.0, q = 7.0),
        mode(freq = 1500.0, db = -4.0, q = 5.0),
    )

    // High-Q → long ring, for the tail-stability test.
    fun glassModes() = listOf(
        mode(freq = 1050.0, db = 0.0, q = 50.0),
        mode(freq = 2100.0, db = -3.0, q = 60.0),
        mode(freq = 3300.0, db = -6.0, q = 45.0),
    )

    // The rows below run the bank wet-only (see [bodyBank]); its blend is `ResonatorBankSpec`'s law row. The SVF
    // bandpass is unity-peak (its own `k * v1` tap, since C2), so the body gain is the plain dB factor and `db` is the
    // actual peak emphasis, independent of Q: `ResonatorBankSpec` pins the bank against the law written out and a
    // band's unity peak at any q.

    "body bank - wet-only: rejects a tone far from every mode" {
        val offBand = sine(12000.0, blockFrames) // far above every wood mode
        val inOff = rms(offBand)

        bodyBank(woodModes()).process(buffer = offBand, offset = 0, length = offBand.size)

        // Wet-only: nothing near 12 kHz, near silence. A real stage's mix re-adds the dry.
        rms(offBand) shouldBeLessThan (inOff * 0.2)
    }

    "body bank - stays finite and the ring decays over a long tail" {
        val filter = bodyBank(glassModes())

        val first = AudioBuffer(blockFrames) { if (it == 0) 1.0 else 0.0 }
        filter.process(buffer = first, offset = 0, length = first.size)
        rms(first).isFinite() shouldBe true

        var lastRms = 0.0
        repeat(200) {
            val silent = AudioBuffer(blockFrames) { 0.0 }
            filter.process(buffer = silent, offset = 0, length = silent.size)
            lastRms = rms(silent)
            lastRms.isFinite() shouldBe true
            lastRms shouldBeLessThan 10.0  // bounded — never blows up
        }
        lastRms shouldBeLessThan 1e-3
    }

    "body bank - non-finite mode params still produce finite output" {
        val bands = listOf(
            mode(freq = Double.NaN, db = 0.0, q = 10.0),
            mode(freq = 500.0, db = Double.NaN, q = Double.POSITIVE_INFINITY),
        )
        val buf = sine(500.0, blockFrames)

        bodyBank(bands).process(buffer = buf, offset = 0, length = buf.size)

        buf.all { it.isFinite() } shouldBe true
    }
})

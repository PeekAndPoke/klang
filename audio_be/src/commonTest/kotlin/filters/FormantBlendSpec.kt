/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.filters

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.peekandpoke.klang.audio_be.AudioBuffer
import io.peekandpoke.klang.audio_bridge.FilterDef
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Guards `createFormant` (the vowel filter): the formants must clearly DOMINATE the dry floor,
 * otherwise the vowel is inaudible (regression: when VOWEL_TAME was too small / VOWEL_FLOOR too
 * high, F1 was only +7 dB over the valley and F2 was below the floor). Also pins that a `body()`
 * after it does not erase the vowel.
 */
class FormantBlendSpec : StringSpec({

    val sr = 44100.0
    val n = 8192

    fun sine(freq: Double) = AudioBuffer(n) { i -> sin(2.0 * PI * freq * i / sr) }
    fun rms(b: AudioBuffer): Double = sqrt(b.fold(0.0) { a, v -> a + v * v } / b.size)

    // Soprano "u": F1 325, F2 700, then steep rolloff.
    val uBands = listOf(
        FilterDef.Formant.Band(freq = 325.0, db = 0.0, q = 80.0),
        FilterDef.Formant.Band(freq = 700.0, db = -16.0, q = 90.0),
        FilterDef.Formant.Band(freq = 2700.0, db = -35.0, q = 120.0),
        FilterDef.Formant.Band(freq = 3800.0, db = -40.0, q = 130.0),
        FilterDef.Formant.Band(freq = 4950.0, db = -60.0, q = 140.0),
    )
    val woodModes = listOf(
        FilterDef.Body.Mode(freq = 100.0, db = 3.0, q = 12.0), FilterDef.Body.Mode(freq = 200.0, db = 2.0, q = 11.0),
        FilterDef.Body.Mode(freq = 300.0, db = 1.0, q = 10.0), FilterDef.Body.Mode(freq = 430.0, db = 0.0, q = 9.0),
        FilterDef.Body.Mode(freq = 650.0, db = -1.0, q = 8.0), FilterDef.Body.Mode(freq = 900.0, db = -2.0, q = 7.0),
        FilterDef.Body.Mode(freq = 1300.0, db = -4.0, q = 6.0), FilterDef.Body.Mode(freq = 1900.0, db = -6.0, q = 5.0),
    )

    fun gainAt(freq: Double, filters: List<AudioFilter>): Double {
        val buf = sine(freq)
        val inR = rms(buf)
        filters.forEach { it.process(buffer = buf, offset = 0, length = buf.size) }
        return rms(buf) / inR
    }

    "createFormant - vowel formants clearly dominate the floor" {
        val f1 = gainAt(325.0, listOf(LowPassHighPassFilters.createFormant(bands = uBands, mix = 1.0, sampleRate = sr)))
        val f2 = gainAt(700.0, listOf(LowPassHighPassFilters.createFormant(bands = uBands, mix = 1.0, sampleRate = sr)))
        val valley = gainAt(1200.0, listOf(LowPassHighPassFilters.createFormant(bands = uBands, mix = 1.0, sampleRate = sr)))

        f1 shouldBeGreaterThan (valley * 8.0)    // strong F1 (measured ~14×)
        f2 shouldBeGreaterThan (valley * 2.5)    // present F2 (measured ~3.9×)
    }

    "createFormant - a following body() does not erase the vowel" {
        val chain = {
            listOf(
                LowPassHighPassFilters.createFormant(bands = uBands, mix = 1.0, sampleRate = sr),
                LowPassHighPassFilters.createBody(bands = woodModes, mix = 0.5, sampleRate = sr),
            )
        }
        val f1 = gainAt(325.0, chain())
        val valley = gainAt(1200.0, chain())

        f1 shouldBeGreaterThan (valley * 8.0)    // vowel survives the body stage
    }
})

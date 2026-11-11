/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.ignitor

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.shouldBeLessThan
import io.peekandpoke.klang.audio_be.probes.FRAMING_NONE
import io.peekandpoke.klang.audio_be.probes.FramingNote
import io.peekandpoke.klang.audio_be.probes.PROBE_SAMPLE_RATE
import io.peekandpoke.klang.audio_be.probes.blackmanHarris7
import io.peekandpoke.klang.audio_be.probes.renderFramedVoice
import io.peekandpoke.klang.audio_be.probes.windowedPowerSpectrum
import io.peekandpoke.klang.audio_bridge.VoiceData
import io.peekandpoke.klang.common.infra.toKlangMap
import java.util.Locale
import kotlin.math.abs
import kotlin.math.log10

/**
 * **The string's read damps the highs alike from note to note** (KLANG-00430, audit finding L3-07, moved from
 * KLANG-00426). The fractional delay sits inside the loop, so its gain is paid on every trip: a linear read passes 1 at a
 * fraction of 0 and `cos(w / 2)` at 0.5, so at brightness 1 (the one-pole a wire) a note whose read falls halfway between
 * two frames lost its top within tens of milliseconds while a note 2 Hz away kept it for seconds (the replica in
 * KLANG-00426: -65.8 against -3.6 dB above 5 kHz). The cubic Lagrange read ([lagrange4]) passes 0.88 at 12 kHz at its
 * worst fraction: the dependence shrinks.
 *
 * Measured on the built-in `pluck` through `VoiceFactory`, analog 0, brightness 1: the energy above 5 kHz re the whole,
 * in the window from 0.2 s (8192 frames, 7-term Blackman-Harris), at 438.4 Hz (read fraction about 0.49) and 440.4 Hz
 * (about 0.99).
 */
class PluckReadSpec : StringSpec({

    val sr = PROBE_SAMPLE_RATE

    fun highShareDb(note: Double): Double {
        val x = renderFramedVoice(
            note = FramingNote(onsetFrame = 0, gateFrames = sr, cutFrame = FRAMING_NONE, renderFrames = sr),
            data = VoiceData.empty.copy(sound = "pluck", freqHz = note, ignitorParams = mapOf("analog" to 0.0, "brightness" to 1.0).toKlangMap()),
        ).left
        val n = 8192
        val power = windowedPowerSpectrum(samples = x, from = sr / 5, window = blackmanHarris7(n))
        val binHz = sr.toDouble() / n
        var high = 0.0
        var all = 0.0

        for (k in 1 until power.size) {
            all += power[k]

            if (k * binHz > 5000.0) {
                high += power[k]
            }
        }

        return 10.0 * log10(high / all)
    }

    "two plucks 2 Hz apart at brightness 1 keep their highs alike (L3-07)" {
        val a = highShareDb(438.4)
        val b = highShareDb(440.4)

        println(String.format(Locale.ROOT, "PLUCK-READ above 5 kHz re the whole, 0.2 s on: 438.4 Hz %.1f dB, 440.4 Hz %.1f dB", a, b))

        abs(a - b) shouldBeLessThan 20.0
    }
})

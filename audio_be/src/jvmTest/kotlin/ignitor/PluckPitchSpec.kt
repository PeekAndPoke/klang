/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.ignitor

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_be.ignitor.PitchMeter.cents
import io.peekandpoke.klang.audio_be.probes.FRAMING_NONE
import io.peekandpoke.klang.audio_be.probes.FramingNote
import io.peekandpoke.klang.audio_be.probes.PROBE_SAMPLE_RATE
import io.peekandpoke.klang.audio_be.probes.renderFramedVoice
import io.peekandpoke.klang.audio_bridge.VoiceData
import io.peekandpoke.klang.common.infra.toKlangMap
import io.peekandpoke.klang.common.math.semitones
import java.util.Locale
import kotlin.math.abs

/**
 * **The pluck plays in tune** (KLANG-00426, audit finding L3-01). The loop's period is the line's read delay plus the
 * phase delay of its two filters; the nodes take that delay out of the read (`KarplusString.loopDelayOf`). Before, the
 * line alone was one period: the default pluck was 15.75 cents flat at A4 and 36.73 at C6, brightness moved the pitch
 * by more than a semitone over its range, and any stiffness above 0 took almost one sample more.
 *
 * Measured as the audit's disproof pass measured it (`docs/audio-audit/2026-11-10/audit/disproof-sound.md`, L3-01):
 * the registered built-in voice through `VoiceFactory` and `Voice.render`, analog 0, 2 s at 48 kHz, its fundamental by
 * two independent methods ([PitchMeter.pitchOf]: the FFT peak and the heterodyne phase slope).
 *
 * A case has a pitch when the two methods read it alike ([METHODS_AGREE]); the darkest string at C7 dies within a few
 * periods and has none (the disproof pass: brightness 0.1 at 2093 Hz, where the methods read thousands of cents apart). The cases without a pitch are listed and
 * pinned ([NO_PITCH]), so a case that loses its pitch shows. Method B, the heterodyne phase slope, reads the pole's own
 * rotation and is held to [TOLERANCE] wherever there is a pitch; method A, the FFT peak, where the tone rings long
 * ([LONG_TONE_PERIODS]).
 */
class PluckPitchSpec : StringSpec({

    val sr = PROBE_SAMPLE_RATE

    /** A1 to C7: the notes of the disproof pass's table, and A1 below them. */
    val notes = listOf(55.0, 110.0, 220.0, 440.0, 880.0, 1046.5, 2093.0)

    fun render(sound: String, note: Double, params: Map<String, Double>): DoubleArray = renderFramedVoice(
        note = FramingNote(onsetFrame = 0, gateFrames = 2 * sr, cutFrame = FRAMING_NONE, renderFrames = 2 * sr),
        data = VoiceData.empty.copy(sound = sound, freqHz = note, ignitorParams = params.toKlangMap()),
    ).left

    fun f(x: Double): String = String.format(Locale.ROOT, "%8.3f", x)

    /**
     * How many periods of [note] the tone [x] rings above -60 dB re its peak. Read on [x] less its running mean over one
     * period: the loop keeps a residue of DC some 90 dB under the burst (the read's fraction leaves a sliver of its
     * mean), which outlives a dark tone by seconds and, behind the voice's 10 ms attack, can match a tone that dies
     * within it.
     */
    fun ringPeriods(x: DoubleArray, note: Double): Double {
        val l = maxOf(1, Math.round(sr / note).toInt())
        val y = DoubleArray(x.size)
        var sum = 0.0

        for (i in x.indices) {
            sum += x[i]

            if (i >= l) {
                sum -= x[i - l]
            }

            y[i] = x[i] - sum / minOf(i + 1, l)
        }

        return PitchMeter.aliveEnd(x = y, from = 0, floorRel = 1e-3) * note / sr
    }

    /** Measures one case and returns its label when it has no pitch; asserts the pitch otherwise. */
    fun checkCase(sound: String, note: Double, brightness: Double, stiffness: Double, extra: Map<String, Double> = emptyMap()): String? {
        val params = mapOf("analog" to 0.0, "brightness" to brightness, "stiffness" to stiffness) + extra
        val x = render(sound = sound, note = note, params = params)
        val (a, b) = PitchMeter.pitchOf(x = x, nominalHz = note)
        val ringPeriods = ringPeriods(x = x, note = note)
        val ca = cents(hz = a, nominalHz = note)
        val cb = cents(hz = b, nominalHz = note)
        val label = "$sound $note Hz, brightness $brightness, stiffness $stiffness"
        val hasPitch = abs(ca - cb) < METHODS_AGREE
        val longTone = ringPeriods >= LONG_TONE_PERIODS

        println(
            "PLUCK-PITCH $label: A ${f(ca)} c  B ${f(cb)} c  rings ${f(ringPeriods)} periods above -60 dB" +
                if (hasPitch) "" else "  (no pitch)",
        )

        if (!hasPitch) {
            return label
        }

        withClue("$label: method B (the heterodyne phase slope), cents off the note") {
            abs(cb) shouldBeLessThan TOLERANCE
        }

        if (longTone) {
            withClue("$label: method A (the FFT peak), cents off the note") {
                abs(ca) shouldBeLessThan TOLERANCE
            }
        }

        return null
    }

    "the pluck is in tune from A1 to C7 at every brightness and stiffness where it has a pitch" {
        val noPitch = mutableListOf<String>()

        for (note in notes) {
            for (brightness in listOf(0.1, 0.2, 0.5, 1.0)) {
                for (stiffness in listOf(0.0, 0.01, 0.5, 0.99)) {
                    checkCase(sound = "pluck", note = note, brightness = brightness, stiffness = stiffness)?.let { noPitch += it }
                }
            }
        }

        noPitch shouldBe NO_PITCH.filter { it.startsWith("pluck ") }
    }

    "the superpluck's string is in tune the same way (one string, no detune)" {
        val noPitch = mutableListOf<String>()

        for (note in notes) {
            for (brightness in listOf(0.1, 0.5, 1.0)) {
                for (stiffness in listOf(0.0, 0.01, 0.5)) {
                    checkCase(sound = "superpluck", note = note, brightness = brightness, stiffness = stiffness, extra = mapOf("voices" to 1.0))
                        ?.let { noPitch += it }
                }
            }
        }

        noPitch shouldBe NO_PITCH.filter { it.startsWith("superpluck ") }
    }

    "each unison string of a superpluck is in tune with its own detuned note" {
        // Three strings three semitones apart (spread 6): far enough apart for the FFT to read each peak on its own. The
        // heterodyne method reads one tone and is not used here. The settings ring long enough for the FFT: a string
        // that dies in a few dozen periods has a peak too wide to read beside its neighbours. That each string takes
        // the loop delay of its OWN period is pinned exactly by `SuperPluckBaseDelaySpec`.
        val voices = 3
        val spread = 6.0

        for (note in listOf(220.0, 440.0, 1046.5)) {
            for ((brightness, stiffness) in listOf(0.5 to 0.0, 0.5 to 0.5, 1.0 to 0.5)) {
                val params = mapOf("analog" to 0.0, "brightness" to brightness, "stiffness" to stiffness, "voices" to voices.toDouble(), "spread" to spread)
                val x = render(sound = "superpluck", note = note, params = params)
                val start = (4.0 * sr / note).toInt()
                val end = PitchMeter.aliveEnd(x = x, from = start, floorRel = 1e-7)

                for (n in 0 until voices) {
                    val target = note * Ignitors.getUnisonDetune(unison = voices, detune = spread, voiceIndex = n).semitones()
                    val hz = PitchMeter.fftPeakHz(x = x, from = start, length = end - start, loHz = target * 0.98, hiHz = target * 1.02)
                    val c = cents(hz = hz, nominalHz = target)
                    val label = "superpluck $note Hz string $n (target ${f(target)} Hz), brightness $brightness, stiffness $stiffness"

                    println("PLUCK-PITCH $label: A ${f(c)} c")

                    withClue("$label: cents off the string's own note") {
                        abs(c) shouldBeLessThan TOLERANCE
                    }
                }
            }
        }
    }
}) {
    private companion object {
        /** The tuning asked of the pluck, in cents (KLANG-00426). */
        const val TOLERANCE: Double = 1.0

        /**
         * Method A (the FFT peak) is held to [TOLERANCE] on a tone that rings this many periods above -60 dB. A tone that
         * dies in a few dozen periods (brightness 0.1 from 880 Hz up: 25 periods and fewer) biases the peak by a cent or two (the disproof pass saw the two methods up to 3.4
         * cents apart there before the fix); method B reads the pole's own rotation and is held everywhere.
         */
        const val LONG_TONE_PERIODS: Double = 50.0

        /**
         * A tone has a pitch when the two methods read it within this many cents of each other. Where the tone dies within
         * a few periods, the heterodyne method reads thousands of cents off; elsewhere they agree to about one cent.
         */
        const val METHODS_AGREE: Double = 20.0

        /** The cases without a pitch: the darkest string at C7 dies within a few periods. */
        val NO_PITCH: List<String> = listOf(
            "pluck 2093.0 Hz, brightness 0.1, stiffness 0.0",
            "pluck 2093.0 Hz, brightness 0.1, stiffness 0.01",
            "pluck 2093.0 Hz, brightness 0.1, stiffness 0.5",
            "pluck 2093.0 Hz, brightness 0.1, stiffness 0.99",
            "superpluck 2093.0 Hz, brightness 0.1, stiffness 0.0",
            "superpluck 2093.0 Hz, brightness 0.1, stiffness 0.01",
            "superpluck 2093.0 Hz, brightness 0.1, stiffness 0.5",
        )
    }
}

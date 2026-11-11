/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.probes

import io.kotest.core.spec.style.StringSpec
import java.io.File
import java.time.Instant
import java.util.Locale

/**
 * The aliasing meter's RUNNER (KLANG-00420, probe 2): every row of [aliasingCases] read by [measureAliasing], printed and
 * written to a file. A measurement, not a gate: it asserts nothing about the numbers.
 *
 * **Out of the normal run.** Its one test is disabled unless the environment holds `KLANG_PROBE=aliasing`, so a normal
 * `jvmTest` reports it as skipped and measures nothing (the stage probe's pattern, [StageProbeRunner]). Run it:
 *
 *     KLANG_PROBE=aliasing console/with-build-lock.sh ./gradlew :audio_be:jvmTest --rerun \
 *         --tests io.peekandpoke.klang.audio_be.probes.AliasingRunner
 *
 * The table goes to `$KLANG_PROBE_OUT` when set (a file path), else to `aliasing.txt` in `<build dir>/probes/`
 * ([probeOutputFile]); the same rows go beside it as JSON (`aliasing.json`, [formatAliasingJson]).
 *
 * Then the healthy limit ([measureAliasingLimits]): every source and shaper that can be judged, swept up from about
 * 100 Hz, to the highest note up to which its aliasing below 5 kHz stays under -60 dB re its harmonics. Beside the
 * table as `aliasing-limit.txt` and `aliasing-limit.json` (the engine health page judges by it: healthy at or above
 * 5 kHz).
 */
class AliasingRunner : StringSpec({

    val requested = System.getenv("KLANG_PROBE") == "aliasing"

    "aliasing meter: every source and shaper on a steady tone (KLANG_PROBE=aliasing)".config(enabled = requested) {
        val started = System.nanoTime()
        val config = AliasingConfig()
        val cases = aliasingCases()
        val rows = cases.map { AliasingRow(case = it, reading = measureAliasing(samples = it.render(), nominalHz = it.pitchHz, config = config, findPitch = it.findPitch)) }
        val seconds = (System.nanoTime() - started) / 1e9
        val sidelobe = windowPeakSidelobeDb()

        fun g(x: Double): String = String.format(Locale.ROOT, "%.3f", x)

        val report = buildString {
            appendLine("Aliasing meter (KLANG-00420, probe 2), ${Instant.now()}")
            appendLine(
                "sample rate ${config.sampleRate} Hz, block $PROBE_BLOCK_FRAMES frames; each row renders ${g(config.settleFrames.toDouble() / config.sampleRate)} s " +
                    "to settle, then ONE window of ${config.fftSize} frames (${g(config.fftSize.toDouble() / config.sampleRate)} s), one FFT, bin ${g(config.binHz)} Hz",
            )
            appendLine(
                "window: periodic 7-term Blackman-Harris (Albrecht 2001), main lobe ${BLACKMAN_HARRIS_7_LOBE_BINS} bins each side; " +
                    "its highest sidelobe, measured here: ${String.format(Locale.ROOT, "%.1f", sidelobe)} dB",
            )
            appendLine(
                "bands: every harmonic h * f below Nyquist owns the bins within ${config.bandBins} bins (${g(config.bandBins * config.binHz)} Hz) of it, " +
                    "DC owns bins 0..${config.bandBins}; for a source f is the strongest bin within ${(config.searchFraction * 100).toInt()} percent of the pitch, " +
                    "refined by a parabola through the log power; for a shaper and a control f is the input sine's pitch; everything else is non-harmonic",
            )
            appendLine(
                "pitches ${ALIASING_PITCHES.joinToString(", ")} Hz (7044.4, not 7040: at 7040 the 11th fold lands exactly on a harmonic; " +
                    "at 110 the same happens from the 11th fold on, so there aliases of harmonics past about 4800 count as harmonics)",
            )
            appendLine(
                "sources: bare (no classic()), held, analog=0 on every source (no drift, so no drift reads as aliasing); a super oscillator at " +
                    "voices=1 is measured, at its default 8 voices it is not measurable this way (detune)",
            )
            appendLine(
                "shapers: a pure sine at peak $ALIASING_SINE_LEVEL in, drive amounts ${ALIASING_DRIVES.joinToString(" and ")}; the Ignitor rows read " +
                    "the voice (mono), the Katalyst rows the left channel (both carry the same sine); every harmonic below Nyquist is legitimate there",
            )
            appendLine("the floor of the method is the 'pure sine (test code)' control at each pitch")
            appendLine()
            appendLine("Columns:")
            ALIASING_LEGEND.forEach { appendLine("  $it") }
            appendLine()
            append(formatAliasingTable(rows))
            appendLine()
            appendLine("${rows.size} rows, ${String.format(Locale.ROOT, "%.1f", seconds)} s")
        }

        println(report)

        val target = probeOutputFile("aliasing.txt")

        writeProbeFile(target = target, text = report, what = "aliasing table")
        writeProbeFile(target = jsonTwin(target), text = formatAliasingJson(rows), what = "aliasing JSON")

        // The healthy limit: the sweep of fundamentals.
        fun h(x: Double): String = String.format(Locale.ROOT, "%.0f", x)

        val limitStarted = System.nanoTime()
        val pitches = aliasingSweepPitches(config.sampleRate)
        val limits = measureAliasingLimits(pitches = pitches, config = config)
        val limitSeconds = (System.nanoTime() - limitStarted) / 1e9
        val limitReport = buildString {
            appendLine("Aliasing meter, the healthy limit (KLANG-00420, decision of 2026-11-11), ${Instant.now()}")
            appendLine(
                "per source and shaper: the highest swept fundamental up to which the aliasing below ${h(ALIASING_AUDIBLE_BAND_HZ)} Hz " +
                    "stays under ${h(ALIASING_HEALTHY_DB)} dB re the harmonics, at that pitch and every lower one",
            )
            appendLine(
                "level per pitch: the power of every bin outside the harmonic and DC bands and below ${h(ALIASING_AUDIBLE_BAND_HZ)} Hz, " +
                    "re the power of all harmonic bands (the fundamental's included); the meter's method otherwise (the table's header)",
            )
            appendLine(
                "sweep: ${pitches.size} fundamentals from ${h(pitches.first())} to ${h(pitches.last())} Hz, fs / (N + $ALIASING_SWEEP_FRACTION) " +
                    "for whole N, about a sixth of an octave apart, ascending; a case leaves the sweep at its first unhealthy pitch",
            )
            appendLine(
                "healthy_to_Hz: the limit (the pitch below the first unhealthy one; '>=' when none failed up to the top; 0 when the lowest failed); " +
                    "verdict: healthy at or above ${h(ALIASING_HEALTHY_FROM_HZ)} Hz; at_dB: the level at the first unhealthy pitch",
            )
            appendLine("judged: the measured rows and the controls; the by-design and not-measurable rows of the table are not swept")
            appendLine()
            append(formatAliasingLimitTable(limits))
            appendLine()
            appendLine("${limits.size} rows, ${String.format(Locale.ROOT, "%.1f", limitSeconds)} s")
        }

        println(limitReport)

        val limitTarget = File(target.parentFile, target.nameWithoutExtension + "-limit.txt")

        writeProbeFile(target = limitTarget, text = limitReport, what = "aliasing limit table")
        writeProbeFile(target = jsonTwin(limitTarget), text = formatAliasingLimitJson(limits), what = "aliasing limit JSON")
    }
})

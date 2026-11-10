/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.probes

import io.kotest.core.spec.style.StringSpec
import java.time.Instant
import java.util.Locale

/**
 * The click detector's RUNNER (KLANG-00420, probe 3): every row of [clickCases] read by [measureClick] at each of its
 * instants, both channels, the worst kept, printed and written to a file. A measurement, not a gate: it asserts nothing
 * about the numbers.
 *
 * **Out of the normal run.** Its one test is disabled unless the environment holds `KLANG_PROBE=clicks`, so a normal
 * `jvmTest` reports it as skipped and measures nothing (the stage probe's pattern, [StageProbeRunner]). Run it:
 *
 *     KLANG_PROBE=clicks console/with-build-lock.sh ./gradlew :audio_be:jvmTest --rerun \
 *         --tests io.peekandpoke.klang.audio_be.probes.ClickRunner
 *
 * The table goes to `$KLANG_PROBE_OUT` when set (a file path), else to `clicks.txt` in `<build dir>/probes/`
 * ([probeOutputFile]); the same rows go beside it as JSON (`clicks.json`, [formatClickJson]).
 */
class ClickRunner : StringSpec({

    val requested = System.getenv("KLANG_PROBE") == "clicks"

    "click detector: every knob jump, stage switch, chain swap and voice edge (KLANG_PROBE=clicks)".config(enabled = requested) {
        val started = System.nanoTime()
        val cases = clickCases()
        val rows = cases.map { case ->
            val readings = (0 until case.instants).map { measureClickStereo(render = case.render(it), config = case.config) }

            ClickRow(case = case, reading = readings.reduce { a, b -> worstClick(a, b) })
        }
        val seconds = (System.nanoTime() - started) / 1e9
        val config = ClickConfig()
        val highpass = clickHighpass(config)

        fun db(hz: Double): String = String.format(Locale.ROOT, "%.1f", firResponseDb(taps = highpass, hz = hz, sampleRate = config.sampleRate))

        val report = buildString {
            appendLine("Click detector (KLANG-00420, probe 3), ${Instant.now()}")
            appendLine(
                "sample rate ${config.sampleRate} Hz, block $PROBE_BLOCK_FRAMES frames; the input is a sine at $CLICK_TONE_HZ Hz, peak $CLICK_TONE_LEVEL, " +
                    "faded in over 0.1 s, both channels; the change lands on a block boundary at ${CLICK_EVENT_FRAME.toDouble() / config.sampleRate} s " +
                    "(instants 2 to 4 one block later each), the tone at its peak at the first instant",
            )
            appendLine(
                "windows around the event E: before ${config.beforeSeconds} s ending at E, event ${config.eventSeconds} s from E " +
                    "($CLICK_DRAIN_SECONDS s for a delay or reverb switch-off and the drain swap), after ${config.afterSeconds} s right after the event window",
            )
            appendLine(
                "high band: ${config.highpassTaps}-tap linear-phase windowed-sinc highpass at ${config.highpassHz} Hz (7-term Blackman-Harris), on the whole run; " +
                    "measured response: ${db(110.0)} dB at 110 Hz, ${db(1000.0)} dB at 1 kHz, ${db(3000.0)} dB at 3 kHz, ${db(4500.0)} dB at 4.5 kHz, " +
                    "${db(5000.0)} dB at 5 kHz, ${db(6000.0)} dB at 6 kHz, ${db(10000.0)} dB at 10 kHz; short-time RMS over ${config.burstFrames} frames",
            )
            appendLine(
                "level track: centred RMS over one period (${config.levelFrames} frames, ${String.format(Locale.ROOT, "%.1f", config.levelFrames * 1000.0 / config.sampleRate)} ms); " +
                    "a one-sample step reads a rise of about 0.8 periods (about 7 ms): that is this meter's time resolution",
            )
            appendLine(
                "katalyst: the owner's slot map handed to applyParams per block (the map changes at E), process, processDuck; the classic chain " +
                    "for its stages (switch ON = the door's companion slots written, OFF = an empty map), a declared one-stage chain with Kat.param " +
                    "knobs for the others; a duck's sidechain is the tone itself",
            )
            appendLine(
                "swap: a real Cylinder (the swap specs' CylinderSwapRig) with a sounding owner, the tone written into its mix every block, the new " +
                    "chain requested at E",
            )
            appendLine(
                "voice: the built-in sound through the real VoiceFactory at $CLICK_TONE_HZ Hz, analog=0, gain 1, pan centre, onset " +
                    "${CLICK_VOICE_START.toDouble() / config.sampleRate} s (mid-block), gate 2 s later, a cut 1 s after the onset; read off the cylinder mix",
            )
            appendLine(
                "a voice row is evidence only where the source is smooth (sine, tri, supersine, supertri, perlin, berlin): a bright source's " +
                    "own edges or a noise are the steady reference there and hide a step (see sens); a pluck's start is its excitation",
            )
            appendLine(
                "the meter's range is the four 'by hand' controls: nothing happens (the floor), a hard step, a one-block ramp, a 50 ms glide " +
                    "(none by the knob-glide law)",
            )
            appendLine()
            appendLine("Columns:")
            CLICK_LEGEND.forEach { appendLine("  $it") }
            appendLine()
            append(formatClickTable(rows))
            appendLine()
            appendLine("${rows.size} rows, ${String.format(Locale.ROOT, "%.1f", seconds)} s")
        }

        println(report)

        val target = probeOutputFile("clicks.txt")

        writeProbeFile(target = target, text = report, what = "click table")
        writeProbeFile(target = jsonTwin(target), text = formatClickJson(rows), what = "click JSON")
    }
})

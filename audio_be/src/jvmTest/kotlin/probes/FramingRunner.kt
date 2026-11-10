/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.probes

import io.kotest.core.spec.style.StringSpec
import java.time.Instant
import java.util.Locale

/**
 * The block-framing checker's RUNNER (KLANG-00420, probe 4; the property is KLANG-00156's): every row of
 * [framingCases] swept by [measureFraming] over all 128 offsets inside the block, printed and written to a file. A
 * measurement, not a gate: it asserts nothing about the numbers.
 *
 * **Out of the normal run.** Its one test is disabled unless the environment holds `KLANG_PROBE=framing`, so a normal
 * `jvmTest` reports it as skipped and measures nothing (the stage probe's pattern, [StageProbeRunner]). Run it:
 *
 *     KLANG_PROBE=framing console/with-build-lock.sh ./gradlew :audio_be:jvmTest --rerun \
 *         --tests io.peekandpoke.klang.audio_be.probes.FramingRunner
 *
 * The tables go to `$KLANG_PROBE_OUT` when set (a file path), else to `framing.txt` in `<build dir>/probes/`
 * ([probeOutputFile]); the same rows go beside it as JSON (`framing.json`, [formatFramingJson]).
 */
class FramingRunner : StringSpec({

    val requested = System.getenv("KLANG_PROBE") == "framing"

    "block-framing checker: every source, stage, note end, cut, sample voice and bus at all 128 offsets (KLANG_PROBE=framing)".config(enabled = requested) {
        val started = System.nanoTime()
        val cases = framingCases()
        val rows = cases.map { case ->
            FramingRow(case = case, result = measureFraming(renderer = case.renderer, sweep = case.sweep, offsets = FRAMING_ALL_OFFSETS, config = case.config))
        }
        val seconds = (System.nanoTime() - started) / 1e9
        val config = FramingConfig()

        val report = buildString {
            appendLine("Block-framing checker (KLANG-00420, probe 4; KLANG-00156), ${Instant.now()}")
            appendLine(
                "sample rate $PROBE_SAMPLE_RATE Hz, block $PROBE_BLOCK_FRAMES frames (pinned), offsets 0..${PROBE_BLOCK_FRAMES - 1}; " +
                    "every render a fresh VoiceFactory + PlaybackCtx (pid '$FRAMING_PID', so one voice seed) + Cylinders, Voice.render per block from frame 0",
            )
            appendLine(
                "onset sweep: gate ${config.gateFrames} frames (closes at frame ${config.gateFrames % PROBE_BLOCK_FRAMES} of its block at offset 0); " +
                    "gate sweep: gate ${config.gateBaseFrames} + k; cut sweep: cut ${config.cutBaseFrames} + k after the onset, gate ${config.cutGateFrames}; " +
                    "compared to ${config.tailFrames} frames after the latest event (the bus rows: ${2 * PROBE_SAMPLE_RATE})",
            )
            appendLine(
                "voice rows: $FRAMING_NOTE_HZ Hz, gain 1, pan centre, read off the cylinder's mix before any bus stage (both channels); " +
                    "bus rows: the fusion mix after Cylinders.processAndMix, the voice's katalystParams owning the classic chain",
            )
            appendLine("stage rows: the built-in saw voice (saw.pregain().classic()), analog 0, adsr.on=0 unless the adsr is the stage")
            appendLine()
            appendLine("Columns:")
            FRAMING_LEGEND.forEach { appendLine("  $it") }
            appendLine()
            append(formatFramingTable(rows))
            appendLine()
            appendLine("Known laws (the 'known' column):")
            rows.withIndex().filter { it.value.case.known.isNotEmpty() }.groupBy { it.value.case.known }.forEach { (known, group) ->
                appendLine("  rows ${group.joinToString(",") { (it.index + 1).toString() }}: $known")
            }
            appendLine()
            appendLine("Every offset that is not identical, by case number:")
            append(formatFramingOffsets(rows))
            appendLine()
            appendLine(
                "${rows.size} cases, ${rows.sumOf { it.result.readings.size }} offsets; classes: " +
                    FramingClass.entries.joinToString(", ") { cls -> "${cls.name.lowercase()} ${rows.count { it.result.cls == cls }}" } +
                    "; ${String.format(Locale.ROOT, "%.1f", seconds)} s",
            )
        }

        println(report)

        val target = probeOutputFile("framing.txt")

        writeProbeFile(target = target, text = report, what = "framing table")
        writeProbeFile(target = jsonTwin(target), text = formatFramingJson(rows), what = "framing JSON")
    }
})

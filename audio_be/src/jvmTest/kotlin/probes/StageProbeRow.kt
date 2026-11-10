/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.probes

import java.util.Locale

/** One measured row of the stage probe's table: [case] under [signal], with its [facts]. */
class StageProbeRow(
    val case: StageProbeCase,
    val signal: ProbeSignal,
    val facts: StageFacts,
)

/** The column legend printed above the table. */
val STAGE_PROBE_LEGEND: List<String> = listOf(
    "peak      largest finite |out| (both channels)",
    "gain_dB   peak re the largest finite |in|",
    "dc_in     mean of out over the last 0.1 s of the input (the larger |mean| of the two channels)",
    "dc_end    mean of out over the last 0.1 s of the render (deep in the silence after the input)",
    "nonfin    output samples that are NaN or infinite (0 = all finite)",
    "fin_again seconds from the poison sample (else the first bad output) until out is finite for good; never = latched",
    "tail_s    seconds from the input's end until |out| stays below 1e-5 (NaN counts as loud); never = not in the render",
    "zero_s    seconds from the input's end until out stays exactly 0.0; never = not in the render",
    "subnorm   count of subnormal output samples (0 < |x| < 2.2e-308), and the time of the first",
    "min_tail  smallest non-zero finite |out| after the input's end (how deep the tail sinks before it stops)",
    "x-ch      stereo only, poison rows: did a non-finite sample reach the CLEAN right channel",
)

private fun g(x: Double): String = when {
    x != x -> "NaN" // NaN-guard
    x == Double.POSITIVE_INFINITY -> "-"
    else -> String.format(Locale.ROOT, "%.3g", x)
}

private fun seconds(frames: Int, sampleRate: Int): String =
    if (frames == NEVER) "never" else String.format(Locale.ROOT, "%.4f", frames.toDouble() / sampleRate)

/**
 * One row as a JSON object's fields, for the page that shows the table: the case and signal names, then every field
 * of [OutputFacts] over both channels folded ([StageFacts.worst], the table's row) under its own name, frames as
 * frame counts ([NEVER] stays -1), then `gainDb`, `allFinite` and `crossed` (null for a mono stage).
 */
fun stageProbeJsonRow(row: StageProbeRow): Map<String, Any?> {
    val f = row.facts.worst

    return linkedMapOf(
        "family" to row.case.family,
        "stage" to row.case.stage,
        "knobs" to row.case.knobs,
        "signal" to row.signal.name,
        "frames" to f.frames,
        "peak" to f.peak,
        "inputPeak" to f.inputPeak,
        "dcInput" to f.dcInput,
        "dcEnd" to f.dcEnd,
        "nonFiniteCount" to f.nonFiniteCount,
        "firstNonFiniteFrame" to f.firstNonFiniteFrame,
        "lastNonFiniteFrame" to f.lastNonFiniteFrame,
        "finiteAgainFrames" to f.finiteAgainFrames,
        "tailFrames" to f.tailFrames,
        "zeroFrames" to f.zeroFrames,
        "subnormalCount" to f.subnormalCount,
        "firstSubnormalFrame" to f.firstSubnormalFrame,
        "minTailAbs" to f.minTailAbs,
        "gainDb" to f.gainDb,
        "allFinite" to f.allFinite,
        "crossed" to row.facts.crossed,
    )
}

/** The whole table as JSON: an array of [stageProbeJsonRow] objects, in table order. */
fun formatStageProbeJson(rows: List<StageProbeRow>): String = jsonRows(rows.map { stageProbeJsonRow(it) })

/** The table: one row per stage, knob set and signal, plain columns padded to their widest cell. */
fun formatStageProbeTable(rows: List<StageProbeRow>, sampleRate: Int = PROBE_SAMPLE_RATE): String {
    val header = listOf(
        "family", "stage", "knobs", "signal", "peak", "gain_dB", "dc_in", "dc_end", "nonfin", "fin_again", "tail_s", "zero_s",
        "subnorm", "min_tail", "x-ch",
    )
    val cells = rows.map { row ->
        val f = row.facts.worst
        val poisoned = row.signal.poisonFrame >= 0

        listOf(
            row.case.family,
            row.case.stage,
            row.case.knobs,
            row.signal.name,
            g(f.peak),
            if (f.gainDb != f.gainDb) "-" else String.format(Locale.ROOT, "%+.1f", f.gainDb), // NaN-guard
            g(f.dcInput),
            g(f.dcEnd),
            f.nonFiniteCount.toString(),
            if (f.allFinite) "-" else seconds(frames = f.finiteAgainFrames, sampleRate = sampleRate),
            seconds(frames = f.tailFrames, sampleRate = sampleRate),
            seconds(frames = f.zeroFrames, sampleRate = sampleRate),
            if (f.subnormalCount == 0) "0" else "${f.subnormalCount}@${seconds(frames = f.firstSubnormalFrame, sampleRate = sampleRate)}",
            g(f.minTailAbs),
            when (val crossed = row.facts.crossed) {
                null -> "mono"
                else -> if (poisoned) (if (crossed) "YES" else "no") else "-"
            },
        )
    }
    val widths = header.indices.map { col -> maxOf(header[col].length, cells.maxOfOrNull { it[col].length } ?: 0) }

    fun line(values: List<String>): String = values.indices.joinToString("  ") { values[it].padEnd(widths[it]) }.trimEnd()

    return buildString {
        appendLine(line(header))
        appendLine(widths.joinToString("  ") { "-".repeat(it) })

        for (row in cells) {
            appendLine(line(row))
        }
    }
}

/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.probes

import java.util.Locale
import kotlin.math.log10

// The click table's two forms (KLANG-00420, probe 3): the text table and the JSON rows for a page.

/** The column legend printed above the click table. */
val CLICK_LEGEND: List<String> = listOf(
    "jump_x     largest |y[n] - y[n-1]| in the event window over the largest in the steady windows (before and after)",
    "kink_x     the same for |y[n] - 2 y[n-1] + y[n-2]|: a step reads in both, a slope that breaks only here",
    "kink_dB    kink_x in dB (20 log10)",
    "kink_ms    where the largest second difference sits, ms after the event",
    "burst_rel  peak 0.67 ms RMS of the signal above 5 kHz (FIR highpass) in the event window re the same in the steady windows, dB",
    "burst_abs  the same peak re the signal's RMS (the louder of before and after), dB",
    "lvl_b/a    RMS before and after (the after window starts at the event window's end)",
    "rise_ms    10 to 90 percent of the level's move, on a one-period RMS track; '-' = the level moves 5 percent or less",
    "settle_ms  from the event to 90 percent; a value at the event window's length means it did not settle within it",
    "x_glide    kink_x over the 50 ms glide control's kink_x; dB_glide: burst_abs minus the glide control's burst_abs",
    "sens       what a step of 10 percent of the signal's RMS would read as kink_x here: under about 3 the row cannot tell such a",
    "           step from the signal's own edges or noise (a bright or noisy source), so its 'no click' is no evidence",
    "Every Katalyst, control and swap row is the worst of 4 instants one block apart (the tone's phase 105.6 degrees",
    "further each) and of both channels; a voice row is one render, both channels.",
)

private fun f(x: Double, digits: Int): String = when {
    x != x -> "-" // NaN-guard
    x.isInfinite() -> if (x > 0) "inf" else "-inf"
    else -> String.format(Locale.ROOT, "%.${digits}f", x)
}

/**
 * What a level step of 10 percent of the signal's RMS would read as [ClickReading.kinkRatio] against this row's steady
 * kink: the row's sensitivity. A bright source's own edges, or a noise, set a steady kink so high that a real step
 * disappears in it.
 */
fun clickSensitivity(r: ClickReading): Double = 0.1 * maxOf(r.levelBefore, r.levelAfter) / r.kinkSteady

/** The row of the 50 ms glide control, which every row is also quoted against. */
fun glideControl(rows: List<ClickRow>): ClickRow? = rows.firstOrNull { it.case.family == "control" && it.case.knob == "50 ms gain glide" }

/** The text table, one row per case in the order given, plain columns padded to their widest cell. */
fun formatClickTable(rows: List<ClickRow>, sampleRate: Int = PROBE_SAMPLE_RATE): String {
    val glide = glideControl(rows)?.reading
    val header = listOf(
        "family", "stage", "knob", "change", "jump_x", "kink_x", "kink_dB", "kink_ms", "burst_rel", "burst_abs", "lvl_b", "lvl_a", "rise_ms",
        "settle_ms", "x_glide", "dB_glide", "sens",
    )
    val cells = rows.map { row ->
        val r = row.reading

        listOf(
            row.case.family,
            row.case.stage,
            row.case.knob,
            row.case.change,
            f(r.jumpRatio, 2),
            f(r.kinkRatio, 2),
            f(20.0 * log10(r.kinkRatio), 1),
            f(r.kinkOffsetFrames * 1000.0 / sampleRate, 2),
            f(r.burstRelDb, 1),
            f(r.burstAbsDb, 1),
            f(r.levelBefore, 4),
            f(r.levelAfter, 4),
            f(r.riseMs, 1),
            f(r.settleMs, 1),
            if (glide != null) f(r.kinkRatio / glide.kinkRatio, 2) else "-",
            if (glide != null) f(r.burstAbsDb - glide.burstAbsDb, 1) else "-",
            f(clickSensitivity(r), 1),
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

/** One row as a JSON object's fields, for the page that shows the table. */
fun clickJsonRow(row: ClickRow): Map<String, Any?> {
    val r = row.reading

    return linkedMapOf(
        "family" to row.case.family,
        "stage" to row.case.stage,
        "knob" to row.case.knob,
        "change" to row.case.change,
        "instants" to row.case.instants,
        "eventSeconds" to row.case.config.eventSeconds,
        "jumpRatio" to r.jumpRatio,
        "kinkRatio" to r.kinkRatio,
        "jumpEvent" to r.jumpEvent,
        "jumpSteady" to r.jumpSteady,
        "kinkEvent" to r.kinkEvent,
        "kinkSteady" to r.kinkSteady,
        "kinkOffsetFrames" to r.kinkOffsetFrames,
        "burstRelDb" to r.burstRelDb,
        "burstAbsDb" to r.burstAbsDb,
        "levelBefore" to r.levelBefore,
        "levelAfter" to r.levelAfter,
        "riseMs" to r.riseMs,
        "settleMs" to r.settleMs,
        "sensitivity" to clickSensitivity(r),
    )
}

/** The whole table as JSON: an array of [clickJsonRow] objects, in table order. */
fun formatClickJson(rows: List<ClickRow>): String = jsonRows(rows.map { clickJsonRow(it) })

/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.probes

import java.util.Locale

// The aliasing table's two forms (KLANG-00420, probe 2): the text table and the JSON rows for a page.

/** The column legend printed above the aliasing table. */
val ALIASING_LEGEND: List<String> = listOf(
    "pitch      the note or the input sine, Hz",
    "kind       control (the floor or a harness), measured (off-harmonic energy is aliasing or imaging), by-design (inharmonic",
    "           on purpose, the number is not a defect), n/m (not measurable this way: no number, the note says why)",
    "f_cents    the fundamental found, re the pitch asked, in cents (sources only; a shaper's grid is its input sine's exact pitch)",
    "fund_dBFS  the fundamental's level (a full-scale sine reads 0)",
    "harm_dB    harmonics 2 and up below Nyquist, total, re the fundamental",
    "nonh_dB    every bin outside the harmonic and DC bands, total, re the fundamental: the aliasing",
    "nonh_reH   the same re ALL harmonic bands (the fundamental's included); the number to read where the fundamental is weak",
    "nonh_dBFS  the same as an absolute level",
    "nonh<5k    the non-harmonic energy below 5 kHz only, re the fundamental: the aliases where they are heard (KLANG-00430)",
    "top_Hz     the strongest non-harmonic component, and top_dB its level re the fundamental",
    "dc_dBFS    the DC band (20 log10 of the DC value); not counted as non-harmonic",
    "note       why a row is a control, by design or not measurable",
)

/** A kind as the table and the JSON print it. */
private fun kindLabel(kind: AliasingKind): String = when (kind) {
    AliasingKind.CONTROL -> "control"
    AliasingKind.MEASURED -> "measured"
    AliasingKind.BY_DESIGN -> "by-design"
    AliasingKind.NOT_MEASURABLE -> "n/m"
}

private fun f(x: Double, digits: Int): String = when {
    x != x -> "NaN" // NaN-guard
    x.isInfinite() -> if (x > 0) "inf" else "-inf"
    else -> String.format(Locale.ROOT, "%.${digits}f", x)
}

/** The text table, one row per case in the order given, plain columns padded to their widest cell. */
fun formatAliasingTable(rows: List<AliasingRow>): String {
    val header = listOf(
        "family", "stage", "knobs", "pitch", "kind", "f_cents", "fund_dBFS", "harm_dB", "nonh_dB", "nonh_reH", "nonh_dBFS", "nonh<5k", "top_Hz", "top_dB",
        "dc_dBFS", "note",
    )
    val cells = rows.map { row ->
        val r = row.reading
        val shown = row.case.kind != AliasingKind.NOT_MEASURABLE

        fun num(x: Double, digits: Int): String = if (shown) f(x, digits) else "n/m"

        listOf(
            row.case.family,
            row.case.stage,
            row.case.knobs,
            f(row.case.pitchHz, 1),
            kindLabel(row.case.kind),
            num(r.offsetCents, 3),
            num(r.fundamentalDbfs, 2),
            num(r.harmonicDb, 1),
            num(r.nonHarmonicDb, 1),
            num(r.nonHarmonicReHarmonicsDb, 1),
            num(r.nonHarmonicDbfs, 1),
            num(r.nonHarmonicBelow5kDb, 1),
            num(r.strongestHz, 1),
            num(r.strongestDb, 1),
            num(r.dcDbfs, 1),
            row.case.note,
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

/**
 * One row as a JSON object's fields, for the page that shows the table. A not-measurable row keeps its numbers (with
 * `measurable` false and its `note`): a reader must not rank them.
 */
fun aliasingJsonRow(row: AliasingRow): Map<String, Any?> {
    val r = row.reading

    return linkedMapOf(
        "family" to row.case.family,
        "stage" to row.case.stage,
        "knobs" to row.case.knobs,
        "pitchHz" to row.case.pitchHz,
        "kind" to kindLabel(row.case.kind),
        "measurable" to (row.case.kind != AliasingKind.NOT_MEASURABLE),
        "note" to row.case.note,
        "fundamentalHz" to r.fundamentalHz,
        "offsetCents" to r.offsetCents,
        "fundamentalDbfs" to r.fundamentalDbfs,
        "harmonicCount" to r.harmonicCount,
        "harmonicDb" to r.harmonicDb,
        "harmonicsDbfs" to r.harmonicsDbfs,
        "nonHarmonicDb" to r.nonHarmonicDb,
        "nonHarmonicReHarmonicsDb" to r.nonHarmonicReHarmonicsDb,
        "nonHarmonicDbfs" to r.nonHarmonicDbfs,
        "nonHarmonicBelow5kDb" to r.nonHarmonicBelow5kDb,
        "strongestHz" to r.strongestHz,
        "strongestDb" to r.strongestDb,
        "dcDbfs" to r.dcDbfs,
    )
}

/** The whole table as JSON: an array of [aliasingJsonRow] objects, in table order. */
fun formatAliasingJson(rows: List<AliasingRow>): String = jsonRows(rows.map { aliasingJsonRow(it) })

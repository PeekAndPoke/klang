/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.probes

import java.util.Locale

// The block-framing table's two forms (KLANG-00420, probe 4): the text tables (one row per case, then the offsets of
// every case that is not identical) and the JSON rows for a page.

/** The column legend printed above the framing tables. */
val FRAMING_LEGEND: List<String> = listOf(
    "sweep      onset: the note (fixed gate) starts at offset k, reference at 0; gate: onset on a border, the gate closes at",
    "           offset k, reference = the same note started where its gate lands on a border; cut: as gate, for Voice.cutOff",
    "class      the worst offset's: identical (bit for bit), rounding (max |diff| <= 1e-10 of the reference peak, -200 dB),",
    "           small (<= 1e-5, -100 dB), audible (above)",
    "ident/round/small/aud  how many offsets read each class",
    "worst      the offset with the largest max |diff| re the reference peak",
    "max_dB     max |diff| re the reference's peak, dB; rms_dB: RMS of the difference re the reference's RMS, dB",
    "first      the first differing note frame (from the onset); max@ev: where the largest diff sits, frames from the event",
    "           (the gate close in onset and gate sweeps, the cut in the cut sweep)",
    "region     where the first difference lies: onset (first block [0,128)), transition (within a block of the event),",
    "           held (between), release (after the transition window)",
    "extent     confined: nothing differs after the region's head window (onset: [0,128); transition: [E-128, E+128);",
    "           else one block from the first diff); spills: it goes on, its RMS after the head more than 20 dB under the",
    "           head's; persists: anything else (a phase or clock offset, a per-block law)",
    "persist    RMS of the diff after the head window (to the last differing frame) re its RMS inside, dB",
    "late       the largest |diff| late in the held note, the 7 blocks before the transition window [E-8*128, E-128):",
    "           0 for a defect that is over by then, the offset's size for a phase or clock offset or a per-block law",
    "pre        the largest |sample| either render put out before its own onset (must be 0)",
    "rng        offsets whose voice drew a different number of times from its random stream (read once the voice is done);",
    "           '?' = unknown at every offset (the voice outlived the render)",
    "peak       the reference's peak (a silent reference makes the row vacuous)",
    "known      'yes' where the KLANG-00156 ledger or audio/MEMORY.md names the law (quoted under the table)",
)

private fun f(x: Double, digits: Int): String = when {
    x != x -> "-" // NaN-guard
    x.isInfinite() -> if (x > 0) "inf" else "-inf"
    else -> String.format(Locale.ROOT, "%.${digits}f", x)
}

private fun e(x: Double): String = when {
    x != x -> "-" // NaN-guard
    x.isInfinite() -> if (x > 0) "inf" else "-inf"
    x == 0.0 -> "0"
    else -> String.format(Locale.ROOT, "%.3e", x)
}

private fun frame(n: Int): String = if (n == NEVER) "-" else n.toString()

/** How many offsets of [result] had a different rng draw count, or '?' when none could be read. */
private fun rngCell(result: FramingResult): String {
    val known = result.readings.filter { it.rngSame != null }

    return if (known.isEmpty()) "?" else known.count { it.rngSame == false }.toString()
}

/** Plain columns padded to their widest cell. */
private fun table(header: List<String>, cells: List<List<String>>): String {
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

/** The case table: one row per case, its worst offset's numbers. */
fun formatFramingTable(rows: List<FramingRow>): String {
    val header = listOf(
        "#", "family", "stage", "knobs", "sweep", "class", "ident", "round", "small", "aud", "worst", "max_abs", "max_dB", "rms_dB",
        "first", "max@ev", "region", "extent", "persist", "late", "pre", "rng", "peak", "known",
    )
    val cells = rows.mapIndexed { index, row ->
        val r = row.result
        val w = r.worst

        listOf(
            (index + 1).toString(),
            row.case.family,
            row.case.name,
            row.case.knobs,
            r.sweep.name.lowercase(),
            r.cls.name.lowercase(),
            r.count(FramingClass.IDENTICAL).toString(),
            r.count(FramingClass.ROUNDING).toString(),
            r.count(FramingClass.SMALL).toString(),
            r.count(FramingClass.AUDIBLE).toString(),
            if (w.cls == FramingClass.IDENTICAL) "-" else w.offset.toString(),
            e(w.maxAbsDiff),
            f(w.maxRelDb, 1),
            f(w.rmsRelDb, 1),
            frame(w.firstDiffFrame),
            frame(w.maxDiffFromEvent),
            w.region.name.lowercase(),
            w.extent.name.lowercase(),
            f(w.persistDb, 1),
            e(w.lateMaxAbs),
            e(r.readings.maxOf { it.preOnsetPeak }),
            rngCell(r),
            f(w.referencePeak, 3),
            if (row.case.known.isEmpty()) "" else "yes",
        )
    }

    return table(header, cells)
}

/** The offset table: every offset that is not identical, of every case, case by case (the case's number from [formatFramingTable]). */
fun formatFramingOffsets(rows: List<FramingRow>): String {
    val header = listOf(
        "#", "stage", "sweep", "k", "class", "max_abs", "max_dB", "rms_dB", "first", "last", "frames", "max@", "max@ev", "region", "extent",
        "head_max", "rest_max", "persist", "late", "rng",
    )
    val cells = ArrayList<List<String>>()

    rows.forEachIndexed { index, row ->
        for (r in row.result.readings) {
            if (r.cls == FramingClass.IDENTICAL) {
                continue
            }

            cells.add(
                listOf(
                    (index + 1).toString(),
                    "${row.case.name} ${row.case.knobs}",
                    row.result.sweep.name.lowercase(),
                    r.offset.toString(),
                    r.cls.name.lowercase(),
                    e(r.maxAbsDiff),
                    f(r.maxRelDb, 1),
                    f(r.rmsRelDb, 1),
                    frame(r.firstDiffFrame),
                    frame(r.lastDiffFrame),
                    r.diffFrames.toString(),
                    frame(r.maxDiffFrame),
                    frame(r.maxDiffFromEvent),
                    r.region.name.lowercase(),
                    r.extent.name.lowercase(),
                    e(r.headMaxAbs),
                    e(r.restMaxAbs),
                    f(r.persistDb, 1),
                    e(r.lateMaxAbs),
                    when (r.rngSame) {
                        null -> "?"
                        true -> "same"
                        false -> "DIFFERS"
                    },
                ),
            )
        }
    }

    return table(header, cells)
}

/** One reading as a JSON object's fields. */
fun framingJsonReading(r: FramingReading): Map<String, Any?> = linkedMapOf(
    "offset" to r.offset,
    "class" to r.cls.name.lowercase(),
    "referenceOnset" to r.referenceOnset,
    "testOnset" to r.testOnset,
    "eventFrame" to r.eventFrame,
    "spanFrames" to r.spanFrames,
    "referencePeak" to r.referencePeak,
    "referenceRms" to r.referenceRms,
    "maxAbsDiff" to r.maxAbsDiff,
    "maxRelDb" to r.maxRelDb,
    "rmsRelDb" to r.rmsRelDb,
    "maxDiffFrame" to r.maxDiffFrame,
    "maxDiffFromEvent" to r.maxDiffFromEvent,
    "firstDiffFrame" to r.firstDiffFrame,
    "lastDiffFrame" to r.lastDiffFrame,
    "diffFrames" to r.diffFrames,
    "region" to r.region.name.lowercase(),
    "extent" to r.extent.name.lowercase(),
    "headMaxAbs" to r.headMaxAbs,
    "restMaxAbs" to r.restMaxAbs,
    "persistDb" to r.persistDb,
    "lateMaxAbs" to r.lateMaxAbs,
    "preOnsetPeak" to r.preOnsetPeak,
    "rngSame" to r.rngSame,
)

/** One case as a JSON object: its summary, its worst offset, and every offset that is not identical. */
fun framingJsonRow(row: FramingRow): Map<String, Any?> {
    val r = row.result

    return linkedMapOf(
        "family" to row.case.family,
        "stage" to row.case.name,
        "knobs" to row.case.knobs,
        "sweep" to r.sweep.name.lowercase(),
        "class" to r.cls.name.lowercase(),
        "offsets" to r.readings.size,
        "identical" to r.count(FramingClass.IDENTICAL),
        "rounding" to r.count(FramingClass.ROUNDING),
        "small" to r.count(FramingClass.SMALL),
        "audible" to r.count(FramingClass.AUDIBLE),
        "rngDiffers" to r.readings.count { it.rngSame == false },
        "rngKnown" to r.readings.count { it.rngSame != null },
        "preOnsetPeak" to r.readings.maxOf { it.preOnsetPeak },
        "known" to row.case.known,
        "worst" to framingJsonReading(r.worst),
        "differing" to r.readings.filter { it.cls != FramingClass.IDENTICAL }.map { framingJsonReading(it) },
    )
}

/** The whole table as JSON: an array of [framingJsonRow] objects, in table order. */
fun formatFramingJson(rows: List<FramingRow>): String = jsonRows(rows.map { framingJsonRow(it) })

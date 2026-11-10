/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.probes

// The stage cost table's two forms (KLANG-00420, probe 7): the text table and the JSON rows for a page.

/** The column legend printed above the cost table. */
val STAGE_COST_LEGEND: List<String> = listOf(
    "net_ns    ns per sample, median over the rounds, minus the median of the row's baseline control (the harness)",
    "rt_%      net_ns as a share of one sample period at the probe's rate (100 = one whole core for this alone)",
    "x_sine    net_ns over the plain sine's net_ns",
    "raw_ns    ns per sample, median, harness included; min and max over the rounds beside it",
    "width_%   (max - min) / median of raw_ns: how far the rounds spread",
    "x_ctrl    raw_ns over the baseline control's raw_ns",
    "build_us  the short render: the stage's build plus its first block, median, microseconds",
    "alloc_B   bytes the thread allocated per block in steady state, median; the controls show the harness's own output arrays",
    "net_B     alloc_B minus the baseline control's (non-zero = the stage itself allocates per block: a lead)",
    "baseline  the control row subtracted for net_ns and net_B",
)

/** [rows] by net cost, the most expensive first; a NaN cost last. */
fun sortedByCost(rows: List<StageCostRow>): List<StageCostRow> =
    rows.sortedWith(compareBy<StageCostRow> { it.netNsPerSample != it.netNsPerSample }.thenByDescending { it.netNsPerSample }) // NaN-guard

private fun f(x: Double, digits: Int): String = when {
    x != x -> "NaN" // NaN-guard
    x.isInfinite() -> if (x > 0) "inf" else "-inf"
    else -> formatFixed(x = x, digits = digits)
}

/** The text table, one row per case in the order given (rank = position), plain columns padded to their widest cell. */
fun formatStageCostTable(rows: List<StageCostRow>): String {
    val header = listOf(
        "#", "family", "stage", "knobs", "net_ns", "rt_%", "x_sine", "raw_ns", "min", "max", "width_%", "x_ctrl", "build_us",
        "alloc_B", "net_B", "baseline",
    )
    val cells = rows.mapIndexed { index, row ->
        val ns = row.result.nsPerSample

        listOf(
            (index + 1).toString(),
            row.case.family,
            row.case.stage,
            row.case.knobs,
            f(row.netNsPerSample, 2),
            f(row.realtimeShare * 100.0, 3),
            f(row.ratioToSine, 2),
            f(ns.median, 2),
            f(ns.min, 2),
            f(ns.max, 2),
            f(ns.relativeWidth * 100.0, 1),
            f(row.ratioToControl, 2),
            f(row.result.buildMicros.median, 1),
            f(row.result.allocBytesPerBlock.median, 1),
            f(row.netAllocBytesPerBlock, 1),
            row.case.baseline ?: "-",
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
 * One row as a JSON object's fields, for the page that shows the table; [rank] is its place in the sorted table. A
 * [platform] (`"js"` for the Kotlin/JS table) adds a `platform` field at the end; null leaves it out, as the JVM table
 * has always been written.
 */
fun stageCostJsonRow(row: StageCostRow, rank: Int, platform: String? = null): Map<String, Any?> = linkedMapOf<String, Any?>(
    "rank" to rank,
    "family" to row.case.family,
    "stage" to row.case.stage,
    "knobs" to row.case.knobs,
    "baseline" to row.case.baseline,
    "netNsPerSample" to row.netNsPerSample,
    "realtimeShare" to row.realtimeShare,
    "ratioToSine" to row.ratioToSine,
    "nsPerSample" to row.result.nsPerSample.median,
    "nsPerSampleMin" to row.result.nsPerSample.min,
    "nsPerSampleMax" to row.result.nsPerSample.max,
    "ratioToControl" to row.ratioToControl,
    "buildMicros" to row.result.buildMicros.median,
    "allocBytesPerBlock" to row.result.allocBytesPerBlock.median,
    "netAllocBytesPerBlock" to row.netAllocBytesPerBlock,
    "rounds" to row.result.rounds,
).also { fields ->
    if (platform != null) {
        fields["platform"] = platform
    }
}

/** The table as JSON: an array of [stageCostJsonRow] objects, ranked in the order given, each with [platform] when given. */
fun formatStageCostJson(rows: List<StageCostRow>, platform: String? = null): String =
    jsonRows(rows.mapIndexed { index, row -> stageCostJsonRow(row = row, rank = index + 1, platform = platform) })

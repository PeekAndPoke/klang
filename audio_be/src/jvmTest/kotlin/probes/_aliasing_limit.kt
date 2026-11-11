/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.probes

import java.util.Locale
import kotlin.math.pow
import kotlin.math.roundToInt

// The aliasing meter's healthy limit (KLANG-00420, the maintainer's decision of 2026-11-11: "Judge below 5 kHz. Maybe we
// can also flip it and measure / display up until which frequency the aliasing is healthy (-60 dB)"): per source and
// shaper, the highest note up to which the aliasing that lands below 5 kHz stays under -60 dB. A measurement, never a
// gate; the engine health page judges it (healthy at or above [ALIASING_HEALTHY_FROM_HZ]).

/** The level the aliasing must stay under, dB re the harmonics: -60. */
const val ALIASING_HEALTHY_DB: Double = -60.0

/** The page's verdict line: a source or shaper is healthy when its limit is at or above 5 kHz. */
const val ALIASING_HEALTHY_FROM_HZ: Double = 5000.0

/** The fraction of a sample-rate multiple each sweep pitch sits at: `fs / f = N + 0.382` (see [aliasingSweepPitches]). */
const val ALIASING_SWEEP_FRACTION: Double = 0.382

/**
 * The fundamentals of the sweep, ascending, from about 100 Hz to 14.2 kHz: `fs / (N + 0.382)` for whole numbers N, about
 * a sixth of an octave apart (every N from 3 to 12 at the top, where neighbouring N are already about two semitones
 * apart). The fraction keeps the folds off the harmonics: the first fold of a harmonic lands 0.382 or 0.618 of the
 * fundamental from the nearest harmonic, and the next ones (0.764, 0.146, 0.528, 0.910 ...) stay apart from them up to
 * the eighth, so the meter's harmonic bands do not swallow them. (A pitch where `fs / f` is close to a whole number, such as
 * 7040 Hz or 110 Hz at 48 kHz, would read its aliases as harmonics.)
 */
fun aliasingSweepPitches(sampleRate: Int = PROBE_SAMPLE_RATE): List<Double> {
    val ns = sortedSetOf<Int>()
    var target = 14000.0

    while (target >= 100.0) {
        val n = (sampleRate / target - ALIASING_SWEEP_FRACTION).roundToInt()

        if (n >= 3) {
            ns += n
        }

        target /= 2.0.pow(1.0 / 6.0)
    }

    for (n in 3..12) {
        ns += n
    }

    return ns.map { sampleRate / (it + ALIASING_SWEEP_FRACTION) }.sorted()
}

/** One case's sweep: the levels read at each pitch on the way up, and where it first read unhealthy (NaN: never). */
class AliasingLimitRow(
    val family: String,
    val stage: String,
    val knobs: String,
    val kind: AliasingKind,
    val note: String,
    /** (pitch Hz, the non-harmonic power below 5 kHz re the harmonics, dB) per pitch measured, ascending. */
    val levels: List<Pair<Double, Double>>,
    /** The first pitch whose level was at or above [ALIASING_HEALTHY_DB]; NaN when none was up to the sweep's top. */
    val firstUnhealthyHz: Double,
    /** The highest pitch measured; the sweep's top when nothing failed. */
    val sweepTopHz: Double,
) {
    /**
     * The healthy limit: the highest swept pitch up to which every swept pitch read under [ALIASING_HEALTHY_DB]. 0 when
     * the lowest pitch already failed; the sweep's top when none did (the limit is at least that).
     */
    val healthyToHz: Double
        get() = when {
            firstUnhealthyHz.isNaN() -> sweepTopHz
            levels.size < 2 -> 0.0
            else -> levels[levels.size - 2].first
        }

    /** True when nothing failed up to the sweep's top (the limit is at least [healthyToHz]). */
    val clean: Boolean get() = firstUnhealthyHz.isNaN()
}

/**
 * The healthy limit of every source and shaper of the table that can be judged (the measured rows and the controls; a
 * by-design or not-measurable row is skipped): for each of [pitches] in ascending order, every case still clean is
 * rebuilt at that pitch by [casesAt], rendered and read by [measureAliasing], and its level is
 * [AliasingReading.nonHarmonicBelow5kReHarmonicsDb], the aliasing where it is heard (below 5 kHz) re the signal (all the
 * harmonics, so a rectifier without a fundamental reads right). A case leaves the sweep at its first pitch at or above
 * [ALIASING_HEALTHY_DB]. Cases are matched across pitches by family, stage and knobs.
 */
fun measureAliasingLimits(
    pitches: List<Double> = aliasingSweepPitches(),
    config: AliasingConfig = AliasingConfig(),
    casesAt: (Double) -> List<AliasingCase> = { aliasingControlCases(it) + aliasingSourceCases(it) + aliasingShaperCases(it) },
): List<AliasingLimitRow> {
    class Track(val first: AliasingCase) {
        val levels = ArrayList<Pair<Double, Double>>()
        var failedAt = Double.NaN
    }

    val tracks = LinkedHashMap<String, Track>()

    fun key(c: AliasingCase) = "${c.family}|${c.stage}|${c.knobs}"

    for (pitch in pitches) {
        for (case in casesAt(pitch)) {
            if (case.kind != AliasingKind.MEASURED && case.kind != AliasingKind.CONTROL) {
                continue
            }

            val track = tracks.getOrPut(key(case)) { Track(case) }

            if (!track.failedAt.isNaN()) {
                continue
            }

            val reading = measureAliasing(samples = case.render(), nominalHz = pitch, config = config, findPitch = case.findPitch)
            val level = reading.nonHarmonicBelow5kReHarmonicsDb

            track.levels += pitch to level

            // NaN-guard: a level that is not a number (no harmonic power at all) is not healthy either.
            if (!(level < ALIASING_HEALTHY_DB)) {
                track.failedAt = pitch
            }
        }
    }

    return tracks.values.map { t ->
        AliasingLimitRow(
            family = t.first.family,
            stage = t.first.stage,
            knobs = t.first.knobs,
            kind = t.first.kind,
            note = t.first.note,
            levels = t.levels.toList(),
            firstUnhealthyHz = t.failedAt,
            sweepTopHz = t.levels.lastOrNull()?.first ?: Double.NaN,
        )
    }
}

private fun g(x: Double, digits: Int): String = when {
    x.isNaN() -> "-"
    x.isInfinite() -> if (x > 0) "inf" else "-inf"
    else -> String.format(Locale.ROOT, "%.${digits}f", x)
}

/** The limit table, one row per case: the limit, the verdict against 5 kHz, where it first failed and how loud. */
fun formatAliasingLimitTable(rows: List<AliasingLimitRow>): String {
    val header = listOf("family", "stage", "knobs", "healthy_to_Hz", "verdict", "first_bad_Hz", "at_dB", "pitches")
    val cells = rows.map { r ->
        val limit = if (r.clean) ">= ${g(r.healthyToHz, 0)}" else g(r.healthyToHz, 0)
        val verdict = if (r.healthyToHz >= ALIASING_HEALTHY_FROM_HZ) "healthy" else "below 5k"
        val bad = r.levels.lastOrNull()?.takeIf { !r.clean }

        listOf(r.family, r.stage, r.knobs, limit, verdict, g(r.firstUnhealthyHz, 0), g(bad?.second ?: Double.NaN, 1), r.levels.size.toString())
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

/** The limit rows as JSON, for the engine health page. Non-finite numbers are written as strings ([jsonRows]). */
fun formatAliasingLimitJson(rows: List<AliasingLimitRow>): String = jsonRows(
    rows.map { r ->
        linkedMapOf(
            "family" to r.family,
            "stage" to r.stage,
            "knobs" to r.knobs,
            "kind" to if (r.kind == AliasingKind.CONTROL) "control" else "measured",
            "note" to r.note,
            "healthyToHz" to r.healthyToHz,
            "clean" to r.clean,
            "firstUnhealthyHz" to r.firstUnhealthyHz,
            "sweepTopHz" to r.sweepTopHz,
            "thresholdDb" to ALIASING_HEALTHY_DB,
            "levels" to r.levels.map { listOf(it.first, it.second) },
        )
    },
)

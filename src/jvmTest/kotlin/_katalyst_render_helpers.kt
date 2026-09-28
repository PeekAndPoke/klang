/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang

import io.peekandpoke.klang.audio_engine.KlangOfflineRenderer
import io.peekandpoke.klang.sprudel.SprudelPattern
import kotlin.math.abs

/**
 * The three moves every small Katalyst render row makes: render a sprudel song offline, compare two
 * renders sample for sample, and read a render's peak so a row can prove it is asserting about
 * sound. Shared by [KatalystDoorFillRenderSpec] and [KatalystBodyNonFiniteWetSpec], which the
 * signal-flow plan's §12 gives different lifetimes (a migration fixture and a contract), so the
 * helpers live apart from both.
 *
 * The defaults are the frozen song's: 48 kHz, four cycles at 34.5 rpm.
 *
 * **This renderer has NO sample bank**, so every source here is a SYNTH. An `s("bd")` would render
 * silence and every comparison would pass on two silences.
 */
suspend fun renderSong(
    code: String,
    sampleRate: Int = 48_000,
    cycles: Int = 4,
    cyclesPerSecond: Double = 0.575,
    tailSec: Double = 0.5,
): List<ShortArray> {
    val pattern = SprudelPattern.compile(code) ?: error("the song did not compile: $code")
    val blocks = mutableListOf<ShortArray>()

    KlangOfflineRenderer(sampleRate = sampleRate).render(
        pattern = pattern,
        cycles = cycles,
        cyclesPerSecond = cyclesPerSecond,
        tailSec = tailSec,
        onBlock = { samples, count -> blocks.add(samples.copyOf(count)) },
    )

    return blocks
}

/**
 * The largest sample-to-sample difference between two renders, in 16-bit counts, so a failure says
 * HOW far apart they are: a dropped body is thousands of counts, a rounding difference one or two.
 * A caller asserts equal block COUNTS before it calls this; the per-block length is clamped anyway,
 * so a render that diverges in length is reported by the row that compares the counts, never as an
 * index crash from here.
 */
fun maxDiff(a: List<ShortArray>, b: List<ShortArray>): Int {
    var worst = 0

    for (block in 0 until minOf(a.size, b.size)) {
        val left = a[block]
        val right = b[block]

        for (i in 0 until minOf(left.size, right.size)) {
            val diff = abs(left[i].toInt() - right[i].toInt())

            if (diff > worst) {
                worst = diff
            }
        }
    }

    return worst
}

/** The loudest sample of a render, so a row can prove it is asserting about actual sound. */
fun peakOf(blocks: List<ShortArray>): Int {
    var peak = 0

    for (block in blocks) {
        for (i in block.indices) {
            val level = abs(block[i].toInt())

            if (level > peak) {
                peak = level
            }
        }
    }

    return peak
}

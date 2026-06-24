/*
 * Copyright (C) 2025-2026 The Klang Audio Motör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.sprudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.sprudel.SprudelPattern

/**
 * Guard for the reported "`late(0.002)` produces no events in the first 8 cycles" case.
 *
 * The `<…>` here expands each `!N` into separate alternation slots (see
 * `MnPatternToSprudelPattern.expandRepeat`), so it becomes **56 one-cycle slots**:
 * slots 0–7 (`[~!2]!2 [~!4]!2 [~!8]!2 [~!16] [~!24]` = 2+2+2+1+1) are all-rest groups, and the
 * `sd` hits only begin at slot 8 (`[~ sd ~ sd]!24`). So silence in cycles 0–7 is by design, and
 * there should be exactly two `sd` onsets per cycle from cycle 8 on.
 *
 * These tests replicate the live scheduling path exactly: they query cycle-by-cycle through the
 * `KlangPattern` bridge (`queryEvents`), which applies the same `isOnset` filter the engine uses.
 *
 * They also guard the real defect this case exposed: before [io.peekandpoke.klang.sprudel.pattern.TimeShiftPattern]
 * clipped shifted parts to the query window, `late()`'s non-cycle-aligned source queries re-emitted
 * a step straddling a cycle boundary as a full onset in BOTH adjacent cycles — duplicating the hit
 * (and producing a phantom from the wrap-around slot). Hence the count/time and wide-vs-per-cycle
 * assertions below.
 */
class LangLateAlternationSpec : StringSpec({

    val alt = "<[~!2]!2  [~!4]!2  [~!8]!2  [~!16]  [~!24]  [~  sd  ~ sd]!24 [~    sd    ~    sd]!24>"
    val cps = 0.5

    // Per-cycle onsets via the live KlangPattern bridge (same isOnset filter as the engine).
    fun SprudelPattern.onsetsInCycle(c: Int) =
        queryEvents(fromCycles = c.toDouble(), toCycles = (c + 1).toDouble(), cps = cps)

    "bare alternation: silent in cycles 0-7, two onsets per cycle from cycle 8" {
        val p = SprudelPattern.compile("""s("$alt")""")!!

        (0..7).forEach { c -> p.onsetsInCycle(c).size shouldBe 0 }
        (8..11).forEach { c -> p.onsetsInCycle(c).size shouldBe 2 }
    }

    "late(0.002): same per-cycle onset counts as no-late, times shifted by +0.002" {
        val bare = SprudelPattern.compile("""s("$alt")""")!!
        val late = SprudelPattern.compile("""s("$alt").late(0.002)""")!!

        (0..11).forEach { c ->
            val b = bare.onsetsInCycle(c).map { it.startCycles }.sorted()
            val l = late.onsetsInCycle(c).map { it.startCycles }.sorted()

            l.size shouldBe b.size
            b.forEachIndexed { i, bs ->
                (l[i] - bs) shouldBe (0.002 plusOrMinus 1e-6)
            }
        }
    }

    "wide query [0,12) agrees with the per-cycle union (late)" {
        val late = SprudelPattern.compile("""s("$alt").late(0.002)""")!!

        val wide = late.queryEvents(0.0, 12.0, cps).map { it.startCycles }.sorted()
        val perCycle = (0..11).flatMap { c -> late.onsetsInCycle(c).map { it.startCycles } }.sorted()

        wide.size shouldBe perCycle.size
        wide.forEachIndexed { i, w -> w shouldBe (perCycle[i] plusOrMinus 1e-9) }
    }

    "full reported chain: silent in cycles 0-7, onsets from cycle 8" {
        val full = SprudelPattern.compile(
            """sound("$alt").mute("<0!128 1!32>").late(0.002).orbit(6).gain(0.40).hpf(200).lpf(5200)""" +
                    """.adsr("0.005:0.1:0.7:0.2").superimpose(x => x.bandf(250).bandq(4).gain(0.20))"""
        )!!

        (0..7).forEach { c -> full.onsetsInCycle(c).size shouldBe 0 }
        // superimpose doubles each onset → at least the 2 sd hits, so a non-empty, even count.
        (8..11).forEach { c -> (full.onsetsInCycle(c).size >= 2) shouldBe true }
    }
})

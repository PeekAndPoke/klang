/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_bridge

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.kotest.matchers.types.shouldNotBeSameInstanceAs

/**
 * Chain-in / chain-out contract of the Katalyst chain's pass, [KatalystDsl.optimize] (KLANG-00468, cylinder amp step
 * 2): which `eq` runs merge, which are refused, in what order the sections land, the walk into nested chains, and the
 * same-instance rule. Every expected chain is WRITTEN OUT here from its data classes (the oracle is the rule's text:
 * `Eq(a.sections + b.sections)`), never through [KatalystDsl.optimize]. That the merged chain renders the same bits as the written
 * one is `audio_be`'s `KatalystDslOptimizerRenderSpec`, where an engine exists.
 */
class KatalystDslOptimizerSpec : StringSpec({

    fun c(value: Double): IgnitorDsl.Constant = IgnitorDsl.Constant(value)

    fun bell(freq: Double): IgnitorDsl.EqSection.Bell = IgnitorDsl.EqSection.Bell(freq = c(freq), q = c(0.9), db = c(3.0))

    fun lowpass(freq: Double): IgnitorDsl.EqSection.Lowpass = IgnitorDsl.EqSection.Lowpass(freq = c(freq), q = c(0.707))

    fun tap(freq: Double): IgnitorDsl.EqSection.RawTap = IgnitorDsl.EqSection.RawTap(freq = c(freq), q = c(1.2), gain = c(2.0))

    /** A bell whose freq is a slot, as `e.band(Kat.param(...))` writes it. */
    fun slotBell(): IgnitorDsl.EqSection.Bell =
        IgnitorDsl.EqSection.Bell(freq = IgnitorDsl.Param(name = "cab.freq", default = 800.0), q = c(0.9), db = c(3.0))

    fun eq(vararg sections: IgnitorDsl.EqSection): KatalystStageDsl.Eq = KatalystStageDsl.Eq(sections = sections.toList())

    fun chain(vararg stages: KatalystStageDsl): KatalystDsl = KatalystDsl.of(*stages)

    /** The pass returns [written] itself: nothing merged, nothing rebuilt. */
    fun leftAsWritten(clue: String, written: KatalystDsl) {
        withClue("$clue: left as written") {
            val optimized = written.optimize()

            optimized shouldBe written
            optimized shouldBeSameInstanceAs written
        }
    }

    // ── R1: what merges ─────────────────────────────────────────────────────────────────────────────────────────────

    "two adjacent eq stages of numbers become one stage, the sections in written order" {
        val written = chain(eq(bell(300.0), bell(900.0)), eq(lowpass(5000.0)))

        withClue("merged in order") {
            written.optimize() shouldBe chain(eq(bell(300.0), bell(900.0), lowpass(5000.0)))
        }
    }

    "five in a row become one stage" {
        val written = chain(eq(bell(100.0)), eq(bell(200.0)), eq(bell(300.0)), eq(bell(400.0)), eq(bell(500.0)))

        withClue("merged in order") {
            written.optimize() shouldBe chain(eq(bell(100.0), bell(200.0), bell(300.0), bell(400.0), bell(500.0)))
        }
    }

    "a cabinet as the chain doors write it (three bands, lowpass of three passes, highpass of two) is one stage of eight" {
        val written = Kat {
            it.eq { e -> e.band(freq = 120.kat, db = 2.kat).band(freq = 900.kat, db = (-3).kat).band(freq = 2500.kat, db = 4.kat) }
                .lowpass(freq = 5000.kat) { f -> f.passes(3.0) }
                .highpass(freq = 80.kat) { f -> f.passes(2.0) }
        }

        withClue("the doors write three stages") { written.stages.size shouldBe 3 }

        val sections = written.stages.flatMap { (it as KatalystStageDsl.Eq).sections }

        withClue("merged in order") {
            written.optimize() shouldBe KatalystDsl.of(KatalystStageDsl.Eq(sections = sections))
        }

        withClue("the eight sections, kind by kind") {
            (written.optimize().stages.single() as KatalystStageDsl.Eq).sections.map { it::class } shouldBe listOf(
                IgnitorDsl.EqSection.Bell::class, IgnitorDsl.EqSection.Bell::class, IgnitorDsl.EqSection.Bell::class,
                IgnitorDsl.EqSection.Lowpass::class, IgnitorDsl.EqSection.Lowpass::class, IgnitorDsl.EqSection.Lowpass::class,
                IgnitorDsl.EqSection.Highpass::class, IgnitorDsl.EqSection.Highpass::class,
            )
        }
    }

    // ── Walls ───────────────────────────────────────────────────────────────────────────────────────────────────────

    "any other stage between two eq stages is a wall: gain, distort, times, duck" {
        val walls = listOf(
            KatalystStageDsl.Gain(gain = KatalystKnobDsl.Constant(0.5)),
            KatalystStageDsl.Distort(amount = KatalystKnobDsl.Constant(0.3)),
            KatalystStageDsl.Times(other = KatalystKnobDsl.Constant(2.0)),
            KatalystStageDsl.Duck(cylinder = KatalystKnobDsl.Constant(1.0), depth = KatalystKnobDsl.Constant(0.5)),
        )

        for (wall in walls) {
            leftAsWritten(clue = "wall ${wall::class.simpleName}", written = chain(eq(bell(300.0)), wall, eq(lowpass(5000.0))))
        }
    }

    "a wall ends a run: the stages on each side of it merge among themselves, never across" {
        val gain = KatalystStageDsl.Gain(gain = KatalystKnobDsl.Constant(0.5))
        val written = chain(eq(bell(100.0)), eq(bell(200.0)), gain, eq(bell(300.0)), eq(bell(400.0)))

        withClue("wall: two runs") {
            written.optimize() shouldBe chain(eq(bell(100.0), bell(200.0)), gain, eq(bell(300.0), bell(400.0)))
        }
    }

    // ── The two refusals ────────────────────────────────────────────────────────────────────────────────────────────

    "a tap in the later stage is refused: it reads its own stage's input, which a merge would move" {
        leftAsWritten(clue = "tap in b", written = chain(eq(bell(300.0)), eq(tap(1200.0), lowpass(5000.0))))
    }

    "a tap in the earlier stage merges: it reads the same input before and after" {
        val written = chain(eq(tap(1200.0), bell(300.0)), eq(lowpass(5000.0)))

        withClue("tap in a: merged") {
            written.optimize() shouldBe chain(eq(tap(1200.0), bell(300.0), lowpass(5000.0)))
        }
    }

    "a refused tap starts a run of its own: the stage after it merges into the tap's stage" {
        val written = chain(eq(bell(300.0)), eq(tap(1200.0)), eq(lowpass(5000.0)))

        withClue("tap in b: a new run") {
            written.optimize() shouldBe chain(eq(bell(300.0)), eq(tap(1200.0), lowpass(5000.0)))
        }
    }

    "a slot knob in the earlier stage is refused" {
        leftAsWritten(clue = "slot in a", written = chain(eq(slotBell()), eq(lowpass(5000.0))))
    }

    "a slot knob in the later stage is refused" {
        leftAsWritten(clue = "slot in b", written = chain(eq(lowpass(5000.0)), eq(bell(300.0), slotBell())))
    }

    "every knob of every section kind is read: a slot in that one knob refuses the merge, in a and in b" {
        // The table of every (kind, knob) pair, each section holding a slot in exactly that knob and numbers in the rest.
        // Each runs once as stage `a` and once as stage `b`. A tap is judged by its slot as `a` only: as `b` a tap is
        // refused by clause 2 whatever its knobs hold, so that half could not tell a read knob from a skipped one.
        val slot = IgnitorDsl.Param(name = "cab.knob", default = 1.0)
        val table = listOf(
            "Lowpass.freq" to IgnitorDsl.EqSection.Lowpass(freq = slot, q = c(0.707)),
            "Lowpass.q" to IgnitorDsl.EqSection.Lowpass(freq = c(5000.0), q = slot),
            "Highpass.freq" to IgnitorDsl.EqSection.Highpass(freq = slot, q = c(0.707)),
            "Highpass.q" to IgnitorDsl.EqSection.Highpass(freq = c(80.0), q = slot),
            "Bandpass.freq" to IgnitorDsl.EqSection.Bandpass(freq = slot, q = c(1.0)),
            "Bandpass.q" to IgnitorDsl.EqSection.Bandpass(freq = c(900.0), q = slot),
            "Notch.freq" to IgnitorDsl.EqSection.Notch(freq = slot, q = c(1.0)),
            "Notch.q" to IgnitorDsl.EqSection.Notch(freq = c(3000.0), q = slot),
            "Bell.freq" to IgnitorDsl.EqSection.Bell(freq = slot, q = c(0.9), db = c(3.0)),
            "Bell.q" to IgnitorDsl.EqSection.Bell(freq = c(900.0), q = slot, db = c(3.0)),
            "Bell.db" to IgnitorDsl.EqSection.Bell(freq = c(900.0), q = c(0.9), db = slot),
            "RawTap.freq" to IgnitorDsl.EqSection.RawTap(freq = slot, q = c(1.2), gain = c(2.0)),
            "RawTap.q" to IgnitorDsl.EqSection.RawTap(freq = c(900.0), q = slot, gain = c(2.0)),
            "RawTap.gain" to IgnitorDsl.EqSection.RawTap(freq = c(900.0), q = c(1.2), gain = slot),
        )

        withClue("the table holds every knob of the six kinds: 4 x 2 + 2 x 3") { table.size shouldBe 14 }

        for ((knob, section) in table) {
            leftAsWritten(clue = "slot in a $knob", written = chain(eq(section), eq(lowpass(5000.0))))

            if (section !is IgnitorDsl.EqSection.RawTap) {
                leftAsWritten(clue = "slot in b $knob", written = chain(eq(lowpass(5000.0)), eq(bell(300.0), section)))
            }
        }
    }

    "a long run, 200 one-section stages, ends as one stage with its sections in written order" {
        val sections = List(200) { bell(100.0 + it) }
        val written = KatalystDsl(stages = sections.map { eq(it) })

        withClue("merged in order, long run") {
            written.optimize() shouldBe KatalystDsl.of(KatalystStageDsl.Eq(sections = sections))
        }
    }

    "a slot stage after a long run of numbers starts no merge, and the stage after it does not merge into it" {
        val numbers = List(50) { eq(bell(100.0 + it)) }
        val written = KatalystDsl(stages = numbers + eq(slotBell()) + eq(lowpass(5000.0)))

        withClue("slot after a run") {
            written.optimize() shouldBe KatalystDsl.of(
                KatalystStageDsl.Eq(sections = numbers.flatMap { it.sections }),
                eq(slotBell()),
                eq(lowpass(5000.0)),
            )
        }
    }

    // ── Chains inside chains ────────────────────────────────────────────────────────────────────────────────────────

    "an eq run inside a parallel branch is merged there; the other branch is the same instance" {
        val dry = chain(KatalystStageDsl.Gain(gain = KatalystKnobDsl.Constant(0.5)))
        val written = chain(KatalystStageDsl.Parallel(branches = listOf(chain(eq(bell(300.0)), eq(lowpass(5000.0))), dry)))
        val optimized = written.optimize()

        withClue("nested: merged in the branch") {
            optimized shouldBe chain(KatalystStageDsl.Parallel(branches = listOf(chain(eq(bell(300.0), lowpass(5000.0))), dry)))
        }

        withClue("the unchanged branch") {
            (optimized.stages.single() as KatalystStageDsl.Parallel).branches[1] shouldBeSameInstanceAs dry
        }
    }

    "an eq run inside an oversampleAtLeast body and inside an oversampleBy body is merged there" {
        val atLeast = chain(KatalystStageDsl.OversampleAtLeast(factor = 4, body = chain(eq(bell(300.0)), eq(lowpass(5000.0)))))
        val by = chain(KatalystStageDsl.OversampleBy(factor = 2, body = chain(eq(bell(300.0)), eq(lowpass(5000.0)))))

        withClue("nested: merged in the region") {
            atLeast.optimize() shouldBe
                    chain(KatalystStageDsl.OversampleAtLeast(factor = 4, body = chain(eq(bell(300.0), lowpass(5000.0)))))
            by.optimize() shouldBe chain(KatalystStageDsl.OversampleBy(factor = 2, body = chain(eq(bell(300.0), lowpass(5000.0)))))
        }
    }

    "an eq run inside a region inside a branch is merged there" {
        val region = { body: KatalystDsl -> KatalystStageDsl.OversampleAtLeast(factor = 4, body = body) }
        val dry = KatalystDsl(emptyList())
        val written = chain(KatalystStageDsl.Parallel(branches = listOf(dry, chain(region(chain(eq(bell(300.0)), eq(lowpass(5000.0))))))))

        withClue("nested: merged two levels down") {
            written.optimize() shouldBe
                    chain(KatalystStageDsl.Parallel(branches = listOf(dry, chain(region(chain(eq(bell(300.0), lowpass(5000.0))))))))
        }
    }

    "a region is a wall: an eq before it and the first eq of its body stay apart" {
        leftAsWritten(
            clue = "region wall",
            written = chain(eq(bell(300.0)), KatalystStageDsl.OversampleAtLeast(factor = 4, body = chain(eq(lowpass(5000.0))))),
        )
    }

    // ── The same instance ───────────────────────────────────────────────────────────────────────────────────────────

    "a chain with nothing to merge, nested chains included, comes back as the same instance" {
        leftAsWritten(
            clue = "nothing to merge",
            written = chain(
                eq(bell(300.0)),
                KatalystStageDsl.Distort(amount = KatalystKnobDsl.Constant(0.3)),
                KatalystStageDsl.Parallel(branches = listOf(chain(eq(lowpass(5000.0))), KatalystDsl(emptyList()))),
                KatalystStageDsl.OversampleBy(factor = 2, body = chain(eq(bell(300.0)), KatalystStageDsl.Abs, eq(bell(600.0)))),
            ),
        )
    }

    "KatalystDsl.classic holds no eq stage and comes back as the same instance" {
        leftAsWritten(clue = "classic", written = KatalystDsl.classic)
    }

    "an empty eq beside a full one merges; a lone empty eq stays where it is" {
        withClue("empty merged") {
            chain(eq(), eq(bell(300.0))).optimize() shouldBe chain(eq(bell(300.0)))
            chain(eq(bell(300.0)), eq()).optimize() shouldBe chain(eq(bell(300.0)))
        }

        leftAsWritten(clue = "lone empty", written = chain(KatalystStageDsl.Abs, eq(), KatalystStageDsl.Abs))
    }

    "the pass applied twice: the second run returns the first run's instance" {
        val written = chain(
            eq(slotBell()),
            eq(bell(300.0)),
            eq(lowpass(5000.0)),
            eq(tap(1200.0)),
            eq(bell(600.0)),
            KatalystStageDsl.Parallel(branches = listOf(chain(eq(bell(300.0)), eq(bell(400.0))), KatalystDsl(emptyList()))),
        )
        val once = written.optimize()

        withClue("the first run merged something") { once shouldNotBeSameInstanceAs written }
        withClue("idempotent") { once.optimize() shouldBeSameInstanceAs once }
    }

    // ── Identity ────────────────────────────────────────────────────────────────────────────────────────────────────

    "identity is the written chain's: equal written chains share one name before and after the pass, which changes neither" {
        fun cabinet() = Kat { it.eq { e -> e.band(freq = 900.kat, db = 3.kat) }.lowpass(freq = 5000.kat) { f -> f.passes(2.0) } }

        val first = cabinet()
        val second = cabinet()
        val nameBefore = first.uniqueId()

        first shouldNotBeSameInstanceAs second
        second.uniqueId() shouldBe nameBefore

        val firstOptimized = first.optimize()
        val secondOptimized = second.optimize()

        withClue("deterministic: equal written chains, equal optimized chains") {
            firstOptimized shouldBe secondOptimized
        }

        withClue("the written chain is untouched, so its name is too") {
            first shouldBe cabinet()
            first.uniqueId() shouldBe nameBefore
        }
    }
})

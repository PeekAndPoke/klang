/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.sprudel.lang.parser

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.ints.shouldBeInRange
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.peekandpoke.klang.sprudel.SprudelPattern
import io.peekandpoke.klang.sprudel.lang.note
import io.peekandpoke.klang.sprudel.lang.seed

/**
 * The `?` of the mini-notation: every mark has a random value of its own (the maintainer, 2026-11-24). A check for the
 * future: `[hh?!8]` once dropped all of its steps or none, as every mark read the same value.
 */
class MiniNotationQuestionMarkSpec : StringSpec() {

    private fun parse(input: String) = parseMiniNotation(input) { text, _ -> note(text) }

    /** How many events each of the first [cycles] cycles has. */
    private fun counts(pattern: SprudelPattern, cycles: Int = 64): List<Int> =
        (0 until cycles).map { cycle -> pattern.queryArc(cycle.toDouble(), cycle + 1.0).size }

    init {
        "each copy of a repeated step is dropped on its own: not all of them or none" {
            for (notation in listOf("[a?!8]", "a?!8", "a? a? a? a? a? a? a? a?", "[a? a?] [a? a?] <a? b?> a? a? a?")) {
                val each = counts(parse(notation))

                withClue("'$notation' gives $each") {
                    // most cycles keep some and drop some
                    each.count { it in 1..7 } shouldBeInRange 48..64
                    // and about half of all steps stay
                    each.sum() shouldBeInRange (64 * 8 * 2 / 5)..(64 * 8 * 3 / 5)
                }
            }
        }

        "in every single cycle the marks have their own values, the first cycle too" {
            // (the time's hash is 0 in cycle 0, and a power of two in cycle 2: a mark's number multiplied with it
            // would be the same for all marks there, or for every fourth)
            for (cycle in listOf(0, 1, 2, 3, 4, 8, 16)) {
                val each = (0 until 40).map { seed -> parse("[a?!8]").seed(seed).queryArc(cycle.toDouble(), cycle + 1.0).size }

                withClue("cycle $cycle over 40 seeds gives $each") {
                    each.count { it in 1..7 } shouldBeInRange 30..40
                    // odd counts happen too: the marks are not all tied in pairs
                    each.count { it % 2 == 1 } shouldBeInRange 10..30
                }
            }
        }

        "two marks side by side do not go together" {
            val pattern = parse("a? b?")
            val pairs = (0 until 64).map { cycle -> pattern.queryArc(cycle.toDouble(), cycle + 1.0).map { it.data.note?.lowercase() } }

            // each of the four outcomes happens: both, the one, the other, none
            pairs.toSet().size shouldBe 4
        }

        "the same notation gives the same events again, and under the same seed; another seed gives others" {
            counts(parse("a?!8")) shouldBe counts(parse("a?!8"))
            counts(parse("a?!8").seed(7)) shouldBe counts(parse("a?!8").seed(7))
            counts(parse("a?!8").seed(7)) shouldNotBe counts(parse("a?!8").seed(8))

            // and no small step of the seed is a step in time: seed 31 is not seed 0 a cycle on (it was, in the
            // browser, while the time's hash was spread by 31)
            for (step in listOf(1, 2, 31, 32)) {
                counts(parse("a?!8").seed(step), cycles = 65).dropLast(1) shouldNotBe counts(parse("a?!8").seed(0), cycles = 65).drop(1)
            }
        }

        "the number is the probability of being played: never at 0, always at 1, about a fifth at 0.2, half without one" {
            counts(parse("a?0!8")).sum() shouldBe 0
            counts(parse("a?1!8")).sum() shouldBe 64 * 8
            counts(parse("a?0.2!8")).sum() shouldBeInRange (64 * 8 * 1 / 10)..(64 * 8 * 3 / 10)
            counts(parse("a?0.8!8")).sum() shouldBeInRange (64 * 8 * 7 / 10)..(64 * 8 * 9 / 10)
            counts(parse("a?!8")).sum() shouldBe counts(parse("a?0.5!8")).sum()
        }

        "a number after a space is the next step, not the mark's number" {
            // ("a? 1" was one step that always played, the 1 taken as its probability)
            val both = (0 until 64).map { cycle -> parse("a? 1").queryArc(cycle.toDouble(), cycle + 1.0).map { it.data.note?.lowercase() } }

            both.toSet() shouldBe setOf(listOf("a", "1"), listOf("1"))
        }

        "a step made faster still drops each of its hits on its own" {
            counts(parse("a?*8")).count { it in 1..7 } shouldBeInRange 48..64
        }
    }
}

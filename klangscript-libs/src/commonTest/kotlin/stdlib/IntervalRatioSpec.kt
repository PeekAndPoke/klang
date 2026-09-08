/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.script.stdlib

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.tones.interval.Interval

class IntervalRatioSpec : StringSpec({

    // Every expected value is 2^(n/12) computed from the interval's size in semitones by hand,
    // the size being the one music theory gives it, not one read back from the parser.

    "named intervals convert to their frequency ratio" {
        listOf(
            "P1" to 1.0,         // 0 semitones, unison
            "m3" to 1.1892,      // 3 semitones, a minor third
            "M3" to 1.2599,      // 4 semitones, a major third
            "P4" to 1.3348,      // 5 semitones, a perfect fourth
            "A4" to 1.4142,      // 6 semitones, the tritone
            "P5" to 1.4983,      // 7 semitones, a perfect fifth
            "m7" to 1.7818,      // 10 semitones, a minor seventh
            "P8" to 2.0,         // 12 semitones, an octave
            "M9" to 2.2449,      // 14 semitones, an octave and a major second
        ).forEach { (name, expected) ->
            withClue(name) {
                Interval.get(name).ratio shouldBe (expected plusOrMinus 1e-4)
            }
        }
    }

    "descending intervals convert to a ratio below one" {
        listOf(
            "-2m" to 0.9439,     // -1 semitone, a minor second down
            "-5P" to 0.6674,     // -7 semitones, a fifth down
            "P-5" to 0.6674,     // the shorthand spelling of the same interval
            "-8P" to 0.5,        // -12 semitones, an octave down
        ).forEach { (name, expected) ->
            withClue(name) {
                Interval.get(name).ratio shouldBe (expected plusOrMinus 1e-4)
            }
        }
    }

    "the unison and the octaves are exact" {
        Interval.get("P1").ratio shouldBe 1.0
        Interval.get("P8").ratio shouldBe 2.0
        Interval.get("-8P").ratio shouldBe 0.5
    }

    "an interval and its inversion within the octave multiply to two" {
        // A fifth up plus a fourth up is an octave, so their ratios multiply to 2.
        (Interval.get("P5").ratio * Interval.get("P4").ratio) shouldBe (2.0 plusOrMinus 1e-9)
        (Interval.get("M3").ratio * Interval.get("m6").ratio) shouldBe (2.0 plusOrMinus 1e-9)
    }

    "an interval and the same interval down multiply to one" {
        (Interval.get("P5").ratio * Interval.get("-5P").ratio) shouldBe (1.0 plusOrMinus 1e-9)
        (Interval.get("M3").ratio * Interval.get("-3M").ratio) shouldBe (1.0 plusOrMinus 1e-9)
    }

    "an unparseable name is empty and its ratio is meaningless" {
        // Callers must check empty first, which is why the script door reports the bad name.
        // The minus of a descending interval goes in front of the NUMBER: "-5P" or "P-5",
        // never "-P5", so that spelling is unparseable too.
        listOf("xyz", "", "P0x", "-P5").forEach { name ->
            withClue(name) {
                Interval.get(name).empty shouldBe true
                // semitones is Int.MIN_VALUE there, so 2^(semitones / 12) underflows to zero.
                Interval.get(name).ratio shouldBe 0.0
            }
        }
    }
})

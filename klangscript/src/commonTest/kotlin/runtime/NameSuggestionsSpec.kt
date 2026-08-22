/*
 * Copyright (C) 2025-2026 The Klangmotör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.script.runtime

import io.kotest.core.spec.style.StringSpec
import io.peekandpoke.klang.common.strings.osaDistance
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain

class NameSuggestionsSpec : StringSpec({

    val patternish = listOf("oscp", "oscparam", "gain", "lpf", "lpq", "rev", "note", "sound")

    "suggests the transposed neighbour — the case this exists for" {
        // `.ocsp` for `.oscp` cost a full debugging session. Plain Levenshtein scores it 2 and
        // would be filtered out at the 4-letter threshold of 1, so this row also guards the
        // choice of osaDistance over levenshtein.
        suggestNames("ocsp", patternish) shouldContain "'oscp'"
    }

    "suggests for the ordinary typos too" {
        suggestNames("lpff", patternish) shouldContain "'lpf'"
        suggestNames("gian", patternish) shouldContain "'gain'"
        suggestNames("sund", patternish) shouldContain "'sound'"
    }

    "stays SILENT when nothing is close" {
        // A wrong suggestion is worse than none: it sends the reader off to check a name that
        // was never the problem.
        suggestNames("zzzzqqq", patternish) shouldBe ""
        suggestNames("completelyunrelated", patternish) shouldBe ""
    }

    "the threshold scales with the typed name's length" {
        // Measured, because the first version of this row passed for the wrong reason: it used
        // pairs whose real distances were 1 and 4, so it never exercised the scaling at all and
        // stayed green with the whole `when` replaced by a constant.
        //
        // These pairs are exactly 2 edits apart, which is the only distance that discriminates:
        // rejected on a short name, accepted on a medium one.
        "abcd".osaDistance("abxy") shouldBe 2
        "gaindd".osaDistance("gainxx") shouldBe 2

        suggestNames("abcd", listOf("abxy")) shouldBe ""                     // <=4 chars -> max 1
        suggestNames("gaindd", listOf("gainxx")) shouldContain "'gainxx'"    // 5-8 chars -> max 2
    }

    "a 3-edit match is only offered on a long name" {
        "abcdefghi".osaDistance("abcdefxyz") shouldBe 3

        suggestNames("gaindd", listOf("gaixxx")) shouldBe ""                 // 5-8 chars -> max 2
        suggestNames("abcdefghi", listOf("abcdefxyz")) shouldContain "'abcdefxyz'"
    }

    "ranks NEAREST first, not merely alphabetically" {
        // The previous version used three candidates all at distance 1 and length 3, so only the
        // alphabetical tiebreak fired and deleting the distance comparator left it green. This
        // is the property that makes `oscp` beat `oscparam`.
        // Both candidates must be INSIDE the threshold, at DIFFERENT distances — otherwise the
        // filter does the work and the ordering is never exercised. ("gian" vs "grain" cannot
        // show this: that pair is 2 edits and a 4-char name only admits 1.)
        // The nearer candidate is also the LONGER one, so distance-ordering and the
        // length tiebreak disagree. Without that, the row cannot see the distance comparator
        // at all — a same-length pair sorts identically either way, which is how the previous
        // version of this row stayed green with the comparator deleted.
        "gaindd".osaDistance("gainddx") shouldBe 1
        "gaindd".osaDistance("gainxx") shouldBe 2

        suggestNames("gaindd", listOf("gainxx", "gainddx")) shouldContain "'gainddx' or 'gainxx'"
    }

    "the order does not depend on the caller's iteration order" {
        val a = suggestNames("lpx", listOf("lpf", "lpq", "lpa"))
        val b = suggestNames("lpx", listOf("lpa", "lpq", "lpf"))
        a shouldBe b
    }

    "offers at most three, so the message stays readable" {
        val many = listOf("lpa", "lpb", "lpc", "lpd", "lpe", "lpf")
        suggestNames("lpx", many).split(" or ").size shouldBe 3
    }

    "handles the degenerate inputs without throwing" {
        suggestNames("", patternish) shouldBe ""
        suggestNames("gain", emptyList()) shouldBe ""
    }

    "the available-names list is truncated, and says so" {
        val many = (1..200).map { "name$it" }
        val formatted = formatAvailableNames(many)
        formatted shouldContain "200 total"
        formatted shouldContain "showing 40"
        // The whole point: a 4 KB unranked dump is what buried the answer last time. Count the
        // NAMES (after the "...:" header, which contains a comma of its own).
        val names = formatted.substringAfter("): ").split(", ")
        names.size shouldBe 41 // 40 names + the trailing "..."
        names.last() shouldBe "..."
    }

    "a short list is shown whole, with no truncation notice" {
        val formatted = formatAvailableNames(listOf("gain", "lpf"))
        formatted shouldContain "gain"
        formatted shouldNotContain "total"
    }

    "an empty list contributes nothing" {
        formatAvailableNames(emptyList()) shouldBe ""
    }
})

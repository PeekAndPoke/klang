/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.pages.docs.lexikon.LexikonCategory
import io.peekandpoke.klang.pages.docs.lexikon.LexikonEntry
import io.peekandpoke.klang.pages.docs.lexikon.allLexikonEntries
import io.peekandpoke.klang.pages.docs.lexikon.lexikonAutoLinks

/** A small Lexikon of its own: the rules hold whatever the real entries say. */
private fun entry(term: String, vararg words: String) = LexikonEntry(
    term = term,
    category = LexikonCategory.MotorTerms,
    tags = emptySet(),
    summary = "",
    detail = "",
    linkWords = words.toList(),
)

private val testLexikon = listOf(
    entry("Cylinder", "cylinder", "cylinders"),
    entry("Cylinder bus", "per cylinder bus", "cylinder bus", "per cylinder"),
    entry("Voice", "per voice", "voice", "voices"),
    entry("Mapping"),
)

/** The Lexikon links itself (KLANG-00369): each entry's words, the first time, never where a link or code is. */
class LexikonAutoLinksSpec : StringSpec({

    fun link(text: String, self: String? = null) = lexikonAutoLinks(markdown = text, entries = testLexikon, self = self)

    "an entry's word links to it, the first time only, in the case the text writes it" {
        link("Each Voice has its voice, and voices.") shouldBe "Each [Voice](/manuals/lexikon/voice) has its voice, and voices."
    }

    "the longest word wins: cylinder bus before cylinder, per cylinder to the bus" {
        link("The cylinder bus of a cylinder.") shouldBe
            "The [cylinder bus](/manuals/lexikon/cylinder-bus) of a [cylinder](/manuals/lexikon/cylinder)."
        link("Set once per cylinder.") shouldBe "Set once [per cylinder](/manuals/lexikon/cylinder-bus)."
    }

    "whole words only: a word inside another word, a name or a path stays" {
        link("cylindrical and voiceless and myCylinder and voice-over") shouldBe "cylindrical and voiceless and myCylinder and voice-over"
    }

    "never inside code, a link, a link's text or a URL" {
        link("`cylinder` and ```\ncylinder\n``` and [the cylinder](https://x.org) and https://cylinder.example and <b>cylinder</b>") shouldBe
            "`cylinder` and ```\ncylinder\n``` and [the cylinder](https://x.org) and https://cylinder.example and " +
            "<b>[cylinder](/manuals/lexikon/cylinder)</b>"
    }

    "a KDoc reference names a function, not the concept: it stays as it is" {
        link("Alias for [cylinder], see [SprudelPattern.cylinder].") shouldBe "Alias for [cylinder], see [SprudelPattern.cylinder]."
    }

    "a phrase that starts earlier and runs longer wins: per cylinder bus links whole" {
        link("One compressor per cylinder bus.") shouldBe "One compressor [per cylinder bus](/manuals/lexikon/cylinder-bus)."
    }

    "an entry a hand-written link points to is not linked again" {
        link("[per voice](/manuals/lexikon/voice) and a voice") shouldBe "[per voice](/manuals/lexikon/voice) and a voice"
    }

    "an entry without words links nowhere; an entry's own text never links to itself" {
        link("Mapping maps.") shouldBe "Mapping maps."
        link("A cylinder holds voices.", self = "cylinder") shouldBe "A cylinder holds [voices](/manuals/lexikon/voice)."
    }

    "a long text goes through: a long link text, or a bracket never closed, runs no stack out" {
        val run = "x ".repeat(20_000)
        val long = "[$run](/x) and [an open bracket $run a cylinder"

        lexikonAutoLinks(markdown = long, entries = testLexikon).length shouldBe long.length + "[](/manuals/lexikon/cylinder)".length
    }

    "the real Lexikon: a unison's voices stay plain, per voice links; per cylinder bus links whole" {
        // A bare "voices" in the docs is often a unison's or a super oscillator's copies inside one note (review, KLANG-00369)
        lexikonAutoLinks("Stacks detuned voices, set per voice. One delay per cylinder bus.") shouldBe
            "Stacks detuned voices, set [per voice](/manuals/lexikon/voice). One delay [per cylinder bus](/manuals/lexikon/cylinder-bus)."
    }

    "an unclosed bracket swallows nothing: the words after it still link" {
        link("A range [0, 1) on the cylinder bus, see [x].") shouldBe "A range [0, 1) on the [cylinder bus](/manuals/lexikon/cylinder-bus), see [x]."
    }

    "the real Lexikon's words are all unique, so a word cannot mean two entries" {
        val words = allLexikonEntries.flatMap { e -> e.linkWords.map { it.lowercase() } }

        words.groupBy { it }.filterValues { it.size > 1 }.keys shouldBe emptySet()
    }
})

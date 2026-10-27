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
    entry("Orbit", "orbit", "orbits"),
    entry("Orbit bus", "per orbit bus", "orbit bus", "per orbit"),
    entry("Voice", "per voice", "voice", "voices"),
    entry("Mapping"),
)

/** The Lexikon links itself (KLANG-00369): each entry's words, the first time, never where a link or code is. */
class LexikonAutoLinksSpec : StringSpec({

    fun link(text: String, self: String? = null) = lexikonAutoLinks(markdown = text, entries = testLexikon, self = self)

    "an entry's word links to it, the first time only, in the case the text writes it" {
        link("Each Voice has its voice, and voices.") shouldBe "Each [Voice](/manuals/lexikon/voice) has its voice, and voices."
    }

    "the longest word wins: orbit bus before orbit, per orbit to the bus" {
        link("The orbit bus of an orbit.") shouldBe
            "The [orbit bus](/manuals/lexikon/orbit-bus) of an [orbit](/manuals/lexikon/orbit)."
        link("Set once per orbit.") shouldBe "Set once [per orbit](/manuals/lexikon/orbit-bus)."
    }

    "whole words only: a word inside another word, a name or a path stays" {
        link("orbital and voiceless and myOrbit and voice-over") shouldBe "orbital and voiceless and myOrbit and voice-over"
    }

    "never inside code, a link, a link's text or a URL" {
        link("`orbit` and ```\norbit\n``` and [the orbit](https://x.org) and https://orbit.example and <b>orbit</b>") shouldBe
            "`orbit` and ```\norbit\n``` and [the orbit](https://x.org) and https://orbit.example and " +
            "<b>[orbit](/manuals/lexikon/orbit)</b>"
    }

    "a KDoc reference names a function, not the concept: it stays as it is" {
        link("Alias for [orbit], see [SprudelPattern.orbit].") shouldBe "Alias for [orbit], see [SprudelPattern.orbit]."
    }

    "a phrase that starts earlier and runs longer wins: per orbit bus links whole" {
        link("One compressor per orbit bus.") shouldBe "One compressor [per orbit bus](/manuals/lexikon/orbit-bus)."
    }

    "an entry a hand-written link points to is not linked again" {
        link("[per voice](/manuals/lexikon/voice) and a voice") shouldBe "[per voice](/manuals/lexikon/voice) and a voice"
    }

    "an entry without words links nowhere; an entry's own text never links to itself" {
        link("Mapping maps.") shouldBe "Mapping maps."
        link("An orbit holds voices.", self = "orbit") shouldBe "An orbit holds [voices](/manuals/lexikon/voice)."
    }

    "a long text goes through: a long link text, or a bracket never closed, runs no stack out" {
        val run = "x ".repeat(20_000)
        val long = "[$run](/x) and [an open bracket $run an orbit"

        lexikonAutoLinks(markdown = long, entries = testLexikon).length shouldBe long.length + "[](/manuals/lexikon/orbit)".length
    }

    "the real Lexikon: a unison's voices stay plain, per voice links; per orbit bus links whole" {
        // A bare "voices" in the docs is often a unison's or a super oscillator's copies inside one note (review, KLANG-00369)
        lexikonAutoLinks("Stacks detuned voices, set per voice. One delay per orbit bus.") shouldBe
            "Stacks detuned voices, set [per voice](/manuals/lexikon/voice). One delay [per orbit bus](/manuals/lexikon/orbit-bus)."
    }

    "an unclosed bracket swallows nothing: the words after it still link" {
        link("A range [0, 1) on the orbit bus, see [x].") shouldBe "A range [0, 1) on the [orbit bus](/manuals/lexikon/orbit-bus), see [x]."
    }

    "the real Lexikon's words are all unique, so a word cannot mean two entries" {
        val words = allLexikonEntries.flatMap { e -> e.linkWords.map { it.lowercase() } }

        words.groupBy { it }.filterValues { it.size > 1 }.keys shouldBe emptySet()
    }
})

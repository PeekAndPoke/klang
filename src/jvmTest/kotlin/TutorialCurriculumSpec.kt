/*
 * Copyright (C) 2025-2026 The Klangmotör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.pages.docs.tutorials.Tutorial
import io.peekandpoke.klang.pages.docs.tutorials.allTracks
import io.peekandpoke.klang.pages.docs.tutorials.allTutorials
import io.peekandpoke.klang.pages.docs.tutorials.theKlangPathTrack
import io.peekandpoke.klang.script.klangScript
import io.peekandpoke.klang.script.stdlibLib
import io.peekandpoke.klang.sprudel.SprudelPattern
import io.peekandpoke.klang.sprudel.lang.sprudelLib

/**
 * Guards the tutorial curriculum rules from docs/tasks/tutorial-curriculum.md:
 *
 * 1. Every code block must compile — a tutorial example that errors destroys learner trust.
 * 2. Vocabulary: a lesson may only use functions and mini-notation symbols that an EARLIER
 *    lesson (or the lesson itself) lists in `teaches`, or that it declares in `previews`.
 *    The registry order in TutorialRegistry.kt IS the curriculum order.
 * 3. Declared previews must actually be used — stale preview entries rot.
 * 4. Every lesson needs at least one directed-listening moment ("Listen for ...").
 *
 * Mini-notation symbols use these canonical names in `teaches`/`previews`:
 * "~" rest, "[]" group, "<>" alternation, "*" fast, "!" replicate, "@" weight,
 * "," stack, "|" choice, "?" degrade, "(n,k)" euclid.
 */
class TutorialCurriculumSpec : StringSpec({

    fun engine() = klangScript {
        registerLibrary(stdlibLib)
        registerLibrary(sprudelLib)
    }

    // The playable example component auto-imports both libraries; mirror that here.
    fun compileBlock(code: String): SprudelPattern? = SprudelPattern.compile(
        engine(),
        "import * from \"stdlib\"\nimport * from \"sprudel\"\n" + code,
    )

    "every tutorial code block compiles to a SprudelPattern" {
        for (tutorial in allTutorials) {
            for ((index, section) in tutorial.sections.withIndex()) {
                val code = section.code ?: continue
                withClue("${tutorial.slug} section #$index (${section.heading}):\n$code") {
                    compileBlock(code).shouldNotBeNull()
                }
            }
        }
    }

    "vocabulary: no lesson uses functions or notation before they are taught" {
        val violations = mutableListOf<String>()
        val taught = mutableSetOf<String>()

        for (tutorial in allTutorials) {
            taught.addAll(tutorial.teaches)
            val allowed = taught + tutorial.previews + STRUCTURAL_KEYWORDS

            for ((index, section) in tutorial.sections.withIndex()) {
                val code = section.code ?: continue
                for (word in usedVocabulary(code)) {
                    if (word !in allowed) {
                        violations.add(
                            "${tutorial.slug} section #$index (${section.heading}) uses '$word' " +
                                "before it is taught — teach it earlier, or declare it in previews " +
                                "and call it out as a preview in the text"
                        )
                    }
                }
            }
        }

        violations.shouldBeEmpty()
    }

    "every declared preview is actually used in that lesson's code" {
        val violations = mutableListOf<String>()

        for (tutorial in allTutorials) {
            val used = tutorial.sections.mapNotNull { it.code }.flatMap { usedVocabulary(it) }.toSet()
            for (preview in tutorial.previews) {
                if (preview !in used) {
                    violations.add("${tutorial.slug} declares unused preview '$preview'")
                }
            }
        }

        violations.shouldBeEmpty()
    }

    "every lesson has at least one 'Listen for' moment" {
        for (tutorial in allTutorials) {
            withClue("${tutorial.slug} has no directed-listening moment ('Listen for ...') in any section") {
                tutorial.sections.any { it.text.contains("listen for", ignoreCase = true) } shouldBe true
            }
        }
    }

    "callout labels open their own paragraph ('Try it:' / 'Listen for:' never mid-paragraph)" {
        val violations = mutableListOf<String>()

        for (tutorial in allTutorials) {
            for (section in tutorial.sections) {
                for (paragraph in section.text.split("\n\n")) {
                    for (label in listOf("Try it:", "Listen for:")) {
                        if (paragraph.indexOf(label) > 0) {
                            violations.add(
                                "${tutorial.slug} (${section.heading}): '$label' is buried " +
                                    "mid-paragraph — give it its own \\n\\n paragraph: " +
                                    paragraph.take(60)
                            )
                        }
                    }
                }
            }
        }

        violations.shouldBeEmpty()
    }

    "every code block sets an explicit gain (loudness rule — never rely on the default)" {
        val violations = mutableListOf<String>()

        for (tutorial in allTutorials) {
            for (section in tutorial.sections) {
                val code = section.code ?: continue
                if (!code.contains(".gain(")) {
                    violations.add(
                        "${tutorial.slug} (${section.heading}): code block has no explicit .gain(...) — " +
                            "the default gain 1.0 breaks cross-tutorial loudness parity"
                    )
                }
            }
        }

        violations.shouldBeEmpty()
    }

    "no reading-order references — lessons are browsed freely, cross-references go by name" {
        val forbidden = Regex("""\b(last|next|previous|earlier) +(\w+ +)?lessons?\b""", RegexOption.IGNORE_CASE)
        val violations = mutableListOf<String>()

        for (tutorial in allTutorials) {
            for (section in tutorial.sections) {
                for (content in listOfNotNull(section.text, section.code)) {
                    forbidden.find(content)?.let { match ->
                        violations.add(
                            "${tutorial.slug} (${section.heading}): '${match.value}' assumes a reading " +
                                "order — reference the lesson by name via the Tut constants instead"
                        )
                    }
                }
            }
        }

        violations.shouldBeEmpty()
    }

    "long pattern strings split into halves with a double space (readability rule)" {
        val violations = mutableListOf<String>()

        for (tutorial in allTutorials) {
            for (section in tutorial.sections) {
                val code = section.code ?: continue
                for (literal in STRING_REGEX.findAll(code).map { it.value.trim('"') }) {
                    val tokens = topLevelTokens(literal)
                    if (tokens.size > 4 && tokens.size % 2 == 0) {
                        val halves = literal.split("  ")
                        val ok = halves.size == 2 &&
                            topLevelTokens(halves[0]).size == tokens.size / 2
                        if (!ok) {
                            violations.add(
                                "${tutorial.slug} (${section.heading}): \"$literal\" has " +
                                    "${tokens.size} events — split it into halves with a double space"
                            )
                        }
                    }
                }
            }
        }

        violations.shouldBeEmpty()
    }

    "tutorial slugs are unique" {
        val slugs = allTutorials.map(Tutorial::slug)
        slugs.toSet().size shouldBe slugs.size
    }

    "track slugs are unique and every lesson is on the main path" {
        val trackSlugs = allTracks.map { it.slug }
        trackSlugs.toSet().size shouldBe trackSlugs.size

        val onMainPath = theKlangPathTrack.lessons.toSet()
        val strays = allTracks.flatMap { it.lessons }.filter { it !in onMainPath }
        withClue("every lesson must also be on The Klang Path (it derives the flat list and the global lint)") {
            strays.map { it.slug }.shouldBeEmpty()
        }
    }

    "per-track vocabulary: every track is self-consistent given what it builds on" {
        val violations = mutableListOf<String>()

        for (track in allTracks) {
            val baseline = track.buildsOn
                .flatMap { it.lessons }
                .flatMap { it.teaches }
                .toMutableSet()

            for (lesson in track.lessons) {
                baseline.addAll(lesson.teaches)
                val allowed = baseline + lesson.previews + STRUCTURAL_KEYWORDS

                for (section in lesson.sections) {
                    val code = section.code ?: continue
                    for (word in usedVocabulary(code)) {
                        if (word !in allowed) {
                            violations.add(
                                "track '${track.slug}', lesson '${lesson.slug}' " +
                                    "(${section.heading}) uses '$word' that neither this track nor " +
                                    "its buildsOn tracks teach by that point"
                            )
                        }
                    }
                }
            }
        }

        violations.shouldBeEmpty()
    }
})

/** Keywords the call-scan matches that are not vocabulary. */
private val STRUCTURAL_KEYWORDS = setOf("if", "while", "for")

private val CALL_REGEX = Regex("""([a-zA-Z_][A-Za-z0-9_]*)\s*\(""")
private val STRING_REGEX = Regex(""""([^"\\]|\\.)*"""")

/** Canonical vocabulary names for mini-notation symbols found inside pattern strings. */
private val NOTATION_SYMBOLS = mapOf(
    '~' to "~",
    '[' to "[]",
    '<' to "<>",
    '*' to "*",
    '!' to "!",
    '@' to "@",
    ',' to ",",
    '|' to "|",
    '?' to "?",
    '(' to "(n,k)",
)

/**
 * Splits a mini-notation string into top-level tokens: whitespace separates tokens, but
 * anything inside `[]`/`<>`/`{}`/`()` counts as part of one token.
 */
private fun topLevelTokens(s: String): List<String> {
    val tokens = mutableListOf<String>()
    val current = StringBuilder()
    var depth = 0
    for (ch in s) {
        when {
            ch in "[<{(" -> { depth++; current.append(ch) }
            ch in "]>})" -> { depth--; current.append(ch) }
            ch == ' ' && depth == 0 -> {
                if (current.isNotEmpty()) {
                    tokens.add(current.toString())
                    current.clear()
                }
            }
            else -> current.append(ch)
        }
    }
    if (current.isNotEmpty()) tokens.add(current.toString())
    return tokens
}

/**
 * Extracts the vocabulary a code block uses: called function names (scanned outside string
 * literals, so `"bd(3,8)"` is not mistaken for a call) plus mini-notation symbols (scanned
 * inside string literals only). Commented-out code is scanned too, deliberately — A/B blocks
 * ask the learner to uncomment those lines.
 */
private fun usedVocabulary(code: String): Set<String> {
    val strings = STRING_REGEX.findAll(code).map { it.value }.toList()
    val codeOnly = STRING_REGEX.replace(code, "\"\"")

    val calls = CALL_REGEX.findAll(codeOnly).map { it.groupValues[1] }
    val symbols = strings.asSequence().flatMap { it.asSequence() }.mapNotNull { NOTATION_SYMBOLS[it] }

    return (calls + symbols).toSet()
}

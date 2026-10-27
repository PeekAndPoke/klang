/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.pages.docs.tutorials.Block
import io.peekandpoke.klang.pages.docs.tutorials.Tutorial
import io.peekandpoke.klang.pages.docs.tutorials.TutorialSection
import io.peekandpoke.klang.pages.docs.tutorials.allTracks
import io.peekandpoke.klang.pages.docs.tutorials.allTutorials
import io.peekandpoke.klang.pages.docs.tutorials.codeOnly
import io.peekandpoke.klang.pages.docs.tutorials.soundDesignBasicsTrack
import io.peekandpoke.klang.pages.docs.tutorials.theKlangPathTrack
import io.peekandpoke.klang.script.klangScript
import io.peekandpoke.klang.script.stdlibLib
import io.peekandpoke.klang.sprudel.SprudelPattern
import io.peekandpoke.klang.sprudel.lang.sprudelLib

/**
 * Guards the tutorial curriculum rules from KLANG-00145 (tutorial curriculum):
 *
 * 1. Every KlangScript code block must compile — a broken example destroys learner trust.
 * 2. Vocabulary: a lesson may only use functions and mini-notation symbols that an EARLIER
 *    lesson (or the lesson itself) lists in `teaches`, or that it declares in `previews`.
 *    The main track's order in TutorialRegistry.kt IS the curriculum order; every track must
 *    additionally be self-consistent given what it builds on.
 * 3. Declared previews must actually be used — stale preview entries rot.
 * 4. Every lesson needs at least one directed-listening moment ("Listen for ...").
 * 5. Callout labels ("Try it:" / "Listen for:") open their own paragraph.
 * 6. No reading-order references — lessons are browsed freely; cross-references go by name.
 * 7. Every code block sets an explicit gain (loudness rule).
 * 8. Long pattern strings split into halves with a double space (readability rule).
 * 9. Visuals parse, and their parameter values appear verbatim in the same section's code —
 *    picture and code cannot drift.
 * 10. Player-button names are bold in prose ("press **Update**") — the reader has to find that
 *    button in the chrome, and B1's player tour introduces all of them in bold.
 * 11. Inside one code block, every line comment starts at the same column — the comments are a
 *    narration column, and a ragged one reads as sloppy code.
 * 12. An alternative is a player of its own, never a line behind `//`; each layer of a stack can
 *    be put aside with `//` on every line of it, and what is left still compiles.
 * 13. An ADSR picture follows the players right after it and draws what the first of them writes;
 *    only where it writes its envelope, and in Sound Design Basics wherever one does (KLANG-00371).
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
                for (code in section.klangScriptBlocks()) {
                    withClue("${tutorial.slug} section #$index (${section.heading}):\n$code") {
                        compileBlock(code).shouldNotBeNull()
                    }
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
                for (code in section.klangScriptBlocks()) {
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
        }

        violations.shouldBeEmpty()
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
                    for (code in section.klangScriptBlocks()) {
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
        }

        violations.shouldBeEmpty()
    }

    "every declared preview is actually used in that lesson's code" {
        val violations = mutableListOf<String>()

        for (tutorial in allTutorials) {
            val used = tutorial.sections
                .flatMap { it.klangScriptBlocks() }
                .flatMap { usedVocabulary(it) }
                .toSet()
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
                tutorial.sections
                    .flatMap { it.textBlocks() }
                    .any { it.contains("listen for", ignoreCase = true) } shouldBe true
            }
        }
    }

    "callout labels open their own paragraph ('Try it:' / 'Listen for:' never mid-paragraph)" {
        val violations = mutableListOf<String>()

        for (tutorial in allTutorials) {
            for (section in tutorial.sections) {
                for (text in section.textBlocks()) {
                    for (rawParagraph in text.split("\n\n")) {
                        // Markdown blocks may bold the labels — strip emphasis markers before anchoring
                        val paragraph = rawParagraph.replace("**", "")
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
        }

        violations.shouldBeEmpty()
    }

    "no reading-order references — lessons are browsed freely, cross-references go by name" {
        val forbidden = Regex("""\b(last|next|previous|earlier) +(\w+ +)?lessons?\b""", RegexOption.IGNORE_CASE)
        val violations = mutableListOf<String>()

        for (tutorial in allTutorials) {
            for (section in tutorial.sections) {
                val contents = section.textBlocks() + section.klangScriptBlocks()
                for (content in contents) {
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

    "every code block sets an explicit gain (loudness rule — never rely on the default)" {
        val violations = mutableListOf<String>()

        for (tutorial in allTutorials) {
            for (section in tutorial.sections) {
                for (code in section.klangScriptBlocks()) {
                    if (!code.contains(".gain(")) {
                        violations.add(
                            "${tutorial.slug} (${section.heading}): code block has no explicit .gain(...) — " +
                                "the default gain 1.0 breaks cross-tutorial loudness parity"
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
                for (code in section.klangScriptBlocks()) {
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
        }

        violations.shouldBeEmpty()
    }

    "player-button names are bold in prose (press **Update**, not press Update)" {
        val violations = mutableListOf<String>()

        for (tutorial in allTutorials) {
            for (section in tutorial.sections) {
                for (prose in section.textBlocks()) {
                    for (match in PLAYER_BUTTON_REGEX.findAll(prose)) {
                        val before = prose.substring((match.range.first - 2).coerceAtLeast(0), match.range.first)
                        val after = prose.substring(
                            (match.range.last + 1).coerceAtMost(prose.length),
                            (match.range.last + 3).coerceAtMost(prose.length),
                        )
                        if (before != "**" || after != "**") {
                            violations.add(
                                "${tutorial.slug} (${section.heading}): '${match.value}' is a player " +
                                    "button — write it bold (**${match.value}**) so the reader can find it"
                            )
                        }
                    }
                }
            }
        }

        violations.shouldBeEmpty()
    }

    "line comments inside one code block all start at the same column" {
        val violations = mutableListOf<String>()

        for (tutorial in allTutorials) {
            for (section in tutorial.sections) {
                for (code in section.klangScriptBlocks()) {
                    val columns = code.lines().mapNotNull(::commentColumn).toSet()
                    if (columns.size > 1) {
                        violations.add(
                            "${tutorial.slug} (${section.heading}): comments start at columns " +
                                "${columns.sorted()} — align them into one column"
                        )
                    }
                }
            }
        }

        violations.shouldBeEmpty()
    }

    "visuals parse and their values appear in the same section's code (no drift)" {
        val violations = mutableListOf<String>()

        for (tutorial in allTutorials) {
            for (section in tutorial.sections) {
                val codes = section.klangScriptBlocks()

                for (visual in section.blocks.filterIsInstance<Block.Visual>()) {
                    when (visual) {
                        is Block.Visual.Adsr -> {
                            // Without values of its own it draws what its example plays: checked in the row below
                            val value = visual.value ?: continue
                            val parts = value.split(":").mapNotNull { it.toDoubleOrNull() }
                            if (parts.size != 4) {
                                violations.add(
                                    "${tutorial.slug} (${section.heading}): Adsr visual value " +
                                        "'$value' does not parse as four colon-separated numbers"
                                )
                            }
                            // The visual keeps its compact colon encoding; the DSL is per-param
                            // since C0, so the code shows `.adsr(a, d, s, r)`. Match that form.
                            val perParamCall = parts.joinToString(", ") { n ->
                                if (n == n.toInt().toDouble()) n.toInt().toString() else n.toString()
                            }
                            // A filter envelope lives in named slots of `lpf(...)` (2026-09-07), so the
                            // same four numbers may also appear as `attack = a, decay = d, sustain = s, release = r`.
                            val namedCall = listOf("attack", "decay", "sustain", "release")
                                .zip(perParamCall.split(", "))
                                .joinToString(", ") { (slot, n) -> "$slot = $n" }
                            val matchesCode = codes.any { code ->
                                value in code || perParamCall in code || namedCall in code
                            }
                            if (codes.isNotEmpty() && !matchesCode) {
                                violations.add(
                                    "${tutorial.slug} (${section.heading}): Adsr visual value " +
                                        "'$value' (per-param: '$perParamCall') does not appear " +
                                        "in the section's code — picture and code must not drift"
                                )
                            }
                        }
                    }
                }
            }
        }

        violations.shouldBeEmpty()
    }

    "an ADSR picture has a player right after it and draws what that plays: the players it follows on the page" {
        val violations = mutableListOf<String>()

        for (tutorial in allTutorials) {
            for (section in tutorial.sections) {
                section.blocks.forEachIndexed { index, block ->
                    val visual = block as? Block.Visual.Adsr ?: return@forEachIndexed
                    val next = section.blocks.getOrNull(index + 1) as? Block.Code
                    val where = "${tutorial.slug} (${section.heading}): the Adsr visual '${visual.value ?: "from its example"}'"

                    if (next == null || next.lang != "KlangScript") {
                        violations.add("$where has no example right after it")
                        return@forEachIndexed
                    }

                    val numbers = visual.numbers(example = next.code)
                        ?.map { n -> if (n == n.toInt().toDouble()) n.toInt().toString() else n.toString() }

                    if (numbers == null) {
                        violations.add("$where: its first player writes no envelope, or its numbers do not parse")
                        return@forEachIndexed
                    }

                    // What the first player plays, its comments aside
                    val played = codeOnly(next.code).replace(Regex("""\s+"""), " ")
                    val written = Block.Visual.Adsr().numbers(example = next.code)
                    val named = listOf("attack", "decay", "sustain", "release").zip(numbers)
                        .joinToString(", ") { (slot, n) -> "$slot = $n" }

                    val draws = when (visual.envelope) {
                        // Values of its own or read from it: the player writes the same numbers (`1` and `1.0` alike)
                        Block.Visual.Adsr.Envelope.Loudness -> written == visual.numbers(example = next.code)
                        // The cutoff's envelope is read from no player: its picture names its values
                        Block.Visual.Adsr.Envelope.Cutoff -> visual.value != null && named in played
                    }

                    if (!draws) {
                        violations.add("$where (${visual.envelope}) is not what the first player after it plays")
                    }
                }
            }
        }

        violations.shouldBeEmpty()
    }

    "each player of Sound Design Basics that writes an envelope follows an ADSR picture" {
        // The maintainer, 2026-10-27: the track's examples show their envelope, where they write one (no picture in
        // the lessons before adsr is taught, nor in those that write none: "these do not define adsrs")
        val missing = soundDesignBasicsTrack.lessons.flatMap { tutorial ->
            tutorial.sections.flatMap { section ->
                val followed = section.playersAfterPictures()

                section.blocks.withIndex()
                    .filter { (index, block) -> block is Block.Code && ".adsr(" in codeOnly(block.code) && index !in followed }
                    .map { (index, _) -> "${tutorial.slug} (${section.heading}) block $index" }
            }
        }

        missing.shouldBeEmpty()
    }

    "each note an ADSR picture follows is held long enough to reach its sustain, as the picture draws it" {
        // The Bend of a Note first had adsr(0.8, 0.3, ...) on one-second notes: every note was let go during its
        // decay, the drawn sustain never played, and the light fell away from the curve (the maintainer, 2026-10-27:
        // "Something is up here", "The sustain seems to be broken"). A pluck (sustain 0) dies away anyway.
        val violations = mutableListOf<String>()

        for (tutorial in allTutorials) {
            for (section in tutorial.sections) {
                for ((index, picture) in section.playersAfterPictures()) {
                    if ((section.blocks[picture] as Block.Visual.Adsr).envelope != Block.Visual.Adsr.Envelope.Loudness) {
                        continue
                    }

                    val code = (section.blocks[index] as Block.Code).code
                    val (attack, decay, sustain) = Block.Visual.Adsr().numbers(example = code) ?: continue

                    if (sustain == 0.0) {
                        continue
                    }

                    // The gate is read from the notes' length; legato and clip change it on the way to the engine
                    if (Regex("""\b(legato|clip)\(""").containsMatchIn(codeOnly(code))) {
                        violations.add("${tutorial.slug} (${section.heading}) block $index: legato or clip under a picture, unmeasured")
                        continue
                    }

                    val pattern = compileBlock(code)

                    if (pattern == null) {
                        violations.add("${tutorial.slug} (${section.heading}) block $index: does not compile")
                        continue
                    }

                    val shortest = pattern.queryArc(0.0, 4.0).minOfOrNull { it.durationCycles } ?: continue
                    val heldSec = shortest * 60.0 / tutorial.rpm

                    if (heldSec < attack + decay - 1e-9) {
                        violations.add(
                            "${tutorial.slug} (${section.heading}) block $index: a note held ${heldSec}s, " +
                                "attack and decay take ${attack + decay}s",
                        )
                    }
                }
            }
        }

        violations.shouldBeEmpty()
    }

    "each layer of a stack can be put aside with `//` on its lines, and what is left still plays" {
        // A lesson says "put `//` in front of every line of one layer": the rest must compile (trailing commas are fine)
        val failures = mutableListOf<String>()

        for (tutorial in allTutorials) {
            for (section in tutorial.sections) {
                for (code in section.klangScriptBlocks()) {
                    val lines = code.lines()
                    val layers = stackLayers(lines)

                    // A stack the check cannot read would pass unseen: it fails instead
                    if (layers == null) {
                        if ("stack(" in codeOnly(code)) {
                            failures.add("${tutorial.slug} (${section.heading}): a stack not opened alone on its first line")
                        }

                        continue
                    }

                    for (layer in layers.takeIf { it.size > 1 } ?: continue) {
                        val without = lines.mapIndexed { index, line -> if (index in layer) "// $line" else line }.joinToString("\n")

                        if (runCatching { compileBlock(without) }.getOrNull() == null) {
                            failures.add("${tutorial.slug} (${section.heading}): without lines ${layer.first + 1}..${layer.last + 1}")
                        }
                    }
                }
            }
        }

        failures.shouldBeEmpty()
    }

    "an alternative is a player of its own, never a line behind `//` (the maintainer, 2026-10-27)" {
        val violations = allTutorials.flatMap { tutorial ->
            tutorial.sections.flatMap { section ->
                section.klangScriptBlocks().flatMap { code ->
                    code.lines().map { it.trim() }
                        .filter { it.startsWith("//") && COMMENTED_CALL_REGEX.containsMatchIn(it.removePrefix("//").trim()) }
                        .map { "${tutorial.slug} (${section.heading}): $it" }
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
})

/** All prose of a section: plain text blocks and markdown blocks alike. */
private fun TutorialSection.textBlocks(): List<String> =
    blocks.flatMap { block ->
        when (block) {
            is Block.Text -> listOf(block.text)
            is Block.Markdown -> listOf(block.markdown)
            else -> emptyList()
        }
    }

/** All runnable KlangScript blocks of a section. */
private fun TutorialSection.klangScriptBlocks(): List<String> =
    blocks.filterIsInstance<Block.Code>().filter { it.lang == "KlangScript" }.map { it.code }

/** Player buttons the lessons tell the reader to press; B1's tour names them all in bold. */
private val PLAYER_BUTTON_REGEX = Regex("""\bUpdate\b""")

/**
 * The column a line's trailing comment starts at, or null when the line carries none. A line
 * that is nothing but a comment (a commented-out code line in an A/B pair) has no trailing
 * comment of its own, so only a `//` with code in front of it counts.
 */
private fun commentColumn(line: String): Int? {
    val marker = line.indexOf("//", startIndex = line.indexOfFirst { !it.isWhitespace() }.coerceAtLeast(0) + 1)
    if (marker <= 0 || !line[marker - 1].isWhitespace()) return null
    if (line.take(marker).isBlank()) return null

    return marker
}

/** Keywords the call-scan matches that are not vocabulary. */
private val STRUCTURAL_KEYWORDS = setOf("if", "while", "for")

/**
 * The layers of an example that is a `stack(...)` from its first line, each as the range of its lines; null for any
 * other example. A layer ends at a comma back on the stack's own level; the stack closes on a line of its own.
 */
private fun stackLayers(lines: List<String>): List<IntRange>? {
    if (lines.firstOrNull()?.substringBefore("//")?.trim() != "stack(") {
        return null
    }

    val layers = mutableListOf<IntRange>()
    var depth = 1
    var start = -1

    for (index in 1 until lines.size) {
        val code = lines[index].substringBefore("//").replace(STRING_REGEX, "\"\"")

        if (start < 0 && code.isNotBlank() && !code.trim().startsWith(")")) {
            start = index
        }

        depth += code.count { it == '(' } - code.count { it == ')' }

        val closesOnItsOwn = depth == 0 && code.trim().startsWith(")")

        if (start >= 0 && ((depth == 1 && code.trim().endsWith(",")) || depth == 0)) {
            // The stack's own `)` is no part of the last layer; a layer that closes the stack on its own last line
            // cannot be put aside, so it keeps that line, and fails
            layers.add(start..(if (closesOnItsOwn) index - 1 else index))
            start = -1
        }

        if (depth == 0) {
            break
        }
    }

    return layers
}

/** A commented line that starts with a call (`// note(...)`, `// .adsr(...)`): code put aside, not a remark. */
private val COMMENTED_CALL_REGEX = Regex("""^\.?[a-zA-Z_][A-Za-z0-9_]*\s*\(""")

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

/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.script.intel

import io.kotest.assertions.throwables.shouldThrowAny
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.script.docs.KlangDocsRegistry
import io.peekandpoke.klang.script.parser.KlangScriptParser

/**
 * Where a local is used (KLANG-00349, the Quick help's "Used on"): `AnalyzedAst.usagesAt` finds every word the analysis
 * bound to the same declaration as the word at the cursor, in the text's order, with its line and that line's code.
 * Hand-built empty registry: this is analyzer machinery.
 */
class LocalUsagesSpec : StringSpec({

    fun analyze(source: String) = AnalyzedAst.build(source, KlangDocsRegistry())

    "the uses of a let, in order, the word at the cursor too, each with its line and that line's code" {
        val source = """
            |let a = 1
            |run(a)
            |
            |  run(a + a)
        """.trimMargin()
        val analysis = analyze(source)

        val usages = analysis.usagesAt(pos = source.indexOf("run(a)") + 4)

        usages.map { it.line } shouldContainExactly listOf(2, 4, 4)
        usages.map { it.code } shouldContainExactly listOf("run(a)", "run(a + a)", "run(a + a)")
        usages.map { source.substring(it.start, it.end) } shouldContainExactly listOf("a", "a", "a")
        usages.map { it.start } shouldContainExactly listOf(
            source.indexOf("run(a)") + 4,
            source.indexOf("(a + a)") + 1,
            source.indexOf("+ a)") + 2,
        )
    }

    "a shadowing declaration has its own uses: an inner name's are not the outer one's" {
        val source = """
            |let a = 1
            |let f = a => a + 1
            |run(a)
        """.trimMargin()
        val analysis = analyze(source)

        withClue("the outer a: only its read in run") {
            analysis.usagesAt(pos = source.indexOf("run(a)") + 4).map { it.line } shouldContainExactly listOf(3)
        }

        withClue("the parameter a: only its read in the body") {
            analysis.usagesAt(pos = source.indexOf("a + 1")).map { it.line } shouldContainExactly listOf(2)
        }
    }

    "no uses for what is not a local: a library or unknown name, a literal, a blank" {
        val source = "let a = 1\nrun(a, 2)"
        val analysis = analyze(source)

        analysis.usagesAt(pos = source.indexOf("run")).shouldBeEmpty()
        analysis.usagesAt(pos = source.indexOf("2)")).shouldBeEmpty()
        analysis.usagesAt(pos = source.indexOf(", ") + 1).shouldBeEmpty()
    }

    "with an edit ahead of the analysis, the uses after it move with it, and one the edit touches is left out" {
        val source = """
            |let a = 1
            |
            |run(a)
            |run(a)
        """.trimMargin()
        val analysis = analyze(source)

        // The user types a new const on the blank line; until it has a value the text does not parse
        val insertAt = source.indexOf("\n\n") + 1
        val above = source.substring(0, insertAt) + "const gu" + source.substring(insertAt)
        shouldThrowAny { KlangScriptParser.parse(above) }

        withClue("an edit above: the uses move with it, their lines too") {
            val usages = analysis.usagesAt(pos = above.indexOf("run(a)") + 4, text = above)

            usages.map { above.substring(it.start, it.end) } shouldContainExactly listOf("a", "a")
            usages.map { it.line } shouldContainExactly listOf(3, 4)
        }

        // The user types on the first use itself
        val around = source.replaceFirst("run(a)", "run(ab")
        shouldThrowAny { KlangScriptParser.parse(around) }

        withClue("an edit on the first use: it is left out, the second stays") {
            val usages = analysis.usagesAt(pos = around.lastIndexOf("run(a)") + 4, text = around)

            usages.map { it.line } shouldContainExactly listOf(4)
        }
    }

    "on a declared name: the local as a use finds it, its uses too, also a local nothing reads and an arrow's parameter" {
        val source = """
            |let pedalStock = x => x
            |let unused = 2
            |run(pedalStock)
        """.trimMargin()
        val analysis = analyze(source)

        withClue("the let's declared name") {
            val declared = analysis.declarationAt(pos = source.indexOf("pedalStock") + 2)

            declared?.code shouldBe "let pedalStock = x => x"
            declared?.namePos shouldBe source.indexOf("pedalStock")
            analysis.usagesAt(pos = source.indexOf("pedalStock") + 2).map { it.line } shouldContainExactly listOf(3)
        }

        withClue("a local nothing reads: found, with no uses") {
            analysis.declarationAt(pos = source.indexOf("unused"))?.code shouldBe "let unused = 2"
            analysis.usagesAt(pos = source.indexOf("unused")).shouldBeEmpty()
        }

        withClue("an arrow's parameter where it is declared, its use in the body") {
            analysis.declarationAt(pos = source.indexOf("x =>"))?.namePos shouldBe source.indexOf("x =>")
            analysis.usagesAt(pos = source.indexOf("x =>")).map { source.substring(it.start, it.end) } shouldContainExactly listOf("x")
        }

        withClue("a use is not a declaration; go-to-definition still only answers on uses") {
            analysis.declarationAt(pos = source.indexOf("run(") + 4) shouldBe null
            analysis.definitionAt(pos = source.indexOf("pedalStock")) shouldBe null
        }
    }
})

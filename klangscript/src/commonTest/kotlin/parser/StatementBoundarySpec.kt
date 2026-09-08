/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.script.parser

import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.peekandpoke.klang.script.runtime.KlangScriptSyntaxError

/**
 * Two statements may not share a line without a `;` between them.
 *
 * Without the rule a dropped dot is silent: `velocity("...")tag("hats")` is a finished chain plus an orphan
 * `tag(...)` mapper that nothing consumes, and since tags do not change the sound, nothing at all reports it
 * (`docs/tasks/klangscript-statement-boundaries.md`, found in Der Schmetterling).
 *
 * The must-still-parse rows are the ones that matter: the rule fires on a shared line only, so every
 * multi-line chain, leading-dot continuation and brace form in the song corpus has to survive it.
 */
class StatementBoundarySpec : StringSpec({

    fun parse(code: String) = KlangScriptParser.parse(code, "test.klang")

    // -- Rejected: two statements on one line ------------------------------------------------------------------------

    "the reported bug: a dropped dot before tag()" {
        val error = shouldThrow<KlangScriptSyntaxError> {
            parse("""s("bd*4")tag("drums")""")
        }

        error.message shouldContain "Expected a newline or ';' between statements"
        error.message shouldContain "Did you mean '.tag(...)'?"
        // The diagnostic points at the orphan, not at the end of the healthy chain
        error.location?.startLine shouldBe 1
        error.location?.startColumn shouldBe 10
    }

    "a dropped dot inside a declaration" {
        val error = shouldThrow<KlangScriptSyntaxError> {
            parse("let a = f() g()")
        }

        error.message shouldContain "Expected a newline or ';' between statements"
        error.message shouldContain "Did you mean '.g(...)'?"
    }

    "a dropped dot with a space before it" {
        val error = shouldThrow<KlangScriptSyntaxError> {
            parse("""note("c3") pan(0.5)""")
        }

        error.message shouldContain "Did you mean '.pan(...)'?"
    }

    "two juxtaposed literals, no hint to give" {
        val error = shouldThrow<KlangScriptSyntaxError> {
            parse("1 2")
        }

        error.message shouldContain "Expected a newline or ';' between statements"
        error.message shouldNotContain "Did you mean"
    }

    "a dropped dot inside a block body" {
        val error = shouldThrow<KlangScriptSyntaxError> {
            parse("let f = x => { let y = x.gain(0.5)tag(\"a\"); return y }")
        }

        error.message shouldContain "Did you mean '.tag(...)'?"
    }

    "an identifier that is not called gets no hint" {
        val error = shouldThrow<KlangScriptSyntaxError> {
            parse("let a = 1 b")
        }

        error.message shouldContain "Expected a newline or ';' between statements"
        error.message shouldNotContain "Did you mean"
    }

    // -- Still legal -------------------------------------------------------------------------------------------------

    "programs that must keep parsing" {
        val cases = listOf(
            "explicit semicolon" to "let a = 1; let b = 2",
            "a run of semicolons" to "let a = 1;;; let b = 2",
            "trailing semicolon" to """s("bd*4");""",
            "own lines, no semicolons" to "let a = 1\nlet b = 2",
            "leading-dot continuation" to "s(\"bd*4\")\n  .gain(0.5)\n  .pan(0.3)",
            "arrow body chain" to "let f = x => x.gain(0.5).pan(0.2)",
            "arrow block body on one line" to "let f = x => { let a = 1; return a }",
            "multi-line call args, then a chain" to "stack(\n  a.tag(\"x\"),\n  b.tag(\"y\")\n).tag(\"band\")",
            "if arms on one line" to "let a = 1\nif (a) { f() } else { g() }",
            "for header semicolons" to "for (let i = 0; i < 4; i = i + 1) { }",
            "for body statements on their own lines" to "for (let i = 0; i < 4; i = i + 1) {\n  f()\n  g()\n}",
            "block with a statement per line" to "let f = x => {\n  let a = 1\n  return a\n}",
            "a chain broken across lines mid-argument" to "s(\"bd\").gain(\n  0.5\n).pan(0.3)",
            "statement after a closing brace on the next line" to "let f = x => {\n  return x\n}\nlet b = 2",
        )

        cases.forEach { (name, code) ->
            withClue(name) {
                shouldNotThrowAny { parse(code) }
            }
        }
    }

    "an export chain over several lines keeps its tag" {
        // The shape from the song, written correctly: the rule must not touch it
        val program = parse("export hats = sound(\"hh*8\")\n  .fast(2)\n  .velocity(\"<1.0 0.85>*4\")\n  .tag(\"hats\")")

        program.statements.size shouldBe 1
    }
})

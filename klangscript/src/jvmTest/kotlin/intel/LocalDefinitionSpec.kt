/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.script.intel

import io.kotest.assertions.throwables.shouldThrowAny
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.script.docs.KlangDocsRegistry
import io.peekandpoke.klang.script.parser.KlangScriptParser
import io.peekandpoke.klang.script.types.KlangCallable
import io.peekandpoke.klang.script.types.KlangProperty
import io.peekandpoke.klang.script.types.KlangSymbol
import io.peekandpoke.klang.script.types.KlangType

/**
 * Go to a local's definition, and its code in the hover (KLANG-00321 (editor go to a local's definition and show its
 * code on hover)): `AnalyzedAst.definitionAt` finds the declaration of the `let` / `const` / `export` or arrow
 * parameter a word reads. The cursor stands on the word, as the mouse does in the editor.
 *
 * Hand-built registry, no stdlib: this is analyzer machinery.
 */
class LocalDefinitionSpec : StringSpec({

    val dslType = KlangType("IgnitorDsl")
    val ignType = KlangType("Ign")

    fun registry(): KlangDocsRegistry = KlangDocsRegistry().apply {
        registerAll(
            listOf(
                KlangSymbol(
                    name = "Ign", category = "ignitor", origin = KlangSymbol.Origin.Library("test"),
                    variants = listOf(KlangProperty(name = "Ign", type = ignType, library = "test")),
                ),
                KlangSymbol(
                    name = "saw", category = "ignitor", origin = KlangSymbol.Origin.Library("test"),
                    variants = listOf(
                        KlangCallable(name = "saw", receiver = ignType, params = emptyList(), returnType = dslType, library = "test")
                    ),
                ),
            )
        )
    }

    fun analyze(source: String) = AnalyzedAst.build(source, registry())

    "a reference to a let, const and export finds its declaration" {
        val source = """
            |let a = 1
            |const b = 2
            |export c = 3
            |run(a + b + c)
        """.trimMargin()
        val analysis = analyze(source)
        val use = source.indexOf("run(")

        val expected = listOf(
            "let a = 1" to KlangSymbol.LocalKind.LET,
            "const b = 2" to KlangSymbol.LocalKind.CONST,
            "export c = 3" to KlangSymbol.LocalKind.EXPORT,
        )

        for ((statement, kind) in expected) {
            val (keyword, name) = statement.split(" ")
            val start = source.indexOf(statement)

            withClue("$keyword $name: the declaration") {
                val definition = analysis.definitionAt(source.indexOf(name, startIndex = use)).shouldNotBeNull()

                definition.namePos shouldBe start + keyword.length + 1
                definition.start shouldBe start
                definition.end shouldBe start + statement.length
                definition.code shouldBe statement
                definition.symbol.origin shouldBe KlangSymbol.Origin.Local(kind)
            }
        }
    }

    "an arrow parameter inside its body finds the parameter" {
        val source = """
            |run((p, q) => p + q)
            |run(x => x.gain(1))
        """.trimMargin()
        val analysis = analyze(source)

        withClue("param q") {
            val q = analysis.definitionAt(source.indexOf("+ q") + 2).shouldNotBeNull()

            q.namePos shouldBe source.indexOf("q)")
            q.start shouldBe q.namePos
            q.end shouldBe q.namePos + 1
            q.code shouldBe "run((p, q) => p + q)"
            q.symbol.origin shouldBe KlangSymbol.Origin.Local(KlangSymbol.LocalKind.PARAM)
        }

        withClue("param p") {
            analysis.definitionAt(source.indexOf("p +")).shouldNotBeNull().namePos shouldBe source.indexOf("p, q")
        }

        withClue("param x") {
            analysis.definitionAt(source.indexOf("x.gain")).shouldNotBeNull().namePos shouldBe source.indexOf("x =>")
        }
    }

    "an inner declaration shadows an outer one" {
        val source = """
            |let g = 1
            |const f = () => {
            |  let g = 2
            |  run(g)
            |}
            |run(g)
        """.trimMargin()
        val analysis = analyze(source)

        withClue("the inner use reads the inner g") {
            val inner = analysis.definitionAt(source.indexOf("run(g)") + 4).shouldNotBeNull()

            inner.namePos shouldBe source.indexOf("let g = 2") + 4
            inner.code shouldBe "let g = 2"
        }

        withClue("the outer use reads the outer g") {
            analysis.definitionAt(source.lastIndexOf("run(g)") + 4).shouldNotBeNull().namePos shouldBe 4
        }
    }

    "a library name, a member name and the declared name itself find nothing" {
        val source = """
            |let gain = 1
            |const h = () => 2
            |let g = Ign.saw()
            |run(g.gain, h)
        """.trimMargin()
        val analysis = analyze(source)

        withClue("the library name") { analysis.definitionAt(source.indexOf("Ign")) shouldBe null }
        withClue("a member") { analysis.definitionAt(source.indexOf("saw")) shouldBe null }
        withClue("a member named like a local") { analysis.definitionAt(source.indexOf(".gain") + 1) shouldBe null }
        withClue("a literal") { analysis.definitionAt(source.indexOf("1")) shouldBe null }
        withClue("a keyword") { analysis.definitionAt(source.indexOf("const")) shouldBe null }
        // An arrow initializer sees its own name, so the declared name is in scope where it stands
        withClue("the declared name") { analysis.definitionAt(source.indexOf("h =")) shouldBe null }

        // The same words where they read a local do find it
        analysis.definitionAt(source.indexOf("g.gain")).shouldNotBeNull().namePos shouldBe source.indexOf("g = Ign")
        analysis.definitionAt(source.indexOf("h)")).shouldNotBeNull().namePos shouldBe source.indexOf("h =")
    }

    "a multi-line initializer gives the whole statement's range" {
        val source = """
            |let a = foo(
            |  1,
            |  [2, 3],
            |)
            |run(a)
        """.trimMargin()
        val definition = analyze(source).definitionAt(source.indexOf("run(a)") + 4).shouldNotBeNull()
        val statement = source.substringBefore("\nrun")

        withClue("the statement's range") {
            definition.start shouldBe 0
            definition.end shouldBe statement.length
        }

        definition.code shouldBe statement

        // An arrow's block body: its closing brace ends the statement
        val block = "const f = () => {\n  run(1)\n}\nf()"
        val blockStatement = block.substringBefore("\nf()")

        withClue("a block body's closing brace") {
            val blockDefinition = analyze(block).definitionAt(block.lastIndexOf("f()")).shouldNotBeNull()

            blockDefinition.end shouldBe blockStatement.length
            blockDefinition.code shouldBe blockStatement
        }
    }

    "stale text: an edit above keeps the jump correct, and an edit around the declaration gives nothing" {
        val source = """
            |let a = 1
            |
            |let b = 2
            |run(a + b)
        """.trimMargin()
        val analysis = analyze(source)

        // The user types a new const on the blank line; until it has a value the text does not parse
        val insertAt = source.indexOf("\n\n") + 1
        val above = source.substring(0, insertAt) + "const gu" + source.substring(insertAt)
        shouldThrowAny { KlangScriptParser.parse(above) }

        withClue("an edit above: b moves with it") {
            val b = analysis.definitionAt(pos = above.indexOf("+ b") + 2, text = above).shouldNotBeNull()

            b.namePos shouldBe above.indexOf("let b") + 4
            b.start shouldBe above.indexOf("let b")
            b.code shouldBe "let b = 2"
        }

        withClue("an edit below a: a stays") {
            analysis.definitionAt(pos = above.indexOf("(a") + 1, text = above).shouldNotBeNull().namePos shouldBe 4
        }

        // The user edits the declaration of b itself
        val around = source.replace("let b = 2", "let b = (2")
        shouldThrowAny { KlangScriptParser.parse(around) }

        withClue("an edit around b: no jump") {
            analysis.definitionAt(pos = around.indexOf("+ b") + 2, text = around) shouldBe null
        }

        withClue("an edit around b: a above it still jumps") {
            analysis.definitionAt(pos = around.indexOf("(a") + 1, text = around).shouldNotBeNull().namePos shouldBe 4
        }
    }

    "stale text: a word inside the edited stretch finds nothing, even where the old text has the same word in place" {
        // The user renamed the parameter `x` to `a` and is typing at the end. Between the two changes the `a` in the
        // body stands where the old text has an `a` too, which read the outer `let a`; in the new text it reads the
        // parameter.
        val source = "let a = 1\nconst f = (x) => run(a)\n"
        val typed = "let a = 1\nconst f = (a) => run(a)\n("
        shouldThrowAny { KlangScriptParser.parse(typed) }

        withClue("a word inside the edited stretch") {
            analyze(source).definitionAt(pos = typed.indexOf("run(a)") + 4, text = typed) shouldBe null
        }
    }

    "an identifier inside a template interpolation never takes the place of an equal one outside it" {
        // The parser reads `${...}` on its own: the `x` in it sits at 2:3 counted from the interpolation, where the
        // `x` of line 2 sits in the source, and the two nodes are equal
        val source = "let x = 1\n  x\nconst f = (x) => `\${\n  x}`"

        withClue("the x of line 2") {
            analyze(source).definitionAt(12).shouldNotBeNull().namePos shouldBe 4
        }
    }

    "the hover's code: the declaration's whole lines without their common indentation, at most 12, then an ellipsis" {
        val elements = (1..14).joinToString("\n") { "    $it," }
        val source = "if (true) {\n  const long = [\n$elements\n  ]\n  run(long)\n}"

        val code = analyze(source).definitionAt(source.indexOf("run(long)") + 4).shouldNotBeNull().code
        val expected = listOf("const long = [") + (1..11).map { "  $it," } + "..."

        withClue("the hover's code") {
            code shouldBe expected.joinToString("\n")
        }
    }
})

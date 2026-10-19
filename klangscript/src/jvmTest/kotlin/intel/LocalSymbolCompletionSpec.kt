/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.script.intel

import io.kotest.assertions.throwables.shouldThrowAny
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.script.docs.KlangDocsRegistry
import io.peekandpoke.klang.script.parser.KlangScriptParser
import io.peekandpoke.klang.script.types.KlangCallable
import io.peekandpoke.klang.script.types.KlangProperty
import io.peekandpoke.klang.script.types.KlangSymbol
import io.peekandpoke.klang.script.types.KlangType

/**
 * Code completion offers the song's own names (`docs/tasks/editor-local-symbol-completion.md`): the `let` / `const` /
 * `export` names and the arrow parameters visible at the cursor, through `AnalyzedAst.localsAt` and
 * `CompletionProvider.localCompletions`. The cursor is the start of a word being typed, as in the editor.
 *
 * Hand-built registry, no stdlib: this is analyzer machinery.
 */
class LocalSymbolCompletionSpec : StringSpec({

    val dslType = KlangType("IgnitorDsl")
    val ignType = KlangType("Ign")
    val fnType = KlangType("Function1", functionParams = listOf(KlangType("Number")), functionReturn = KlangType("Number"))

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
                KlangSymbol(
                    name = "shaper", category = "test", origin = KlangSymbol.Origin.Library("test"),
                    variants = listOf(KlangProperty(name = "shaper", type = fnType, library = "test")),
                ),
            )
        )
    }

    fun analyze(source: String) = AnalyzedAst.build(source, registry())

    /** The names [AnalyzedAst.localsAt] offers at the start of [marker] in [source]. */
    fun namesAt(source: String, marker: String): List<String> {
        val pos = source.indexOf(marker)
        check(pos >= 0) { "marker '$marker' not in the source" }

        return analyze(source).localsAt(pos).map { it.name }
    }

    "top-level let, const and export are offered after their declaration, not before it" {
        val source = """
            |firstHere
            |let a = 1
            |const b = 2
            |betweenHere
            |export c = 3
            |cursorHere
            |let later = 4
        """.trimMargin()
        val analysis = analyze(source)

        val locals = analysis.localsAt(source.indexOf("cursorHere"))
        locals.map { it.name } shouldContainExactly listOf("c", "b", "a")
        locals.map { it.kind } shouldContainExactly listOf(
            KlangSymbol.LocalKind.EXPORT, KlangSymbol.LocalKind.CONST, KlangSymbol.LocalKind.LET,
        )

        namesAt(source, "betweenHere") shouldContainExactly listOf("b", "a")
        namesAt(source, "firstHere") shouldBe emptyList()
    }

    "an inner let shadows an outer one of the same name: one entry, the inner type" {
        val source = """
            |let g = Ign.saw()
            |const f = () => {
            |  let g = 1
            |  cursorHere
            |}
        """.trimMargin()

        val locals = analyze(source).localsAt(source.indexOf("cursorHere"))
        val gs = locals.filter { it.name == "g" }

        gs.size shouldBe 1
        gs.single().type shouldBe KlangType("Number")
        locals.map { it.name } shouldContainExactly listOf("g", "f")
    }

    "arrow parameters are visible inside the arrow body only" {
        val source = """
            |run(x => x.gain(insideHere))
            |const f = (p, q) => p + insideF
            |afterHere
        """.trimMargin()

        namesAt(source, "insideHere") shouldContainExactly listOf("x")
        namesAt(source, "insideF") shouldContainAll listOf("p", "q")
        namesAt(source, "afterHere") shouldContainExactly listOf("f")
    }

    "a binding declared in a sibling block is not offered" {
        val source = """
            |let c = true
            |if (c) {
            |  let inThen = 1
            |  inThen
            |} else {
            |  let inElse = 2
            |  elseHere
            |}
            |run(a => a, b => bHere)
        """.trimMargin()

        val inElse = namesAt(source, "elseHere")
        inElse shouldContainExactly listOf("inElse", "c")
        inElse shouldNotContain "inThen"

        val inB = namesAt(source, "bHere")
        inB shouldContainExactly listOf("b", "c")
        inB shouldNotContain "a"
    }

    "a local with an inferred type carries it in the detail, a local with an unknown type carries none" {
        val source = """
            |let g = Ign.saw()
            |const u = whatever()
            |let fx = shaper
            |cursorHere
        """.trimMargin()
        val analysis = analyze(source)
        val provider = CompletionProvider(analysis.registry)
        val locals = analysis.localsAt(source.indexOf("cursorHere"))

        val byName = provider.localCompletions(locals, prefix = "").associateBy { it.name }

        byName.getValue("g").detail shouldBe "let . IgnitorDsl"
        byName.getValue("g").kind shouldBe CompletionSuggestion.Kind.PROPERTY
        byName.getValue("u").detail shouldBe "const"
        byName.getValue("fx").kind shouldBe CompletionSuggestion.Kind.FUNCTION

        // The typed word filters, ignoring case
        provider.localCompletions(locals, prefix = "G").map { it.name } shouldContainExactly listOf("g")
    }

    "stale analysis: an insertion that does not parse keeps the declarations above it, never one below it" {
        val source = """
            |let a = 1
            |
            |const b = () => 2
        """.trimMargin()
        val analysis = analyze(source)

        // The user types a new const on the blank line; until it has a value the text does not parse
        val insertAt = source.indexOf("\n\n") + 1
        val typed = source.substring(0, insertAt) + "const gu" + source.substring(insertAt)
        shouldThrowAny { KlangScriptParser.parse(typed) }

        val names = analysis.localsAt(pos = typed.indexOf("gu"), text = typed).map { it.name }
        names shouldContainExactly listOf("a")
    }

    "stale analysis: an edit inside a lambda body keeps that body's locals above it, never one below it" {
        val source = """
            |let top = 1
            |run(p => {
            |  let inner = 2
            |
            |  let below = () => 3
            |  below
            |})
        """.trimMargin()
        val analysis = analyze(source)

        val insertAt = source.indexOf("\n\n") + 1
        val typed = source.substring(0, insertAt) + "  const gu" + source.substring(insertAt)
        shouldThrowAny { KlangScriptParser.parse(typed) }

        analysis.localsAt(pos = typed.indexOf("gu"), text = typed).map { it.name } shouldContainExactly
            listOf("inner", "p", "top")
    }

    "stale analysis: an edit at the end of a lambda body never offers a sibling lambda's parameter" {
        val source = """
            |let top = 1
            |run(p => { let inner = 2; inner }, q => q.gain(1, 2, 3))
        """.trimMargin()
        val analysis = analyze(source)

        val insertAt = source.indexOf("inner }") + "inner".length
        val typed = source.substring(0, insertAt) + " + const gu" + source.substring(insertAt)
        shouldThrowAny { KlangScriptParser.parse(typed) }

        val names = analysis.localsAt(pos = typed.indexOf("gu"), text = typed).map { it.name }
        names shouldNotContain "q"
        names shouldContain "top"
    }

    "stale analysis: typed text that repeats the text after it never offers the declaration after the cursor" {
        // The user typed `let q = foo(b` at the start of line 2 and closeBrackets added the `)`. The shared prefix
        // runs on into `let ` of `let b`, so where the edit starts is ambiguous.
        val source = "let a = 1\nlet b = 2"
        val typed = "let a = 1\nlet q = foo(b)let b = 2"
        shouldThrowAny { KlangScriptParser.parse(typed) }

        analyze(source).localsAt(pos = typed.indexOf("(b)") + 1, text = typed).map { it.name } shouldContainExactly
            listOf("a")

        // The same with an arrow initializer: `b` is then visible from its keyword on, right where the edit can start
        val arrowSource = "let a = 1\nlet b = () => 2"
        val arrowTyped = "let a = 1\nlet q = foo(b)let b = () => 2"
        shouldThrowAny { KlangScriptParser.parse(arrowTyped) }

        val arrowNames = analyze(arrowSource).localsAt(pos = arrowTyped.indexOf("(b)") + 1, text = arrowTyped)
        arrowNames.map { it.name } shouldContainExactly listOf("a")
    }

    "a let with a plain initializer is not offered inside it, an arrow initializer sees its own name" {
        val source = """
            |let osc = run(osHere)
            |const f = () => f()
        """.trimMargin()

        namesAt(source, "osHere") shouldBe emptyList()
        namesAt(source, "f()") shouldContainExactly listOf("f", "osc")
    }

    "inside its own initializer a shadowing let offers the outer binding it reads" {
        val source = """
            |let g = Ign.saw()
            |if (true) {
            |  let g = g.gain(2)
            |}
        """.trimMargin()

        val locals = analyze(source).localsAt(source.indexOf("g.gain"))

        locals.map { it.name } shouldContainExactly listOf("g")
        locals.single().type shouldBe dslType
    }

    "a scope two levels deep is found past its parent's own last token" {
        val source = """
            |if (true) {
            |  run(p => {
            |    let inner = 1
            |    innerHere
            |  })
            |}
        """.trimMargin()

        namesAt(source, "innerHere") shouldContainExactly listOf("inner", "p")
    }

    "an arrow inside a template interpolation offers its parameter nowhere else" {
        // The parser reads `${...}` on its own: the arrow's own offsets there count from the interpolation's start
        val source = """
            |let a = 1
            |cursorHere
            |let s = `${'$'}{run(zz => zz)}`
        """.trimMargin()

        namesAt(source, "cursorHere") shouldContainExactly listOf("a")
    }
})

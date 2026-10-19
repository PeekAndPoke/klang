/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.script.intel

import io.kotest.assertions.throwables.shouldThrowAny
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldStartWith
import io.peekandpoke.klang.script.docs.KlangDocsRegistry
import io.peekandpoke.klang.script.parser.KlangScriptParser
import io.peekandpoke.klang.script.types.KlangCallable
import io.peekandpoke.klang.script.types.KlangParam
import io.peekandpoke.klang.script.types.KlangProperty
import io.peekandpoke.klang.script.types.KlangSymbol
import io.peekandpoke.klang.script.types.KlangType

/**
 * Code completion offers a call's unfilled parameters as `name = ` where an argument starts (task 6.4 of
 * `docs/tasks/klangscript-named-args-docs-polish.md`), through `AnalyzedAst.namedArgumentsAt` and
 * `CompletionProvider.namedArgumentCompletions`. A `|` in a test source marks the cursor and is cut out: it stands at
 * the start of the word being typed, as the editor passes it.
 *
 * Hand-built registry, no stdlib: this is analyzer machinery. The callables mirror real signatures:
 * `Ignitor.saw(freq?, configure?)`, `IgnitorDsl.lowpass(freq, q = 0.707, configure?)`,
 * `IgnitorDsl.adsr(attack, decay, sustain, release, configure?)`, `Ignitor.variants(vararg children)` and sprudel's
 * `note(pattern)` with `SprudelPattern.gain(amount?)`.
 */
class NamedArgumentCompletionSpec : StringSpec({

    val dslType = KlangType("IgnitorDsl")
    val ignType = KlangType("Ign")
    val patternType = KlangType("SprudelPattern")
    val knobType = KlangType("IgnitorDslLike")

    fun configureOf(builder: String) = KlangType(
        simpleName = "Function1",
        isNullable = true,
        functionParams = listOf(KlangType(builder)),
        functionReturn = KlangType(builder),
    )

    fun required(name: String) = KlangParam(name = name, type = knobType)

    fun configure(builder: String) =
        KlangParam(name = "configure", type = configureOf(builder), isOptional = true, defaultDoc = "null")

    fun method(name: String, receiver: KlangType, params: List<KlangParam>, returnType: KlangType) = KlangSymbol(
        name = name, category = "test", origin = KlangSymbol.Origin.Library("test"),
        variants = listOf(
            KlangCallable(name = name, receiver = receiver, params = params, returnType = returnType, library = "test")
        ),
    )

    fun registry(): KlangDocsRegistry = KlangDocsRegistry().apply {
        registerAll(
            listOf(
                KlangSymbol(
                    name = "Ign", category = "ignitor", origin = KlangSymbol.Origin.Library("test"),
                    variants = listOf(KlangProperty(name = "Ign", type = ignType, library = "test")),
                ),
                method(
                    name = "saw",
                    receiver = ignType,
                    params = listOf(
                        KlangParam(name = "freq", type = knobType.copy(isNullable = true), isOptional = true, defaultDoc = "null"),
                        configure("OscSawBuilder"),
                    ),
                    returnType = dslType,
                ),
                method(
                    name = "variants",
                    receiver = ignType,
                    params = listOf(KlangParam(name = "children", type = knobType, isVararg = true, description = "The children.")),
                    returnType = dslType,
                ),
                method(
                    name = "lowpass",
                    receiver = dslType,
                    params = listOf(
                        required("freq"),
                        KlangParam(name = "q", type = knobType, isOptional = true, defaultDoc = "0.707"),
                        configure("FilterBuilder"),
                    ),
                    returnType = dslType,
                ),
                method(
                    name = "adsr",
                    receiver = dslType,
                    params = listOf(required("attack"), required("decay"), required("sustain"), required("release"), configure("AdsrBuilder")),
                    returnType = dslType,
                ),
                KlangSymbol(
                    name = "note", category = "sprudel", origin = KlangSymbol.Origin.Library("test"),
                    variants = listOf(
                        KlangCallable(
                            name = "note",
                            params = listOf(KlangParam(name = "pattern", type = KlangType("String"))),
                            returnType = patternType,
                            library = "test",
                        )
                    ),
                ),
                method(
                    name = "gain",
                    receiver = patternType,
                    params = listOf(KlangParam(name = "amount", type = KlangType("PatternLike", isNullable = true), isOptional = true)),
                    returnType = patternType,
                ),
            )
        )
    }

    fun analyze(source: String) = AnalyzedAst.build(source, registry())

    /** [marked] without its `|`, and the offset where the `|` stood. */
    fun cursorIn(marked: String): Pair<String, Int> {
        val pos = marked.indexOf("|")
        check(pos >= 0) { "no cursor '|' in the source" }

        return marked.removeRange(pos, pos + 1) to pos
    }

    /** The parameter names [AnalyzedAst.namedArgumentsAt] offers at the `|` in [marked], analysed as it stands. */
    fun namesAt(marked: String): List<String> {
        val (text, pos) = cursorIn(marked)

        return analyze(text).namedArgumentsAt(pos).map { it.name }
    }

    /** The names offered at the `|` in [typedMarked] (which does not parse) by the analysis of [source]. */
    fun staleNamesAt(source: String, typedMarked: String): List<String> {
        val (typed, pos) = cursorIn(typedMarked)
        shouldThrowAny { KlangScriptParser.parse(typed) }

        return analyze(source).namedArgumentsAt(pos = pos, text = typed).map { it.name }
    }

    "at the start of an argument list every parameter is offered, in declaration order" {
        namesAt("Ign.saw().lowpass(|)") shouldContainExactly listOf("freq", "q", "configure")

        // The word being typed is the slot's name prefix, no positional argument
        namesAt("Ign.saw().lowpass(|fr)") shouldContainExactly listOf("freq", "q", "configure")

        // A value of one word reads the same way: a name goes in front of it (`freq = 800`)
        namesAt("Ign.saw().lowpass(|800)") shouldContainExactly listOf("freq", "q", "configure")

        namesAt("Ign.saw().lowpass(\n  |\n)") shouldContainExactly listOf("freq", "q", "configure")

        // Blanks between the callee and its `(`
        namesAt("Ign.saw().lowpass (|)") shouldContainExactly listOf("freq", "q", "configure")
    }

    "after named arguments the unfilled parameters are offered, never one named before or after the cursor" {
        namesAt("Ign.saw().lowpass(freq = 800, |)") shouldContainExactly listOf("q", "configure")
        namesAt("Ign.saw().lowpass(q = 1, |x, freq = 2)") shouldContainExactly listOf("configure")

        // A comment is no value, and the commas and positional arguments of a nested call are its own
        namesAt("Ign.saw().lowpass(freq = 800, /* a, b */ |)") shouldContainExactly listOf("q", "configure")
        namesAt("Ign.saw().lowpass(freq = Ign.saw().adsr(1, 2, 3, 4), |)") shouldContainExactly listOf("q", "configure")
        namesAt("Ign.saw().lowpass(|x, q = Ign.saw().adsr(1, 2, 3, 4))") shouldContainExactly listOf("freq", "configure")

        // One argument per line with a trailing comma: the empty slot after it is no positional argument
        namesAt("Ign.saw().lowpass(\n    |q,\n    freq = 800,\n)") shouldContainExactly listOf("q", "configure")
    }

    "commas inside strings and backtick strings split no arguments, an escaped quote ends no string" {
        namesAt("Ign.saw().lowpass(freq = 1, q = `a, b`, |)") shouldContainExactly listOf("configure")
        namesAt("""Ign.saw().lowpass(freq = 1, q = "a\", b", |)""") shouldContainExactly listOf("configure")
    }

    "a positional argument anywhere in the call offers nothing: the check refuses mixing the two styles" {
        namesAt("Ign.saw().lowpass(800, |)") shouldBe emptyList()
        namesAt("Ign.saw().lowpass(|x, 800)") shouldBe emptyList()

        // A comparison and an arrow are values, not names
        namesAt("Ign.saw().lowpass(freq == 1, |)") shouldBe emptyList()
        namesAt("Ign.saw().lowpass(q => 1, |)") shouldBe emptyList()

        // The rule the completion follows
        analyze("Ign.saw().lowpass(800, q = 1)").diagnostics.single().message shouldContain
            "uses both positional and named arguments"
    }

    "a value begun in the slot, or anything after the word in it, offers nothing" {
        namesAt("Ign.saw().lowpass(freq = |1)") shouldBe emptyList()
        namesAt("Ign.saw().lowpass(freq = 800 + |x)") shouldBe emptyList()

        // At the start of a name already written, and before a positional value
        namesAt("Ign.saw().lowpass(|freq = 800)") shouldBe emptyList()
        namesAt("Ign.saw().lowpass(|\"c\")") shouldBe emptyList()
    }

    "inside a string, a backtick string or a comment nothing is offered" {
        namesAt("Ign.saw().lowpass(freq = 1, q = \"a, |\")") shouldBe emptyList()
        namesAt("Ign.saw().lowpass(freq = 1, // |\n)") shouldBe emptyList()

        // Typing on in a backtick string that is not closed yet
        staleNamesAt(source = "Ign.saw().lowpass(freq = 1, )", typedMarked = "Ign.saw().lowpass(freq = 1, q = `a, |)") shouldBe
            emptyList()

        // ... and in a block comment that is not closed yet (the lexer still reads the last character, so this parses)
        namesAt("Ign.saw().lowpass(/* |)") shouldBe emptyList()
    }

    "a call inside a template interpolation never answers for the call at its offset" {
        // The parser reads `${...}` on its own: the inner call's `(` counts from the interpolation, and lands on the
        // offset of the outer call's `(`. The outer receiver is unknown, so nothing is offered there.
        val marked = """
            |abcdefghi.lowpass(|)
            |let s = `${'$'}{Ign.saw().lowpass(1)}`
        """.trimMargin()

        namesAt(marked) shouldBe emptyList()
    }

    "a nested call offers its own parameters, the outer call's once the nested one is closed" {
        namesAt("Ign.saw(freq = Ign.saw().lowpass(|))") shouldContainExactly listOf("freq", "q", "configure")
        namesAt("Ign.saw(freq = Ign.saw().lowpass(800), |)") shouldContainExactly listOf("configure")
    }

    "an unknown callable, a local function, a bracket and an arrow body offer nothing" {
        namesAt("whatever(|)") shouldBe emptyList()
        namesAt("x => x.lowpass(|)") shouldBe emptyList()

        // A local shadows the library's `note`, and a local function has no parameters the analysis knows
        namesAt("const note = (x) => x\nnote(|)") shouldBe emptyList()

        namesAt("let g = (|a)") shouldBe emptyList()
        namesAt("Ign.saw().lowpass(freq = [|])") shouldBe emptyList()
        namesAt("Ign.saw(x => |x)") shouldBe emptyList()

        // The scan itself reads argument lists only, never `[...]` or `{...}`
        ArgumentSlotAt.scan(text = "f()", pos = 2)?.openParen shouldBe 1
        ArgumentSlotAt.scan(text = "f([])", pos = 3) shouldBe null
        ArgumentSlotAt.scan(text = "f({})", pos = 3) shouldBe null
    }

    "a method call on a receiver offers the method's parameters" {
        namesAt("note(\"c\").gain(|)") shouldContainExactly listOf("amount")
        namesAt("Ign.saw().adsr(attack = 0.01, |)") shouldContainExactly listOf("decay", "sustain", "release", "configure")
        namesAt("let g = Ign.saw()\ng.lowpass(|)") shouldContainExactly listOf("freq", "q", "configure")
    }

    "a vararg parameter is offered by name, where it takes an array; a positional vararg call offers nothing" {
        namesAt("Ign.variants(|)") shouldContainExactly listOf("children")
        namesAt("Ign.variants(Ign.saw(), |)") shouldBe emptyList()
        namesAt("Ign.variants(children = [Ign.saw()], |)") shouldBe emptyList()

        val (text, pos) = cursorIn("Ign.variants(|)")
        val params = analyze(text).namedArgumentsAt(pos)
        val children = CompletionProvider(registry()).namedArgumentCompletions(params, prefix = "").single()

        children.detail shouldBe "vararg IgnitorDslLike"
        children.description shouldStartWith "By name it takes an array: children = [...]."
        children.description shouldContain "The children."
    }

    "a suggestion shows `name =`, inserts `name = `, and says the type and the default" {
        val (text, pos) = cursorIn("Ign.saw().lowpass(|)")
        val params = analyze(text).namedArgumentsAt(pos)
        val provider = CompletionProvider(registry())

        val suggestions = provider.namedArgumentCompletions(params, prefix = "")

        suggestions.map { it.label } shouldContainExactly listOf("freq =", "q =", "configure =")
        suggestions.map { it.insertText } shouldContainExactly listOf("freq = ", "q = ", "configure = ")
        suggestions.map { it.kind }.toSet() shouldBe setOf(CompletionSuggestion.Kind.PARAMETER)
        suggestions.map { it.detail } shouldContainExactly listOf(
            "IgnitorDslLike . required",
            "IgnitorDslLike . default 0.707",
            "((FilterBuilder) -> FilterBuilder)? . default null",
        )

        // An optional parameter with no documented default
        val (gainText, gainPos) = cursorIn("note(\"c\").gain(|)")
        provider.namedArgumentCompletions(analyze(gainText).namedArgumentsAt(gainPos), prefix = "").single().detail shouldBe
            "PatternLike? . optional"

        // The typed word filters, ignoring case
        provider.namedArgumentCompletions(params, prefix = "Q").map { it.name } shouldContainExactly listOf("q")
    }

    "stale analysis: the call comes from the last good analysis, the filled names from the text being typed" {
        // The user types on in the argument list; `q = 1 +` does not parse yet
        staleNamesAt(
            source = "Ign.saw().lowpass(freq = 800)\nlet b = 1",
            typedMarked = "Ign.saw().lowpass(freq = 800, q = 1 +, |)\nlet b = 1",
        ) shouldContainExactly listOf("configure")

        // A line that does not parse below the call
        staleNamesAt(
            source = "Ign.saw().lowpass(freq = 800, )\nlet b = 1",
            typedMarked = "Ign.saw().lowpass(freq = 800, |)\nlet b = 1\nconst gu",
        ) shouldContainExactly listOf("q", "configure")
    }

    "stale analysis: an edit in front of the call's `(` offers nothing until the text parses again" {
        // The edit rewrote the line: the new `(` stands at the very offset of the old call's `(`, but the last good
        // analysis never saw this receiver
        staleNamesAt(source = "Ign.saw().lowpass(1)", typedMarked = "Ign.sin().lowpass(|") shouldBe emptyList()

        // One edit ran from the broken line above into the receiver: `note("c")` was `Ign.saw()`, same length, same
        // offsets, and a pattern has no `lowpass`
        staleNamesAt(
            source = "let a = 1\nIgn.saw().lowpass(freq = 1, )",
            typedMarked = "let a = (\nnote(\"c\").lowpass(freq = 1, |)",
        ) shouldBe emptyList()

        // ... or into the call's name: `adsr` was `lowpass`
        staleNamesAt(
            source = "let a = 1\nIgn.saw().lowpass(freq = 800, )",
            typedMarked = "let a = (\nIgn.saw().adsr(freq = 800, |)",
        ) shouldBe emptyList()

        // A broken line above the call could declare a local named like the receiver: nothing either
        staleNamesAt(
            source = "let a = 1\nIgn.saw().lowpass(freq = 800, )",
            typedMarked = "let a = 1\nconst gu\nIgn.saw().lowpass(freq = 800, |)",
        ) shouldBe emptyList()
    }

    "a grouping paren is no call: the call around it does not answer for it" {
        // The index covers a call from its callee to its last argument, so the node at the inner `(` is `lowpass`
        namesAt("Ign.saw().lowpass(freq = 1, (|q))") shouldBe emptyList()
    }

    "stale analysis: a grouping paren the edit made a call offers nothing" {
        // Review round 1's probe, aligned: seven blanks stand where the text has `lowpass`, so the `(` stands at the
        // same offset in both texts and only the guards refuse it (the edit lies in front of the `(`, and the node
        // there is the call around the grouping paren)
        staleNamesAt(
            source = "let a = 1\nIgn.saw().lowpass(freq = 1, ${" ".repeat(7)}(q))",
            typedMarked = "let a = (\nIgn.saw().lowpass(freq = 1, lowpass(|q))",
        ) shouldBe emptyList()
    }

    "stale analysis: a call in an arrow a declaration binds offers nothing, an arrow passed as an argument does" {
        // `f` runs later and reads the `g` declared below it; the edit changed that `g` into a pattern
        staleNamesAt(
            source = "const f = () => g.lowpass()\nlet g = Ign.saw()",
            typedMarked = "const f = () => g.lowpass(|)\nlet g = note(\"c\"",
        ) shouldBe emptyList()

        // An arrow passed to a call runs where it is written and reads only what is declared above it
        staleNamesAt(
            source = "let g = Ign.saw()\nrun(() => g.lowpass())\nlet h = 1",
            typedMarked = "let g = Ign.saw()\nrun(() => g.lowpass(|))\nlet h = (",
        ) shouldContainExactly listOf("freq", "q", "configure")
    }
})

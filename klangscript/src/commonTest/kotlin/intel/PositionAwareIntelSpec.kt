/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.script.intel

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.ints.shouldBeGreaterThanOrEqual
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import io.peekandpoke.klang.script.docs.KlangDocsRegistry
import io.peekandpoke.klang.script.klangScriptEngine
import io.peekandpoke.klang.script.runtime.NumberValue
import io.peekandpoke.klang.script.types.KlangCallable
import io.peekandpoke.klang.script.types.KlangParam
import io.peekandpoke.klang.script.types.KlangProperty
import io.peekandpoke.klang.script.types.KlangSymbol
import io.peekandpoke.klang.script.types.KlangType

/**
 * The editor reads a name as what it is where it stands (the open items of `docs/tasks-archive/2026-10/20261007-callable-object-docs.md`):
 *
 * - the hover of a name stdlib and sprudel share shows the variant of that position (a bare `perlin` is sprudel's
 *   object, `Ignitor.perlin` the stdlib method), and the hover of a second name of a callable object (`Kat`) carries
 *   the object's call form;
 * - a closure sees a local declared after it, as the interpreter does when the closure runs;
 * - the param tools respect a local that shadows a global;
 * - a diagnostic names the call as written (`lowpass`, not `lpf`).
 *
 * A hand-built registry in the editor's order (the stdlib first, then sprudel) with the shapes KSP emits; the real
 * registries are checked in sprudel's `ParamToolArgumentSpec` and `SignalShorthandIntelSpec`.
 */
class PositionAwareIntelSpec : StringSpec({

    val number = KlangType("Number")
    val ignitorType = KlangType("Ignitor")
    val perlinType = KlangType("perlin")
    val katalystType = KlangType("Katalyst")
    val lpfType = KlangType("lpf")

    fun registry() = KlangDocsRegistry().apply {
        // stdlib, registered first
        register(
            KlangSymbol(
                name = "Ignitor",
                category = "object",
                origin = KlangSymbol.Origin.Library("stdlib"),
                variants = listOf(KlangProperty(name = "Ignitor", type = ignitorType, library = "stdlib")),
            )
        )
        register(
            KlangSymbol(
                name = "perlin",
                category = "ignitor",
                origin = KlangSymbol.Origin.Library("stdlib"),
                variants = listOf(
                    KlangCallable(
                        name = "perlin",
                        receiver = ignitorType,
                        params = listOf(KlangParam(name = "rate", type = number)),
                        description = "Ignitor perlin prose",
                        library = "stdlib",
                    )
                ),
            )
        )
        register(
            KlangSymbol(
                name = "Katalyst",
                category = "object",
                origin = KlangSymbol.Origin.Library("stdlib"),
                variants = listOf(
                    KlangProperty(name = "Katalyst", type = katalystType, description = "Katalyst object prose", library = "stdlib"),
                    KlangCallable(
                        name = "Katalyst",
                        params = listOf(KlangParam(name = "configure", type = KlangType("Function1"))),
                        description = "Katalyst call prose",
                        library = "stdlib",
                    ),
                ),
            )
        )
        register(
            KlangSymbol(
                name = "Kat",
                category = "object",
                origin = KlangSymbol.Origin.Library("stdlib"),
                variants = listOf(KlangProperty(name = "Kat", type = katalystType, library = "stdlib")),
            )
        )

        // sprudel
        register(
            KlangSymbol(
                name = "perlin",
                category = "signal",
                origin = KlangSymbol.Origin.Library("sprudel"),
                variants = listOf(
                    KlangProperty(name = "perlin", type = perlinType, description = "sprudel perlin object prose", library = "sprudel"),
                    KlangCallable(
                        name = "perlin",
                        params = listOf(KlangParam(name = "from", type = number), KlangParam(name = "to", type = number)),
                        description = "sprudel perlin call prose",
                        library = "sprudel",
                    ),
                ),
            )
        )
        register(
            KlangSymbol(
                name = "lpf",
                category = "effect",
                origin = KlangSymbol.Origin.Library("sprudel"),
                variants = listOf(
                    KlangProperty(name = "lpf", type = lpfType, library = "sprudel"),
                    KlangCallable(
                        name = "lpf",
                        params = listOf(KlangParam(name = "freq", type = number, uitools = listOf("FreqTool"))),
                        library = "sprudel",
                    ),
                ),
            )
        )
        register(
            KlangSymbol(
                name = "lowpass",
                category = "effect",
                origin = KlangSymbol.Origin.Library("sprudel"),
                variants = listOf(KlangProperty(name = "lowpass", type = lpfType, library = "sprudel")),
            )
        )
        register(
            KlangSymbol(
                name = "gain",
                category = "effect",
                origin = KlangSymbol.Origin.Library("sprudel"),
                variants = listOf(
                    KlangCallable(
                        name = "gain",
                        params = listOf(KlangParam(name = "amount", type = number, uitools = listOf("GainTool"))),
                        library = "sprudel",
                    )
                ),
            )
        )
    }

    /** A name two libraries use for a top-level function (registered first) and an object (registered second). */
    fun KlangDocsRegistry.withWave() = apply {
        register(
            KlangSymbol(
                name = "wave",
                category = "test",
                origin = KlangSymbol.Origin.Library("a"),
                variants = listOf(KlangCallable(name = "wave", params = emptyList(), library = "a")),
            )
        )
        register(
            KlangSymbol(
                name = "wave",
                category = "test",
                origin = KlangSymbol.Origin.Library("b"),
                variants = listOf(KlangProperty(name = "wave", type = KlangType("Wave"), library = "b")),
            )
        )
    }

    /** `note`: a top-level function and a pattern method, as sprudel registers it. */
    fun KlangDocsRegistry.withNote() = apply {
        register(
            KlangSymbol(
                name = "note",
                category = "test",
                origin = KlangSymbol.Origin.Library("sprudel"),
                variants = listOf(
                    KlangCallable(name = "note", params = listOf(KlangParam(name = "note", type = KlangType("String"))), library = "sprudel"),
                    KlangCallable(
                        name = "note",
                        receiver = KlangType("SprudelPattern"),
                        params = listOf(KlangParam(name = "noteName", type = KlangType("String"))),
                        library = "sprudel",
                    ),
                ),
            )
        )
    }

    fun analyze(code: String) = AnalyzedAst.build(code, registry().withWave().withNote())

    fun posOf(code: String, needle: String, nth: Int = 0): Int {
        var pos = -1

        repeat(nth + 1) { pos = code.indexOf(needle, pos + 1) }

        withClue("needle '$needle' #$nth in '$code'") { pos shouldBeGreaterThanOrEqual 0 }

        return pos
    }

    fun hover(code: String, needle: String, nth: Int = 0): KlangSymbol =
        analyze(code).symbolAt(posOf(code, needle, nth)).shouldNotBeNull()

    fun KlangSymbol.prose(): String? = variants.firstOrNull { it.description.isNotBlank() }?.description

    // ── 1. Hover prose on shared names ──────────────────────────────────────

    "a bare shared name hovers as its top-level object, not the stdlib method" {
        val symbol = hover("perlin", "perlin")

        symbol.prose() shouldBe "sprudel perlin object prose"
        symbol.variants.map { it.signature } shouldBe listOf("val perlin: perlin", "perlin(from: Number, to: Number)")
        symbol.origin shouldBe KlangSymbol.Origin.Library("sprudel")
    }

    "a called shared name hovers as its top-level object, then the call form" {
        val symbol = hover("perlin(100, 200)", "perlin")

        symbol.prose() shouldBe "sprudel perlin object prose"
        symbol.variants.map { it.signature } shouldBe listOf("val perlin: perlin", "perlin(from: Number, to: Number)")
    }

    "a member access on a known receiver keeps the receiver's method" {
        val symbol = hover("Ignitor.perlin(2)", "perlin")

        symbol.prose() shouldBe "Ignitor perlin prose"
        symbol.variants.map { it.signature } shouldBe listOf("Ignitor.perlin(rate: Number)")
    }

    "a member access on an unknown receiver shows the methods" {
        val symbol = hover("x => x.perlin(2)", "perlin")

        symbol.variants.map { it.signature } shouldBe listOf("Ignitor.perlin(rate: Number)")
    }

    "a member of a namespace import keeps the whole symbol, the top-level function it calls included" {
        val symbol = hover("import * as sp from \"sprudel\"\nsp.note(\"c\")", "note")

        symbol.variants.map { it.signature } shouldBe listOf("note(note: String)", "SprudelPattern.note(noteName: String)")
    }

    "the object comes first in a top-level hover, whatever the registration order" {
        hover("wave", "wave").variants.map { it.signature } shouldBe listOf("val wave: Wave", "wave()")
    }

    "the receiver of a member of the same name is the bare name" {
        val symbol = hover("perlin.perlin", "perlin", nth = 0)

        symbol.prose() shouldBe "sprudel perlin object prose"
    }

    // ── 2. Hover on an alias ────────────────────────────────────────────────

    "a second name of a callable object hovers with the object's call form" {
        hover("Kat", "Kat").variants.map { it.signature } shouldBe
                listOf("val Kat: Katalyst", "Katalyst(configure: Function1)")

        hover("Kat(k => k)", "Kat").variants.map { it.signature } shouldBe
                listOf("val Kat: Katalyst", "Katalyst(configure: Function1)")
    }

    "a callable object hovers with its own call form once" {
        hover("Katalyst", "Katalyst").variants.map { it.signature } shouldBe
                listOf("val Katalyst: Katalyst", "Katalyst(configure: Function1)")
    }

    // ── 3. A closure sees a local declared after it ─────────────────────────

    "a closure calling a local declared after it is checked against the local, not the global" {
        val code = "const f = () => gain(level = 1)\nconst gain = (level) => level"
        val a = analyze(code)

        a.diagnostics.shouldBeEmpty()
        a.symbolAt(posOf(code, "gain")).shouldNotBeNull().origin shouldBe KlangSymbol.Origin.Local(KlangSymbol.LocalKind.CONST)
    }

    "a deferred body sees the top level's later local through a chain of deferred bodies, eager code inside included" {
        listOf(
            "const f = () => { const g = () => gain(level = 1)\nreturn g() }\nconst gain = (level) => level",
            "const f = () => { if (true) { gain(level = 1) } }\nconst gain = (level) => level",
            "const f = () => run(() => gain(level = 1))\nconst gain = (level) => level",
            // Two scopes between the reader and the top level, the inner deferred one inside an eager argument
            "let r = 0\nconst f = () => { run(() => { const h = () => gain(level = 1)\nr = h() }) }\nconst gain = (level) => level\nf()",
        ).forEach { code ->
            withClue(code) {
                analyze(code).diagnostics.shouldBeEmpty()
            }
        }
    }

    "a helper declared in a body that runs in place sees the global: the body finishes before the later local" {
        listOf(
            "let r = 0\nif (true) { const h = () => gain(level = 1)\nr = h() }\nconst gain = (level) => level",
            "const a = (() => { const h = () => gain(level = 1)\nreturn h() })()\nconst gain = (level) => level",
            "run((x) => { const h = () => gain(level = 1)\nreturn h() })\nconst gain = (level) => level",
            "for (let i = 0; i < 2; i = i + 1) { const h = () => gain(level = 1)\nh() }\nconst gain = (level) => level",
        ).forEach { code ->
            withClue(code) {
                analyze(code).diagnostics.single().message shouldContain "Unknown parameter 'level' on 'gain'"
            }
        }
    }

    "a recursive local sees itself" {
        analyze("const gain = (level) => gain(level = level)").diagnostics.shouldBeEmpty()
    }

    "code that runs before the declaration still sees the global" {
        val straight = analyze("gain(level = 1)\nconst gain = (level) => level")

        straight.diagnostics.single().message shouldContain "Unknown parameter 'level' on 'gain'"

        // A block runs where it is written, inside a closure too
        val inBody = analyze("const f = () => { gain(level = 1)\nconst gain = (level) => level }")

        inBody.diagnostics.single().message shouldContain "Unknown parameter 'level' on 'gain'"
    }

    "an arrow that runs where it is written (a call argument, an IIFE) sees the global, as at runtime" {
        listOf(
            "run(() => gain(level = 1))\nconst gain = (level) => level",
            "const a = (() => gain(level = 1))()\nconst gain = (level) => level",
            "const a = [() => gain(level = 1)]\nconst gain = (level) => level",
        ).forEach { code ->
            withClue(code) {
                analyze(code).diagnostics.single().message shouldContain "Unknown parameter 'level' on 'gain'"
            }
        }
    }

    "an arrow returned from a body that runs in place sees the global, as on main" {
        listOf(
            "run(() => { return () => gain(level = 1) })\nconst gain = (level) => level",
            "run(() => () => gain(level = 1))\nconst gain = (level) => level",
        ).forEach { code ->
            withClue(code) {
                analyze(code).diagnostics.single().message shouldContain "Unknown parameter 'level' on 'gain'"
            }
        }
    }

    "a closure in a for body sees a later declaration of the body" {
        analyze("for (let i = 0; i < 2; i = i + 1) { const f = () => gain(level = 1)\nconst gain = (level) => level }")
            .diagnostics.shouldBeEmpty()
    }

    "a closure in a while or do-while body sees a later declaration of the body" {
        listOf(
            "let i = 0\nwhile (i < 1) { const f = () => gain(level = 1)\nconst gain = (level) => level\nf()\ni = i + 1 }",
            "let i = 0\ndo { const f = () => gain(level = 1)\nconst gain = (level) => level\nf()\ni = i + 1 } while (i < 1)",
        ).forEach { code ->
            withClue(code) {
                analyze(code).diagnostics.shouldBeEmpty()
            }
        }
    }

    "a closure sees a later export" {
        analyze("const f = () => gain(level = 1)\nexport gain = (level) => level").diagnostics.shouldBeEmpty()
    }

    "a later local read from a closure takes the type of its declaration" {
        val code = "const f = () => d(800)\nconst d = lpf"
        val a = analyze(code)

        a.symbolAt(posOf(code, "d(")).shouldNotBeNull().variants.map { it.signature } shouldBe listOf("val d: lpf")
        a.argumentAt(posOf(code, "800")).shouldNotBeNull().binding.param.uitools shouldBe listOf("FreqTool")

        analyze("const f = () => d(fre = 800)\nconst d = lpf").diagnostics.single().message shouldContain
                "Unknown parameter 'fre' on 'd'"
    }

    "the runtime agrees: a closure run after the declaration calls the local, code before it the global" {
        fun run(code: String): Double {
            val engine = klangScriptEngine {
                registerFunctionRaw("gain") { _, _ -> NumberValue(1.0) }
            }

            return engine.execute(code).shouldBeInstanceOf<NumberValue>().value
        }

        run("const f = () => gain()\nconst gain = () => 2\nf()") shouldBe 2.0
        run("const before = gain()\nconst gain = () => 2\nbefore") shouldBe 1.0
        // An arrow that runs where it is written: a call argument, an IIFE
        run("const run = (f) => f()\nconst a = run(() => gain())\nconst gain = () => 2\na") shouldBe 1.0
        run("const a = (() => gain())()\nconst gain = () => 2\na") shouldBe 1.0
        // A helper declared and called in a body that runs in place: the body finishes before the later local
        run("const a = (() => { const h = () => gain()\nreturn h() })()\nconst gain = () => 2\na") shouldBe 1.0
        run("let r = 0\nif (true) { const h = () => gain()\nr = h() }\nconst gain = () => 2\nr") shouldBe 1.0
        // A chain of deferred bodies run after the declaration: the local
        run("const f = () => { const g = () => gain()\nreturn g() }\nconst gain = () => 2\nf()") shouldBe 2.0
        // A helper inside an eager argument inside a deferred body, run after the declaration: the local
        run("const run = (x) => x()\nlet r = 0\nconst f = () => { run(() => { const h = () => gain()\nr = h() }) }\nconst gain = () => 2\nf()\nr") shouldBe 2.0
    }

    // ── 4. The param tools respect local bindings ───────────────────────────

    "a local shadows the global for the param tools" {
        val global = "gain(0.5)"
        analyze(global).argumentAt(posOf(global, "0.5")).shouldNotBeNull().binding.param.uitools shouldBe listOf("GainTool")

        val shadowed = "const gain = (a) => a\ngain(0.5)"
        analyze(shadowed).argumentAt(posOf(shadowed, "0.5")).shouldBeNull()
    }

    "a local holding a callable object binds through the object" {
        val code = "let d = lpf\nd(800)"
        val found = analyze(code).argumentAt(posOf(code, "800")).shouldNotBeNull()

        found.symbol.name shouldBe "lpf"
        found.binding.param.uitools shouldBe listOf("FreqTool")
    }

    "a second name of a callable object binds through the object for the param tools" {
        val code = "lowpass(800)"
        val found = analyze(code).argumentAt(posOf(code, "800")).shouldNotBeNull()

        found.symbol.name shouldBe "lpf"
        found.binding.param.uitools shouldBe listOf("FreqTool")
    }

    // ── 5. The diagnostic names the call as written ─────────────────────────

    "a diagnostic names the call as written, not the object it resolves to" {
        analyze("lowpass(fre = 800)").diagnostics.single().message shouldContain "Unknown parameter 'fre' on 'lowpass'"
        analyze("lpf(fre = 800)").diagnostics.single().message shouldContain "on 'lpf'"
        analyze("Kat(configur = 1)").diagnostics.single().message shouldContain "on 'Kat'"
    }
})

/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.script.intel

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.peekandpoke.klang.script.ast.ArrowFunction
import io.peekandpoke.klang.script.ast.ArrowFunctionBody
import io.peekandpoke.klang.script.ast.CallExpression
import io.peekandpoke.klang.script.ast.ExpressionStatement
import io.peekandpoke.klang.script.ast.Identifier
import io.peekandpoke.klang.script.ast.MemberAccess
import io.peekandpoke.klang.script.docs.KlangDocsRegistry
import io.peekandpoke.klang.script.types.KlangCallable
import io.peekandpoke.klang.script.types.KlangParam
import io.peekandpoke.klang.script.types.KlangProperty
import io.peekandpoke.klang.script.types.KlangSymbol
import io.peekandpoke.klang.script.types.KlangType

/**
 * The analyzer's side of a callable object: `Katalyst(k => k.gain(2))` resolves to the object's call
 * form, the receiver-less callable named after the object on the object's own symbol (the shape KSP
 * emits), so the call's return type, the typed lambda parameter and the popup signature all work, and
 * a value holding the object (`Kat`) is callable through its type. Hand-built registry, no stdlib:
 * this is language machinery.
 */
class InvokeAnalysisTest : StringSpec({

    val katalystType = KlangType("Katalyst", fqcn = "test.Katalyst")
    val builderType = KlangType("KatalystBuilder", fqcn = "test.KatalystBuilder")
    val dslType = KlangType("KatalystDsl", fqcn = "test.KatalystDsl")
    val configureType = KlangType(
        "Function1", fqcn = "kotlin.Function1", isNullable = true,
        functionParams = listOf(builderType), functionReturn = builderType,
    )

    fun registry(): KlangDocsRegistry = KlangDocsRegistry().apply {
        registerAll(listOf(
            KlangSymbol(
                name = "Katalyst", category = "katalyst", origin = KlangSymbol.Origin.Library("test"),
                variants = listOf(
                    KlangProperty(name = "Katalyst", type = katalystType, library = "test"),
                    KlangCallable(
                        name = "Katalyst",
                        params = listOf(KlangParam(name = "configure", type = configureType, isOptional = true)),
                        returnType = dslType,
                        library = "test",
                    ),
                ),
            ),
            // A second name for the object: a constant of the object's type, no call form of its own
            KlangSymbol(
                name = "Kat", category = "katalyst", origin = KlangSymbol.Origin.Library("test"),
                variants = listOf(KlangProperty(name = "Kat", type = katalystType, library = "test")),
            ),
            // A callable object named like a value's type but of another type: `five()` is no call of it
            KlangSymbol(
                name = "Number", category = "katalyst", origin = KlangSymbol.Origin.Library("test"),
                variants = listOf(
                    KlangProperty(name = "Number", type = KlangType("Number", fqcn = "test.NumberFactory"), library = "test"),
                    KlangCallable(name = "Number", params = emptyList(), returnType = dslType, library = "test"),
                ),
            ),
            KlangSymbol(
                name = "five", category = "katalyst", origin = KlangSymbol.Origin.Library("test"),
                variants = listOf(KlangProperty(name = "five", type = KlangType("Number", fqcn = "kotlin.Double"), library = "test")),
            ),
            // A plain top-level function, for the shadowing rows
            KlangSymbol(
                name = "note", category = "katalyst", origin = KlangSymbol.Origin.Library("test"),
                variants = listOf(
                    KlangCallable(name = "note", params = listOf(KlangParam(name = "n", type = KlangType("Number"))), returnType = dslType, library = "test"),
                ),
            ),
            // An operator symbol registered on the object's type, as a hand registration could: never offered
            KlangSymbol(
                name = "__invoke__", category = "katalyst", origin = KlangSymbol.Origin.Library("test"),
                variants = listOf(KlangCallable(name = "__invoke__", receiver = katalystType, params = emptyList(), library = "test")),
            ),
            // One library's object and another library's top-level function of the same name: no call form
            KlangSymbol(
                name = "mixed", category = "katalyst", origin = KlangSymbol.Origin.Library("a"),
                variants = listOf(
                    KlangProperty(name = "mixed", type = KlangType("mixed", fqcn = "a.mixed"), library = "a"),
                    KlangCallable(name = "mixed", params = emptyList(), returnType = dslType, library = "b"),
                ),
            ),
            KlangSymbol(
                name = "classic", category = "katalyst", origin = KlangSymbol.Origin.Library("test"),
                variants = listOf(KlangCallable(name = "classic", receiver = katalystType, params = emptyList(), returnType = dslType)),
            ),
            KlangSymbol(
                name = "gain", category = "katalyst", origin = KlangSymbol.Origin.Library("test"),
                variants = listOf(
                    KlangCallable(
                        name = "gain", receiver = builderType,
                        params = listOf(KlangParam(name = "gain", type = KlangType("Number"))),
                        returnType = builderType,
                    )
                ),
            ),
        ))
    }

    fun analyze(code: String) = AnalyzedAst.build(code, registry())
    fun AnalyzedAst.top() = (ast.statements.first() as ExpressionStatement).expression

    "Katalyst() resolves through its call form to the object's return type" {
        val a = analyze("Katalyst()")
        a.typeOf(a.top())?.simpleName shouldBe "KatalystDsl"
    }

    "a second name for the object (Kat) is callable through the type's call form" {
        analyze("Kat()").let { it.typeOf(it.top())?.simpleName shouldBe "KatalystDsl" }
        registry().getCallForm(katalystType).shouldNotBeNull().name shouldBe "Katalyst"

        val code = "Kat(k => k.gain(2))"
        analyze(code).receiverTypeBeforeDot(code.indexOf("k.gain") + 1)?.simpleName shouldBe "KatalystBuilder"
    }

    "a local holding the object is callable through its type's call form: type and named-argument checks" {
        val a = analyze("let d = Katalyst\nd()")
        val call = (a.ast.statements[1] as ExpressionStatement).expression
        a.typeOf(call)?.simpleName shouldBe "KatalystDsl"

        analyze("let d = Katalyst\nd(configur = k => k)").diagnostics.size shouldBe 1
        analyze("let d = Katalyst\nd(configure = k => k)").diagnostics.size shouldBe 0
    }

    "a local named like a global never resolves to the global: no type, no false named-argument error" {
        analyze("const Katalyst = (y) => y\nKatalyst(y = 1)").diagnostics.size shouldBe 0
        analyze("const note = (x) => x\nnote(x = 1)").diagnostics.size shouldBe 0
        // the global itself is still checked
        analyze("note(x = 1)").diagnostics.size shouldBe 1
        val shadowed = analyze("const Katalyst = (y) => y\nKatalyst(1)")
        shadowed.typeOf((shadowed.ast.statements[1] as ExpressionStatement).expression).shouldBeNull()
    }

    "a type named otherwise (a cross-module reference) finds the call form by its FQCN" {
        registry().getCallForm(KlangType("KlangScriptKatalyst", fqcn = "test.Katalyst")).shouldNotBeNull().name shouldBe "Katalyst"
        registry().getCallForm(KlangType("KlangScriptKatalyst", fqcn = "test.Other")).shouldBeNull()
    }

    "the docs card of a second name shows the object's call form" {
        val reg = registry()
        reg.callFormFor(reg.get("Kat")!!).shouldNotBeNull().name shouldBe "Katalyst"
        reg.callFormFor(reg.get("Katalyst")!!) shouldBe reg.get("Katalyst")!!.callForm
        reg.callFormFor(reg.get("note")!!).shouldBeNull()
    }

    "a same-named symbol that is not the object of the queried type gives no call form" {
        registry().getCallForm(KlangType("Number", fqcn = "kotlin.Double")).shouldBeNull()
        analyze("five()").let { it.typeOf(it.top()).shouldBeNull() }
        // the object itself is callable
        registry().getCallForm(KlangType("Number", fqcn = "test.NumberFactory")).shouldNotBeNull()
    }

    "a top-level function of another library is no call form of this library's object" {
        registry().get("mixed")!!.callForm.shouldBeNull()
    }

    "hover on the object shows both forms, the object first, then the call" {
        val code = "Katalyst()"
        val symbol = analyze(code).symbolAt(1).shouldNotBeNull()
        symbol.variants.map { it.signature } shouldBe listOf(
            "val Katalyst: Katalyst",
            "Katalyst(configure: ((KatalystBuilder) -> KatalystBuilder)?): KatalystDsl",
        )
        symbol.callForm.shouldNotBeNull().name shouldBe "Katalyst"
    }

    "top-level completion offers the object once" {
        val names = CompletionProvider(registry()).topLevelCompletions("Kata").map { it.name }
        names shouldBe listOf("Katalyst")
    }

    "a re-registration keeps both forms (the merge tells a property from a callable of the same name)" {
        val reg = registry()
        reg.register(reg.get("Katalyst")!!)
        reg.get("Katalyst")!!.variants.size shouldBe 2
    }

    "Katalyst(k => k.gain(2)) types the lambda parameter as the builder" {
        val code = "Katalyst(k => k.gain(2))"
        val a = analyze(code)
        val call = a.top() as CallExpression
        val arrow = call.arguments.single().value as ArrowFunction
        val body = (arrow.body as ArrowFunctionBody.ExpressionBody).expression as CallExpression
        val k = (body.callee as MemberAccess).obj as Identifier
        a.typeOf(k)?.simpleName shouldBe "KatalystBuilder"
        a.typeOf(body)?.simpleName shouldBe "KatalystBuilder"
        a.receiverTypeBeforeDot(code.indexOf("k.gain") + 1)?.simpleName shouldBe "KatalystBuilder"
    }

    "a plain method on the object still resolves ahead of invoke" {
        val a = analyze("Katalyst.classic()")
        a.typeOf(a.top())?.simpleName shouldBe "KatalystDsl"
    }

    "an object without invoke is not a callable for the analyzer" {
        val reg = registry()
        val a = AnalyzedAst.build("gain(2)", reg)  // `gain` is a builder method, not an object
        a.typeOf(a.top()).shouldBeNull()
    }

    "the call form's signature renders as the call the user writes" {
        val callForm = registry().getCallable("Katalyst", receiverType = null)
        callForm.shouldNotBeNull()
        callForm.signature shouldBe "Katalyst(configure: ((KatalystBuilder) -> KatalystBuilder)?): KatalystDsl"
    }

    "named-argument diagnostics reach a callable object through its call form" {
        val a = analyze("Katalyst(configur = k => k.gain(2))")
        a.diagnostics.size shouldBe 1
        a.diagnostics.single().message shouldContain "Unknown parameter 'configur'"
        a.diagnostics.single().message shouldContain "configure"
        // and a correct call is clean
        analyze("Katalyst(configure = k => k.gain(2))").diagnostics.size shouldBe 0
    }

    "the call form is no member: completions after the dot offer the methods only" {
        val names = CompletionProvider(registry()).memberCompletions(katalystType, "").map { it.name }
        names shouldNotContain "Katalyst"
        names shouldNotContain "__invoke__"
        names shouldBe listOf("classic")
    }
})

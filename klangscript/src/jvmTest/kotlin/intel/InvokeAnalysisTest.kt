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
 * The analyzer's side of the `invoke` operator: `Master(m => m.gain(2))` resolves to the
 * `invoke` callable registered on the object's type, so the call's return type, the typed
 * lambda parameter and the popup signature all work, while `invoke` never shows up as a
 * member completion. Hand-built registry, no stdlib: this is language machinery.
 */
class InvokeAnalysisTest : StringSpec({

    val masterType = KlangType("Master", fqcn = "test.Master")
    val builderType = KlangType("MasterBuilder", fqcn = "test.MasterBuilder")
    val dslType = KlangType("MasterDsl", fqcn = "test.MasterDsl")
    val configureType = KlangType(
        "Function1", fqcn = "kotlin.Function1", isNullable = true,
        functionParams = listOf(builderType), functionReturn = builderType,
    )

    fun registry(): KlangDocsRegistry = KlangDocsRegistry().apply {
        registerAll(listOf(
            KlangSymbol(
                name = "Master", category = "master", origin = KlangSymbol.Origin.Library("test"),
                variants = listOf(KlangProperty(name = "Master", type = masterType)),
            ),
            KlangSymbol(
                name = "invoke", category = "master", origin = KlangSymbol.Origin.Library("test"),
                variants = listOf(
                    KlangCallable(
                        name = "invoke", receiver = masterType,
                        params = listOf(KlangParam(name = "configure", type = configureType, isOptional = true)),
                        returnType = dslType,
                    )
                ),
            ),
            KlangSymbol(
                name = "default", category = "master", origin = KlangSymbol.Origin.Library("test"),
                variants = listOf(KlangCallable(name = "default", receiver = masterType, params = emptyList(), returnType = dslType)),
            ),
            KlangSymbol(
                name = "gain", category = "master", origin = KlangSymbol.Origin.Library("test"),
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

    "Master() resolves through invoke to the object's return type" {
        val a = analyze("Master()")
        a.typeOf(a.top())?.simpleName shouldBe "MasterDsl"
    }

    "Master(m => m.gain(2)) types the lambda parameter as the builder" {
        val code = "Master(m => m.gain(2))"
        val a = analyze(code)
        val call = a.top() as CallExpression
        val arrow = call.arguments.single().value as ArrowFunction
        val body = (arrow.body as ArrowFunctionBody.ExpressionBody).expression as CallExpression
        val m = (body.callee as MemberAccess).obj as Identifier
        a.typeOf(m)?.simpleName shouldBe "MasterBuilder"
        a.typeOf(body)?.simpleName shouldBe "MasterBuilder"
        a.receiverTypeBeforeDot(code.indexOf("m.gain") + 1)?.simpleName shouldBe "MasterBuilder"
    }

    "a plain method on the object still resolves ahead of invoke" {
        val a = analyze("Master.default()")
        a.typeOf(a.top())?.simpleName shouldBe "MasterDsl"
    }

    "an object without invoke is not a callable for the analyzer" {
        val reg = registry()
        val a = AnalyzedAst.build("gain(2)", reg)  // `gain` is a builder method, not an object
        a.typeOf(a.top()).shouldBeNull()
    }

    "the invoke signature renders as the call the user writes" {
        val invoke = registry().getCallable("invoke", masterType)
        invoke.shouldNotBeNull()
        invoke.signature shouldBe "Master(configure: ((MasterBuilder) -> MasterBuilder)?): MasterDsl"
    }

    "named-argument diagnostics reach a callable object through invoke" {
        val a = analyze("Master(configur = m => m.gain(2))")
        a.diagnostics.size shouldBe 1
        a.diagnostics.single().message shouldContain "Unknown parameter 'configur'"
        a.diagnostics.single().message shouldContain "configure"
        // and a correct call is clean
        analyze("Master(configure = m => m.gain(2))").diagnostics.size shouldBe 0
    }

    "invoke is hidden from member completions after the dot" {
        val names = CompletionProvider(registry()).memberCompletions(masterType, "").map { it.name }
        names shouldNotContain "invoke"
        names shouldBe listOf("default")
    }
})

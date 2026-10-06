/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.script.intel

import io.kotest.assertions.throwables.shouldThrowAny
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.kotest.matchers.string.shouldStartWith
import io.peekandpoke.klang.script.ast.ExpressionStatement
import io.peekandpoke.klang.script.docs.KlangDocsRegistry
import io.peekandpoke.klang.script.generated.generatedStdlibDocs
import io.peekandpoke.klang.script.klangScript
import io.peekandpoke.klang.script.runtime.KlangScriptError
import io.peekandpoke.klang.script.types.KlangCallable
import io.peekandpoke.klang.script.types.KlangProperty

/**
 * The stdlib's one callable object, `Katalyst`, shows both its forms (`docs/tasks/callable-object-docs.md`),
 * against the real generated stdlib registry, on every platform: the symbol carries `val Katalyst: Katalyst`
 * and `Katalyst(configure)`, hover shows both, completion offers the name once, `Katalyst(` and `Kat(` resolve
 * to the call form, no symbol is named after the call operator, and a runtime error at the call names
 * `Katalyst`, never the internal `__invoke__`.
 */
class KatalystCallFormSpec : StringSpec({

    val registry = KlangDocsRegistry().apply { registerAll(generatedStdlibDocs) }

    fun analyze(code: String) = AnalyzedAst.build(code, registry)
    fun AnalyzedAst.top() = (ast.statements.first() as ExpressionStatement).expression

    "the Katalyst symbol carries the object first and its call form second" {
        val symbol = generatedStdlibDocs["Katalyst"].shouldNotBeNull()
        val obj = symbol.variants[0] as KlangProperty
        val call = symbol.variants[1] as KlangCallable

        obj.signature shouldBe "val Katalyst: Katalyst"
        call.receiver.shouldBeNull()
        call.signature shouldStartWith "Katalyst(configure"
        call.returnType.shouldNotBeNull().simpleName shouldBe "KatalystDsl"
        symbol.callForm shouldBe call
    }

    "the stdlib has exactly one callable object, and no symbol named after the call operator" {
        generatedStdlibDocs.values.filter { it.callForm != null }.map { it.name } shouldBe listOf("Katalyst")
        generatedStdlibDocs.keys shouldNotContain "invoke"
        generatedStdlibDocs.keys shouldNotContain "__invoke__"
    }

    "hover on Katalyst shows both signatures" {
        val symbol = analyze("Katalyst(k => k.gain(2))").symbolAt(1).shouldNotBeNull()
        symbol.variants.filter { it is KlangProperty && it.owner == null || it is KlangCallable && it.receiver == null }
            .map { it.signature.substringBefore("(configure") } shouldBe listOf("val Katalyst: Katalyst", "Katalyst")
    }

    "completion offers Katalyst once, as the object" {
        val hits = CompletionProvider(registry).topLevelCompletions("Katalyst").filter { it.name == "Katalyst" }
        hits.size shouldBe 1
        hits.single().kind shouldBe CompletionSuggestion.Kind.PROPERTY
    }

    "Katalyst( and Kat( resolve to the call form: the return type and the typed lambda parameter" {
        for (name in listOf("Katalyst", "Kat")) {
            val code = "$name(k => k.gain(2))"
            val a = analyze(code)
            a.diagnostics.size shouldBe 0
            a.typeOf(a.top()).shouldNotBeNull().simpleName shouldBe "KatalystDsl"
            a.receiverTypeBeforeDot(code.indexOf("k.gain") + 1).shouldNotBeNull().simpleName shouldBe "KatalystBuilder"
        }
    }

    "the call form is no member: completion after Katalyst. offers the object's methods only" {
        val type = generatedStdlibDocs["Katalyst"].shouldNotBeNull().variants.filterIsInstance<KlangProperty>().single().type
        val members = CompletionProvider(registry).memberCompletions(type, "").map { it.name }
        members shouldNotContain "Katalyst"
        members shouldNotContain "invoke"
        members shouldNotContain "__invoke__"
    }

    "a wrong argument at Katalyst( is reported for Katalyst, never for the internal symbol" {
        val engine = klangScript()
        engine.execute("import * from \"stdlib\"")
        val error = shouldThrowAny { engine.execute("Katalyst(configur = k => k)") }
        val text = (error as KlangScriptError).format()
        text shouldContain "in Katalyst: unknown parameter 'configur'"
        text shouldNotContain "invoke"
    }

    "the docs card of Kat shows the Katalyst call form, and the call form documents configure" {
        val page = KlangDocsRegistry().apply { registerAll(generatedStdlibDocs) }
        val callForm = page.get("Katalyst").shouldNotBeNull().callForm.shouldNotBeNull()

        page.callFormFor(page.get("Kat").shouldNotBeNull()) shouldBe callForm
        callForm.params.single().description shouldBe "receives the [KatalystBuilder] and returns it."
    }
})


/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.sprudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldStartWith
import io.peekandpoke.klang.script.ast.ExpressionStatement
import io.peekandpoke.klang.script.docs.KlangDocsRegistry
import io.peekandpoke.klang.script.generated.generatedSprudelDocs
import io.peekandpoke.klang.script.intel.AnalyzedAst
import io.peekandpoke.klang.script.intel.CompletionProvider
import io.peekandpoke.klang.script.types.KlangProperty

/**
 * The editor's view of the `freq` accessor, against the real generated sprudel registry:
 * bare `freq` is a typed constant, `freq(...)` resolves through `invoke`, and `freq.` offers the
 * first-step operators registered on `PatternMapperProvider`.
 */
class FreqAccessorIntelSpec : StringSpec({

    val registry = KlangDocsRegistry().apply { registerAll(generatedSprudelDocs) }

    fun analyze(code: String) = AnalyzedAst.build(code, registry)
    fun AnalyzedAst.top() = (ast.statements.first() as ExpressionStatement).expression

    val freqType = registry.get("freq").shouldNotBeNull().variants.filterIsInstance<KlangProperty>().single().type

    "bare freq is an object whose type displays as freq" {
        freqType.simpleName shouldBe "freq"
    }

    "the object's KDoc examples reach the registry as samples" {
        val prop = registry.get("freq").shouldNotBeNull().variants.filterIsInstance<KlangProperty>().single()
        prop.samples.size shouldBe 2
        prop.samples.first().code shouldContain "bpf(freq)"
    }

    "freq(440) resolves through invoke and the signature renders as the call the user writes" {
        val invoke = registry.getCallable("invoke", freqType).shouldNotBeNull()
        invoke.signature shouldStartWith "freq(hz"

        val a = analyze("freq(440)")
        a.typeOf(a.top()).shouldNotBeNull()
        a.diagnostics.size shouldBe 0
    }

    "freq.mul(2) is typed through the provider supertype" {
        val code = "freq.mul(2)"
        val a = analyze(code)
        a.receiverTypeBeforeDot(code.indexOf(".mul"))?.simpleName shouldBe "freq"
        a.typeOf(a.top()).shouldNotBeNull()
    }

    "completions after freq. offer the first-step operators and hide invoke" {
        val names = CompletionProvider(registry).memberCompletions(freqType, "").map { it.name }
        names shouldContainAll listOf("add", "sub", "mul", "div")
        names shouldNotContain "invoke"
    }

    "every batch-one accessor is an object with a call form and the first-step operators" {
        listOf("gain", "velocity", "pan", "postgain", "lpf", "hpf", "bpf", "lpq", "hpq", "bpq",
            "attack", "decay", "sustain", "release").forEach { name ->
            val type = registry.get(name).shouldNotBeNull().variants.filterIsInstance<KlangProperty>().single().type
            type.simpleName shouldBe name
            registry.getCallable("invoke", type).shouldNotBeNull().signature shouldStartWith "$name("
            CompletionProvider(registry).memberCompletions(type, "").map { it.name } shouldContainAll listOf("add", "sub", "mul", "div")
            analyze("$name(0.5)").diagnostics.size shouldBe 0
            analyze("$name.mul(2)").typeOf(analyze("$name.mul(2)").top()).shouldNotBeNull()
        }
    }

    "named-argument diagnostics reach the setter through invoke" {
        val bad = analyze("freq(hzz = 440)")
        bad.diagnostics.size shouldBe 1
        bad.diagnostics.single().message shouldContain "Unknown parameter 'hzz'"
        analyze("freq(hz = 440)").diagnostics.size shouldBe 0
    }
})

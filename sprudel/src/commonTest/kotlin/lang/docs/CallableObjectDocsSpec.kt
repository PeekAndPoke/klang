/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.sprudel.lang.docs

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotBeBlank
import io.kotest.matchers.string.shouldStartWith
import io.peekandpoke.klang.script.ast.ExpressionStatement
import io.peekandpoke.klang.script.docs.KlangDocsRegistry
import io.peekandpoke.klang.script.generated.generatedSprudelDocs
import io.peekandpoke.klang.script.generated.generatedStdlibDocs
import io.peekandpoke.klang.script.intel.AnalyzedAst
import io.peekandpoke.klang.script.intel.CompletionProvider
import io.peekandpoke.klang.script.intel.CompletionSuggestion
import io.peekandpoke.klang.script.types.KlangCallable
import io.peekandpoke.klang.script.types.KlangProperty
import io.peekandpoke.klang.script.types.parametersByName

/**
 * A callable object shows both its forms (`docs/tasks-archive/2026-10/20261007-callable-object-docs.md`), against the real generated
 * registries, the way the editor builds them (stdlib, then sprudel), on every platform: the object's own symbol
 * carries the object (`perlin: perlin`) and its call form (`perlin(from, to)`), hover shows both, completion
 * offers the name once, `perlin(` resolves to the call form, and no symbol is named after the call operator.
 */
class CallableObjectDocsSpec : StringSpec({

    val registry = KlangDocsRegistry().apply {
        registerAll(generatedStdlibDocs)
        registerAll(generatedSprudelDocs)
    }

    fun analyze(code: String) = AnalyzedAst.build(code, registry)
    fun AnalyzedAst.top() = (ast.statements.first() as ExpressionStatement).expression

    /** The object, its call, and the first parameter of the call form. */
    val cases = listOf(
        Triple("perlin", "perlin(200, 400)", "from"),
        Triple("adsr", "adsr(0.01, 0.1, 0.5, 0.2)", "attack"),
        Triple("duck", "duck(1, 0.5)", "orbit"),
    )

    "no docs symbol is named after the call operator, in either library" {
        for (docs in listOf(generatedSprudelDocs, generatedStdlibDocs)) {
            docs.keys shouldNotContain "invoke"
            docs.keys shouldNotContain "__invoke__"
            docs.values.flatMap { it.variants }.filterIsInstance<KlangCallable>().map { it.name } shouldNotContain "__invoke__"
        }
    }

    "every callable object's symbol carries the object first and its call form second" {
        val callables = generatedSprudelDocs.values.filter { it.callForm != null }
        // 51 callable objects in sprudel on 2026-10-07; the count guards that none lost its call form
        callables.size shouldBe 51

        for (symbol in callables) {
            withClue(symbol.name) {
                val obj = symbol.variants[0] as KlangProperty
                val call = symbol.variants[1] as KlangCallable
                obj.name shouldBe symbol.name
                obj.owner.shouldBeNull()
                call.name shouldBe symbol.name
                call.receiver.shouldBeNull()
                call.library shouldBe obj.library
                symbol.callForm shouldBe call
                registry.getCallForm(obj.type) shouldBe call
            }
        }
    }

    "hover on the object shows both signatures, the object first, then the call" {
        for ((name, code, firstParam) in cases) {
            withClue(name) {
                val symbol = analyze(code).symbolAt(1).shouldNotBeNull()
                val signatures = symbol.variants.filter { it is KlangProperty && it.owner == null || it is KlangCallable && it.receiver == null }
                    .map { it.signature }
                signatures.size shouldBe 2
                signatures[0] shouldBe "val $name: $name"
                signatures[1] shouldStartWith "$name($firstParam"
            }
        }
    }

    "completion offers the object once, as the object" {
        for ((name, _, _) in cases) {
            withClue(name) {
                val hits = CompletionProvider(registry).topLevelCompletions(name).filter { it.name == name }
                hits.size shouldBe 1
                hits.single().kind shouldBe CompletionSuggestion.Kind.PROPERTY
            }
        }
    }

    "the call resolves to the call form: its signature, its return type, no diagnostics" {
        for ((name, code, firstParam) in cases) {
            withClue(name) {
                val a = analyze(code)
                a.diagnostics.size shouldBe 0
                val call = a.top()
                val callForm = registry.getCallable(name, receiverType = null).shouldNotBeNull()
                callForm.signature shouldStartWith "$name($firstParam"
                a.typeOf(call) shouldBe callForm.returnType
            }
        }
    }

    "a named argument the call form does not have is reported at the call" {
        analyze("duck(orbitt = 1)").diagnostics.size shouldBe 1
        analyze("duck(orbit = 1)").diagnostics.size shouldBe 0
    }

    "the call form is no member: completion after the dot offers the object's children only" {
        for ((name, _, _) in cases) {
            withClue(name) {
                val type = registry.get(name).shouldNotBeNull().variants.filterIsInstance<KlangProperty>().first { it.owner == null }.type
                val members = CompletionProvider(registry).memberCompletions(type, "").map { it.name }
                members shouldNotContain "invoke"
                members shouldNotContain "__invoke__"
            }
        }
    }

    "every call form documents each of its parameters" {
        for (symbol in generatedSprudelDocs.values) {
            val callForm = symbol.callForm ?: continue

            for (param in callForm.params) {
                withClue("${symbol.name}(${param.name})") { param.description.shouldNotBeBlank() }
            }
        }
    }

    "the docs card of a second name shows the object's call form (the docs page's own registry)" {
        val page = KlangDocsRegistry().apply { registerAll(generatedSprudelDocs) }
        mapOf(
            "lowpass" to "lpf", "highpass" to "hpf", "bandpass" to "bpf", "comp" to "compressor", "uni" to "unison",
            "vib" to "vibrato", "pamt" to "penv", "vel" to "velocity", "o" to "orbit", "d" to "density", "clip" to "legato",
        ).forEach { (alias, canonical) ->
            withClue(alias) {
                page.callFormFor(page.get(alias).shouldNotBeNull()) shouldBe page.get(canonical).shouldNotBeNull().callForm.shouldNotBeNull()
            }
        }
    }

    "the hover's parameter table keeps a description a later variant gives (pan)" {
        val symbol = registry.get("pan").shouldNotBeNull()
        val sorted = symbol.variants.sortedBy {
            when (it) {
                is KlangCallable -> it.receiver?.simpleName?.length ?: 0
                is KlangProperty -> it.owner?.simpleName?.length ?: 0
            }
        }
        sorted.parametersByName().single { it.name == "amount" }.description shouldBe "Pan position, 0 left to 1 right."
    }

    "a local named like a callable object is never the object: no false named-argument error" {
        analyze("const gain = (amount) => amount\ngain(level = 1)").diagnostics.size shouldBe 0
        analyze("let speed = (x) => x * 2\nspeed(x = 3)").diagnostics.size shouldBe 0
        analyze("note(\"c\").superimpose(pan => pan(level = 1))").diagnostics.size shouldBe 0
    }

    "a bare name is its top-level object, and a local holding it calls its call form (duck)" {
        val code = "duck.depth"
        analyze(code).receiverTypeBeforeDot(code.indexOf(".depth")).shouldNotBeNull().simpleName shouldBe "duck"
        val type = analyze("duck").let { it.typeOf(it.top()) }.shouldNotBeNull()
        CompletionProvider(registry).memberCompletions(type, "").map { it.name } shouldContain "depth"

        analyze("let d = duck\nd(orbitt = 1)").diagnostics.size shouldBe 1
        analyze("let d = duck\nd(orbit = 1)").diagnostics.size shouldBe 0
    }

    "a name that is its own object is offered once, not again as another symbol's alias" {
        for (name in listOf("density", "orbit", "velocity")) {
            withClue(name) {
                CompletionProvider(registry).topLevelCompletions(name).count { it.name == name } shouldBe 1
            }
        }
    }
})


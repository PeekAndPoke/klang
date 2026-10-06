/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.sprudel.lang

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotBeBlank
import io.kotest.matchers.string.shouldStartWith
import io.peekandpoke.klang.script.ast.ExpressionStatement
import io.peekandpoke.klang.script.docs.KlangDocsRegistry
import io.peekandpoke.klang.script.generated.generatedSprudelDocs
import io.peekandpoke.klang.script.intel.AnalyzedAst
import io.peekandpoke.klang.script.intel.CompletionProvider
import io.peekandpoke.klang.script.intel.CompletionSuggestion
import io.peekandpoke.klang.script.types.KlangCallable
import io.peekandpoke.klang.script.types.KlangProperty

/**
 * The editor's and the docs page's view of the eight signals, against the real generated sprudel registry: each is an
 * object whose type carries `SprudelPattern`, so `perlin.` completes the pattern methods, and `perlin(` shows the
 * range shorthand: the call form is the second variant of the signal's own symbol (KSP), so hover and the docs page
 * show both forms on one card.
 */
class SignalShorthandIntelSpec : StringSpec({

    val registry = KlangDocsRegistry().apply { registerAll(generatedSprudelDocs) }

    fun analyze(code: String) = AnalyzedAst.build(code, registry)
    fun AnalyzedAst.top() = (ast.statements.first() as ExpressionStatement).expression

    val names = listOf("sine", "cosine", "saw", "tri", "square", "perlin", "berlin", "rand")

    "every signal is a documented object that is a pattern, with its call form on its own card" {
        for (name in names) {
            withClue(name) {
                val symbol = registry.get(name).shouldNotBeNull()
                val prop = symbol.variants.filterIsInstance<KlangProperty>().single { it.owner == null }

                prop.type.simpleName shouldBe name
                prop.type.supertypes.map { it.simpleName } shouldContain "SprudelPattern"
                prop.samples.isNotEmpty() shouldBe true

                val callForm = registry.getCallForm(prop.type).shouldNotBeNull()
                callForm.signature shouldStartWith "$name(from"
                callForm.receiver.shouldBeNull()
                symbol.callForm shouldBe callForm
                symbol.variants.map { it.signature } shouldBe listOf("val $name: $name", callForm.signature)
            }
        }
    }

    "completions after a signal offer the pattern methods as functions with their docs, and no call form" {
        for (name in names) {
            withClue(name) {
                val type = registry.get(name).shouldNotBeNull()
                    .variants.filterIsInstance<KlangProperty>().single { it.owner == null }.type
                val suggestions = CompletionProvider(registry).memberCompletions(type, "")
                val members = suggestions.map { it.name }

                members shouldContainAll listOf("slow", "range", "rangex", "segment", "add")
                members shouldNotContain "__invoke__"

                // Found through the SprudelPattern supertype, they show as functions with their docs, the same
                // suggestion a plain pattern gets
                val patternType = registry.get("seq").shouldNotBeNull()
                    .variants.filterIsInstance<KlangCallable>().first { it.receiver == null }.returnType.shouldNotBeNull()
                val onPattern = CompletionProvider(registry).memberCompletions(patternType, "").associateBy { it.name }

                for (method in listOf("slow", "range", "segment")) {
                    withClue(method) {
                        val suggestion = suggestions.single { it.name == method }
                        suggestion.kind shouldBe CompletionSuggestion.Kind.FUNCTION
                        suggestion.description.shouldNotBeBlank()
                        suggestion.description shouldBe onPattern.getValue(method).description
                    }
                }
            }
        }
    }

    "the shorthand, the bare name in a door and a pattern method on it all analyze without diagnostics" {
        for (name in names) {
            withClue(name) {
                analyze("$name(200, 400)").let { it.typeOf(it.top()).shouldNotBeNull() }
                analyze("$name(200, 400).slow(8)").diagnostics.size shouldBe 0
                analyze("""note("c e").pan($name)""").diagnostics.size shouldBe 0

                val code = "$name.slow(8)"
                val a = analyze(code)
                a.receiverTypeBeforeDot(code.indexOf(".slow"))?.simpleName shouldBe name
                a.typeOf(a.top()).shouldNotBeNull()
            }
        }
    }
})

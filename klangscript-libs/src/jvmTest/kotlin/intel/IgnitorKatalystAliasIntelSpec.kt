/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.script.intel

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith
import io.peekandpoke.klang.script.ast.ExpressionStatement
import io.peekandpoke.klang.script.docs.KlangDocsRegistry
import io.peekandpoke.klang.script.generated.generatedStdlibDocs
import io.peekandpoke.klang.script.types.KlangProperty
import io.peekandpoke.klang.script.types.KlangType

/**
 * The editor's view of the short names `Ign` and `Kat` and of `Katalyst.slot`, against the real generated stdlib
 * registry (the Ignitor/Katalyst naming, plan section 7.1, the intel row; the `FreqAccessorIntelSpec` pattern):
 * completion after `Ign.` offers what `Ignitor.` offers, `Kat(` resolves through the object's call form with its signature, both
 * docs symbols carry a category, and `Kat.slot.reverb.wet` types through to a `KatalystParam`.
 */
class IgnitorKatalystAliasIntelSpec : StringSpec({

    val registry = KlangDocsRegistry().apply { registerAll(generatedStdlibDocs) }
    val completions = CompletionProvider(registry)

    fun typeOfSymbol(name: String): KlangType =
        registry.get(name).shouldNotBeNull().variants.filterIsInstance<KlangProperty>().single { it.owner == null }.type

    fun analyze(code: String) = AnalyzedAst.build(code, registry)
    fun AnalyzedAst.top() = (ast.statements.first() as ExpressionStatement).expression

    "completion after Ign. offers exactly the Ignitor members" {
        val ign = completions.memberCompletions(typeOfSymbol("Ign"), "").map { it.name }.sorted()
        val ignitor = completions.memberCompletions(typeOfSymbol("Ignitor"), "").map { it.name }.sorted()

        ign shouldContainAll listOf("sine", "supersaw", "param", "slot")
        ign shouldBe ignitor
    }

    "completion after Kat. offers exactly the Katalyst members, slot included" {
        val kat = completions.memberCompletions(typeOfSymbol("Kat"), "").map { it.name }.sorted()
        val katalyst = completions.memberCompletions(typeOfSymbol("Katalyst"), "").map { it.name }.sorted()

        kat shouldContainAll listOf("build", "classic", "param", "slot")
        kat shouldBe katalyst
    }

    "Kat( resolves through the call form and shows the call signature" {
        val callForm = registry.getCallForm(typeOfSymbol("Kat")).shouldNotBeNull()
        callForm.signature shouldStartWith "Katalyst(configure"

        val a = analyze("Kat(k => k.reverb(0.2))")
        a.typeOf(a.top())?.simpleName shouldBe "KatalystDsl"
        a.diagnostics.size shouldBe 0
    }

    "the docs symbols Ign and Kat carry the object category" {
        registry.get("Ign").shouldNotBeNull().category shouldBe "object"
        registry.get("Kat").shouldNotBeNull().category shouldBe "object"
    }

    "Kat.slot.reverb.wet and Katalyst.param(...) type as a KatalystParam" {
        for (code in listOf("Kat.slot.reverb.wet", "Katalyst.slot.gain.gain", """Katalyst.param("room", 5)""")) {
            val a = analyze(code)
            a.typeOf(a.top())?.simpleName shouldBe "KatalystParam"
        }

        completions.memberCompletions(
            analyze("Kat.slot").let { it.typeOf(it.top()).shouldNotBeNull() }, "",
        ).map { it.name } shouldContainAll listOf("body", "vowel", "delay", "reverb", "phaser", "compressor", "gain", "duck")
    }
})

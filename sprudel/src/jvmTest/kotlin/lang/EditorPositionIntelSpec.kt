/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.sprudel.lang

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.ints.shouldBeGreaterThanOrEqual
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.peekandpoke.klang.script.docs.KlangDocsRegistry
import io.peekandpoke.klang.script.generated.generatedSprudelDocs
import io.peekandpoke.klang.script.generated.generatedStdlibDocs
import io.peekandpoke.klang.script.intel.AnalyzedAst
import io.peekandpoke.klang.script.intel.argumentAt
import io.peekandpoke.klang.script.types.KlangCallable
import io.peekandpoke.klang.script.types.KlangDecl
import io.peekandpoke.klang.script.types.KlangProperty
import io.peekandpoke.klang.script.types.KlangSymbol

/**
 * The editor reads a name as what it is where it stands, against the real registries in the editor's order (the
 * stdlib first, then sprudel): the hover of a shared name (`perlin`, `gain`) shows the variant of that position,
 * the hover of a second name of a callable object (`Kat`, `lowpass`) carries the call form, the param tools and the
 * named-argument check respect a local (a closure sees one declared after it), and a diagnostic names the call as
 * written. The logic is pinned on both platforms by `PositionAwareIntelSpec` in klangscript; this is the proof on the
 * generated docs (`docs/tasks-archive/2026-10/20261007-callable-object-docs.md`, open items).
 */
class EditorPositionIntelSpec : StringSpec({

    val registry = KlangDocsRegistry().apply {
        registerAll(generatedStdlibDocs)
        registerAll(generatedSprudelDocs)
    }

    fun analyze(code: String) = AnalyzedAst.build(code, registry)

    fun hover(code: String, needle: String): KlangSymbol {
        val pos = code.indexOf(needle)

        withClue("needle '$needle' in '$code'") { pos shouldBeGreaterThanOrEqual 0 }

        return analyze(code).symbolAt(pos).shouldNotBeNull()
    }

    fun KlangSymbol.prose(): String? = variants.firstOrNull { it.description.isNotBlank() }?.description

    fun KlangDecl.isTopLevel() = when (this) {
        is KlangCallable -> receiver == null
        is KlangProperty -> owner == null
    }

    "a bare shared name hovers with sprudel's top-level prose, a member of Ignitor with the stdlib's" {
        for (name in listOf("perlin", "sine", "adsr", "duck", "tremolo", "gain", "compressor")) {
            withClue(name) {
                val all = registry.get(name).shouldNotBeNull()
                val sprudelTopLevel = all.variants.first { it.isTopLevel() && it.description.isNotBlank() }
                val stdlibFirst = all.variants.first { it.description.isNotBlank() }

                // The case the fix is about: in registration order the stdlib's prose came first
                stdlibFirst.library shouldBe "stdlib"
                sprudelTopLevel.library shouldBe "sprudel"

                val bare = hover(name, name)
                bare.prose() shouldBe sprudelTopLevel.description
                bare.variants.all { it.isTopLevel() } shouldBe true
                bare.origin shouldBe KlangSymbol.Origin.Library("sprudel")
            }
        }

        hover("Ignitor.perlin()", "perlin").variants.map { it.library }.distinct() shouldBe listOf("stdlib")
    }

    "a called shared name hovers as the object, then its call form" {
        val symbol = registry.get("perlin").shouldNotBeNull()
        val callForm = symbol.callForm.shouldNotBeNull()
        val sprudelObject = symbol.variants.single { it is KlangProperty && it.owner == null }

        hover("perlin(100, 200)", "perlin").variants shouldBe listOf(sprudelObject, callForm)
    }

    "a second name of a callable object hovers with the object's call form" {
        for ((alias, objectName) in listOf("Kat" to "Katalyst", "lowpass" to "lpf")) {
            withClue(alias) {
                val callForm = registry.get(objectName).shouldNotBeNull().callForm.shouldNotBeNull()

                callForm.name shouldBe objectName
                hover(alias, alias).variants shouldContain callForm
            }
        }
    }

    "a second name of a callable object opens the object's param tool" {
        listOf(
            Triple("lowpass", "lpf", listOf("SprudelLpFilterEditor", "SprudelLpFilterSequenceEditor")),
            Triple("highpass", "hpf", listOf("SprudelHpFilterEditor", "SprudelHpFilterSequenceEditor")),
            Triple("bandpass", "bpf", listOf("SprudelBpFilterEditor", "SprudelBpFilterSequenceEditor")),
        ).forEach { (alias, objectName, tools) ->
            withClue(alias) {
                val viaAlias = "note(\"c\").apply($alias(0.5))"
                val found = analyze(viaAlias).argumentAt(viaAlias.indexOf("0.5")).shouldNotBeNull()

                found.symbol.name shouldBe objectName
                found.binding.param.uitools shouldNotBe emptyList<String>()
                found.binding.param.uitools shouldBe tools
            }
        }
    }

    "a member of a namespace import keeps the top-level function it calls" {
        val code = "import * as sp from \"sprudel\"\nsp.note(\"c\")"
        val hovered = analyze(code).symbolAt(code.indexOf("note")).shouldNotBeNull()

        hovered.variants.filterIsInstance<KlangCallable>().any { it.receiver == null } shouldBe true
    }

    "a local shadows the global gain for the param tools" {
        val global = """note("c").superimpose(gain(0.5))"""
        analyze(global).argumentAt(global.indexOf("0.5")).shouldNotBeNull().binding.param.uitools shouldNotBe emptyList<String>()

        val shadowed = "const gain = (a) => a\n$global"
        analyze(shadowed).argumentAt(shadowed.indexOf("0.5")).shouldBeNull()
    }

    "a closure bound by a declaration sees a later local gain, a transform does not" {
        analyze("gain(level = 1)").diagnostics.map { it.message }.single() shouldContain "Unknown parameter 'level' on 'gain'"

        analyze("const f = () => gain(level = 1)\nconst gain = (level) => level").diagnostics.shouldBeEmpty()

        // A transform runs where it is written, before the later local exists: the global, as at runtime
        analyze("note(\"c\").superimpose(x => x.apply(gain(level = 1)))\nconst gain = (level) => level")
            .diagnostics.map { it.message }.single() shouldContain "Unknown parameter 'level' on 'gain'"
    }

    "a diagnostic names the call as written" {
        val message = analyze("lowpass(fre = 800)").diagnostics.single().message

        message shouldContain "Unknown parameter 'fre' on 'lowpass'"
        message.split(" ") shouldNotContain "'lpf'"
    }
})

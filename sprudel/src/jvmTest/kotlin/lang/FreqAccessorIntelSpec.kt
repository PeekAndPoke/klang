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

    "every effects accessor is an object with a call form and the first-step operators" {
        listOf("distort", "distos", "crush", "crushos", "coarse", "coarseos", "roomWet", "roomsize", "roomfade", "roomlp",
            "roomdim", "delayWet", "delaytime", "delayfeedback", "phaser", "phaserWet", "phaserFloor", "phasercenter",
            "phasersweep", "tremolosync", "tremolodepth", "tremoloskew", "tremolophase", "delaycap").forEach { name ->
            val type = registry.get(name).shouldNotBeNull().variants.filterIsInstance<KlangProperty>().single().type
            type.simpleName shouldBe name
            registry.getCallable("invoke", type).shouldNotBeNull().signature shouldStartWith "$name("
            CompletionProvider(registry).memberCompletions(type, "").map { it.name } shouldContainAll listOf("add", "sub", "mul", "div")
            analyze("$name(0.5)").diagnostics.size shouldBe 0
        }
    }

    "every batch-three accessor is an object with a call form and the first-step operators" {
        listOf("begin", "end", "speed", "loopBegin", "loopEnd", "cut", "fmh", "fmattack", "fmdecay", "fmsustain", "vowelWet", "vowelFloor", "bodyWet", "bodyFloor", "legato", "vibrato", "vibratoMod", "pattack", "pdecay", "prelease", "penv", "pcurve", "panchor", "accelerate", "notchf", "nresonance", "nfattack", "nfdecay", "nfsustain", "nfrelease", "nfenv", "lpe", "lpx", "hpe", "hpx", "bpe").forEach { name ->
            val type = registry.get(name).shouldNotBeNull().variants.filterIsInstance<KlangProperty>().single().type
            type.simpleName shouldBe name
            registry.getCallable("invoke", type).shouldNotBeNull().signature shouldStartWith "$name("
            CompletionProvider(registry).memberCompletions(type, "").map { it.name } shouldContainAll listOf("add", "sub", "mul", "div")
            analyze("$name(0.5)").diagnostics.size shouldBe 0
        }
    }

    "every batch-four accessor is an object with a call form and the first-step operators" {
        listOf("unison", "spread", "panSpread", "density", "orbit", "duckorbit", "duckattack", "duckdepth", "compressor", "fmenv", "analog", "duty", "onepole").forEach { name ->
            val type = registry.get(name).shouldNotBeNull().variants.filterIsInstance<KlangProperty>().single().type
            type.simpleName shouldBe name
            registry.getCallable("invoke", type).shouldNotBeNull().signature shouldStartWith "$name("
            CompletionProvider(registry).memberCompletions(type, "").map { it.name } shouldContainAll listOf("add", "sub", "mul", "div")
            analyze("$name(0.5)").diagnostics.size shouldBe 0
        }
    }

    "every alias constant carries its canonical object's type, so it calls and reads like the original" {
        mapOf("fmmod" to "fmenv", "uni" to "unison", "voices" to "unison", "d" to "density", "o" to "orbit", "duck" to "duckorbit", "duckatt" to "duckattack", "comp" to "compressor",
            "clip" to "legato", "vib" to "vibrato", "patt" to "pattack", "pdec" to "pdecay", "prel" to "prelease", "pamt" to "penv", "pcrv" to "pcurve", "panc" to "panchor", "loopb" to "loopBegin", "loope" to "loopEnd", "fmatt" to "fmattack", "fmdec" to "fmdecay", "fmsus" to "fmsustain", "notch" to "notchf", "ntf" to "notchf", "notchq" to "nresonance", "ntq" to "nresonance", "nfa" to "nfattack", "nfd" to "nfdecay", "nfs" to "nfsustain", "nfr" to "nfrelease", "nfe" to "nfenv",
            "vel" to "velocity", "lowpass" to "lpf", "highpass" to "hpf", "bandpass" to "bpf", "dist" to "distort", "distortOversampling" to "distos", "crushOversampling" to "crushos",
            "coarseOversampling" to "coarseos", "rsize" to "roomsize", "sz" to "roomsize", "size" to "roomsize",
            "rfade" to "roomfade", "rlp" to "roomlp", "rdim" to "roomdim", "delayfb" to "delayfeedback",
            "dfb" to "delayfeedback", "ph" to "phaser", "phc" to "phasercenter", "phs" to "phasersweep",
            "tremsync" to "tremolosync", "tremdepth" to "tremolodepth", "tremskew" to "tremoloskew",
            "tremphase" to "tremolophase", "dcap" to "delaycap").forEach { (alias, canonical) ->
            val symbol = registry.get(alias).shouldNotBeNull()
            // An alias constant's KDoc carries the category: the property entry merges first and
            // would otherwise turn the whole symbol "uncategorized" on the docs page.
            (symbol.category != "uncategorized") shouldBe true
            val type = symbol.variants.filterIsInstance<KlangProperty>().single().type
            type.simpleName shouldBe canonical
            registry.getCallable("invoke", type).shouldNotBeNull().signature shouldStartWith "$canonical("
            analyze("$alias(0.5)").diagnostics.size shouldBe 0
            CompletionProvider(registry).memberCompletions(type, "").map { it.name } shouldContainAll listOf("mul")
        }
    }

    "named-argument diagnostics reach the setter through invoke" {
        val bad = analyze("freq(hzz = 440)")
        bad.diagnostics.size shouldBe 1
        bad.diagnostics.single().message shouldContain "Unknown parameter 'hzz'"
        analyze("freq(hz = 440)").diagnostics.size shouldBe 0
    }
})

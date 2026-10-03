/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.script.intel

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldHaveAtLeastSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.peekandpoke.klang.script.ast.Expression
import io.peekandpoke.klang.script.ast.ExpressionStatement
import io.peekandpoke.klang.script.docs.KlangDocsRegistry
import io.peekandpoke.klang.script.generated.generatedStdlibDocs
import io.peekandpoke.klang.script.parser.KlangScriptParser
import io.peekandpoke.klang.script.types.KlangCallable
import io.peekandpoke.klang.script.types.KlangProperty
import io.peekandpoke.klang.script.types.KlangType

/**
 * Tests using the real generated stdlib docs to verify type inference
 * works end-to-end with actual production data.
 */
class StdlibDocsInferenceTest : StringSpec({

    fun stdlibRegistry(): KlangDocsRegistry = KlangDocsRegistry().apply {
        registerAll(generatedStdlibDocs)
    }

    fun parseExpr(code: String): Expression {
        val program = KlangScriptParser.parse(code)
        return (program.statements.first() as ExpressionStatement).expression
    }

    // ── Real stdlib: object identifiers ──────────────────────────────────

    "real stdlib: Ignitor identifier infers Ignitor" {
        val inferrer = ExpressionTypeInferrer(stdlibRegistry())
        inferrer.inferType(parseExpr("Ignitor"))?.simpleName shouldBe "Ignitor"
    }

    "real stdlib: Math identifier infers Math" {
        val inferrer = ExpressionTypeInferrer(stdlibRegistry())
        inferrer.inferType(parseExpr("Math"))?.simpleName shouldBe "Math"
    }

    // ── Real stdlib: Ignitor method calls ───────────────────────────────────

    "real stdlib: Ignitor.sine() returns IgnitorDsl" {
        val inferrer = ExpressionTypeInferrer(stdlibRegistry())
        inferrer.inferType(parseExpr("Ignitor.sine()"))?.simpleName shouldBe "IgnitorDsl"
    }

    "real stdlib: Ignitor.saw() and Ignitor.supersaw() return the base IgnitorDsl (knobs live on builders)" {
        val inferrer = ExpressionTypeInferrer(stdlibRegistry())
        inferrer.inferType(parseExpr("Ignitor.saw()"))?.simpleName shouldBe "IgnitorDsl"
        inferrer.inferType(parseExpr("Ignitor.supersaw()"))?.simpleName shouldBe "IgnitorDsl"
    }

    "real stdlib: the supersaw door's configure parameter is a typed function of the builder" {
        val reg = stdlibRegistry()
        val supersaw = reg.getCallable("supersaw", KlangType("Ignitor"))!!
        val configure = supersaw.params.single { it.name == "configure" }
        configure.type.render() shouldBe "((OscSuperSawBuilder) -> OscSuperSawBuilder)?"
        configure.isOptional shouldBe true
    }

    "real stdlib: builder knobs resolve on the builder and keep its type, base wrappers do not" {
        val reg = stdlibRegistry()
        val builder = reg.getCallable("supersaw", KlangType("Ignitor"))!!
            .params.single { it.name == "configure" }.type.functionParams!!.single()
        reg.getCallable("voices", builder)!!.returnType?.simpleName shouldBe "OscSuperSawBuilder"
        reg.getCallable("phasePool", builder)!!.returnType?.simpleName shouldBe "OscSuperSawBuilder"
        reg.getCallable("lowpass", builder) shouldBe null
    }

    "real stdlib: Ignitor.whitenoise() returns IgnitorDsl" {
        val inferrer = ExpressionTypeInferrer(stdlibRegistry())
        inferrer.inferType(parseExpr("Ignitor.whitenoise()"))?.simpleName shouldBe "IgnitorDsl"
    }

    // ── Real stdlib: IgnitorDsl chains ──────────────────────────────────

    "real stdlib: Ignitor.sine().lowpass(1000) returns IgnitorDsl" {
        val inferrer = ExpressionTypeInferrer(stdlibRegistry())
        inferrer.inferType(parseExpr("Ignitor.sine().lowpass(1000)"))?.simpleName shouldBe "IgnitorDsl"
    }

    "real stdlib: Ignitor.supersaw().lowpass(2000).adsr(0.01, 0.2, 0.5, 0.5) returns IgnitorDsl" {
        val inferrer = ExpressionTypeInferrer(stdlibRegistry())
        inferrer.inferType(
            parseExpr("Ignitor.supersaw().lowpass(2000).adsr(0.01, 0.2, 0.5, 0.5)")
        )?.simpleName shouldBe "IgnitorDsl"
    }

    // ── Real stdlib: Math method calls ──────────────────────────────────

    "real stdlib: Math.sqrt(16) returns Number" {
        val inferrer = ExpressionTypeInferrer(stdlibRegistry())
        inferrer.inferType(parseExpr("Math.sqrt(16)"))?.simpleName shouldBe "Number"
    }

    "real stdlib: Math.abs(-5) returns Number" {
        val inferrer = ExpressionTypeInferrer(stdlibRegistry())
        inferrer.inferType(parseExpr("Math.abs(-5)"))?.simpleName shouldBe "Number"
    }

    // ── Real stdlib: registry merge preserves all variants ──────────────

    "real stdlib: registry has Ignitor, Math, Object object symbols" {
        val reg = stdlibRegistry()
        reg.get("Ignitor") shouldNotBe null
        reg.get("Math") shouldNotBe null
        reg.get("Object") shouldNotBe null
    }

    "real stdlib: getVariantsForReceiver(Ignitor) returns Ignitor methods" {
        val reg = stdlibRegistry()
        val oscMembers = reg.getVariantsForReceiver(KlangType("Ignitor"))
        oscMembers shouldHaveAtLeastSize 5 // sine, saw, square, triangle, slot, etc.
        // Each returned symbol has at least one variant whose owner/receiver is Ignitor.
        oscMembers.all { symbol ->
            symbol.variants.any { v ->
                when (v) {
                    is KlangCallable -> v.receiver?.simpleName == "Ignitor"
                    is KlangProperty -> v.owner?.simpleName == "Ignitor"
                }
            }
        } shouldBe true
    }

    "real stdlib: getVariantsForReceiver(IgnitorDsl) returns IgnitorDsl methods" {
        val reg = stdlibRegistry()
        val methods = reg.getVariantsForReceiver(KlangType("IgnitorDsl"))
        methods shouldHaveAtLeastSize 3 // lowpass, adsr, mul, etc.
    }

    "real stdlib: getVariantsForReceiver(Math) returns Math methods" {
        val reg = stdlibRegistry()
        val methods = reg.getVariantsForReceiver(KlangType("Math"))
        methods shouldHaveAtLeastSize 5 // sqrt, abs, floor, ceil, etc.
    }

    "real stdlib: getCallable finds sine with receiver Ignitor" {
        val reg = stdlibRegistry()
        val callable = reg.getCallable("sine", KlangType("Ignitor"))
        callable shouldNotBe null
        callable!!.returnType?.simpleName shouldBe "IgnitorDsl"
    }

    "real stdlib: getCallable finds lowpass with receiver IgnitorDsl" {
        val reg = stdlibRegistry()
        val callable = reg.getCallable("lowpass", KlangType("IgnitorDsl"))
        callable shouldNotBe null
        callable!!.returnType?.simpleName shouldBe "IgnitorDsl"
    }

    "real stdlib: getCallable returns null for sine with wrong receiver" {
        val reg = stdlibRegistry()
        reg.getCallable("sine", KlangType("Math")) shouldBe null
    }

    // ── FQCN coverage in real KSP-emitted docs ──────────────────────────
    //
    // These tests act as a snapshot for the KSP code-gen: they assert that
    // production stdlib symbols carry the expected FQCNs. If KSP regresses
    // (stops emitting fqcn, or emits the wrong one), these fail loudly.

    "real stdlib: Ignitor symbol's type carries the KlangScriptIgnitor FQCN" {
        val reg = stdlibRegistry()
        val osc = reg.get("Ignitor")!!
        val prop = osc.variants.filterIsInstance<KlangProperty>().first()
        prop.type.simpleName shouldBe "Ignitor"
        prop.type.fqcn shouldBe "io.peekandpoke.klang.script.stdlib.KlangScriptIgnitor"
    }

    "real stdlib: the Ign alias symbol's type is the Ignitor object, with the KlangScriptIgnitor FQCN" {
        val reg = stdlibRegistry()
        val ign = reg.get("Ign")!!
        val prop = ign.variants.filterIsInstance<KlangProperty>().first()
        prop.type.simpleName shouldBe "Ignitor"
        prop.type.fqcn shouldBe "io.peekandpoke.klang.script.stdlib.KlangScriptIgnitor"
    }

    "real stdlib: Ignitor.slot member-property owner FQCN matches KlangScriptIgnitor" {
        val reg = stdlibRegistry()
        val slotSym = reg.get("slot")!!
        val slotProp = slotSym.variants
            .filterIsInstance<KlangProperty>()
            .first { it.owner?.simpleName == "Ignitor" }
        slotProp.owner!!.fqcn shouldBe "io.peekandpoke.klang.script.stdlib.KlangScriptIgnitor"
        // And its type points at KlangScriptIgnitorSlots' FQCN so the inferrer can chain.
        slotProp.type.simpleName shouldBe "KlangScriptIgnitorSlots"
        slotProp.type.fqcn shouldBe "io.peekandpoke.klang.script.stdlib.KlangScriptIgnitorSlots"
    }

    "real stdlib: Ignitor.slot.analog member-property owner FQCN matches KlangScriptIgnitorSlots" {
        val reg = stdlibRegistry()
        val analogSym = reg.get("analog")!!
        val analogProp = analogSym.variants
            .filterIsInstance<KlangProperty>()
            .first { it.owner?.simpleName == "KlangScriptIgnitorSlots" }
        analogProp.owner!!.fqcn shouldBe "io.peekandpoke.klang.script.stdlib.KlangScriptIgnitorSlots"
    }

    "real stdlib: Ignitor.sine method receiver FQCN matches KlangScriptIgnitor" {
        val reg = stdlibRegistry()
        val sine = reg.getCallable("sine", KlangType("Ignitor"))!!
        val sineReceiver = sine.receiver.shouldNotBeNull()
        sineReceiver.simpleName shouldBe "Ignitor"
        sineReceiver.fqcn shouldBe "io.peekandpoke.klang.script.stdlib.KlangScriptIgnitor"
    }

    "real stdlib: Math.sqrt method receiver FQCN matches KlangScriptMath" {
        val reg = stdlibRegistry()
        val sqrt = reg.getCallable("sqrt", KlangType("Math"))!!
        val sqrtReceiver = sqrt.receiver.shouldNotBeNull()
        sqrtReceiver.simpleName shouldBe "Math"
        sqrtReceiver.fqcn shouldBe "io.peekandpoke.klang.script.stdlib.KlangScriptMath"
    }

    "real stdlib: type inference of Ignitor.slot returns the KlangScriptIgnitorSlots KlangType with FQCN" {
        val inferrer = ExpressionTypeInferrer(stdlibRegistry())
        val type = inferrer.inferType(parseExpr("Ignitor.slot"))!!
        type.simpleName shouldBe "KlangScriptIgnitorSlots"
        type.fqcn shouldBe "io.peekandpoke.klang.script.stdlib.KlangScriptIgnitorSlots"
    }

    "real stdlib: chained Ignitor.slot.analog resolves to IgnitorDsl" {
        // Full chain: identifier → property access → property access.
        // Each step depends on the previous step's KlangType being correctly
        // populated, and on FQCN-aware lookup matching the next-step owner.
        val inferrer = ExpressionTypeInferrer(stdlibRegistry())
        val type = inferrer.inferType(parseExpr("Ignitor.slot.analog"))!!
        type.simpleName shouldBe "IgnitorDsl"
    }

    "real stdlib: chained Ignitor.slot.analog.lowpass(2000) resolves to IgnitorDsl" {
        // Confirms FQCN-aware lookup chains through an extension method call too.
        val inferrer = ExpressionTypeInferrer(stdlibRegistry())
        val type = inferrer.inferType(parseExpr("Ignitor.slot.analog.lowpass(2000)"))!!
        type.simpleName shouldBe "IgnitorDsl"
    }

    // ── Registry merge simulation ───────────────────────────────────────

    "merge: stdlib + mock sprudel docs preserves both variants for shared name" {
        val reg = stdlibRegistry()

        // Simulate a sprudel "abs" function (top-level, no receiver)
        val sprudelAbs = io.peekandpoke.klang.script.types.KlangSymbol(
            name = "abs", category = "math", origin = io.peekandpoke.klang.script.types.KlangSymbol.Origin.Library("sprudel"),
            variants = listOf(
                KlangCallable(
                    name = "abs", receiver = null,
                    params = listOf(io.peekandpoke.klang.script.types.KlangParam(name = "x", type = KlangType("Number"))),
                    returnType = KlangType("Number")
                )
            )
        )
        reg.register(sprudelAbs)

        val symbol = reg.get("abs")!!
        // stdlib contributes Math.abs (Number), IgnitorDsl.abs (signal) and Number.abs (the method on a
        // number, since 2026-09-08); sprudel adds a top-level.
        symbol.variants.size shouldBe 4

        // Receiver-aware lookup distinguishes them
        val mathAbs = reg.getCallable("abs", KlangType("Math"))
        mathAbs shouldNotBe null
        mathAbs!!.receiver?.simpleName shouldBe "Math"

        val topLevelAbs = reg.getCallable("abs", null)
        topLevelAbs shouldNotBe null
        topLevelAbs!!.receiver shouldBe null
    }

    // ── Per-variant library field ───────────────────────────────────────

    "real stdlib: generated callables carry library=stdlib" {
        val reg = stdlibRegistry()
        val callable = reg.getCallable("sine", KlangType("Ignitor"))
        callable shouldNotBe null
        callable!!.library shouldBe "stdlib"
    }

    // ── Chain breakage with real docs ───────────────────────────────────

    "real stdlib: chain breaks at unknown method" {
        val inferrer = ExpressionTypeInferrer(stdlibRegistry())
        inferrer.inferType(parseExpr("Ignitor.sine().unknownMethod()")) shouldBe null
    }

    "real stdlib: chain break propagates null through subsequent calls" {
        val inferrer = ExpressionTypeInferrer(stdlibRegistry())
        inferrer.inferType(parseExpr("Ignitor.sine().unknownMethod().lowpass(1000)")) shouldBe null
    }
})

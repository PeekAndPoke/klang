/*
 * Copyright (C) 2025-2026 The Klang Audio Motör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.script.intel

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldHaveAtLeastSize
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

    "real stdlib: Osc identifier infers Osc" {
        val inferrer = ExpressionTypeInferrer(stdlibRegistry())
        inferrer.inferType(parseExpr("Osc"))?.simpleName shouldBe "Osc"
    }

    "real stdlib: Math identifier infers Math" {
        val inferrer = ExpressionTypeInferrer(stdlibRegistry())
        inferrer.inferType(parseExpr("Math"))?.simpleName shouldBe "Math"
    }

    // ── Real stdlib: Osc method calls ───────────────────────────────────

    "real stdlib: Osc.sine() returns IgnitorDsl" {
        val inferrer = ExpressionTypeInferrer(stdlibRegistry())
        inferrer.inferType(parseExpr("Osc.sine()"))?.simpleName shouldBe "IgnitorDsl"
    }

    "real stdlib: Osc.saw() returns the narrow Sawtooth subtype" {
        // Phase 2: saw() narrows to IgnitorDsl.Sawtooth so the shape config methods (.resetSamples/.shapeMax)
        // are discoverable; the supertype walk keeps the base IgnitorDsl methods (.lowpass/.adsr) resolvable.
        val inferrer = ExpressionTypeInferrer(stdlibRegistry())
        inferrer.inferType(parseExpr("Osc.saw()"))?.simpleName shouldBe "Sawtooth"
    }

    "real stdlib: Osc.supersaw() returns the narrow SuperSaw subtype" {
        // Phase 2: supersaw() narrows its return to IgnitorDsl.SuperSaw so the typed
        // config methods (.spreadPower/.sideAtten/…) are discoverable. The supertype
        // walk (below) keeps the base IgnitorDsl methods resolvable on it.
        val inferrer = ExpressionTypeInferrer(stdlibRegistry())
        inferrer.inferType(parseExpr("Osc.supersaw()"))?.simpleName shouldBe "SuperSaw"
    }

    "real stdlib: Osc.supersaw() carries IgnitorDsl as a supertype" {
        val inferrer = ExpressionTypeInferrer(stdlibRegistry())
        val type = inferrer.inferType(parseExpr("Osc.supersaw()"))!!
        type.supertypes.map { it.simpleName } shouldContain "IgnitorDsl"
    }

    "real stdlib: base IgnitorDsl method resolves on a narrowed SuperSaw receiver" {
        // getCallable must walk the receiver's supertypes: .lowpass() is registered on
        // IgnitorDsl, yet the receiver here is the narrowed SuperSaw.
        val reg = stdlibRegistry()
        val superSaw = reg.getCallable("supersaw", KlangType("Osc"))!!.returnType!!
        val lowpass = reg.getCallable("lowpass", superSaw)
        lowpass shouldNotBe null
        lowpass!!.returnType?.simpleName shouldBe "IgnitorDsl"
    }

    "real stdlib: Osc.whitenoise() returns IgnitorDsl" {
        val inferrer = ExpressionTypeInferrer(stdlibRegistry())
        inferrer.inferType(parseExpr("Osc.whitenoise()"))?.simpleName shouldBe "IgnitorDsl"
    }

    // ── Real stdlib: IgnitorDsl chains ──────────────────────────────────

    "real stdlib: Osc.sine().lowpass(1000) returns IgnitorDsl" {
        val inferrer = ExpressionTypeInferrer(stdlibRegistry())
        inferrer.inferType(parseExpr("Osc.sine().lowpass(1000)"))?.simpleName shouldBe "IgnitorDsl"
    }

    "real stdlib: Osc.supersaw().lowpass(2000).adsr(0.01, 0.2, 0.5, 0.5) returns IgnitorDsl" {
        val inferrer = ExpressionTypeInferrer(stdlibRegistry())
        inferrer.inferType(
            parseExpr("Osc.supersaw().lowpass(2000).adsr(0.01, 0.2, 0.5, 0.5)")
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

    "real stdlib: registry has Osc, Math, Object object symbols" {
        val reg = stdlibRegistry()
        reg.get("Osc") shouldNotBe null
        reg.get("Math") shouldNotBe null
        reg.get("Object") shouldNotBe null
    }

    "real stdlib: getVariantsForReceiver(Osc) returns Osc methods" {
        val reg = stdlibRegistry()
        val oscMembers = reg.getVariantsForReceiver(KlangType("Osc"))
        oscMembers shouldHaveAtLeastSize 5 // sine, saw, square, triangle, slot, etc.
        // Each returned symbol has at least one variant whose owner/receiver is Osc.
        oscMembers.all { symbol ->
            symbol.variants.any { v ->
                when (v) {
                    is KlangCallable -> v.receiver?.simpleName == "Osc"
                    is KlangProperty -> v.owner?.simpleName == "Osc"
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

    "real stdlib: getCallable finds sine with receiver Osc" {
        val reg = stdlibRegistry()
        val callable = reg.getCallable("sine", KlangType("Osc"))
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

    "real stdlib: Osc symbol's type carries the KlangScriptOsc FQCN" {
        val reg = stdlibRegistry()
        val osc = reg.get("Osc")!!
        val prop = osc.variants.filterIsInstance<KlangProperty>().first()
        prop.type.simpleName shouldBe "Osc"
        prop.type.fqcn shouldBe "io.peekandpoke.klang.script.stdlib.KlangScriptOsc"
    }

    "real stdlib: OscSlot symbol's type carries the KlangScriptOscSlot FQCN" {
        val reg = stdlibRegistry()
        val oscSlot = reg.get("OscSlot")!!
        val prop = oscSlot.variants.filterIsInstance<KlangProperty>().first()
        prop.type.simpleName shouldBe "OscSlot"
        prop.type.fqcn shouldBe "io.peekandpoke.klang.script.stdlib.KlangScriptOscSlot"
    }

    "real stdlib: Osc.slot member-property owner FQCN matches KlangScriptOsc" {
        val reg = stdlibRegistry()
        val slotSym = reg.get("slot")!!
        val slotProp = slotSym.variants
            .filterIsInstance<KlangProperty>()
            .first { it.owner?.simpleName == "Osc" }
        slotProp.owner!!.fqcn shouldBe "io.peekandpoke.klang.script.stdlib.KlangScriptOsc"
        // And its type points at OscSlot's FQCN so the inferrer can chain.
        slotProp.type.simpleName shouldBe "OscSlot"
        slotProp.type.fqcn shouldBe "io.peekandpoke.klang.script.stdlib.KlangScriptOscSlot"
    }

    "real stdlib: OscSlot.analog member-property owner FQCN matches KlangScriptOscSlot" {
        val reg = stdlibRegistry()
        val analogSym = reg.get("analog")!!
        val analogProp = analogSym.variants
            .filterIsInstance<KlangProperty>()
            .first { it.owner?.simpleName == "OscSlot" }
        analogProp.owner!!.fqcn shouldBe "io.peekandpoke.klang.script.stdlib.KlangScriptOscSlot"
    }

    "real stdlib: Osc.sine method receiver FQCN matches KlangScriptOsc" {
        val reg = stdlibRegistry()
        val sine = reg.getCallable("sine", KlangType("Osc"))!!
        sine.receiver!!.simpleName shouldBe "Osc"
        sine.receiver.fqcn shouldBe "io.peekandpoke.klang.script.stdlib.KlangScriptOsc"
    }

    "real stdlib: Math.sqrt method receiver FQCN matches KlangScriptMath" {
        val reg = stdlibRegistry()
        val sqrt = reg.getCallable("sqrt", KlangType("Math"))!!
        sqrt.receiver!!.simpleName shouldBe "Math"
        sqrt.receiver.fqcn shouldBe "io.peekandpoke.klang.script.stdlib.KlangScriptMath"
    }

    "real stdlib: type inference of Osc.slot returns OscSlot KlangType with FQCN" {
        val inferrer = ExpressionTypeInferrer(stdlibRegistry())
        val type = inferrer.inferType(parseExpr("Osc.slot"))!!
        type.simpleName shouldBe "OscSlot"
        type.fqcn shouldBe "io.peekandpoke.klang.script.stdlib.KlangScriptOscSlot"
    }

    "real stdlib: chained Osc.slot.analog resolves to IgnitorDsl" {
        // Full chain: identifier → property access → property access.
        // Each step depends on the previous step's KlangType being correctly
        // populated, and on FQCN-aware lookup matching the next-step owner.
        val inferrer = ExpressionTypeInferrer(stdlibRegistry())
        val type = inferrer.inferType(parseExpr("Osc.slot.analog"))!!
        type.simpleName shouldBe "IgnitorDsl"
    }

    "real stdlib: chained Osc.slot.analog.lowpass(2000) resolves to IgnitorDsl" {
        // Confirms FQCN-aware lookup chains through an extension method call too.
        val inferrer = ExpressionTypeInferrer(stdlibRegistry())
        val type = inferrer.inferType(parseExpr("Osc.slot.analog.lowpass(2000)"))!!
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
        // stdlib contributes Math.abs (Number) + IgnitorDsl.abs (signal) — sprudel adds a top-level.
        symbol.variants.size shouldBe 3

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
        val callable = reg.getCallable("sine", KlangType("Osc"))
        callable shouldNotBe null
        callable!!.library shouldBe "stdlib"
    }

    // ── Chain breakage with real docs ───────────────────────────────────

    "real stdlib: chain breaks at unknown method" {
        val inferrer = ExpressionTypeInferrer(stdlibRegistry())
        inferrer.inferType(parseExpr("Osc.sine().unknownMethod()")) shouldBe null
    }

    "real stdlib: chain break propagates null through subsequent calls" {
        val inferrer = ExpressionTypeInferrer(stdlibRegistry())
        inferrer.inferType(parseExpr("Osc.sine().unknownMethod().lowpass(1000)")) shouldBe null
    }
})

package io.peekandpoke.klang.script.intel

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveAtLeastSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.peekandpoke.klang.script.ast.Expression
import io.peekandpoke.klang.script.ast.ExpressionStatement
import io.peekandpoke.klang.script.docs.KlangDocsRegistry
import io.peekandpoke.klang.script.generated.generatedStdlibDocs
import io.peekandpoke.klang.script.parser.KlangScriptParser
import io.peekandpoke.klang.script.types.KlangCallable
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

    "real stdlib: Osc.sine() returns ExciterDsl" {
        val inferrer = ExpressionTypeInferrer(stdlibRegistry())
        inferrer.inferType(parseExpr("Osc.sine()"))?.simpleName shouldBe "ExciterDsl"
    }

    "real stdlib: Osc.saw() returns ExciterDsl" {
        val inferrer = ExpressionTypeInferrer(stdlibRegistry())
        inferrer.inferType(parseExpr("Osc.saw()"))?.simpleName shouldBe "ExciterDsl"
    }

    "real stdlib: Osc.supersaw() returns ExciterDsl" {
        val inferrer = ExpressionTypeInferrer(stdlibRegistry())
        inferrer.inferType(parseExpr("Osc.supersaw()"))?.simpleName shouldBe "ExciterDsl"
    }

    "real stdlib: Osc.whitenoise() returns ExciterDsl" {
        val inferrer = ExpressionTypeInferrer(stdlibRegistry())
        inferrer.inferType(parseExpr("Osc.whitenoise()"))?.simpleName shouldBe "ExciterDsl"
    }

    // ── Real stdlib: ExciterDsl chains ──────────────────────────────────

    "real stdlib: Osc.sine().lowpass(1000) returns ExciterDsl" {
        val inferrer = ExpressionTypeInferrer(stdlibRegistry())
        inferrer.inferType(parseExpr("Osc.sine().lowpass(1000)"))?.simpleName shouldBe "ExciterDsl"
    }

    "real stdlib: Osc.supersaw().lowpass(2000).adsr(0.01, 0.2, 0.5, 0.5) returns ExciterDsl" {
        val inferrer = ExpressionTypeInferrer(stdlibRegistry())
        inferrer.inferType(
            parseExpr("Osc.supersaw().lowpass(2000).adsr(0.01, 0.2, 0.5, 0.5)")
        )?.simpleName shouldBe "ExciterDsl"
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
        val oscMethods = reg.getVariantsForReceiver(KlangType("Osc"))
        oscMethods shouldHaveAtLeastSize 5 // sine, saw, square, triangle, etc.
        oscMethods.all { symbol ->
            symbol.variants.any { v -> v is KlangCallable && v.receiver?.simpleName == "Osc" }
        } shouldBe true
    }

    "real stdlib: getVariantsForReceiver(ExciterDsl) returns ExciterDsl methods" {
        val reg = stdlibRegistry()
        val methods = reg.getVariantsForReceiver(KlangType("ExciterDsl"))
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
        callable!!.returnType?.simpleName shouldBe "ExciterDsl"
    }

    "real stdlib: getCallable finds lowpass with receiver ExciterDsl" {
        val reg = stdlibRegistry()
        val callable = reg.getCallable("lowpass", KlangType("ExciterDsl"))
        callable shouldNotBe null
        callable!!.returnType?.simpleName shouldBe "ExciterDsl"
    }

    "real stdlib: getCallable returns null for sine with wrong receiver" {
        val reg = stdlibRegistry()
        reg.getCallable("sine", KlangType("Math")) shouldBe null
    }

    // ── Registry merge simulation ───────────────────────────────────────

    "merge: stdlib + mock sprudel docs preserves both variants for shared name" {
        val reg = stdlibRegistry()

        // Simulate a sprudel "abs" function (top-level, no receiver)
        val sprudelAbs = io.peekandpoke.klang.script.types.KlangSymbol(
            name = "abs", category = "math", library = "sprudel",
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
        // Should have both: Math.abs (from stdlib) + top-level abs (from sprudel)
        symbol.variants.size shouldBe 2

        // Receiver-aware lookup distinguishes them
        val mathAbs = reg.getCallable("abs", KlangType("Math"))
        mathAbs shouldNotBe null
        mathAbs!!.receiver?.simpleName shouldBe "Math"

        val topLevelAbs = reg.getCallable("abs", null)
        topLevelAbs shouldNotBe null
        topLevelAbs!!.receiver shouldBe null
    }

    // ── Builder docs-aware registration ─────────────────────────────────

    "KlangStdLib.create() includes manually registered Osc.register docs" {
        val lib = io.peekandpoke.klang.script.stdlib.KlangStdLib.create()
        val registerSymbol = lib.docs.get("register")
        registerSymbol shouldNotBe null
        val callable = registerSymbol!!.variants.filterIsInstance<KlangCallable>().firstOrNull()
        callable shouldNotBe null
        callable!!.receiver?.simpleName shouldBe "Osc"
        callable.returnType?.simpleName shouldBe "String"
        callable.params.map { it.name } shouldBe listOf("name", "dsl")
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

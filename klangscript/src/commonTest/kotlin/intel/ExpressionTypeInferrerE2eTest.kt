package io.peekandpoke.klang.script.intel

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.peekandpoke.klang.script.ast.AstIndex
import io.peekandpoke.klang.script.ast.AstNode
import io.peekandpoke.klang.script.ast.CallExpression
import io.peekandpoke.klang.script.ast.Expression
import io.peekandpoke.klang.script.ast.ExpressionStatement
import io.peekandpoke.klang.script.ast.MemberAccess
import io.peekandpoke.klang.script.docs.KlangDocsRegistry
import io.peekandpoke.klang.script.parser.KlangScriptParser
import io.peekandpoke.klang.script.types.KlangCallable
import io.peekandpoke.klang.script.types.KlangParam
import io.peekandpoke.klang.script.types.KlangProperty
import io.peekandpoke.klang.script.types.KlangSymbol
import io.peekandpoke.klang.script.types.KlangType

/**
 * End-to-end tests: parse real KlangScript source → build AstIndex → infer types
 * on actual AST nodes produced by the parser.
 */
class ExpressionTypeInferrerE2eTest : StringSpec({

    fun registry(): KlangDocsRegistry = KlangDocsRegistry().apply {
        register(
            KlangSymbol(
                name = "Osc", category = "object", library = "stdlib",
                variants = listOf(KlangProperty(name = "Osc", type = KlangType("Osc")))
            )
        )
        register(
            KlangSymbol(
                name = "Math", category = "object", library = "stdlib",
                variants = listOf(KlangProperty(name = "Math", type = KlangType("Math")))
            )
        )
        register(
            KlangSymbol(
                name = "sine", category = "oscillator", library = "stdlib",
                variants = listOf(
                    KlangCallable(
                        name = "sine", receiver = KlangType("Osc"),
                        params = emptyList(), returnType = KlangType("IgnitorDsl")
                    )
                )
            )
        )
        register(
            KlangSymbol(
                name = "lowpass", category = "filter", library = "stdlib",
                variants = listOf(
                    KlangCallable(
                        name = "lowpass", receiver = KlangType("IgnitorDsl"),
                        params = listOf(KlangParam(name = "cutoffHz", type = KlangType("Number"))),
                        returnType = KlangType("IgnitorDsl")
                    )
                )
            )
        )
        register(
            KlangSymbol(
                name = "adsr", category = "envelope", library = "stdlib",
                variants = listOf(
                    KlangCallable(
                        name = "adsr", receiver = KlangType("IgnitorDsl"),
                        params = listOf(
                            KlangParam(name = "a", type = KlangType("Number")),
                            KlangParam(name = "d", type = KlangType("Number")),
                            KlangParam(name = "s", type = KlangType("Number")),
                            KlangParam(name = "r", type = KlangType("Number")),
                        ),
                        returnType = KlangType("IgnitorDsl")
                    )
                )
            )
        )
        register(
            KlangSymbol(
                name = "sqrt", category = "math", library = "stdlib",
                variants = listOf(
                    KlangCallable(
                        name = "sqrt", receiver = KlangType("Math"),
                        params = listOf(KlangParam(name = "x", type = KlangType("Number"))),
                        returnType = KlangType("Number")
                    )
                )
            )
        )
        register(
            KlangSymbol(
                name = "note", category = "pattern", library = "sprudel",
                variants = listOf(
                    KlangCallable(
                        name = "note", receiver = null,
                        params = listOf(KlangParam(name = "pattern", type = KlangType("String"))),
                        returnType = KlangType("Pattern")
                    )
                )
            )
        )
        register(
            KlangSymbol(
                name = "gain", category = "effect", library = "sprudel",
                variants = listOf(
                    KlangCallable(
                        name = "gain", receiver = KlangType("Pattern"),
                        params = listOf(KlangParam(name = "amount", type = KlangType("Number"))),
                        returnType = KlangType("Pattern")
                    )
                )
            )
        )
    }

    /** Parse code, extract the expression from the first ExpressionStatement. */
    fun parseExpr(code: String): Expression {
        val program = KlangScriptParser.parse(code)
        return (program.statements.first() as ExpressionStatement).expression
    }

    // ── Parsed literals ─────────────────────────────────────────────────

    "parsed: 42 infers Number" {
        val inferrer = ExpressionTypeInferrer(registry())
        inferrer.inferType(parseExpr("42"))?.simpleName shouldBe "Number"
    }

    "parsed: string literal infers String" {
        val inferrer = ExpressionTypeInferrer(registry())
        inferrer.inferType(parseExpr("\"hello\""))?.simpleName shouldBe "String"
    }

    "parsed: true infers Boolean" {
        val inferrer = ExpressionTypeInferrer(registry())
        inferrer.inferType(parseExpr("true"))?.simpleName shouldBe "Boolean"
    }

    "parsed: [1, 2, 3] infers Array" {
        val inferrer = ExpressionTypeInferrer(registry())
        inferrer.inferType(parseExpr("[1, 2, 3]"))?.simpleName shouldBe "Array"
    }

    "parsed: {a: 1} infers Object" {
        val inferrer = ExpressionTypeInferrer(registry())
        inferrer.inferType(parseExpr("{a: 1}"))?.simpleName shouldBe "Object"
    }

    // ── Parsed identifiers ──────────────────────────────────────────────

    "parsed: Osc infers Osc" {
        val inferrer = ExpressionTypeInferrer(registry())
        inferrer.inferType(parseExpr("Osc"))?.simpleName shouldBe "Osc"
    }

    "parsed: Math infers Math" {
        val inferrer = ExpressionTypeInferrer(registry())
        inferrer.inferType(parseExpr("Math"))?.simpleName shouldBe "Math"
    }

    // ── Parsed method calls ─────────────────────────────────────────────

    "parsed: Osc.sine() infers IgnitorDsl" {
        val inferrer = ExpressionTypeInferrer(registry())
        inferrer.inferType(parseExpr("Osc.sine()"))?.simpleName shouldBe "IgnitorDsl"
    }

    "parsed: Math.sqrt(16) infers Number" {
        val inferrer = ExpressionTypeInferrer(registry())
        inferrer.inferType(parseExpr("Math.sqrt(16)"))?.simpleName shouldBe "Number"
    }

    "parsed: note(c3) infers Pattern" {
        val inferrer = ExpressionTypeInferrer(registry())
        inferrer.inferType(parseExpr("note(\"c3\")"))?.simpleName shouldBe "Pattern"
    }

    // ── Parsed call chains ──────────────────────────────────────────────

    "parsed: Osc.sine().lowpass(1000) infers IgnitorDsl" {
        val inferrer = ExpressionTypeInferrer(registry())
        inferrer.inferType(parseExpr("Osc.sine().lowpass(1000)"))?.simpleName shouldBe "IgnitorDsl"
    }

    "parsed: Osc.sine().lowpass(1000).adsr(0.01, 0.2, 0.5, 0.5) infers IgnitorDsl" {
        val inferrer = ExpressionTypeInferrer(registry())
        inferrer.inferType(parseExpr("Osc.sine().lowpass(1000).adsr(0.01, 0.2, 0.5, 0.5)"))?.simpleName shouldBe "IgnitorDsl"
    }

    "parsed: note(c3).gain(0.5) infers Pattern" {
        val inferrer = ExpressionTypeInferrer(registry())
        inferrer.inferType(parseExpr("note(\"c3\").gain(0.5)"))?.simpleName shouldBe "Pattern"
    }

    // ── AstIndex integration: infer type of sub-expression at cursor ────

    "AstIndex nodeAt + infer: find Osc.sine() in a chain and infer its type" {
        val code = "Osc.sine().lowpass(1000)"
        val program = KlangScriptParser.parse(code)
        val astIndex = AstIndex.build(program, code)
        val inferrer = ExpressionTypeInferrer(registry())

        // Position inside "sine" (offset ~4-7)
        val node = astIndex.nodeAt(5)
        node shouldNotBe null

        // Walk up to find the MemberAccess or CallExpression
        // At "sine", the node should be the Identifier "sine" or the MemberAccess Osc.sine
        // The parent chain should reach CallExpression(Osc.sine(), [])
        var current: AstNode? = node
        var callExpr: CallExpression? = null
        while (current != null) {
            if (current is CallExpression) {
                callExpr = current
                break
            }
            current = astIndex.parentOf(current)
        }
        callExpr shouldNotBe null
        // This should be the inner CallExpression: Osc.sine()
        // Infer its type
        inferrer.inferType(callExpr!!)?.simpleName shouldBe "IgnitorDsl"
    }

    "AstIndex nodeAt: infer type of node found inside Osc.sine() call" {
        val code = "Osc.sine()"
        val program = KlangScriptParser.parse(code)
        val astIndex = AstIndex.build(program, code)
        val inferrer = ExpressionTypeInferrer(registry())

        // Find the node at a position inside "Osc" (offset 1)
        val node = astIndex.nodeAt(1)
        node shouldNotBe null

        // Walk up to find the nearest CallExpression
        var current: AstNode? = node
        var callExpr: CallExpression? = null
        while (current != null) {
            if (current is CallExpression) {
                callExpr = current; break
            }
            current = astIndex.parentOf(current)
        }
        callExpr shouldNotBe null
        inferrer.inferType(callExpr!!)?.simpleName shouldBe "IgnitorDsl"
    }

    // ── Receiver-aware hover: detect MemberAccess context ───────────────

    "Sprudel chain with import: note(c3).adsr() resolves in multiline code" {
        val reg = KlangDocsRegistry().apply {
            register(
                KlangSymbol(
                    name = "note", category = "tonal", library = "sprudel",
                    variants = listOf(
                        KlangCallable(
                            name = "note", receiver = null,
                            params = listOf(KlangParam(name = "note", type = KlangType("String"))),
                            returnType = KlangType("SprudelPattern")
                        )
                    )
                )
            )
            register(
                KlangSymbol(
                    name = "adsr", category = "dynamics", library = "sprudel",
                    variants = listOf(
                        KlangCallable(
                            name = "adsr", receiver = KlangType("SprudelPattern"),
                            params = listOf(KlangParam(name = "envelope", type = KlangType("String"))),
                            returnType = KlangType("SprudelPattern")
                        )
                    )
                )
            )
            register(
                KlangSymbol(
                    name = "adsr", category = "uncategorized", library = "stdlib",
                    variants = listOf(
                        KlangCallable(
                            name = "adsr", receiver = KlangType("IgnitorDsl"),
                            params = emptyList(), returnType = KlangType("IgnitorDsl")
                        )
                    )
                )
            )
        }
        // Multiline code mimicking real editor content
        val code = """
            import * from "sprudel"
            note("c3").adsr("0.01:0.2:0.5:0.5")
        """.trimIndent()
        val program = KlangScriptParser.parse(code)
        val astIndex = AstIndex.build(program, code)
        val inferrer = ExpressionTypeInferrer(reg)

        val adsrPos = code.indexOf("adsr") + 1
        val node = astIndex.nodeAt(adsrPos)
        node shouldNotBe null

        val memberAccess = when {
            node is MemberAccess && node.property == "adsr" -> node
            node is CallExpression && (node.callee as? MemberAccess)?.property == "adsr" ->
                node.callee

            else -> {
                // Walk up parents to find MemberAccess
                var current: AstNode? = node
                var found: MemberAccess? = null
                while (current != null) {
                    if (current is CallExpression && (current.callee as? MemberAccess)?.property == "adsr") {
                        found = current.callee; break
                    }
                    if (current is MemberAccess && current.property == "adsr") {
                        found = current; break
                    }
                    current = astIndex.parentOf(current)
                }
                found
            }
        }
        memberAccess shouldNotBe null
        val receiverType = inferrer.inferType(memberAccess!!.obj)
        receiverType?.simpleName shouldBe "SprudelPattern"
    }

    "Sprudel chain: note(c3).adsr() resolves receiver as SprudelPattern" {
        val reg = KlangDocsRegistry().apply {
            register(
                KlangSymbol(
                    name = "note", category = "tonal", library = "sprudel",
                    variants = listOf(
                        KlangCallable(
                            name = "note", receiver = null,
                            params = listOf(KlangParam(name = "note", type = KlangType("String"))),
                            returnType = KlangType("SprudelPattern")
                        )
                    )
                )
            )
            register(
                KlangSymbol(
                    name = "adsr", category = "dynamics", library = "sprudel",
                    variants = listOf(
                        KlangCallable(
                            name = "adsr", receiver = KlangType("SprudelPattern"),
                            params = listOf(KlangParam(name = "envelope", type = KlangType("String"))),
                            returnType = KlangType("SprudelPattern")
                        )
                    )
                )
            )
            // Also add the stdlib adsr to simulate the merged registry
            register(
                KlangSymbol(
                    name = "adsr", category = "uncategorized", library = "stdlib",
                    variants = listOf(
                        KlangCallable(
                            name = "adsr", receiver = KlangType("IgnitorDsl"),
                            params = emptyList(), returnType = KlangType("IgnitorDsl")
                        )
                    )
                )
            )
        }
        val code = """note("c3").adsr("0.01:0.2:0.5:0.5")"""
        val program = KlangScriptParser.parse(code)
        val astIndex = AstIndex.build(program, code)
        val inferrer = ExpressionTypeInferrer(reg)

        // Simulate what wordDocAt does: find node at "adsr" position
        val adsrPos = code.indexOf("adsr") + 1
        val node = astIndex.nodeAt(adsrPos)
        node shouldNotBe null

        // The wordDocAt algorithm: check if node is CallExpression with MemberAccess callee
        val memberAccess = when (node) {
            is MemberAccess if node.property == "adsr" -> node
            is CallExpression if (node.callee as? MemberAccess)?.property == "adsr" -> node.callee
            else -> null
        }
        memberAccess shouldNotBe null

        // Infer receiver type: should be SprudelPattern (from note() return type)
        val receiverType = inferrer.inferType(memberAccess!!.obj)
        receiverType?.simpleName shouldBe "SprudelPattern"

        // Look up the correct variant
        val symbol = reg.getSymbolWithReceiver("adsr", receiverType)
        symbol shouldNotBe null
        // Should have only the SprudelPattern variant, not the IgnitorDsl one
        symbol!!.variants.size shouldBe 1
        (symbol.variants[0] as KlangCallable).receiver?.simpleName shouldBe "SprudelPattern"
    }

    "AstIndex: detect MemberAccess for hover over lowpass in chain" {
        val code = "Osc.sine().lowpass(1000)"
        val program = KlangScriptParser.parse(code)
        val astIndex = AstIndex.build(program, code)
        val inferrer = ExpressionTypeInferrer(registry())
        val reg = registry()

        // "lowpass" starts at offset 11
        val lowpassPos = code.indexOf("lowpass") + 2 // somewhere inside "lowpass"
        val node = astIndex.nodeAt(lowpassPos)
        node shouldNotBe null

        // Find the MemberAccess containing this position
        var memberAccess: MemberAccess? = null
        var current: AstNode? = node
        while (current != null) {
            if (current is MemberAccess && current.property == "lowpass") {
                memberAccess = current
                break
            }
            current = astIndex.parentOf(current)
        }
        memberAccess shouldNotBe null

        // Infer receiver type
        val receiverType = inferrer.inferType(memberAccess!!.obj)
        receiverType?.simpleName shouldBe "IgnitorDsl"

        // Look up the correct symbol variant
        val symbol = reg.getSymbolWithReceiver("lowpass", receiverType)
        symbol shouldNotBe null
        symbol!!.variants.size shouldBe 1
        (symbol.variants[0] as KlangCallable).receiver?.simpleName shouldBe "IgnitorDsl"
    }
})

package io.peekandpoke.klang.script.intel

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.script.ast.ArrayLiteral
import io.peekandpoke.klang.script.ast.ArrowFunction
import io.peekandpoke.klang.script.ast.ArrowFunctionBody
import io.peekandpoke.klang.script.ast.BinaryOperation
import io.peekandpoke.klang.script.ast.BinaryOperator
import io.peekandpoke.klang.script.ast.BooleanLiteral
import io.peekandpoke.klang.script.ast.CallExpression
import io.peekandpoke.klang.script.ast.Identifier
import io.peekandpoke.klang.script.ast.MemberAccess
import io.peekandpoke.klang.script.ast.NullLiteral
import io.peekandpoke.klang.script.ast.NumberLiteral
import io.peekandpoke.klang.script.ast.ObjectLiteral
import io.peekandpoke.klang.script.ast.StringLiteral
import io.peekandpoke.klang.script.ast.TemplateLiteral
import io.peekandpoke.klang.script.ast.TemplatePart
import io.peekandpoke.klang.script.docs.KlangDocsRegistry
import io.peekandpoke.klang.script.types.KlangCallable
import io.peekandpoke.klang.script.types.KlangParam
import io.peekandpoke.klang.script.types.KlangProperty
import io.peekandpoke.klang.script.types.KlangSymbol
import io.peekandpoke.klang.script.types.KlangType

class ExpressionTypeInferrerTest : StringSpec({

    fun registry(): KlangDocsRegistry = KlangDocsRegistry().apply {
        // Object: Osc (property with type "Osc")
        register(
            KlangSymbol(
                name = "Osc", category = "object", library = "stdlib",
                variants = listOf(KlangProperty(name = "Osc", type = KlangType("Osc")))
            )
        )
        // Object: Math
        register(
            KlangSymbol(
                name = "Math", category = "object", library = "stdlib",
                variants = listOf(KlangProperty(name = "Math", type = KlangType("Math")))
            )
        )
        // Osc.sine() -> IgnitorDsl
        register(
            KlangSymbol(
                name = "sine", category = "oscillator", library = "stdlib",
                variants = listOf(
                    KlangCallable(
                        name = "sine",
                        receiver = KlangType("Osc"),
                        params = emptyList(),
                        returnType = KlangType("IgnitorDsl"),
                    )
                )
            )
        )
        // IgnitorDsl.lowpass() -> IgnitorDsl
        register(
            KlangSymbol(
                name = "lowpass", category = "filter", library = "stdlib",
                variants = listOf(
                    KlangCallable(
                        name = "lowpass",
                        receiver = KlangType("IgnitorDsl"),
                        params = listOf(KlangParam(name = "cutoffHz", type = KlangType("Number"))),
                        returnType = KlangType("IgnitorDsl"),
                    )
                )
            )
        )
        // Math.sqrt() -> Number
        register(
            KlangSymbol(
                name = "sqrt", category = "math", library = "stdlib",
                variants = listOf(
                    KlangCallable(
                        name = "sqrt",
                        receiver = KlangType("Math"),
                        params = listOf(KlangParam(name = "x", type = KlangType("Number"))),
                        returnType = KlangType("Number"),
                    )
                )
            )
        )
        // Top-level function: note() -> Pattern
        register(
            KlangSymbol(
                name = "note", category = "pattern", library = "sprudel",
                variants = listOf(
                    KlangCallable(
                        name = "note",
                        receiver = null,
                        params = listOf(KlangParam(name = "pattern", type = KlangType("String"))),
                        returnType = KlangType("Pattern"),
                    )
                )
            )
        )
        // Pattern.gain() -> Pattern
        register(
            KlangSymbol(
                name = "gain", category = "effect", library = "sprudel",
                variants = listOf(
                    KlangCallable(
                        name = "gain",
                        receiver = KlangType("Pattern"),
                        params = listOf(KlangParam(name = "amount", type = KlangType("Number"))),
                        returnType = KlangType("Pattern"),
                    )
                )
            )
        )
    }

    // ── Literals ────────────────────────────────────────────────────────

    "NumberLiteral infers Number" {
        val inferrer = ExpressionTypeInferrer(registry())
        inferrer.inferType(NumberLiteral(42.0))?.simpleName shouldBe "Number"
    }

    "StringLiteral infers String" {
        val inferrer = ExpressionTypeInferrer(registry())
        inferrer.inferType(StringLiteral("hello"))?.simpleName shouldBe "String"
    }

    "BooleanLiteral infers Boolean" {
        val inferrer = ExpressionTypeInferrer(registry())
        inferrer.inferType(BooleanLiteral(true))?.simpleName shouldBe "Boolean"
    }

    "ArrayLiteral infers Array" {
        val inferrer = ExpressionTypeInferrer(registry())
        inferrer.inferType(ArrayLiteral(emptyList()))?.simpleName shouldBe "Array"
    }

    "ObjectLiteral infers Object" {
        val inferrer = ExpressionTypeInferrer(registry())
        inferrer.inferType(ObjectLiteral(emptyList()))?.simpleName shouldBe "Object"
    }

    "NullLiteral infers null" {
        val inferrer = ExpressionTypeInferrer(registry())
        inferrer.inferType(NullLiteral) shouldBe null
    }

    "TemplateLiteral infers String" {
        val inferrer = ExpressionTypeInferrer(registry())
        inferrer.inferType(TemplateLiteral(parts = listOf(TemplatePart.Text("hi"))))?.simpleName shouldBe "String"
    }

    // ── Identifiers ─────────────────────────────────────────────────────

    "Identifier Osc infers type Osc" {
        val inferrer = ExpressionTypeInferrer(registry())
        inferrer.inferType(Identifier("Osc"))?.simpleName shouldBe "Osc"
    }

    "Identifier Math infers type Math" {
        val inferrer = ExpressionTypeInferrer(registry())
        inferrer.inferType(Identifier("Math"))?.simpleName shouldBe "Math"
    }

    "Unknown identifier infers null" {
        val inferrer = ExpressionTypeInferrer(registry())
        inferrer.inferType(Identifier("unknownVar")) shouldBe null
    }

    // ── Top-level calls ─────────────────────────────────────────────────

    "Top-level call note() infers Pattern" {
        val inferrer = ExpressionTypeInferrer(registry())
        val call = CallExpression(
            callee = Identifier("note"),
            arguments = listOf(StringLiteral("c3")),
        )
        inferrer.inferType(call)?.simpleName shouldBe "Pattern"
    }

    // ── Method calls ────────────────────────────────────────────────────

    "Osc.sine() infers IgnitorDsl" {
        val inferrer = ExpressionTypeInferrer(registry())
        val call = CallExpression(
            callee = MemberAccess(obj = Identifier("Osc"), property = "sine"),
            arguments = emptyList(),
        )
        inferrer.inferType(call)?.simpleName shouldBe "IgnitorDsl"
    }

    "Math.sqrt(16) infers Number" {
        val inferrer = ExpressionTypeInferrer(registry())
        val call = CallExpression(
            callee = MemberAccess(obj = Identifier("Math"), property = "sqrt"),
            arguments = listOf(NumberLiteral(16.0)),
        )
        inferrer.inferType(call)?.simpleName shouldBe "Number"
    }

    // ── Call chains ─────────────────────────────────────────────────────

    "Osc.sine().lowpass(1000) infers IgnitorDsl" {
        val inferrer = ExpressionTypeInferrer(registry())
        val chain = CallExpression(
            callee = MemberAccess(
                obj = CallExpression(
                    callee = MemberAccess(obj = Identifier("Osc"), property = "sine"),
                    arguments = emptyList(),
                ),
                property = "lowpass",
            ),
            arguments = listOf(NumberLiteral(1000.0)),
        )
        inferrer.inferType(chain)?.simpleName shouldBe "IgnitorDsl"
    }

    "note(c3).gain(0.5) infers Pattern" {
        val inferrer = ExpressionTypeInferrer(registry())
        val chain = CallExpression(
            callee = MemberAccess(
                obj = CallExpression(
                    callee = Identifier("note"),
                    arguments = listOf(StringLiteral("c3")),
                ),
                property = "gain",
            ),
            arguments = listOf(NumberLiteral(0.5)),
        )
        inferrer.inferType(chain)?.simpleName shouldBe "Pattern"
    }

    // ── Unknown method on known type returns null ───────────────────────

    "Osc.unknownMethod() infers null" {
        val inferrer = ExpressionTypeInferrer(registry())
        val call = CallExpression(
            callee = MemberAccess(obj = Identifier("Osc"), property = "unknownMethod"),
            arguments = emptyList(),
        )
        inferrer.inferType(call) shouldBe null
    }

    // ── Unsupported expressions return null ─────────────────────────────

    "BinaryOperation infers null" {
        val inferrer = ExpressionTypeInferrer(registry())
        inferrer.inferType(BinaryOperation(NumberLiteral(1.0), BinaryOperator.ADD, NumberLiteral(2.0))) shouldBe null
    }

    // ── Chain breakage propagates null ──────────────────────────────────

    "Chain breaks at unknown method and returns null" {
        val inferrer = ExpressionTypeInferrer(registry())
        // Osc.sine().unknownMethod().lowpass(1000) — unknownMethod breaks the chain
        val chain = CallExpression(
            callee = MemberAccess(
                obj = CallExpression(
                    callee = MemberAccess(
                        obj = CallExpression(
                            callee = MemberAccess(obj = Identifier("Osc"), property = "sine"),
                            arguments = emptyList(),
                        ),
                        property = "unknownMethod",
                    ),
                    arguments = emptyList(),
                ),
                property = "lowpass",
            ),
            arguments = listOf(NumberLiteral(1000.0)),
        )
        inferrer.inferType(chain) shouldBe null
    }

    // ── Identifier with only callable variants returns null ─────────────

    "Identifier for a symbol with only callable variants infers null" {
        val inferrer = ExpressionTypeInferrer(registry())
        // "note" is a top-level function (KlangCallable, receiver=null) — bare identifier should not produce a type
        inferrer.inferType(Identifier("note")) shouldBe null
    }

    // ── IIFE / arrow function callee returns null ───────────────────────

    "CallExpression with non-Identifier non-MemberAccess callee infers null" {
        val inferrer = ExpressionTypeInferrer(registry())
        val iife = CallExpression(
            callee = ArrowFunction(
                parameters = emptyList(),
                body = ArrowFunctionBody.ExpressionBody(NumberLiteral(42.0)),
            ),
            arguments = emptyList(),
        )
        inferrer.inferType(iife) shouldBe null
    }

    // ── Property access on object ───────────────────────────────────────

    "MemberAccess resolves property with matching owner" {
        val reg = KlangDocsRegistry().apply {
            register(
                KlangSymbol(
                    name = "Osc", category = "object", library = "stdlib",
                    variants = listOf(KlangProperty(name = "Osc", type = KlangType("Osc")))
                )
            )
            register(
                KlangSymbol(
                    name = "sampleRate", category = "property", library = "stdlib",
                    variants = listOf(
                        KlangProperty(
                            name = "sampleRate",
                            owner = KlangType("Osc"),
                            type = KlangType("Number"),
                        )
                    )
                )
            )
        }
        val inferrer = ExpressionTypeInferrer(reg)
        // Osc.sampleRate should resolve to Number via property lookup
        inferrer.inferType(MemberAccess(obj = Identifier("Osc"), property = "sampleRate"))?.simpleName shouldBe "Number"
    }
})

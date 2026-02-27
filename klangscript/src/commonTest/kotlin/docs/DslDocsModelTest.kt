package io.peekandpoke.klang.script.docs

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.script.types.KlangCallable
import io.peekandpoke.klang.script.types.KlangObject
import io.peekandpoke.klang.script.types.KlangParam
import io.peekandpoke.klang.script.types.KlangType

/**
 * Unit tests for the structured DSL documentation model:
 * [KlangType], [KlangParam], [KlangCallable], and [KlangObject].
 *
 * These tests verify the render logic and data-model invariants
 * independently of KSP / code generation.
 */
class DslDocsModelTest : StringSpec({

    // ─── KlangType ──────────────────────────────────────────────────────────

    "KlangType renders simple name" {
        KlangType("StrudelPattern").render() shouldBe "StrudelPattern"
    }

    "KlangType renders nullable type with trailing question mark" {
        KlangType("String", isNullable = true).render() shouldBe "String?"
    }

    "KlangType with isTypeAlias=true still renders just the alias name" {
        // The alias flag is metadata for the UI; it does not change the rendered string
        KlangType("PatternLike", isTypeAlias = true).render() shouldBe "PatternLike"
    }

    "KlangType with both isTypeAlias and isNullable renders alias name with question mark" {
        KlangType("PatternLike", isTypeAlias = true, isNullable = true).render() shouldBe "PatternLike?"
    }

    "KlangType.toString delegates to render so string interpolation works" {
        val t = KlangType("StrudelPattern")
        "$t" shouldBe "StrudelPattern"
        t.toString() shouldBe t.render()
    }

    // ─── KlangParam ──────────────────────────────────────────────────────────

    "KlangParam renders plain name: type" {
        KlangParam("amount", KlangType("Double")).render() shouldBe "amount: Double"
    }

    "KlangParam renders vararg prefix" {
        KlangParam("patterns", KlangType("PatternLike"), isVararg = true)
            .render() shouldBe "vararg patterns: PatternLike"
    }

    "KlangParam renders nullable type" {
        KlangParam("label", KlangType("String", isNullable = true))
            .render() shouldBe "label: String?"
    }

    "KlangParam renders vararg + nullable type" {
        KlangParam("args", KlangType("Any", isNullable = true), isVararg = true)
            .render() shouldBe "vararg args: Any?"
    }

    "KlangParam description is accessible and defaults to empty string" {
        val p = KlangParam("x", KlangType("Int"))
        p.description shouldBe ""

        val p2 = KlangParam("x", KlangType("Int"), description = "The x value")
        p2.description shouldBe "The x value"
    }

    // ─── KlangObject — property / object mode (no parens) ───────────────────

    "KlangObject.signature renders as 'name: type' (no parens)" {
        KlangObject(
            name = "sine",
            type = KlangType("StrudelPattern"),
        ).signature shouldBe "sine: StrudelPattern"
    }

    "KlangObject.signature with owner renders with dot notation" {
        KlangObject(
            name = "fmh",
            owner = KlangType("StrudelPattern"),
            type = KlangType("StrudelPattern"),
        ).signature shouldBe "StrudelPattern.fmh: StrudelPattern"
    }

    // ─── KlangCallable — callable mode (parens) ──────────────────────────────

    "KlangCallable.signature with emptyList params renders with empty parens" {
        KlangCallable(
            name = "accelerate",
            params = emptyList(),
            returnType = KlangType("StrudelPattern"),
        ).signature shouldBe "accelerate(): StrudelPattern"
    }

    "KlangCallable.signature extension callable with emptyList params renders receiver.name()" {
        KlangCallable(
            name = "accelerate",
            receiver = KlangType("StrudelPattern"),
            params = emptyList(),
            returnType = KlangType("StrudelPattern"),
        ).signature shouldBe "StrudelPattern.accelerate(): StrudelPattern"
    }

    "KlangCallable.signature with one param renders full signature" {
        KlangCallable(
            name = "gain",
            params = listOf(KlangParam("value", KlangType("PatternLike"))),
            returnType = KlangType("StrudelPattern"),
        ).signature shouldBe "gain(value: PatternLike): StrudelPattern"
    }

    "KlangCallable.signature with vararg param renders vararg keyword" {
        KlangCallable(
            name = "seq",
            params = listOf(
                KlangParam("patterns", KlangType("PatternLike"), isVararg = true),
            ),
            returnType = KlangType("StrudelPattern"),
        ).signature shouldBe "seq(vararg patterns: PatternLike): StrudelPattern"
    }

    "KlangCallable.signature with multiple params renders comma-separated list" {
        KlangCallable(
            name = "echo",
            params = listOf(
                KlangParam("times", KlangType("Int")),
                KlangParam("decay", KlangType("Double")),
                KlangParam("delay", KlangType("Double")),
            ),
            returnType = KlangType("StrudelPattern"),
        ).signature shouldBe "echo(times: Int, decay: Double, delay: Double): StrudelPattern"
    }

    "KlangCallable.signature extension function with params renders receiver.name(...): ReturnType" {
        KlangCallable(
            name = "seq",
            receiver = KlangType("StrudelPattern"),
            params = listOf(
                KlangParam("patterns", KlangType("PatternLike"), isVararg = true),
            ),
            returnType = KlangType("StrudelPattern"),
        ).signature shouldBe "StrudelPattern.seq(vararg patterns: PatternLike): StrudelPattern"
    }

    "KlangCallable.signature String extension function renders correctly" {
        KlangCallable(
            name = "seq",
            receiver = KlangType("String"),
            params = listOf(
                KlangParam("patterns", KlangType("PatternLike"), isVararg = true),
            ),
            returnType = KlangType("StrudelPattern"),
        ).signature shouldBe "String.seq(vararg patterns: PatternLike): StrudelPattern"
    }

    "KlangCallable.signature with no return type omits colon" {
        KlangCallable(
            name = "doSomething",
            params = emptyList(),
        ).signature shouldBe "doSomething()"
    }

    // ─── KlangCallable computed properties ──────────────────────────────────

    "KlangCallable.signature is computed from name, params, returnType" {
        val callable = KlangCallable(
            name = "seq",
            params = listOf(
                KlangParam("patterns", KlangType("PatternLike"), isVararg = true),
            ),
            returnType = KlangType("StrudelPattern"),
            description = "Creates a sequence pattern.",
        )

        callable.signature shouldBe "seq(vararg patterns: PatternLike): StrudelPattern"
    }

    "KlangObject.params is always empty (object has no params)" {
        val obj = KlangObject(
            name = "sine",
            type = KlangType("StrudelPattern"),
            description = "Sine oscillator.",
        )

        obj.signature shouldBe "sine: StrudelPattern"
    }

    "KlangCallable with emptyList params has empty params" {
        val callable = KlangCallable(
            name = "accelerate",
            params = emptyList(),
            returnType = KlangType("StrudelPattern"),
            description = "Pitch ramp.",
        )

        callable.params.shouldBeEmpty()
        callable.signature shouldBe "accelerate(): StrudelPattern"
    }

    "KlangCallable.params reflects KlangParam list" {
        val paramModel = KlangParam(
            name = "patterns",
            type = KlangType("PatternLike", isTypeAlias = true),
            isVararg = true,
            description = "Patterns to sequence.",
        )
        val callable = KlangCallable(
            name = "seq",
            params = listOf(paramModel),
            returnType = KlangType("StrudelPattern"),
            description = "Creates a sequence.",
        )

        callable.params shouldHaveSize 1
        callable.params[0].name shouldBe "patterns"
        callable.params[0].type.simpleName shouldBe "PatternLike"
        callable.params[0].type.isTypeAlias shouldBe true
        callable.params[0].isVararg shouldBe true
        callable.params[0].description shouldBe "Patterns to sequence."
    }

    "KlangCallable samples and returnDoc default to empty" {
        val callable = KlangCallable(
            name = "foo",
            params = emptyList(),
            description = "A function.",
        )

        callable.returnDoc shouldBe ""
        callable.samples.shouldBeEmpty()
    }

    "KlangCallable with samples and returnDoc stores them correctly" {
        val callable = KlangCallable(
            name = "seq",
            params = emptyList(),
            description = "A function.",
            returnDoc = "A new pattern.",
            samples = listOf("""seq("a b c").note()""", """seq("bd", "sd").s()"""),
        )

        callable.returnDoc shouldBe "A new pattern."
        callable.samples shouldHaveSize 2
        callable.samples[0] shouldBe """seq("a b c").note()"""
        callable.samples[1] shouldBe """seq("bd", "sd").s()"""
    }
})

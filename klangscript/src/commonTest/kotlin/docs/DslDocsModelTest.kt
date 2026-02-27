package io.peekandpoke.klang.script.docs

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.script.types.*

/**
 * Unit tests for the structured DSL documentation model:
 * [KlangType], [KlangParam], [KlangFunSignature], and [KlangFunVariant].
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

    // ─── KlangFunSignature — property / object mode (params = null) ────────────

    "KlangFunSignature with params=null renders as 'name: ReturnType' (no parens)" {
        KlangFunSignature(
            name = "sine",
            returnType = KlangType("StrudelPattern"),
        ).render() shouldBe "sine: StrudelPattern"
    }

    "KlangFunSignature with params=null and no return type renders as bare name" {
        KlangFunSignature(name = "silence").render() shouldBe "silence"
    }

    "KlangFunSignature extension property (params=null with receiver) renders with dot notation" {
        KlangFunSignature(
            name = "fmh",
            receiver = KlangType("StrudelPattern"),
            returnType = KlangType("StrudelPattern"),
        ).render() shouldBe "StrudelPattern.fmh: StrudelPattern"
    }

    // ─── KlangFunSignature — callable / unknown params mode (params = emptyList) ─

    "KlangFunSignature with emptyList params renders with empty parens" {
        KlangFunSignature(
            name = "accelerate",
            params = emptyList(),
            returnType = KlangType("StrudelPattern"),
        ).render() shouldBe "accelerate(): StrudelPattern"
    }

    "KlangFunSignature extension callable with emptyList params renders receiver.name()" {
        KlangFunSignature(
            name = "accelerate",
            receiver = KlangType("StrudelPattern"),
            params = emptyList(),
            returnType = KlangType("StrudelPattern"),
        ).render() shouldBe "StrudelPattern.accelerate(): StrudelPattern"
    }

    // ─── KlangFunSignature — full param list mode (params = [...]) ──────────────

    "KlangFunSignature with one param renders full signature" {
        KlangFunSignature(
            name = "gain",
            params = listOf(KlangParam("value", KlangType("PatternLike"))),
            returnType = KlangType("StrudelPattern"),
        ).render() shouldBe "gain(value: PatternLike): StrudelPattern"
    }

    "KlangFunSignature with vararg param renders vararg keyword" {
        KlangFunSignature(
            name = "seq",
            params = listOf(
                KlangParam("patterns", KlangType("PatternLike"), isVararg = true),
            ),
            returnType = KlangType("StrudelPattern"),
        ).render() shouldBe "seq(vararg patterns: PatternLike): StrudelPattern"
    }

    "KlangFunSignature with multiple params renders comma-separated list" {
        KlangFunSignature(
            name = "echo",
            params = listOf(
                KlangParam("times", KlangType("Int")),
                KlangParam("decay", KlangType("Double")),
                KlangParam("delay", KlangType("Double")),
            ),
            returnType = KlangType("StrudelPattern"),
        ).render() shouldBe "echo(times: Int, decay: Double, delay: Double): StrudelPattern"
    }

    "KlangFunSignature extension function with params renders receiver.name(...): ReturnType" {
        KlangFunSignature(
            name = "seq",
            receiver = KlangType("StrudelPattern"),
            params = listOf(
                KlangParam("patterns", KlangType("PatternLike"), isVararg = true),
            ),
            returnType = KlangType("StrudelPattern"),
        ).render() shouldBe "StrudelPattern.seq(vararg patterns: PatternLike): StrudelPattern"
    }

    "KlangFunSignature String extension function renders correctly" {
        KlangFunSignature(
            name = "seq",
            receiver = KlangType("String"),
            params = listOf(
                KlangParam("patterns", KlangType("PatternLike"), isVararg = true),
            ),
            returnType = KlangType("StrudelPattern"),
        ).render() shouldBe "String.seq(vararg patterns: PatternLike): StrudelPattern"
    }

    "KlangFunSignature with no return type omits colon" {
        KlangFunSignature(
            name = "doSomething",
            params = emptyList(),
        ).render() shouldBe "doSomething()"
    }

    // ─── KlangFunVariant computed properties ──────────────────────────────────────

    "KlangFunVariant.signature is computed from signatureModel.render()" {
        val variant = KlangFunVariant(
            type = KlangFunKind.TOP_LEVEL,
            signatureModel = KlangFunSignature(
                name = "seq",
                params = listOf(
                    KlangParam("patterns", KlangType("PatternLike"), isVararg = true),
                ),
                returnType = KlangType("StrudelPattern"),
            ),
            description = "Creates a sequence pattern.",
        )

        variant.signature shouldBe "seq(vararg patterns: PatternLike): StrudelPattern"
    }

    "KlangFunVariant.params returns emptyList when signatureModel.params is null (object mode)" {
        val variant = KlangFunVariant(
            type = KlangFunKind.OBJECT,
            signatureModel = KlangFunSignature(
                name = "sine",
                returnType = KlangType("StrudelPattern"),
            ),
            description = "Sine oscillator.",
        )

        variant.params.shouldBeEmpty()
        variant.signature shouldBe "sine: StrudelPattern"
    }

    "KlangFunVariant.params returns emptyList when signatureModel.params is emptyList (callable mode)" {
        val variant = KlangFunVariant(
            type = KlangFunKind.TOP_LEVEL,
            signatureModel = KlangFunSignature(
                name = "accelerate",
                params = emptyList(),
                returnType = KlangType("StrudelPattern"),
            ),
            description = "Pitch ramp.",
        )

        variant.params.shouldBeEmpty()
        variant.signature shouldBe "accelerate(): StrudelPattern"
    }

    "KlangFunVariant.params reflects KlangParam list from signatureModel" {
        val paramModel = KlangParam(
            name = "patterns",
            type = KlangType("PatternLike", isTypeAlias = true),
            isVararg = true,
            description = "Patterns to sequence.",
        )
        val variant = KlangFunVariant(
            type = KlangFunKind.TOP_LEVEL,
            signatureModel = KlangFunSignature(
                name = "seq",
                params = listOf(paramModel),
                returnType = KlangType("StrudelPattern"),
            ),
            description = "Creates a sequence.",
        )

        variant.params shouldHaveSize 1
        variant.params[0].name shouldBe "patterns"
        variant.params[0].type.simpleName shouldBe "PatternLike"
        variant.params[0].type.isTypeAlias shouldBe true
        variant.params[0].isVararg shouldBe true
        variant.params[0].description shouldBe "Patterns to sequence."
    }

    "KlangFunVariant samples and returnDoc default to empty" {
        val variant = KlangFunVariant(
            type = KlangFunKind.TOP_LEVEL,
            signatureModel = KlangFunSignature(name = "foo", params = emptyList()),
            description = "A function.",
        )

        variant.returnDoc shouldBe ""
        variant.samples.shouldBeEmpty()
    }

    "KlangFunVariant with samples and returnDoc stores them correctly" {
        val variant = KlangFunVariant(
            type = KlangFunKind.TOP_LEVEL,
            signatureModel = KlangFunSignature(name = "seq", params = emptyList()),
            description = "A function.",
            returnDoc = "A new pattern.",
            samples = listOf("""seq("a b c").note()""", """seq("bd", "sd").s()"""),
        )

        variant.returnDoc shouldBe "A new pattern."
        variant.samples shouldHaveSize 2
        variant.samples[0] shouldBe """seq("a b c").note()"""
        variant.samples[1] shouldBe """seq("bd", "sd").s()"""
    }
})

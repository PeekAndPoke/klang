package io.peekandpoke.klang.script.docs

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe

/**
 * Unit tests for the structured DSL documentation model:
 * [TypeModel], [ParamModel], [SignatureModel], and [VariantDoc].
 *
 * These tests verify the render logic and data-model invariants
 * independently of KSP / code generation.
 */
class DslDocsModelTest : StringSpec({

    // ─── TypeModel ──────────────────────────────────────────────────────────

    "TypeModel renders simple name" {
        TypeModel("StrudelPattern").render() shouldBe "StrudelPattern"
    }

    "TypeModel renders nullable type with trailing question mark" {
        TypeModel("String", isNullable = true).render() shouldBe "String?"
    }

    "TypeModel with isTypeAlias=true still renders just the alias name" {
        // The alias flag is metadata for the UI; it does not change the rendered string
        TypeModel("PatternLike", isTypeAlias = true).render() shouldBe "PatternLike"
    }

    "TypeModel with both isTypeAlias and isNullable renders alias name with question mark" {
        TypeModel("PatternLike", isTypeAlias = true, isNullable = true).render() shouldBe "PatternLike?"
    }

    "TypeModel.toString delegates to render so string interpolation works" {
        val t = TypeModel("StrudelPattern")
        "$t" shouldBe "StrudelPattern"
        t.toString() shouldBe t.render()
    }

    // ─── ParamModel ──────────────────────────────────────────────────────────

    "ParamModel renders plain name: type" {
        ParamModel("amount", TypeModel("Double")).render() shouldBe "amount: Double"
    }

    "ParamModel renders vararg prefix" {
        ParamModel("patterns", TypeModel("PatternLike"), isVararg = true)
            .render() shouldBe "vararg patterns: PatternLike"
    }

    "ParamModel renders nullable type" {
        ParamModel("label", TypeModel("String", isNullable = true))
            .render() shouldBe "label: String?"
    }

    "ParamModel renders vararg + nullable type" {
        ParamModel("args", TypeModel("Any", isNullable = true), isVararg = true)
            .render() shouldBe "vararg args: Any?"
    }

    "ParamModel description is accessible and defaults to empty string" {
        val p = ParamModel("x", TypeModel("Int"))
        p.description shouldBe ""

        val p2 = ParamModel("x", TypeModel("Int"), description = "The x value")
        p2.description shouldBe "The x value"
    }

    // ─── SignatureModel — property / object mode (params = null) ────────────

    "SignatureModel with params=null renders as 'name: ReturnType' (no parens)" {
        SignatureModel(
            name = "sine",
            returnType = TypeModel("StrudelPattern"),
        ).render() shouldBe "sine: StrudelPattern"
    }

    "SignatureModel with params=null and no return type renders as bare name" {
        SignatureModel(name = "silence").render() shouldBe "silence"
    }

    "SignatureModel extension property (params=null with receiver) renders with dot notation" {
        SignatureModel(
            name = "fmh",
            receiver = TypeModel("StrudelPattern"),
            returnType = TypeModel("StrudelPattern"),
        ).render() shouldBe "StrudelPattern.fmh: StrudelPattern"
    }

    // ─── SignatureModel — callable / unknown params mode (params = emptyList) ─

    "SignatureModel with emptyList params renders with empty parens" {
        SignatureModel(
            name = "accelerate",
            params = emptyList(),
            returnType = TypeModel("StrudelPattern"),
        ).render() shouldBe "accelerate(): StrudelPattern"
    }

    "SignatureModel extension callable with emptyList params renders receiver.name()" {
        SignatureModel(
            name = "accelerate",
            receiver = TypeModel("StrudelPattern"),
            params = emptyList(),
            returnType = TypeModel("StrudelPattern"),
        ).render() shouldBe "StrudelPattern.accelerate(): StrudelPattern"
    }

    // ─── SignatureModel — full param list mode (params = [...]) ──────────────

    "SignatureModel with one param renders full signature" {
        SignatureModel(
            name = "gain",
            params = listOf(ParamModel("value", TypeModel("PatternLike"))),
            returnType = TypeModel("StrudelPattern"),
        ).render() shouldBe "gain(value: PatternLike): StrudelPattern"
    }

    "SignatureModel with vararg param renders vararg keyword" {
        SignatureModel(
            name = "seq",
            params = listOf(
                ParamModel("patterns", TypeModel("PatternLike"), isVararg = true),
            ),
            returnType = TypeModel("StrudelPattern"),
        ).render() shouldBe "seq(vararg patterns: PatternLike): StrudelPattern"
    }

    "SignatureModel with multiple params renders comma-separated list" {
        SignatureModel(
            name = "echo",
            params = listOf(
                ParamModel("times", TypeModel("Int")),
                ParamModel("decay", TypeModel("Double")),
                ParamModel("delay", TypeModel("Double")),
            ),
            returnType = TypeModel("StrudelPattern"),
        ).render() shouldBe "echo(times: Int, decay: Double, delay: Double): StrudelPattern"
    }

    "SignatureModel extension function with params renders receiver.name(...): ReturnType" {
        SignatureModel(
            name = "seq",
            receiver = TypeModel("StrudelPattern"),
            params = listOf(
                ParamModel("patterns", TypeModel("PatternLike"), isVararg = true),
            ),
            returnType = TypeModel("StrudelPattern"),
        ).render() shouldBe "StrudelPattern.seq(vararg patterns: PatternLike): StrudelPattern"
    }

    "SignatureModel String extension function renders correctly" {
        SignatureModel(
            name = "seq",
            receiver = TypeModel("String"),
            params = listOf(
                ParamModel("patterns", TypeModel("PatternLike"), isVararg = true),
            ),
            returnType = TypeModel("StrudelPattern"),
        ).render() shouldBe "String.seq(vararg patterns: PatternLike): StrudelPattern"
    }

    "SignatureModel with no return type omits colon" {
        SignatureModel(
            name = "doSomething",
            params = emptyList(),
        ).render() shouldBe "doSomething()"
    }

    // ─── VariantDoc computed properties ──────────────────────────────────────

    "VariantDoc.signature is computed from signatureModel.render()" {
        val variant = VariantDoc(
            type = DslType.TOP_LEVEL,
            signatureModel = SignatureModel(
                name = "seq",
                params = listOf(
                    ParamModel("patterns", TypeModel("PatternLike"), isVararg = true),
                ),
                returnType = TypeModel("StrudelPattern"),
            ),
            description = "Creates a sequence pattern.",
        )

        variant.signature shouldBe "seq(vararg patterns: PatternLike): StrudelPattern"
    }

    "VariantDoc.params returns emptyList when signatureModel.params is null (object mode)" {
        val variant = VariantDoc(
            type = DslType.OBJECT,
            signatureModel = SignatureModel(
                name = "sine",
                returnType = TypeModel("StrudelPattern"),
            ),
            description = "Sine oscillator.",
        )

        variant.params.shouldBeEmpty()
        variant.signature shouldBe "sine: StrudelPattern"
    }

    "VariantDoc.params returns emptyList when signatureModel.params is emptyList (callable mode)" {
        val variant = VariantDoc(
            type = DslType.TOP_LEVEL,
            signatureModel = SignatureModel(
                name = "accelerate",
                params = emptyList(),
                returnType = TypeModel("StrudelPattern"),
            ),
            description = "Pitch ramp.",
        )

        variant.params.shouldBeEmpty()
        variant.signature shouldBe "accelerate(): StrudelPattern"
    }

    "VariantDoc.params reflects ParamModel list from signatureModel" {
        val paramModel = ParamModel(
            name = "patterns",
            type = TypeModel("PatternLike", isTypeAlias = true),
            isVararg = true,
            description = "Patterns to sequence.",
        )
        val variant = VariantDoc(
            type = DslType.TOP_LEVEL,
            signatureModel = SignatureModel(
                name = "seq",
                params = listOf(paramModel),
                returnType = TypeModel("StrudelPattern"),
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

    "VariantDoc samples and returnDoc default to empty" {
        val variant = VariantDoc(
            type = DslType.TOP_LEVEL,
            signatureModel = SignatureModel(name = "foo", params = emptyList()),
            description = "A function.",
        )

        variant.returnDoc shouldBe ""
        variant.samples.shouldBeEmpty()
    }

    "VariantDoc with samples and returnDoc stores them correctly" {
        val variant = VariantDoc(
            type = DslType.TOP_LEVEL,
            signatureModel = SignatureModel(name = "seq", params = emptyList()),
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

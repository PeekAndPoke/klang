package io.peekandpoke.klang.strudel.lang.docs

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldHaveAtLeastSize
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.peekandpoke.klang.script.docs.DslDocsRegistry
import io.peekandpoke.klang.script.docs.DslType
import io.peekandpoke.klang.strudel.lang.initStrudelLang
import io.kotest.matchers.string.shouldContain as stringShouldContain

class StrudelDocsSpec : StringSpec({

    beforeTest {
        // Ensure Strudel is initialized and docs are registered
        initStrudelLang()
    }

    "seq documentation should be registered" {
        val seqDoc = DslDocsRegistry.global.get("seq")

        seqDoc shouldNotBe null
        seqDoc!!.name shouldBe "seq"
        seqDoc.category shouldBe "structural"
        seqDoc.library shouldBe "strudel"
        seqDoc.tags shouldContain "sequence"
    }

    "seq should have 3 variants" {
        val seqDoc = DslDocsRegistry.global.get("seq")!!

        seqDoc.variants shouldHaveSize 3

        // Check variant types
        val variantTypes = seqDoc.variants.map { it.type }
        variantTypes shouldContain DslType.TOP_LEVEL
        variantTypes shouldContain DslType.EXTENSION_METHOD
    }

    "seq top-level variant should have complete documentation" {
        val seqDoc = DslDocsRegistry.global.get("seq")!!
        val topLevel = seqDoc.variants.first { it.type == DslType.TOP_LEVEL }

        topLevel.signature shouldBe "seq(vararg patterns: PatternLike): StrudelPattern"
        topLevel.description.stringShouldContain("one cycle")
        topLevel.params shouldHaveSize 1
        topLevel.params[0].name shouldBe "patterns"
        topLevel.samples.size shouldBe 3
    }

    "DslDocsRegistry.search should find seq by name" {
        val results = DslDocsRegistry.global.search("seq")

        results shouldHaveAtLeastSize 10
    }

    "DslDocsRegistry.search should find seq by category" {
        val results = DslDocsRegistry.global.search("structural")
        val names = results.map { it.name }

        names shouldContain "seq"
    }

    "DslDocsRegistry.search should find seq by tag" {
        val results = DslDocsRegistry.global.search("sequence")
        val names = results.map { it.name }

        names shouldContain "seq"
    }

    "DslDocsRegistry.getFunctionsByCategory should return seq" {
        val structural = DslDocsRegistry.global.getFunctionsByCategory("structural")
        val names = structural.map { it.name }

        names shouldContain "seq"
    }

    "DslDocsRegistry.getFunctionsByLibrary should return seq" {
        val strudelFuncs = DslDocsRegistry.global.getFunctionsByLibrary("strudel")
        val names = strudelFuncs.map { it.name }

        names shouldContain "seq"
    }

    // --- Property / dslObject docs ---

    "sine documentation should be registered as OBJECT variant" {
        val doc = DslDocsRegistry.global.get("sine")

        doc shouldNotBe null
        doc!!.name shouldBe "sine"
        doc.category shouldBe "continuous"
        doc.library shouldBe "strudel"
        doc.tags shouldContain "oscillator"

        val variant = doc.variants.first { it.type == DslType.OBJECT }
        variant.signature shouldBe "sine: StrudelPattern"
    }

    "sine OBJECT variant should have samples parsed from fenced KlangScript blocks" {
        val variant = DslDocsRegistry.global.get("sine")!!
            .variants.first { it.type == DslType.OBJECT }

        variant.samples shouldHaveAtLeastSize 2
        variant.samples.any { it.contains("sine") } shouldBe true
    }

    // ─── SignatureModel integration tests ────────────────────────────────────

    "seq top-level variant SignatureModel should have full param details" {
        val topLevel = DslDocsRegistry.global.get("seq")!!
            .variants.first { it.type == DslType.TOP_LEVEL }

        val model = topLevel.signatureModel
        model.name shouldBe "seq"
        model.receiver shouldBe null
        model.params shouldNotBe null
        model.params!! shouldHaveSize 1

        val param = model.params!![0]
        param.name shouldBe "patterns"
        param.isVararg shouldBe true
        param.type.simpleName shouldBe "PatternLike"
        param.type.isTypeAlias shouldBe true          // PatternLike is a type alias

        model.returnType shouldNotBe null
        model.returnType!!.simpleName shouldBe "StrudelPattern"
    }

    "seq extension method should have StrudelPattern receiver in SignatureModel" {
        val extension = DslDocsRegistry.global.get("seq")!!
            .variants.first {
                it.type == DslType.EXTENSION_METHOD &&
                        it.signatureModel.receiver?.simpleName == "StrudelPattern"
            }

        val model = extension.signatureModel
        model.name shouldBe "seq"
        model.receiver shouldNotBe null
        model.receiver!!.simpleName shouldBe "StrudelPattern"
        model.params shouldNotBe null
        model.returnType?.simpleName shouldBe "StrudelPattern"
    }

    "seq String extension should have String receiver in SignatureModel" {
        val extension = DslDocsRegistry.global.get("seq")!!
            .variants.first {
                it.type == DslType.EXTENSION_METHOD &&
                        it.signatureModel.receiver?.simpleName == "String"
            }

        extension.signatureModel.receiver!!.simpleName shouldBe "String"
        extension.signature shouldBe "String.seq(vararg patterns: PatternLike): StrudelPattern"
    }

    "accelerate property-based delegate should be classified as TOP_LEVEL (not OBJECT)" {
        val doc = DslDocsRegistry.global.get("accelerate")!!
        val types = doc.variants.map { it.type }
        types shouldContain DslType.TOP_LEVEL
    }

    "accelerate top-level variant should have emptyList params (callable but no param info from property)" {
        val topLevel = DslDocsRegistry.global.get("accelerate")!!
            .variants.first { it.type == DslType.TOP_LEVEL }

        val model = topLevel.signatureModel
        model.name shouldBe "accelerate"
        model.receiver shouldBe null
        model.params shouldNotBe null         // not null — it IS callable
        model.params!!.shouldBeEmpty()        // but no param info available from property delegate
        model.returnType?.simpleName shouldBe "StrudelPattern"

        // Signature renders with empty parens, not as a bare property name
        topLevel.signature shouldBe "accelerate(): StrudelPattern"
    }

    "accelerate extension variants should have receiver in SignatureModel" {
        val extensions = DslDocsRegistry.global.get("accelerate")!!
            .variants.filter { it.type == DslType.EXTENSION_METHOD }

        extensions shouldHaveAtLeastSize 1
        val receivers = extensions.map { it.signatureModel.receiver?.simpleName }
        receivers shouldContain "StrudelPattern"
        receivers shouldContain "String"
    }

    "sine OBJECT variant should have params=null in SignatureModel (renders without parens)" {
        val variant = DslDocsRegistry.global.get("sine")!!
            .variants.first { it.type == DslType.OBJECT }

        val model = variant.signatureModel
        model.name shouldBe "sine"
        model.receiver shouldBe null
        model.params shouldBe null                    // null = no parens in signature
        model.returnType?.simpleName shouldBe "StrudelPattern"

        // VariantDoc.params computed property returns emptyList when model.params is null
        variant.params.shouldBeEmpty()

        // Rendered signature has no parens
        variant.signature shouldBe "sine: StrudelPattern"
    }
})

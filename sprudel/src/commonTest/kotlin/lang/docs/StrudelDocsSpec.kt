package io.peekandpoke.klang.sprudel.lang.docs

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldHaveAtLeastSize
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.peekandpoke.klang.script.docs.KlangDocsRegistry
import io.peekandpoke.klang.script.types.KlangCallable
import io.peekandpoke.klang.script.types.KlangProperty
import io.peekandpoke.klang.sprudel.lang.initStrudelLang
import io.kotest.matchers.string.shouldContain as stringShouldContain

class StrudelDocsSpec : StringSpec({

    beforeTest {
        // Ensure Strudel is initialized and docs are registered
        initStrudelLang()
    }

    "seq documentation should be registered" {
        val seqDoc = KlangDocsRegistry.global.get("seq")

        seqDoc shouldNotBe null
        seqDoc!!.name shouldBe "seq"
        seqDoc.category shouldBe "structural"
        seqDoc.library shouldBe "strudel"
        seqDoc.tags shouldContain "sequence"
    }

    "seq should have 3 variants" {
        val seqDoc = KlangDocsRegistry.global.get("seq")!!

        seqDoc.variants shouldHaveSize 3

        // Check variant types
        val hasTopLevel = seqDoc.variants.any { it is KlangCallable && it.receiver == null }
        val hasExtension = seqDoc.variants.any { it is KlangCallable && it.receiver != null }
        hasTopLevel shouldBe true
        hasExtension shouldBe true
    }

    "seq top-level variant should have complete documentation" {
        val seqDoc = KlangDocsRegistry.global.get("seq")!!
        val topLevel = seqDoc.variants.filterIsInstance<KlangCallable>().first { it.receiver == null }

        topLevel.signature shouldBe "seq(vararg patterns: PatternLike): StrudelPattern"
        topLevel.description.stringShouldContain("one cycle")
        topLevel.params shouldHaveSize 1
        topLevel.params[0].name shouldBe "patterns"
        topLevel.samples.size shouldBe 3
    }

    "KlangDocsRegistry.search should find seq by name" {
        val results = KlangDocsRegistry.global.search("seq")

        results shouldHaveAtLeastSize 10
    }

    "KlangDocsRegistry.search should find seq by category" {
        val results = KlangDocsRegistry.global.search("structural")
        val names = results.map { it.name }

        names shouldContain "seq"
    }

    "KlangDocsRegistry.search should find seq by tag" {
        val results = KlangDocsRegistry.global.search("sequence")
        val names = results.map { it.name }

        names shouldContain "seq"
    }

    "KlangDocsRegistry.getByCategory should return seq" {
        val structural = KlangDocsRegistry.global.getByCategory("structural")
        val names = structural.map { it.name }

        names shouldContain "seq"
    }

    "KlangDocsRegistry.getByLibrary should return seq" {
        val strudelFuncs = KlangDocsRegistry.global.getByLibrary("strudel")
        val names = strudelFuncs.map { it.name }

        names shouldContain "seq"
    }

    // --- Property / dslObject docs ---

    "sine documentation should be registered as KlangProperty variant" {
        val doc = KlangDocsRegistry.global.get("sine")

        doc shouldNotBe null
        doc!!.name shouldBe "sine"
        doc.category shouldBe "continuous"
        doc.library shouldBe "strudel"
        doc.tags shouldContain "oscillator"

        val variant = doc.variants.filterIsInstance<KlangProperty>().first()
        variant.signature shouldBe "val sine: StrudelPattern"
    }

    "sine KlangProperty variant should have samples parsed from fenced KlangScript blocks" {
        val variant = KlangDocsRegistry.global.get("sine")!!
            .variants.filterIsInstance<KlangProperty>().first()

        variant.samples shouldHaveAtLeastSize 2
        variant.samples.any { it.contains("sine") } shouldBe true
    }

    // ─── KlangCallable integration tests ────────────────────────────────────

    "seq top-level variant KlangCallable should have full param details" {
        val topLevel = KlangDocsRegistry.global.get("seq")!!
            .variants.filterIsInstance<KlangCallable>().first { it.receiver == null }

        topLevel.name shouldBe "seq"
        topLevel.receiver shouldBe null
        topLevel.params shouldHaveSize 1

        val param = topLevel.params[0]
        param.name shouldBe "patterns"
        param.isVararg shouldBe true
        param.type.simpleName shouldBe "PatternLike"
        param.type.isTypeAlias shouldBe true          // PatternLike is a type alias

        topLevel.returnType shouldNotBe null
        topLevel.returnType!!.simpleName shouldBe "StrudelPattern"
    }

    "seq extension method should have StrudelPattern receiver" {
        val extension = KlangDocsRegistry.global.get("seq")!!
            .variants.filterIsInstance<KlangCallable>().first {
                it.receiver?.simpleName == "StrudelPattern"
            }

        extension.name shouldBe "seq"
        extension.receiver shouldNotBe null
        extension.receiver!!.simpleName shouldBe "StrudelPattern"
        extension.params shouldHaveSize 1
        extension.returnType?.simpleName shouldBe "StrudelPattern"
    }

    "seq String extension should have String receiver" {
        val extension = KlangDocsRegistry.global.get("seq")!!
            .variants.filterIsInstance<KlangCallable>().first {
                it.receiver?.simpleName == "String"
            }

        extension.receiver!!.simpleName shouldBe "String"
        extension.signature shouldBe "String.seq(vararg patterns: PatternLike): StrudelPattern"
    }

    "accelerate property-based delegate should be classified as KlangCallable (top-level, no receiver)" {
        val doc = KlangDocsRegistry.global.get("accelerate")!!
        val hasTopLevel = doc.variants.any { it is KlangCallable && it.receiver == null }
        hasTopLevel shouldBe true
    }

    "accelerate top-level variant should have emptyList params (callable but no param info from property)" {
        val topLevel = KlangDocsRegistry.global.get("accelerate")!!
            .variants.filterIsInstance<KlangCallable>().first { it.receiver == null }

        topLevel.name shouldBe "accelerate"
        topLevel.receiver shouldBe null
        topLevel.params shouldHaveSize 1        // but no param info available from property delegate
        topLevel.returnType?.simpleName shouldBe "PatternMapperFn"
    }

    "accelerate extension variants should have receiver" {
        val extensions = KlangDocsRegistry.global.get("accelerate")!!
            .variants.filterIsInstance<KlangCallable>().filter { it.receiver != null }

        extensions shouldHaveAtLeastSize 1
        val receivers = extensions.map { it.receiver?.simpleName }
        receivers shouldContain "StrudelPattern"
        receivers shouldContain "String"
    }

    "sine KlangProperty variant should have no params (renders without parens)" {
        val variant = KlangDocsRegistry.global.get("sine")!!
            .variants.filterIsInstance<KlangProperty>().first()

        variant.name shouldBe "sine"
        variant.owner shouldBe null
        variant.type.simpleName shouldBe "StrudelPattern"

        // Rendered signature has no parens
        variant.signature shouldBe "val sine: StrudelPattern"
    }
})

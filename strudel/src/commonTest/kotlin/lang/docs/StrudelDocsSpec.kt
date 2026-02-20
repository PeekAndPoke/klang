package io.peekandpoke.klang.strudel.lang.docs

import io.kotest.core.spec.style.StringSpec
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
})

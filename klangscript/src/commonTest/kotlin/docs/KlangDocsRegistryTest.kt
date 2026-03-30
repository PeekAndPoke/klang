package io.peekandpoke.klang.script.docs

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.peekandpoke.klang.script.types.KlangCallable
import io.peekandpoke.klang.script.types.KlangProperty
import io.peekandpoke.klang.script.types.KlangSymbol
import io.peekandpoke.klang.script.types.KlangType

class KlangDocsRegistryTest : StringSpec({

    // ── Merge behavior ──────────────────────────────────────────────────

    "register merges variants when names collide" {
        val registry = KlangDocsRegistry()

        registry.register(
            KlangSymbol(
                name = "sine", category = "oscillator", library = "stdlib",
                variants = listOf(
                    KlangCallable(name = "sine", receiver = KlangType("Osc"), params = emptyList())
                )
            )
        )
        registry.register(
            KlangSymbol(
                name = "sine", category = "pattern", library = "sprudel",
                variants = listOf(
                    KlangCallable(name = "sine", receiver = null, params = emptyList())
                )
            )
        )

        val symbol = registry.get("sine")!!
        symbol.variants shouldHaveSize 2
    }

    "register merges tags and aliases from both symbols" {
        val registry = KlangDocsRegistry()

        registry.register(
            KlangSymbol(name = "foo", category = "a", variants = emptyList(), tags = listOf("t1"), aliases = listOf("f"))
        )
        registry.register(
            KlangSymbol(name = "foo", category = "b", variants = emptyList(), tags = listOf("t2"), aliases = listOf("g"))
        )

        val symbol = registry.get("foo")!!
        symbol.tags shouldBe listOf("t1", "t2")
        symbol.aliases shouldBe listOf("f", "g")
    }

    // ── getCallable ─────────────────────────────────────────────────────

    "getCallable finds variant matching receiver type" {
        val registry = KlangDocsRegistry()
        registry.register(
            KlangSymbol(
                name = "sine", category = "test",
                variants = listOf(
                    KlangCallable(name = "sine", receiver = KlangType("Osc"), params = emptyList(), returnType = KlangType("ExciterDsl")),
                    KlangCallable(name = "sine", receiver = null, params = emptyList(), returnType = KlangType("Pattern")),
                )
            )
        )

        val oscVariant = registry.getCallable("sine", KlangType("Osc"))
        oscVariant shouldNotBe null
        oscVariant!!.returnType?.simpleName shouldBe "ExciterDsl"

        val topLevel = registry.getCallable("sine", null)
        topLevel shouldNotBe null
        topLevel!!.returnType?.simpleName shouldBe "Pattern"
    }

    "getCallable returns null for unknown name" {
        val registry = KlangDocsRegistry()
        registry.getCallable("nonexistent", null) shouldBe null
    }

    "getCallable returns null for unmatched receiver" {
        val registry = KlangDocsRegistry()
        registry.register(
            KlangSymbol(
                name = "sine", category = "test",
                variants = listOf(KlangCallable(name = "sine", receiver = KlangType("Osc"), params = emptyList()))
            )
        )
        registry.getCallable("sine", KlangType("Math")) shouldBe null
    }

    // ── getVariantsForReceiver ──────────────────────────────────────────

    "getVariantsForReceiver returns symbols with matching receiver" {
        val registry = KlangDocsRegistry()
        registry.register(
            KlangSymbol(
                name = "lowpass", category = "filter",
                variants = listOf(KlangCallable(name = "lowpass", receiver = KlangType("ExciterDsl"), params = emptyList()))
            )
        )
        registry.register(
            KlangSymbol(
                name = "adsr", category = "envelope",
                variants = listOf(KlangCallable(name = "adsr", receiver = KlangType("ExciterDsl"), params = emptyList()))
            )
        )
        registry.register(
            KlangSymbol(
                name = "gain", category = "effect",
                variants = listOf(KlangCallable(name = "gain", receiver = KlangType("Pattern"), params = emptyList()))
            )
        )

        val exciterMethods = registry.getVariantsForReceiver(KlangType("ExciterDsl"))
        exciterMethods shouldHaveSize 2
        exciterMethods.map { it.name }.toSet() shouldBe setOf("lowpass", "adsr")
    }

    // ── getSymbolWithReceiver ───────────────────────────────────────────

    "getSymbolWithReceiver filters variants to matching receiver" {
        val registry = KlangDocsRegistry()
        registry.register(
            KlangSymbol(
                name = "sine", category = "test",
                variants = listOf(
                    KlangCallable(name = "sine", receiver = KlangType("Osc"), params = emptyList()),
                    KlangCallable(name = "sine", receiver = null, params = emptyList()),
                )
            )
        )

        val filtered = registry.getSymbolWithReceiver("sine", KlangType("Osc"))!!
        filtered.variants shouldHaveSize 1
        (filtered.variants[0] as KlangCallable).receiver?.simpleName shouldBe "Osc"
    }

    "getSymbolWithReceiver returns all variants when receiver is null" {
        val registry = KlangDocsRegistry()
        registry.register(
            KlangSymbol(
                name = "sine", category = "test",
                variants = listOf(
                    KlangCallable(name = "sine", receiver = KlangType("Osc"), params = emptyList()),
                    KlangCallable(name = "sine", receiver = null, params = emptyList()),
                )
            )
        )

        val all = registry.getSymbolWithReceiver("sine", null)!!
        all.variants shouldHaveSize 2
    }

    "getSymbolWithReceiver falls back to all variants when no match" {
        val registry = KlangDocsRegistry()
        registry.register(
            KlangSymbol(
                name = "sine", category = "test",
                variants = listOf(KlangCallable(name = "sine", receiver = KlangType("Osc"), params = emptyList()))
            )
        )

        val fallback = registry.getSymbolWithReceiver("sine", KlangType("Unknown"))!!
        fallback.variants shouldHaveSize 1 // falls back to all
    }

    "getSymbolWithReceiver returns null for unknown name" {
        val registry = KlangDocsRegistry()
        registry.getSymbolWithReceiver("nonexistent", KlangType("Osc")) shouldBe null
    }

    "getSymbolWithReceiver sets library from filtered variant" {
        val registry = KlangDocsRegistry()
        registry.register(
            KlangSymbol(
                name = "adsr", category = "dynamics", library = "sprudel",
                variants = listOf(
                    KlangCallable(name = "adsr", receiver = KlangType("Pattern"), params = emptyList(), library = "sprudel"),
                )
            )
        )
        registry.register(
            KlangSymbol(
                name = "adsr", category = "uncategorized", library = "stdlib",
                variants = listOf(
                    KlangCallable(name = "adsr", receiver = KlangType("ExciterDsl"), params = emptyList(), library = "stdlib"),
                )
            )
        )

        val sprudelAdsr = registry.getSymbolWithReceiver("adsr", KlangType("Pattern"))!!
        sprudelAdsr.library shouldBe "sprudel"

        val stdlibAdsr = registry.getSymbolWithReceiver("adsr", KlangType("ExciterDsl"))!!
        stdlibAdsr.library shouldBe "stdlib"
    }

    // ── getVariantsForReceiver with KlangProperty ───────────────────────

    "getVariantsForReceiver includes KlangProperty with matching owner" {
        val registry = KlangDocsRegistry()
        registry.register(
            KlangSymbol(
                name = "sampleRate", category = "property",
                variants = listOf(
                    KlangProperty(name = "sampleRate", owner = KlangType("Osc"), type = KlangType("Number"))
                )
            )
        )
        registry.register(
            KlangSymbol(
                name = "lowpass", category = "filter",
                variants = listOf(
                    KlangCallable(name = "lowpass", receiver = KlangType("Osc"), params = emptyList())
                )
            )
        )

        val oscMembers = registry.getVariantsForReceiver(KlangType("Osc"))
        oscMembers shouldHaveSize 2
        oscMembers.map { it.name }.toSet() shouldBe setOf("sampleRate", "lowpass")
    }

    // ── snapshot ────────────────────────────────────────────────────────

    // ── Top-level vs extension-only distinction ───────────────────────

    "symbol with only extension variants has no top-level callable" {
        val registry = KlangDocsRegistry()
        registry.register(
            KlangSymbol(
                name = "lowpass", category = "filter",
                variants = listOf(
                    KlangCallable(name = "lowpass", receiver = KlangType("ExciterDsl"), params = emptyList()),
                )
            )
        )

        val symbol = registry.get("lowpass")!!
        // No variant has receiver == null
        val hasTopLevel = symbol.variants.any { variant ->
            when (variant) {
                is KlangCallable -> variant.receiver == null
                is KlangProperty -> variant.owner == null
            }
        }
        hasTopLevel shouldBe false
    }

    "merged symbol with top-level and extension variants has top-level callable" {
        val registry = KlangDocsRegistry()
        // sprudel abs (top-level)
        registry.register(
            KlangSymbol(
                name = "abs", category = "math",
                variants = listOf(
                    KlangCallable(name = "abs", receiver = null, params = emptyList()),
                )
            )
        )
        // stdlib Math.abs (extension)
        registry.register(
            KlangSymbol(
                name = "abs", category = "math",
                variants = listOf(
                    KlangCallable(name = "abs", receiver = KlangType("Math"), params = emptyList()),
                )
            )
        )

        val symbol = registry.get("abs")!!
        symbol.variants shouldHaveSize 2
        // Has a top-level variant
        val hasTopLevel = symbol.variants.any { variant ->
            when (variant) {
                is KlangCallable -> variant.receiver == null
                is KlangProperty -> variant.owner == null
            }
        }
        hasTopLevel shouldBe true
        // getCallable with null finds the top-level one
        registry.getCallable("abs", null) shouldNotBe null
        registry.getCallable("abs", null)!!.receiver shouldBe null
    }

    // ── snapshot ────────────────────────────────────────────────────────

    "snapshot creates an independent copy" {
        val registry = KlangDocsRegistry()
        registry.register(
            KlangSymbol(name = "foo", category = "test", variants = emptyList())
        )
        val copy = registry.snapshot()
        registry.register(
            KlangSymbol(name = "bar", category = "test", variants = emptyList())
        )

        copy.get("foo") shouldNotBe null
        copy.get("bar") shouldBe null // not affected by later mutations
    }
})

package io.peekandpoke.klang.script.intel

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldHaveAtLeastSize
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.script.docs.KlangDocsRegistry
import io.peekandpoke.klang.script.generated.generatedStdlibDocs
import io.peekandpoke.klang.script.types.KlangCallable
import io.peekandpoke.klang.script.types.KlangParam
import io.peekandpoke.klang.script.types.KlangProperty
import io.peekandpoke.klang.script.types.KlangSymbol
import io.peekandpoke.klang.script.types.KlangType

class CompletionProviderTest : StringSpec({

    // ── Shared fixture builders ────────────────────────────────────────────

    fun emptyRegistry(): KlangDocsRegistry = KlangDocsRegistry()

    fun stdlibRegistry(): KlangDocsRegistry = KlangDocsRegistry().apply {
        registerAll(generatedStdlibDocs)
    }

    /** A mock "sprudel" library with top-level note() and extension methods on Pattern. */
    fun sprudelSymbols(): List<KlangSymbol> = listOf(
        KlangSymbol(
            name = "note",
            category = "pattern",
            library = "sprudel",
            aliases = listOf("n"),
            variants = listOf(
                KlangCallable(
                    name = "note",
                    params = listOf(KlangParam(name = "pattern", type = KlangType("String"))),
                    returnType = KlangType("Pattern"),
                    library = "sprudel",
                )
            )
        ),
        KlangSymbol(
            name = "sound",
            category = "pattern",
            library = "sprudel",
            aliases = listOf("s"),
            variants = listOf(
                KlangCallable(
                    name = "sound",
                    params = listOf(KlangParam(name = "pattern", type = KlangType("String"))),
                    returnType = KlangType("Pattern"),
                    library = "sprudel",
                )
            )
        ),
        KlangSymbol(
            name = "gain",
            category = "effect",
            library = "sprudel",
            variants = listOf(
                KlangCallable(
                    name = "gain",
                    receiver = KlangType("Pattern"),
                    params = listOf(KlangParam(name = "amount", type = KlangType("Number"))),
                    returnType = KlangType("Pattern"),
                    library = "sprudel",
                )
            )
        ),
        KlangSymbol(
            name = "pan",
            category = "effect",
            library = "sprudel",
            variants = listOf(
                KlangCallable(
                    name = "pan",
                    receiver = KlangType("Pattern"),
                    params = listOf(KlangParam(name = "position", type = KlangType("String"))),
                    returnType = KlangType("Pattern"),
                    library = "sprudel",
                )
            )
        ),
        KlangSymbol(
            name = "adsr",
            category = "effect",
            library = "sprudel",
            variants = listOf(
                KlangCallable(
                    name = "adsr",
                    receiver = KlangType("Pattern"),
                    params = listOf(KlangParam(name = "envelope", type = KlangType("String"))),
                    returnType = KlangType("Pattern"),
                    library = "sprudel",
                )
            )
        ),
    )

    fun sprudelRegistry(): KlangDocsRegistry = KlangDocsRegistry().apply {
        sprudelSymbols().forEach { register(it) }
    }

    /** Merged registry: stdlib + sprudel. Has overlapping symbol "adsr" on different receivers. */
    fun multiLibRegistry(): KlangDocsRegistry = KlangDocsRegistry().apply {
        registerAll(generatedStdlibDocs)
        sprudelSymbols().forEach { register(it) }
    }

    // ════════════════════════════════════════════════════════════════════════
    // Category 1: Top-level completions
    // ════════════════════════════════════════════════════════════════════════

    "top-level: empty prefix returns all top-level symbols" {
        val provider = CompletionProvider(stdlibRegistry())
        val names = provider.topLevelCompletions("").map { it.name }
        names shouldContainExactlyInAnyOrder listOf("Osc", "Math", "Object", "PI", "E")
    }

    "top-level: filtered prefix" {
        val provider = CompletionProvider(stdlibRegistry())
        val names = provider.topLevelCompletions("O").map { it.name }
        names shouldContainExactlyInAnyOrder listOf("Osc", "Object")
    }

    "top-level: case-insensitive prefix" {
        val provider = CompletionProvider(stdlibRegistry())
        val upper = provider.topLevelCompletions("O").map { it.name }
        val lower = provider.topLevelCompletions("o").map { it.name }
        upper shouldBe lower
    }

    "top-level: no match returns empty" {
        val provider = CompletionProvider(stdlibRegistry())
        provider.topLevelCompletions("xyz").shouldBeEmpty()
    }

    "top-level: extension methods NOT shown" {
        val provider = CompletionProvider(stdlibRegistry())
        val names = provider.topLevelCompletions("").map { it.name }
        names shouldNotContain "sine"
        names shouldNotContain "lowpass"
        names shouldNotContain "sqrt"
        names shouldNotContain "adsr"
    }

    "top-level: functions with receiver=null are included" {
        val provider = CompletionProvider(sprudelRegistry())
        val names = provider.topLevelCompletions("").map { it.name }
        names.contains("note") shouldBe true
        names.contains("sound") shouldBe true
    }

    "top-level: extension-only symbols excluded" {
        val provider = CompletionProvider(sprudelRegistry())
        val names = provider.topLevelCompletions("").map { it.name }
        // gain and pan are extension methods on Pattern, not top-level
        names shouldNotContain "gain"
        names shouldNotContain "pan"
    }

    "top-level: suggestions have correct kind for properties" {
        val provider = CompletionProvider(stdlibRegistry())
        val osc = provider.topLevelCompletions("").first { it.name == "Osc" }
        osc.kind shouldBe CompletionSuggestion.Kind.PROPERTY
    }

    "top-level: suggestions have correct kind for functions" {
        val provider = CompletionProvider(sprudelRegistry())
        val note = provider.topLevelCompletions("").first { it.name == "note" }
        note.kind shouldBe CompletionSuggestion.Kind.FUNCTION
    }

    // ════════════════════════════════════════════════════════════════════════
    // Category 2: Member / chain completions
    // ════════════════════════════════════════════════════════════════════════

    "member: known receiver shows methods" {
        val provider = CompletionProvider(stdlibRegistry())
        val names = provider.memberCompletions(KlangType("Osc"), "").map { it.name }
        names shouldHaveAtLeastSize 5
        names.toSet().containsAll(listOf("sine", "saw", "square", "triangle")) shouldBe true
    }

    "member: excludes other types' methods" {
        val provider = CompletionProvider(stdlibRegistry())
        val names = provider.memberCompletions(KlangType("Osc"), "").map { it.name }
        names shouldNotContain "sqrt"
        names shouldNotContain "abs"
    }

    "member: excludes top-level symbols" {
        val provider = CompletionProvider(stdlibRegistry())
        val names = provider.memberCompletions(KlangType("Osc"), "").map { it.name }
        names shouldNotContain "Osc"
        names shouldNotContain "Math"
        names shouldNotContain "Object"
    }

    "member: prefix filters results" {
        val provider = CompletionProvider(stdlibRegistry())
        val names = provider.memberCompletions(KlangType("Osc"), "s").map { it.name }
        names.all { it.startsWith("s", ignoreCase = true) } shouldBe true
        names.toSet().containsAll(listOf("sine", "saw", "square")) shouldBe true
    }

    "member: unknown receiver returns empty" {
        val provider = CompletionProvider(stdlibRegistry())
        provider.memberCompletions(KlangType("NonExistent"), "").shouldBeEmpty()
    }

    "member: chain — IgnitorDsl methods after Osc.sine()" {
        val provider = CompletionProvider(stdlibRegistry())
        val names = provider.memberCompletions(KlangType("IgnitorDsl"), "").map { it.name }
        names shouldHaveAtLeastSize 3
        names.toSet().containsAll(listOf("lowpass", "adsr")) shouldBe true
    }

    "member: chain — Math methods stay on Math" {
        val provider = CompletionProvider(stdlibRegistry())
        val names = provider.memberCompletions(KlangType("Math"), "").map { it.name }
        names shouldHaveAtLeastSize 5
        names.toSet().containsAll(listOf("sqrt", "abs", "floor")) shouldBe true
    }

    "member: KlangProperty variants appear with PROPERTY kind" {
        val registry = KlangDocsRegistry()
        registry.register(
            KlangSymbol(
                name = "length",
                category = "property",
                library = "stdlib",
                variants = listOf(
                    KlangProperty(
                        name = "length",
                        owner = KlangType("String"),
                        type = KlangType("Number"),
                        description = "The length of the string",
                        library = "stdlib",
                    )
                )
            )
        )
        val provider = CompletionProvider(registry)
        val suggestions = provider.memberCompletions(KlangType("String"), "")
        val length = suggestions.first { it.name == "length" }
        length.kind shouldBe CompletionSuggestion.Kind.PROPERTY
    }

    "member: suggestion has correct kind FUNCTION" {
        val provider = CompletionProvider(stdlibRegistry())
        val sine = provider.memberCompletions(KlangType("Osc"), "sine").first { it.name == "sine" }
        sine.kind shouldBe CompletionSuggestion.Kind.FUNCTION
    }

    "member: prefix matching nothing returns empty" {
        val provider = CompletionProvider(stdlibRegistry())
        provider.memberCompletions(KlangType("Osc"), "xyz").shouldBeEmpty()
    }

    "member: Pattern methods from sprudel" {
        val provider = CompletionProvider(sprudelRegistry())
        val names = provider.memberCompletions(KlangType("Pattern"), "").map { it.name }
        names.toSet().containsAll(listOf("gain", "pan", "adsr")) shouldBe true
    }

    // ════════════════════════════════════════════════════════════════════════
    // Category 3: Multi-library merge
    // ════════════════════════════════════════════════════════════════════════

    "multi-lib: same method name on different receivers — IgnitorDsl sees stdlib adsr" {
        val provider = CompletionProvider(multiLibRegistry())
        val names = provider.memberCompletions(KlangType("IgnitorDsl"), "").map { it.name }
        names.contains("adsr") shouldBe true
    }

    "multi-lib: same method name on different receivers — Pattern sees sprudel adsr" {
        val provider = CompletionProvider(multiLibRegistry())
        val names = provider.memberCompletions(KlangType("Pattern"), "").map { it.name }
        names.contains("adsr") shouldBe true
    }

    "multi-lib: unknown receiver sees neither adsr" {
        val provider = CompletionProvider(multiLibRegistry())
        val names = provider.memberCompletions(KlangType("Unknown"), "").map { it.name }
        names.contains("adsr") shouldBe false
    }

    "multi-lib: top-level from both libs visible" {
        val provider = CompletionProvider(multiLibRegistry())
        val names = provider.topLevelCompletions("").map { it.name }
        // stdlib: Osc, Math, Object. sprudel: note, sound.
        names.containsAll(listOf("Osc", "Math", "Object", "note", "sound")) shouldBe true
    }

    "multi-lib: extension methods don't leak across receivers" {
        val provider = CompletionProvider(multiLibRegistry())
        // Osc should NOT see Pattern methods (gain, pan from sprudel)
        val oscNames = provider.memberCompletions(KlangType("Osc"), "").map { it.name }
        oscNames shouldNotContain "gain"
        oscNames shouldNotContain "pan"

        // Pattern should NOT see Osc methods (sine, saw from stdlib)
        val patternNames = provider.memberCompletions(KlangType("Pattern"), "").map { it.name }
        patternNames shouldNotContain "sine"
        patternNames shouldNotContain "saw"
    }

    "multi-lib: two libs register same top-level symbol — variants merged" {
        val registry = KlangDocsRegistry()

        // lib A registers note() returning PatternA
        registry.register(
            KlangSymbol(
                name = "note", category = "pattern", library = "libA",
                variants = listOf(
                    KlangCallable(
                        name = "note",
                        params = listOf(KlangParam(name = "p", type = KlangType("String"))),
                        returnType = KlangType("PatternA"),
                        library = "libA",
                    )
                )
            )
        )

        // lib B also registers note() returning PatternB
        registry.register(
            KlangSymbol(
                name = "note", category = "pattern", library = "libB",
                variants = listOf(
                    KlangCallable(
                        name = "note",
                        params = listOf(KlangParam(name = "p", type = KlangType("String"))),
                        returnType = KlangType("PatternB"),
                        library = "libB",
                    )
                )
            )
        )

        // Should appear once in top-level (merged, not duplicated)
        val provider = CompletionProvider(registry)
        val suggestions = provider.topLevelCompletions("")
        suggestions.count { it.name == "note" } shouldBe 1

        // The underlying symbol should have 2 variants
        val symbol = registry.get("note")!!
        symbol.variants.size shouldBe 2
    }

    "multi-lib: merged adsr has variants from both libraries" {
        val registry = multiLibRegistry()
        val symbol = registry.get("adsr")!!
        // stdlib variant (receiver=IgnitorDsl) + sprudel variant (receiver=Pattern)
        symbol.variants.filterIsInstance<KlangCallable>().let { callables ->
            callables.any { it.receiver?.simpleName == "IgnitorDsl" } shouldBe true
            callables.any { it.receiver?.simpleName == "Pattern" } shouldBe true
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // Category 4: Import completions
    // ════════════════════════════════════════════════════════════════════════

    "import: returns available library names" {
        val provider = CompletionProvider(emptyRegistry())
        val suggestions = provider.importCompletions(setOf("stdlib", "sprudel"))
        suggestions.map { it.name } shouldContainExactlyInAnyOrder listOf("stdlib", "sprudel")
    }

    "import: empty set returns empty" {
        val provider = CompletionProvider(emptyRegistry())
        provider.importCompletions(emptySet()).shouldBeEmpty()
    }

    "import: suggestions have KEYWORD kind" {
        val provider = CompletionProvider(emptyRegistry())
        val suggestions = provider.importCompletions(setOf("stdlib"))
        suggestions.first().kind shouldBe CompletionSuggestion.Kind.KEYWORD
    }

    "import: detail says library" {
        val provider = CompletionProvider(emptyRegistry())
        val suggestions = provider.importCompletions(setOf("stdlib"))
        suggestions.first().detail shouldBe "library"
    }

    // ════════════════════════════════════════════════════════════════════════
    // Category 5: Negative / suppression
    // ════════════════════════════════════════════════════════════════════════

    "negative: empty registry — top-level returns empty" {
        val provider = CompletionProvider(emptyRegistry())
        provider.topLevelCompletions("").shouldBeEmpty()
    }

    "negative: empty registry — member returns empty" {
        val provider = CompletionProvider(emptyRegistry())
        provider.memberCompletions(KlangType("Osc"), "").shouldBeEmpty()
    }

    "negative: no methods registered for queried type" {
        // Registry has Osc methods but we query a type that has nothing registered
        val provider = CompletionProvider(stdlibRegistry())
        provider.memberCompletions(KlangType("CompletelyUnknownType"), "").shouldBeEmpty()
    }

    "negative: extension-only symbol NOT in top-level" {
        // Register only an extension method (no top-level variant)
        val registry = KlangDocsRegistry()
        registry.register(
            KlangSymbol(
                name = "lowpass",
                category = "effect",
                library = "stdlib",
                variants = listOf(
                    KlangCallable(
                        name = "lowpass",
                        receiver = KlangType("IgnitorDsl"),
                        params = listOf(KlangParam(name = "freq", type = KlangType("Number"))),
                        returnType = KlangType("IgnitorDsl"),
                        library = "stdlib",
                    )
                )
            )
        )
        val provider = CompletionProvider(registry)
        provider.topLevelCompletions("").shouldBeEmpty()
    }

    "negative: prefix uses startsWith, not contains" {
        val provider = CompletionProvider(stdlibRegistry())
        // "ine" should NOT match "sine" (startsWith, not contains)
        provider.memberCompletions(KlangType("Osc"), "ine").shouldBeEmpty()
    }

    "negative: member on type with zero registered variants" {
        val registry = KlangDocsRegistry()
        // Register a top-level property but no methods for its type
        registry.register(
            KlangSymbol(
                name = "Foo",
                category = "object",
                library = "test",
                variants = listOf(
                    KlangProperty(name = "Foo", type = KlangType("Foo"), library = "test")
                )
            )
        )
        val provider = CompletionProvider(registry)
        provider.memberCompletions(KlangType("Foo"), "").shouldBeEmpty()
    }

    // ════════════════════════════════════════════════════════════════════════
    // Category 6: Alias edge cases
    // ════════════════════════════════════════════════════════════════════════

    "alias: appears alongside original in top-level" {
        val provider = CompletionProvider(sprudelRegistry())
        val names = provider.topLevelCompletions("").map { it.name }
        names.contains("note") shouldBe true
        names.contains("n") shouldBe true
        names.contains("sound") shouldBe true
        names.contains("s") shouldBe true
    }

    "alias: has correct metadata" {
        val provider = CompletionProvider(sprudelRegistry())
        val alias = provider.topLevelCompletions("").first { it.name == "n" }
        alias.isAlias shouldBe true
        alias.aliasFor shouldBe "note"
    }

    "alias: filtered by prefix" {
        val provider = CompletionProvider(sprudelRegistry())
        // prefix "n" should match both "note" and alias "n"
        val names = provider.topLevelCompletions("n").map { it.name }
        names.contains("note") shouldBe true
        names.contains("n") shouldBe true
        // "sound" and "s" should NOT match
        names shouldNotContain "sound"
        names shouldNotContain "s"
    }

    "alias: extension-only symbol alias NOT in top-level" {
        val registry = KlangDocsRegistry()
        registry.register(
            KlangSymbol(
                name = "lowpass",
                category = "effect",
                library = "stdlib",
                aliases = listOf("lp"),
                variants = listOf(
                    KlangCallable(
                        name = "lowpass",
                        receiver = KlangType("IgnitorDsl"),
                        params = listOf(KlangParam(name = "freq", type = KlangType("Number"))),
                        returnType = KlangType("IgnitorDsl"),
                        library = "stdlib",
                    )
                )
            )
        )
        val provider = CompletionProvider(registry)
        val names = provider.topLevelCompletions("").map { it.name }
        // Neither the original nor the alias should appear at top level
        names shouldNotContain "lowpass"
        names shouldNotContain "lp"
    }

    "alias: in multi-lib — alias from sprudel visible alongside stdlib top-level" {
        val provider = CompletionProvider(multiLibRegistry())
        val names = provider.topLevelCompletions("").map { it.name }
        // sprudel aliases "n" and "s" should be present
        names.contains("n") shouldBe true
        names.contains("s") shouldBe true
        // stdlib top-level should also be present
        names.contains("Osc") shouldBe true
    }
})

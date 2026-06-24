/*
 * Copyright (C) 2025-2026 The Klang Audio Motör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.script.docs

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
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
                name = "sine", category = "oscillator", origin = KlangSymbol.Origin.Library("stdlib"),
                variants = listOf(
                    KlangCallable(name = "sine", receiver = KlangType("Osc"), params = emptyList())
                )
            )
        )
        registry.register(
            KlangSymbol(
                name = "sine", category = "pattern", origin = KlangSymbol.Origin.Library("sprudel"),
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
                    KlangCallable(name = "sine", receiver = KlangType("Osc"), params = emptyList(), returnType = KlangType("IgnitorDsl")),
                    KlangCallable(name = "sine", receiver = null, params = emptyList(), returnType = KlangType("Pattern")),
                )
            )
        )

        val oscVariant = registry.getCallable("sine", KlangType("Osc"))
        oscVariant shouldNotBe null
        oscVariant!!.returnType?.simpleName shouldBe "IgnitorDsl"

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
                variants = listOf(KlangCallable(name = "lowpass", receiver = KlangType("IgnitorDsl"), params = emptyList()))
            )
        )
        registry.register(
            KlangSymbol(
                name = "adsr", category = "envelope",
                variants = listOf(KlangCallable(name = "adsr", receiver = KlangType("IgnitorDsl"), params = emptyList()))
            )
        )
        registry.register(
            KlangSymbol(
                name = "gain", category = "effect",
                variants = listOf(KlangCallable(name = "gain", receiver = KlangType("Pattern"), params = emptyList()))
            )
        )

        val ignitorMethods = registry.getVariantsForReceiver(KlangType("IgnitorDsl"))
        ignitorMethods shouldHaveSize 2
        ignitorMethods.map { it.name }.toSet() shouldBe setOf("lowpass", "adsr")
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

    "getSymbolWithReceiver returns null when receiver is specified but no variant matches" {
        val registry = KlangDocsRegistry()
        registry.register(
            KlangSymbol(
                name = "sine", category = "test",
                variants = listOf(KlangCallable(name = "sine", receiver = KlangType("Osc"), params = emptyList()))
            )
        )

        // Strict: receiver given + no variant matches → null (so the hover popup
        // doesn't leak unrelated DSL variants when the user is on a known receiver).
        registry.getSymbolWithReceiver("sine", KlangType("Unknown")) shouldBe null
    }

    "getSymbolWithReceiver returns null for unknown name" {
        val registry = KlangDocsRegistry()
        registry.getSymbolWithReceiver("nonexistent", KlangType("Osc")) shouldBe null
    }

    "getSymbolWithReceiver sets library from filtered variant" {
        val registry = KlangDocsRegistry()
        registry.register(
            KlangSymbol(
                name = "adsr", category = "dynamics", origin = KlangSymbol.Origin.Library("sprudel"),
                variants = listOf(
                    KlangCallable(name = "adsr", receiver = KlangType("Pattern"), params = emptyList(), library = "sprudel"),
                )
            )
        )
        registry.register(
            KlangSymbol(
                name = "adsr", category = "uncategorized", origin = KlangSymbol.Origin.Library("stdlib"),
                variants = listOf(
                    KlangCallable(name = "adsr", receiver = KlangType("IgnitorDsl"), params = emptyList(), library = "stdlib"),
                )
            )
        )

        val sprudelAdsr = registry.getSymbolWithReceiver("adsr", KlangType("Pattern"))!!
        sprudelAdsr.getLibrary()?.name shouldBe "sprudel"

        val stdlibAdsr = registry.getSymbolWithReceiver("adsr", KlangType("IgnitorDsl"))!!
        stdlibAdsr.getLibrary()?.name shouldBe "stdlib"
    }

    // ── KlangSymbol.Origin / getLibrary helper ──────────────────────────

    "getLibrary returns the Library origin when origin is Library" {
        val sym = KlangSymbol(
            name = "x", category = "test",
            origin = KlangSymbol.Origin.Library("stdlib"),
            variants = emptyList(),
        )
        sym.getLibrary() shouldBe KlangSymbol.Origin.Library("stdlib")
        sym.getLibrary()?.name shouldBe "stdlib"
    }

    "getLibrary returns null when origin is Local" {
        val sym = KlangSymbol(
            name = "signal", category = "local",
            origin = KlangSymbol.Origin.Local(KlangSymbol.LocalKind.LET),
            variants = emptyList(),
        )
        sym.getLibrary() shouldBe null
    }

    "getLibrary returns null when origin is unknown (null)" {
        // Default constructor leaves origin = null = "we don't know".
        val sym = KlangSymbol(name = "x", category = "test", variants = emptyList())
        sym.origin shouldBe null
        sym.getLibrary() shouldBe null
    }

    "registry.libraries lists only Library-origin names" {
        val registry = KlangDocsRegistry()
        registry.register(
            KlangSymbol(
                name = "a", category = "x", variants = emptyList(),
                origin = KlangSymbol.Origin.Library("stdlib")
            )
        )
        registry.register(
            KlangSymbol(
                name = "b", category = "x", variants = emptyList(),
                origin = KlangSymbol.Origin.Library("sprudel")
            )
        )
        // Locals and null-origin symbols should not appear in the libraries list.
        registry.register(
            KlangSymbol(
                name = "signal", category = "local", variants = emptyList(),
                origin = KlangSymbol.Origin.Local(KlangSymbol.LocalKind.LET)
            )
        )
        registry.register(
            KlangSymbol(name = "unknown", category = "x", variants = emptyList()) // origin = null
        )

        registry.libraries shouldBe listOf("sprudel", "stdlib")
    }

    "registry.getByLibrary skips locals and unknown-origin symbols" {
        val registry = KlangDocsRegistry()
        registry.register(
            KlangSymbol(
                name = "fromStdlib", category = "x", variants = emptyList(),
                origin = KlangSymbol.Origin.Library("stdlib")
            )
        )
        registry.register(
            KlangSymbol(
                name = "fromSprudel", category = "x", variants = emptyList(),
                origin = KlangSymbol.Origin.Library("sprudel")
            )
        )
        registry.register(
            KlangSymbol(
                name = "signal", category = "local", variants = emptyList(),
                origin = KlangSymbol.Origin.Local(KlangSymbol.LocalKind.LET)
            )
        )

        registry.getByLibrary("stdlib").map { it.name } shouldBe listOf("fromStdlib")
        registry.getByLibrary("sprudel").map { it.name } shouldBe listOf("fromSprudel")
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
                    KlangCallable(name = "lowpass", receiver = KlangType("IgnitorDsl"), params = emptyList()),
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

    // ── FQCN-aware lookup ───────────────────────────────────────────────
    //
    // FQCN is the canonical cross-module identity key. When both the registered
    // owner and the lookup-query type carry an `fqcn`, only the fqcn is consulted.
    // simpleName is used as a fallback for primitives / RuntimeValue subtypes
    // that don't map to a single Kotlin declaration.

    "typeMatches: equal FQCN wins even when simpleName differs (cross-module case)" {
        val registry = KlangDocsRegistry()
        registry.register(
            KlangSymbol(
                name = "analog", category = "test",
                variants = listOf(
                    KlangProperty(
                        name = "analog",
                        // In-module emission: script-name + fqcn
                        owner = KlangType("OscSlot", fqcn = "io.peekandpoke.klang.script.stdlib.KlangScriptOscSlot"),
                        type = KlangType("IgnitorDsl"),
                    )
                )
            )
        )

        // Cross-module emission would carry the Kotlin simpleName but matching fqcn
        val crossModuleQuery = KlangType(
            simpleName = "KlangScriptOscSlot",
            fqcn = "io.peekandpoke.klang.script.stdlib.KlangScriptOscSlot",
        )
        registry.getVariantsForReceiver(crossModuleQuery).map { it.name } shouldBe listOf("analog")
    }

    "typeMatches: FQCN mismatch overrides simpleName-equal" {
        // Two different classes happen to share the simpleName "Foo" — must not collide.
        val registry = KlangDocsRegistry()
        registry.register(
            KlangSymbol(
                name = "bar", category = "test",
                variants = listOf(
                    KlangCallable(
                        name = "bar",
                        receiver = KlangType("Foo", fqcn = "com.example.a.Foo"),
                        params = emptyList(),
                    )
                )
            )
        )

        val differentFoo = KlangType("Foo", fqcn = "com.example.b.Foo")
        registry.getVariantsForReceiver(differentFoo).shouldBeEmpty()
    }

    "typeMatches: simpleName fallback when neither side has FQCN" {
        val registry = KlangDocsRegistry()
        registry.register(
            KlangSymbol(
                name = "abs", category = "math",
                variants = listOf(KlangCallable(name = "abs", receiver = KlangType("Number"), params = emptyList()))
            )
        )
        // Primitives / RuntimeValue subtypes don't carry FQCN in either direction.
        registry.getVariantsForReceiver(KlangType("Number")).map { it.name } shouldBe listOf("abs")
    }

    "typeMatches: simpleName fallback when only one side has FQCN" {
        // Mixed: owner has FQCN, query doesn't (or vice versa) — match by simpleName.
        val registry = KlangDocsRegistry()
        registry.register(
            KlangSymbol(
                name = "sine", category = "test",
                variants = listOf(
                    KlangCallable(
                        name = "sine",
                        receiver = KlangType("Osc", fqcn = "io.peekandpoke.klang.script.stdlib.KlangScriptOsc"),
                        params = emptyList(),
                    )
                )
            )
        )

        // Inferrer emits a KlangType without fqcn — should still match by simpleName.
        registry.getVariantsForReceiver(KlangType("Osc")).map { it.name } shouldBe listOf("sine")
    }

    "typeMatches: null query matches null-owner (top-level functions)" {
        val registry = KlangDocsRegistry()
        registry.register(
            KlangSymbol(
                name = "note", category = "pattern",
                variants = listOf(KlangCallable(name = "note", receiver = null, params = emptyList()))
            )
        )
        registry.getCallable("note", null)?.name shouldBe "note"
        // A query for a real type must not match top-level entries.
        registry.getCallable("note", KlangType("Osc")) shouldBe null
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

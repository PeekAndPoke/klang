/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.script.ksp

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

/**
 * How the generated registration is laid out over files (2026-10-06): the area of a source file decides
 * the registration file of its blocks, and every distinct docs `KlangType` is emitted once.
 */
class GeneratedLayoutTest : StringSpec({

    // ===== sourceArea / areaIdentifier =====

    "a lang_<group>_<subgroup> file belongs to its group" {
        sourceArea("lang_structural_chunk.kt") shouldBe "lang_structural"
        sourceArea("lang_structural_seq.kt") shouldBe "lang_structural"
        sourceArea("lang_synthesis_snd_super.kt") shouldBe "lang_synthesis"
    }

    "a file with one or no underscore is an area of its own" {
        sourceArea("lang_katalyst.kt") shouldBe "lang_katalyst"
        sourceArea("KlangScriptIgnitor.kt") shouldBe "KlangScriptIgnitor"
    }

    "an unknown file is the misc area" {
        sourceArea(null) shouldBe "misc"
        sourceArea(".kt") shouldBe "misc"
    }

    "an area becomes an identifier part" {
        areaIdentifier("lang_structural") shouldBe "LangStructural"
        areaIdentifier("KlangScriptIgnitor") shouldBe "KlangScriptIgnitor"
        areaIdentifier("misc") shouldBe "Misc"
    }

    "an area without letters or digits is Misc, never the entry point's empty name" {
        areaIdentifier("_") shouldBe "Misc"
        areaIdentifier(sourceArea("__x.kt")) shouldBe "Misc"
    }

    "areas that normalize alike share one identifier, so one file" {
        areaIdentifier(sourceArea("lang_foo_x.kt")) shouldBe areaIdentifier(sourceArea("LangFoo.kt"))
        areaIdentifier(sourceArea("lang-foo.kt")) shouldBe "LangFoo"
    }

    // ===== distributeIntoChunks / entryPointCalls =====

    "interleaved areas keep the collected order, numbered per area" {
        val chunks = distributeIntoChunks(
            blocks = listOf("A" to "a1", "B" to "b1", "A" to "a2"),
            budget = 1_000,
            functionPrefix = "registerLib",
        )

        chunks.map { it.functionName } shouldBe listOf("registerLibAChunk0", "registerLibBChunk0", "registerLibAChunk1")
        entryPointCalls(chunks) shouldBe listOf("registerLibAChunk0()", "registerLibBChunk0()", "registerLibAChunk1()")
    }

    "a chunk never mixes areas, and consecutive blocks of one area share a chunk" {
        val chunks = distributeIntoChunks(
            blocks = listOf("A" to "a1", "A" to "a2", "B" to "b1", "B" to "b2"),
            budget = 1_000,
            functionPrefix = "r",
        )

        chunks.map { it.area to it.blocks } shouldBe listOf("A" to listOf("a1", "a2"), "B" to listOf("b1", "b2"))
    }

    "a block that does not fit starts the next chunk of the same area" {
        val chunks = distributeIntoChunks(
            blocks = listOf("A" to "xxxx", "A" to "yyyy", "A" to "z"),
            budget = 6,
            functionPrefix = "r",
        )

        chunks.map { it.functionName to it.blocks } shouldBe listOf(
            "rAChunk0" to listOf("xxxx"),
            "rAChunk1" to listOf("yyyy", "z"),
        )
    }

    // ===== KlangTypeTable =====

    "the type table names each distinct expression once, in order of first use" {
        val table = KlangTypeTable()

        table.ref("KlangType(simpleName = \"A\")") shouldBe "kt0"
        table.ref("KlangType(simpleName = \"B\")") shouldBe "kt1"
        table.ref("KlangType(simpleName = \"A\")") shouldBe "kt0"
    }

    "the type table renders one private val per entry, nested entries first" {
        val table = KlangTypeTable()
        val inner = table.ref("KlangType(simpleName = \"KlangPattern\")")
        table.ref("KlangType(simpleName = \"SprudelPattern\", supertypes = listOf($inner))")

        table.render() shouldBe
            "private val kt0 = KlangType(simpleName = \"KlangPattern\")\n" +
            "private val kt1 = KlangType(simpleName = \"SprudelPattern\", supertypes = listOf(kt0))\n"
    }
})

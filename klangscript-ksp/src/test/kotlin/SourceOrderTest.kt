/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.script.ksp

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlin.random.Random

/**
 * The processor sorts what KSP hands it into source order (2026-10-07), so the generated output does not depend on
 * the order the file system lists the source directory in.
 */
class SourceOrderTest : StringSpec({

    val inSourceOrder = listOf(
        SourcePosition("/m/src/lang/lang_effects_delay.kt", 12, "a.delay"),
        SourcePosition("/m/src/lang/lang_effects_delay.kt", 40, "a.delayTime"),
        SourcePosition("/m/src/lang/lang_effects_reverb.kt", 3, "a.reverb"),
        SourcePosition("/m/src/lang/lang_effects_reverb.kt", 3, "a.room"),
        SourcePosition("/m/src/lang/lang_structural_chunk.kt", 1, "a.chunk"),
        SourcePosition("/m/src/lang/lang_structural_chunk.kt", 9, "a.chunkBack"),
        SourcePosition("/m/src/lang/lang_structural_chunk.kt", 120, "a.fastChunk"),
        SourcePosition("/m/src/stdlib/KlangScriptMath.kt", 5, "a.Math"),
    )

    "file path first, then line, then name" {
        inSourceOrder.reversed().sortedBySource { it } shouldBe inSourceOrder
    }

    "a line sorts by its number, not by its text" {
        val nine = SourcePosition("/m/a.kt", 9, "x")
        val hundred = SourcePosition("/m/a.kt", 100, "x")

        listOf(hundred, nine).sortedBySource { it } shouldBe listOf(nine, hundred)
    }

    "every arrival order gives the same list" {
        val random = Random(20261007)

        repeat(200) {
            inSourceOrder.shuffled(random).sortedBySource { it } shouldBe inSourceOrder
        }
    }

    "a Windows path sorts like the same path with forward slashes" {
        val windows = listOf(
            SourcePosition("C:\\m\\a\\z.kt", 1, "inDirectory"),
            SourcePosition("C:\\m\\aB.kt", 1, "besideIt"),
        )
        val unix = listOf(
            SourcePosition("C:/m/a/z.kt", 1, "inDirectory"),
            SourcePosition("C:/m/aB.kt", 1, "besideIt"),
        )

        // '/' sorts before 'B', '\' after it: unnormalized, the two spellings would disagree.
        unix.reversed().sortedBySource { it }.map { it.name } shouldBe listOf("inDirectory", "besideIt")
        windows.reversed().sortedBySource { it }.map { it.name } shouldBe listOf("inDirectory", "besideIt")
    }

    "sorted blocks give each area as few chunks as its size needs" {
        // What the processor saw before the sort: two areas interleaved the way a directory listing mixes files.
        val interleaved = listOf(
            SourcePosition("/m/lang_b_one.kt", 1, "b1"),
            SourcePosition("/m/lang_a_one.kt", 1, "a1"),
            SourcePosition("/m/lang_b_two.kt", 1, "b2"),
            SourcePosition("/m/lang_a_two.kt", 1, "a2"),
        )

        fun chunksOf(order: List<SourcePosition>) = distributeIntoChunks(
            blocks = order.map { areaIdentifier(sourceArea(it.filePath.substringAfterLast('/'))) to "block ${it.name}\n" },
            budget = 20_000,
            functionPrefix = "registerX",
        )

        chunksOf(interleaved).size shouldBe 4
        chunksOf(interleaved.sortedBySource { it }).map { it.functionName } shouldBe listOf("registerXLangAChunk0", "registerXLangBChunk0")
    }
})

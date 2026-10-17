/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.script.ksp

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe

/**
 * The source files of the aggregating outputs ([collectSourceFiles], typed-doors rollout S6): a top-level extension
 * function whose receiver has no file in the module (a builder in `audio_bridge`, a `String`) still records its OWN
 * file, or an incremental KSP run drops its registrations.
 */
class SourceFilesTest : StringSpec({

    "an extension function on a receiver without a file records its own file" {
        val sources = collectSourceFiles(
            objects = emptyList(),
            typeExtensions = listOf(SourceRefs(classFile = null, methodFiles = listOf("stdlib/BandsBuilders.kt"))),
            functionFiles = emptyList(),
            propertyFiles = emptyList(),
        )

        sources shouldContain "stdlib/BandsBuilders.kt"
    }

    "every kind of entry counts, each file once, in first-seen order" {
        collectSourceFiles(
            objects = listOf(SourceRefs(classFile = "KlangScriptIgnitor.kt", methodFiles = emptyList())),
            typeExtensions = listOf(
                SourceRefs(classFile = "KlangScriptIgnitorExtensions.kt", methodFiles = listOf("KlangScriptIgnitorExtensions.kt")),
                SourceRefs(classFile = null, methodFiles = listOf("lang_structural_chunk.kt", null)),
            ),
            functionFiles = listOf("KlangScriptMath.kt", null),
            propertyFiles = listOf("KlangScriptConstants.kt"),
        ) shouldBe listOf(
            "KlangScriptIgnitor.kt",
            "KlangScriptIgnitorExtensions.kt",
            "lang_structural_chunk.kt",
            "KlangScriptMath.kt",
            "KlangScriptConstants.kt",
        )
    }
})

/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import java.io.File

/**
 * The Spine's stores are its own (KLANG-00389, Persistence): only the Spine's areas read and write them, the UI asks the
 * area. Kotlin has no package-private, so this spec keeps it, a check for the future: no source outside `spine/`
 * imports `io.peekandpoke.klang.spine.storage`. Specs may, to set up and read back what is kept.
 */
class SpineStorageSpec : StringSpec({

    "no source outside the Spine imports the Spine's storage" {
        // Gradle runs the root project's JVM tests in the root folder
        val sources = File("src/jsMain/kotlin").walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .toList()

        val outside = sources.filterNot { it.invariantSeparatorsPath.startsWith("src/jsMain/kotlin/spine/") }

        val found = outside.flatMap { file ->
            file.readLines().withIndex()
                .filter { (_, line) -> line.trim().startsWith("import io.peekandpoke.klang.spine.storage") }
                .map { (index, line) -> "${file.path}:${index + 1}: ${line.trim()}" }
        }

        // The walk found the app, and the Spine's storage is there to be imported
        outside.size shouldBeGreaterThan 100
        sources.any { it.invariantSeparatorsPath.startsWith("src/jsMain/kotlin/spine/storage/") } shouldBe true
        found.shouldBeEmpty()
    }
})

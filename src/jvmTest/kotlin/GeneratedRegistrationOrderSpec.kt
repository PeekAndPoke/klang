/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.script.generated.generatedStdlibDocs
import io.peekandpoke.klang.script.types.KlangCallable
import java.io.File

/**
 * The KSP processor emits the registration in source order (2026-10-07, `docs/tasks/reduce-js-bundle-size.md`,
 * "Step 3: source order"), so the output does not depend on the order the file system lists the source directory.
 * This reads the REAL generated entry point of both modules the processor runs on and checks what that order
 * promises: the chunks of one area are called in ONE contiguous run, numbered from 0 upwards, and the runs come in
 * the order of the source files. Before the sort, sprudel's entry point alternated between areas about 150 times.
 * The last row reads the generated DOCS: a symbol documented on several receivers lists its variants in source
 * order too, and its category is the first variant's, so the order is visible to users.
 */
class GeneratedRegistrationOrderSpec : StringSpec({

    val generatedDir = "build/generated/ksp/metadata/commonMain/kotlin/io/peekandpoke/klang/script/generated"
    val registrationAnnotation = Regex("""@KlangScript\.(Function|Object|TypeExtensions|Constant)\b""")
    val chunkCall = Regex("""^\s*register(\w+?)Chunk(\d+)\(\)\s*$""")

    /**
     * The area identifier of a source file, as `sourceArea` and `areaIdentifier` in `klangscript-ksp` compute it
     * (internal there): the name without `.kt` cut after its second `_`-separated part, as an identifier.
     */
    fun areaIdentifierOf(file: File): String =
        file.name.removeSuffix(".kt").split('_').take(2).joinToString("_")
            .split('_', '-', '.', ' ')
            .filter { it.isNotEmpty() }
            .joinToString("") { part -> part.filter { it.isLetterOrDigit() }.replaceFirstChar { it.uppercase() } }

    fun checkModule(module: String, library: String, sourceDir: String) {
        val entryPoint = File("$module/$generatedDir/Generated${library}Registration.kt")

        withClue("the generated entry point exists: ${entryPoint.path}") { entryPoint.isFile shouldBe true }

        // (area, chunk number) per call, in call order
        val calls = entryPoint.readLines().mapNotNull { line ->
            chunkCall.matchEntire(line)?.let { it.groupValues[1].removePrefix(library) to it.groupValues[2].toInt() }
        }

        withClue("the entry point calls chunks at all") { calls.size shouldBeGreaterThan 10 }

        val runs = calls.fold(mutableListOf<MutableList<Int>>() to mutableListOf<String>()) { (chunks, areas), (area, n) ->
            if (areas.lastOrNull() != area) {
                areas.add(area)
                chunks.add(mutableListOf())
            }
            chunks.last().add(n)
            chunks to areas
        }
        val runAreas = runs.second

        withClue("each area is ONE contiguous run of chunk calls: $runAreas") {
            runAreas.distinct() shouldBe runAreas
        }

        runs.first.forEachIndexed { i, numbers ->
            withClue("the chunks of ${runAreas[i]} are called 0, 1, 2, ...") {
                numbers shouldBe numbers.indices.toList()
            }
        }

        val sourceOrder = File(sourceDir).walkTopDown()
            .filter { it.isFile && it.extension == "kt" && registrationAnnotation.containsMatchIn(it.readText()) }
            .sortedBy { it.invariantSeparatorsPath }
            .map { areaIdentifierOf(it) }
            .distinct()
            .filter { it in runAreas }
            .toList()

        withClue("the areas run in the order of their source files") {
            runAreas shouldBe sourceOrder
        }
    }

    "sprudel: one run per area, in source order" {
        checkModule("sprudel", "Sprudel", "sprudel/src/commonMain/kotlin")
    }

    "klangscript-libs: one run per area, in source order" {
        checkModule("klangscript-libs", "Stdlib", "klangscript-libs/src/commonMain/kotlin")
    }

    "a docs symbol on several receivers lists its variants in source order, and takes its category from the first" {
        // `indexOf` is declared on Array and on String, each in its own file; the file paths decide the order.
        // Limit: as a regression detector this row is machine-dependent. Without the symbol sorts the order follows the
        // file system listing, which may happen to match the sorted order (then a removed sort stays green here).
        val receiverOfFile = mapOf(
            "KlangScriptArrayExtensions.kt" to "Array",
            "KlangScriptStringExtensions.kt" to "String",
        )
        val inSourceOrder = File("klangscript-libs/src/commonMain/kotlin/stdlib").listFiles()!!
            .filter { it.name in receiverOfFile && it.readText().contains("fun indexOf(") }
            .sortedBy { it.invariantSeparatorsPath }
            .map { receiverOfFile.getValue(it.name) }

        withClue("both declaring files are found") { inSourceOrder.size shouldBe 2 }

        val symbol = generatedStdlibDocs.getValue("indexOf")

        symbol.variants.filterIsInstance<KlangCallable>().map { it.receiver?.simpleName } shouldBe inSourceOrder
        // the first variant's category: `array` (KlangScriptArrayExtensions.kt sorts first); it read `string` while
        // the order followed the file system on a machine that listed the String file first
        symbol.category shouldBe "array"
    }
})

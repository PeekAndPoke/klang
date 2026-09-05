/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.peekandpoke.klang.script.generated.generatedSprudelDocs
import io.peekandpoke.klang.script.klangScript
import io.peekandpoke.klang.script.stdlibLib
import io.peekandpoke.klang.script.types.KlangCodeSampleType
import io.peekandpoke.klang.sprudel.SprudelPattern
import io.peekandpoke.klang.sprudel.lang.sprudelLib
import java.io.File

/**
 * Compiles every example in the sprudel DSL's own KDoc.
 *
 * These are not decorative snippets. `klangscript-ksp`'s KDocParser lifts every
 * ```` ```KlangScript ```` fence out of the KDoc into `KlangDecl.samples`, and
 * `KlangScriptLibraryDocsPage` renders each one as a `PlayableCodeExample` with a real
 * play button, next to the same text the editor's hover-help popup shows. A reader who
 * presses play on a broken example learns that Klang is broken.
 *
 * Nothing checked them until now, and they rot in a very specific way: a DSL function gets
 * renamed and its own examples keep calling the old name. That is exactly how eight
 * `unison`/`uni`/`voices` examples were left calling a `.detune()` that had not existed on
 * a pattern since the detune-to-spread rename.
 *
 * **What this catches and what it does not.** Each example is compiled AND queried for one
 * cycle. Compiling proves the functions exist and the call shape is accepted; querying forces
 * the lambda bodies that compiling skips, which is how three `filter` examples and one
 * `tweaks` example were caught reaching for things that do not exist.
 *
 * Two known blind spots, both measured by mutation, not assumed:
 *
 * - A lambda that the one-cycle query does not happen to invoke is still unchecked. A
 *   deliberate typo inside `plyWith(4, x => x.NOSUCHFN(7))` passes; the same typo outside a
 *   lambda is caught. Widening the query arc would narrow this, at a cost.
 * - It cannot prove an example is *right*. Three reverb and delay examples compiled and
 *   queried perfectly while being silent (a send whose gate parameter was missing), and a
 *   wrong unit in the prose survives anything short of a listener. That is the render gate in
 *   `docs/tasks/tutorial-curriculum.md`, or a human. This spec is the cheap half, deliberately.
 *
 * Samples are deduplicated by code: the same example usually appears on all four doors of a
 * function (`SprudelPattern.x`, `String.x`, `x()`, `PatternMapperFn.x`), so compiling each
 * distinct string once keeps the run quick and the failure list readable.
 */
class DslDocExamplesSpec : StringSpec({

    // Mirrors KlangScriptLibraryDocsPage: sprudel samples auto-import stdlib + sprudel.
    fun engine() = klangScript {
        registerLibrary(stdlibLib)
        registerLibrary(sprudelLib)
    }

    fun compileSample(code: String): SprudelPattern? = SprudelPattern.compile(
        engine(),
        "import * from \"stdlib\"\nimport * from \"sprudel\"\n" + code,
    )

    "every playable example in the sprudel DSL docs compiles" {
        // code -> the symbols that show it, so a failure names where to go and fix it
        val byCode = linkedMapOf<String, MutableSet<String>>()

        for ((name, symbol) in generatedSprudelDocs) {
            for (decl in symbol.variants) {
                for (sample in decl.samples) {
                    if (sample.type == KlangCodeSampleType.PLAYABLE) {
                        byCode.getOrPut(sample.code) { linkedSetOf() }.add(name)
                    }
                }
            }
        }

        val broken = linkedMapOf<String, String>()

        for ((code, owners) in byCode) {
            // Compiling alone is not enough: a mapper argument (`plyWith(4, x => x.add(7))`)
            // stores its lambda and never runs it, so a typo inside the body compiles happily.
            // Querying one cycle forces those bodies, which is where several of these examples
            // actually live. It still proves nothing about the SOUND.
            val error = runCatching { compileSample(code)?.also { it.queryArc(0.0, 1.0) } }
                .fold(
                    onSuccess = { if (it == null) "compiled to null" else null },
                    onFailure = { it.message?.lines()?.firstOrNull() ?: it::class.simpleName },
                )

            if (error != null) {
                broken[code] = "${owners.joinToString(", ")}: ${code.replace('\n', ' ')}\n      -> $error"
            }
        }

        // Regenerating the baseline is one copy: this file IS the baseline format.
        val dump = File("build/reports/dsl-doc-examples-failing.txt")
        dump.parentFile.mkdirs()
        dump.writeText(broken.keys.joinToString("\n$SEPARATOR\n"))

        val baseline = loadBaseline()
        val newlyBroken = broken.filterKeys { it !in baseline }
        val newlyFixed = baseline.filter { it !in broken.keys }

        // The whole list goes in the clue: kotest prints only the first element of a failed
        // collection assertion, and a doc-rot sweep is only useful if you can see every hit.
        withClue(
            buildString {
                append("${newlyBroken.size} NEWLY broken of ${byCode.size} distinct playable examples ")
                append("(${baseline.size} already known-broken, see $BASELINE_RESOURCE).\n")
                append("Each is rendered with a play button, so each one is a broken button.\n\n")
                newlyBroken.forEach { (code, why) -> append("  - ").append(why).append("\n\n") }
            }
        ) {
            newlyBroken.keys.shouldBeEmpty()
        }

        // The ratchet: a baselined example that now compiles must leave the baseline, or the
        // list quietly becomes a place where rot hides again.
        withClue(
            "these examples compile now — delete them from $BASELINE_RESOURCE " +
                "(the current failing set was just written to ${dump.path}):\n" +
                newlyFixed.joinToString("\n\n") { "  - ${it.replace('\n', ' ')}" }
        ) {
            newlyFixed.shouldBeEmpty()
        }
    }
})

private const val BASELINE_RESOURCE = "dsl-doc-examples-baseline.txt"

/** Records are separated by a line of dashes, because an example may itself be multi-line. */
private const val SEPARATOR = "--------"

/**
 * Examples that are known-broken and allowed to stay that way for now. **Currently empty.**
 *
 * It was 42 of 1425 when this gate was introduced (2026-08-31): 38 were doc rot and were fixed,
 * and the last 4 were removed from the KDoc because they were not rot at all but call forms no
 * test has ever run — see `docs/tasks/sprudel-function-testing.md`. Keep it empty if you can:
 * an entry here is a to-do, never an exemption, and the ratchet below means it can only shrink.
 *
 * Records are separated by [SEPARATOR]; a record starting with `#` is a comment.
 */
private fun loadBaseline(): Set<String> {
    val stream = object {}.javaClass.classLoader.getResourceAsStream(BASELINE_RESOURCE)
        ?: return emptySet()

    return stream.bufferedReader().readText()
        .split("\n$SEPARATOR\n")
        .map { it.trim('\n') }
        .filter { it.isNotBlank() && !it.startsWith("#") }
        .toSet()
}

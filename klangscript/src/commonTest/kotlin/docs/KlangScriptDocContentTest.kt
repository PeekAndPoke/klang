package io.peekandpoke.klang.script.docs

import io.kotest.core.spec.style.StringSpec
import io.peekandpoke.klang.script.klangScript
import io.peekandpoke.klang.script.stdlib.KlangStdLib

/**
 * Verifies that all KlangScript documentation examples execute without errors.
 *
 * Each example is run through a fresh KlangScriptEngine with the stdlib.
 * The test passes if no exception is thrown — it does not check output values.
 */
class KlangScriptDocContentTest : StringSpec({

    for (section in klangScriptDocSections) {
        for (example in section.examples) {
            val testName = "${section.title} — ${example.title ?: "example"}"

            testName {
                val engine = klangScript {
                    registerLibrary(KlangStdLib.create())
                }

                engine.execute(example.code.trimMargin())
            }
        }
    }
})

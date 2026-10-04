/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.script.stdlib

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.peekandpoke.klang.script.klangScript
import io.peekandpoke.klang.script.runtime.KlangScriptImportError
import io.peekandpoke.klang.script.runtime.KlangScriptReferenceError

/**
 * How a script reaches the standard library (2026-10-04, when its `export { ... }` source block was removed because it
 * governed nothing): importing "stdlib" loads every stdlib name into the engine's native environment, the parent of
 * every script scope. The library has no export list, so a selective or namespace import of it has nothing to bind.
 */
class StdLibScopeSpec : StringSpec({

    val names = listOf("Math", "Object", "console", "Ignitor", "Ign", "Katalyst", "Kat")

    "import * from stdlib loads every stdlib name" {
        val engine = klangScript()
        engine.execute("""import * from "stdlib"""")

        engine.execute("Math.max(1, 2)").toDisplayString() shouldBe "2"
        names.forEach { name -> withClue(name) { engine.execute(name).toDisplayString().isNotEmpty() shouldBe true } }
    }

    "without an import, the stdlib names are not in scope" {
        val engine = klangScript()

        names.forEach { name ->
            withClue(name) { shouldThrow<KlangScriptReferenceError> { engine.execute(name) } }
        }
    }

    "a selective import of stdlib has nothing to bind: the library exports no names" {
        val engine = klangScript()

        val error = shouldThrow<KlangScriptImportError> { engine.execute("""import { Math } from "stdlib"""") }
        error.message shouldContain "Math"
    }
})

/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.script

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.script.builder.registerFunction
import io.peekandpoke.klang.script.builder.registerLibrary
import io.peekandpoke.klang.script.builder.registerType

/**
 * Two libraries that extend the same receiver type are merged per method name, in import order.
 *
 * Before 2026-09-08 `Environment.register` replaced the receiver's whole method map with the map of
 * the library imported last, so `import * from "stdlib"` followed by `import * from "sprudel"` (the
 * header of every song) silently removed every stdlib string method. Found while adding
 * `"M3".toRatio()` (`docs/tasks-archive/2026-09/20260908-klangscript-number-methods.md`); the sprudel side is pinned in
 * `LangStdlibStringMethodCollisionSpec`.
 */
class LibraryExtensionMergeSpec : StringSpec({

    class Box(val value: Double)

    val first = klangScriptLibrary("first") {
        registerType<Box> {
            registerMethod("twice") { Box(value * 2) }
            registerMethod("shared") { Box(value + 1) }
            registerProperty("doubled") { value * 2 }
        }
        registerFunction("box") { value: Double -> Box(value) }
    }

    val second = klangScriptLibrary("second") {
        registerType<Box> {
            registerMethod("thrice") { Box(value * 3) }
            registerMethod("shared") { Box(value + 100) }
            registerProperty("tripled") { value * 3 }
        }
        registerFunction("unbox") { box: Box -> box.value }
    }

    fun engine() = klangScriptEngine {
        registerLibrary(first)
        registerLibrary(second)
    }

    "methods of both libraries stay callable after both imports, in either order" {
        listOf(
            "first then second" to "import * from \"first\"\nimport * from \"second\"\n",
            "second then first" to "import * from \"second\"\nimport * from \"first\"\n",
        ).forEach { (name, imports) ->
            withClue(name) {
                val result = engine().execute(imports + "unbox(box(5).twice().thrice())")

                result.toDisplayString() shouldBe "30"
            }
        }
    }

    "properties of both libraries stay readable after both imports" {
        // The properties map has the same receiver-keyed shape and used the same shallow putAll
        val result = engine().execute("import * from \"first\"\nimport * from \"second\"\nbox(5).doubled + box(5).tripled")

        result.toDisplayString() shouldBe "25"
    }

    "a method registered by both belongs to the library imported last" {
        val firstWins = engine().execute("import * from \"second\"\nimport * from \"first\"\nunbox(box(5).shared())")
        firstWins.toDisplayString() shouldBe "6"

        val secondWins = engine().execute("import * from \"first\"\nimport * from \"second\"\nunbox(box(5).shared())")
        secondWins.toDisplayString() shouldBe "105"
    }
})

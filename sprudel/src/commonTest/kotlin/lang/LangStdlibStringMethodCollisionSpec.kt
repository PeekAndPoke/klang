/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.sprudel.lang

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.peekandpoke.klang.script.KlangScriptLibrary
import io.peekandpoke.klang.script.klangScript
import io.peekandpoke.klang.script.runtime.NumberValue
import io.peekandpoke.klang.script.runtime.StringValue
import io.peekandpoke.klang.script.stdlib.KlangStdLib

/**
 * Sprudel registers every pattern function as a method on strings too (`"bd sd".fast(2)`), and a
 * library imported later replaces a method of the same name and receiver. So a stdlib string method
 * that shares its name with a sprudel function is silently dead in every song, which imports both.
 *
 * Found in review on 2026-09-08: the interval door was first called `ratio()`, the name of sprudel's
 * colon-ratio step, and `"M3".ratio()` would have yielded a null voice value instead of 1.2599
 * (`docs/tasks-archive/2026-09/20260908-klangscript-number-methods.md`). The door is `toRatio()` now, and this spec keeps the
 * two name sets apart for good.
 */
class LangStdlibStringMethodCollisionSpec : StringSpec({

    fun stringMethodNames(library: KlangScriptLibrary): Set<String> =
        library.native.extensionMethods[StringValue::class]?.keys?.toSet() ?: emptySet()

    "no stdlib string method shares its name with a sprudel string method" {
        val stdlib = stringMethodNames(KlangStdLib.create())
        val sprudel = stringMethodNames(sprudelLib)

        stdlib.shouldNotBeEmptyClue()
        sprudel.shouldNotBeEmptyClue()

        // `repeat` and `slice` collided before this spec existed: in a song, sprudel's pattern versions win
        // and the stdlib string versions are unreachable. Parked for the maintainer on 2026-09-08; a new
        // name on the list needs the same decision, not an allowlist entry.
        val parkedCollisions = setOf("repeat", "slice")

        withClue("stdlib string methods that sprudel would overwrite") {
            (stdlib.intersect(sprudel) - parkedCollisions).toList().shouldBeEmpty()
        }
    }

    "a stdlib string method survives the sprudel import" {
        // Before 2026-09-08 the library registration replaced the receiver's whole method map, so every
        // stdlib string method was gone in a song (which imports stdlib, then sprudel).
        val engine = klangScript {
            registerLibrary(KlangStdLib.create())
            registerLibrary(sprudelLib)
        }

        val result = engine.execute("import * from \"stdlib\"\nimport * from \"sprudel\"\n\"hello\".toUpperCase()")

        result.shouldBeInstanceOf<StringValue>().value shouldBe "HELLO"
    }

    "the interval door still answers with both libraries imported, in either order" {
        listOf(
            "stdlib then sprudel" to "import * from \"stdlib\"\nimport * from \"sprudel\"\n",
            "sprudel then stdlib" to "import * from \"sprudel\"\nimport * from \"stdlib\"\n",
        ).forEach { (name, imports) ->
            withClue(name) {
                val engine = klangScript {
                    registerLibrary(KlangStdLib.create())
                    registerLibrary(sprudelLib)
                }

                val result = engine.execute(imports + "\"M3\".toRatio()")

                result.shouldBeInstanceOf<NumberValue>().value shouldBe (1.2599 plusOrMinus 1e-4)
            }
        }
    }
})

private fun Set<String>.shouldNotBeEmptyClue() {
    withClue("a library with no string methods means the lookup is wrong, not that there is no collision") {
        isEmpty() shouldBe false
    }
}

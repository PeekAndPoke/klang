package io.peekandpoke.klang.script.stdlib

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.peekandpoke.klang.script.klangScript
import io.peekandpoke.klang.script.runtime.ArrayValue
import io.peekandpoke.klang.script.runtime.BooleanValue
import io.peekandpoke.klang.script.runtime.NumberValue
import io.peekandpoke.klang.script.runtime.StringValue

/**
 * Tests for array methods registered as extension methods
 *
 * Validates:
 * - length() method
 * - push(), pop(), shift(), unshift()
 * - slice(), concat(), join(), reverse()
 * - indexOf(), includes()
 */
class StdLibArrayMethodsTest : StringSpec({

    // ===== Property-like methods =====

    "Array length() on empty array" {
        val engine = klangScript {
            registerLibrary(KlangStdLib.create())
        }

        val result = engine.execute(
            """
            import * from "stdlib"
            [].length()
            """.trimIndent()
        )

        (result as NumberValue).value shouldBe 0.0
    }

    "Array length() on array with elements" {
        val engine = klangScript {
            registerLibrary(KlangStdLib.create())
        }

        val result = engine.execute(
            """
            import * from "stdlib"
            [1, 2, 3, 4, 5].length()
            """.trimIndent()
        )

        (result as NumberValue).value shouldBe 5.0
    }

    // ===== Mutating methods =====

    "Array push() adds elements and returns new length" {
        val engine = klangScript {
            registerLibrary(KlangStdLib.create())
        }

        val result = engine.execute(
            """
            import * from "stdlib"
            let arr = [1, 2, 3]
            arr.push(4, 5)
            """.trimIndent()
        )

        (result as NumberValue).value shouldBe 5.0
        val arr = engine.getVariable("arr") as ArrayValue
        arr.elements.size shouldBe 5
    }

    "Array pop() removes and returns last element" {
        val engine = klangScript {
            registerLibrary(KlangStdLib.create())
        }

        val result = engine.execute(
            """
            import * from "stdlib"
            let arr = [1, 2, 3]
            arr.pop()
            """.trimIndent()
        )

        (result as NumberValue).value shouldBe 3.0
        val arr = engine.getVariable("arr") as ArrayValue
        arr.elements.size shouldBe 2
    }

    "Array shift() removes and returns first element" {
        val engine = klangScript {
            registerLibrary(KlangStdLib.create())
        }

        val result = engine.execute(
            """
            import * from "stdlib"
            let arr = [1, 2, 3]
            arr.shift()
            """.trimIndent()
        )

        (result as NumberValue).value shouldBe 1.0
        val arr = engine.getVariable("arr") as ArrayValue
        arr.elements.size shouldBe 2
    }

    "Array unshift() adds elements at beginning" {
        val engine = klangScript {
            registerLibrary(KlangStdLib.create())
        }

        val result = engine.execute(
            """
            import * from "stdlib"
            let arr = [3, 4]
            arr.unshift(1, 2)
            """.trimIndent()
        )

        (result as NumberValue).value shouldBe 4.0
        val arr = engine.getVariable("arr") as ArrayValue
        arr.elements.size shouldBe 4
    }

    // ===== Non-mutating methods =====

    "Array slice() extracts sub-array" {
        val engine = klangScript {
            registerLibrary(KlangStdLib.create())
        }

        val result = engine.execute(
            """
            import * from "stdlib"
            [1, 2, 3, 4, 5].slice(1, 4)
            """.trimIndent()
        ) as ArrayValue

        result.elements.size shouldBe 3
        (result.elements[0] as NumberValue).value shouldBe 2.0
        (result.elements[2] as NumberValue).value shouldBe 4.0
    }

    "Array concat() combines arrays" {
        val engine = klangScript {
            registerLibrary(KlangStdLib.create())
        }

        val result = engine.execute(
            """
            import * from "stdlib"
            [1, 2].concat([3, 4])
            """.trimIndent()
        ) as ArrayValue

        result.elements.size shouldBe 4
        (result.elements[3] as NumberValue).value shouldBe 4.0
    }

    "Array join() creates string" {
        val engine = klangScript {
            registerLibrary(KlangStdLib.create())
        }

        val result = engine.execute(
            """
            import * from "stdlib"
            [1, 2, 3].join(", ")
            """.trimIndent()
        )

        (result as StringValue).value shouldContain "1"
        result.value shouldContain "2"
        result.value shouldContain "3"
    }

    "Array reverse() reverses elements" {
        val engine = klangScript {
            registerLibrary(KlangStdLib.create())
        }

        val result = engine.execute(
            """
            import * from "stdlib"
            [1, 2, 3].reverse()
            """.trimIndent()
        ) as ArrayValue

        (result.elements[0] as NumberValue).value shouldBe 3.0
        (result.elements[2] as NumberValue).value shouldBe 1.0
    }

    "Array indexOf() finds element index" {
        val engine = klangScript {
            registerLibrary(KlangStdLib.create())
        }

        val result = engine.execute(
            """
            import * from "stdlib"
            [10, 20, 30].indexOf(20)
            """.trimIndent()
        )

        (result as NumberValue).value shouldBe 1.0
    }

    "Array includes() checks if element exists" {
        val engine = klangScript {
            registerLibrary(KlangStdLib.create())
        }

        val result = engine.execute(
            """
            import * from "stdlib"
            [10, 20, 30].includes(20)
            """.trimIndent()
        )

        (result as BooleanValue).value shouldBe true
    }
})

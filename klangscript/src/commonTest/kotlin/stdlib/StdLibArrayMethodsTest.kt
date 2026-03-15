package io.peekandpoke.klang.script.stdlib

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.script.klangScript
import io.peekandpoke.klang.script.runtime.ArrayValue
import io.peekandpoke.klang.script.runtime.BooleanValue
import io.peekandpoke.klang.script.runtime.NumberValue
import io.peekandpoke.klang.script.runtime.StringValue

/**
 * Tests for array methods (Kotlin-style API)
 */
class StdLibArrayMethodsTest : StringSpec({

    // ===== Properties =====

    "Array size() on empty array" {
        val engine = klangScript { registerLibrary(KlangStdLib.create()) }
        val result = engine.execute("""import * from "stdlib"; [].size()""".trimIndent())
        (result as NumberValue).value shouldBe 0.0
    }

    "Array size() on array with elements" {
        val engine = klangScript { registerLibrary(KlangStdLib.create()) }
        val result = engine.execute("""import * from "stdlib"; [1, 2, 3, 4, 5].size()""".trimIndent())
        (result as NumberValue).value shouldBe 5.0
    }

    // ===== Access =====

    "Array first() returns first element" {
        val engine = klangScript { registerLibrary(KlangStdLib.create()) }
        val result = engine.execute("""import * from "stdlib"; [10, 20, 30].first()""".trimIndent())
        (result as NumberValue).value shouldBe 10.0
    }

    "Array last() returns last element" {
        val engine = klangScript { registerLibrary(KlangStdLib.create()) }
        val result = engine.execute("""import * from "stdlib"; [10, 20, 30].last()""".trimIndent())
        (result as NumberValue).value shouldBe 30.0
    }

    // ===== Mutating =====

    "Array add() adds element and returns new size" {
        val engine = klangScript { registerLibrary(KlangStdLib.create()) }
        val result = engine.execute(
            """
            import * from "stdlib"
            let arr = [1, 2, 3]
            arr.add(4)
            """.trimIndent()
        )
        (result as NumberValue).value shouldBe 4.0
        val arr = engine.getVariable("arr") as ArrayValue
        arr.elements.size shouldBe 4
    }

    "Array removeLast() removes and returns last element" {
        val engine = klangScript { registerLibrary(KlangStdLib.create()) }
        val result = engine.execute(
            """
            import * from "stdlib"
            let arr = [1, 2, 3]
            arr.removeLast()
            """.trimIndent()
        )
        (result as NumberValue).value shouldBe 3.0
        val arr = engine.getVariable("arr") as ArrayValue
        arr.elements.size shouldBe 2
    }

    "Array removeFirst() removes and returns first element" {
        val engine = klangScript { registerLibrary(KlangStdLib.create()) }
        val result = engine.execute(
            """
            import * from "stdlib"
            let arr = [1, 2, 3]
            arr.removeFirst()
            """.trimIndent()
        )
        (result as NumberValue).value shouldBe 1.0
        val arr = engine.getVariable("arr") as ArrayValue
        arr.elements.size shouldBe 2
    }

    "Array removeAt() removes element at index" {
        val engine = klangScript { registerLibrary(KlangStdLib.create()) }
        val result = engine.execute(
            """
            import * from "stdlib"
            let arr = [10, 20, 30]
            arr.removeAt(1)
            """.trimIndent()
        )
        (result as NumberValue).value shouldBe 20.0
        val arr = engine.getVariable("arr") as ArrayValue
        arr.elements.size shouldBe 2
    }

    // ===== Non-mutating =====

    "Array reversed() returns reversed copy" {
        val engine = klangScript { registerLibrary(KlangStdLib.create()) }
        val result = engine.execute("""import * from "stdlib"; [1, 2, 3].reversed()""".trimIndent()) as ArrayValue
        (result.elements[0] as NumberValue).value shouldBe 3.0
        (result.elements[2] as NumberValue).value shouldBe 1.0
    }

    "Array drop() skips first n elements" {
        val engine = klangScript { registerLibrary(KlangStdLib.create()) }
        val result = engine.execute("""import * from "stdlib"; [1, 2, 3, 4, 5].drop(2)""".trimIndent()) as ArrayValue
        result.elements.size shouldBe 3
        (result.elements[0] as NumberValue).value shouldBe 3.0
    }

    "Array take() keeps first n elements" {
        val engine = klangScript { registerLibrary(KlangStdLib.create()) }
        val result = engine.execute("""import * from "stdlib"; [1, 2, 3, 4, 5].take(2)""".trimIndent()) as ArrayValue
        result.elements.size shouldBe 2
        (result.elements[1] as NumberValue).value shouldBe 2.0
    }

    "Array subList() extracts sub-array" {
        val engine = klangScript { registerLibrary(KlangStdLib.create()) }
        val result = engine.execute("""import * from "stdlib"; [1, 2, 3, 4, 5].subList(1, 4)""".trimIndent()) as ArrayValue
        result.elements.size shouldBe 3
        (result.elements[0] as NumberValue).value shouldBe 2.0
        (result.elements[2] as NumberValue).value shouldBe 4.0
    }

    "Array joinToString() creates string" {
        val engine = klangScript { registerLibrary(KlangStdLib.create()) }
        val result = engine.execute("""import * from "stdlib"; [1, 2, 3].joinToString(", ")""".trimIndent())
        (result as StringValue).value shouldBe "1, 2, 3"
    }

    "Array indexOf() finds element index" {
        val engine = klangScript { registerLibrary(KlangStdLib.create()) }
        val result = engine.execute("""import * from "stdlib"; [10, 20, 30].indexOf(20)""".trimIndent())
        (result as NumberValue).value shouldBe 1.0
    }

    "Array contains() checks if element exists" {
        val engine = klangScript { registerLibrary(KlangStdLib.create()) }
        val result = engine.execute("""import * from "stdlib"; [10, 20, 30].contains(20)""".trimIndent())
        (result as BooleanValue).value shouldBe true
    }

    "Array isEmpty() on empty array" {
        val engine = klangScript { registerLibrary(KlangStdLib.create()) }
        val result = engine.execute("""import * from "stdlib"; [].isEmpty()""".trimIndent())
        (result as BooleanValue).value shouldBe true
    }

    "Array isNotEmpty() on non-empty array" {
        val engine = klangScript { registerLibrary(KlangStdLib.create()) }
        val result = engine.execute("""import * from "stdlib"; [1].isNotEmpty()""".trimIndent())
        (result as BooleanValue).value shouldBe true
    }
})

package io.peekandpoke.klang.script

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.peekandpoke.klang.script.runtime.ArrayValue
import io.peekandpoke.klang.script.runtime.NumberValue

/**
 * Tests for array literal syntax and evaluation
 *
 * Validates:
 * - Empty arrays
 * - Single and multiple element arrays
 * - Mixed type arrays
 * - Nested arrays
 * - Expressions as elements
 * - Arrays in variables
 * - Arrays as function arguments
 * - Arrays in objects
 * - Trailing commas
 */
class ArrayLiteralTest : StringSpec({

    "Empty array" {
        val engine = klangScript()
        val result = engine.execute("[]")

        (result as ArrayValue).elements.size shouldBe 0
        result.toDisplayString() shouldBe "[]"
    }

    "Single element array" {
        val engine = klangScript()
        val result = engine.execute("[42]") as ArrayValue

        result.elements.size shouldBe 1
        result.toDisplayString() shouldContain "42"
    }

    "Multiple element array - numbers" {
        val engine = klangScript()
        val result = engine.execute("[1, 2, 3]") as ArrayValue

        result.elements.size shouldBe 3
        result.toDisplayString() shouldContain "1"
        result.toDisplayString() shouldContain "2"
        result.toDisplayString() shouldContain "3"
    }

    "Array with strings" {
        val engine = klangScript()
        val result = engine.execute("""["a", "b", "c"]""") as ArrayValue

        result.elements.size shouldBe 3
        result.toDisplayString() shouldBe "[a, b, c]"
    }

    "Mixed type array" {
        val engine = klangScript()
        val result = engine.execute("""[1, "hello", true, null]""") as ArrayValue

        result.elements.size shouldBe 4
        result.toDisplayString() shouldContain "1"
        result.toDisplayString() shouldContain "hello"
        result.toDisplayString() shouldContain "true"
        result.toDisplayString() shouldContain "null"
    }

    "Array with expressions" {
        val engine = klangScript()
        val result = engine.execute("[1 + 1, 2 * 2, 3 - 1]") as ArrayValue

        result.elements.size shouldBe 3
        result.toDisplayString() shouldContain "2"
        result.toDisplayString() shouldContain "4"
    }

    "Array in variable" {
        val engine = klangScript()
        val result = engine.execute(
            """
            let arr = [1, 2, 3]
            arr
            """.trimIndent()
        ) as ArrayValue

        result.elements.size shouldBe 3
        result.toDisplayString() shouldContain "1"
        result.toDisplayString() shouldContain "2"
        result.toDisplayString() shouldContain "3"
    }

    "Multiple arrays in variables" {
        val engine = klangScript()
        engine.execute(
            """
            let numbers = [1, 2, 3]
            let words = ["a", "b", "c"]
            let mixed = [1, "hello", true]
            """.trimIndent()
        )

        val numbers = engine.getVariable("numbers") as ArrayValue
        numbers.elements.size shouldBe 3
        numbers.toDisplayString() shouldContain "1"
        numbers.toDisplayString() shouldContain "2"
        numbers.toDisplayString() shouldContain "3"

        val words = engine.getVariable("words") as ArrayValue
        words.elements.size shouldBe 3
        words.toDisplayString() shouldBe "[a, b, c]"

        val mixed = engine.getVariable("mixed") as ArrayValue
        mixed.elements.size shouldBe 3
        mixed.toDisplayString() shouldContain "1"
        mixed.toDisplayString() shouldContain "hello"
        mixed.toDisplayString() shouldContain "true"
    }

    "Array with variables as elements" {
        val engine = klangScript()
        val result = engine.execute(
            """
            let x = 5
            let y = 10
            [x, y, x + y]
            """.trimIndent()
        )

        (result as ArrayValue).elements.size shouldBe 3
        result.toDisplayString() shouldContain "5"
        result.toDisplayString() shouldContain "10"
        result.toDisplayString() shouldContain "15"
    }

    "Array as function argument" {
        val captured = mutableListOf<String>()

        val engine = klangScript {
            registerFunctionRaw("capture") { args, _ ->
                captured.add(args[0].toDisplayString())
                args[0]
            }
        }

        engine.execute("""capture([1, 2, 3])""")

        captured.size shouldBe 1
        captured[0] shouldContain "1"
        captured[0] shouldContain "2"
        captured[0] shouldContain "3"
    }

    "Array with function calls as elements" {
        val engine = klangScript {
            registerFunctionRaw("getValue") { _, _ -> NumberValue(42.0) }
            registerFunctionRaw("getDouble") { args, _ ->
                val n = args[0] as NumberValue
                NumberValue(n.value * 2)
            }
        }

        val result = engine.execute("[getValue(), getDouble(5)]") as ArrayValue
        result.elements.size shouldBe 2
        result.toDisplayString() shouldContain "42"
        result.toDisplayString() shouldContain "10"
    }

    "Nested arrays" {
        val engine = klangScript()
        val result = engine.execute("[[1, 2], [3, 4], [5, 6]]") as ArrayValue

        result.elements.size shouldBe 3
        result.toDisplayString() shouldContain "1"
        result.toDisplayString() shouldContain "2"
        result.toDisplayString() shouldContain "3"
        result.toDisplayString() shouldContain "4"
        result.toDisplayString() shouldContain "5"
        result.toDisplayString() shouldContain "6"
    }

    "Deeply nested arrays" {
        val engine = klangScript()
        val result = engine.execute("[[[1, 2]], [[3, 4]]]") as ArrayValue

        result.elements.size shouldBe 2
        result.toDisplayString() shouldContain "1"
        result.toDisplayString() shouldContain "2"
        result.toDisplayString() shouldContain "3"
        result.toDisplayString() shouldContain "4"
    }

    "Array with objects" {
        val engine = klangScript()
        val result = engine.execute("""[{ a: 1 }, { b: 2 }]""")

        result.toDisplayString() shouldContain "[object]"
        (result as ArrayValue).elements.size shouldBe 2
    }

    "Object with array property" {
        val engine = klangScript()
        val result = engine.execute(
            """
            let obj = { items: [1, 2, 3], names: ["a", "b"] }
            obj
            """.trimIndent()
        )

        result.toDisplayString() shouldBe "[object]"
    }

    "Trailing comma in array" {
        val engine = klangScript()
        val result = engine.execute("[1, 2, 3,]") as ArrayValue

        result.elements.size shouldBe 3
        result.toDisplayString() shouldContain "1"
        result.toDisplayString() shouldContain "2"
        result.toDisplayString() shouldContain "3"
    }

    "Array with trailing comma after single element" {
        val engine = klangScript()
        val result = engine.execute("[42,]") as ArrayValue

        result.elements.size shouldBe 1
        result.toDisplayString() shouldContain "42"
    }

    "Multi-line array" {
        val engine = klangScript()
        val result = engine.execute(
            """
            [
                1,
                2,
                3
            ]
            """.trimIndent()
        ) as ArrayValue

        result.elements.size shouldBe 3
        result.toDisplayString() shouldContain "1"
        result.toDisplayString() shouldContain "2"
        result.toDisplayString() shouldContain "3"
    }

    "Array with mixed expressions and literals" {
        val engine = klangScript()
        val result = engine.execute(
            """
            let x = 10
            [1, x, x * 2, 5 + 5, true, "hello"]
            """.trimIndent()
        ) as ArrayValue

        result.elements.size shouldBe 6
        result.toDisplayString() shouldContain "1"
        result.toDisplayString() shouldContain "10"
        result.toDisplayString() shouldContain "20"
        result.toDisplayString() shouldContain "true"
        result.toDisplayString() shouldContain "hello"
    }

    "Empty nested arrays" {
        val engine = klangScript()
        val result = engine.execute("[[], [], []]")

        (result as ArrayValue).elements.size shouldBe 3
        result.toDisplayString() shouldBe "[[], [], []]"
    }

    "Array with arrow functions" {
        val engine = klangScript()
        val result = engine.execute("[x => x + 1, y => y * 2]")

        (result as ArrayValue).elements.size shouldBe 2
        result.toDisplayString() shouldContain "function"
    }
})

package io.peekandpoke.klang.script

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.script.runtime.*

/**
 * Comprehensive tests for the 11 new easy language features:
 *
 * 1. Scientific notation number literals (1e6, 2.5e-3)
 * 2. Strict equality (===, !==)
 * 3. Exponentiation (**)
 * 4. Variable assignment (x = expr, obj.prop = expr, arr[i] = expr)
 * 5. Compound assignment (+=, -=, *=, /=, %=)
 * 6. Prefix increment/decrement (++x, --x)
 * 7. Postfix increment/decrement (x++, x--)
 * 8. Ternary operator (cond ? then : else)
 * 9. Array/object index access (arr[i], obj["key"])
 * 10. Object property shorthand ({ name, age })
 * 11. `in` operator ("key" in obj)
 */
class EasyFeaturesTest : StringSpec({

    fun engine() = klangScript()

    // =============================================================
    // Feature 1: Scientific notation number literals
    // =============================================================

    "scientific notation: 1e6" {
        val result = engine().execute("1e6")
        result shouldBe NumberValue(1_000_000.0)
    }

    "scientific notation: 2.5e3" {
        val result = engine().execute("2.5e3")
        result shouldBe NumberValue(2500.0)
    }

    "scientific notation: 1e-3" {
        val result = engine().execute("1e-3")
        result shouldBe NumberValue(0.001)
    }

    "scientific notation: 2.5e-3" {
        val result = engine().execute("2.5e-3")
        result shouldBe NumberValue(0.0025)
    }

    "scientific notation: 1E+10 (uppercase E with plus)" {
        val result = engine().execute("1E+10")
        result shouldBe NumberValue(10_000_000_000.0)
    }

    "scientific notation: arithmetic with sci notation" {
        val result = engine().execute("1e3 + 2e2")
        result shouldBe NumberValue(1200.0)
    }

    // =============================================================
    // Feature 2: Strict equality (===, !==)
    // =============================================================

    "strict equality: same number === same number" {
        val result = engine().execute("42 === 42")
        result shouldBe BooleanValue(true)
    }

    "strict equality: different numbers" {
        val result = engine().execute("42 === 43")
        result shouldBe BooleanValue(false)
    }

    "strict equality: same string === same string" {
        val result = engine().execute(""""hello" === "hello"""")
        result shouldBe BooleanValue(true)
    }

    "strict equality: number !== string with same display" {
        // strict: 1 !== "1" because different types
        val result = engine().execute("""1 !== "1"""")
        result shouldBe BooleanValue(true)
    }

    "strict equality: true === true" {
        val result = engine().execute("true === true")
        result shouldBe BooleanValue(true)
    }

    "strict equality: true !== false" {
        val result = engine().execute("true !== false")
        result shouldBe BooleanValue(true)
    }

    "strict equality: null === null" {
        val result = engine().execute("null === null")
        result shouldBe BooleanValue(true)
    }

    "strict equality: null !== false (different types)" {
        val result = engine().execute("null !== false")
        result shouldBe BooleanValue(true)
    }

    "strict equality: objects by reference (different objects !== )" {
        val result = engine().execute(
            """
            let a = { x: 1 }
            let b = { x: 1 }
            a === b
            """.trimIndent()
        )
        result shouldBe BooleanValue(false)
    }

    "strict equality: same object reference ===" {
        val result = engine().execute(
            """
            let a = { x: 1 }
            let b = a
            a === b
            """.trimIndent()
        )
        result shouldBe BooleanValue(true)
    }

    // =============================================================
    // Feature 3: Exponentiation (**)
    // =============================================================

    "exponentiation: 2 ** 3 = 8" {
        val result = engine().execute("2 ** 3")
        result shouldBe NumberValue(8.0)
    }

    "exponentiation: 10 ** 0 = 1" {
        val result = engine().execute("10 ** 0")
        result shouldBe NumberValue(1.0)
    }

    "exponentiation: 2 ** 10 = 1024" {
        val result = engine().execute("2 ** 10")
        result shouldBe NumberValue(1024.0)
    }

    "exponentiation: right-associative: 2 ** 3 ** 2 = 2 ** 9 = 512" {
        val result = engine().execute("2 ** 3 ** 2")
        result shouldBe NumberValue(512.0)
    }

    "exponentiation: higher precedence than multiplication: 2 * 3 ** 2 = 2 * 9 = 18" {
        val result = engine().execute("2 * 3 ** 2")
        result shouldBe NumberValue(18.0)
    }

    "exponentiation: square root: 4 ** 0.5 = 2" {
        val result = engine().execute("4 ** 0.5")
        result shouldBe NumberValue(2.0)
    }

    // =============================================================
    // Feature 4: Variable assignment (x = expr)
    // =============================================================

    "assignment: simple variable reassignment" {
        val result = engine().execute(
            """
            let x = 10
            x = 20
            x
            """.trimIndent()
        )
        result shouldBe NumberValue(20.0)
    }

    "assignment: returns the assigned value" {
        val result = engine().execute(
            """
            let x = 0
            x = 42
            """.trimIndent()
        )
        result shouldBe NumberValue(42.0)
    }

    "assignment: chained assignment (right-associative)" {
        val result = engine().execute(
            """
            let a = 0
            let b = 0
            a = b = 5
            a
            """.trimIndent()
        )
        result shouldBe NumberValue(5.0)
    }

    "assignment: object property assignment" {
        val result = engine().execute(
            """
            let obj = { x: 1, y: 2 }
            obj.x = 99
            obj.x
            """.trimIndent()
        )
        result shouldBe NumberValue(99.0)
    }

    "assignment: array element assignment" {
        val result = engine().execute(
            """
            let arr = [1, 2, 3]
            arr[1] = 99
            arr[1]
            """.trimIndent()
        )
        result shouldBe NumberValue(99.0)
    }

    "assignment: object property via string key" {
        val result = engine().execute(
            """
            let obj = { name: "Alice" }
            obj["name"] = "Bob"
            obj["name"]
            """.trimIndent()
        )
        result shouldBe StringValue("Bob")
    }

    // =============================================================
    // Feature 5: Compound assignment (+=, -=, *=, /=, %=)
    // =============================================================

    "compound assignment: +=" {
        val result = engine().execute(
            """
            let x = 10
            x += 5
            x
            """.trimIndent()
        )
        result shouldBe NumberValue(15.0)
    }

    "compound assignment: -=" {
        val result = engine().execute(
            """
            let x = 10
            x -= 3
            x
            """.trimIndent()
        )
        result shouldBe NumberValue(7.0)
    }

    "compound assignment: *=" {
        val result = engine().execute(
            """
            let x = 4
            x *= 3
            x
            """.trimIndent()
        )
        result shouldBe NumberValue(12.0)
    }

    "compound assignment: /=" {
        val result = engine().execute(
            """
            let x = 20
            x /= 4
            x
            """.trimIndent()
        )
        result shouldBe NumberValue(5.0)
    }

    "compound assignment: %=" {
        val result = engine().execute(
            """
            let x = 17
            x %= 5
            x
            """.trimIndent()
        )
        result shouldBe NumberValue(2.0)
    }

    "compound assignment: compound in loop accumulation" {
        val result = engine().execute(
            """
            let sum = 0
            sum += 10
            sum += 20
            sum += 12
            sum
            """.trimIndent()
        )
        result shouldBe NumberValue(42.0)
    }

    // =============================================================
    // Feature 6: Prefix increment/decrement (++x, --x)
    // =============================================================

    "prefix increment: ++x returns new value" {
        val result = engine().execute(
            """
            let x = 5
            ++x
            """.trimIndent()
        )
        result shouldBe NumberValue(6.0)
    }

    "prefix increment: variable is updated" {
        val result = engine().execute(
            """
            let x = 5
            ++x
            x
            """.trimIndent()
        )
        result shouldBe NumberValue(6.0)
    }

    "prefix decrement: --x returns new value" {
        val result = engine().execute(
            """
            let x = 5
            --x
            """.trimIndent()
        )
        result shouldBe NumberValue(4.0)
    }

    "prefix decrement: variable is updated" {
        val result = engine().execute(
            """
            let x = 5
            --x
            x
            """.trimIndent()
        )
        result shouldBe NumberValue(4.0)
    }

    // =============================================================
    // Feature 7: Postfix increment/decrement (x++, x--)
    // =============================================================

    "postfix increment: x++ returns original value" {
        val result = engine().execute(
            """
            let x = 5
            x++
            """.trimIndent()
        )
        result shouldBe NumberValue(5.0)
    }

    "postfix increment: variable is updated after" {
        val result = engine().execute(
            """
            let x = 5
            x++
            x
            """.trimIndent()
        )
        result shouldBe NumberValue(6.0)
    }

    "postfix decrement: x-- returns original value" {
        val result = engine().execute(
            """
            let x = 5
            x--
            """.trimIndent()
        )
        result shouldBe NumberValue(5.0)
    }

    "postfix decrement: variable is updated after" {
        val result = engine().execute(
            """
            let x = 5
            x--
            x
            """.trimIndent()
        )
        result shouldBe NumberValue(4.0)
    }

    "postfix vs prefix: difference in expression value" {
        // prefix returns new, postfix returns old
        val resultPrefix = engine().execute(
            """
            let x = 10
            let a = ++x
            a
            """.trimIndent()
        )
        val resultPostfix = engine().execute(
            """
            let x = 10
            let a = x++
            a
            """.trimIndent()
        )
        resultPrefix shouldBe NumberValue(11.0)
        resultPostfix shouldBe NumberValue(10.0)
    }

    // =============================================================
    // Feature 8: Ternary operator (cond ? then : else)
    // =============================================================

    "ternary: true condition returns then branch" {
        val result = engine().execute("true ? 1 : 2")
        result shouldBe NumberValue(1.0)
    }

    "ternary: false condition returns else branch" {
        val result = engine().execute("false ? 1 : 2")
        result shouldBe NumberValue(2.0)
    }

    "ternary: number condition truthy" {
        val result = engine().execute("42 ? 100 : 200")
        result shouldBe NumberValue(100.0)
    }

    "ternary: zero is falsy" {
        val result = engine().execute("0 ? 100 : 200")
        result shouldBe NumberValue(200.0)
    }

    "ternary: null is falsy" {
        val result = engine().execute("null ? 100 : 200")
        result shouldBe NumberValue(200.0)
    }

    "ternary: string condition truthy" {
        val result = engine().execute(""""hello" ? 1 : 2""")
        result shouldBe NumberValue(1.0)
    }

    "ternary: empty string is falsy" {
        val result = engine().execute(""""" ? 1 : 2""")
        result shouldBe NumberValue(2.0)
    }

    "ternary: nested ternary (right-associative)" {
        val result = engine().execute("true ? false ? 1 : 2 : 3")
        result shouldBe NumberValue(2.0)
    }

    "ternary: with expression condition" {
        val result = engine().execute(
            """
            let x = 10
            x > 5 ? "big" : "small"
            """.trimIndent()
        )
        result shouldBe StringValue("big")
    }

    "ternary: used in variable initialization" {
        val result = engine().execute(
            """
            let max = (10 > 5) ? 10 : 5
            max
            """.trimIndent()
        )
        result shouldBe NumberValue(10.0)
    }

    // =============================================================
    // Feature 9: Array/object index access (arr[i], obj["key"])
    // =============================================================

    "index access: array read first element" {
        val result = engine().execute(
            """
            let arr = [10, 20, 30]
            arr[0]
            """.trimIndent()
        )
        result shouldBe NumberValue(10.0)
    }

    "index access: array read last element" {
        val result = engine().execute(
            """
            let arr = [10, 20, 30]
            arr[2]
            """.trimIndent()
        )
        result shouldBe NumberValue(30.0)
    }

    "index access: array out of bounds returns null" {
        val result = engine().execute(
            """
            let arr = [1, 2, 3]
            arr[10]
            """.trimIndent()
        )
        result shouldBe NullValue
    }

    "index access: object by string key" {
        val result = engine().execute(
            """
            let obj = { name: "Alice", age: 30 }
            obj["name"]
            """.trimIndent()
        )
        result shouldBe StringValue("Alice")
    }

    "index access: object missing key returns null" {
        val result = engine().execute(
            """
            let obj = { name: "Alice" }
            obj["missing"]
            """.trimIndent()
        )
        result shouldBe NullValue
    }

    "index access: dynamic key with variable" {
        val result = engine().execute(
            """
            let obj = { a: 1, b: 2, c: 3 }
            let key = "b"
            obj[key]
            """.trimIndent()
        )
        result shouldBe NumberValue(2.0)
    }

    "index access: array with variable index" {
        val result = engine().execute(
            """
            let arr = [100, 200, 300]
            let i = 1
            arr[i]
            """.trimIndent()
        )
        result shouldBe NumberValue(200.0)
    }

    "index access: chained array access (nested arrays)" {
        val result = engine().execute(
            """
            let matrix = [[1, 2], [3, 4]]
            matrix[1][0]
            """.trimIndent()
        )
        result shouldBe NumberValue(3.0)
    }

    "index access: write then read array element" {
        val result = engine().execute(
            """
            let arr = [1, 2, 3]
            arr[0] = 99
            arr[0]
            """.trimIndent()
        )
        result shouldBe NumberValue(99.0)
    }

    "index access: write then read object by key" {
        val result = engine().execute(
            """
            let obj = { x: 1 }
            obj["x"] = 42
            obj["x"]
            """.trimIndent()
        )
        result shouldBe NumberValue(42.0)
    }

    // =============================================================
    // Feature 10: Object property shorthand ({ name, age })
    // =============================================================

    "object shorthand: single property" {
        val result = engine().execute(
            """
            let name = "Alice"
            let obj = { name }
            obj.name
            """.trimIndent()
        )
        result shouldBe StringValue("Alice")
    }

    "object shorthand: multiple properties" {
        val result = engine().execute(
            """
            let x = 10
            let y = 20
            let point = { x, y }
            point.x
            """.trimIndent()
        )
        result shouldBe NumberValue(10.0)
    }

    "object shorthand: y from shorthand" {
        val result = engine().execute(
            """
            let x = 10
            let y = 20
            let point = { x, y }
            point.y
            """.trimIndent()
        )
        result shouldBe NumberValue(20.0)
    }

    "object shorthand: mixed with regular property" {
        val result = engine().execute(
            """
            let name = "Bob"
            let person = { name, age: 30 }
            person.age
            """.trimIndent()
        )
        result shouldBe NumberValue(30.0)
    }

    "object shorthand: mixed with regular property - shorthand value" {
        val result = engine().execute(
            """
            let name = "Bob"
            let person = { name, age: 30 }
            person.name
            """.trimIndent()
        )
        result shouldBe StringValue("Bob")
    }

    "object shorthand: empty object still works" {
        val result = engine().execute("{}")
        result shouldBe ObjectValue(mutableMapOf())
    }

    // =============================================================
    // Feature 11: `in` operator ("key" in obj)
    // =============================================================

    "in operator: key exists returns true" {
        val result = engine().execute(
            """
            let obj = { name: "Alice", age: 30 }
            "name" in obj
            """.trimIndent()
        )
        result shouldBe BooleanValue(true)
    }

    "in operator: key does not exist returns false" {
        val result = engine().execute(
            """
            let obj = { name: "Alice" }
            "missing" in obj
            """.trimIndent()
        )
        result shouldBe BooleanValue(false)
    }

    "in operator: dynamic key check" {
        val result = engine().execute(
            """
            let obj = { a: 1, b: 2 }
            let key = "a"
            key in obj
            """.trimIndent()
        )
        result shouldBe BooleanValue(true)
    }

    "in operator: used in ternary" {
        val result = engine().execute(
            """
            let obj = { x: 10 }
            let result = "x" in obj ? "found" : "not found"
            result
            """.trimIndent()
        )
        result shouldBe StringValue("found")
    }

    "in operator: after property deletion via reassignment" {
        // Can't delete, but after setting a key it is there
        val result = engine().execute(
            """
            let obj = {}
            obj["key"] = 42
            "key" in obj
            """.trimIndent()
        )
        result shouldBe BooleanValue(true)
    }

    // =============================================================
    // Integration tests: Multiple features combined
    // =============================================================

    "integration: counter with postfix increment and ternary" {
        val result = engine().execute(
            """
            let count = 0
            count++
            count++
            count++
            count > 2 ? "many" : "few"
            """.trimIndent()
        )
        result shouldBe StringValue("many")
    }

    "integration: fibonacci using assignment" {
        val result = engine().execute(
            """
            let a = 0
            let b = 1
            let tmp = 0
            tmp = a + b
            a = b
            b = tmp
            tmp = a + b
            a = b
            b = tmp
            tmp = a + b
            a = b
            b = tmp
            b
            """.trimIndent()
        )
        // Step 1: tmp=0+1=1, a=1, b=1
        // Step 2: tmp=1+1=2, a=1, b=2
        // Step 3: tmp=1+2=3, a=2, b=3
        result shouldBe NumberValue(3.0)
    }

    "integration: array sum using compound assignment" {
        val result = engine().execute(
            """
            let arr = [10, 20, 12]
            let sum = 0
            sum += arr[0]
            sum += arr[1]
            sum += arr[2]
            sum
            """.trimIndent()
        )
        result shouldBe NumberValue(42.0)
    }

    "integration: scientific notation in expression" {
        val result = engine().execute("2e2 ** 2")
        result shouldBe NumberValue(40000.0)
    }

    "integration: strict equals in ternary" {
        val result = engine().execute(
            """
            let x = 42
            x === 42 ? "exact" : "not exact"
            """.trimIndent()
        )
        result shouldBe StringValue("exact")
    }

    "integration: object shorthand + in operator" {
        val result = engine().execute(
            """
            let name = "Alice"
            let age = 30
            let person = { name, age }
            "name" in person
            """.trimIndent()
        )
        result shouldBe BooleanValue(true)
    }
})

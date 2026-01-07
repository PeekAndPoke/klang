package io.peekandpoke.klang.script

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.peekandpoke.klang.script.runtime.NumberValue
import io.peekandpoke.klang.script.runtime.ObjectValue
import io.peekandpoke.klang.script.runtime.StringValue

/**
 * Tests for object literal expressions
 */
class ObjectLiteralTest : StringSpec({

    "should create empty object" {
        val script = klangScript()

        val result = script.execute("{}")
        result.shouldBeInstanceOf<ObjectValue>()
    }

    "should create object with single property" {
        val script = klangScript()

        val result = script.execute("{ x: 10 }")
        result.shouldBeInstanceOf<ObjectValue>()

        val xValue = result.getProperty("x")
        xValue.shouldBeInstanceOf<NumberValue>()
        xValue.value shouldBe 10.0
    }

    "should create object with multiple properties" {
        val script = klangScript()

        val result = script.execute("{ x: 10, y: 20, z: 30 }")
        result.shouldBeInstanceOf<ObjectValue>()

        (result.getProperty("x") as NumberValue).value shouldBe 10.0
        (result.getProperty("y") as NumberValue).value shouldBe 20.0
        (result.getProperty("z") as NumberValue).value shouldBe 30.0
    }

    "should create object with string keys" {
        val script = klangScript()

        val result = script.execute("""{ "first-name": "Alice", "last-name": "Smith" }""")
        result.shouldBeInstanceOf<ObjectValue>()

        (result.getProperty("first-name") as StringValue).value shouldBe "Alice"
        (result.getProperty("last-name") as StringValue).value shouldBe "Smith"
    }

    "should create object with mixed value types" {
        val script = klangScript()

        val result = script.execute("""{ name: "Bob", age: 25, score: 98.5 }""")
        result.shouldBeInstanceOf<ObjectValue>()

        (result.getProperty("name") as StringValue).value shouldBe "Bob"
        (result.getProperty("age") as NumberValue).value shouldBe 25.0
        (result.getProperty("score") as NumberValue).value shouldBe 98.5
    }

    "should create object with expression values" {
        val script = klangScript()

        val result = script.execute("{ sum: 1 + 2, product: 3 * 4 }")
        result.shouldBeInstanceOf<ObjectValue>()

        (result.getProperty("sum") as NumberValue).value shouldBe 3.0
        (result.getProperty("product") as NumberValue).value shouldBe 12.0
    }

    "should create nested objects" {
        val script = klangScript()

        val result = script.execute("{ outer: { inner: 42 } }")
        result.shouldBeInstanceOf<ObjectValue>()

        val outer = result.getProperty("outer")
        outer.shouldBeInstanceOf<ObjectValue>()

        val innerValue = outer.getProperty("inner")
        (innerValue as NumberValue).value shouldBe 42.0
    }

    "should create object with variable values" {
        val script = klangScript()

        script.execute(
            """
                let x = 100
                let y = 200
            """
        )

        val result = script.execute("{ a: x, b: y }")
        result.shouldBeInstanceOf<ObjectValue>()

        (result.getProperty("a") as NumberValue).value shouldBe 100.0
        (result.getProperty("b") as NumberValue).value shouldBe 200.0
    }

    "should create object with function call values" {
        val script = klangScript {
            registerFunction("getValue") { NumberValue(99.0) }
        }

        val result = script.execute("{ result: getValue() }")
        result.shouldBeInstanceOf<ObjectValue>()

        (result.getProperty("result") as NumberValue).value shouldBe 99.0
    }

    "should access object properties" {
        val script = klangScript()

        script.execute("let obj = { x: 10, y: 20 }")

        val resultX = script.execute("obj.x")
        (resultX as NumberValue).value shouldBe 10.0

        val resultY = script.execute("obj.y")
        (resultY as NumberValue).value shouldBe 20.0
    }

    "should create object with arrow function property" {
        val script = klangScript()

        val result = script.execute("{ double: x => x * 2 }")
        result.shouldBeInstanceOf<ObjectValue>()

        val doubleFunc = result.getProperty("double")
        doubleFunc.shouldBeInstanceOf<io.peekandpoke.klang.script.runtime.FunctionValue>()
    }

    "should use object in arrow function" {
        val script = klangScript()

        val result = script.execute("(x => ({ value: x, doubled: x * 2 }))(5)")
        result.shouldBeInstanceOf<ObjectValue>()

        (result.getProperty("value") as NumberValue).value shouldBe 5.0
        (result.getProperty("doubled") as NumberValue).value shouldBe 10.0
    }

    "should create object with multiple nested objects" {
        val script = klangScript()

        val result = script.execute("{ a: { x: 1 }, b: { y: 2 } }")
        result.shouldBeInstanceOf<ObjectValue>()

        val a = result.getProperty("a") as ObjectValue
        val b = result.getProperty("b") as ObjectValue

        (a.getProperty("x") as NumberValue).value shouldBe 1.0
        (b.getProperty("y") as NumberValue).value shouldBe 2.0
    }
})

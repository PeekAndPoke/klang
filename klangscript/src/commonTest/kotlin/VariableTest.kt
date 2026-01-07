package io.peekandpoke.klang.script

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.peekandpoke.klang.script.runtime.NullValue
import io.peekandpoke.klang.script.runtime.NumberValue
import io.peekandpoke.klang.script.runtime.StringValue

/**
 * Tests for variable declarations (let and const)
 */
class VariableTest : StringSpec({

    "should declare let variable with initializer" {
        val script = klangScript()

        script.execute(
            """
                let x = 42
            """
        )

        val result = script.execute("x")
        result.shouldBeInstanceOf<NumberValue>()
        result.value shouldBe 42.0
    }

    "should declare let variable without initializer (defaults to null)" {
        val script = klangScript()

        script.execute("let x")

        val result = script.execute("x")
        result shouldBe NullValue
    }

    "should declare const variable with initializer" {
        val script = klangScript()

        script.execute("const MAX = 100")

        val result = script.execute("MAX")
        result.shouldBeInstanceOf<NumberValue>()
        result.value shouldBe 100.0
    }

    "should use let variable in expressions" {
        val script = klangScript()

        script.execute(
            """
                let x = 10
                let y = 20
            """
        )

        val result = script.execute("x + y")
        result.shouldBeInstanceOf<NumberValue>()
        result.value shouldBe 30.0
    }

    "should use const variable in expressions" {
        val script = klangScript()

        script.execute(
            """
                const PI = 3.14159
                const radius = 5
            """
        )

        val result = script.execute("PI * radius * radius")
        result.shouldBeInstanceOf<NumberValue>()
        result.value shouldBe 78.53975
    }

    "should declare variable with expression as initializer" {
        val script = klangScript()

        script.execute("let result = 2 + 3 * 4")

        val result = script.execute("result")
        result.shouldBeInstanceOf<NumberValue>()
        result.value shouldBe 14.0  // 2 + (3 * 4)
    }

    "should declare variable with function call as initializer" {
        val script = klangScript {
            registerFunctionRaw("getValue") { NumberValue(99.0) }
        }

        script.execute("let x = getValue()")

        val result = script.execute("x")
        result.shouldBeInstanceOf<NumberValue>()
        result.value shouldBe 99.0
    }

    "should declare string variable" {
        val script = klangScript()

        script.execute("let greeting = \"Hello, World!\"")

        val result = script.execute("greeting")
        result.shouldBeInstanceOf<StringValue>()
        result.value shouldBe "Hello, World!"
    }

    "should support multiple variable declarations" {
        val script = klangScript()

        script.execute(
            """
                let a = 1
                let b = 2
                let c = 3
                const d = 4
            """
        )

        val resultA = script.execute("a")
        val resultB = script.execute("b")
        val resultC = script.execute("c")
        val resultD = script.execute("d")

        (resultA as NumberValue).value shouldBe 1.0
        (resultB as NumberValue).value shouldBe 2.0
        (resultC as NumberValue).value shouldBe 3.0
        (resultD as NumberValue).value shouldBe 4.0
    }

    "should use variable in arrow function" {
        val script = klangScript()

        script.execute("let multiplier = 10")

        val result = script.execute("(x => x * multiplier)(5)")
        result.shouldBeInstanceOf<NumberValue>()
        result.value shouldBe 50.0
    }

    "should capture variable in closure" {
        val script = klangScript()

        script.execute(
            """
                let x = 100
                const makeAdder = y => x + y
            """
        )

        val result = script.execute("makeAdder(23)")
        result.shouldBeInstanceOf<NumberValue>()
        result.value shouldBe 123.0
    }

    "should pass variable to native function" {
        val script = klangScript {
            registerFunctionRaw("double") { args ->
                val num = (args[0] as NumberValue).value
                NumberValue(num * 2)
            }
        }

        script.execute("let value = 21")

        val result = script.execute("double(value)")
        result.shouldBeInstanceOf<NumberValue>()
        result.value shouldBe 42.0
    }
})

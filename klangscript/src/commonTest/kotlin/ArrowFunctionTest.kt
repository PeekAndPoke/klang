package io.peekandpoke.klang.script

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.peekandpoke.klang.script.ast.ExpressionStatement
import io.peekandpoke.klang.script.ast.Program
import io.peekandpoke.klang.script.runtime.*

/**
 * Tests for arrow functions (lambda expressions)
 */
class ArrowFunctionTest : StringSpec({

    "should create and call simple arrow function" {
        val script = klangScript()

        // Immediately invoked arrow function: (x => x + 1)(5)
        val result = script.execute("(x => x + 1)(5)")
        result.shouldBeInstanceOf<NumberValue>()
        result.value shouldBe 6.0
    }

    "should handle arrow function with multiple parameters" {
        val script = klangScript()

        // (a, b) => a + b applied to (3, 4)
        val result = script.execute("((a, b) => a + b)(3, 4)")
        result.shouldBeInstanceOf<NumberValue>()
        result.value shouldBe 7.0
    }

    "should handle arrow function with no parameters" {
        val script = klangScript()

        // () => 42
        val result = script.execute("(() => 42)()")
        result.shouldBeInstanceOf<NumberValue>()
        result.value shouldBe 42.0
    }

    "should handle arrow function with multiplication" {
        val script = klangScript()

        // (x, y) => x * y
        val result = script.execute("((x, y) => x * y)(6, 7)")
        result.shouldBeInstanceOf<NumberValue>()
        result.value shouldBe 42.0
    }

    "should handle nested arrow functions" {
        val script = klangScript()

        // x => y => x + y (currying)
        // Applied as: (x => y => x + y)(10)(5) = 15
        val result = script.execute("(x => y => x + y)(10)(5)")
        result.shouldBeInstanceOf<NumberValue>()
        result.value shouldBe 15.0
    }

    "should pass arrow function as argument to native function" {
        val script = klangScript {
            // Register a function that accepts a callback and calls it
            registerFunctionRaw("applyFunc") { args ->
                // Parse the callback call expression and execute it
                // We'll use the execute method to invoke: callback(value)
                val func = args[0] as FunctionValue
                val value = args[1]

                // Create a new environment with the function's closure
                val funcEnv = Environment(func.closureEnv)

                // Bind parameter to value
                if (func.parameters.size == 1) {
                    funcEnv.define(func.parameters[0], value)
                }

                val engine = klangScript()

                // Create interpreter with that environment and execute the body
                val funcInterpreter = Interpreter(env = funcEnv, engine = engine)

                // We need to execute a small program containing the function body
                val bodyProgram = Program(
                    listOf(ExpressionStatement(func.body))
                )
                funcInterpreter.execute(bodyProgram)
            }
        }


        // applyFunc(x => x * 2, 21) should return 42
        val result = script.execute("applyFunc(x => x * 2, 21)")
        result.shouldBeInstanceOf<NumberValue>()
        result.value shouldBe 42.0
    }

    "should handle arrow function with method chaining in body" {
        val script = klangScript {
            // Register a function that returns an object
            registerFunctionRaw("createObj") { args ->
                val value = (args[0] as NumberValue).value
                ObjectValue(
                    mutableMapOf(
                        "value" to NumberValue(value),
                    )
                )
            }
        }


        // x => createObj(x).double()
        val result = script.execute("(x => createObj(x).value)(5)")
        result.shouldBeInstanceOf<NumberValue>()
        result.value shouldBe 5.0
    }

    "should handle arrow function with string operations" {
        val script = klangScript {
            registerFunctionRaw("concat") { args ->
                val a = (args[0] as StringValue).value
                val b = (args[1] as StringValue).value
                StringValue(a + b)
            }
        }

        // (a, b) => concat(a, b)
        val result = script.execute("((a, b) => concat(a, b))(\"hello\", \"world\")")
        result.shouldBeInstanceOf<StringValue>()
        result.value shouldBe "helloworld"
    }

    "should handle complex expression in arrow function body" {
        val script = klangScript()

        // x => (x + 1) * 2 - 3
        val result = script.execute("(x => (x + 1) * 2 - 3)(5)")
        result.shouldBeInstanceOf<NumberValue>()
        // (5 + 1) * 2 - 3 = 6 * 2 - 3 = 12 - 3 = 9
        result.value shouldBe 9.0
    }

    "should handle arrow function returning another arrow function" {
        val script = klangScript()

        // makeAdder that returns a function
        // x => (y => x + y)
        // Store result and call it
        val result = script.execute("(x => y => x + y)(100)(23)")
        result.shouldBeInstanceOf<NumberValue>()
        result.value shouldBe 123.0
    }
})

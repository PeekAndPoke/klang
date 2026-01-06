package io.peekandpoke.klang.script

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.script.runtime.NumberValue
import io.peekandpoke.klang.script.runtime.StringValue

class KlangScriptIntegrationTest : StringSpec({

    "should execute a simple print statement" {
        val engine = KlangScript()
        val output = mutableListOf<String>()

        // Register a print function
        engine.registerFunction1("print") { value ->
            output.add(value.toDisplayString())
            value
        }

        // Execute script
        engine.execute("""print("hello")""")

        // Verify
        output shouldBe listOf("hello")
    }

    "should execute multiple statements" {
        val engine = KlangScript()
        val output = mutableListOf<String>()

        engine.registerFunction1("print") { value ->
            output.add(value.toDisplayString())
            value
        }

        // Execute script with multiple lines
        engine.execute(
            """
            print("first")
            print("second")
        """.trimIndent()
        )

        output shouldBe listOf("first", "second")
    }

    "should handle numeric values" {
        val engine = KlangScript()
        var receivedValue: Double? = null

        engine.registerFunction1("check") { value ->
            receivedValue = (value as NumberValue).value
            value
        }

        engine.execute("""check(42)""")

        receivedValue shouldBe 42.0
    }

    "should call functions with multiple arguments" {
        val engine = KlangScript()
        var sum = 0.0

        engine.registerFunction("add") { args ->
            sum = args.sumOf { (it as NumberValue).value }
            NumberValue(sum)
        }

        engine.execute("""add(1, 2, 3)""")

        sum shouldBe 6.0
    }

    "should handle nested function calls" {
        val engine = KlangScript()
        val output = mutableListOf<String>()

        engine.registerFunction1("print") { value ->
            output.add(value.toDisplayString())
            value
        }

        engine.registerFunction1("upper") { value ->
            StringValue((value as StringValue).value.uppercase())
        }

        engine.execute("""print(upper("hello"))""")

        output shouldBe listOf("HELLO")
    }
})

package io.peekandpoke.klang.script

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.script.runtime.NumberValue
import io.peekandpoke.klang.script.runtime.StringValue

class KlangScriptIntegrationTest : StringSpec({

    "should execute a simple print statement" {
        val output = mutableListOf<String>()

        // Register a print function
        val engine = KlangScript.builder()
            .registerFunction1("print") { value ->
                output.add(value.toDisplayString())
                value
            }
            .build()

        // Execute script
        engine.execute("""print("hello")""")

        // Verify
        output shouldBe listOf("hello")
    }

    "should execute multiple statements" {
        val output = mutableListOf<String>()

        val engine = KlangScript.builder()
            .registerFunction1("print") { value ->
                output.add(value.toDisplayString())
                value
            }
            .build()

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
        var receivedValue: Double? = null

        val engine = KlangScript.builder()
            .registerFunction1("check") { value ->
                receivedValue = (value as NumberValue).value
                value
            }
            .build()

        engine.execute("""check(42)""")

        receivedValue shouldBe 42.0
    }

    "should call functions with multiple arguments" {
        var sum = 0.0

        val engine = KlangScript.builder()
            .registerFunction("add") { args ->
                sum = args.sumOf { (it as NumberValue).value }
                NumberValue(sum)
            }
            .build()

        engine.execute("""add(1, 2, 3)""")

        sum shouldBe 6.0
    }

    "should handle nested function calls" {
        val output = mutableListOf<String>()

        val engine = KlangScript.builder()
            .registerFunction1("print") { value ->
                output.add(value.toDisplayString())
                value
            }
            .registerFunction1("upper") { value ->
                StringValue((value as StringValue).value.uppercase())
            }
            .build()

        engine.execute("""print(upper("hello"))""")

        output shouldBe listOf("HELLO")
    }
})

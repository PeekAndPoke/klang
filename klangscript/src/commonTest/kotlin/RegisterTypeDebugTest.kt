package io.peekandpoke.klang.script

import io.kotest.core.spec.style.StringSpec
import io.peekandpoke.klang.script.builder.registerFunction
import io.peekandpoke.klang.script.builder.registerType

class RegisterTypeDebugTest : StringSpec({

    class TestPattern(val value: Double) {
        fun double(): TestPattern = TestPattern(value * 2)
    }

    "Check what registerType returns" {
        val engine = klangScript {
            registerFunction<Double, TestPattern>("create") { value ->
                TestPattern(value)
            }
            registerType<TestPattern> {
                registerMethod("double") { double() }
            }
        }

        val result = engine.execute("create(5.0).double()")

        println("Result type: ${result::class.simpleName}")
        println("Result value: $result")
        println("Result display: ${result.toDisplayString()}")
    }
})

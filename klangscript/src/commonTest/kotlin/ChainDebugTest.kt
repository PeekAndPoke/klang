package io.peekandpoke.klang.script

import io.kotest.core.spec.style.StringSpec
import io.peekandpoke.klang.script.builder.registerFunction
import io.peekandpoke.klang.script.builder.registerType

class ChainDebugTest : StringSpec({

    class ContinuousPattern(val value: Double) {
        fun fromBipolar(): ContinuousPattern {
            println("fromBipolar: $value -> ${(value + 1.0) / 2.0}")
            return ContinuousPattern((value + 1.0) / 2.0)
        }

        fun range(min: Double, max: Double): ContinuousPattern {
            val result = min + value * (max - min)
            println("range($min, $max): $value -> $result")
            return ContinuousPattern(result)
        }
    }

    "Debug chain calculation" {
        val engine = klangScript {
            registerFunction<Double, ContinuousPattern>("sine2") { value ->
                println("sine2($value)")
                ContinuousPattern(value)
            }
            registerType<ContinuousPattern> {
                registerMethod("fromBipolar") { fromBipolar() }
                registerMethod("range") { min: Double, max: Double -> range(min, max) }
                registerMethod("value") {
                    println("Getting value: $value")
                    value
                }
            }
        }

        val script = """sine2(0.5).fromBipolar().range(0.1, 0.9).value()"""

        val result = engine.execute(script)
        println("Final result: $result (type: ${result::class.simpleName})")
        println("Display: ${result.toDisplayString()}")
    }
})

package io.peekandpoke.klang.script

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.script.runtime.NumberValue
import io.peekandpoke.klang.script.runtime.ObjectValue

class MinimalChainTest : StringSpec({

    "Simplest possible chain: obj().prop" {
        val engine = klangScript {
            registerFunctionRaw("obj") { _, _ ->
                ObjectValue(
                    mutableMapOf(
                        "prop" to NumberValue(42.0)
                    )
                )
            }
        }

        try {
            val result = engine.execute("obj().prop")
            println("SUCCESS: $result")
            result.shouldBe(NumberValue(42.0))
        } catch (e: Exception) {
            println("FAILED: ${e.message}")
            e.printStackTrace()
            throw e
        }
    }

    "Pattern from bug: obj.method().prop" {
        val engine = klangScript()

        val obj = ObjectValue(
            mutableMapOf(
            "method" to engine.createNativeFunction("method") {
                ObjectValue(
                    mutableMapOf(
                        "prop" to NumberValue(42.0)
                    )
                )
            }
        ))
        engine.registerVariable("obj", obj)

        try {
            val result = engine.execute("obj.method().prop")
            println("SUCCESS: $result")
            result.shouldBe(NumberValue(42.0))
        } catch (e: Exception) {
            println("FAILED: ${e.message}")
            e.printStackTrace()
            throw e
        }
    }

    "Variable then chain: pattern.method().prop" {
        val engine = klangScript()

        val pattern = ObjectValue(
            mutableMapOf(
            "method" to engine.createNativeFunction("method") {
                ObjectValue(
                    mutableMapOf(
                        "prop" to NumberValue(99.0)
                    )
                )
            }
        ))
        engine.registerVariable("pattern", pattern)

        try {
            val result = engine.execute("pattern.method().prop")
            println("SUCCESS: $result")
            result.shouldBe(NumberValue(99.0))
        } catch (e: Exception) {
            println("FAILED: ${e.message}")
            e.printStackTrace()
            throw e
        }
    }
})

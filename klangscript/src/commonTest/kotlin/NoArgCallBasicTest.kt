package io.peekandpoke.klang.script

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.script.runtime.NumberValue
import io.peekandpoke.klang.script.runtime.ObjectValue
import io.peekandpoke.klang.script.runtime.StringValue

class NoArgCallBasicTest : StringSpec({

    "Basic no-arg function call" {
        val engine = klangScript {
            registerFunctionRaw("getValue") { _, _ ->
                NumberValue(42.0)
            }
        }

        val result = engine.execute("getValue()")
        result.shouldBe(NumberValue(42.0))
    }

    "Basic no-arg method call on object" {
        val engine = klangScript()

        // Create an object with a no-arg method
        val obj = ObjectValue(
            mutableMapOf(
                "getValue" to engine.createNativeFunction("getValue") {
                    NumberValue(42.0)
                }
            )
        )
        engine.registerVariable("obj", obj)

        val result = engine.execute("obj.getValue()")
        result.shouldBe(NumberValue(42.0))
    }

    "No-arg call without any member access" {
        val engine = klangScript {
            registerFunctionRaw("test") { _, _ ->
                StringValue("works")
            }
        }

        val result = engine.execute("test()")
        result.toDisplayString() shouldBe "works"
    }
})

package io.peekandpoke.klang.script

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.peekandpoke.klang.script.runtime.NullValue
import io.peekandpoke.klang.script.runtime.NumberValue
import io.peekandpoke.klang.script.runtime.ObjectValue
import io.peekandpoke.klang.script.runtime.StringValue

/**
 * Tests for member access (dot notation) and method chaining
 */
class MemberAccessTest : StringSpec({

    "should access object property" {
        val script = klangScript()
        val obj = ObjectValue(
            mutableMapOf(
                "name" to StringValue("Alice")
            )
        )
        script.registerVariable("person", obj)

        val result = script.execute("person.name")
        result.shouldBeInstanceOf<StringValue>()
        result.value shouldBe "Alice"
    }

    "should access nested properties" {
        val script = klangScript()
        val innerObj = ObjectValue(
            mutableMapOf(
                "city" to StringValue("Berlin")
            )
        )
        val outerObj = ObjectValue(
            mutableMapOf(
                "address" to innerObj
            )
        )
        script.registerVariable("person", outerObj)

        val result = script.execute("person.address.city")
        result.shouldBeInstanceOf<StringValue>()
        result.value shouldBe "Berlin"
    }

    "should return NullValue for missing property" {
        val script = klangScript()
        val obj = ObjectValue()
        script.registerVariable("obj", obj)

        val result = script.execute("obj.missing")
        result shouldBe NullValue
    }

    "should call method on object" {
        val script = klangScript()
        val obj = ObjectValue(
            mutableMapOf(
                "getValue" to script.createNativeFunction("getValue") {
                    NumberValue(42.0)
                }
            )
        )
        script.registerVariable("obj", obj)

        val result = script.execute("obj.getValue()")
        result.shouldBeInstanceOf<NumberValue>()
        result.value shouldBe 42.0
    }

    "should access property after function call" {
        val script = klangScript {
            registerFunction("getObject") {
                ObjectValue(
                    mutableMapOf(
                        "x" to NumberValue(100.0),
                        "y" to NumberValue(200.0)
                    )
                )
            }
        }

        val result = script.execute("getObject().x")
        result.shouldBeInstanceOf<NumberValue>()
        result.value shouldBe 100.0
    }

    "should support multiple chained property accesses" {
        val script = klangScript()

        val level3 = ObjectValue(mutableMapOf("value" to NumberValue(42.0)))
        val level2 = ObjectValue(mutableMapOf("c" to level3))
        val level1 = ObjectValue(mutableMapOf("b" to level2))
        script.registerVariable("a", level1)

        val result = script.execute("a.b.c.value")
        result.shouldBeInstanceOf<NumberValue>()
        result.value shouldBe 42.0
    }

    "should support method call in middle of chain" {
        val script = klangScript()

        val innerObj = ObjectValue(
            mutableMapOf(
                "name" to StringValue("test")
            )
        )
        val obj = ObjectValue(
            mutableMapOf(
                "getInner" to script.createNativeFunction("getInner") {
                    innerObj
                }
            )
        )
        script.registerVariable("obj", obj)

        val result = script.execute("obj.getInner().name")
        result.shouldBeInstanceOf<StringValue>()
        result.value shouldBe "test"
    }

    "should evaluate complex expression with member access and arithmetic" {
        val script = klangScript()

        val obj = ObjectValue(
            mutableMapOf(
                "x" to NumberValue(10.0),
                "y" to NumberValue(5.0)
            )
        )
        script.registerVariable("obj", obj)

        val result = script.execute("obj.x + obj.y * 2")
        result.shouldBeInstanceOf<NumberValue>()
        result.value shouldBe 20.0
    }
})

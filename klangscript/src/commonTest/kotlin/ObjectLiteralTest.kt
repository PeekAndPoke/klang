import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.peekandpoke.klang.script.KlangScript
import io.peekandpoke.klang.script.runtime.NumberValue
import io.peekandpoke.klang.script.runtime.ObjectValue
import io.peekandpoke.klang.script.runtime.StringValue

/**
 * Tests for object literal expressions
 */
class ObjectLiteralTest : StringSpec({

    "should create empty object" {
        val script = KlangScript()

        val result = script.execute("{}")
        result.shouldBeInstanceOf<ObjectValue>()
    }

    "should create object with single property" {
        val script = KlangScript()

        val result = script.execute("{ x: 10 }")
        result.shouldBeInstanceOf<ObjectValue>()

        val obj = result as ObjectValue
        val xValue = obj.getProperty("x")
        xValue.shouldBeInstanceOf<NumberValue>()
        (xValue as NumberValue).value shouldBe 10.0
    }

    "should create object with multiple properties" {
        val script = KlangScript()

        val result = script.execute("{ x: 10, y: 20, z: 30 }")
        result.shouldBeInstanceOf<ObjectValue>()

        val obj = result as ObjectValue
        (obj.getProperty("x") as NumberValue).value shouldBe 10.0
        (obj.getProperty("y") as NumberValue).value shouldBe 20.0
        (obj.getProperty("z") as NumberValue).value shouldBe 30.0
    }

    "should create object with string keys" {
        val script = KlangScript()

        val result = script.execute("""{ "first-name": "Alice", "last-name": "Smith" }""")
        result.shouldBeInstanceOf<ObjectValue>()

        val obj = result as ObjectValue
        (obj.getProperty("first-name") as StringValue).value shouldBe "Alice"
        (obj.getProperty("last-name") as StringValue).value shouldBe "Smith"
    }

    "should create object with mixed value types" {
        val script = KlangScript()

        val result = script.execute("""{ name: "Bob", age: 25, score: 98.5 }""")
        result.shouldBeInstanceOf<ObjectValue>()

        val obj = result as ObjectValue
        (obj.getProperty("name") as StringValue).value shouldBe "Bob"
        (obj.getProperty("age") as NumberValue).value shouldBe 25.0
        (obj.getProperty("score") as NumberValue).value shouldBe 98.5
    }

    "should create object with expression values" {
        val script = KlangScript()

        val result = script.execute("{ sum: 1 + 2, product: 3 * 4 }")
        result.shouldBeInstanceOf<ObjectValue>()

        val obj = result as ObjectValue
        (obj.getProperty("sum") as NumberValue).value shouldBe 3.0
        (obj.getProperty("product") as NumberValue).value shouldBe 12.0
    }

    "should create nested objects" {
        val script = KlangScript()

        val result = script.execute("{ outer: { inner: 42 } }")
        result.shouldBeInstanceOf<ObjectValue>()

        val obj = result as ObjectValue
        val outer = obj.getProperty("outer")
        outer.shouldBeInstanceOf<ObjectValue>()

        val innerValue = (outer as ObjectValue).getProperty("inner")
        (innerValue as NumberValue).value shouldBe 42.0
    }

    "should create object with variable values" {
        val script = KlangScript()

        script.execute(
            """
            let x = 100
            let y = 200
        """
        )

        val result = script.execute("{ a: x, b: y }")
        result.shouldBeInstanceOf<ObjectValue>()

        val obj = result as ObjectValue
        (obj.getProperty("a") as NumberValue).value shouldBe 100.0
        (obj.getProperty("b") as NumberValue).value shouldBe 200.0
    }

    "should create object with function call values" {
        val script = KlangScript()

        script.registerFunction("getValue") { NumberValue(99.0) }

        val result = script.execute("{ result: getValue() }")
        result.shouldBeInstanceOf<ObjectValue>()

        val obj = result as ObjectValue
        (obj.getProperty("result") as NumberValue).value shouldBe 99.0
    }

    "should access object properties" {
        val script = KlangScript()

        script.execute("let obj = { x: 10, y: 20 }")

        val resultX = script.execute("obj.x")
        (resultX as NumberValue).value shouldBe 10.0

        val resultY = script.execute("obj.y")
        (resultY as NumberValue).value shouldBe 20.0
    }

    "should create object with arrow function property" {
        val script = KlangScript()

        val result = script.execute("{ double: x => x * 2 }")
        result.shouldBeInstanceOf<ObjectValue>()

        val obj = result as ObjectValue
        val doubleFunc = obj.getProperty("double")
        doubleFunc.shouldBeInstanceOf<io.peekandpoke.klang.script.runtime.FunctionValue>()
    }

    "should use object in arrow function" {
        val script = KlangScript()

        val result = script.execute("(x => ({ value: x, doubled: x * 2 }))(5)")
        result.shouldBeInstanceOf<ObjectValue>()

        val obj = result as ObjectValue
        (obj.getProperty("value") as NumberValue).value shouldBe 5.0
        (obj.getProperty("doubled") as NumberValue).value shouldBe 10.0
    }

    "should create object with multiple nested objects" {
        val script = KlangScript()

        val result = script.execute("{ a: { x: 1 }, b: { y: 2 } }")
        result.shouldBeInstanceOf<ObjectValue>()

        val obj = result as ObjectValue
        val a = obj.getProperty("a") as ObjectValue
        val b = obj.getProperty("b") as ObjectValue

        (a.getProperty("x") as NumberValue).value shouldBe 1.0
        (b.getProperty("y") as NumberValue).value shouldBe 2.0
    }
})

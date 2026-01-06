import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.peekandpoke.klang.script.KlangScript
import io.peekandpoke.klang.script.runtime.*

/**
 * Tests for Step 1.4: Parser - Literals & Identifiers
 *
 * This test suite verifies:
 * - Boolean literals (true, false)
 * - Null literal
 * - Using literals in various contexts
 */
class LiteralsTest : StringSpec({

    // ============================================================
    // Boolean Literals
    // ============================================================

    "should parse true literal" {
        val script = KlangScript()

        val result = script.execute("true")

        result.shouldBeInstanceOf<BooleanValue>()
        result.value shouldBe true
    }

    "should parse false literal" {
        val script = KlangScript()

        val result = script.execute("false")

        result.shouldBeInstanceOf<BooleanValue>()
        result.value shouldBe false
    }

    "should use boolean in let declaration" {
        val script = KlangScript()

        script.execute("let flag = true")
        val result = script.execute("flag")

        result.shouldBeInstanceOf<BooleanValue>()
        result.value shouldBe true
    }

    "should use boolean in const declaration" {
        val script = KlangScript()

        script.execute("const enabled = false")
        val result = script.execute("enabled")

        result.shouldBeInstanceOf<BooleanValue>()
        result.value shouldBe false
    }

    "should use boolean in object literal" {
        val script = KlangScript()

        val result = script.execute("{ active: true, disabled: false }")

        result.shouldBeInstanceOf<ObjectValue>()
        val obj = result
        (obj.getProperty("active") as BooleanValue).value shouldBe true
        (obj.getProperty("disabled") as BooleanValue).value shouldBe false
    }

    // ============================================================
    // Null Literal
    // ============================================================

    "should parse null literal" {
        val script = KlangScript()

        val result = script.execute("null")

        result.shouldBeInstanceOf<NullValue>()
    }

    "should use null in let declaration" {
        val script = KlangScript()

        script.execute("let value = null")
        val result = script.execute("value")

        result.shouldBeInstanceOf<NullValue>()
    }

    "should use null in const declaration" {
        val script = KlangScript()

        script.execute("const empty = null")
        val result = script.execute("empty")

        result.shouldBeInstanceOf<NullValue>()
    }

    "should use null in object literal" {
        val script = KlangScript()

        val result = script.execute("{ value: null, name: null }")

        result.shouldBeInstanceOf<ObjectValue>()
        val obj = result
        obj.getProperty("value").shouldBeInstanceOf<NullValue>()
        obj.getProperty("name").shouldBeInstanceOf<NullValue>()
    }

    "should handle uninitialized let as null" {
        val script = KlangScript()

        script.execute("let x")
        val result = script.execute("x")

        result.shouldBeInstanceOf<NullValue>()
    }

    // ============================================================
    // Mixed Literals
    // ============================================================

    "should handle object with all literal types" {
        val script = KlangScript()

        val result = script.execute(
            """
            {
                number: 42,
                text: "hello",
                flag: true,
                empty: null
            }
        """.trimIndent()
        )

        result.shouldBeInstanceOf<ObjectValue>()

        (result.getProperty("number") as NumberValue).value shouldBe 42.0
        (result.getProperty("text") as StringValue).value shouldBe "hello"
        (result.getProperty("flag") as BooleanValue).value shouldBe true
        result.getProperty("empty").shouldBeInstanceOf<NullValue>()
    }
})

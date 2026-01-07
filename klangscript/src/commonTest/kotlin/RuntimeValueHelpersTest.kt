package io.peekandpoke.klang.script

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.script.ast.NumberLiteral
import io.peekandpoke.klang.script.runtime.*

/**
 * Tests for RuntimeValue helper extension functions
 *
 * Validates:
 * - Integer conversion: toIntOrNull(), toIntOr()
 * - Long conversion: toLongOrNull(), toLongOr()
 * - Double conversion: toDoubleOrNull(), toDoubleOr()
 * - String conversion: toStringOrNull(), toStringOr()
 * - Boolean conversion: toBooleanOrNull(), toBooleanOr()
 * - Generic object conversion: toObjectOrNull(), toObjectOr()
 */
class RuntimeValueHelpersTest : StringSpec({

    // ===== Integer conversion helpers =====

    "toIntOrNull() returns integer from NumberValue" {
        val value = NumberValue(42.0)
        value.toIntOrNull() shouldBe 42
    }

    "toIntOrNull() truncates decimal from NumberValue" {
        val value = NumberValue(42.7)
        value.toIntOrNull() shouldBe 42
    }

    "toIntOrNull() returns null for non-number" {
        val value = StringValue("hello")
        value.toIntOrNull() shouldBe null
    }

    "toIntOrNull() returns null for NullValue" {
        val value = NullValue
        value.toIntOrNull() shouldBe null
    }

    "toIntOr() returns integer from NumberValue" {
        val value = NumberValue(42.0)
        value.toIntOr(99) shouldBe 42
    }

    "toIntOr() returns default for non-number" {
        val value = StringValue("hello")
        value.toIntOr(99) shouldBe 99
    }

    "toIntOr() returns default for NullValue" {
        val value = NullValue
        value.toIntOr(99) shouldBe 99
    }

    // ===== Long conversion helpers =====

    "toLongOrNull() returns long from NumberValue" {
        val value = NumberValue(123456789.0)
        value.toLongOrNull() shouldBe 123456789L
    }

    "toLongOrNull() returns null for non-number" {
        val value = BooleanValue(true)
        value.toLongOrNull() shouldBe null
    }

    "toLongOr() returns long from NumberValue" {
        val value = NumberValue(987654321.0)
        value.toLongOr(0L) shouldBe 987654321L
    }

    "toLongOr() returns default for non-number" {
        val value = StringValue("text")
        value.toLongOr(42L) shouldBe 42L
    }

    // ===== Double conversion helpers =====

    "toDoubleOrNull() returns double from NumberValue" {
        val value = NumberValue(3.14159)
        value.toDoubleOrNull() shouldBe 3.14159
    }

    "toDoubleOrNull() returns null for non-number" {
        val value = StringValue("not a number")
        value.toDoubleOrNull() shouldBe null
    }

    "toDoubleOr() returns double from NumberValue" {
        val value = NumberValue(2.71828)
        value.toDoubleOr(0.0) shouldBe 2.71828
    }

    "toDoubleOr() returns default for non-number" {
        val value = BooleanValue(false)
        value.toDoubleOr(1.5) shouldBe 1.5
    }

    "toDoubleOr() returns default for NullValue" {
        val value = NullValue
        value.toDoubleOr(9.9) shouldBe 9.9
    }

    // ===== String conversion helpers =====

    "toStringOrNull() returns string from StringValue" {
        val value = StringValue("hello world")
        value.toStringOrNull() shouldBe "hello world"
    }

    "toStringOrNull() returns null for non-string" {
        val value = NumberValue(42.0)
        value.toStringOrNull() shouldBe null
    }

    "toStringOrNull() returns null for NullValue" {
        val value = NullValue
        value.toStringOrNull() shouldBe null
    }

    "toStringOr() returns string from StringValue" {
        val value = StringValue("test")
        value.toStringOr("default") shouldBe "test"
    }

    "toStringOr() returns default for non-string" {
        val value = NumberValue(123.0)
        value.toStringOr("fallback") shouldBe "fallback"
    }

    "toStringOr() returns default for NullValue" {
        val value = NullValue
        value.toStringOr("empty") shouldBe "empty"
    }

    // ===== Boolean conversion helpers =====

    "toBooleanOrNull() returns boolean from BooleanValue true" {
        val value = BooleanValue(true)
        value.toBooleanOrNull() shouldBe true
    }

    "toBooleanOrNull() returns boolean from BooleanValue false" {
        val value = BooleanValue(false)
        value.toBooleanOrNull() shouldBe false
    }

    "toBooleanOrNull() returns null for non-boolean" {
        val value = StringValue("true")
        value.toBooleanOrNull() shouldBe null
    }

    "toBooleanOrNull() returns null for NullValue" {
        val value = NullValue
        value.toBooleanOrNull() shouldBe null
    }

    "toBooleanOr() returns boolean from BooleanValue" {
        val value = BooleanValue(true)
        value.toBooleanOr(false) shouldBe true
    }

    "toBooleanOr() returns default for non-boolean" {
        val value = NumberValue(1.0)
        value.toBooleanOr(false) shouldBe false
    }

    "toBooleanOr() returns default for NullValue" {
        val value = NullValue
        value.toBooleanOr(true) shouldBe true
    }

    // ===== Generic object conversion helpers =====

    "toObjectOrNull() returns typed object from matching value" {
        data class TestData(val x: Int, val y: Int)

        val testData = TestData(1, 2)
        val value = NativeObjectValue.fromValue(testData)

        value.toObjectOrNull<TestData>() shouldBe testData
    }

    "toObjectOrNull() returns null for non-matching type" {
        val value = StringValue("not an object")
        value.toObjectOrNull<List<String>>() shouldBe null
    }

    "toObjectOrNull() returns null for NullValue" {
        val value = NullValue
        value.toObjectOrNull<String>() shouldBe null
    }

    "toObjectOr() returns typed object from matching value" {
        data class Point(val x: Double, val y: Double)

        val point = Point(3.0, 4.0)
        val value = NativeObjectValue.fromValue(point)
        val default = Point(0.0, 0.0)

        value.toObjectOr(default) shouldBe point
    }

    "toObjectOr() returns default for non-matching type" {
        val value = NumberValue(42.0)
        val default = "fallback"

        value.toObjectOr(default) shouldBe default
    }

    "toObjectOr() returns default for NullValue" {
        val value = NullValue
        val default = listOf("a", "b", "c")

        value.toObjectOr(default) shouldBe default
    }

    // ===== Edge cases =====

    "toIntOrNull() handles negative numbers" {
        val value = NumberValue(-42.0)
        value.toIntOrNull() shouldBe -42
    }

    "toIntOrNull() handles zero" {
        val value = NumberValue(0.0)
        value.toIntOrNull() shouldBe 0
    }

    "toDoubleOrNull() handles negative numbers" {
        val value = NumberValue(-3.14)
        value.toDoubleOrNull() shouldBe -3.14
    }

    "toDoubleOrNull() handles zero" {
        val value = NumberValue(0.0)
        value.toDoubleOrNull() shouldBe 0.0
    }

    "toStringOrNull() handles empty string" {
        val value = StringValue("")
        value.toStringOrNull() shouldBe ""
    }

    "toStringOrNull() handles string with special characters" {
        val value = StringValue("hello\nworld\t!")
        value.toStringOrNull() shouldBe "hello\nworld\t!"
    }

    // ===== Conversion between different RuntimeValue types =====

    "Number helpers return null for ArrayValue" {
        val value = ArrayValue(mutableListOf(NumberValue(1.0)))
        value.toIntOrNull() shouldBe null
        value.toLongOrNull() shouldBe null
        value.toDoubleOrNull() shouldBe null
    }

    "String helpers return null for ObjectValue" {
        val value = ObjectValue(mutableMapOf("key" to StringValue("value")))
        value.toStringOrNull() shouldBe null
    }

    "Boolean helpers return null for FunctionValue" {
        val value = FunctionValue(listOf("x"), NumberLiteral(1.0), Environment())
        value.toBooleanOrNull() shouldBe null
    }

    // ===== Large number handling =====

    "toIntOrNull() handles large numbers within Int range" {
        val value = NumberValue(2147483647.0) // Int.MAX_VALUE
        value.toIntOrNull() shouldBe 2147483647
    }

    "toLongOrNull() handles very large numbers" {
        val value = NumberValue(9007199254740991.0) // JavaScript MAX_SAFE_INTEGER
        value.toLongOrNull() shouldBe 9007199254740991L
    }

    // ===== Float conversion helpers =====

    "toFloatOrNull() returns float from NumberValue" {
        val value = NumberValue(3.14)
        value.toFloatOrNull() shouldBe 3.14f
    }

    "toFloatOrNull() returns null for non-number" {
        val value = StringValue("not a number")
        value.toFloatOrNull() shouldBe null
    }

    "toFloatOr() returns float from NumberValue" {
        val value = NumberValue(2.5)
        value.toFloatOr(0.0f) shouldBe 2.5f
    }

    "toFloatOr() returns default for non-number" {
        val value = BooleanValue(true)
        value.toFloatOr(1.5f) shouldBe 1.5f
    }

    // ===== Type checking helpers =====

    "isNumber() returns true for NumberValue" {
        val value = NumberValue(42.0)
        value.isNumber() shouldBe true
    }

    "isNumber() returns false for non-number" {
        StringValue("42").isNumber() shouldBe false
        BooleanValue(true).isNumber() shouldBe false
        NullValue.isNumber() shouldBe false
    }

    "isString() returns true for StringValue" {
        val value = StringValue("hello")
        value.isString() shouldBe true
    }

    "isString() returns false for non-string" {
        NumberValue(42.0).isString() shouldBe false
        BooleanValue(false).isString() shouldBe false
        NullValue.isString() shouldBe false
    }

    "isBoolean() returns true for BooleanValue" {
        BooleanValue(true).isBoolean() shouldBe true
        BooleanValue(false).isBoolean() shouldBe true
    }

    "isBoolean() returns false for non-boolean" {
        NumberValue(1.0).isBoolean() shouldBe false
        StringValue("true").isBoolean() shouldBe false
        NullValue.isBoolean() shouldBe false
    }

    "isNull() returns true for NullValue" {
        val value = NullValue
        value.isNull() shouldBe true
    }

    "isNull() returns false for non-null" {
        NumberValue(0.0).isNull() shouldBe false
        StringValue("").isNull() shouldBe false
        BooleanValue(false).isNull() shouldBe false
    }

    "isArray() returns true for ArrayValue" {
        val value = ArrayValue(mutableListOf(NumberValue(1.0), NumberValue(2.0)))
        value.isArray() shouldBe true
    }

    "isArray() returns false for non-array" {
        NumberValue(42.0).isArray() shouldBe false
        StringValue("[]").isArray() shouldBe false
        NullValue.isArray() shouldBe false
    }

    "isObject() returns true for ObjectValue" {
        val value = ObjectValue(mutableMapOf("key" to StringValue("value")))
        value.isObject() shouldBe true
    }

    "isObject() returns false for non-object" {
        NumberValue(42.0).isObject() shouldBe false
        ArrayValue(mutableListOf()).isObject() shouldBe false
        NullValue.isObject() shouldBe false
    }

    "isFunction() returns true for FunctionValue" {
        val value = FunctionValue(listOf("x"), NumberLiteral(1.0), Environment())
        value.isFunction() shouldBe true
    }

    "isFunction() returns true for NativeFunctionValue" {
        val value = NativeFunctionValue("test") { NullValue }
        value.isFunction() shouldBe true
    }

    "isFunction() returns true for BoundNativeMethod" {
        val receiver = NativeObjectValue.fromValue("test")
        val value = BoundNativeMethod("method", receiver) { NullValue }
        value.isFunction() shouldBe true
    }

    "isFunction() returns false for non-function" {
        NumberValue(42.0).isFunction() shouldBe false
        StringValue("function").isFunction() shouldBe false
        NullValue.isFunction() shouldBe false
    }
})

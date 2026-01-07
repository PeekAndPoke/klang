package io.peekandpoke.klang.script.stdlib

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.script.klangScript
import io.peekandpoke.klang.script.runtime.ArrayValue
import io.peekandpoke.klang.script.runtime.NumberValue
import io.peekandpoke.klang.script.runtime.StringValue

/**
 * Tests for Object utility methods
 *
 * Validates:
 * - Object.keys()
 * - Object.values()
 * - Object.entries()
 */
class StdLibObjectMethodsTest : StringSpec({

    "Object.keys() returns array of property names" {
        val engine = klangScript {
            registerLibrary(KlangStdLib.create())
        }

        val result = engine.execute(
            """
            import * from "stdlib"
            let obj = { a: 1, b: 2, c: 3 }
            Object.keys(obj)
            """.trimIndent()
        ) as ArrayValue

        result.elements.size shouldBe 3
        val keys = result.elements.map { (it as StringValue).value }.toSet()
        keys shouldBe setOf("a", "b", "c")
    }

    "Object.keys() on empty object returns empty array" {
        val engine = klangScript {
            registerLibrary(KlangStdLib.create())
        }

        val result = engine.execute(
            """
            import * from "stdlib"
            let obj = { }
            Object.keys(obj)
            """.trimIndent()
        ) as ArrayValue

        result.elements.size shouldBe 0
    }

    "Object.values() returns array of property values" {
        val engine = klangScript {
            registerLibrary(KlangStdLib.create())
        }

        val result = engine.execute(
            """
            import * from "stdlib"
            let obj = { a: 1, b: 2, c: 3 }
            Object.values(obj)
            """.trimIndent()
        ) as ArrayValue

        result.elements.size shouldBe 3
        val values = result.elements.map { (it as NumberValue).value }.toSet()
        values shouldBe setOf(1.0, 2.0, 3.0)
    }

    "Object.values() on empty object returns empty array" {
        val engine = klangScript {
            registerLibrary(KlangStdLib.create())
        }

        val result = engine.execute(
            """
            import * from "stdlib"
            let obj = { }
            Object.values(obj)
            """.trimIndent()
        ) as ArrayValue

        result.elements.size shouldBe 0
    }

    "Object.entries() returns array of [key, value] pairs" {
        val engine = klangScript {
            registerLibrary(KlangStdLib.create())
        }

        val result = engine.execute(
            """
            import * from "stdlib"
            let obj = { a: 1, b: 2 }
            Object.entries(obj)
            """.trimIndent()
        ) as ArrayValue

        result.elements.size shouldBe 2

        // Each entry should be an array of [key, value]
        val entries = result.elements.map { it as ArrayValue }
        entries.forEach { entry ->
            entry.elements.size shouldBe 2
            // First element should be a string (key)
            entry.elements[0] shouldBe io.kotest.matchers.types.instanceOf<StringValue>()
            // Second element should be a number (value)
            entry.elements[1] shouldBe io.kotest.matchers.types.instanceOf<NumberValue>()
        }
    }

    "Object.entries() on empty object returns empty array" {
        val engine = klangScript {
            registerLibrary(KlangStdLib.create())
        }

        val result = engine.execute(
            """
            import * from "stdlib"
            let obj = { }
            Object.entries(obj)
            """.trimIndent()
        ) as ArrayValue

        result.elements.size shouldBe 0
    }

    "Object.keys() with mixed value types" {
        val engine = klangScript {
            registerLibrary(KlangStdLib.create())
        }

        val result = engine.execute(
            """
            import * from "stdlib"
            let obj = { name: "Alice", age: 30, active: true }
            Object.keys(obj)
            """.trimIndent()
        ) as ArrayValue

        result.elements.size shouldBe 3
        val keys = result.elements.map { (it as StringValue).value }.toSet()
        keys shouldBe setOf("name", "age", "active")
    }

    "Object.values() with mixed value types" {
        val engine = klangScript {
            registerLibrary(KlangStdLib.create())
        }

        val result = engine.execute(
            """
            import * from "stdlib"
            let obj = { count: 5, label: "test" }
            Object.values(obj)
            """.trimIndent()
        ) as ArrayValue

        result.elements.size shouldBe 2
        // Values include both number and string types
    }
})

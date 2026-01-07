package io.peekandpoke.klang.script

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.peekandpoke.klang.script.builder.registerFunction
import io.peekandpoke.klang.script.runtime.ArgumentError
import io.peekandpoke.klang.script.runtime.ImportError
import io.peekandpoke.klang.script.runtime.ReferenceError
import io.peekandpoke.klang.script.runtime.TypeError

/**
 * Tests for end-to-end source location tracking in error messages
 *
 * Verifies that the parser captures source locations and the interpreter
 * propagates them through to error messages.
 */
class LocationTrackingTest : StringSpec({

    "ReferenceError includes source location from parser" {
        val engine = klangScript()
        val script = """
            let x = 5
            undefinedVariable
        """.trimIndent()

        val error = shouldThrow<ReferenceError> {
            engine.execute(script, sourceName = "test.klang")
        }

        error.symbolName shouldBe "undefinedVariable"
        error.location shouldNotBe null
        error.location?.source shouldBe "test.klang"
        error.location?.line shouldBe 2
        error.format() shouldContain "test.klang:2:"
    }

    "TypeError includes source location for binary operations" {
        val engine = klangScript()
        val script = """
            let x = 5
            let y = "hello"
            x + y
        """.trimIndent()

        val error = shouldThrow<TypeError> {
            engine.execute(script, sourceName = "math.klang")
        }

        error.location shouldNotBe null
        error.location?.source shouldBe "math.klang"
        error.location?.line shouldBe 3
        error.format() shouldContain "math.klang:3:"
    }

    "TypeError includes source location for member access" {
        val engine = klangScript()
        val script = """
            let num = 42
            num.property
        """.trimIndent()

        val error = shouldThrow<TypeError> {
            engine.execute(script, sourceName = "access.klang")
        }

        error.location shouldNotBe null
        error.location?.source shouldBe "access.klang"
        error.location?.line shouldBe 2
        error.format() shouldContain "access.klang:2:"
    }

    "TypeError includes source location for function calls" {
        val engine = klangScript()
        val script = """
            let x = 5
            x()
        """.trimIndent()

        val error = shouldThrow<TypeError> {
            engine.execute(script, sourceName = "call.klang")
        }

        error.location shouldNotBe null
        error.location?.source shouldBe "call.klang"
        error.location?.line shouldBe 2
        error.format() shouldContain "call.klang:2:"
    }

    "ArgumentError includes source location for script functions" {
        val engine = klangScript()

        val script = """
            let add = (a, b) => a + b
            add(1)
        """.trimIndent()

        val error = shouldThrow<ArgumentError> {
            engine.execute(script, sourceName = "args.klang")
        }

        error.location shouldNotBe null
        error.location?.source shouldBe "args.klang"
        error.location?.line shouldBe 2
        error.format() shouldContain "args.klang:2:"
    }

    "ArgumentError for native functions (no location yet)" {
        // Note: Native function argument validation happens in the helper functions
        // which don't have access to source location. This is a known limitation.
        val engine = klangScript {
            registerFunction<Double, Double, Double>("test") { x, y -> x + y }
        }

        val error = shouldThrow<ArgumentError> {
            engine.execute("test(1)", sourceName = "native.klang")
        }

        error.functionName shouldBe "test"
        error.expected shouldBe 2
        error.actual shouldBe 1
        // Location is null for native function errors (limitation)
        error.location shouldBe null
    }

    "ImportError includes source location" {
        val engine = klangScript()

        val script = """
            import * from "nonexistent"
        """.trimIndent()

        val error = shouldThrow<ImportError> {
            engine.execute(script, sourceName = "imports.klang")
        }

        error.location shouldNotBe null
        error.location?.source shouldBe "imports.klang"
        error.location?.line shouldBe 1
        error.format() shouldContain "imports.klang:1:"
    }

    "Location tracking works across multiple lines" {
        val engine = klangScript()

        val script = """
            let a = 1
            let b = 2
            let c = 3
            let d = 4
            undefinedVar
        """.trimIndent()

        val error = shouldThrow<ReferenceError> {
            engine.execute(script, sourceName = "multiline.klang")
        }

        error.location?.line shouldBe 5
        error.format() shouldContain "multiline.klang:5:"
    }

    "Location tracking works without source name" {
        val engine = klangScript()

        val error = shouldThrow<ReferenceError> {
            engine.execute("missingVar")
        }

        error.location shouldNotBe null
        error.location?.source shouldBe null
        error.location?.line shouldBe 1
        error.format() shouldContain "line 1"
    }
})

//private infix fun <T> T.shouldNotBe(expected: T) {
//    if (this == expected) {
//        throw AssertionError("Expected value not to be $expected but it was")
//    }
//}

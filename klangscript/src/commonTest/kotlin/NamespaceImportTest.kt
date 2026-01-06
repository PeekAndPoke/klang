package io.peekandpoke.klang.script

import com.github.h0tk3y.betterParse.parser.ParseException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.peekandpoke.klang.script.ast.ImportStatement
import io.peekandpoke.klang.script.parser.KlangScriptParser
import io.peekandpoke.klang.script.runtime.NumberValue
import io.peekandpoke.klang.script.runtime.ObjectValue

/**
 * Tests for Namespace Imports (Step 3.8.2)
 *
 * Validates:
 * - Parsing namespace import statements
 * - Creating namespace objects from exports
 * - Accessing exports via namespace.property
 * - No scope pollution (exports not in current scope)
 * - Multiple namespaces from different libraries
 * - Error handling
 */
class NamespaceImportTest : StringSpec({

    "should parse namespace import statement" {
        val result = KlangScriptParser.parse("""import * as math from "math" """)

        result.statements.size shouldBe 1
        val stmt = result.statements[0]
        stmt.shouldBeInstanceOf<ImportStatement>()
        stmt.libraryName shouldBe "math"
        stmt.imports shouldBe null  // Wildcard
        stmt.namespaceAlias shouldBe "math"
    }

    "should create namespace object from exports" {
        val engine = KlangScript()

        engine.registerLibrary(
            "math", """
            let add = (a, b) => a + b
            let multiply = (a, b) => a * b
            export { add, multiply }
        """.trimIndent()
        )

        engine.execute(
            """
            import * as math from "math"
        """.trimIndent()
        )

        // Verify namespace is an object
        val namespace = engine.getVariable("math")
        namespace.shouldBeInstanceOf<ObjectValue>()
    }

    "should access exports via namespace property" {
        val engine = KlangScript()

        engine.registerLibrary(
            "math", """
            let add = (a, b) => a + b
            let multiply = (a, b) => a * b
            export { add, multiply }
        """.trimIndent()
        )

        val result = engine.execute(
            """
            import * as math from "math"
            math.add(1, 2)
        """.trimIndent()
        )

        result shouldBe NumberValue(3.0)
    }

    "should access multiple exports via namespace" {
        val engine = KlangScript()

        engine.registerLibrary(
            "math", """
            let add = (a, b) => a + b
            let multiply = (a, b) => a * b
            export { add, multiply }
        """.trimIndent()
        )

        val result = engine.execute(
            """
            import * as math from "math"
            math.add(2, math.multiply(3, 4))
        """.trimIndent()
        )

        result shouldBe NumberValue(14.0)
    }

    "should not pollute current scope" {
        val engine = KlangScript()

        engine.registerLibrary(
            "math", """
            let add = (a, b) => a + b
            export { add }
        """.trimIndent()
        )

        try {
            engine.execute(
                """
                import * as math from "math"
                add(1, 2)  // Should fail - 'add' is not in current scope
            """.trimIndent()
            )
            error("Should have thrown exception")
        } catch (e: RuntimeException) {
            e.message shouldBe "Undefined variable: add"
        }
    }

    "should support multiple namespaces from different libraries" {
        val engine = KlangScript()

        engine.registerLibrary(
            "math", """
            let add = (a, b) => a + b
            export { add }
        """.trimIndent()
        )

        engine.registerLibrary(
            "strings", """
            let concat = (a, b) => a + " " + b
            export { concat }
        """.trimIndent()
        )

        engine.execute(
            """
            import * as math from "math"
            import * as str from "strings"
        """.trimIndent()
        )

        val result1 = engine.execute("math.add(1, 2)")
        result1 shouldBe NumberValue(3.0)

        // Note: We can't easily test string concat without string type support
        // but the namespace should be defined
        val namespace = engine.getVariable("str")
        namespace.shouldBeInstanceOf<ObjectValue>()
    }

    "should handle namespace with nested function calls" {
        val engine = KlangScript()

        engine.registerLibrary(
            "ops", """
            let double = (x) => x * 2
            let square = (x) => x * x
            export { double, square }
        """.trimIndent()
        )

        val result = engine.execute(
            """
            import * as ops from "ops"
            ops.double(ops.square(3))
        """.trimIndent()
        )

        result shouldBe NumberValue(18.0)  // square(3) = 9, double(9) = 18
    }

    "should namespace work with object properties" {
        val engine = KlangScript()

        engine.registerLibrary(
            "config", """
            let settings = { value: 42, flag: true }
            export { settings }
        """.trimIndent()
        )

        val result = engine.execute(
            """
            import * as cfg from "config"
            cfg.settings.value
        """.trimIndent()
        )

        result shouldBe NumberValue(42.0)
    }

    "should allow different alias names for namespace" {
        val engine = KlangScript()

        engine.registerLibrary(
            "mathematics", """
            let pi = 3.14159
            export { pi }
        """.trimIndent()
        )

        val result = engine.execute(
            """
            import * as m from "mathematics"
            m.pi
        """.trimIndent()
        )

        result shouldBe NumberValue(3.14159)
    }

    "should namespace only include exported symbols" {
        val engine = KlangScript()

        engine.registerLibrary(
            "lib", """
            let public = (x) => x + 1
            let private = (x) => x * 2
            export { public }
        """.trimIndent()
        )

        engine.execute(
            """
            import * as lib from "lib"
        """.trimIndent()
        )

        val namespace = engine.getVariable("lib") as ObjectValue
        val props = namespace.properties

        props.containsKey("public") shouldBe true
        props.containsKey("private") shouldBe false
    }

    "should error when combining namespace with selective import" {
        val engine = KlangScript()

        engine.registerLibrary(
            "math", """
            let add = (a, b) => a + b
            export { add }
        """.trimIndent()
        )

        shouldThrow<ParseException> {
            // This should fail at parse time or runtime
            // Parser allows it, so check runtime error
            engine.execute(
                """
                import { add } as math from "math"
            """.trimIndent()
            )
            error("Should have thrown exception")
        }
    }

    "should namespace work with backward compatible libraries" {
        val engine = KlangScript()

        // Library without export statement - exports all
        engine.registerLibrary(
            "old", """
            let func1 = (x) => x + 1
            let func2 = (x) => x * 2
        """.trimIndent()
        )

        val result = engine.execute(
            """
            import * as old from "old"
            old.func1(old.func2(5))
        """.trimIndent()
        )

        result shouldBe NumberValue(11.0)  // func2(5) = 10, func1(10) = 11
    }

    "should parse namespace import and wildcard import differently" {
        val withNamespace = KlangScriptParser.parse("""import * as math from "lib" """)
        val withoutNamespace = KlangScriptParser.parse("""import * from "lib" """)

        val stmt1 = withNamespace.statements[0] as ImportStatement
        val stmt2 = withoutNamespace.statements[0] as ImportStatement

        stmt1.namespaceAlias shouldBe "math"
        stmt2.namespaceAlias shouldBe null
    }

    "should support namespace with native functions in library" {
        val engine = KlangScript()

        engine.registerFunction1("nativeSquare") { value ->
            val num = (value as NumberValue).value
            NumberValue(num * num)
        }

        engine.registerLibrary(
            "helpers", """
            let doubleSquare = (x) => nativeSquare(x) * 2
            export { doubleSquare }
        """.trimIndent()
        )

        val result = engine.execute(
            """
            import * as h from "helpers"
            h.doubleSquare(4)
        """.trimIndent()
        )

        result shouldBe NumberValue(32.0)  // square(4) = 16, * 2 = 32
    }
})

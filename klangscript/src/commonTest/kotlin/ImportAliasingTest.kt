package io.peekandpoke.klang.script

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.peekandpoke.klang.script.ast.ImportStatement
import io.peekandpoke.klang.script.builder.registerLibrary
import io.peekandpoke.klang.script.parser.KlangScriptParser
import io.peekandpoke.klang.script.runtime.NumberValue

/**
 * Tests for Import Aliasing (Step 3.8.1)
 *
 * Validates:
 * - Parsing import statements with aliases
 * - Single import with alias
 * - Multiple imports with aliases
 * - Mixed imports (some aliased, some not)
 * - Name conflict resolution via aliasing
 */
class ImportAliasingTest : StringSpec({

    "should parse import with single alias" {
        val result = KlangScriptParser.parse("""import { add as sum } from "math" """)

        result.statements.size shouldBe 1
        val stmt = result.statements[0]
        stmt.shouldBeInstanceOf<ImportStatement>()
        stmt.libraryName shouldBe "math"
        stmt.imports shouldBe listOf(Pair("add", "sum"))
    }

    "should parse import with multiple aliases" {
        val result = KlangScriptParser.parse("""import { add as sum, multiply as mul } from "math" """)

        result.statements.size shouldBe 1
        val stmt = result.statements[0]
        stmt.shouldBeInstanceOf<ImportStatement>()
        stmt.libraryName shouldBe "math"
        stmt.imports shouldBe listOf(
            Pair("add", "sum"),
            Pair("multiply", "mul")
        )
    }

    "should parse mixed import with and without aliases" {
        val result = KlangScriptParser.parse("""import { add, multiply as mul } from "math" """)

        result.statements.size shouldBe 1
        val stmt = result.statements[0]
        stmt.shouldBeInstanceOf<ImportStatement>()
        stmt.libraryName shouldBe "math"
        stmt.imports shouldBe listOf(
            Pair("add", "add"),  // No alias, same name
            Pair("multiply", "mul")  // Aliased
        )
    }

    "should support aliasing to avoid name conflicts" {
        val engine = klangScript {
            registerLibrary(
                "math", """
                    let add = (a, b) => a + b
                    export { add }
                """.trimIndent()
            )

            registerLibrary(
                "strings", """
                    let add = (a, b) => a + " " + b
                    export { add }
                """.trimIndent()
            )
        }

        val result = engine.execute(
            """
                import { add as mathAdd } from "math"
                import { add as stringAdd } from "strings"
                mathAdd(1, 2)
            """.trimIndent()
        )

        result shouldBe NumberValue(3.0)
    }

    "should use aliased name in scope" {
        val engine = klangScript {
            registerLibrary(
                "math", """
                    let add = (a, b) => a + b
                    let multiply = (a, b) => a * b
                    export { add, multiply }
                """.trimIndent()
            )
        }

        val result = engine.execute(
            """
                import { add as sum, multiply as mul } from "math"
                sum(2, mul(3, 4))
            """.trimIndent()
        )

        result shouldBe NumberValue(14.0)
    }

    "should not expose original name when aliased" {
        val engine = klangScript {
            registerLibrary(
                "math", """
                    let add = (a, b) => a + b
                    export { add }
                """.trimIndent()
            )
        }

        try {
            engine.execute(
                """
                    import { add as sum } from "math"
                    add(1, 2)  // Should fail - only 'sum' is defined
                """.trimIndent()
            )
            error("Should have thrown exception")
        } catch (e: RuntimeException) {
            // Expected - 'add' is not defined, only 'sum' is
            e.message shouldBe "Undefined variable: add"
        }
    }

    "should work with single alias" {
        val engine = klangScript {
            registerLibrary(
                "math", """
                    let square = (x) => x * x
                    export { square }
                """.trimIndent()
            )
        }

        val result = engine.execute(
            """
                import { square as sq } from "math"
                sq(5)
            """.trimIndent()
        )

        result shouldBe NumberValue(25.0)
    }

    "should allow multiple imports with different aliases" {
        val engine = klangScript {
            registerLibrary(
                "lib", """
                    let a = 1
                    let b = 2
                    let c = 3
                    export { a, b, c }
                """.trimIndent()
            )
        }

        val result = engine.execute(
            """
                import { a as x } from "lib"
                import { b as y, c as z } from "lib"
                x + y + z
            """.trimIndent()
        )

        result shouldBe NumberValue(6.0)
    }

    "should support aliasing for complex library patterns" {
        val engine = klangScript {
            registerFunctionRaw("nativeLog") { values ->
                val value = values[0]
                NumberValue((value as NumberValue).value * 10)
            }

            registerLibrary(
                "helpers", """
                    let processValue = (x) => nativeLog(x)
                    let transformValue = (x) => x * 2
                    export { processValue, transformValue }
                """.trimIndent()
            )
        }

        val result = engine.execute(
            """
                import { processValue as process, transformValue as transform } from "helpers"
                process(transform(3))
            """.trimIndent()
        )

        result shouldBe NumberValue(60.0)  // 3 * 2 * 10
    }

    "should error when trying to import non-exported symbol even with alias" {
        val engine = klangScript {
            registerLibrary(
                "lib", """
                    let public = (x) => x + 1
                    let private = (x) => x * 2
                    export { public }
                """.trimIndent()
            )
        }

        try {
            engine.execute(
                """
                    import { private as priv } from "lib"
                """.trimIndent()
            )
            error("Should have thrown exception")
        } catch (e: RuntimeException) {
            e.message shouldBe "Cannot import non-exported symbols: private"
        }
    }

    "should support aliasing with object properties" {
        val engine = klangScript {
            registerLibrary(
                "objects", """
                    let config = { value: 42 }
                    export { config }
                """.trimIndent()
            )
        }

        val result = engine.execute(
            """
                import { config as settings } from "objects"
                settings.value
            """.trimIndent()
        )

        result shouldBe NumberValue(42.0)
    }

    "should parse non-aliased imports as identity pairs" {
        val result = KlangScriptParser.parse("""import { add, multiply } from "math" """)

        val stmt = result.statements[0] as ImportStatement
        stmt.imports shouldBe listOf(
            Pair("add", "add"),
            Pair("multiply", "multiply")
        )
    }
})

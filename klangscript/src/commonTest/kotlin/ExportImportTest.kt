package io.peekandpoke.klang.script

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.peekandpoke.klang.script.ast.ExportStatement
import io.peekandpoke.klang.script.ast.ImportStatement
import io.peekandpoke.klang.script.builder.registerLibrary
import io.peekandpoke.klang.script.parser.KlangScriptParser
import io.peekandpoke.klang.script.runtime.NumberValue
import io.peekandpoke.klang.script.runtime.StringValue

/**
 * Tests for Export/Import System with JavaScript-like syntax
 *
 * Validates:
 * - Parsing export statements
 * - Parsing selective import statements
 * - Export preventing scope pollution
 * - Selective imports
 * - Error handling for non-exported symbols
 */
class ExportImportTest : StringSpec({

    "should parse export statement" {
        val result = KlangScriptParser.parse("""export { add, multiply }""")

        result.statements.size shouldBe 1
        val stmt = result.statements[0]
        stmt.shouldBeInstanceOf<ExportStatement>()
        stmt.exports shouldBe listOf(Pair("add", "add"), Pair("multiply", "multiply"))
    }

    "should parse selective import statement" {
        val result = KlangScriptParser.parse("""import { add, multiply } from "math" """)

        result.statements.size shouldBe 1
        val stmt = result.statements[0]
        stmt.shouldBeInstanceOf<ImportStatement>()
        stmt.libraryName shouldBe "math"
        stmt.imports shouldBe listOf(Pair("add", "add"), Pair("multiply", "multiply"))
    }

    "should parse wildcard import statement" {
        val result = KlangScriptParser.parse("""import * from "math" """)

        result.statements.size shouldBe 1
        val stmt = result.statements[0]
        stmt.shouldBeInstanceOf<ImportStatement>()
        stmt.libraryName shouldBe "math"
        stmt.imports shouldBe null  // Wildcard
    }

    "should prevent scope pollution with explicit exports" {
        val engine = klangScript {
            registerLibrary(
                "math", """
                    let internalHelper = (x) => x * 2
                    let add = (a, b) => a + b
                    let multiply = (a, b) => a * b
                    export { add, multiply }
                """.trimIndent()
            )
        }

        // Try to import non-exported symbol
        try {
            engine.execute(
                """
                    import { add, internalHelper } from "math"
                    add(1, 2)
                """.trimIndent()
            )
            error("Should have thrown exception")
        } catch (e: RuntimeException) {
            e.message shouldBe "Cannot import non-exported symbols: internalHelper"
        }
    }

    "should import only exported symbols with wildcard" {
        val engine = klangScript {
            registerLibrary(
                "math", """
                    let internalHelper = (x) => x * 2
                    let add = (a, b) => a + b
                    let multiply = (a, b) => a * b
                    export { add, multiply }
                """.trimIndent()
            )
        }

        val result = engine.execute(
            """
                import * from "math"
                add(5, 3)
            """.trimIndent()
        )

        result shouldBe NumberValue(8.0)

        // Internal helper should not be accessible
        try {
            engine.execute(
                """
                    import * from "math"
                    internalHelper(5)
                """.trimIndent()
            )
            error("Should have thrown exception")
        } catch (_: RuntimeException) {
            // Expected - internalHelper is not exported
        }
    }

    "should support selective imports" {
        val engine = klangScript {
            registerLibrary(
                "math", """
                    let add = (a, b) => a + b
                    let subtract = (a, b) => a - b
                    let multiply = (a, b) => a * b
                    let divide = (a, b) => a / b
                    export { add, subtract, multiply, divide }
                """.trimIndent()
            )
        }

        val result = engine.execute(
            """
                import { add, multiply } from "math"
                add(2, multiply(3, 4))
            """.trimIndent()
        )

        result shouldBe NumberValue(14.0)
    }

    "should allow importing single symbol" {
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
                import { square } from "math"
                square(5)
            """.trimIndent()
        )

        result shouldBe NumberValue(25.0)
    }

    "should support libraries with mixed exports" {
        val engine = klangScript {
            registerLibrary(
                "signals", """
                    let frequency = 440
                    let amplitude = 1.0
                    let sine = {
                        freq: frequency,
                        amp: amplitude
                    }
                    let internal = "private"
                    export { sine, frequency }
                """.trimIndent()
            )
        }

        val result = engine.execute(
            """
                import { sine } from "signals"
                sine.freq
            """.trimIndent()
        )

        result shouldBe NumberValue(440.0)
    }

    "should backward compatible - libraries without exports export all" {
        // Library without export statement
        val engine = klangScript {
            registerLibrary(
                "old", """
                    let func1 = (x) => x + 1
                    let func2 = (x) => x * 2
                """.trimIndent()
            )
        }

        val result = engine.execute(
            """
                import * from "old"
                func1(func2(5))
            """.trimIndent()
        )

        result shouldBe NumberValue(11.0)
    }

    "should prevent importing from library without matching export" {
        val engine = klangScript {
            registerLibrary(
                "lib", """
                    let add = (a, b) => a + b
                    export { add }
                """.trimIndent()
            )
        }

        try {
            engine.execute(
                """
                import { subtract } from "lib"
            """.trimIndent()
            )
            error("Should have thrown exception")
        } catch (e: RuntimeException) {
            e.message shouldBe "Cannot import non-exported symbols: subtract"
        }
    }

    "should allow multiple selective imports from same library" {
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
                import { a } from "lib"
                import { b, c } from "lib"
                a + b + c
            """.trimIndent()
        )

        result shouldBe NumberValue(6.0)
    }


    "should export functions that use native functions" {
        val engine = klangScript {
            registerFunctionRaw("nativeDouble") { values ->
                val value = values[0]
                NumberValue((value as NumberValue).value * 2)
            }

            registerLibrary(
                "lib", """
                    let useNative = (x) => nativeDouble(x)
                    let internal = (x) => x + 1
                    export { useNative }
                """.trimIndent()
            )
        }

        val result = engine.execute(
            """
                import { useNative } from "lib"
                useNative(7)
            """.trimIndent()
        )

        result shouldBe NumberValue(14.0)
    }

    "should support complex real-world library pattern" {
        val engine = klangScript {
            registerLibrary(
                "strudel", """
                    // Internal helpers
                    let createPattern = (str) => { value: str }
                    let addMethod = (obj, name, fn) => {
                        value: obj.value,
                        method: fn
                    }
    
                    // Public API
                    let note = (pattern) => createPattern(pattern)
                    let sound = (pattern) => createPattern(pattern)
                    let sine = { freq: 440 }
    
                    export { note, sound, sine }
                """.trimIndent()
            )
        }

        val result = engine.execute(
            """
                import { note, sine } from "strudel"
                note("a b c").value
            """.trimIndent()
        )

        result shouldBe StringValue("a b c")
    }

    "should error on importing multiple non-exported symbols" {
        val engine = klangScript {
            registerLibrary(
                "lib", """
                    let a = 1
                    export { a }
                """.trimIndent()
            )
        }

        try {
            engine.execute(
                """
                    import { a, b, c } from "lib"
                """.trimIndent()
            )
            error("Should have thrown exception")
        } catch (e: RuntimeException) {
            e.message shouldBe "Cannot import non-exported symbols: b, c"
        }
    }
})

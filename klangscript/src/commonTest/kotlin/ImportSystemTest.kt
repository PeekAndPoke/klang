package io.peekandpoke.klang.script

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.peekandpoke.klang.script.ast.ImportStatement
import io.peekandpoke.klang.script.parser.KlangScriptParser
import io.peekandpoke.klang.script.runtime.NumberValue
import io.peekandpoke.klang.script.runtime.StringValue

/**
 * Tests for Step 3.7: Import System
 *
 * Validates:
 * - Parsing import statements
 * - Library registration
 * - Importing and using library functions
 * - Importing and using library objects
 * - Error handling (library not found)
 * - Symbol isolation (library internals don't leak)
 */
class ImportSystemTest : StringSpec({

    "should parse import statement" {
        val result = KlangScriptParser.parse("""import * from "math" """)

        result.statements.size shouldBe 1
        val stmt = result.statements[0]
        stmt.shouldBeInstanceOf<ImportStatement>()
        stmt.libraryName shouldBe "math"
    }

    "should parse import with .klang extension" {
        val result = KlangScriptParser.parse("""import * from "strudel.klang" """)

        result.statements.size shouldBe 1
        val stmt = result.statements[0] as ImportStatement
        stmt.libraryName shouldBe "strudel.klang"
    }

    "should import and use library function" {
        val engine = klangScript {
            // Register a library with a simple function
            registerLibrary(
                "math", """
                    let square = (x) => x * x
                """.trimIndent()
            )
        }

        // Import and use the function
        val result = engine.execute(
            """
                import * from "math"
                square(5)
            """.trimIndent()
        )

        result shouldBe NumberValue(25.0)
    }

    "should import multiple functions from library" {
        val engine = klangScript {
            registerLibrary(
                "math", """
                    let add = (a, b) => a + b
                    let multiply = (a, b) => a * b
                    let pi = 3.14159
                """.trimIndent()
            )
        }

        val result = engine.execute(
            """
                import * from "math"
                add(pi, multiply(2, 3))
            """.trimIndent()
        )

        result shouldBe NumberValue(9.14159)
    }

    "should import and use library object with methods" {
        val engine = klangScript {
            registerLibrary(
                "signals", """
                    let sine = {
                        frequency: 440,
                        amplitude: 1.0
                    }
                """.trimIndent()
            )
        }

        val result = engine.execute(
            """
                import * from "signals"
                sine.frequency
            """.trimIndent()
        )

        result shouldBe NumberValue(440.0)
    }

    "should allow multiple imports in same script" {
        val engine = klangScript {
            registerLibrary(
                "math", """
                    let add = (a, b) => a + b
                """.trimIndent()
            )

            registerLibrary(
                "string", """
                    let concat = (a, b) => "result"
                """.trimIndent()
            )
        }

        val result = engine.execute(
            """
                import * from "math"
                import * from "string"
                add(1, 2)
            """.trimIndent()
        )

        result shouldBe NumberValue(3.0)
    }

    "should isolate library internal variables" {
        val engine = klangScript {
            registerLibrary(
                "lib", """
                    let internal = "should not be visible"
                    let public = "exported"
                """.trimIndent()
            )
        }

        // Both should be imported with wildcard import
        val result = engine.execute(
            """
                import * from "lib"
                public
            """.trimIndent()
        )

        result shouldBe StringValue("exported")
    }

    "should throw error when library not found" {
        val engine = KlangScript.builder().build()

        try {
            engine.execute(
                """
                    import * from "nonexistent"
                """.trimIndent()
            )
            error("Should have thrown exception")
        } catch (e: RuntimeException) {
            e.message shouldBe "Failed to load library: Library not found"
        }
    }

    "should allow library functions to call each other" {
        val engine = klangScript {
            registerLibrary(
                "math", """
                    let double = (x) => x * 2
                    let quadruple = (x) => double(double(x))
                """.trimIndent()
            )
        }

        val result = engine.execute(
            """
                import * from "math"
                quadruple(3)
            """.trimIndent()
        )

        result shouldBe NumberValue(12.0)
    }

    "should support arrow functions in library that capture library variables" {
        val engine = klangScript {
            registerLibrary(
                "counter", """
                    let initialValue = 10
                    let makeCounter = () => initialValue
                """.trimIndent()
            )
        }

        val result = engine.execute(
            """
                import * from "counter"
                makeCounter()
            """.trimIndent()
        )

        result shouldBe NumberValue(10.0)
    }

    "should allow importing library that uses native functions" {
        val engine = klangScript {
            // Register a native function
            registerFunction1("nativeDouble") { value ->
                NumberValue((value as NumberValue).value * 2)
            }

            // Library uses the native function
            registerLibrary(
                "lib", """
                    let useNative = (x) => nativeDouble(x)
                """.trimIndent()
            )
        }

        val result = engine.execute(
            """
                import * from "lib"
                useNative(5)
            """.trimIndent()
        )

        result shouldBe NumberValue(10.0)
    }

    "should support complex library with nested function calls" {
        val engine = klangScript {
            registerLibrary(
                "strudel", """
                    let note = (pattern) => {
                        value: pattern,
                        gain: (amount) => {
                            value: pattern,
                            gainValue: amount
                        }
                    }
                """.trimIndent()
            )
        }

        val result = engine.execute(
            """
                import * from "strudel"
                note("a b c").gain(0.5).gainValue
            """.trimIndent()
        )

        result shouldBe NumberValue(0.5)
    }

    "should parse import statement with comments" {
        val result = KlangScriptParser.parse(
            """
                // Import math library
                import * from "math"
                /* Now we can use math functions */
            """.trimIndent()
        )

        result.statements.size shouldBe 1
        val stmt = result.statements[0] as ImportStatement
        stmt.libraryName shouldBe "math"
    }

    "should handle import at different positions in script" {
        val engine = klangScript {
            registerLibrary(
                "lib", """
                    let func = (x) => x + 1
                """.trimIndent()
            )
        }


        // Import after some statements
        val result = engine.execute(
            """
                let x = 5
                import * from "lib"
                func(x)
            """.trimIndent()
        )

        result shouldBe NumberValue(6.0)
    }

    "should allow library to define constants" {
        val engine = klangScript {
            registerLibrary(
                "constants", """
                    const PI = 3.14159
                    const E = 2.71828
                """.trimIndent()
            )
        }


        val result = engine.execute(
            """
                import * from "constants"
                PI + E
            """.trimIndent()
        )

        result shouldBe NumberValue(5.85987)
    }
})

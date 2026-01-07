package io.peekandpoke.klang.script.stdlib

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.shouldBeExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.peekandpoke.klang.script.klangScript
import io.peekandpoke.klang.script.runtime.NumberValue
import io.peekandpoke.klang.script.runtime.StringValue

/**
 * Tests for KlangScript Standard Library
 *
 * Validates:
 * - I/O functions (print, console.log)
 * - Math functions (sqrt, abs, min, max, floor, ceil, round, pow, sin, cos, tan)
 * - String functions (length, toUpperCase, toLowerCase)
 * - Library import and usage patterns
 * - Error handling for invalid arguments
 */
class KlangStdLibTest : StringSpec({

    // ===== I/O Functions =====

    "print() outputs single argument" {
        val output = mutableListOf<String>()
        KlangStdLib.outputHandler = { output.add(it.joinToString()) }

        val engine = klangScript {
            registerLibrary(KlangStdLib.create())
        }

        engine.execute(
            """
                import * from "stdlib"
                print("Hello, World!")
            """.trimIndent()
        )

        output.size shouldBe 1
        output[0] shouldBe "Hello, World!"
    }

    "print() outputs multiple arguments separated by spaces" {
        val output = mutableListOf<String>()
        KlangStdLib.outputHandler = { output.add(it.joinToString()) }

        val engine = klangScript {
            registerLibrary(KlangStdLib.create())
        }

        engine.execute(
            """
            import * from "stdlib"
            print("x =", 42, "y =", 10)
        """.trimIndent()
        )

        output.size shouldBe 1
        output[0] shouldContain "x ="
        output[0] shouldContain "42"
        output[0] shouldContain "y ="
        output[0] shouldContain "10"
    }

    "print() with no arguments outputs empty line" {
        val output = mutableListOf<String>()
        KlangStdLib.outputHandler = { output.add(it.joinToString()) }

        val engine = klangScript {
            registerLibrary(KlangStdLib.create())
        }

        engine.execute(
            """
            import * from "stdlib"
            print()
        """.trimIndent()
        )

        output.size shouldBe 1
        output[0] shouldBe ""
    }

    "console.log() works like print()" {
        val output = mutableListOf<String>()
        KlangStdLib.outputHandler = { output.add(it.joinToString()) }

        val engine = klangScript {
            registerLibrary(KlangStdLib.create())
        }

        engine.execute(
            """
            import * from "stdlib"
            console.log("Test message")
        """.trimIndent()
        )

        output.size shouldBe 1
        output[0] shouldBe "Test message"
    }

    // ===== Math Functions - Single Parameter =====

    "Math.sqrt() calculates square root" {
        val engine = klangScript {
            registerLibrary(KlangStdLib.create())
        }

        val result = engine.execute(
            """
            import * from "stdlib"
            Math.sqrt(16)
        """.trimIndent()
        )

        (result as NumberValue).value shouldBeExactly 4.0
    }

    "Math.sqrt() with decimal" {
        val engine = klangScript {
            registerLibrary(KlangStdLib.create())
        }

        val result = engine.execute(
            """
            import * from "stdlib"
            Math.sqrt(2)
        """.trimIndent()
        ) as NumberValue

        result.value shouldBe 1.4142135623730951
    }

    "Math.abs() returns absolute value" {
        val engine = klangScript {
            registerLibrary(KlangStdLib.create())
        }

        val negative = engine.execute(
            """
            import * from "stdlib"
            Math.abs(-42)
        """.trimIndent()
        )

        (negative as NumberValue).value shouldBeExactly 42.0

        val positive = engine.execute(
            """
            import * from "stdlib"
            Math.abs(10)
        """.trimIndent()
        )

        (positive as NumberValue).value shouldBeExactly 10.0
    }

    "Math.floor() rounds down" {
        val engine = klangScript {
            registerLibrary(KlangStdLib.create())
        }

        val result = engine.execute(
            """
            import * from "stdlib"
            Math.floor(3.7)
        """.trimIndent()
        )

        (result as NumberValue).value shouldBeExactly 3.0
    }

    "Math.ceil() rounds up" {
        val engine = klangScript {
            registerLibrary(KlangStdLib.create())
        }

        val result = engine.execute(
            """
            import * from "stdlib"
            Math.ceil(3.2)
        """.trimIndent()
        )

        (result as NumberValue).value shouldBeExactly 4.0
    }

    "Math.round() rounds to nearest integer" {
        val engine = klangScript {
            registerLibrary(KlangStdLib.create())
        }

        val down = engine.execute(
            """
            import * from "stdlib"
            Math.round(3.4)
        """.trimIndent()
        )

        (down as NumberValue).value shouldBeExactly 3.0

        val up = engine.execute(
            """
            import * from "stdlib"
            Math.round(3.6)
        """.trimIndent()
        )

        (up as NumberValue).value shouldBeExactly 4.0
    }

    "Math.sin() calculates sine" {
        val engine = klangScript {
            registerLibrary(KlangStdLib.create())
        }

        val result = engine.execute(
            """
            import * from "stdlib"
            Math.sin(0)
        """.trimIndent()
        )

        (result as NumberValue).value shouldBeExactly 0.0
    }

    "Math.cos() calculates cosine" {
        val engine = klangScript {
            registerLibrary(KlangStdLib.create())
        }

        val result = engine.execute(
            """
            import * from "stdlib"
            Math.cos(0)
        """.trimIndent()
        )

        (result as NumberValue).value shouldBeExactly 1.0
    }

    "Math.tan() calculates tangent" {
        val engine = klangScript {
            registerLibrary(KlangStdLib.create())
        }

        val result = engine.execute(
            """
            import * from "stdlib"
            Math.tan(0)
        """.trimIndent()
        )

        (result as NumberValue).value shouldBeExactly 0.0
    }

    // ===== Math Functions - Two Parameters =====

    "Math.min() returns smaller value" {
        val engine = klangScript {
            registerLibrary(KlangStdLib.create())
        }

        val result = engine.execute(
            """
            import * from "stdlib"
            Math.min(5, 10)
        """.trimIndent()
        )

        (result as NumberValue).value shouldBeExactly 5.0
    }

    "Math.max() returns larger value" {
        val engine = klangScript {
            registerLibrary(KlangStdLib.create())
        }

        val result = engine.execute(
            """
            import * from "stdlib"
            Math.max(5, 10)
        """.trimIndent()
        )

        (result as NumberValue).value shouldBeExactly 10.0
    }

    "Math.pow() calculates power" {
        val engine = klangScript {
            registerLibrary(KlangStdLib.create())
        }

        val result = engine.execute(
            """
            import * from "stdlib"
            Math.pow(2, 8)
        """.trimIndent()
        )

        (result as NumberValue).value shouldBeExactly 256.0
    }

    "Math.pow() with decimal exponent" {
        val engine = klangScript {
            registerLibrary(KlangStdLib.create())
        }

        val result = engine.execute(
            """
            import * from "stdlib"
            Math.pow(4, 0.5)
        """.trimIndent()
        )

        (result as NumberValue).value shouldBeExactly 2.0
    }

    // ===== String Functions =====

    "length() returns string length" {
        val engine = klangScript {
            registerLibrary(KlangStdLib.create())
        }

        val result = engine.execute(
            """
            import * from "stdlib"
            length("hello")
        """.trimIndent()
        )

        (result as NumberValue).value shouldBeExactly 5.0
    }

    "length() with empty string" {
        val engine = klangScript {
            registerLibrary(KlangStdLib.create())
        }

        val result = engine.execute(
            """
            import * from "stdlib"
            length("")
        """.trimIndent()
        )

        (result as NumberValue).value shouldBeExactly 0.0
    }

    "toUpperCase() converts to uppercase" {
        val engine = klangScript {
            registerLibrary(KlangStdLib.create())
        }

        val result = engine.execute(
            """
            import * from "stdlib"
            toUpperCase("hello")
        """.trimIndent()
        )

        (result as StringValue).value shouldBe "HELLO"
    }

    "toLowerCase() converts to lowercase" {
        val engine = klangScript {
            registerLibrary(KlangStdLib.create())
        }

        val result = engine.execute(
            """
            import * from "stdlib"
            toLowerCase("WORLD")
        """.trimIndent()
        )

        (result as StringValue).value shouldBe "world"
    }

    // ===== Combined Usage =====

    "math functions can be combined" {
        val engine = klangScript {
            registerLibrary(KlangStdLib.create())
        }

        val result = engine.execute(
            """
            import * from "stdlib"
            let x = Math.sqrt(16)
            let y = Math.pow(2, 3)
            Math.max(x, y)
        """.trimIndent()
        )

        (result as NumberValue).value shouldBeExactly 8.0
    }

    "string functions can be chained" {
        val engine = klangScript {
            registerLibrary(KlangStdLib.create())
        }

        val result = engine.execute(
            """
            import * from "stdlib"
            let s = "Hello"
            toUpperCase(toLowerCase(s))
        """.trimIndent()
        )

        (result as StringValue).value shouldBe "HELLO"
    }

    "functions work with variables and expressions" {
        val output = mutableListOf<String>()
        KlangStdLib.outputHandler = { output.add(it.joinToString()) }

        val engine = klangScript {
            registerLibrary(KlangStdLib.create())
        }

        engine.execute(
            """
            import * from "stdlib"
            let x = 10
            let y = 5
            print("Sum:", x + y)
            print("Max:", Math.max(x, y))
            print("Sqrt of x:", Math.sqrt(x))
        """.trimIndent()
        )

        output.size shouldBe 3
        output[0] shouldContain "15"
        output[1] shouldContain "10"
        output[2] shouldContain "3.16"
    }

    // ===== Error Handling =====

    "Math.sqrt() with too many arguments still works" {
        val engine = klangScript {
            registerLibrary(KlangStdLib.create())
        }

        val result = engine.execute(
            """
                import * from "stdlib"
                Math.sqrt(16, 25)
            """.trimIndent()
        )

        result shouldBe NumberValue(4.0)
    }

    "Math.sqrt() with non-number throws error" {
        val engine = klangScript {
            registerLibrary(KlangStdLib.create())
        }

        shouldThrow<Exception> {
            engine.execute(
                """
                import * from "stdlib"
                Math.sqrt("hello")
            """.trimIndent()
            )
        }
    }

    "Math.min() requires exactly 2 arguments" {
        val engine = klangScript {
            registerLibrary(KlangStdLib.create())
        }

        shouldThrow<Exception> {
            engine.execute(
                """
                import * from "stdlib"
                Math.min(5)
            """.trimIndent()
            )
        }
    }

    "length() with non-string throws error" {
        val engine = klangScript {
            registerLibrary(KlangStdLib.create())
        }

        shouldThrow<IllegalArgumentException> {
            engine.execute(
                """
                import * from "stdlib"
                length(42)
            """.trimIndent()
            )
        }
    }

    // ===== Import Behavior =====

    /**
     * Note: Native functions registered in libraries are GLOBALLY available after import.
     * They are NOT controlled by export statements or selective imports.
     * This is by design - native registrations happen at the engine level, not the module level.
     *
     * To use stdlib functions:
     * 1. Import the library (any import statement will trigger native registration)
     * 2. Functions become globally available
     *
     * This differs from script functions which ARE controlled by exports.
     */
    "native functions are globally available after any import" {
        val engine = klangScript {
            registerLibrary(KlangStdLib.create())
        }

        // Any import triggers library registration, making ALL native functions global
        val result = engine.execute(
            """
            import * from "stdlib"
            Math.sqrt(16)
        """.trimIndent()
        )

        (result as NumberValue).value shouldBeExactly 4.0
    }

    "stdlib functions available after wildcard import" {
        val engine = klangScript {
            registerLibrary(KlangStdLib.create())
        }

        val result = engine.execute(
            """
            import * from "stdlib"
            Math.max(Math.sqrt(16), 5)
        """.trimIndent()
        )

        (result as NumberValue).value shouldBeExactly 5.0
    }
})

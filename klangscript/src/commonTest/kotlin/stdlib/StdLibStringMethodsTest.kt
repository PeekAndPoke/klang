package io.peekandpoke.klang.script.stdlib

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.script.klangScript
import io.peekandpoke.klang.script.runtime.ArrayValue
import io.peekandpoke.klang.script.runtime.BooleanValue
import io.peekandpoke.klang.script.runtime.NumberValue
import io.peekandpoke.klang.script.runtime.StringValue

/**
 * Tests for string methods registered as extension methods
 *
 * Validates:
 * - length(), charAt(), substring()
 * - indexOf(), split()
 * - toUpperCase(), toLowerCase(), trim()
 * - startsWith(), endsWith(), replace()
 * - slice(), concat(), repeat()
 */
class StdLibStringMethodsTest : StringSpec({

    // ===== Property-like methods =====

    "String length() returns string length" {
        val engine = klangScript {
            registerLibrary(KlangStdLib.create())
        }

        val result = engine.execute(
            """
            import * from "stdlib"
            "hello".length()
            """.trimIndent()
        )

        (result as NumberValue).value shouldBe 5.0
    }

    "String length() on empty string" {
        val engine = klangScript {
            registerLibrary(KlangStdLib.create())
        }

        val result = engine.execute(
            """
            import * from "stdlib"
            "".length()
            """.trimIndent()
        )

        (result as NumberValue).value shouldBe 0.0
    }

    // ===== Character access =====

    "String charAt() returns character at index" {
        val engine = klangScript {
            registerLibrary(KlangStdLib.create())
        }

        val result = engine.execute(
            """
            import * from "stdlib"
            "hello".charAt(1)
            """.trimIndent()
        )

        (result as StringValue).value shouldBe "e"
    }

    "String charAt() returns empty for out of bounds" {
        val engine = klangScript {
            registerLibrary(KlangStdLib.create())
        }

        val result = engine.execute(
            """
            import * from "stdlib"
            "hello".charAt(10)
            """.trimIndent()
        )

        (result as StringValue).value shouldBe ""
    }

    // ===== Substring operations =====

    "String substring() extracts substring" {
        val engine = klangScript {
            registerLibrary(KlangStdLib.create())
        }

        val result = engine.execute(
            """
            import * from "stdlib"
            "hello world".substring(0, 5)
            """.trimIndent()
        )

        (result as StringValue).value shouldBe "hello"
    }

    "String slice() extracts substring" {
        val engine = klangScript {
            registerLibrary(KlangStdLib.create())
        }

        val result = engine.execute(
            """
            import * from "stdlib"
            "hello world".slice(6, 11)
            """.trimIndent()
        )

        (result as StringValue).value shouldBe "world"
    }

    // ===== Search operations =====

    "String indexOf() finds substring position" {
        val engine = klangScript {
            registerLibrary(KlangStdLib.create())
        }

        val result = engine.execute(
            """
            import * from "stdlib"
            "hello world".indexOf("world")
            """.trimIndent()
        )

        (result as NumberValue).value shouldBe 6.0
    }

    "String indexOf() returns -1 when not found" {
        val engine = klangScript {
            registerLibrary(KlangStdLib.create())
        }

        val result = engine.execute(
            """
            import * from "stdlib"
            "hello world".indexOf("xyz")
            """.trimIndent()
        )

        (result as NumberValue).value shouldBe -1.0
    }

    // ===== Split operation =====

    "String split() splits into array" {
        val engine = klangScript {
            registerLibrary(KlangStdLib.create())
        }

        val result = engine.execute(
            """
            import * from "stdlib"
            "a,b,c".split(",")
            """.trimIndent()
        ) as ArrayValue

        result.elements.size shouldBe 3
        (result.elements[0] as StringValue).value shouldBe "a"
        (result.elements[1] as StringValue).value shouldBe "b"
        (result.elements[2] as StringValue).value shouldBe "c"
    }

    // ===== Case conversion =====

    "String toUpperCase() converts to uppercase" {
        val engine = klangScript {
            registerLibrary(KlangStdLib.create())
        }

        val result = engine.execute(
            """
            import * from "stdlib"
            "hello".toUpperCase()
            """.trimIndent()
        )

        (result as StringValue).value shouldBe "HELLO"
    }

    "String toLowerCase() converts to lowercase" {
        val engine = klangScript {
            registerLibrary(KlangStdLib.create())
        }

        val result = engine.execute(
            """
            import * from "stdlib"
            "HELLO".toLowerCase()
            """.trimIndent()
        )

        (result as StringValue).value shouldBe "hello"
    }

    // ===== Trimming =====

    "String trim() removes whitespace" {
        val engine = klangScript {
            registerLibrary(KlangStdLib.create())
        }

        val result = engine.execute(
            """
            import * from "stdlib"
            "  hello  ".trim()
            """.trimIndent()
        )

        (result as StringValue).value shouldBe "hello"
    }

    // ===== Prefix/Suffix checks =====

    "String startsWith() checks prefix" {
        val engine = klangScript {
            registerLibrary(KlangStdLib.create())
        }

        val result = engine.execute(
            """
            import * from "stdlib"
            "hello world".startsWith("hello")
            """.trimIndent()
        )

        (result as BooleanValue).value shouldBe true
    }

    "String startsWith() returns false for non-matching prefix" {
        val engine = klangScript {
            registerLibrary(KlangStdLib.create())
        }

        val result = engine.execute(
            """
            import * from "stdlib"
            "hello world".startsWith("world")
            """.trimIndent()
        )

        (result as BooleanValue).value shouldBe false
    }

    "String endsWith() checks suffix" {
        val engine = klangScript {
            registerLibrary(KlangStdLib.create())
        }

        val result = engine.execute(
            """
            import * from "stdlib"
            "hello world".endsWith("world")
            """.trimIndent()
        )

        (result as BooleanValue).value shouldBe true
    }

    // ===== Replace operation =====

    "String replace() replaces substring" {
        val engine = klangScript {
            registerLibrary(KlangStdLib.create())
        }

        val result = engine.execute(
            """
            import * from "stdlib"
            "hello world".replace("world", "universe")
            """.trimIndent()
        )

        (result as StringValue).value shouldBe "hello universe"
    }

    // ===== Concatenation =====

    "String concat() concatenates strings" {
        val engine = klangScript {
            registerLibrary(KlangStdLib.create())
        }

        val result = engine.execute(
            """
            import * from "stdlib"
            "hello".concat(" world")
            """.trimIndent()
        )

        (result as StringValue).value shouldBe "hello world"
    }

    // ===== Repeat operation =====

    "String repeat() repeats string" {
        val engine = klangScript {
            registerLibrary(KlangStdLib.create())
        }

        val result = engine.execute(
            """
            import * from "stdlib"
            "ha".repeat(3)
            """.trimIndent()
        )

        (result as StringValue).value shouldBe "hahaha"
    }

    "String repeat() with zero returns empty string" {
        val engine = klangScript {
            registerLibrary(KlangStdLib.create())
        }

        val result = engine.execute(
            """
            import * from "stdlib"
            "hello".repeat(0)
            """.trimIndent()
        )

        (result as StringValue).value shouldBe ""
    }
})

package io.peekandpoke.klang.script

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.peekandpoke.klang.script.ast.SourceLocation
import io.peekandpoke.klang.script.builder.registerLibrary
import io.peekandpoke.klang.script.runtime.*

/**
 * Comprehensive tests for error handling in KlangScript
 *
 * Tests all error types, error messages, source location tracking,
 * and error formatting.
 */
class ErrorHandlingTest : StringSpec({

    // ============================================================
    // ReferenceError Tests
    // ============================================================

    "ReferenceError - undefined variable should throw ReferenceError" {
        val engine = klangScript()

        val error = shouldThrow<ReferenceError> {
            engine.execute("undefinedVariable")
        }

        error.errorType shouldBe "ReferenceError"
        error.symbolName shouldBe "undefinedVariable"
        error.message shouldBe "Undefined variable: undefinedVariable"
    }

    "ReferenceError - format() without location" {
        val error = ReferenceError("foo")

        error.format() shouldBe "ReferenceError: Undefined variable: foo"
    }

    "ReferenceError - format() with location (no source)" {
        val location = SourceLocation(null, 5, 12)
        val error = ReferenceError("foo", location = location)

        error.format() shouldBe "ReferenceError at line 5, column 12: Undefined variable: foo"
    }

    "ReferenceError - format() with location (with source)" {
        val location = SourceLocation("main.klang", 5, 12)
        val error = ReferenceError("foo", location = location)

        error.format() shouldBe "ReferenceError at main.klang:5:12: Undefined variable: foo"
    }

    // ============================================================
    // TypeError Tests
    // ============================================================

    "TypeError - calling non-function should throw TypeError" {
        val engine = klangScript()

        val error = shouldThrow<TypeError> {
            engine.execute("let x = 5\nx()")
        }

        error.errorType shouldBe "TypeError"
        error.message shouldContain "Cannot call non-function"
    }

    "TypeError - binary operation on incompatible types" {
        val engine = klangScript()

        val error = shouldThrow<TypeError> {
            engine.execute("\"hello\" + null")
        }

        error.errorType shouldBe "TypeError"
        error.operation shouldBe "ADD"
    }

    "TypeError - member access on non-object" {
        val engine = klangScript()

        val error = shouldThrow<TypeError> {
            engine.execute("let x = 5\nx.foo")
        }

        error.errorType shouldBe "TypeError"
        error.message shouldContain "Cannot access property"
        error.operation shouldBe "member access"
    }

    "TypeError - format() without location or operation" {
        val error = TypeError("Invalid type")

        error.format() shouldBe "TypeError: Invalid type"
    }

    "TypeError - format() with operation but no location" {
        val error = TypeError("Cannot add string and number", operation = "+")

        error.format() shouldBe "TypeError in +: Cannot add string and number"
    }

    "TypeError - format() with location but no operation" {
        val location = SourceLocation("math.klang", 10, 5)
        val error = TypeError("Invalid type", location = location)

        error.format() shouldBe "TypeError at math.klang:10:5: Invalid type"
    }

    "TypeError - format() with both location and operation" {
        val location = SourceLocation("math.klang", 10, 5)
        val error = TypeError("Cannot add string and number", operation = "+", location = location)

        error.format() shouldBe "TypeError at math.klang:10:5 in +: Cannot add string and number"
    }

    // ============================================================
    // ArgumentError Tests
    // ============================================================

    "ArgumentError - wrong number of arguments to native function" {
        val builder = KlangScript.builder()
        builder.registerNativeFunction("double") { values ->
            val x = values[0]
            NumberValue((x as NumberValue).value * 2)
        }

        val engine = builder.build()

        val error = shouldThrow<ArgumentError> {
            engine.execute("double(1, 2, 3)")
        }

        error.errorType shouldBe "ArgumentError"
        error.functionName shouldBe "double"
        error.expected shouldBe 1
        error.actual shouldBe 3
    }

    "ArgumentError - wrong number of arguments to script function" {
        val engine = klangScript()

        val error = shouldThrow<ArgumentError> {
            engine.execute(
                """
                    let add = (a, b) => a + b
                    add(1)
                """.trimIndent()
            )
        }

        error.errorType shouldBe "ArgumentError"
        error.message shouldContain "expects 2 arguments"
    }

    "ArgumentError - format() without location" {
        val error = ArgumentError("myFunc", "Wrong arguments", expected = 2, actual = 3)

        error.format() shouldBe "ArgumentError in myFunc: Expected 2 arguments, got 3"
    }

    "ArgumentError - format() with location" {
        val location = SourceLocation("app.klang", 15, 8)
        val error = ArgumentError("myFunc", "Wrong arguments", expected = 2, actual = 3, location = location)

        error.format() shouldBe "ArgumentError at app.klang:15:8 in myFunc: Expected 2 arguments, got 3"
    }

    "ArgumentError - format() with custom message and location" {
        val location = SourceLocation("app.klang", 15, 8)
        val error = ArgumentError("myFunc", "Invalid argument type", location = location)

        error.format() shouldBe "ArgumentError at app.klang:15:8 in myFunc: Invalid argument type"
    }

    // ============================================================
    // ImportError Tests
    // ============================================================

    "ImportError - library not found" {
        val engine = klangScript()

        val error = shouldThrow<ImportError> {
            engine.execute("import * from \"nonexistent\"")
        }

        error.errorType shouldBe "ImportError"
        error.message shouldContain "Failed to load library"
    }

    "ImportError - importing non-exported symbol" {
        val builder = KlangScript.builder()
        builder.registerLibrary(
            "math", """
                let internal = 42
                let add = (a, b) => a + b
                export { add }
            """.trimIndent()
        )

        val engine = builder.build()

        val error = shouldThrow<ImportError> {
            engine.execute("import { internal } from \"math\"")
        }

        error.errorType shouldBe "ImportError"
        error.libraryName shouldBe "math"
        error.message shouldContain "non-exported symbols"
    }

    "ImportError - format() without library name or location" {
        val error = ImportError(null, "Cannot import")

        error.format() shouldBe "ImportError: Cannot import"
    }

    "ImportError - format() with library name but no location" {
        val error = ImportError("math", "Library not found")

        error.format() shouldBe "ImportError in library 'math': Library not found"
    }

    "ImportError - format() with location and library name" {
        val location = SourceLocation("main.klang", 1, 1)
        val error = ImportError("math", "Library not found", location = location)

        error.format() shouldBe "ImportError at main.klang:1:1 in library 'math': Library not found"
    }

    // ============================================================
    // AssignmentError Tests
    // ============================================================

    "AssignmentError - reassigning const variable" {
        // Note: Current implementation doesn't support assignment expressions yet
        // This test documents expected behavior when assignment is implemented
        // For now, we test that const tracking works in Environment
        val error = AssignmentError("x", "Cannot reassign const variable")

        error.errorType shouldBe "AssignmentError"
        error.variableName shouldBe "x"
    }

    "AssignmentError - format() without location" {
        val error = AssignmentError("x", "Cannot reassign const")

        error.format() shouldBe "AssignmentError for variable 'x': Cannot reassign const"
    }

    "AssignmentError - format() with location" {
        val location = SourceLocation("app.klang", 20, 3)
        val error = AssignmentError("x", "Cannot reassign const", location = location)

        error.format() shouldBe "AssignmentError at app.klang:20:3 for variable 'x': Cannot reassign const"
    }

    "AssignmentError - format() without variable name" {
        val error = AssignmentError(null, "Invalid assignment")

        error.format() shouldBe "AssignmentError: Invalid assignment"
    }

    // ============================================================
    // SourceLocation Tests
    // ============================================================

    "SourceLocation - toString() without source" {
        val location = SourceLocation(null, 10, 5)

        location.toString() shouldBe "line 10, column 5"
    }

    "SourceLocation - toString() with source" {
        val location = SourceLocation("math.klang", 10, 5)

        location.toString() shouldBe "math.klang:10:5"
    }

    // ============================================================
    // Error Scenarios in Imported Libraries
    // ============================================================

    "Error in library function should be thrown" {
        val builder = KlangScript.builder()
        builder.registerLibrary(
            "broken", """
                let divide = (a, b) => a / b
                let willFail = () => undefinedVar
                export { divide, willFail }
            """.trimIndent()
        )

        val engine = builder.build()

        val error = shouldThrow<ReferenceError> {
            engine.execute(
                """
                    import { willFail } from "broken"
                    willFail()
                """.trimIndent()
            )
        }

        error.symbolName shouldBe "undefinedVar"
    }

    "TypeError in library operation" {
        val builder = KlangScript.builder()
        builder.registerLibrary(
            "math", """
                let badAdd = (a, b) => a + b
                export { badAdd }
            """.trimIndent()
        )

        val engine = builder.build()

        val error = shouldThrow<TypeError> {
            engine.execute(
                """
                    import { badAdd } from "math"
                    badAdd("hello", null)
                """.trimIndent()
            )
        }

        error.operation shouldBe "ADD"
    }

    // ============================================================
    // Nested Function Call Errors
    // ============================================================

    "Error in nested function call" {
        val builder = KlangScript.builder()
        builder.registerNativeFunction("process") { values ->
            val x = values[0]
            NumberValue((x as NumberValue).value * 2)
        }

        val engine = builder.build()

        val error = shouldThrow<ReferenceError> {
            engine.execute(
                """
                    let calculate = () => process(missingVar)
                    calculate()
                """.trimIndent()
            )
        }

        error.symbolName shouldBe "missingVar"
    }

    "TypeError in nested arithmetic" {
        val engine = klangScript()

        val error = shouldThrow<TypeError> {
            engine.execute(
                """
                    let outer = (x) => x * 2
                    let inner = () => "string" + null
                    outer(inner())
                """.trimIndent()
            )
        }

        error.operation shouldBe "ADD"
    }

    // ============================================================
    // Error Message Quality
    // ============================================================

    "Error messages should be descriptive - undefined variable" {
        val engine = klangScript()

        val error = shouldThrow<ReferenceError> {
            engine.execute("myVariable")
        }

        error.message shouldBe "Undefined variable: myVariable"
        error.format() shouldContain "ReferenceError"
        error.format() shouldContain "myVariable"
    }

    "Error messages should be descriptive - type error" {
        val engine = klangScript()

        val error = shouldThrow<TypeError> {
            engine.execute("5()")
        }

        error.message shouldContain "Cannot call non-function"
        error.message shouldContain "5"
    }

    "Error messages should be descriptive - argument count" {
        val builder = KlangScript.builder()
        builder.registerNativeFunction("test") { x -> x.first() }

        val engine = builder.build()

        val error = shouldThrow<ArgumentError> {
            engine.execute("test(1, 2)")
        }

        error.format() shouldContain "Expected 1 arguments, got 2"
    }

    // ============================================================
    // Edge Cases - Unary Operations
    // ============================================================

    "TypeError - unary NOT on string should work (JavaScript truthiness)" {
        val engine = klangScript()

        // JavaScript-like truthiness: !"string" should work and return false
        val result = engine.execute("!\"hello\"")
        result.toDisplayString() shouldBe "false"
    }

    "TypeError - unary PLUS on string" {
        val engine = klangScript()

        val error = shouldThrow<TypeError> {
            engine.execute("+\"hello\"")
        }

        error.errorType shouldBe "TypeError"
        error.operation shouldBe "unary +"
    }

    "TypeError - unary NEGATE on string" {
        val engine = klangScript()

        val error = shouldThrow<TypeError> {
            engine.execute("-\"hello\"")
        }

        error.errorType shouldBe "TypeError"
        error.operation shouldBe "unary -"
    }

    "TypeError - unary NEGATE on null" {
        val engine = klangScript()

        val error = shouldThrow<TypeError> {
            engine.execute("-null")
        }

        error.errorType shouldBe "TypeError"
        error.operation shouldBe "unary -"
    }

    // ============================================================
    // Edge Cases - Arithmetic Operations
    // ============================================================

    "TypeError - multiplication with string" {
        val engine = klangScript()

        val error = shouldThrow<TypeError> {
            engine.execute("5 * \"hello\"")
        }

        error.errorType shouldBe "TypeError"
        error.operation shouldBe "MULTIPLY"
    }

    "TypeError - division with string" {
        val engine = klangScript()

        val error = shouldThrow<TypeError> {
            engine.execute("10 / \"hello\"")
        }

        error.errorType shouldBe "TypeError"
        error.operation shouldBe "DIVIDE"
    }

    "TypeError - subtraction with null" {
        val engine = klangScript()

        val error = shouldThrow<TypeError> {
            engine.execute("5 - null")
        }

        error.errorType shouldBe "TypeError"
        error.operation shouldBe "SUBTRACT"
    }

    "TypeError - boolean in arithmetic" {
        val engine = klangScript()

        val error = shouldThrow<TypeError> {
            engine.execute("true + 5")
        }

        error.errorType shouldBe "TypeError"
        error.operation shouldBe "ADD"
    }

    // ============================================================
    // Edge Cases - Member Access
    // ============================================================

    "TypeError - member access on null" {
        val engine = klangScript()

        val error = shouldThrow<TypeError> {
            engine.execute("null.property")
        }

        error.errorType shouldBe "TypeError"
        error.message shouldContain "Cannot access property"
    }

    "TypeError - chained member access on non-object" {
        val engine = klangScript()

        val error = shouldThrow<TypeError> {
            engine.execute(
                """
                    let obj = { a: 5 }
                    obj.a.b
                """.trimIndent()
            )
        }

        error.errorType shouldBe "TypeError"
        error.message shouldContain "Cannot access property"
    }

    "TypeError - member access on string" {
        val engine = klangScript()

        val error = shouldThrow<TypeError> {
            engine.execute("\"hello\".length")
        }

        error.errorType shouldBe "TypeError"
        error.message shouldContain "Cannot access property"
    }

    // ============================================================
    // Edge Cases - Function Calls
    // ============================================================

    "TypeError - calling null" {
        val engine = klangScript()

        val error = shouldThrow<TypeError> {
            engine.execute("null()")
        }

        error.errorType shouldBe "TypeError"
        error.message shouldContain "Cannot call non-function"
    }

    "TypeError - calling boolean" {
        val engine = klangScript()

        val error = shouldThrow<TypeError> {
            engine.execute("true()")
        }

        error.errorType shouldBe "TypeError"
        error.message shouldContain "Cannot call non-function"
    }

    "TypeError - calling object" {
        val engine = klangScript()

        val error = shouldThrow<TypeError> {
            engine.execute("let obj = { a: 1 }\nobj()")
        }

        error.errorType shouldBe "TypeError"
        error.message shouldContain "Cannot call non-function"
    }

    // ============================================================
    // Edge Cases - Complex Scenarios
    // ============================================================

    "ReferenceError - undefined variable in object literal" {
        val engine = klangScript()

        val error = shouldThrow<ReferenceError> {
            engine.execute("{ a: undefinedVar }")
        }

        error.symbolName shouldBe "undefinedVar"
    }

    "TypeError - nested operations with mixed types" {
        val engine = klangScript()

        val error = shouldThrow<TypeError> {
            engine.execute("(5 + 3) * \"hello\"")
        }

        error.operation shouldBe "MULTIPLY"
    }

    "ReferenceError - undefined in chained calls" {
        val builder = KlangScript.builder()
        builder.registerNativeFunction("process") { x -> x.first() }

        val engine = builder.build()

        val error = shouldThrow<ReferenceError> {
            engine.execute(
                """
                    let obj = { method: (x) => x }
                    obj.method(missingVar)
                """.trimIndent()
            )
        }

        error.symbolName shouldBe "missingVar"
    }
})

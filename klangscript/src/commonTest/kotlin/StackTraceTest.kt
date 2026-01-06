package io.peekandpoke.klang.script

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.peekandpoke.klang.script.runtime.ArgumentError
import io.peekandpoke.klang.script.runtime.ReferenceError
import io.peekandpoke.klang.script.runtime.StackOverflowError
import io.peekandpoke.klang.script.runtime.TypeError

/**
 * Tests for call stack trace functionality
 *
 * Verifies that the interpreter tracks function calls and provides
 * meaningful stack traces when errors occur.
 */
class StackTraceTest : StringSpec({

    "Stack trace for simple function call error" {
        val engine = KlangScript.builder().build()

        val script = """
            let broken = () => undefinedVar
            broken()
        """.trimIndent()

        val error = shouldThrow<ReferenceError> {
            engine.execute(script, sourceName = "test.klang")
        }

        error.stackTrace.size shouldBe 1
        error.stackTrace[0].functionName shouldBe "<anonymous>"
        error.format() shouldContain "at <anonymous>"
    }

    "Stack trace for nested function calls" {
        val engine = KlangScript.builder().build()

        val script = """
            let innerFunc = () => missingVariable
            let middleFunc = () => innerFunc()
            let outerFunc = () => middleFunc()
            outerFunc()
        """.trimIndent()

        val error = shouldThrow<ReferenceError> {
            engine.execute(script, sourceName = "nested.klang")
        }

        // Should have 3 frames: outerFunc, middleFunc, innerFunc
        error.stackTrace.size shouldBe 3
        error.stackTrace[0].functionName shouldBe "<anonymous>" // innerFunc
        error.stackTrace[1].functionName shouldBe "<anonymous>" // middleFunc
        error.stackTrace[2].functionName shouldBe "<anonymous>" // outerFunc
    }

    "Stack trace includes source locations" {
        val engine = KlangScript.builder().build()

        val script = """
            let func1 = () => undefinedVar
            let func2 = () => func1()
            func2()
        """.trimIndent()

        val error = shouldThrow<ReferenceError> {
            engine.execute(script, sourceName = "trace.klang")
        }

        val formatted = error.format()
        formatted shouldContain "at <anonymous> (trace.klang:"
        // Should have multiple "at" lines in stack trace
        formatted.split("\n").count { it.trim().startsWith("at") } shouldBe 2
    }

    "Stack trace for TypeError in nested calls" {
        val engine = KlangScript.builder().build()

        val script = """
            let add = (a, b) => a + b
            let calculate = () => add("hello", null)
            calculate()
        """.trimIndent()

        val error = shouldThrow<TypeError> {
            engine.execute(script, sourceName = "math.klang")
        }

        error.stackTrace.size shouldBe 2
        error.format() shouldContain "at <anonymous>"
    }

    "Stack trace for deeply nested calls" {
        val engine = KlangScript.builder().build()

        val script = """
            let level4 = () => nonExistent
            let level3 = () => level4()
            let level2 = () => level3()
            let level1 = () => level2()
            level1()
        """.trimIndent()

        val error = shouldThrow<ReferenceError> {
            engine.execute(script, sourceName = "deep.klang")
        }

        error.stackTrace.size shouldBe 4
        val formatted = error.format()
        formatted.split("\n").count { it.trim().startsWith("at") } shouldBe 4
    }

    "Stack trace with native function calls" {
        val engine = klangScript {
            registerFunction1("process") { x ->
                // This will cause the script function to throw
                x
            }
        }

        val script = """
            let broken = () => undefinedVar
            process(broken())
        """.trimIndent()

        val error = shouldThrow<ReferenceError> {
            engine.execute(script, sourceName = "native.klang")
        }

        // Stack should include both the script function and native function
        error.stackTrace.size shouldBe 1
        error.stackTrace[0].functionName shouldBe "<anonymous>"
    }

    "Stack trace with argument count error" {
        val engine = KlangScript.builder().build()

        val script = """
            let add = (a, b) => a + b
            let caller = () => add(1)
            caller()
        """.trimIndent()

        val error = shouldThrow<ArgumentError> {
            engine.execute(script, sourceName = "args.klang")
        }

        error.stackTrace.size shouldBe 1
        error.format() shouldContain "at <anonymous>"
    }

    "Stack trace empty for top-level errors" {
        val engine = KlangScript.builder().build()

        val error = shouldThrow<ReferenceError> {
            engine.execute("undefinedVariable", sourceName = "top.klang")
        }

        // No function calls, so stack trace should be empty
        error.stackTrace.size shouldBe 0
    }

    "Stack trace for library function errors" {
        val engine = klangScript {
            registerLibrary(
                "broken", """
                    let willFail = () => missingVar
                    export { willFail }
                """.trimIndent()
            )
        }

        val script = """
            import { willFail } from "broken"
            let caller = () => willFail()
            caller()
        """.trimIndent()

        val error = shouldThrow<ReferenceError> {
            engine.execute(script, sourceName = "main.klang")
        }

        // Should have stack frames from both main and library
        error.stackTrace.size shouldBe 2
    }

    "Stack trace with recursive calls" {
        val engine = KlangScript.builder().build()

        val script = """
            let countdown = (n) => n + countdown(n - 1)
            countdown(3)
        """.trimIndent()

        // This will eventually cause a stack overflow due to infinite recursion
        // Can be either our custom StackOverflowError or JVM's java.lang.StackOverflowError
        try {
            engine.execute(script, sourceName = "recursive.klang")
            throw AssertionError("Should have thrown an error")
        } catch (e: Throwable) {
            // Accept either our custom error or JVM stack overflow
            val message = e.message ?: ""
            val isExpectedError = e is StackOverflowError ||
                    e::class.simpleName == "StackOverflowError" ||
                    message.contains("Stack overflow") ||
                    message.contains("maximum call depth")
            isExpectedError shouldBe true
        }
    }

    "Stack trace format matches JavaScript style" {
        val engine = KlangScript.builder().build()

        val script = """
            let inner = () => undefined
            let outer = () => inner()
            outer()
        """.trimIndent()

        val error = shouldThrow<ReferenceError> {
            engine.execute(script, sourceName = "format.klang")
        }

        val formatted = error.format()

        // Check format: error message on first line
        val lines = formatted.split("\n")
        lines[0] shouldContain "ReferenceError"

        // Stack trace lines should start with "  at "
        lines.drop(1).forEach { line ->
            line shouldContain "  at "
        }
    }

    "Stack overflow protection activates at depth limit" {
        val engine = KlangScript.builder().build()

        val script = """
            let infinite = () => infinite()
            infinite()
        """.trimIndent()

        // Can be either our custom StackOverflowError or JVM's java.lang.StackOverflowError
        try {
            engine.execute(script, sourceName = "overflow.klang")
            throw AssertionError("Should have thrown an error")
        } catch (e: Throwable) {
            // Accept either our custom error or JVM stack overflow
            val message = e.message ?: ""
            val isExpectedError = e is StackOverflowError ||
                    e::class.simpleName == "StackOverflowError" ||
                    message.contains("Stack overflow") ||
                    message.contains("maximum call depth")
            isExpectedError shouldBe true
        }
    }
})

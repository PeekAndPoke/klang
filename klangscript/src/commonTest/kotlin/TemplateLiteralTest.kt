package io.peekandpoke.klang.script

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.script.runtime.NullValue
import io.peekandpoke.klang.script.runtime.StringValue

/**
 * Tests for template literal interpolation:
 * - `Hello ${name}!` style backtick strings
 * - Single and multiple interpolations
 * - Nested expressions
 * - No interpolation (plain backtick strings still work)
 */
@Suppress("IllegalEscapeSequence")
class TemplateLiteralTest : StringSpec({

    // ============================================================
    // Plain backtick strings (no interpolation)
    // ============================================================

    "template: plain backtick string without interpolation still works" {
        val engine = klangScript()
        val result = engine.execute("`hello world`")
        (result as StringValue).value shouldBe "hello world"
    }

    "template: empty backtick string" {
        val engine = klangScript()
        val result = engine.execute("``")
        (result as StringValue).value shouldBe ""
    }

    "template: backtick string with newlines" {
        val engine = klangScript()
        val result = engine.execute("`line1\\nline2`")
        (result as StringValue).value shouldBe "line1\nline2"
    }

    // ============================================================
    // Template literal interpolation
    // ============================================================

    "template: single interpolation of variable" {
        val engine = klangScript()
        val dollar = "\$"
        val result = engine.execute(
            """
            let name = "World"
            `Hello, $dollar{name}!`
            """.trimIndent()
        )
        (result as StringValue).value shouldBe "Hello, World!"
    }

    "template: interpolation at start of string" {
        val engine = klangScript()
        val dollar = "\$"
        val result = engine.execute(
            """
            let name = "Alice"
            `$dollar{name} is here`
            """.trimIndent()
        )
        (result as StringValue).value shouldBe "Alice is here"
    }

    "template: interpolation at end of string" {
        val engine = klangScript()
        val dollar = "\$"
        val result = engine.execute(
            """
            let name = "Bob"
            `Hello $dollar{name}`
            """.trimIndent()
        )
        (result as StringValue).value shouldBe "Hello Bob"
    }

    "template: multiple interpolations" {
        val engine = klangScript()
        val dollar = "\$"
        val result = engine.execute(
            """
            let first = "John"
            let last = "Doe"
            `$dollar{first} $dollar{last}`
            """.trimIndent()
        )
        (result as StringValue).value shouldBe "John Doe"
    }

    "template: arithmetic expression in interpolation" {
        val engine = klangScript()
        val dollar = "\$"
        val result = engine.execute(
            """
            let a = 40
            let b = 2
            `The answer is $dollar{a + b}`
            """.trimIndent()
        )
        (result as StringValue).value shouldBe "The answer is 42"
    }

    "template: number interpolation" {
        val engine = klangScript()
        val dollar = "\$"
        val result = engine.execute(
            """
            let x = 42
            `x = $dollar{x}`
            """.trimIndent()
        )
        (result as StringValue).value shouldBe "x = 42"
    }

    "template: boolean interpolation" {
        val engine = klangScript()
        val dollar = "\$"
        val result = engine.execute(
            """
            let flag = true
            `flag is $dollar{flag}`
            """.trimIndent()
        )
        (result as StringValue).value shouldBe "flag is true"
    }

    "template: null interpolation" {
        val engine = klangScript()
        val dollar = "\$"
        val result = engine.execute(
            """
            let x = null
            `x is $dollar{x}`
            """.trimIndent()
        )
        (result as StringValue).value shouldBe "x is null"
    }

    "template: function call in interpolation" {
        val engine = klangScript {
            registerFunctionRaw("greet") { args, _ ->
                val name = (args[0] as StringValue).value
                StringValue("Hello, $name")
            }
        }
        val dollar = "\$"
        val result = engine.execute(
            """
            `$dollar{greet("World")} from template`
            """.trimIndent()
        )
        (result as StringValue).value shouldBe "Hello, World from template"
    }

    "template: only interpolation, no surrounding text" {
        val engine = klangScript()
        val dollar = "\$"
        val result = engine.execute(
            """
            let x = "pure"
            `$dollar{x}`
            """.trimIndent()
        )
        (result as StringValue).value shouldBe "pure"
    }

    "template: complex expression with comparison" {
        val engine = klangScript()
        val dollar = "\$"
        val result = engine.execute(
            """
            let x = 10
            `x > 5 is $dollar{x > 5}`
            """.trimIndent()
        )
        (result as StringValue).value shouldBe "x > 5 is true"
    }

    "template: works in let declaration" {
        val engine = klangScript()
        val dollar = "\$"
        val result = engine.execute(
            """
            let name = "KlangScript"
            let greeting = `Welcome to $dollar{name}!`
            greeting
            """.trimIndent()
        )
        (result as StringValue).value shouldBe "Welcome to KlangScript!"
    }

    "template: works in function argument" {
        val received = mutableListOf<String>()
        val engine = klangScript {
            registerFunctionRaw("log") { args, _ ->
                received.add((args[0] as StringValue).value)
                NullValue
            }
        }
        val dollar = "\$"
        engine.execute(
            """
            let version = "1.0"
            log(`Version $dollar{version} loaded`)
            """.trimIndent()
        )
        received shouldBe listOf("Version 1.0 loaded")
    }

    "template: works inside if expression" {
        val engine = klangScript()
        val dollar = "\$"
        val result = engine.execute(
            """
            let score = 85
            let grade = if (score >= 90) { `A: $dollar{score}` } else { `B: $dollar{score}` }
            grade
            """.trimIndent()
        )
        (result as StringValue).value shouldBe "B: 85"
    }

    "template: works inside while loop" {
        val results = mutableListOf<String>()
        val engine = klangScript {
            registerFunctionRaw("log") { args, _ ->
                results.add((args[0] as StringValue).value)
                NullValue
            }
        }
        val dollar = "\$"
        engine.execute(
            """
            let i = 1
            while (i <= 3) {
                log(`item $dollar{i}`)
                i = i + 1
            }
            """.trimIndent()
        )
        results shouldBe listOf("item 1", "item 2", "item 3")
    }

    "template: three interpolations" {
        val engine = klangScript()
        val dollar = "\$"
        val result = engine.execute(
            """
            let a = "one"
            let b = "two"
            let c = "three"
            `$dollar{a}-$dollar{b}-$dollar{c}`
            """.trimIndent()
        )
        (result as StringValue).value shouldBe "one-two-three"
    }

    // ============================================================
    // Brace-in-string edge cases (naive parser used to break here)
    // ============================================================

    "template: double-quoted string with braces inside interpolation" {
        val engine = klangScript()
        val dollar = "\$"
        val result = engine.execute(
            """
            let x = "{"
            `value: $dollar{x}`
            """.trimIndent()
        )
        (result as StringValue).value shouldBe "value: {"
    }

    "template: single-quoted string with braces inside interpolation" {
        val engine = klangScript()
        val dollar = "\$"
        val result = engine.execute(
            """
            let pick = (a, b) => a
            `result: $dollar{pick("{yes}", "{no}")}`
            """.trimIndent()
        )
        (result as StringValue).value shouldBe "result: {yes}"
    }

    "template: escaped quote inside interpolation string literal" {
        val engine = klangScript()
        val dollar = "\$"
        val result = engine.execute(
            """
            let s = "hello"
            `got: $dollar{s}`
            """.trimIndent()
        )
        (result as StringValue).value shouldBe "got: hello"
    }
})

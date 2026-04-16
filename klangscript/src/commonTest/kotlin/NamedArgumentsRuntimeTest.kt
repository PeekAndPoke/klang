package io.peekandpoke.klang.script

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.peekandpoke.klang.script.runtime.KlangScriptArgumentError
import io.peekandpoke.klang.script.runtime.NumberValue
import io.peekandpoke.klang.script.runtime.StringValue

/**
 * Phase 3 — runtime behaviour of named arguments.
 *
 * Covers:
 *   - named calls bind correctly to script arrow-function parameters
 *   - the all-or-nothing rule (mixing positional and named → error)
 *   - duplicate / unknown / missing named errors
 *   - native functions still accept positional unchanged; named → transitional error
 *     that will go away once Phase 5 ships the builder rewrite
 */
class NamedArgumentsRuntimeTest : StringSpec({

    // ── Script arrow functions: named binding ─────────────────────────────────

    "named call binds to arrow-function parameters in declaration order" {
        val engine = klangScript()
        val result = engine.execute("((x, y) => x + y)(x = 1, y = 2)")
        (result as NumberValue).value shouldBe 3.0
    }

    "named call accepts arguments in any order" {
        val engine = klangScript()
        val result = engine.execute("((x, y) => x - y)(y = 2, x = 10)")
        (result as NumberValue).value shouldBe 8.0
    }

    "named call with single parameter" {
        val engine = klangScript()
        val result = engine.execute("(x => x * 2)(x = 7)")
        (result as NumberValue).value shouldBe 14.0
    }

    "positional call still works unchanged after named-arg support" {
        val engine = klangScript()
        val result = engine.execute("((x, y) => x + y)(1, 2)")
        (result as NumberValue).value shouldBe 3.0
    }

    "named call against block-bodied arrow function" {
        val engine = klangScript()
        val source = """
            let makeGreeting = (greeting, target) => {
                return greeting + " " + target
            }
            makeGreeting(target = "world", greeting = "hi")
        """.trimIndent()
        val result = engine.execute(source)
        (result as StringValue).value shouldBe "hi world"
    }

    // ── Error cases ───────────────────────────────────────────────────────────

    "mixing positional and named at the same call site is rejected" {
        val engine = klangScript()
        val err = shouldThrow<KlangScriptArgumentError> {
            engine.execute("((x, y) => x)(1, y = 2)")
        }
        err.message!! shouldContain "all positional or all named"
    }

    "mixing named then positional is also rejected" {
        val engine = klangScript()
        val err = shouldThrow<KlangScriptArgumentError> {
            engine.execute("((x, y) => x)(x = 1, 2)")
        }
        err.message!! shouldContain "all positional or all named"
    }

    "duplicate named argument is rejected" {
        val engine = klangScript()
        val err = shouldThrow<KlangScriptArgumentError> {
            engine.execute("(x => x)(x = 1, x = 2)")
        }
        err.message!! shouldContain "Duplicate named argument"
        err.message!! shouldContain "x"
    }

    "unknown named argument is rejected with expected-list hint" {
        val engine = klangScript()
        val err = shouldThrow<KlangScriptArgumentError> {
            engine.execute("((a, b) => a + b)(a = 1, nope = 2)")
        }
        err.message!! shouldContain "unknown parameter"
        err.message!! shouldContain "nope"
        err.message!! shouldContain "a, b"
    }

    "missing required parameter in a named call is rejected" {
        val engine = klangScript()
        val err = shouldThrow<KlangScriptArgumentError> {
            engine.execute("((a, b) => a + b)(a = 1)")
        }
        err.message!! shouldContain "missing required parameter"
        err.message!! shouldContain "b"
    }

    "too many positional arguments still errors through the legacy path" {
        val engine = klangScript()
        val err = shouldThrow<KlangScriptArgumentError> {
            engine.execute("(x => x)(1, 2, 3)")
        }
        err.message!! shouldContain "1"
        err.message!! shouldContain "3"
    }

    // ── Native functions (Phase 3 transitional) ───────────────────────────────

    "native function accepts positional arguments unchanged" {
        val engine = klangScript()
        val result = engine.execute(
            """
            import * from "stdlib"
            Math.sqrt(16)
        """.trimIndent()
        )
        (result as NumberValue).value shouldBe 4.0
    }

    "native function rejects named arguments with transitional message" {
        val engine = klangScript()
        val err = shouldThrow<KlangScriptArgumentError> {
            engine.execute(
                """
                import * from "stdlib"
                Math.sqrt(x = 16)
            """.trimIndent()
            )
        }
        err.message!! shouldContain "does not yet support named arguments"
    }
})

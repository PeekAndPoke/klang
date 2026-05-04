package io.peekandpoke.klang.script

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.peekandpoke.klang.script.ast.ExportDeclaration
import io.peekandpoke.klang.script.ast.NumberLiteral
import io.peekandpoke.klang.script.builder.registerLibrary
import io.peekandpoke.klang.script.parser.KlangScriptParser
import io.peekandpoke.klang.script.runtime.KlangScriptAssignmentError
import io.peekandpoke.klang.script.runtime.NumberValue
import io.peekandpoke.klang.script.runtime.StringValue

/**
 * Tests for the export-declaration form: `export name = expr`
 *
 * Validates:
 * - Parsing
 * - Binding semantics (immutable, like const)
 * - Auto-export under the same name (equivalent to `const name = ...; export { name }`)
 * - Importable from another module
 * - Coexistence with the existing `export { a, b }` form
 */
class ExportDeclarationTest : StringSpec({

    // ── parsing ──────────────────────────────────────────────────────────────

    "should parse export declaration" {
        val program = KlangScriptParser.parse("""export bass = 42""")

        program.statements.size shouldBe 1
        val stmt = program.statements[0]
        stmt.shouldBeInstanceOf<ExportDeclaration>()
        stmt.name shouldBe "bass"
        stmt.initializer.shouldBeInstanceOf<NumberLiteral>()
    }

    "should parse export declaration with method-chain initializer" {
        val program = KlangScriptParser.parse(
            """
                export lead = note("a b c").gain(0.5)
            """.trimIndent()
        )

        program.statements.size shouldBe 1
        program.statements[0].shouldBeInstanceOf<ExportDeclaration>()
    }

    "should parse multiple export declarations" {
        val program = KlangScriptParser.parse(
            """
                export a = 1
                export b = 2
                export c = 3
            """.trimIndent()
        )

        program.statements.size shouldBe 3
        program.statements.forEach { it.shouldBeInstanceOf<ExportDeclaration>() }
    }

    "should still parse existing export-block form (regression)" {
        val program = KlangScriptParser.parse(
            """
                let a = 1
                let b = 2
                export { a, b }
            """.trimIndent()
        )

        program.statements.size shouldBe 3
        // last statement should be the existing ExportStatement, not ExportDeclaration
        val last = program.statements.last()
        last::class.simpleName shouldBe "ExportStatement"
    }

    "should error if no '=' follows the export name" {
        shouldThrow<Exception> {
            KlangScriptParser.parse("""export bass""")
        }
    }

    // ── evaluation ───────────────────────────────────────────────────────────

    "should bind the value in the current scope" {
        val engine = klangScript()

        engine.execute("""export answer = 42""")
        val result = engine.execute("""answer""")

        result shouldBe NumberValue(42.0)
    }

    "should be immutable like const (assignment throws)" {
        val engine = klangScript()

        engine.execute("""export answer = 42""")

        shouldThrow<KlangScriptAssignmentError> {
            engine.execute("""answer = 99""")
        }
    }

    // ── as a library export ──────────────────────────────────────────────────

    "should be importable from another module" {
        val engine = klangScript {
            registerLibrary(
                "song", """
                    export bass = 42
                """.trimIndent()
            )
        }

        val result = engine.execute(
            """
                import { bass } from "song"
                bass
            """.trimIndent()
        )

        result shouldBe NumberValue(42.0)
    }

    "should expose multiple named parts" {
        val engine = klangScript {
            registerLibrary(
                "song", """
                    export lead = "lead-pattern"
                    export bass = "bass-pattern"
                    export drums = "drums-pattern"
                """.trimIndent()
            )
        }

        val result = engine.execute(
            """
                import { lead, bass, drums } from "song"
                lead
            """.trimIndent()
        )

        result shouldBe StringValue("lead-pattern")
    }

    "should support importing a single named part" {
        val engine = klangScript {
            registerLibrary(
                "song", """
                    let internalHelper = (x) => x * 2
                    export bass = internalHelper(21)
                """.trimIndent()
            )
        }

        val result = engine.execute(
            """
                import { bass } from "song"
                bass
            """.trimIndent()
        )

        result shouldBe NumberValue(42.0)
    }

    "should keep non-exported declarations private" {
        val engine = klangScript {
            registerLibrary(
                "song", """
                    let internalHelper = (x) => x * 2
                    export bass = 1
                """.trimIndent()
            )
        }

        // internalHelper is not exported — importing it must fail
        try {
            engine.execute(
                """
                    import { internalHelper } from "song"
                """.trimIndent()
            )
            error("Should have thrown")
        } catch (e: RuntimeException) {
            e.message shouldBe "Cannot import non-exported symbols: internalHelper"
        }
    }

    "should work alongside the existing export-block form" {
        // A library mixing both forms — the new declaration form and the old block form.
        val engine = klangScript {
            registerLibrary(
                "song", """
                    let oldStyle = 1
                    export newStyle = 2
                    export { oldStyle }
                """.trimIndent()
            )
        }

        val result = engine.execute(
            """
                import { oldStyle, newStyle } from "song"
                oldStyle + newStyle
            """.trimIndent()
        )

        result shouldBe NumberValue(3.0)
    }

    "should support a song-shaped library: parts plus an assembled 'song' export" {
        val engine = klangScript {
            registerLibrary(
                "der_schmetterling", """
                    export lead = "lead-line"
                    export bass = "bass-line"
                    export song = lead + " | " + bass
                """.trimIndent()
            )
        }

        val result = engine.execute(
            """
                import { song } from "der_schmetterling"
                song
            """.trimIndent()
        )

        result shouldBe StringValue("lead-line | bass-line")
    }

    // ── namespaced URIs (Projekt Klangbuch forward-compat) ───────────────────

    "should import from a namespaced URI like 'peekandpoke/<name>'" {
        val engine = klangScript {
            registerLibrary(
                "peekandpoke/der-schmetterling", """
                    export bass = 42
                """.trimIndent()
            )
        }

        val result = engine.execute(
            """
                import { bass } from "peekandpoke/der-schmetterling"
                bass
            """.trimIndent()
        )

        result shouldBe NumberValue(42.0)
    }

    "should import from a klang/builtin/* URI" {
        val engine = klangScript {
            registerLibrary(
                "klang/builtin/test-song", """
                    export song = "hello"
                """.trimIndent()
            )
        }

        val result = engine.execute(
            """
                import { song } from "klang/builtin/test-song"
                song
            """.trimIndent()
        )

        result shouldBe StringValue("hello")
    }

    "should produce a clean 'library not found' error for unknown namespaced URIs (forward-compat)" {
        val engine = klangScript()

        // A namespaced/versioned URI that doesn't resolve must NOT be a parse error.
        // It must be a clean runtime "library not found" error.
        try {
            engine.execute(
                """
                    import { song } from "peekandpoke/whatever@1.0"
                """.trimIndent()
            )
            error("Should have thrown")
        } catch (e: RuntimeException) {
            // Message format comes from Environment.loadLibrary — just confirm it's a library
            // resolution error, not a parse error.
            (e.message ?: "").let { msg ->
                msg.lowercase().let {
                    require("library" in it || "not found" in it) {
                        "Expected a library-not-found error, got: $msg"
                    }
                }
            }
        }
    }
})

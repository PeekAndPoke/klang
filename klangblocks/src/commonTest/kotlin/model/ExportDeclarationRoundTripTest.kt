package io.peekandpoke.klang.blocks.model

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

/**
 * Round-trip tests for the `export name = expr` declaration form.
 *
 * Source → AST → KBProgram → generated code → AST: the round-trip preserves
 * the structure and the regenerated code is byte-equivalent.
 */
class ExportDeclarationRoundTripTest : StringSpec({

    // ── basic forms ──────────────────────────────────────────────────────────

    "export with number initializer round-trips" {
        roundTrip("export bpm = 120").shouldRoundTripWithCode()
    }

    "export with string initializer round-trips" {
        roundTrip("""export name = "kick"""").shouldRoundTripWithCode()
    }

    "export with boolean initializer round-trips" {
        roundTrip("export muted = false").shouldRoundTripWithCode()
    }

    "export with nested chain initializer round-trips" {
        roundTrip("""export bass = note("c3 e3 g3").gain(0.5)""").shouldRoundTripWithCode()
    }

    "export with binary expression initializer round-trips" {
        roundTrip("export answer = 6 * 7").shouldRoundTripWithCode()
    }

    // ── structure ────────────────────────────────────────────────────────────

    "export produces KBExportStmt with correct name and value" {
        val result = roundTrip("export bpm = 120")
        val stmt = result.blocks.statements.single()
        stmt.shouldBeInstanceOf<KBExportStmt>()
        stmt.name shouldBe "bpm"
        stmt.value shouldBe KBNumberArg(120.0)
    }

    "export with nested chain produces KBExportStmt with KBNestedChainArg" {
        val result = roundTrip("""export bass = note("c3 e3 g3").gain(0.5)""")
        val stmt = result.blocks.statements.single() as KBExportStmt
        stmt.name shouldBe "bass"
        stmt.value.shouldBeInstanceOf<KBNestedChainArg>()
    }

    // ── code generation ──────────────────────────────────────────────────────

    "export with number generates correct code" {
        roundTrip("export bpm = 120").generatedCode shouldBe "export bpm = 120"
    }

    "export with string generates correct code" {
        roundTrip("""export name = "kick"""").generatedCode shouldBe """export name = "kick""""
    }

    // ── coexistence with other forms ─────────────────────────────────────────

    "export coexists with let and const in a program" {
        val src = """
            const bpm = 120
            let p = note("c3")
            export bass = note("c3").gain(0.5)
        """.trimIndent()
        roundTrip(src).shouldRoundTripWithCode()
    }

    "export coexists with the existing 'export { a }' form" {
        val src = """
            let a = 1
            export newStyle = 2
            export { a }
        """.trimIndent()
        // The existing export-block form is skipped by AstToKBlocks (ReturnStatement / ExportStatement → null),
        // so a full round-trip on the regenerated code drops the `export { a }` line. We assert only that
        // the new export-declaration form survives the trip and the resulting program is well-formed.
        val result = roundTrip(src)
        val exportStmts = result.blocks.statements.filterIsInstance<KBExportStmt>()
        exportStmts.size shouldBe 1
        exportStmts[0].name shouldBe "newStyle"
    }

    "multiple export declarations round-trip" {
        val src = """
            export lead = note("c3")
            export bass = note("c2")
            export drums = sound("bd")
        """.trimIndent()
        roundTrip(src).shouldRoundTripWithCode()
    }
})

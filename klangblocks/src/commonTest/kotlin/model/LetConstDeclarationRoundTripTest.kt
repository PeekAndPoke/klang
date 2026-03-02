package io.peekandpoke.klang.blocks.model

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

class LetConstDeclarationRoundTripTest : StringSpec({

    // ── let ───────────────────────────────────────────────────────────────────

    "let without initializer round-trips" {
        roundTrip("let x").shouldRoundTrip()
    }

    "let with number initializer round-trips" {
        roundTrip("let bpm = 120").shouldRoundTrip()
    }

    "let with string initializer round-trips" {
        roundTrip("""let pat = "c3 e3 g3"""").shouldRoundTrip()
    }

    "let with boolean initializer round-trips" {
        roundTrip("let muted = false").shouldRoundTrip()
    }

    "let with nested chain initializer round-trips" {
        roundTrip("""let p = note("c3").gain(0.5)""").shouldRoundTrip()
    }

    "let without initializer produces KBLetStmt with null value" {
        val result = roundTrip("let x")
        val stmt = result.blocks.statements.single()
        stmt.shouldBeInstanceOf<KBLetStmt>()
        (stmt as KBLetStmt).value shouldBe null
    }

    "let with number produces KBLetStmt with KBNumberArg" {
        val result = roundTrip("let bpm = 120")
        val stmt = result.blocks.statements.single() as KBLetStmt
        stmt.name shouldBe "bpm"
        stmt.value shouldBe KBNumberArg(120.0)
    }

    "let without initializer generates correct code" {
        roundTrip("let x").generatedCode shouldBe "let x"
    }

    "let with number generates correct code" {
        roundTrip("let bpm = 120").generatedCode shouldBe "let bpm = 120"
    }

    // ── const ─────────────────────────────────────────────────────────────────

    "const with number initializer round-trips" {
        roundTrip("const bpm = 120").shouldRoundTrip()
    }

    "const with string initializer round-trips" {
        roundTrip("""const name = "kick"""").shouldRoundTrip()
    }

    "const with nested chain initializer round-trips" {
        roundTrip("""const kick = sound("bd").gain(0.8)""").shouldRoundTrip()
    }

    "const with boolean initializer round-trips" {
        roundTrip("const debug = true").shouldRoundTrip()
    }

    "const produces KBConstStmt with correct name and value" {
        val result = roundTrip("const bpm = 120")
        val stmt = result.blocks.statements.single() as KBConstStmt
        stmt.name shouldBe "bpm"
        stmt.value shouldBe KBNumberArg(120.0)
    }

    "const generates correct code" {
        roundTrip("const bpm = 120").generatedCode shouldBe "const bpm = 120"
    }

    // ── mixed program ─────────────────────────────────────────────────────────

    "let and const together in a program round-trip" {
        roundTrip("const bpm = 120\nlet p = note(\"c3\")\np.gain(0.5)").shouldRoundTrip()
    }
})

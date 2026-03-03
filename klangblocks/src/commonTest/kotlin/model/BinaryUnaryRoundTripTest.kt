package io.peekandpoke.klang.blocks.model

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class BinaryUnaryRoundTripTest : StringSpec({

    // ── binary operations (KBBinaryArg) ───────────────────────────────────────

    "binary add round-trips" {
        roundTrip("note(x + 1)").shouldRoundTripWithCode()
    }

    "binary subtract round-trips" {
        roundTrip("note(x - 1)").shouldRoundTripWithCode()
    }

    "binary multiply round-trips" {
        roundTrip("note(x * 2)").shouldRoundTripWithCode()
    }

    "binary divide round-trips" {
        roundTrip("note(x / 2)").shouldRoundTripWithCode()
    }

    "binary equality round-trips" {
        roundTrip("note(x == y)").shouldRoundTripWithCode()
    }

    "binary not-equal round-trips" {
        roundTrip("note(x != y)").shouldRoundTripWithCode()
    }

    "binary less-than round-trips" {
        roundTrip("note(x < y)").shouldRoundTripWithCode()
    }

    "binary logical AND round-trips" {
        roundTrip("note(a && b)").shouldRoundTripWithCode()
    }

    "binary logical OR round-trips" {
        roundTrip("note(a || b)").shouldRoundTripWithCode()
    }

    "nested binary (right-associative, no parens needed) round-trips" {
        // x + y * 2 is unambiguous: multiply binds tighter
        roundTrip("note(x + y * 2)").shouldRoundTripWithCode()
    }

    "binary produces KBBinaryArg with correct structure" {
        val result = roundTrip("note(x + 1)")
        val block = result.blocks.statements
            .filterIsInstance<KBChainStmt>()
            .flatMap { it.steps }
            .filterIsInstance<KBCallBlock>()
            .first()
        block.args.single() shouldBe KBBinaryArg(KBIdentifierArg("x"), "+", KBNumberArg(1.0))
    }

    "binary generates correct code" {
        roundTrip("note(x + 1)").generatedCode shouldBe "note(x + 1)"
        roundTrip("note(a && b)").generatedCode shouldBe "note(a && b)"
    }

    // ── unary operations (KBUnaryArg) ─────────────────────────────────────────

    "unary negate round-trips" {
        roundTrip("note(-x)").shouldRoundTripWithCode()
    }

    "unary not round-trips" {
        roundTrip("note(!flag)").shouldRoundTripWithCode()
    }

    "unary plus round-trips" {
        roundTrip("note(+x)").shouldRoundTripWithCode()
    }

    "unary produces KBUnaryArg with correct structure" {
        val result = roundTrip("note(-x)")
        val block = result.blocks.statements
            .filterIsInstance<KBChainStmt>()
            .flatMap { it.steps }
            .filterIsInstance<KBCallBlock>()
            .first()
        block.args.single() shouldBe KBUnaryArg("-", KBIdentifierArg("x"))
    }

    "unary generates correct code" {
        roundTrip("note(-x)").generatedCode shouldBe "note(-x)"
        roundTrip("note(!flag)").generatedCode shouldBe "note(!flag)"
    }
})

package io.peekandpoke.klang.blocks.model

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class LiteralsRoundTripTest : StringSpec({

    // ── boolean ───────────────────────────────────────────────────────────────

    "bool true as argument round-trips" {
        roundTrip("note(true)").shouldRoundTrip()
    }

    "bool false as argument round-trips" {
        roundTrip("note(false)").shouldRoundTrip()
    }

    "bool in multi-arg position round-trips" {
        roundTrip("note(\"c3\", false)").shouldRoundTrip()
    }

    "bool true produces KBBoolArg(true)" {
        val result = roundTrip("note(true)")
        val block = result.blocks.statements
            .filterIsInstance<KBChainStmt>()
            .flatMap { it.steps }
            .filterIsInstance<KBCallBlock>()
            .first()
        block.args.single() shouldBe KBBoolArg(true)
    }

    "bool generates correct code" {
        roundTrip("note(true)").generatedCode shouldBe "note(true)"
        roundTrip("note(false)").generatedCode shouldBe "note(false)"
    }

    // ── null ──────────────────────────────────────────────────────────────────

    "null as argument round-trips" {
        roundTrip("note(null)").shouldRoundTrip()
    }

    "null alongside other args round-trips" {
        roundTrip("note(\"c3\", null)").shouldRoundTrip()
    }

    "null produces KBIdentifierArg(\"null\")" {
        val result = roundTrip("note(null)")
        val block = result.blocks.statements
            .filterIsInstance<KBChainStmt>()
            .flatMap { it.steps }
            .filterIsInstance<KBCallBlock>()
            .first()
        block.args.single() shouldBe KBIdentifierArg("null")
    }

    "null generates correct code" {
        roundTrip("note(null)").generatedCode shouldBe "note(null)"
    }
})

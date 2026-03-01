package io.peekandpoke.klang.blocks.model

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class IdentifierRoundTripTest : StringSpec({

    // ── identifier as argument (KBIdentifierArg) ──────────────────────────────

    "identifier as single argument round-trips" {
        roundTrip("note(myPattern)").shouldRoundTrip()
    }

    "identifier alongside literal args round-trips" {
        roundTrip("gain(myPattern, 0.5)").shouldRoundTrip()
    }

    "identifier in chained call round-trips" {
        roundTrip("note(myPattern).gain(vol)").shouldRoundTrip()
    }

    "identifier produces KBIdentifierArg" {
        val result = roundTrip("note(myPattern)")
        val block = result.blocks.statements
            .filterIsInstance<KBChainStmt>()
            .flatMap { it.steps }
            .filterIsInstance<KBCallBlock>()
            .first()
        block.args.single() shouldBe KBIdentifierArg("myPattern")
    }

    "identifier generates correct code" {
        roundTrip("note(myPattern)").generatedCode shouldBe "note(myPattern)"
    }

    // ── identifier as chain head (KBIdentifierItem) ───────────────────────────

    "identifier head with single chained call round-trips" {
        roundTrip("sine.range(0.25, 0.75)").shouldRoundTrip()
    }

    "identifier head with multiple chained calls round-trips" {
        roundTrip("sine.range(0.25, 0.75).slow(2)").shouldRoundTrip()
    }

    "identifier head produces KBIdentifierItem as first step" {
        val result = roundTrip("sine.range(0.25, 0.75)")
        val steps = result.blocks.statements
            .filterIsInstance<KBChainStmt>()
            .first().steps
        steps.first() shouldBe KBIdentifierItem("sine")
    }

    "identifier head generates correct code" {
        roundTrip("sine.range(0.25, 0.75)").generatedCode shouldBe "sine.range(0.25, 0.75)"
    }
})

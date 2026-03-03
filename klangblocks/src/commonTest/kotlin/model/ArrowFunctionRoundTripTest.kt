package io.peekandpoke.klang.blocks.model

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class ArrowFunctionRoundTripTest : StringSpec({

    // ── expression body ───────────────────────────────────────────────────────

    "single param arrow function round-trips" {
        roundTrip("note(x => x * 2)").shouldRoundTripWithCode()
    }

    "multi-param arrow function round-trips" {
        roundTrip("note((x, y) => x + y)").shouldRoundTripWithCode()
    }

    "no-param arrow function round-trips" {
        roundTrip("note(() => 42)").shouldRoundTripWithCode()
    }

    "arrow function used as chain argument round-trips" {
        roundTrip("note(\"c3\").apply(x => x * 2)").shouldRoundTripWithCode()
    }

    // ── block body ────────────────────────────────────────────────────────────

    "arrow function with block body round-trips" {
        // Body is stored as raw source text — round-trips via toSourceString reconstruction
        roundTrip("note(x => { return x * 2 })").shouldRoundTripWithCode()
    }

    // ── block model assertions ────────────────────────────────────────────────

    "single-param arrow produces KBArrowFunctionArg with correct params and body" {
        val result = roundTrip("note(x => x * 2)")
        val block = result.blocks.statements
            .filterIsInstance<KBChainStmt>()
            .flatMap { it.steps }
            .filterIsInstance<KBCallBlock>()
            .first()
        val arg = block.args.single() as KBArrowFunctionArg
        arg.params shouldBe listOf("x")
        arg.bodySource shouldBe "x * 2"
    }

    "multi-param arrow produces KBArrowFunctionArg with all params" {
        val result = roundTrip("note((x, y) => x + y)")
        val block = result.blocks.statements
            .filterIsInstance<KBChainStmt>()
            .flatMap { it.steps }
            .filterIsInstance<KBCallBlock>()
            .first()
        val arg = block.args.single() as KBArrowFunctionArg
        arg.params shouldBe listOf("x", "y")
        arg.bodySource shouldBe "x + y"
    }

    // ── generated code ────────────────────────────────────────────────────────

    "single-param arrow generates code without extra parentheses" {
        roundTrip("note(x => x * 2)").generatedCode shouldBe "note(x => x * 2)"
    }

    "multi-param arrow generates code with parentheses around params" {
        roundTrip("note((x, y) => x + y)").generatedCode shouldBe "note((x, y) => x + y)"
    }

    "no-param arrow generates code with empty parens" {
        roundTrip("note(() => 42)").generatedCode shouldBe "note(() => 42)"
    }
})

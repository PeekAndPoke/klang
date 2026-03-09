package io.peekandpoke.klang.blocks.model

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

/**
 * Round-trip tests for KlangBlocks-only chain items that have no direct
 * counterpart in a single AST node:
 *
 * - [KBStringLiteralItem]   — string literal as chain receiver: `"C4".transpose(1)`
 * - [KBIdentifierItem]      — identifier as chain receiver: `sine.range(0.25, 0.75)`
 * - [KBBlankLine]           — blank line between statements
 * - [KBNewlineHint]         — `\n  .` line break between chained calls
 * - [KBPocketLayout.VERTICAL] — multi-line argument layout inside a block
 */
class KBBlocksOnlyConstructsRoundTripTest : StringSpec({

    // ── KBStringLiteralItem ───────────────────────────────────────────────────

    "string literal chain head round-trips" {
        roundTrip(""""C4".transpose(1)""").shouldRoundTripWithCode()
    }

    "string literal head with longer chain round-trips" {
        roundTrip(""""C4".transpose(1).slow(2)""").shouldRoundTripWithCode()
    }

    "string literal head produces KBStringLiteralItem as first step" {
        val result = roundTrip(""""C4".transpose(1)""")
        val steps = result.blocks.statements
            .filterIsInstance<KBChainStmt>()
            .first().steps
        steps.first().shouldBeInstanceOf<KBStringLiteralItem>()
        (steps.first() as KBStringLiteralItem).value shouldBe "C4"
    }

    "string literal head generates correct code" {
        roundTrip(""""C4".transpose(1)""").generatedCode shouldBe """"C4".transpose(1)"""
    }

    // ── KBIdentifierItem ──────────────────────────────────────────────────────

    "identifier chain head round-trips" {
        roundTrip("sine.range(0.25, 0.75)").shouldRoundTripWithCode()
    }

    "identifier head with longer chain round-trips" {
        roundTrip("sine.range(0.25, 0.75).slow(2)").shouldRoundTripWithCode()
    }

    "identifier head produces KBIdentifierItem as first step" {
        val result = roundTrip("sine.range(0.25, 0.75)")
        val steps = result.blocks.statements
            .filterIsInstance<KBChainStmt>()
            .first().steps
        steps.first().shouldBeInstanceOf<KBIdentifierItem>()
        (steps.first() as KBIdentifierItem).name shouldBe "sine"
    }

    "identifier head generates correct code" {
        roundTrip("sine.range(0.25, 0.75)").generatedCode shouldBe "sine.range(0.25, 0.75)"
    }

    // ── KBBlankLine ───────────────────────────────────────────────────────────

    "blank line between two statements round-trips" {
        val src = "sound(\"bd\")\n\nnote(\"c3\")"
        roundTrip(src).shouldRoundTripWithCode()
    }

    "blank line between three statements round-trips" {
        val src = "sound(\"bd\")\n\nnote(\"c3\")\n\ngain(0.5)"
        roundTrip(src).shouldRoundTripWithCode()
    }

    "blank line is preserved as KBBlankLine node in block model" {
        val result = roundTrip("sound(\"bd\")\n\nnote(\"c3\")")
        val stmts = result.blocks.statements
        stmts.size shouldBe 3
        stmts[0].shouldBeInstanceOf<KBChainStmt>()
        stmts[1].shouldBeInstanceOf<KBBlankLine>()
        stmts[2].shouldBeInstanceOf<KBChainStmt>()
    }

    "blank line generates an empty line in the output" {
        val src = "sound(\"bd\")\n\nnote(\"c3\")"
        roundTrip(src).generatedCode shouldBe src
    }

    // ── KBNewlineHint ─────────────────────────────────────────────────────────

    "newline hint: multi-line chain round-trips" {
        roundTrip("sound(\"bd\")\n  .gain(0.5)").shouldRoundTripWithCode()
    }

    "newline hint: longer multi-line chain round-trips" {
        roundTrip("sound(\"bd\")\n  .gain(0.5)\n  .slow(2)").shouldRoundTripWithCode()
    }

    "newline hint: inserts KBNewlineHint between cross-line calls" {
        val result = roundTrip("sound(\"bd\")\n  .gain(0.5)")
        val steps = result.blocks.statements
            .filterIsInstance<KBChainStmt>()
            .first().steps
        // [KBCallBlock(sound), KBNewlineHint, KBCallBlock(gain)]
        steps.size shouldBe 3
        steps[0].shouldBeInstanceOf<KBCallBlock>()
        (steps[0] as KBCallBlock).funcName shouldBe "sound"
        steps[1].shouldBeInstanceOf<KBNewlineHint>()
        steps[2].shouldBeInstanceOf<KBCallBlock>()
        (steps[2] as KBCallBlock).funcName shouldBe "gain"
    }

    "newline hint: generates correct multi-line code" {
        roundTrip("sound(\"bd\")\n  .gain(0.5)").generatedCode shouldBe "sound(\"bd\")\n  .gain(0.5)"
    }

    // ── KBPocketLayout.VERTICAL ───────────────────────────────────────────────

    "vertical layout: block with multiple args round-trips" {
        roundTrip("sound(\n  \"bd\",\n  0.5\n)").shouldRoundTrip()
    }

    "vertical layout: produces KBPocketLayout.VERTICAL on the block" {
        val result = roundTrip("sound(\n  \"bd\",\n  0.5\n)")
        val block = result.blocks.statements
            .filterIsInstance<KBChainStmt>()
            .first().steps
            .filterIsInstance<KBCallBlock>()
            .first()
        block.pocketLayout shouldBe KBPocketLayout.VERTICAL
    }

    "vertical layout: generates args on separate lines" {
        roundTrip("sound(\n  \"bd\",\n  0.5\n)").generatedCode shouldBe "sound(\n  \"bd\",\n  0.5\n)"
    }
})

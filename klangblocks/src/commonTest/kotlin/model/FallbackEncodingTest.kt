package io.peekandpoke.klang.blocks.model

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf

/**
 * Tests for the opaque-fallback encoding of [ObjectLiteral] and [ArrayLiteral].
 *
 * These expressions are NOT AST-round-trippable: the fallback converts them to a
 * [KBStringArg] containing the serialised source text, so re-parsing the generated
 * code yields a [io.peekandpoke.klang.script.ast.StringLiteral], not the original node.
 *
 * Tests here verify the encoding is deterministic, parseable, and correct — without
 * asserting AST equality (which would always fail by design).
 */
class FallbackEncodingTest : StringSpec({

    // ── object literal (with numeric values — avoids inner-quote issues) ───────

    "object literal with numeric values encodes without crashing (step 2–4)" {
        // steps 2–4 must not throw; step 5/6 are intentionally skipped
        val result = roundTrip("note({ x: 1, y: 2 })")
        result.generatedCode shouldNotBe ""
    }

    "object literal is encoded as KBStringArg fallback" {
        val result = roundTrip("note({ x: 1, y: 2 })")
        val block = result.blocks.statements
            .filterIsInstance<KBChainStmt>()
            .flatMap { it.steps }
            .filterIsInstance<KBCallBlock>()
            .first()
        block.args.single().shouldBeInstanceOf<KBStringArg>()
    }

    "object literal fallback embeds the serialised source in the string arg" {
        val result = roundTrip("note({ x: 1, y: 2 })")
        val block = result.blocks.statements
            .filterIsInstance<KBChainStmt>()
            .flatMap { it.steps }
            .filterIsInstance<KBCallBlock>()
            .first()
        val arg = block.args.single() as KBStringArg
        arg.value shouldBe "{ x: 1, y: 2 }"
    }

    "object literal fallback generated code is parseable" {
        val result = roundTrip("note({ x: 1, y: 2 })")
        // Step 5 already succeeded if roundTrip() returned without throwing
        result.resultAst shouldNotBe null
    }

    // ── array literal ──────────────────────────────────────────────────────────

    "array literal encodes without crashing (step 2–4)" {
        val result = roundTrip("note([1, 2, 3])")
        result.generatedCode shouldNotBe ""
    }

    "array literal is encoded as KBStringArg fallback" {
        val result = roundTrip("note([1, 2, 3])")
        val block = result.blocks.statements
            .filterIsInstance<KBChainStmt>()
            .flatMap { it.steps }
            .filterIsInstance<KBCallBlock>()
            .first()
        block.args.single().shouldBeInstanceOf<KBStringArg>()
    }

    "array literal fallback embeds the serialised source in the string arg" {
        val result = roundTrip("note([1, 2, 3])")
        val block = result.blocks.statements
            .filterIsInstance<KBChainStmt>()
            .flatMap { it.steps }
            .filterIsInstance<KBCallBlock>()
            .first()
        val arg = block.args.single() as KBStringArg
        arg.value shouldBe "[1, 2, 3]"
    }

    "array literal fallback generated code is parseable" {
        val result = roundTrip("note([1, 2, 3])")
        result.resultAst shouldNotBe null
    }

    // ── if expression as function argument ─────────────────────────────────────

    "if expression arg is encoded as KBStringArg fallback" {
        val result = roundTrip("note(if (true) { 1 } else { 2 })")
        val block = result.blocks.statements
            .filterIsInstance<KBChainStmt>()
            .flatMap { it.steps }
            .filterIsInstance<KBCallBlock>()
            .first()
        block.args.single().shouldBeInstanceOf<KBStringArg>()
    }

    "if expression fallback embeds the correct source" {
        val result = roundTrip("note(if (true) { 1 } else { 2 })")
        val block = result.blocks.statements
            .filterIsInstance<KBChainStmt>()
            .flatMap { it.steps }
            .filterIsInstance<KBCallBlock>()
            .first()
        val arg = block.args.single() as KBStringArg
        arg.value shouldBe "if (true) { 1 } else { 2 }"
    }

    "if expression fallback generated code is parseable" {
        val result = roundTrip("note(if (true) { 1 } else { 2 })")
        result.resultAst shouldNotBe null
    }

    "if-else-if expression fallback embeds the correct source" {
        val result = roundTrip("note(if (x > 0) { 1 } else if (x < 0) { -1 } else { 0 })")
        val block = result.blocks.statements
            .filterIsInstance<KBChainStmt>()
            .flatMap { it.steps }
            .filterIsInstance<KBCallBlock>()
            .first()
        val arg = block.args.single() as KBStringArg
        arg.value shouldBe "if (x > 0) { 1 } else if (x < 0) { -1 } else { 0 }"
    }

    // ── template literal as function argument ──────────────────────────────────

    "template literal arg is encoded as KBStringArg fallback" {
        val result = roundTrip("note(`hello`)")
        val block = result.blocks.statements
            .filterIsInstance<KBChainStmt>()
            .flatMap { it.steps }
            .filterIsInstance<KBCallBlock>()
            .first()
        block.args.single().shouldBeInstanceOf<KBStringArg>()
    }

    "template literal with interpolation fallback embeds the correct source" {
        val result = roundTrip("note(`val: \${x}`)")
        val block = result.blocks.statements
            .filterIsInstance<KBChainStmt>()
            .flatMap { it.steps }
            .filterIsInstance<KBCallBlock>()
            .first()
        val arg = block.args.single() as KBStringArg
        arg.value shouldBe "`val: \${x}`"
    }

    "template literal fallback generated code is parseable" {
        val result = roundTrip("note(`val: \${x}`)")
        result.resultAst shouldNotBe null
    }
})

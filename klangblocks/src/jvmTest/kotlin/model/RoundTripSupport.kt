package io.peekandpoke.klang.blocks.model

import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.script.ast.Program
import io.peekandpoke.klang.script.parser.KlangScriptParser

// ── Result type ───────────────────────────────────────────────────────────────

/**
 * Holds the partial output of every step in the 6-step round-trip pipeline:
 *
 * 1. [source]        — the original KlangScript string (input)
 * 2. [originalAst]   — result of parsing [source]
 * 3. [blocks]        — result of converting [originalAst] via [AstToKBlocks]
 * 4. [generatedCode] — result of [KBProgram.toCode]
 * 5. [resultAst]     — result of re-parsing [generatedCode]
 * 6. [shouldRoundTrip] — asserts [resultAst] == [originalAst]
 *
 * Each field is available for deeper assertions in individual tests.
 */
data class RoundTripResult(
    val source: String,
    val originalAst: Program,
    val blocks: KBProgram,
    val generatedCode: String,
    val resultAst: Program,
) {
    /**
     * Step 6: asserts that [resultAst] equals [originalAst].
     *
     * On failure the error message includes both [source] and [generatedCode]
     * so the exact divergence point is immediately visible.
     *
     * Returns `this` so the call can be chained with further field-level assertions.
     */
    fun shouldRoundTrip(): RoundTripResult = apply {
        withClue(
            "\nRound-trip AST mismatch (step 6)" +
                    "\n  source   : $source" +
                    "\n  generated: $generatedCode\n"
        ) {
            resultAst shouldBe originalAst
        }
    }
}

// ── Entry point ───────────────────────────────────────────────────────────────

/**
 * Runs the full 6-step round-trip pipeline and returns a [RoundTripResult].
 *
 * Each step is wrapped so that failures identify exactly which step threw,
 * along with the source and (when available) the generated code.
 */
fun roundTrip(source: String): RoundTripResult {
    val originalAst = try {
        KlangScriptParser.parse(source)
    } catch (e: Exception) {
        throw AssertionError(
            "Round-trip step 2 failed — parse(source)\n  source: $source\n  error : ${e.message}", e
        )
    }

    val blocks = try {
        AstToKBlocks.convert(originalAst)
    } catch (e: Exception) {
        throw AssertionError(
            "Round-trip step 3 failed — AstToKBlocks.convert()\n  source: $source\n  error : ${e.message}", e
        )
    }

    val generatedCode = try {
        blocks.toCode()
    } catch (e: Exception) {
        throw AssertionError(
            "Round-trip step 4 failed — blocks.toCode()\n  source: $source\n  error : ${e.message}", e
        )
    }

    val resultAst = try {
        KlangScriptParser.parse(generatedCode)
    } catch (e: Exception) {
        throw AssertionError(
            "Round-trip step 5 failed — parse(generatedCode)" +
                    "\n  source   : $source" +
                    "\n  generated: $generatedCode" +
                    "\n  error    : ${e.message}", e
        )
    }

    return RoundTripResult(source, originalAst, blocks, generatedCode, resultAst)
}

package io.peekandpoke.klang.strudel.lang.parser

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

/**
 * Round-trip tests: String → MnPattern → String → MnPattern must produce the same tree.
 *
 * The invariant is:
 * ```
 * parse(render(parse(input))) == parse(input)
 * ```
 *
 * Note: the rendered string may differ from the input (e.g. normalised whitespace),
 * but re-parsing it must yield a structurally equal [MnPattern].
 */
class MnRoundTripSpec : StringSpec() {

    private fun parse(input: String): MnPattern = parseMiniNotationMnPattern(input)
    private fun render(pattern: MnPattern): String = MnRenderer.render(pattern)
    private fun roundTrip(input: String): MnPattern = parse(render(parse(input)))

    /**
     * Asserts that `parse(render(parse(input))) == parse(input)`.
     * Uses [stripSourceRanges] so position info doesn't affect structural equality.
     */
    private fun assertRoundTrip(input: String) {
        val original = parse(input).stripSourceRanges()
        val tripped = roundTrip(input).stripSourceRanges()
        tripped shouldBe original
    }

    init {

        // ── Atoms ────────────────────────────────────────────────────────────

        "single atom 'bd'" { assertRoundTrip("bd") }
        "single rest '~'" { assertRoundTrip("~") }
        "atom with colon 'c4:minor'" { assertRoundTrip("c4:minor") }
        "chord slash 'F/A'" { assertRoundTrip("F/A") }

        // ── Sequences ────────────────────────────────────────────────────────

        "sequence 'bd sd hh cp'" { assertRoundTrip("bd sd hh cp") }
        "sequence with rest 'bd ~ sd ~'" { assertRoundTrip("bd ~ sd ~") }

        // ── Stacks ───────────────────────────────────────────────────────────

        "stack 'bd, sd'" { assertRoundTrip("bd, sd") }
        "multi-layer stack 'a b, c d, e'" { assertRoundTrip("a b, c d, e") }

        // ── Groups ───────────────────────────────────────────────────────────

        "group '[bd sd]'" { assertRoundTrip("[bd sd]") }
        "nested group '[[bd sd] hh]'" { assertRoundTrip("[[bd sd] hh]") }
        "group with stack '[bd, sd]'" { assertRoundTrip("[bd, sd]") }
        "group in sequence '[bd sd] hh cp'" { assertRoundTrip("[bd sd] hh cp") }

        // ── Alternation ───────────────────────────────────────────────────────

        "alternation '<bd sd hh>'" { assertRoundTrip("<bd sd hh>") }
        "alternation in sequence '<bd sd> hh'" { assertRoundTrip("<bd sd> hh") }

        // ── Modifiers ────────────────────────────────────────────────────────

        "multiplier 'bd*2'" { assertRoundTrip("bd*2") }
        "divisor 'bd/2'" { assertRoundTrip("bd/2") }
        "weight 'bd@2'" { assertRoundTrip("bd@2") }
        "probability default 'bd?'" { assertRoundTrip("bd?") }
        "probability explicit 'bd?0.3'" { assertRoundTrip("bd?0.3") }
        "multiplier on group '[bd sd]*2'" { assertRoundTrip("[bd sd]*2") }
        "weight on group '[bd sd]@2'" { assertRoundTrip("[bd sd]@2") }
        "multiplier on alternation '<bd sd>*2'" { assertRoundTrip("<bd sd>*2") }

        // ── Euclidean ─────────────────────────────────────────────────────────

        "euclidean 'bd(3,8)'" { assertRoundTrip("bd(3,8)") }
        "euclidean with rotation 'bd(3,8,1)'" { assertRoundTrip("bd(3,8,1)") }

        // ── Bang ─────────────────────────────────────────────────────────────

        "bang 'bd!3' round-trips via rendered '[bd bd bd]'" {
            // parse("bd!3") = 3 atoms; render = "bd bd bd"; parse again = 3 atoms
            assertRoundTrip("bd!3")
        }

        "bang with modifier 'bd!2*2'" { assertRoundTrip("bd!2*2") }

        // ── Choice ────────────────────────────────────────────────────────────

        "choice 'a | b'" { assertRoundTrip("a | b") }
        "choice chain 'a | b | c'" { assertRoundTrip("a | b | c") }

        // ── Complex patterns ──────────────────────────────────────────────────

        "complex 'bd sd [hh cp] ~'" { assertRoundTrip("bd sd [hh cp] ~") }
        "complex 'bd*2 [sd hh]*0.5 ~ cp'" { assertRoundTrip("bd*2 [sd hh]*0.5 ~ cp") }
        "complex '<bd sd> [hh cp]@2'" { assertRoundTrip("<bd sd> [hh cp]@2") }
        "complex 'bd(3,8) sd'" { assertRoundTrip("bd(3,8) sd") }
        "complex 'a b, c d' stack" { assertRoundTrip("a b, c d") }
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────────

/**
 * Returns a copy of this [MnPattern] with all [MnNode.Atom.sourceRange] set to null.
 * This lets round-trip tests ignore position info when comparing trees.
 */
private fun MnPattern.stripSourceRanges(): MnPattern =
    MnPattern(layers.map { layer -> layer.map { it.stripSourceRanges() } })

private fun MnNode.stripSourceRanges(): MnNode = when (this) {
    is MnNode.Atom -> copy(sourceRange = null, sourceLine = 1, sourceColumn = null)
    is MnNode.Group -> copy(layers = layers.map { layer -> layer.map { it.stripSourceRanges() } })
    is MnNode.Alternation -> copy(items = items.map { it.stripSourceRanges() })
    is MnNode.Choice -> copy(options = options.map { it.stripSourceRanges() })
    is MnNode.Rest -> this
}

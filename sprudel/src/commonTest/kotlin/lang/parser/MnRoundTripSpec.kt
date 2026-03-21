package io.peekandpoke.klang.sprudel.lang.parser

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

/**
 * Round-trip tests: `parse(render(parse(input))) == parse(input)`.
 *
 * The rendered string may differ from the input (e.g. normalised whitespace),
 * but re-parsing it must yield a structurally equal [MnPattern] (source positions stripped).
 */
class MnRoundTripSpec : StringSpec() {

    private fun parse(input: String): MnPattern = parseMiniNotationMnPattern(input)
    private fun render(pattern: MnPattern): String = MnRenderer.render(pattern)
    private fun roundTrip(input: String): MnPattern = parse(render(parse(input)))

    private fun assertRoundTrip(input: String) {
        val original = parse(input).stripSourceRanges()
        val tripped = roundTrip(input).stripSourceRanges()
        tripped shouldBe original
    }

    init {

        // ── Empty / atoms ─────────────────────────────────────────────────────

        "empty string" { assertRoundTrip("") }
        "single atom 'bd'" { assertRoundTrip("bd") }
        "single rest '~'" { assertRoundTrip("~") }
        "atom with colon 'c4:minor'" { assertRoundTrip("c4:minor") }
        "chord slash 'F/A'" { assertRoundTrip("F/A") }

        // ── Sequences ────────────────────────────────────────────────────────

        "sequence 'bd sd hh cp'" { assertRoundTrip("bd sd hh cp") }
        "sequence with rest 'bd ~ sd ~'" { assertRoundTrip("bd ~ sd ~") }
        "long sequence 'a b c d e f'" { assertRoundTrip("a b c d e f") }

        // ── Stacks ───────────────────────────────────────────────────────────

        "stack 'bd, sd'" { assertRoundTrip("bd, sd") }
        "stack with sequences 'a b, c d'" { assertRoundTrip("a b, c d") }
        "three-layer stack 'a, b, c'" { assertRoundTrip("a, b, c") }
        "three-layer stack with sequences 'a b, c d, e f'" { assertRoundTrip("a b, c d, e f") }
        "stack with rest 'bd, ~'" { assertRoundTrip("bd, ~") }
        "asymmetric stack 'a b c, d'" { assertRoundTrip("a b c, d") }

        // ── Groups ───────────────────────────────────────────────────────────

        "group '[bd sd]'" { assertRoundTrip("[bd sd]") }
        "nested group '[[bd sd] hh]'" { assertRoundTrip("[[bd sd] hh]") }
        "group in sequence 'bd [sd hh] cp'" { assertRoundTrip("bd [sd hh] cp") }
        "multiple groups '[a b] [c d]'" { assertRoundTrip("[a b] [c d]") }
        "deeply nested '[[a b] [c d]]'" { assertRoundTrip("[[a b] [c d]]") }
        "group with single atom '[bd]'" { assertRoundTrip("[bd]") }

        // ── Stack inside group ────────────────────────────────────────────────

        "group with stack '[bd, sd]'" { assertRoundTrip("[bd, sd]") }
        "group with 3-layer stack '[a, b, c]'" { assertRoundTrip("[a, b, c]") }
        "group with sequence stack '[a b, c d]'" { assertRoundTrip("[a b, c d]") }
        "stacked group in sequence 'x [a, b] y'" { assertRoundTrip("x [a, b] y") }
        "nested stacked groups '[[a, b] [c, d]]'" { assertRoundTrip("[[a, b] [c, d]]") }

        // ── Alternation ───────────────────────────────────────────────────────

        "alternation '<bd sd hh>'" { assertRoundTrip("<bd sd hh>") }
        "alternation in sequence '<bd sd> hh'" { assertRoundTrip("<bd sd> hh") }
        "alternation with group '<[a b] c>'" { assertRoundTrip("<[a b] c>") }
        "alternation with rest '<bd ~>'" { assertRoundTrip("<bd ~>") }
        "multiple alternations '<a b> <c d>'" { assertRoundTrip("<a b> <c d>") }

        // ── Modifiers on atoms ────────────────────────────────────────────────

        "multiplier 'bd*2'" { assertRoundTrip("bd*2") }
        "multiplier fractional 'bd*0.5'" { assertRoundTrip("bd*0.5") }
        "divisor 'bd/2'" { assertRoundTrip("bd/2") }
        "weight 'bd@2'" { assertRoundTrip("bd@2") }
        "weight fractional 'bd@1.5'" { assertRoundTrip("bd@1.5") }
        "probability default 'bd?'" { assertRoundTrip("bd?") }
        "probability explicit 'bd?0.3'" { assertRoundTrip("bd?0.3") }

        // ── Modifiers on groups ───────────────────────────────────────────────

        "multiplier on group '[bd sd]*2'" { assertRoundTrip("[bd sd]*2") }
        "divisor on group '[bd sd]/2'" { assertRoundTrip("[bd sd]/2") }
        "weight on group '[bd sd]@2'" { assertRoundTrip("[bd sd]@2") }
        "probability on group '[bd sd]?0.5'" { assertRoundTrip("[bd sd]?0.5") }
        "multiplier on alternation '<bd sd>*2'" { assertRoundTrip("<bd sd>*2") }
        "weight on stacked group '[a, b]@2'" { assertRoundTrip("[a, b]@2") }

        // ── Euclidean ─────────────────────────────────────────────────────────

        "euclidean 'bd(3,8)'" { assertRoundTrip("bd(3,8)") }
        "euclidean with rotation 'bd(3,8,1)'" { assertRoundTrip("bd(3,8,1)") }
        "euclidean on group '[bd sd](3,8)'" { assertRoundTrip("[bd sd](3,8)") }

        // ── Bang / Repeat ─────────────────────────────────────────────────────

        "repeat 'bd!3' round-trips as Repeat node" { assertRoundTrip("bd!3") }
        "repeat 'bd!' default count 2" { assertRoundTrip("bd!") }
        "repeat with modifier 'bd!2*2'" { assertRoundTrip("bd!2*2") }
        "repeat in sequence 'bd!2 sd'" { assertRoundTrip("bd!2 sd") }

        // ── Choice ────────────────────────────────────────────────────────────

        "choice 'a | b'" { assertRoundTrip("a | b") }
        "choice chain 'a | b | c'" { assertRoundTrip("a | b | c") }
        "choice with modifier 'a | b*2'" { assertRoundTrip("a | b*2") }

        // ── Linebreaks ────────────────────────────────────────────────────────

        "linebreak: two lines 'bd sd\\nhh cp'" { assertRoundTrip("bd sd\nhh cp") }
        "linebreak: three lines 'a b\\nc d\\ne f'" { assertRoundTrip("a b\nc d\ne f") }
        "linebreak: consecutive linebreaks 'a b\\n\\nc d'" { assertRoundTrip("a b\n\nc d") }
        "linebreak: leading linebreak '\\nbd sd'" { assertRoundTrip("\nbd sd") }
        "linebreak: trailing linebreak 'bd sd\\n'" { assertRoundTrip("bd sd\n") }
        "linebreak: with modifiers 'bd*2\\nsd/2'" { assertRoundTrip("bd*2\nsd/2") }
        "linebreak: with groups 'bd [sd hh]\\ncp ~'" { assertRoundTrip("bd [sd hh]\ncp ~") }
        "linebreak inside group round-trips" { assertRoundTrip("[bd sd\nhh cp]") }
        "linebreak inside alternation round-trips" { assertRoundTrip("<c\nd>") }
        "linebreak: AST contains Linebreak node" {
            val p = parse("bd sd\nhh cp")
            p.items.any { it is MnNode.Linebreak } shouldBe true
        }
        "linebreak: splitOnLinebreaks produces two segments" {
            val p = parse("bd sd\nhh cp")
            val lines = p.splitOnLinebreaks()
            lines.size shouldBe 2
            lines[0].items.size shouldBe 2 // bd, sd
            lines[1].items.size shouldBe 2 // hh, cp
        }
        "linebreak: consecutive linebreaks produce empty middle segment" {
            val p = parse("a b\n\nc d")
            val lines = p.splitOnLinebreaks()
            lines.size shouldBe 3
            lines[1].items shouldBe emptyList()
        }

        // ── Complex combinations ──────────────────────────────────────────────

        "complex 'bd sd [hh cp] ~'" { assertRoundTrip("bd sd [hh cp] ~") }
        "complex 'bd*2 [sd hh]*0.5 ~ cp'" { assertRoundTrip("bd*2 [sd hh]*0.5 ~ cp") }
        "complex '<bd sd> [hh cp]@2'" { assertRoundTrip("<bd sd> [hh cp]@2") }
        "complex 'bd(3,8) sd'" { assertRoundTrip("bd(3,8) sd") }
        "complex stack 'bd sd, hh oh'" { assertRoundTrip("bd sd, hh oh") }
        "complex stacked groups '[bd sd], [hh oh]'" { assertRoundTrip("[bd sd], [hh oh]") }
        "complex with alternation in stack '<bd sd>, hh'" { assertRoundTrip("<bd sd>, hh") }
        "complex group in stack '[bd sd*2, hh oh]'" { assertRoundTrip("[bd sd*2, hh oh]") }
        "complex nested mods '[bd sd]@2 [hh cp]*2'" { assertRoundTrip("[bd sd]@2 [hh cp]*2") }
        "complex with choice 'a | b, c | d'" { assertRoundTrip("a | b, c | d") }
        "real drum pattern 'bd sd [hh hh] cp, hh oh hh oh'" {
            assertRoundTrip("bd sd [hh hh] cp, hh oh hh oh")
        }
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────────

private fun MnPattern.stripSourceRanges(): MnPattern =
    MnPattern(items.map { it.stripSourceRanges() })

private fun MnNode.stripSourceRanges(): MnNode = when (this) {
    is MnNode.Atom -> copy(sourceRange = null, sourceLine = 1, sourceColumn = null)
    is MnNode.Group -> copy(items = items.map { it.stripSourceRanges() })
    is MnNode.Alternation -> copy(items = items.map { it.stripSourceRanges() })
    is MnNode.Choice -> copy(options = options.map { it.stripSourceRanges() })
    is MnNode.Stack -> copy(layers = layers.map { layer -> layer.map { it.stripSourceRanges() } })
    is MnNode.Repeat -> copy(node = node.stripSourceRanges())
    is MnNode.Rest -> copy(sourceRange = null)
    is MnNode.Linebreak -> this
    is MnPattern -> MnPattern(items.map { it.stripSourceRanges() })
}

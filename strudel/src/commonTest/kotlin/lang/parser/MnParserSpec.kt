package io.peekandpoke.klang.strudel.lang.parser

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

/**
 * Tests for phase 1: [MiniNotationParser] → [MnPattern] structure.
 *
 * Covers correct AST construction for all mini-notation constructs.
 * Round-trip tests (parse → render → parse) are in [MnRoundTripSpec].
 */
class MnParserSpec : StringSpec() {

    fun parse(input: String): MnPattern = parseMiniNotationMnPattern(input)

    // Convenience helpers
    fun atom(value: String) = MnNode.Atom(value, sourceRange = null)
    fun rest() = MnNode.Rest

    init {

        // ── Atoms ────────────────────────────────────────────────────────────

        "empty string produces empty pattern" {
            parse("").layers shouldBe emptyList()
        }

        "single atom 'bd'" {
            val result = parse("bd")
            result.items.size shouldBe 1
            (result.items[0] as MnNode.Atom).value shouldBe "bd"
        }

        "single rest '~'" {
            val result = parse("~")
            result shouldBe MnPattern.of(MnNode.Rest)
        }

        // ── Sequences ────────────────────────────────────────────────────────

        "sequence 'bd sd'" {
            val result = parse("bd sd")
            result.items.size shouldBe 2
            (result.items[0] as MnNode.Atom).value shouldBe "bd"
            (result.items[1] as MnNode.Atom).value shouldBe "sd"
        }

        "sequence with rest 'bd ~ sd'" {
            val result = parse("bd ~ sd")
            result.items.size shouldBe 3
            (result.items[0] as MnNode.Atom).value shouldBe "bd"
            result.items[1] shouldBe MnNode.Rest
            (result.items[2] as MnNode.Atom).value shouldBe "sd"
        }

        // ── Stacks (comma-separated layers) ──────────────────────────────────

        "stack 'bd, sd' produces two layers" {
            val result = parse("bd, sd")
            result.layers.size shouldBe 2
            (result.layers[0][0] as MnNode.Atom).value shouldBe "bd"
            (result.layers[1][0] as MnNode.Atom).value shouldBe "sd"
        }

        "three-layer stack 'a, b, c'" {
            val result = parse("a, b, c")
            result.layers.size shouldBe 3
        }

        // ── Groups ───────────────────────────────────────────────────────────

        "group '[bd sd]' produces single Group node" {
            val result = parse("[bd sd]")
            result.items.size shouldBe 1
            val group = result.items[0] as MnNode.Group
            group.layers.size shouldBe 1
            group.layers[0].size shouldBe 2
            (group.layers[0][0] as MnNode.Atom).value shouldBe "bd"
            (group.layers[0][1] as MnNode.Atom).value shouldBe "sd"
        }

        "group with stack '[bd, sd]'" {
            val result = parse("[bd, sd]")
            val group = result.items[0] as MnNode.Group
            group.layers.size shouldBe 2
        }

        "nested group '[[bd sd] hh]'" {
            val result = parse("[[bd sd] hh]")
            val outer = result.items[0] as MnNode.Group
            outer.layers[0].size shouldBe 2
            outer.layers[0][0] as MnNode.Group
        }

        // ── Alternation ───────────────────────────────────────────────────────

        "alternation '<bd sd hh>'" {
            val result = parse("<bd sd hh>")
            val alt = result.items[0] as MnNode.Alternation
            alt.items.size shouldBe 3
            (alt.items[0] as MnNode.Atom).value shouldBe "bd"
            (alt.items[1] as MnNode.Atom).value shouldBe "sd"
            (alt.items[2] as MnNode.Atom).value shouldBe "hh"
        }

        // ── Multiplier (*) ────────────────────────────────────────────────────

        "multiplier 'bd*2'" {
            val result = parse("bd*2")
            val atom = result.items[0] as MnNode.Atom
            atom.value shouldBe "bd"
            atom.mods.multiplier shouldBe 2.0
        }

        "fractional multiplier 'bd*0.5'" {
            val result = parse("bd*0.5")
            val atom = result.items[0] as MnNode.Atom
            atom.mods.multiplier shouldBe 0.5
        }

        "multiple multipliers 'bd*2*3' compound to 6" {
            val result = parse("bd*2*3")
            val atom = result.items[0] as MnNode.Atom
            atom.mods.multiplier shouldBe 6.0
        }

        // ── Divisor (/) ───────────────────────────────────────────────────────

        "divisor 'bd/2'" {
            val result = parse("bd/2")
            val atom = result.items[0] as MnNode.Atom
            atom.value shouldBe "bd"
            atom.mods.divisor shouldBe 2.0
        }

        // ── Weight (@) ────────────────────────────────────────────────────────

        "weight 'bd@2'" {
            val result = parse("bd@2")
            val atom = result.items[0] as MnNode.Atom
            atom.mods.weight shouldBe 2.0
        }

        "weight on group '[bd sd]@2'" {
            val result = parse("[bd sd]@2")
            val group = result.items[0] as MnNode.Group
            group.mods.weight shouldBe 2.0
        }

        // ── Probability (?) ───────────────────────────────────────────────────

        "probability default '?' uses 0.5" {
            val result = parse("bd?")
            val atom = result.items[0] as MnNode.Atom
            atom.mods.probability shouldBe 0.5
        }

        "probability explicit 'bd?0.3'" {
            val result = parse("bd?0.3")
            val atom = result.items[0] as MnNode.Atom
            atom.mods.probability shouldBe 0.3
        }

        // ── Bang (!) ──────────────────────────────────────────────────────────

        "bang 'bd!3' expands to 3 atoms" {
            val result = parse("bd!3")
            result.items.size shouldBe 3
            result.items.all { (it as MnNode.Atom).value == "bd" } shouldBe true
        }

        "bang default 'bd!' expands to 2 atoms" {
            val result = parse("bd!")
            result.items.size shouldBe 2
        }

        "bang in sequence 'bd!2 sd' produces 3 steps" {
            val result = parse("bd!2 sd")
            result.items.size shouldBe 3
            (result.items[0] as MnNode.Atom).value shouldBe "bd"
            (result.items[1] as MnNode.Atom).value shouldBe "bd"
            (result.items[2] as MnNode.Atom).value shouldBe "sd"
        }

        "bang with subsequent modifier 'bd!2*2' produces Group" {
            val result = parse("bd!2*2")
            // !2 followed by *2 → Group([bd, bd])*2 as single item
            result.items.size shouldBe 1
            val group = result.items[0] as MnNode.Group
            group.mods.multiplier shouldBe 2.0
            group.layers[0].size shouldBe 2
        }

        "modifier before bang '*2!3' applies to each copy" {
            val result = parse("bd*2!3")
            result.items.size shouldBe 3
            result.items.all { (it as MnNode.Atom).mods.multiplier == 2.0 } shouldBe true
        }

        "bang inside alternation '<bd!2 sd>'" {
            val result = parse("<bd!2 sd>")
            val alt = result.items[0] as MnNode.Alternation
            alt.items.size shouldBe 3  // bd bd sd
            (alt.items[0] as MnNode.Atom).value shouldBe "bd"
            (alt.items[1] as MnNode.Atom).value shouldBe "bd"
            (alt.items[2] as MnNode.Atom).value shouldBe "sd"
        }

        // ── Choice (|) ────────────────────────────────────────────────────────

        "choice 'a | b' produces Choice node" {
            val result = parse("a | b")
            val choice = result.items[0] as MnNode.Choice
            choice.options.size shouldBe 2
            (choice.options[0] as MnNode.Atom).value shouldBe "a"
            (choice.options[1] as MnNode.Atom).value shouldBe "b"
        }

        "choice chain 'a | b | c' flattens to Choice([a,b,c])" {
            val result = parse("a | b | c")
            val choice = result.items[0] as MnNode.Choice
            choice.options.size shouldBe 3
        }

        "choice with modifier on right 'a | b*2'" {
            val result = parse("a | b*2")
            val choice = result.items[0] as MnNode.Choice
            choice.options.size shouldBe 2
            (choice.options[1] as MnNode.Atom).mods.multiplier shouldBe 2.0
        }

        // ── Euclidean ─────────────────────────────────────────────────────────

        "euclidean 'bd(3,8)'" {
            val result = parse("bd(3,8)")
            val atom = result.items[0] as MnNode.Atom
            atom.mods.euclidean shouldBe MnNode.Euclidean(3, 8, 0)
        }

        "euclidean with rotation 'bd(3,8,1)'" {
            val result = parse("bd(3,8,1)")
            val atom = result.items[0] as MnNode.Atom
            atom.mods.euclidean shouldBe MnNode.Euclidean(3, 8, 1)
        }

        // ── Source ranges ─────────────────────────────────────────────────────

        "source range for atom 'bd' starts at 0" {
            val result = parse("bd")
            val atom = result.items[0] as MnNode.Atom
            atom.sourceRange shouldBe 0..1
            atom.sourceLine shouldBe 1
            atom.sourceColumn shouldBe 1
        }

        "source range for second atom in 'bd sd'" {
            val result = parse("bd sd")
            val atom2 = result.items[1] as MnNode.Atom
            atom2.sourceRange shouldBe 3..4
            atom2.sourceLine shouldBe 1
            atom2.sourceColumn shouldBe 4
        }

        // ── Slash-in-literal passthrough ──────────────────────────────────────

        "chord 'F/A' is treated as a single literal atom" {
            val result = parse("F/A")
            result.items.size shouldBe 1
            (result.items[0] as MnNode.Atom).value shouldBe "F/A"
        }

        "scale 'C4:minor' is a single atom (colon not special)" {
            val result = parse("C4:minor")
            result.items.size shouldBe 1
            (result.items[0] as MnNode.Atom).value shouldBe "C4:minor"
        }
    }
}

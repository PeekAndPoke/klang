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

    init {

        // ── Atoms ────────────────────────────────────────────────────────────

        "empty string produces empty pattern" {
            parse("").items shouldBe emptyList()
        }

        "single atom 'bd'" {
            val result = parse("bd")
            result.items.size shouldBe 1
            (result.items[0] as MnNode.Atom).value shouldBe "bd"
        }

        "single rest '~'" {
            val result = parse("~")
            result.items.size shouldBe 1
            result.items[0] shouldBe MnNode.Rest(sourceRange = 0 until 1)
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
            (result.items[1] as MnNode.Rest).sourceRange shouldBe (3 until 4)
            (result.items[2] as MnNode.Atom).value shouldBe "sd"
        }

        // ── Stacks (MnNode.Stack) ─────────────────────────────────────────────

        "stack 'bd, sd' produces a root Stack node with two layers" {
            val result = parse("bd, sd")
            result.items.size shouldBe 1
            val stack = result.items[0] as MnNode.Stack
            stack.layers.size shouldBe 2
            (stack.layers[0][0] as MnNode.Atom).value shouldBe "bd"
            (stack.layers[1][0] as MnNode.Atom).value shouldBe "sd"
        }

        "three-layer stack 'a, b, c'" {
            val result = parse("a, b, c")
            val stack = result.items[0] as MnNode.Stack
            stack.layers.size shouldBe 3
        }

        "multi-element stack 'a b, c d'" {
            val result = parse("a b, c d")
            val stack = result.items[0] as MnNode.Stack
            stack.layers.size shouldBe 2
            stack.layers[0].size shouldBe 2
            stack.layers[1].size shouldBe 2
        }

        // ── Groups ───────────────────────────────────────────────────────────

        "group '[bd sd]' produces a flat Group node" {
            val result = parse("[bd sd]")
            result.items.size shouldBe 1
            val group = result.items[0] as MnNode.Group
            group.items.size shouldBe 2
            (group.items[0] as MnNode.Atom).value shouldBe "bd"
            (group.items[1] as MnNode.Atom).value shouldBe "sd"
        }

        "group with stack '[bd, sd]' produces Group containing Stack" {
            val result = parse("[bd, sd]")
            val group = result.items[0] as MnNode.Group
            group.items.size shouldBe 1
            val stack = group.items[0] as MnNode.Stack
            stack.layers.size shouldBe 2
            (stack.layers[0][0] as MnNode.Atom).value shouldBe "bd"
            (stack.layers[1][0] as MnNode.Atom).value shouldBe "sd"
        }

        "nested group '[[bd sd] hh]'" {
            val result = parse("[[bd sd] hh]")
            val outer = result.items[0] as MnNode.Group
            outer.items.size shouldBe 2
            outer.items[0] as MnNode.Group
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

        // ── Bang / Repeat (!) ─────────────────────────────────────────────────

        "bang 'bd!3' produces a single Repeat node" {
            val result = parse("bd!3")
            result.items.size shouldBe 1
            val repeat = result.items[0] as MnNode.Repeat
            repeat.count shouldBe 3
            (repeat.node as MnNode.Atom).value shouldBe "bd"
        }

        "bang default 'bd!' produces Repeat with count 2" {
            val result = parse("bd!")
            result.items.size shouldBe 1
            val repeat = result.items[0] as MnNode.Repeat
            repeat.count shouldBe 2
        }

        "bang in sequence 'bd!2 sd' produces Repeat + atom" {
            val result = parse("bd!2 sd")
            result.items.size shouldBe 2
            val repeat = result.items[0] as MnNode.Repeat
            repeat.count shouldBe 2
            (result.items[1] as MnNode.Atom).value shouldBe "sd"
        }

        "bang with subsequent modifier 'bd!2*2' produces Repeat with mods" {
            val result = parse("bd!2*2")
            result.items.size shouldBe 1
            val repeat = result.items[0] as MnNode.Repeat
            repeat.count shouldBe 2
            repeat.mods.multiplier shouldBe 2.0
        }

        "modifier before bang 'bd*2!3' produces Repeat of bd*2" {
            val result = parse("bd*2!3")
            result.items.size shouldBe 1
            val repeat = result.items[0] as MnNode.Repeat
            repeat.count shouldBe 3
            (repeat.node as MnNode.Atom).mods.multiplier shouldBe 2.0
        }

        "bang inside alternation '<bd!2 sd>' produces Repeat + atom in alt" {
            val result = parse("<bd!2 sd>")
            val alt = result.items[0] as MnNode.Alternation
            alt.items.size shouldBe 2
            alt.items[0] as MnNode.Repeat
            (alt.items[1] as MnNode.Atom).value shouldBe "sd"
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
            result.items.size shouldBe 1
            val choice = result.items[0] as MnNode.Choice
            choice.options.size shouldBe 3
            (choice.options[0] as MnNode.Atom).value shouldBe "a"
            (choice.options[1] as MnNode.Atom).value shouldBe "b"
            (choice.options[2] as MnNode.Atom).value shouldBe "c"
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

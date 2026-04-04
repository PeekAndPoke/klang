package io.peekandpoke.klang.sprudel.lang.parser

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.peekandpoke.klang.sprudel.EPSILON
import io.peekandpoke.klang.sprudel.lang.note

/**
 * Tests for the `{key=value ...}` attribute block syntax in mini-notation.
 *
 * Covers:
 * - Tokenization and parsing of attribute blocks
 * - Round-trip stability (parse → render → parse)
 * - Spaces around `=` handling
 * - Attributes on different node types (atoms, groups, etc.)
 * - Combination with other modifiers
 * - Error cases
 */
class MiniNotationAttrsSpec : StringSpec() {

    private fun parse(input: String): MnPattern = parseMiniNotationMnPattern(input)
    private fun render(pattern: MnPattern): String = MnRenderer.render(pattern)

    init {

        // ── Basic parsing ────────────────────────────────────────────────────

        "single attr on atom" {
            val result = parse("c4{g=0.5}")
            result.items.size shouldBe 1
            val atom = result.items[0].shouldBeInstanceOf<MnNode.Atom>()
            atom.value shouldBe "c4"
            atom.mods.attrs.entries shouldBe mapOf("g" to "0.5")
        }

        "multiple attrs on atom" {
            val result = parse("c4{g=0.5 pan=-0.3}")
            val atom = result.items[0].shouldBeInstanceOf<MnNode.Atom>()
            atom.mods.attrs.entries shouldBe mapOf("g" to "0.5", "pan" to "-0.3")
        }

        "spaces around equals" {
            val result = parse("c4{g = 0.5}")
            val atom = result.items[0].shouldBeInstanceOf<MnNode.Atom>()
            atom.mods.attrs.entries shouldBe mapOf("g" to "0.5")
        }

        "spaces around equals with multiple attrs" {
            val result = parse("c4{v = 0.8 pan = -0.5}")
            val atom = result.items[0].shouldBeInstanceOf<MnNode.Atom>()
            atom.mods.attrs.entries shouldBe mapOf("v" to "0.8", "pan" to "-0.5")
        }

        "string value attr" {
            val result = parse("bd{bank=casio}")
            val atom = result.items[0].shouldBeInstanceOf<MnNode.Atom>()
            atom.mods.attrs.entries shouldBe mapOf("bank" to "casio")
        }

        "compound colon value" {
            val result = parse("c4{adsr=0.01:0.1:0.5:0.3}")
            val atom = result.items[0].shouldBeInstanceOf<MnNode.Atom>()
            atom.mods.attrs.entries shouldBe mapOf("adsr" to "0.01:0.1:0.5:0.3")
        }

        "empty braces produce empty attrs" {
            val result = parse("c4{}")
            val atom = result.items[0].shouldBeInstanceOf<MnNode.Atom>()
            atom.mods.attrs.isEmpty shouldBe true
        }

        // ── On different node types ──────────────────────────────────────────

        "attrs on group" {
            val result = parse("[c4 e4]{l=2}")
            val group = result.items[0].shouldBeInstanceOf<MnNode.Group>()
            group.mods.attrs.entries shouldBe mapOf("l" to "2")
            group.items.size shouldBe 2
        }

        "attrs on rest" {
            val result = parse("~{v=0}")
            val rest = result.items[0].shouldBeInstanceOf<MnNode.Rest>()
            rest.mods.attrs.entries shouldBe mapOf("v" to "0")
        }

        "attrs on alternation" {
            val result = parse("<c4 e4>{v=0.5}")
            val alt = result.items[0].shouldBeInstanceOf<MnNode.Alternation>()
            alt.mods.attrs.entries shouldBe mapOf("v" to "0.5")
        }

        // ── Combined with other modifiers ────────────────────────────────────

        "attrs with multiplier" {
            val result = parse("c4*2{v=0.8}")
            val atom = result.items[0].shouldBeInstanceOf<MnNode.Atom>()
            atom.mods.multiplier shouldBe 2.0
            atom.mods.attrs.entries shouldBe mapOf("v" to "0.8")
        }

        "attrs with weight" {
            val result = parse("c4@3{l=2}")
            val atom = result.items[0].shouldBeInstanceOf<MnNode.Atom>()
            atom.mods.weight shouldBe 3.0
            atom.mods.attrs.entries shouldBe mapOf("l" to "2")
        }

        "attrs with probability" {
            val result = parse("c4?0.5{pan=0.3}")
            val atom = result.items[0].shouldBeInstanceOf<MnNode.Atom>()
            atom.mods.probability shouldBe 0.5
            atom.mods.attrs.entries shouldBe mapOf("pan" to "0.3")
        }

        "attrs with euclidean" {
            val result = parse("c4(3,8){v=0.6}")
            val atom = result.items[0].shouldBeInstanceOf<MnNode.Atom>()
            atom.mods.euclidean shouldBe MnNode.Euclidean(3, 8)
            atom.mods.attrs.entries shouldBe mapOf("v" to "0.6")
        }

        "attrs with multiple modifiers" {
            val result = parse("c4*2@3{g=0.5 pan=-0.5}")
            val atom = result.items[0].shouldBeInstanceOf<MnNode.Atom>()
            atom.mods.multiplier shouldBe 2.0
            atom.mods.weight shouldBe 3.0
            atom.mods.attrs.entries shouldBe mapOf("g" to "0.5", "pan" to "-0.5")
        }

        // ── In sequences ─────────────────────────────────────────────────────

        "attrs on individual notes in sequence" {
            val result = parse("c4{v=1.0} e4{v=0.3}")
            result.items.size shouldBe 2
            val c4 = result.items[0].shouldBeInstanceOf<MnNode.Atom>()
            val e4 = result.items[1].shouldBeInstanceOf<MnNode.Atom>()
            c4.mods.attrs.entries shouldBe mapOf("v" to "1.0")
            e4.mods.attrs.entries shouldBe mapOf("v" to "0.3")
        }

        "mixed attrs and plain notes" {
            val result = parse("bd{v=1.0} hh sd{pan=0.5}")
            result.items.size shouldBe 3
            val bd = result.items[0].shouldBeInstanceOf<MnNode.Atom>()
            val hh = result.items[1].shouldBeInstanceOf<MnNode.Atom>()
            val sd = result.items[2].shouldBeInstanceOf<MnNode.Atom>()
            bd.mods.attrs.entries shouldBe mapOf("v" to "1.0")
            hh.mods.attrs.isEmpty shouldBe true
            sd.mods.attrs.entries shouldBe mapOf("pan" to "0.5")
        }

        // ── Round-trip ───────────────────────────────────────────────────────

        "round-trip: single attr" {
            val rendered = render(parse("c4{g=0.5}"))
            rendered shouldBe "c4{g=0.5}"
        }

        "round-trip: multiple attrs" {
            val rendered = render(parse("c4{g=0.5 pan=-0.3}"))
            rendered shouldBe "c4{g=0.5 pan=-0.3}"
        }

        "round-trip: attrs with other mods" {
            val rendered = render(parse("c4*2{v=0.8}"))
            rendered shouldBe "c4*2{v=0.8}"
        }

        "round-trip: attrs on group" {
            val rendered = render(parse("[c4 e4]{l=2}"))
            rendered shouldBe "[c4 e4]{l=2}"
        }

        "round-trip: compound value" {
            val rendered = render(parse("c4{adsr=0.01:0.1:0.5:0.3}"))
            rendered shouldBe "c4{adsr=0.01:0.1:0.5:0.3}"
        }

        "round-trip: string value" {
            val rendered = render(parse("bd{bank=casio}"))
            rendered shouldBe "bd{bank=casio}"
        }

        "round-trip: spaces around equals normalize to no spaces" {
            val rendered = render(parse("c4{g = 0.5 pan = -0.3}"))
            rendered shouldBe "c4{g=0.5 pan=-0.3}"
        }

        "round-trip: empty braces are stripped" {
            val rendered = render(parse("c4{}"))
            rendered shouldBe "c4"
        }

        "round-trip: attrs in sequence" {
            val rendered = render(parse("bd{v=1.0} hh sd{pan=0.5}"))
            rendered shouldBe "bd{v=1.0} hh sd{pan=0.5}"
        }

        "round-trip: all mods plus attrs" {
            val rendered = render(parse("c4(3,8)*2/3?0.5@2{v=0.8 l=2}"))
            rendered shouldBe "c4(3,8)*2/3?0.5@2{v=0.8 l=2}"
        }

        // ── Error cases ──────────────────────────────────────────────────────

        "unclosed brace throws parse error" {
            val e = try {
                parse("c4{g=0.5")
                null
            } catch (e: MiniNotationParseException) {
                e
            }
            (e != null) shouldBe true
        }

        "missing equals throws parse error" {
            val e = try {
                parse("c4{g}")
                null
            } catch (e: MiniNotationParseException) {
                e
            }
            (e != null) shouldBe true
        }

        "missing value throws parse error" {
            val e = try {
                parse("c4{g=}")
                null
            } catch (e: MiniNotationParseException) {
                e
            }
            (e != null) shouldBe true
        }

        // ── Phase 2: Voice data verification ─────────────────────────────────

        "phase 2: gain attr sets voice data gain" {
            val pattern = parseToPattern("c4{g=0.5}")
            val events = pattern.queryArc(0.0, 1.0)
            events.size shouldBe 1
            events[0].data.gain shouldBe (0.5 plusOrMinus EPSILON)
        }

        "phase 2: velocity attr sets voice data velocity" {
            val pattern = parseToPattern("c4{v=0.7}")
            val events = pattern.queryArc(0.0, 1.0)
            events.size shouldBe 1
            events[0].data.velocity shouldBe (0.7 plusOrMinus EPSILON)
        }

        "phase 2: pan attr sets voice data pan" {
            val pattern = parseToPattern("c4{pan=-0.5}")
            val events = pattern.queryArc(0.0, 1.0)
            events.size shouldBe 1
            events[0].data.pan shouldBe (-0.5 plusOrMinus EPSILON)
        }

        "phase 2: legato attr sets voice data legato" {
            val pattern = parseToPattern("c4{l=2}")
            val events = pattern.queryArc(0.0, 1.0)
            events.size shouldBe 1
            events[0].data.legato shouldBe (2.0 plusOrMinus EPSILON)
        }

        "phase 2: orbit attr sets voice data cylinder" {
            val pattern = parseToPattern("c4{o=2}")
            val events = pattern.queryArc(0.0, 1.0)
            events.size shouldBe 1
            events[0].data.cylinder shouldBe 2
        }

        "phase 2: bank attr sets voice data bank" {
            val pattern = parseToPattern("c4{bank=casio}")
            val events = pattern.queryArc(0.0, 1.0)
            events.size shouldBe 1
            events[0].data.bank shouldBe "casio"
        }

        "phase 2: adsr compound attr sets all envelope fields" {
            val pattern = parseToPattern("c4{adsr=0.01:0.1:0.5:0.3}")
            val events = pattern.queryArc(0.0, 1.0)
            events.size shouldBe 1
            events[0].data.attack shouldBe (0.01 plusOrMinus EPSILON)
            events[0].data.decay shouldBe (0.1 plusOrMinus EPSILON)
            events[0].data.sustain shouldBe (0.5 plusOrMinus EPSILON)
            events[0].data.release shouldBe (0.3 plusOrMinus EPSILON)
        }

        "phase 2: postgain attr sets voice data postGain" {
            val pattern = parseToPattern("c4{pg=0.8}")
            val events = pattern.queryArc(0.0, 1.0)
            events.size shouldBe 1
            events[0].data.postGain shouldBe (0.8 plusOrMinus EPSILON)
        }

        "phase 2: multiple attrs applied together" {
            val pattern = parseToPattern("c4{g=0.5 pan=-0.3 v=0.8}")
            val events = pattern.queryArc(0.0, 1.0)
            events.size shouldBe 1
            events[0].data.gain shouldBe (0.5 plusOrMinus EPSILON)
            events[0].data.pan shouldBe (-0.3 plusOrMinus EPSILON)
            events[0].data.velocity shouldBe (0.8 plusOrMinus EPSILON)
        }

        "phase 2: unknown attr is silently ignored" {
            val pattern = parseToPattern("c4{zzz=1}")
            val events = pattern.queryArc(0.0, 1.0)
            events.size shouldBe 1
            // Note is present — the unknown attr didn't break anything
            events[0].data.note shouldBe "c4"
        }

        "phase 2: full alias names work" {
            val pattern = parseToPattern("c4{velocity=0.6 gain=0.4 legato=3 orbit=1 postgain=0.9}")
            val events = pattern.queryArc(0.0, 1.0)
            events.size shouldBe 1
            events[0].data.velocity shouldBe (0.6 plusOrMinus EPSILON)
            events[0].data.gain shouldBe (0.4 plusOrMinus EPSILON)
            events[0].data.legato shouldBe (3.0 plusOrMinus EPSILON)
            events[0].data.cylinder shouldBe 1
            events[0].data.postGain shouldBe (0.9 plusOrMinus EPSILON)
        }
    }

    companion object {
        fun parseToPattern(input: String) = parseMiniNotation(input) { text, _ -> note(text) }
    }
}

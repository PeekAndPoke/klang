/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.sprudel.lang.parser

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf

/**
 * Tests for the `{name name ...}` tweak block syntax in mini-notation.
 *
 * A tweak block attaches tweak NAMES to a node. The transform each name refers to is bound later
 * by `tweaks(...)`; parsing never resolves it. See `docs/tasks-archive/2026-08/20260831-mini-notation-tweaks.md`.
 *
 * Covers:
 * - Tokenization and parsing of tweak blocks
 * - Order and repetition (a list, not a set)
 * - Round-trip stability (parse -> render -> parse)
 * - Tweaks on different node types and combined with other modifiers
 * - The migration error for the removed `{key=value}` attribute block
 */
class MiniNotationTweaksSpec : StringSpec() {

    private fun parse(input: String): MnPattern = parseMiniNotationMnPattern(input)
    private fun render(pattern: MnPattern): String = MnRenderer.render(pattern)

    init {

        // ── Basic parsing ────────────────────────────────────────────────────

        "single tweak on atom" {
            val result = parse("c4{swell}")
            result.items.size shouldBe 1
            val atom = result.items[0].shouldBeInstanceOf<MnNode.Atom>()
            atom.value shouldBe "c4"
            atom.mods.tweaks shouldBe listOf("swell")
        }

        "multiple tweaks on atom keep their written order" {
            val result = parse("c4{swell bend}")
            val atom = result.items[0].shouldBeInstanceOf<MnNode.Atom>()
            atom.mods.tweaks shouldBe listOf("swell", "bend")
        }

        "reversed order is a different list" {
            val atom = parse("c4{bend swell}").items[0].shouldBeInstanceOf<MnNode.Atom>()
            atom.mods.tweaks shouldBe listOf("bend", "swell")
        }

        "repeated tweak is kept twice" {
            val atom = parse("c4{bend bend}").items[0].shouldBeInstanceOf<MnNode.Atom>()
            atom.mods.tweaks shouldBe listOf("bend", "bend")
        }

        "empty braces produce no tweaks" {
            val atom = parse("c4{}").items[0].shouldBeInstanceOf<MnNode.Atom>()
            atom.mods.tweaks shouldBe emptyList()
            atom.mods.isEmpty shouldBe true
        }

        "tweak names may contain digits and dashes" {
            val atom = parse("c4{bend-2}").items[0].shouldBeInstanceOf<MnNode.Atom>()
            atom.mods.tweaks shouldBe listOf("bend-2")
        }

        // ── On different node types ──────────────────────────────────────────

        "tweaks on group" {
            val group = parse("[c4 e4]{swell}").items[0].shouldBeInstanceOf<MnNode.Group>()
            group.mods.tweaks shouldBe listOf("swell")
            group.items.size shouldBe 2
        }

        "tweaks on rest" {
            val rest = parse("~{swell}").items[0].shouldBeInstanceOf<MnNode.Rest>()
            rest.mods.tweaks shouldBe listOf("swell")
        }

        "tweaks on alternation" {
            val alt = parse("<c4 e4>{swell}").items[0].shouldBeInstanceOf<MnNode.Alternation>()
            alt.mods.tweaks shouldBe listOf("swell")
        }

        // ── Combined with other modifiers ────────────────────────────────────

        "tweaks with multiplier" {
            val atom = parse("c4*2{swell}").items[0].shouldBeInstanceOf<MnNode.Atom>()
            atom.mods.multiplier shouldBe 2.0
            atom.mods.tweaks shouldBe listOf("swell")
        }

        "tweaks with weight" {
            val atom = parse("c4@3{swell}").items[0].shouldBeInstanceOf<MnNode.Atom>()
            atom.mods.weight shouldBe 3.0
            atom.mods.tweaks shouldBe listOf("swell")
        }

        "tweaks with probability" {
            val atom = parse("c4?0.5{swell}").items[0].shouldBeInstanceOf<MnNode.Atom>()
            atom.mods.probability shouldBe 0.5
            atom.mods.tweaks shouldBe listOf("swell")
        }

        "tweaks with euclidean" {
            val atom = parse("c4(3,8){swell}").items[0].shouldBeInstanceOf<MnNode.Atom>()
            atom.mods.euclidean shouldBe MnNode.Euclidean(3, 8)
            atom.mods.tweaks shouldBe listOf("swell")
        }

        "tweaks with multiple modifiers" {
            val atom = parse("c4*2@3{swell bend}").items[0].shouldBeInstanceOf<MnNode.Atom>()
            atom.mods.multiplier shouldBe 2.0
            atom.mods.weight shouldBe 3.0
            atom.mods.tweaks shouldBe listOf("swell", "bend")
        }

        "two tweak blocks on one step accumulate" {
            val atom = parse("c4{swell}{bend}").items[0].shouldBeInstanceOf<MnNode.Atom>()
            atom.mods.tweaks shouldBe listOf("swell", "bend")
        }

        // ── In sequences ─────────────────────────────────────────────────────

        "tweaks on individual notes in sequence" {
            val result = parse("c4{swell} e4{bend}")
            result.items.size shouldBe 2
            result.items[0].shouldBeInstanceOf<MnNode.Atom>().mods.tweaks shouldBe listOf("swell")
            result.items[1].shouldBeInstanceOf<MnNode.Atom>().mods.tweaks shouldBe listOf("bend")
        }

        "mixed tweaked and plain notes" {
            val result = parse("bd{swell} hh sd{bend}")
            result.items.size shouldBe 3
            result.items[0].shouldBeInstanceOf<MnNode.Atom>().mods.tweaks shouldBe listOf("swell")
            result.items[1].shouldBeInstanceOf<MnNode.Atom>().mods.tweaks shouldBe emptyList()
            result.items[2].shouldBeInstanceOf<MnNode.Atom>().mods.tweaks shouldBe listOf("bend")
        }

        // ── Round-trip ───────────────────────────────────────────────────────

        "round-trip: single tweak" {
            render(parse("c4{swell}")) shouldBe "c4{swell}"
        }

        "round-trip: multiple tweaks keep order" {
            render(parse("c4{swell bend}")) shouldBe "c4{swell bend}"
        }

        "round-trip: reversed order stays reversed" {
            render(parse("c4{bend swell}")) shouldBe "c4{bend swell}"
        }

        "round-trip: repeated tweak survives" {
            render(parse("c4{bend bend}")) shouldBe "c4{bend bend}"
        }

        "round-trip: tweaks with other mods" {
            render(parse("c4*2{swell}")) shouldBe "c4*2{swell}"
        }

        "round-trip: tweaks on group" {
            render(parse("[c4 e4]{swell}")) shouldBe "[c4 e4]{swell}"
        }

        "round-trip: empty braces are stripped" {
            render(parse("c4{}")) shouldBe "c4"
        }

        "round-trip: tweaks in sequence" {
            render(parse("bd{swell} hh sd{bend}")) shouldBe "bd{swell} hh sd{bend}"
        }

        "round-trip: all mods plus tweaks" {
            render(parse("c4(3,8)*2/3?0.5@2{swell bend}")) shouldBe "c4(3,8)*2/3?0.5@2{swell bend}"
        }

        // ── Error cases ──────────────────────────────────────────────────────

        "unclosed brace throws parse error" {
            val e = shouldThrowParseError { parse("c4{swell") }
            e.message!! shouldContain "'}'"
        }

        "the removed key=value block throws a migration error" {
            val e = shouldThrowParseError { parse("c4{g=0.5}") }
            e.message!! shouldContain "tweaks"
        }

        "key=value with several pairs also throws" {
            shouldThrowParseError { parse("c4{v=0.8 pan=-0.3}") }
        }

        "a stray equals outside braces also throws" {
            // '=' stopped being a mini-notation character with the attribute block; without the
            // guard this would silently become an atom named "bd=2".
            shouldThrowParseError { parse("bd=2") }
        }

        "key=value with spaces around equals also throws" {
            // '=' no longer breaks a literal, so this arrives as the names "g", "=" and "0.5";
            // the bare '=' is what trips the migration check here.
            shouldThrowParseError { parse("c4{g = 0.5}") }
        }
    }

    private fun shouldThrowParseError(block: () -> Unit): MiniNotationParseException {
        val e = try {
            block()
            null
        } catch (e: MiniNotationParseException) {
            e
        }
        (e != null) shouldBe true
        return e!!
    }
}

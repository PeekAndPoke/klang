/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.script.ksp

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

/**
 * Coverage for [KDocParser], which turns the KDoc of a DSL declaration into the docs model.
 *
 * The module had no test for it until 2026-09-08, which is how the unknown-tag hole below survived:
 * a tag the parser does not know used to be appended to the CONTENT of whichever tag came before it,
 * so one typo silently rewrote a neighbouring tag and the docs shipped it.
 */
class KDocParserTest : StringSpec({

    fun parse(kdoc: String) = KDocParser.parse(kdoc.trimIndent())

    // ── @scope ───────────────────────────────────────────────────────────────

    "reads @scope" {
        parse(
            """
            Reverb.

            @scope orbit
            @category effects
            """
        ).let {
            it.scope shouldBe "orbit"
            it.category shouldBe "effects"
        }
    }

    "no @scope is null, not empty" {
        parse("Just a description.\n\n@category effects").scope shouldBe null
    }

    "an unrecognised @scope value is still handed up (the processor validates and warns)" {
        parse("X.\n\n@scope sideways").scope shouldBe "sideways"
    }

    // ── unknown tags do not corrupt their neighbour ──────────────────────────

    "a misspelled tag does not end up inside the preceding tag" {
        val kdoc = parse(
            """
            Reverb.

            @tags room, wet
            @scpoe orbit
            @category effects
            """
        )

        // The bug this row exists for: tags used to come back as ["room", "wet @scpoe orbit"]
        kdoc.tags shouldBe listOf("room", "wet")
        kdoc.category shouldBe "effects"
        kdoc.unusableTags shouldBe listOf("scpoe")
    }

    "an unknown tag before any known one is reported, not silently dropped" {
        val kdoc = parse("X.\n\n@bogus something\n@category effects")

        kdoc.unusableTags shouldBe listOf("bogus")
        kdoc.category shouldBe "effects"
    }

    "a known-tag-only doc reports no unknown tags" {
        parse("X.\n\n@param a A thing.\n@category effects").unusableTags.shouldBeEmpty()
    }

    "a tag name that merely STARTS with a known one is not swallowed by it" {
        // @return used to eat @returns, @alias ate @aliases, and @param ate @parameter, landing their
        // content in the known tag. Matching now stops at a word boundary (review round 1).
        val kdoc = parse(
            """
            X.

            @returns The pattern.
            @aliases rev, rm
            @category effects
            """
        )

        kdoc.returnDoc shouldBe ""
        kdoc.aliases.shouldBeEmpty()
        kdoc.category shouldBe "effects"
        kdoc.unusableTags shouldBe listOf("returns", "aliases")
    }

    "a misspelling that starts with @param does not corrupt the tag before it" {
        // @parameter matched startsWith("@param") but not the @param regex, so currentTag stayed on
        // the PREVIOUS tag and every following line was appended to it.
        val kdoc = parse(
            """
            X.

            @category effects
            @parameter wet Send into the reverb.
              and more prose
            @tags room
            """
        )

        kdoc.category shouldBe "effects"
        kdoc.tags shouldBe listOf("room")
        kdoc.unusableTags shouldBe listOf("parameter")
    }

    "the retired @param-tool is reported as retired, never read, never taken for @param" {
        parse("X.\n\n@param-tool wet SprudelReverbEditor\n  SprudelReverbSequenceEditor\n@category effects").let {
            it.retiredTags shouldBe listOf("param-tool")
            it.paramTools shouldBe emptyMap()
            it.params.isEmpty() shouldBe true
            it.unusableTags.shouldBeEmpty()
            it.category shouldBe "effects"
        }
    }

    "a known tag written with no value is reported, not silently dropped" {
        val kdoc = parse("X.\n\n@scope\n@category effects")

        kdoc.scope shouldBe null
        kdoc.category shouldBe "effects"
        kdoc.unusableTags shouldBe listOf("scope")
    }

    // ── the continuation behaviour the unknown-tag branch must not break ─────

    "a tag's description continues on the following lines" {
        parse(
            """
            X.

            @param wet Send into the orbit reverb,
              0 to 1, per voice.
            @category effects
            """
        ).params["wet"] shouldBe "Send into the orbit reverb, 0 to 1, per voice."
    }

    // ── existing tags keep working ───────────────────────────────────────────

    "a retired tag is one build error per symbol, saying what to write instead" {
        val kdocs = listOf(parse("A.\n\n@param-tool wet X"), parse("B.\n\n@param-tool size Y"), parse("C."))
        val errors = KDocParser.retiredTagErrors(symbolName = "reverb", kdocs = kdocs)

        errors.size shouldBe 1
        errors.single() shouldContain "'@param-tool' on 'reverb' is retired"
        errors.single() shouldContain "@tool <param>: <Tool>"
        KDocParser.retiredTagErrors(symbolName = "gain", kdocs = listOf(parse("C.\n\n@tool amount: X"))).shouldBeEmpty()
    }

    "@tool in its two forms: a parameter's tools after `name:`, the whole call's without; beside @param" {
        val kdoc = parse(
            """
            X.

            @param wet Send, 0 to 1.
            @tool SprudelReverbEditor
            @tool wet: SprudelReverbEditor,
              SprudelReverbSequenceEditor
            @tool size : SprudelReverbSizeEditor
            @category effects
            """
        )

        kdoc.params shouldBe mapOf("wet" to "Send, 0 to 1.")
        kdoc.callTools shouldBe listOf("SprudelReverbEditor")
        kdoc.paramTools shouldBe mapOf(
            "wet" to listOf("SprudelReverbEditor", "SprudelReverbSequenceEditor"),
            "size" to listOf("SprudelReverbSizeEditor"),
        )
        kdoc.retiredTags.shouldBeEmpty()
        kdoc.category shouldBe "effects"
    }

    "@tool with nothing named is reported; `@tools` is no @tool" {
        parse("X.\n\n@tool\n@tool wet:\n@tools A").let {
            it.callTools.shouldBeEmpty()
            it.paramTools shouldBe emptyMap()
            it.unusableTags shouldBe listOf("tool", "tool", "tools")
        }
    }

    "a function's tools from all its variants: each named once, in the order first named" {
        val tools = EditorTools.of(
            listOf(
                parse("A.\n\n@tool SprudelGainEditor\n@tool amount: SprudelGainEditor, SprudelGainSequenceEditor"),
                parse("B.\n\n@param amount The gain."),
                parse("C.\n\n@tool SprudelGainEditor, Other\n@tool amount: SprudelGainSequenceEditor, Third\n@tool pan: P"),
            )
        )

        tools.call shouldBe listOf("SprudelGainEditor", "Other")
        tools.unknownParams(setOf("amount", "callInfo")) shouldBe listOf("pan")
        tools.unknownParams(setOf("amount", "pan")).shouldBeEmpty()
        tools.params shouldBe mapOf(
            "amount" to listOf("SprudelGainEditor", "SprudelGainSequenceEditor", "Third"),
            "pan" to listOf("P"),
        )
    }

    "@tags and @alias split on commas" {
        val kdoc = parse("X.\n\n@tags room, wet, size\n@alias rev, rm")

        kdoc.tags shouldBe listOf("room", "wet", "size")
        kdoc.aliases shouldBe listOf("rev", "rm")
    }

    "the description stops at the first tag" {
        parse("Line one.\nLine two.\n\n@category effects").description shouldBe "Line one.\nLine two."
    }

    "a null KDoc yields empty everything" {
        KDocParser.parse(null).let {
            it.scope shouldBe null
            it.category shouldBe null
            it.unusableTags.shouldBeEmpty()
            it.description shouldBe ""
        }
    }
})

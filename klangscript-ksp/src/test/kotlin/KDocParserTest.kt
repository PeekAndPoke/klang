/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.script.ksp

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe

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

            @scope orbit-send
            @category effects
            """
        ).let {
            it.scope shouldBe "orbit-send"
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
        kdoc.unknownTags shouldBe listOf("scpoe")
    }

    "an unknown tag before any known one is reported, not silently dropped" {
        val kdoc = parse("X.\n\n@bogus something\n@category effects")

        kdoc.unknownTags shouldBe listOf("bogus")
        kdoc.category shouldBe "effects"
    }

    "a known-tag-only doc reports no unknown tags" {
        parse("X.\n\n@param a A thing.\n@category effects").unknownTags.shouldBeEmpty()
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

    "@param-tool is recognised before @param" {
        val kdoc = parse(
            """
            X.

            @param wet Send, 0 to 1.
            @param-tool wet SprudelReverbEditor, SprudelReverbSequenceEditor
            @category effects
            """
        )

        kdoc.params shouldBe mapOf("wet" to "Send, 0 to 1.")
        kdoc.paramTools shouldBe mapOf("wet" to listOf("SprudelReverbEditor", "SprudelReverbSequenceEditor"))
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
            it.unknownTags.shouldBeEmpty()
            it.description shouldBe ""
        }
    }
})

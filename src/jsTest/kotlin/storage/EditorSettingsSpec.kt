/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.storage

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.codemirror.CodeMirrorHighlightBuffer
import kotlinx.browser.localStorage

/**
 * The code highlights' settings (KLANG-00411): on or off, and their strength, 66 percent for one who never chose; kept
 * in the browser, held in range.
 */
class EditorSettingsSpec : StringSpec({

    "the strength starts at 66 percent and may go from 0 to 100" {
        EditorSettings.HIGHLIGHT_STRENGTH_DEFAULT shouldBe 66
        EditorSettings.HIGHLIGHT_STRENGTH_RANGE shouldBe 0..100
    }

    "a strength chosen shows at once and is kept; one out of range is held at its end" {
        val before = EditorSettings.highlightStrength()

        try {
            EditorSettings.setHighlightStrength(40)

            withClue("shown and kept") {
                EditorSettings.highlightStrength() shouldBe 40
                localStorage.getItem(StorageKeys.EDITOR_HIGHLIGHT_STRENGTH) shouldBe "40"
            }

            EditorSettings.setHighlightStrength(150)
            withClue("above 100") { EditorSettings.highlightStrength() shouldBe 100 }

            EditorSettings.setHighlightStrength(-5)
            withClue("below 0") { EditorSettings.highlightStrength() shouldBe 0 }
        } finally {
            EditorSettings.setHighlightStrength(before)
            localStorage.removeItem(StorageKeys.EDITOR_HIGHLIGHT_STRENGTH)
        }
    }

    "the code highlights are on for anyone who never chose, off only when switched off" {
        withClue("never chose") { EditorSettings.highlightsOnFrom(null) shouldBe true }
        withClue("switched on") { EditorSettings.highlightsOnFrom("true") shouldBe true }
        withClue("switched off") { EditorSettings.highlightsOnFrom("false") shouldBe false }
    }

    "the code highlights switched off and on: shown at once and kept" {
        val before = EditorSettings.highlightsOn()

        try {
            EditorSettings.setHighlightsOn(false)

            withClue("off") {
                EditorSettings.highlightsOn() shouldBe false
                localStorage.getItem(StorageKeys.EDITOR_HIGHLIGHTS_ON) shouldBe "false"
            }

            EditorSettings.setHighlightsOn(true)

            withClue("on again") {
                EditorSettings.highlightsOn() shouldBe true
                localStorage.getItem(StorageKeys.EDITOR_HIGHLIGHTS_ON) shouldBe "true"
            }
        } finally {
            EditorSettings.setHighlightsOn(before)
            localStorage.removeItem(StorageKeys.EDITOR_HIGHLIGHTS_ON)
        }
    }

    "the highlight layer's strength is held between 0 and 1" {
        val buffer = CodeMirrorHighlightBuffer()

        buffer.strength = 0.66
        buffer.strength shouldBe 0.66

        buffer.strength = 1.5
        withClue("above 1") { buffer.strength shouldBe 1.0 }

        buffer.strength = -0.5
        withClue("below 0") { buffer.strength shouldBe 0.0 }
    }
})

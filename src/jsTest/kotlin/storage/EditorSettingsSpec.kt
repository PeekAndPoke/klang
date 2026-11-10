/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.storage

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.codemirror.CodeMirrorHighlightBuffer
import io.peekandpoke.klang.spine.Settings
import io.peekandpoke.klang.spine.storage.SectionStores
import io.peekandpoke.klang.state.EditorSettings
import io.peekandpoke.klang.workshop.storage.KeyValueStore
import io.peekandpoke.klang.workshop.storage.MemoryKeyValueStore

/**
 * The editor's settings (KLANG-00411, KLANG-00422): the code highlights on or off, per sound event and their strength,
 * the quick help on hover; one stored section of the Spine, each number held in its range, and what a browser kept
 * before the section existed read into it once.
 */
class EditorSettingsSpec : StringSpec({

    /** The settings as the Spine builds them, the user's store being [user]. */
    fun settings(user: KeyValueStore) =
        Settings(stores = SectionStores(user = user, machine = MemoryKeyValueStore(), tab = MemoryKeyValueStore()))

    "never chosen: the highlights on, 25 per sound event, 66 percent strong, the quick help on hover" {
        val settings = settings(MemoryKeyValueStore())

        settings.editor() shouldBe EditorSettings(highlightsOn = true, highlightsPerEvent = 25, highlightStrength = 66, hoverHelp = true)

        EditorSettings.HIGHLIGHT_STRENGTH_RANGE shouldBe 0..100
        EditorSettings.HIGHLIGHTS_PER_EVENT_RANGE shouldBe 0..50
    }

    "a setting chosen shows at once and is kept as one document; a number out of range is held at its end" {
        val store = MemoryKeyValueStore()
        val settings = settings(store)
        val strengths = mutableListOf<Int>()
        val watching = settings.editor.part { it.highlightStrength }.subscribeToStream { strengths.add(it) }

        try {
            settings.editor.update { it.copy(highlightStrength = 40, highlightsOn = false) }

            withClue("shown") { settings.editor().highlightStrength shouldBe 40 }
            withClue("kept: what was chosen, so a default that changes later reaches the rest") {
                store.read(StorageKeys.EDITOR) shouldBe """{"v":1,"data":{"highlightsOn":false,"highlightStrength":40}}"""
            }

            settings.editor.update { it.copy(highlightStrength = 150, highlightsPerEvent = 99) }
            withClue("above the range") {
                settings.editor().highlightStrength shouldBe 100
                settings.editor().highlightsPerEvent shouldBe 50
            }

            settings.editor.update { it.copy(highlightStrength = -5) }
            withClue("below the range") { settings.editor().highlightStrength shouldBe 0 }

            settings.editor.update { it.copy(hoverHelp = false) }
            withClue("a part says something only when it changes") { strengths.takeLast(3) shouldBe listOf(40, 100, 0) }

            withClue("settings built on the same store read it back") {
                settings(store).editor() shouldBe
                    EditorSettings(highlightsOn = false, highlightsPerEvent = 50, highlightStrength = 0, hoverHelp = false)
            }
        } finally {
            watching()
        }
    }

    "what a browser kept before, a key each, is read once into the document, and the old keys go" {
        val store = MemoryKeyValueStore(
            initial = mapOf(
                StorageKeys.EDITOR_HIGHLIGHTS_ON to "false",
                StorageKeys.EDITOR_HIGHLIGHT_STRENGTH to "40",
                StorageKeys.EDITOR_HIGHLIGHTS_PER_EVENT to "999",
            )
        )

        val settings = settings(store)

        withClue("the old values, the one never kept at its default, the one out of range held") {
            settings.editor() shouldBe
                EditorSettings(highlightsOn = false, highlightsPerEvent = 50, highlightStrength = 40, hoverHelp = true)
        }
        withClue("kept as the one document, the old keys gone") {
            store.all shouldBe mapOf(
                StorageKeys.EDITOR to """{"v":1,"data":{"highlightsOn":false,"highlightsPerEvent":50,"highlightStrength":40}}"""
            )
        }
        withClue("reset by its key, as the storage page does: the defaults at once, nothing kept") {
            settings.reset(StorageKeys.EDITOR)

            settings.editor() shouldBe EditorSettings()
            store.all shouldBe emptyMap()
        }
        withClue("the other way round: a switch off, a number that is none, the rest never kept") {
            val other = MemoryKeyValueStore(
                initial = mapOf(StorageKeys.EDITOR_HOVER_HELP to "false", StorageKeys.EDITOR_HIGHLIGHTS_PER_EVENT to "abc")
            )

            settings(other).editor() shouldBe
                EditorSettings(highlightsOn = true, highlightsPerEvent = 25, highlightStrength = 66, hoverHelp = false)
            withClue("a number that is none was no choice") {
                other.all shouldBe mapOf(StorageKeys.EDITOR to """{"v":1,"data":{"hoverHelp":false}}""")
            }
        }
        withClue("the two numbers kept at what the default says: choices") {
            val same = MemoryKeyValueStore(
                initial = mapOf(StorageKeys.EDITOR_HIGHLIGHTS_PER_EVENT to "25", StorageKeys.EDITOR_HIGHLIGHT_STRENGTH to "66")
            )

            settings(same)
            same.all shouldBe mapOf(StorageKeys.EDITOR to """{"v":1,"data":{"highlightsPerEvent":25,"highlightStrength":66}}""")
        }
        withClue("a switch is on unless it says false, as it was read before") {
            val odd = MemoryKeyValueStore(
                initial = mapOf(StorageKeys.EDITOR_HIGHLIGHTS_ON to "garbage", StorageKeys.EDITOR_HOVER_HELP to "true")
            )

            settings(odd).editor() shouldBe EditorSettings()
            withClue("and what a key kept is a choice, also where it says what the default says") {
                odd.all shouldBe mapOf(StorageKeys.EDITOR to """{"v":1,"data":{"highlightsOn":true,"hoverHelp":true}}""")
            }
        }
        withClue("nothing kept before: no document is made up") {
            MemoryKeyValueStore().also { settings(it) }.all shouldBe emptyMap()
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

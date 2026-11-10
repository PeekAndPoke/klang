/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.spine

import io.peekandpoke.klang.spine.storage.SectionStores
import io.peekandpoke.klang.spine.storage.Stored
import io.peekandpoke.klang.state.EditorSettings
import io.peekandpoke.klang.storage.StorageKeys
import io.peekandpoke.klang.workshop.storage.DocumentStorage
import io.peekandpoke.klang.workshop.storage.KeyValueStore

/**
 * The user's settings (KLANG-00422), each kind a stored section of its own: a component reads
 * `spine.settings.editor.part { it.highlightStrength }` and changes it with
 * `spine.settings.editor.update { it.copy(highlightStrength = 80) }`.
 */
class Settings(stores: SectionStores) {

    companion object {
        /**
         * The editor's settings as they were kept until KLANG-00422, a key each: read once into the one document.
         * Scaffolding: it goes when no browser holds the old keys any more.
         */
        private fun editorBefore(store: KeyValueStore): DocumentStorage.Legacy<EditorSettings>? {
            // Each old key with the document's field it moves into
            val fields = mapOf(
                StorageKeys.EDITOR_HIGHLIGHTS_ON to "highlightsOn",
                StorageKeys.EDITOR_HIGHLIGHTS_PER_EVENT to "highlightsPerEvent",
                StorageKeys.EDITOR_HIGHLIGHT_STRENGTH to "highlightStrength",
                StorageKeys.EDITOR_HOVER_HELP to "hoverHelp",
            )
            val numbers = setOf(StorageKeys.EDITOR_HIGHLIGHTS_PER_EVENT, StorageKeys.EDITOR_HIGHLIGHT_STRENGTH)
            val keys = fields.keys.toList()
            val kept = keys.associateWith { store.read(it) }

            if (kept.values.all { it == null }) {
                return null
            }

            return DocumentStorage.Legacy(
                value = EditorSettings(
                    // On for anyone who never chose, off only when switched off
                    highlightsOn = kept[StorageKeys.EDITOR_HIGHLIGHTS_ON] != "false",
                    highlightsPerEvent = kept[StorageKeys.EDITOR_HIGHLIGHTS_PER_EVENT]?.toIntOrNull()
                        ?: EditorSettings.HIGHLIGHTS_PER_EVENT_DEFAULT,
                    highlightStrength = kept[StorageKeys.EDITOR_HIGHLIGHT_STRENGTH]?.toIntOrNull()
                        ?: EditorSettings.HIGHLIGHT_STRENGTH_DEFAULT,
                    hoverHelp = kept[StorageKeys.EDITOR_HOVER_HELP] != "false",
                ),
                keys = keys,
                // A key that was kept was the user's choice, also where it says what the default says; a number that
                // cannot be read never was one
                chosen = kept.filter { (key, text) -> text != null && (key !in numbers || text.toIntOrNull() != null) }
                    .keys.mapNotNull { fields[it] }.toSet(),
            )
        }
    }

    /** The editor: the code highlights on or off, per sound event and their strength, the quick help on hover. */
    val editor = Stored(
        key = StorageKeys.EDITOR,
        version = 1,
        serializer = EditorSettings.serializer(),
        default = EditorSettings(),
        // The user's: every tab shows a change at once
        store = stores.user,
        normalise = { it.inRange() },
        legacy = ::editorBefore,
    )

    private val sections: List<Stored<*>> = listOf(editor)

    /** Forgets the section kept under [key], if it is one of these: it shows its default at once. */
    fun reset(key: String) {
        sections.filter { it.key == key }.forEach { it.reset() }
    }
}

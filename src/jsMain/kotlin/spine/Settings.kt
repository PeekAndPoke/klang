/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.spine

import io.peekandpoke.klang.spine.storage.SectionStores
import io.peekandpoke.klang.spine.storage.Stored
import io.peekandpoke.klang.state.EditorSettings
import io.peekandpoke.klang.state.ToolSettings
import io.peekandpoke.klang.storage.StorageKeys
import io.peekandpoke.klang.ui.codetools.KlangToolAutoUpdate
import io.peekandpoke.klang.workshop.storage.DocumentStorage
import io.peekandpoke.klang.workshop.storage.KeyValueStore
import io.peekandpoke.ultra.streams.Unsubscribe

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

        /**
         * The tools' switch as `klangui` kept it itself until KLANG-00422: `true` or `false` under its own key.
         * Scaffolding, as [editorBefore].
         */
        private fun toolsBefore(store: KeyValueStore): DocumentStorage.Legacy<ToolSettings>? {
            val kept = store.read(StorageKeys.TOOL_AUTO_UPDATE) ?: return null

            return DocumentStorage.Legacy(
                // On unless switched off, as a text that is neither was read before
                value = ToolSettings(autoUpdate = kept != "false"),
                keys = listOf(StorageKeys.TOOL_AUTO_UPDATE),
                chosen = setOf("autoUpdate"),
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

    /** The code tools: whether a tool writes each change into the code at once. */
    val tools = Stored(
        key = StorageKeys.TOOLS,
        version = 1,
        serializer = ToolSettings.serializer(),
        default = ToolSettings(),
        store = stores.user,
        legacy = ::toolsBefore,
    )

    internal val sections: List<Stored<*>> = listOf(editor, tools)

    /**
     * Ties the tools' kept switch to the widgets' one (`klangui`'s [KlangToolAutoUpdate], which knows nothing of
     * storage), once, at the app's start: the switch shows what is kept and follows it (another tab, a reset), and a
     * press on it is kept. The way to untie comes back.
     */
    fun attachTools(): Unsubscribe {
        val unsubscribes = listOf(
            tools.part { it.autoUpdate }.subscribeToStream { KlangToolAutoUpdate.set(it) },
            KlangToolAutoUpdate.subscribeToStream { on -> tools.update { it.copy(autoUpdate = on) } },
        )

        return { unsubscribes.forEach { it() } }
    }
}

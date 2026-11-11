/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.spine

import io.peekandpoke.klang.spine.storage.SectionStores
import io.peekandpoke.klang.spine.storage.Stored
import io.peekandpoke.klang.storage.KeymapJson
import io.peekandpoke.klang.storage.StorageKeys
import io.peekandpoke.klang.workshop.keys.KeymapStore
import io.peekandpoke.klang.workshop.storage.DocumentStorage
import io.peekandpoke.klang.workshop.storage.KeyValueStore
import io.peekandpoke.ultra.streams.Unsubscribe
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.builtins.serializer

/**
 * The keys the user gave Klang's actions, over the defaults (KLANG-00325), as a stored section (KLANG-00422): the
 * user's, a field per action id, so two tabs that each change another action's keys both keep theirs.
 *
 * An action's field holds its keys (an empty list: every key taken off), or `null` once the user put it back to its
 * defaults. A write cannot take a field out of the document, and `null` says the same to every tab.
 *
 * The key manager (the workshop's) reads and writes through [store]; this class holds no keymap of its own.
 */
class UserKeys(stores: SectionStores) {

    companion object {
        /**
         * The keys as they were kept until KLANG-00422: one JSON object, no version. Scaffolding: it goes when no
         * browser holds the old key any more. A text with no readable entry is left where it is.
         */
        private fun before(store: KeyValueStore): DocumentStorage.Legacy<Map<String, List<String>?>>? {
            val keys = store.read(StorageKeys.KEYMAP)?.let { KeymapJson.read(it) }.orEmpty()

            if (keys.isEmpty()) {
                return null
            }

            return DocumentStorage.Legacy(value = keys, keys = listOf(StorageKeys.KEYMAP))
        }

        /** The actions that have keys of the user's, without those put back to their defaults. */
        private fun chosen(kept: Map<String, List<String>?>): Map<String, List<String>> =
            kept.mapNotNull { (actionId, keys) -> keys?.let { actionId to it } }.toMap()
    }

    private val kept = Stored(
        key = StorageKeys.USER_KEYS,
        version = 1,
        serializer = MapSerializer(String.serializer(), ListSerializer(String.serializer()).nullable),
        default = emptyMap(),
        // The user's: a key changed in one tab works in every tab at once
        store = stores.user,
        legacy = ::before,
    )

    internal val sections: List<Stored<*>> = listOf(kept)

    /** What the key manager keeps the user's keys in. */
    val store: KeymapStore = object : KeymapStore {

        override fun load(): Map<String, List<String>> = chosen(kept())

        override fun save(userKeys: Map<String, List<String>>) {
            // An action that had keys and has none here went back to its defaults: null, the field stays
            kept.update { all -> all.mapValues { (actionId, _) -> userKeys[actionId] } + userKeys }
        }

        override fun watch(onChange: (Map<String, List<String>>) -> Unit): Unsubscribe =
            kept.stream.subscribeToStream { onChange(chosen(it)) }
    }
}

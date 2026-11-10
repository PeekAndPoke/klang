/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.spine

import io.peekandpoke.klang.spine.storage.SectionStores
import io.peekandpoke.klang.spine.storage.Stored
import io.peekandpoke.klang.state.BenchLayout
import io.peekandpoke.klang.storage.StorageKeys
import io.peekandpoke.klang.workshop.storage.DocumentStorage
import io.peekandpoke.klang.workshop.storage.KeyValueStore
import io.peekandpoke.klang.workshop.storage.StoredDocument
import kotlinx.serialization.json.JsonObject

/**
 * The workbench's layout, a part of the Spine (KLANG-00422): which tool windows are pinned and open, and the bottom
 * panel. It is the tab's own (the maintainer, 2026-11-10: with several tabs open, "navigating around and opening /
 * closing tools should not influence the other tab"), and lives through a reload. A new tab begins with the layout
 * arranged last in any tab of this machine.
 */
class Workbench(stores: SectionStores) {

    companion object {
        /**
         * The layout as it was kept until KLANG-00422, one for every tab, the fields under the same names: read once
         * as the machine's last. Scaffolding: it goes when no browser holds the old key any more.
         */
        private fun before(store: KeyValueStore): DocumentStorage.Legacy<BenchLayout>? {
            val text = store.read(StorageKeys.WORKBENCH_LAYOUT) ?: return null
            val fields = StoredDocument.json.parseToJsonElement(text) as? JsonObject ?: return null

            return DocumentStorage.Legacy(
                value = StoredDocument.json.decodeFromJsonElement(BenchLayout.serializer(), fields),
                keys = listOf(StorageKeys.WORKBENCH_LAYOUT),
                chosen = fields.keys,
            )
        }
    }

    /**
     * The layout arranged last in any tab of this machine: what a new tab begins with. Nobody shows it, so it follows
     * nothing. Each tab writes the parts it changed, so it is the last pinning of one tab beside the last panel
     * height of another.
     */
    private val last = Stored(
        key = StorageKeys.WORKBENCH,
        version = 1,
        serializer = BenchLayout.serializer(),
        default = BenchLayout(),
        store = stores.machine,
        normalise = { it.inRange() },
        legacy = ::before,
        follows = false,
    )

    /** This tab's layout, read once by the bench when it is drawn; [arranged] changes it. */
    val layout = Stored(
        key = StorageKeys.WORKBENCH,
        version = 1,
        serializer = BenchLayout.serializer(),
        default = BenchLayout(),
        store = stores.tab,
        normalise = { it.inRange() },
        // The tab's storage has no other writer
        follows = false,
        start = { last() },
    )

    internal val sections: List<Stored<*>> = listOf(layout, last)

    /** The user arranged the bench as [next]: this tab's layout, and what the next new tab begins with. */
    fun arranged(next: BenchLayout) {
        layout.update { next }
        last.update { next }
    }
}

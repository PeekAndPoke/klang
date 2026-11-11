/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.spine

import io.peekandpoke.klang.spine.storage.SectionStores
import io.peekandpoke.klang.spine.storage.Stored
import io.peekandpoke.klang.state.LookChoice
import io.peekandpoke.klang.state.OwnLook
import io.peekandpoke.klang.storage.StorageKeys
import io.peekandpoke.klang.ui.feel.KlangLoadout
import io.peekandpoke.klang.ui.feel.KlangLookPreset
import io.peekandpoke.klang.ui.feel.KlangLookShelf
import io.peekandpoke.klang.ui.feel.KlangLookText
import io.peekandpoke.klang.ui.feel.KlangTheme
import io.peekandpoke.klang.workshop.storage.DocumentStorage
import io.peekandpoke.klang.workshop.storage.KeyValueStore
import io.peekandpoke.ultra.streams.Unsubscribe
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/** The own looks and the slots as one text, as they were kept until KLANG-00422. Scaffolding, with the readers below. */
@Serializable
private class LooksBefore(val own: List<JsonObject> = emptyList(), val slots: List<String?>? = null)

/**
 * The user's looks as stored sections (KLANG-00339, KLANG-00382, stored so since KLANG-00422): the look worn with
 * the equipped slots, and the looks of the user's own, a field per look.
 *
 * The look itself stays the widgets' (`klangui`: [KlangTheme], [KlangLookShelf]), which know nothing of storage.
 * [attach] ties the two together once, at the app's start: what is kept goes onto the shelf and the theme, and what
 * the shelf becomes is kept. The look worn is told here by whoever lets the user choose one ([wear]).
 *
 * A tab keeps its look until it is loaded again (the maintainer, 2026-11-11: "Keep the look per tab until reload"):
 * neither section follows another tab, so a look chosen, made, changed or deleted elsewhere shows here after a
 * reload. What this tab writes is still merged field by field, so it does not undo what another tab kept.
 */
class Looks(stores: SectionStores) {

    companion object {
        private val beforeJson = Json { ignoreUnknownKeys = true }

        /** The old text of the own looks and the slots; null when there is none, or it cannot be read. */
        private fun before(store: KeyValueStore): LooksBefore? = try {
            store.read(StorageKeys.LOOKS)?.let { beforeJson.decodeFromString(LooksBefore.serializer(), it) }
        } catch (_: Exception) {
            null
        }

        /** The looks of the user's own in the old text. */
        private fun ownLooksBefore(store: KeyValueStore): List<KlangLookPreset> =
            before(store)?.own.orEmpty().mapNotNull { KlangLookText.fromJson(it) }

        /**
         * The own looks as they were kept until KLANG-00422. The old text holds the slots too, so it is forgotten by
         * whichever of the two readers comes last: here only once the choice is kept, in [choiceBefore] only once the
         * own looks are. So a document that could not be kept leaves the old text for the next start. Scaffolding:
         * it goes when no browser holds the old keys any more.
         */
        private fun ownBefore(store: KeyValueStore): DocumentStorage.Legacy<Map<String, OwnLook?>>? {
            val looks = ownLooksBefore(store)

            if (looks.isEmpty()) {
                return null
            }

            return DocumentStorage.Legacy(
                value = looks.associate { it.id to kept(it) },
                keys = listOfNotNull(StorageKeys.LOOKS.takeIf { store.read(StorageKeys.LOOK_CHOICE) != null }),
            )
        }

        /** The look worn and the slots as they were kept until KLANG-00422, a key each. Scaffolding, as [ownBefore]. */
        private fun choiceBefore(store: KeyValueStore): DocumentStorage.Legacy<LookChoice>? {
            val worn = store.read(StorageKeys.LOOK)
            val shelf = before(store)

            if (worn == null && shelf == null) {
                return null
            }

            // A text that could not be read stays where it is, and so does one whose looks are not kept anew yet
            val looksKept = ownLooksBefore(store).isEmpty() || store.read(StorageKeys.OWN_LOOKS) != null

            return DocumentStorage.Legacy(
                value = LookChoice(worn = worn, slots = shelf?.slots),
                keys = listOfNotNull(StorageKeys.LOOK, StorageKeys.LOOKS.takeIf { shelf != null && looksKept }),
            )
        }

        /** [look] as it is kept. */
        private fun kept(look: KlangLookPreset): OwnLook =
            OwnLook(name = look.name, author = look.author, values = look.values.mapKeys { (role, _) -> role.cssVar })

        /** The look kept as [look] under [id], as the shelf holds it: without what a look may not hold. */
        private fun lookOf(id: String, look: OwnLook): KlangLookPreset =
            KlangLookText.own(id = id, name = look.name, author = look.author, values = look.values)

        /** The looks in [kept], in the document's order, without the deleted ones. */
        private fun looksOf(kept: Map<String, OwnLook?>): List<KlangLookPreset> =
            kept.mapNotNull { (id, look) -> look?.let { lookOf(id = id, look = it) } }

        /** The slots as they are kept: null while they are the ones a new user finds. */
        private fun slotsOf(loadout: KlangLoadout): List<String?>? = loadout.slots.takeIf { it != KlangLoadout.default.slots }
    }

    // Before the choice: this one's reader of the old text runs first and leaves it for the choice's (see ownBefore)
    private val own = Stored(
        key = StorageKeys.OWN_LOOKS,
        version = 1,
        serializer = MapSerializer(String.serializer(), OwnLook.serializer().nullable),
        default = emptyMap(),
        store = stores.user,
        legacy = ::ownBefore,
        // The user's, and this tab's until it is loaded again
        follows = false,
    )

    private val choice = Stored(
        key = StorageKeys.LOOK_CHOICE,
        version = 1,
        serializer = LookChoice.serializer(),
        default = LookChoice(),
        store = stores.user,
        legacy = ::choiceBefore,
        follows = false,
    )

    internal val sections: List<Stored<*>> = listOf(own, choice)

    init {
        // Both documents kept, an old key still there (a write that failed once, made good later): no reader runs
        // any more, so it goes here, or a reset of one section would read the old text again. Scaffolding, with the
        // readers
        val store = stores.user

        if (store.read(StorageKeys.OWN_LOOKS) != null && store.read(StorageKeys.LOOK_CHOICE) != null) {
            store.remove(StorageKeys.LOOK)

            if (before(store) != null) {
                store.remove(StorageKeys.LOOKS)
            }
        }
    }

    /** While what the shelf became is being kept: nothing of it needs showing. */
    private var keeping = false

    /** The id of the look kept as worn; null for one who never chose. */
    val worn: String? get() = choice().worn

    /** Keeps [look] as the one worn. The theme is the caller's to change, as the widgets do it. */
    fun wear(look: KlangLookPreset) {
        choice.update { it.copy(worn = look.id) }
    }

    /**
     * Ties the kept looks to the widgets' shelf and theme, once, at the app's start: the shelf holds what is kept, the
     * theme wears the look kept as worn ([firstVisit] where none is kept), and both follow a reset of what is kept
     * (the storage page); what the shelf becomes is kept. The way to untie comes back.
     *
     * A change the shelf makes itself is only kept, never shown back: the page that deletes or changes the look worn
     * tells the theme itself. A look kept as worn that this page does not have (its looks reset, a built-in look of
     * a newer build) stays kept: the page wears [firstVisit] meanwhile, and a look's id is never given twice.
     */
    fun attach(firstVisit: KlangLookPreset): Unsubscribe {
        fun show() {
            if (keeping) {
                return
            }

            val looks = looksOf(own())
            val slots = choice().slots
            val shelf = KlangLookShelf()

            if (shelf.own != looks || slotsOf(shelf.loadout) != slots) {
                KlangLookShelf.restore(own = looks, slots = slots)
            }

            val look = choice().worn?.let { KlangLookShelf().byId(it) } ?: firstVisit

            // Also at the start: a theme never told wears the look that puts nothing on the page
            if (KlangTheme.worn != look) {
                KlangTheme.choose(look)
            }
        }

        val unsubscribes = listOf(
            own.stream.subscribeToStream { show() },
            choice.stream.subscribeToStream { show() },
            KlangLookShelf.subscribeToStream { keep(it) },
        )

        return { unsubscribes.forEach { it() } }
    }

    /**
     * Keeps what the shelf holds: its own looks, a deleted one as null, and the slots. A look the shelf holds as it
     * is kept stays as it is kept: a value this build cannot take (a role of a newer build) is not on the shelf, and
     * is not dropped from the document for that.
     */
    private fun keep(shelf: KlangLookShelf.State) {
        keeping = true

        try {
            own.update { all ->
                val looks = shelf.own.associate { look ->
                    look.id to (all[look.id]?.takeIf { lookOf(id = look.id, look = it) == look } ?: kept(look))
                }

                all.mapValues { (id, _) -> looks[id] } + looks
            }
            choice.update { it.copy(slots = slotsOf(shelf.loadout)) }
        } finally {
            keeping = false
        }
    }
}

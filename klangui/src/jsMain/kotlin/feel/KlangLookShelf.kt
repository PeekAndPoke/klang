/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.ui.feel

import io.peekandpoke.ultra.streams.Stream
import io.peekandpoke.ultra.streams.StreamSource
import io.peekandpoke.ultra.streams.Unsubscribe
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Every look there is, built-in and the user's own, and which are equipped (KLANG-00382): what the drawer's LOOK row
 * and the look editor read. The app keeps it in the browser (it subscribes and stores each change; `klangui` cannot
 * reach the app's storage) and hands back what it kept with [restore].
 */
object KlangLookShelf : Stream<KlangLookShelf.State> {

    data class State(
        /** The user's own looks, oldest first. */
        val own: List<KlangLookPreset> = emptyList(),
        val loadout: KlangLoadout = KlangLoadout.default,
    ) {
        /** The built-in looks, then the user's own. */
        val all: List<KlangLookPreset> get() = KlangLooks.all + own

        /** The look with [id]; null for none. */
        fun byId(id: String): KlangLookPreset? = KlangLooks.currentId(id).let { now -> all.firstOrNull { it.id == now } }

        /** The slots, each with its look or null. */
        val equipped: List<KlangLookPreset?> get() = loadout.slots.map { id -> id?.let { byId(it) } }

        /** The looks in no slot, built-in first. */
        val bench: List<KlangLookPreset> get() = all.filter { loadout.slotOf(it.id) == null }
    }

    private val source: StreamSource<State> = StreamSource(State())

    override fun invoke(): State = source()

    override fun subscribeToStream(sub: (State) -> Unit): Unsubscribe = source.subscribeToStream(sub)

    /** What the app kept: the own looks, and the slots, cleaned of ids no look has any more. */
    fun restore(own: List<KlangLookPreset>, slots: List<String?>?) {
        val known = KlangLooks.all + own
        // A slot remembered by a look's old id holds it by its new one
        val loadout = slots?.let { KlangLoadout(it.map { id -> id?.let(KlangLooks::currentId) }) } ?: KlangLoadout.default

        source(State(own = own, loadout = loadout.normalized(known.map { it.id })))
    }

    /** [id] on [slot] (see [KlangLoadout.place]). */
    fun place(id: String, slot: Int) {
        update { it.copy(loadout = it.loadout.place(id, slot)) }
    }

    /** [slot] emptied, its look to the bench. */
    fun unequip(slot: Int) {
        update { it.copy(loadout = it.loadout.unequip(slot)) }
    }

    /** [look] kept as one of the user's own: in its place when it is one already, else new, at the end. */
    fun keep(look: KlangLookPreset) {
        val own = look.copy(own = true)

        update { state ->
            val at = state.own.indexOfFirst { it.id == own.id }

            state.copy(own = if (at >= 0) state.own.toMutableList().also { it[at] = own } else state.own + own)
        }
    }

    /** The user's look [id] gone, and off its slot. */
    fun delete(id: String) {
        update { state -> state.copy(own = state.own.filter { it.id != id }, loadout = state.loadout.without(id)) }
    }

    /**
     * An id no look has, for a new one of the user's own: `own-` and a random UUID, so it repeats neither on another
     * shelf (a second tab, a second computer of the same user) nor after a look was deleted. Looks made before
     * KLANG-00422 keep their counted ids (`own-3`).
     */
    @OptIn(ExperimentalUuidApi::class)
    fun newId(): String = "own-${Uuid.random()}"

    private fun update(change: (State) -> State) {
        source(change(source()))
    }
}

/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.ui.feel

/**
 * The equipped looks (KLANG-00382): [SIZE] slots, what the Motor drawer's LOOK row shows, in that order, each holding a
 * look's id or nothing. A look not in a slot waits on the bench. Like equipping in a video game (the maintainer,
 * 2026-10-28): a look placed on a slot another holds sends that one to the bench, or swaps with it when it came from a
 * slot itself.
 */
data class KlangLoadout(val slots: List<String?>) {

    companion object {
        /** The drawer's two rows of four. */
        const val SIZE = 8

        /** The built-in looks in their order, the last slot empty. */
        val default: KlangLoadout = KlangLoadout(KlangLooks.all.map { it.id }).normalized(KlangLooks.all.map { it.id })
    }

    /** The slot [id] is in; null when it waits on the bench. */
    fun slotOf(id: String): Int? = slots.indexOf(id).takeIf { it >= 0 }

    /** [id] on [slot]: swapped with the look there when [id] came from a slot, else that look goes to the bench. */
    fun place(id: String, slot: Int): KlangLoadout {
        if (slot !in 0 until SIZE) {
            return this
        }

        val next = slots.toMutableList()
        val from = slotOf(id)

        if (from != null) {
            next[from] = next[slot]
        }

        next[slot] = id

        return KlangLoadout(next)
    }

    /** [slot] emptied; its look goes to the bench. */
    fun unequip(slot: Int): KlangLoadout = KlangLoadout(slots.mapIndexed { i, id -> if (i == slot) null else id })

    /** [id] off every slot it is in (a look deleted). */
    fun without(id: String): KlangLoadout = KlangLoadout(slots.map { if (it == id) null else it })

    /** Exactly [SIZE] slots, each id one of [known] and in one slot only; remembered data may be older or odd. */
    fun normalized(known: Collection<String>): KlangLoadout {
        val seen = mutableSetOf<String>()

        return KlangLoadout(
            (0 until SIZE).map { i ->
                slots.getOrNull(i)?.takeIf { it in known && seen.add(it) }
            }
        )
    }
}

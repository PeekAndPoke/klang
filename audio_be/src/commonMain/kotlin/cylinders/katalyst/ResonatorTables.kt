/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.cylinders.katalyst

import io.peekandpoke.klang.audio_be.filters.ResonatorTable
import io.peekandpoke.klang.audio_bridge.BodyMaterials
import io.peekandpoke.klang.audio_bridge.VowelBands

/**
 * The engine's resonator tables, ONE per catalogue index, built once per process from `BodyMaterials` and
 * `VowelBands` (engine tidy-up step 12 (a)). Index 0 (`none`) has none.
 *
 * **Indices whose rows are equal share ONE table instance.** `bass:a`, `bass:ei` and `bass:au` are three names of one
 * formant bank (so are `alto:*` and `countertenor:*`, and each umlaut's two spellings), and the stage decides whether
 * to install by comparing table REFERENCES. Sharing is what makes that compare answer as the structural compare of the
 * rows did before the step: a switch among aliases installs nothing.
 *
 * The capacity of a kind is its largest row count: the bank a stage pools is sized for every table of its kind, so a
 * change never grows one.
 */
internal object ResonatorTables {

    private val bodies: Array<ResonatorTable?> = tablesOf(
        size = BodyMaterials.names.size,
        rowsAt = { BodyMaterials.modesAt(it.toDouble()) },
        build = ResonatorTable::ofBody,
    )

    private val vowels: Array<ResonatorTable?> = tablesOf(
        size = VowelBands.names.size,
        rowsAt = { VowelBands.bandsAt(it.toDouble()) },
        build = ResonatorTable::ofVowel,
    )

    private val bodyCapacity: Int = bodies.maxOf { it?.count ?: 0 }

    private val vowelCapacity: Int = vowels.maxOf { it?.count ?: 0 }

    /**
     * The table a slot value names for [kind], or null when it names none (the stage off): the catalogue's own index
     * rule (`BodyMaterials.slotIndexAt`, `VowelBands.slotIndexAt`), then this object's table at that index.
     */
    fun at(kind: ResonatorKind, slotValue: Double): ResonatorTable? = when (kind) {
        ResonatorKind.BODY -> bodies[BodyMaterials.slotIndexAt(slotValue)]
        ResonatorKind.VOWEL -> vowels[VowelBands.slotIndexAt(slotValue)]
    }

    /** The largest row count of [kind]'s catalogue: what a stage's pooled banks are sized for. */
    fun capacity(kind: ResonatorKind): Int = when (kind) {
        ResonatorKind.BODY -> bodyCapacity
        ResonatorKind.VOWEL -> vowelCapacity
    }

    /**
     * One table per index: [build] of the rows [rowsAt] gives, none where it gives null, and the table of the first
     * earlier index whose rows are equal (`==`) where there is one. Runs once per catalogue, at init.
     */
    private fun <T> tablesOf(
        size: Int,
        rowsAt: (Int) -> List<T>?,
        build: (List<T>) -> ResonatorTable,
    ): Array<ResonatorTable?> {
        val tables = arrayOfNulls<ResonatorTable>(size)

        for (i in 0 until size) {
            val rows = rowsAt(i) ?: continue
            val earlier = (0 until i).firstOrNull { rowsAt(it) == rows }

            tables[i] = if (earlier != null) tables[earlier] else build(rows)
        }

        return tables
    }
}

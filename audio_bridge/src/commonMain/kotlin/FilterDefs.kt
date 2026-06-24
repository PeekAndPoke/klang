/*
 * Copyright (C) 2025-2026 The Klang Audio Motör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_bridge


data class FilterDefs(
    val filters: List<FilterDef>,
) : List<FilterDef> by filters {

    companion object {
        val empty = FilterDefs(emptyList())
    }

    inline fun <reified T : FilterDef> getByType(): T? {
        return filters.filterIsInstance<T>().firstOrNull()
    }

    fun modifyAll(block: (FilterDef) -> FilterDef): FilterDefs {
        return FilterDefs(filters = filters.map(block))
    }

    fun addOrReplace(filter: FilterDef): FilterDefs {
        val newFilters = if (filters.any { it::class == filter::class }) {
            filters.map { if (it::class == filter::class) filter else it }
        } else {
            filters + filter
        }

        return FilterDefs(filters = newFilters)
    }

    fun addOrReplace(filters: List<FilterDef>): FilterDefs {
        return filters.fold(this) { acc, filter -> acc.addOrReplace(filter) }
    }

    fun addOrReplace(defs: FilterDefs): FilterDefs {
        return addOrReplace(defs.filters)
    }
}

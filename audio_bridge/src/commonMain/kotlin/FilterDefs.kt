package io.peekandpoke.klang.audio_bridge

import kotlinx.serialization.Serializable

@Serializable
class FilterDefs(
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

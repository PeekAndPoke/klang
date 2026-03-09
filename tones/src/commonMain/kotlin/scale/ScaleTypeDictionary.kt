package io.peekandpoke.klang.tones.scale

import io.peekandpoke.klang.tones.pcset.PcSet
import io.peekandpoke.klang.tones.scale.ScaleTypeDictionary.all
import io.peekandpoke.klang.tones.scale.ScaleTypeDictionary.get
import io.peekandpoke.klang.tones.scale.ScaleTypeDictionary.names

/**
 * Singleton registry of all known [ScaleType] definitions.
 *
 * Scale types are loaded from [ScalesData.SCALES] at initialisation and indexed by
 * name, aliases, chroma string, and set number for O(1) lookup.
 *
 * Use [get] to resolve a scale type by any of its keys, or [all] / [names] to
 * enumerate the full catalogue.
 */
object ScaleTypeDictionary {
    /** All registered scale types in definition order. */
    private val dictionary: List<ScaleType>

    /** Lookup index: maps name, alias, chroma, and setNum (as string) → [ScaleType]. */
    private val index: Map<String, ScaleType>

    init {
        val d = mutableListOf<ScaleType>()
        val i = mutableMapOf<String, ScaleType>()

        ScalesData.SCALES.forEach { row ->
            val ivls = row[0].split(" ")
            val name = row[1]
            val aliases = row.drop(2)

            val pcSet = PcSet.get(ivls)
            val scale = ScaleType(pcSet, name, aliases, ivls)

            d.add(scale)

            i[scale.name] = scale
            i[scale.setNum.toString()] = scale
            i[scale.chroma] = scale
            scale.aliases.forEach { alias ->
                i[alias] = scale
            }
        }

        dictionary = d.toList()
        index = i.toMap()
    }

    /**
     * Returns the canonical names of all registered scale types.
     */
    fun names(): List<String> = dictionary.map { it.name }

    /**
     * Looks up a [ScaleType] by name, alias, chroma string, or set number string.
     *
     * @param type Lookup key, e.g. `"major"`, `"ionian"`, `"101011010101"`, or `"2773"`.
     * @return The matching [ScaleType], or [ScaleType.NoScaleType] if not found.
     */
    fun get(type: String): ScaleType {
        return index[type] ?: ScaleType.NoScaleType
    }

    /**
     * Looks up a [ScaleType] by its Fort set-class number.
     *
     * @param setNum The numeric set-class identifier.
     * @return The matching [ScaleType], or [ScaleType.NoScaleType] if not found.
     */
    fun get(setNum: Int): ScaleType {
        return index[setNum.toString()] ?: ScaleType.NoScaleType
    }

    /**
     * Returns all registered [ScaleType] instances in definition order.
     */
    fun all(): List<ScaleType> = dictionary

    /**
     * Returns all lookup keys (names, aliases, chromas, set numbers) in the index.
     */
    fun keys(): List<String> = index.keys.toList()
}

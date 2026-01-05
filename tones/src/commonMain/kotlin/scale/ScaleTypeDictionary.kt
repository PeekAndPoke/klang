package io.peekandpoke.klang.tones.scale

import io.peekandpoke.klang.tones.pcset.PcSet

object ScaleTypeDictionary {
    private var dictionary = mutableListOf<ScaleType>()
    private var index = mutableMapOf<String, ScaleType>()

    init {
        reset()
    }

    /**
     * Returns a list of all scale names.
     */
    fun names(): List<String> = dictionary.map { it.name }

    /**
     * Given a scale name or chroma or set number, return the scale properties.
     */
    fun get(type: Any?): ScaleType {
        return index[type.toString()] ?: ScaleType.NoScaleType
    }

    /**
     * Return a list of all scale types.
     */
    fun all(): List<ScaleType> = dictionary.toList()

    /**
     * Keys used to reference scale types.
     */
    fun keys(): List<String> = index.keys.toList()

    /**
     * Clear the dictionary.
     */
    fun removeAll() {
        dictionary.clear()
        index.clear()
    }

    /**
     * Reset the dictionary to its original state.
     */
    fun reset() {
        removeAll()
        ScalesData.SCALES.forEach { row ->
            val ivls = row[0].split(" ")
            val name = row[1]
            val aliases = row.drop(2)
            add(ivls, name, aliases)
        }
    }

    /**
     * Add a scale into dictionary.
     */
    fun add(
        intervals: List<String>,
        name: String,
        aliases: List<String> = emptyList(),
    ): ScaleType {
        val pcset = PcSet.Companion.get(intervals)
        val scale = ScaleType(pcset, name, aliases, intervals)

        dictionary.add(scale)

        // Index by name, chroma, setNum, and all aliases
        index[scale.name] = scale
        index[scale.setNum.toString()] = scale
        index[scale.chroma] = scale
        scale.aliases.forEach { alias ->
            index[alias] = scale
        }
        return scale
    }
}

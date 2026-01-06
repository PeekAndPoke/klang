package io.peekandpoke.klang.tones.scale

import io.peekandpoke.klang.tones.pcset.PcSet

/**
 * Dictionary for looking up scale types by name, alias, chroma, or set number.
 */
object ScaleTypeDictionary {
    private val dictionary: List<ScaleType>
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

            // Index by name, chroma, setNum, and all aliases
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
     * Returns a list of all scale names.
     */
    fun names(): List<String> = dictionary.map { it.name }

    /**
     * Given a scale name, alias, chroma, or set number, return the scale properties.
     */
    fun get(type: String): ScaleType {
        return index[type] ?: ScaleType.NoScaleType
    }

    /**
     * Given a set number, return the scale properties.
     */
    fun get(setNum: Int): ScaleType {
        return index[setNum.toString()] ?: ScaleType.NoScaleType
    }

    /**
     * Return a list of all scale types.
     */
    fun all(): List<ScaleType> = dictionary

    /**
     * Keys used to reference scale types.
     */
    fun keys(): List<String> = index.keys.toList()
}

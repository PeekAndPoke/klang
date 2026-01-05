package io.peekandpoke.klang.tones.scale

import io.peekandpoke.klang.tones.pcset.PcSet
import io.peekandpoke.klang.tones.pcset.Pcset

/**
 * Properties for a scale in the scale dictionary.
 *
 * @property name The scale name.
 * @property aliases Alternative list of names.
 * @property intervals An array of interval names.
 */
class ScaleType(
    val pcset: Pcset,
    val name: String,
    val aliases: List<String>,
    val intervals: List<String>,
) {
    val empty: Boolean get() = pcset.empty
    val setNum: Int get() = pcset.setNum
    val chroma: String get() = pcset.chroma
    val normalized: String get() = pcset.normalized

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ScaleType) return false
        if (name != other.name) return false
        if (pcset != other.pcset) return false
        return true
    }

    override fun hashCode(): Int {
        var result = pcset.hashCode()
        result = 31 * result + name.hashCode()
        return result
    }

    override fun toString(): String {
        return "ScaleType(name='$name', intervals=$intervals)"
    }

    companion object {
        /**
         * Represents an empty or invalid scale type.
         */
        val NoScaleType = ScaleType(
            pcset = Pcset.EmptyPcset,
            name = "",
            aliases = emptyList(),
            intervals = emptyList()
        )
    }
}

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
        val pcset = PcSet.get(intervals)
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

private object ScalesData {
    val SCALES = listOf(
        // Basic scales
        listOf("1P 2M 3M 5P 6M", "major pentatonic", "pentatonic"),
        listOf("1P 2M 3M 4P 5P 6M 7M", "major", "ionian"),
        listOf("1P 2M 3m 4P 5P 6m 7m", "minor", "aeolian"),

        // Jazz common scales
        listOf("1P 2M 3m 3M 5P 6M", "major blues"),
        listOf("1P 3m 4P 5d 5P 7m", "minor blues", "blues"),
        listOf("1P 2M 3m 4P 5P 6M 7M", "melodic minor"),
        listOf("1P 2M 3m 4P 5P 6m 7M", "harmonic minor"),
        listOf("1P 2M 3M 4P 5P 6M 7m 7M", "bebop"),
        listOf("1P 2M 3m 4P 5d 6m 6M 7M", "diminished", "whole-half diminished"),

        // Modes
        listOf("1P 2M 3m 4P 5P 6M 7m", "dorian"),
        listOf("1P 2M 3M 4A 5P 6M 7M", "lydian"),
        listOf("1P 2M 3M 4P 5P 6M 7m", "mixolydian", "dominant"),
        listOf("1P 2m 3m 4P 5P 6m 7m", "phrygian"),
        listOf("1P 2m 3m 4P 5d 6m 7m", "locrian"),

        // 5-note scales
        listOf("1P 3M 4P 5P 7M", "ionian pentatonic"),
        listOf("1P 3M 4P 5P 7m", "mixolydian pentatonic", "indian"),
        listOf("1P 2M 4P 5P 6M", "ritusen"),
        listOf("1P 2M 4P 5P 7m", "egyptian"),
        listOf("1P 3M 4P 5d 7m", "neopolitan major pentatonic"),
        listOf("1P 3m 4P 5P 6m", "vietnamese 1"),
        listOf("1P 2m 3m 5P 6m", "pelog"),
        listOf("1P 2m 4P 5P 6m", "kumoijoshi"),
        listOf("1P 2M 3m 5P 6m", "hirajoshi"),
        listOf("1P 2m 4P 5d 7m", "iwato"),
        listOf("1P 2m 4P 5P 7m", "in-sen"),
        listOf("1P 3M 4A 5P 7M", "lydian pentatonic", "chinese"),
        listOf("1P 3m 4P 6m 7m", "malkos raga"),
        listOf("1P 3m 4P 5d 7m", "locrian pentatonic", "minor seven flat five pentatonic"),
        listOf("1P 3m 4P 5P 7m", "minor pentatonic", "vietnamese 2"),
        listOf("1P 3m 4P 5P 6M", "minor six pentatonic"),
        listOf("1P 2M 3m 5P 6M", "flat three pentatonic", "kumoi"),
        listOf("1P 2M 3M 5P 6m", "flat six pentatonic"),
        listOf("1P 2m 3M 5P 6M", "scriabin"),
        listOf("1P 3M 5d 6m 7m", "whole tone pentatonic"),
        listOf("1P 3M 4A 5A 7M", "lydian #5P pentatonic"),
        listOf("1P 3M 4A 5P 7m", "lydian dominant pentatonic"),
        listOf("1P 3m 4P 5P 7M", "minor #7M pentatonic"),
        listOf("1P 3m 4d 5d 7m", "super locrian pentatonic"),

        // 6-note scales
        listOf("1P 2M 3m 4P 5P 7M", "minor hexatonic"),
        listOf("1P 2A 3M 5P 5A 7M", "augmented"),
        listOf("1P 2M 4P 5P 6M 7m", "piongio"),
        listOf("1P 2m 3M 4A 6M 7m", "prometheus neopolitan"),
        listOf("1P 2M 3M 4A 6M 7m", "prometheus"),
        listOf("1P 2m 3M 5d 6m 7m", "mystery #1"),
        listOf("1P 2m 3M 4P 5A 6M", "six tone symmetric"),
        listOf("1P 2M 3M 4A 5A 6A", "whole tone", "messiaen's mode #1"),
        listOf("1P 2m 4P 4A 5P 7M", "messiaen's mode #5"),

        // 7-note scales
        listOf("1P 2M 3M 4P 5d 6m 7m", "locrian major", "arabian"),
        listOf("1P 2m 3M 4A 5P 6m 7M", "double harmonic lydian"),
        listOf("1P 2m 2A 3M 4A 6m 7m", "altered", "super locrian", "diminished whole tone", "pomeroy"),
        listOf("1P 2M 3m 4P 5d 6m 7m", "locrian #2", "half-diminished", "aeolian b5"),
        listOf("1P 2M 3M 4P 5P 6m 7m", "mixolydian b6", "melodic minor fifth mode", "hindu"),
        listOf("1P 2M 3M 4A 5P 6M 7m", "lydian dominant", "lydian b7", "overtone"),
        listOf("1P 2M 3M 4A 5A 6M 7M", "lydian augmented"),
        listOf("1P 2m 3m 4P 5P 6M 7m", "dorian b2", "phrygian #6", "melodic minor second mode"),
        listOf("1P 2m 3m 4d 5d 6m 7d", "ultralocrian", "superlocrian bb7", "superlocrian diminished"),
        listOf("1P 2m 3m 4P 5d 6M 7m", "locrian 6", "locrian natural 6", "locrian sharp 6"),
        listOf("1P 2A 3M 4P 5P 5A 7M", "augmented heptatonic"),
        listOf("1P 2M 3m 4A 5P 6M 7m", "dorian #4", "ukrainian dorian", "romanian minor", "altered dorian"),
        listOf("1P 2M 3m 4A 5P 6M 7M", "lydian diminished"),
        listOf("1P 2M 3M 4A 5A 7m 7M", "leading whole tone"),
        listOf("1P 2M 3M 4A 5P 6m 7m", "lydian minor"),
        listOf("1P 2m 3M 4P 5P 6m 7m", "phrygian dominant", "spanish", "phrygian major"),
        listOf("1P 2m 3m 4P 5P 6m 7M", "balinese"),
        listOf("1P 2m 3m 4P 5P 6M 7M", "neopolitan major"),
        listOf("1P 2M 3M 4P 5P 6m 7M", "harmonic major"),
        listOf("1P 2m 3M 4P 5P 6m 7M", "double harmonic major", "gypsy"),
        listOf("1P 2M 3m 4A 5P 6m 7M", "hungarian minor"),
        listOf("1P 2A 3M 4A 5P 6M 7m", "hungarian major"),
        listOf("1P 2m 3M 4P 5d 6M 7m", "oriental"),
        listOf("1P 2m 3m 3M 4A 5P 7m", "flamenco"),
        listOf("1P 2m 3m 4A 5P 6m 7M", "todi raga"),
        listOf("1P 2m 3M 4P 5d 6m 7M", "persian"),
        listOf("1P 2m 3M 5d 6m 7m 7M", "enigmatic"),
        listOf("1P 2M 3M 4P 5A 6M 7M", "major augmented", "major #5", "ionian augmented", "ionian #5"),
        listOf("1P 2A 3M 4A 5P 6M 7M", "lydian #9"),

        // 8-note scales
        listOf("1P 2m 2M 4P 4A 5P 6m 7M", "messiaen's mode #4"),
        listOf("1P 2m 3M 4P 4A 5P 6m 7M", "purvi raga"),
        listOf("1P 2m 3m 3M 4P 5P 6m 7m", "spanish heptatonic"),
        listOf("1P 2M 3m 3M 4P 5P 6M 7m", "bebop minor"),
        listOf("1P 2M 3M 4P 5P 5A 6M 7M", "bebop major"),
        listOf("1P 2m 3m 4P 5d 5P 6m 7m", "bebop locrian"),
        listOf("1P 2M 3m 4P 5P 6m 7m 7M", "minor bebop"),
        listOf("1P 2M 3M 4P 5d 5P 6M 7M", "ichikosucho"),
        listOf("1P 2M 3m 4P 5P 6m 6M 7M", "minor six diminished"),
        listOf("1P 2m 3m 3M 4A 5P 6M 7m", "half-whole diminished", "dominant diminished", "messiaen's mode #2"),
        listOf("1P 3m 3M 4P 5P 6M 7m 7M", "kafi raga"),
        listOf("1P 2M 3M 4P 4A 5A 6A 7M", "messiaen's mode #6"),

        // 9-note scales
        listOf("1P 2M 3m 3M 4P 5d 5P 6M 7m", "composite blues"),
        listOf("1P 2M 3m 3M 4A 5P 6m 7m 7M", "messiaen's mode #3"),

        // 10-note scales
        listOf("1P 2m 2M 3m 4P 4A 5P 6m 6M 7M", "messiaen's mode #7"),

        // 12-note scales
        listOf("1P 2m 2M 3m 3M 4P 5d 5P 6m 6M 7m 7M", "chromatic"),
    )
}

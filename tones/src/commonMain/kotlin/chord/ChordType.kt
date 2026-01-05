package io.peekandpoke.klang.tones.chord

import io.peekandpoke.klang.tones.pcset.PcSet
import io.peekandpoke.klang.tones.pcset.Pcset

/**
 * Represents the quality of a chord.
 */
enum class ChordQuality {
    Major, Minor, Augmented, Diminished, Unknown;

    override fun toString(): String = name
}

/**
 * Properties for a chord type in the chord dictionary.
 *
 * @property pcset The pitch class set of the chord.
 * @property name The full name of the chord (e.g., "major seventh").
 * @property quality The quality of the chord (Major, Minor, etc.).
 * @property aliases A list of alternative names/symbols for the chord (e.g., "maj7", "^7").
 * @property intervals The list of intervals that define the chord.
 */
class ChordType(
    val pcset: Pcset,
    val name: String,
    val quality: ChordQuality,
    val aliases: List<String>,
    val intervals: List<String>,
) {
    val empty: Boolean get() = pcset.empty
    val setNum: Int get() = pcset.setNum
    val chroma: String get() = pcset.chroma
    val normalized: String get() = pcset.normalized

    companion object {
        /**
         * Represents an empty or invalid chord type.
         */
        val NoChordType = ChordType(
            pcset = Pcset.EmptyPcset,
            name = "",
            quality = ChordQuality.Unknown,
            aliases = emptyList(),
            intervals = emptyList()
        )
    }
}

/**
 * Dictionary of all chord types.
 */
object ChordTypeDictionary {
    private val dictionary = mutableListOf<ChordType>()
    private val index = mutableMapOf<String, ChordType>()

    init {
        reset()
    }

    /**
     * Given a chord name or chroma or set number, return the chord properties.
     */
    fun get(type: Any?): ChordType {
        return index[type.toString()] ?: ChordType.NoChordType
    }

    /**
     * Get all chord (long) names.
     */
    fun names(): List<String> = dictionary.map { it.name }.filter { it.isNotEmpty() }

    /**
     * Get all chord symbols.
     */
    fun symbols(): List<String> = dictionary.map { it.aliases.firstOrNull() ?: "" }.filter { it.isNotEmpty() }

    /**
     * Return a list of all chord types.
     */
    fun all(): List<ChordType> = dictionary.toList()

    /**
     * Keys used to reference chord types.
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
        ChordsData.CHORDS.forEach { row ->
            val ivls = row[0].split(" ")
            val fullName = row[1]
            val aliases = row[2].split(" ")
            add(ivls, aliases, fullName)
        }
        dictionary.sortBy { it.setNum }
    }

    /**
     * Add a chord to the dictionary.
     */
    fun add(intervals: List<String>, aliases: List<String>, fullName: String = ""): ChordType {
        val quality = getQuality(intervals)
        val pcset = PcSet.get(intervals)
        val chord = ChordType(pcset, fullName, quality, aliases, intervals)

        dictionary.add(chord)
        if (chord.name.isNotEmpty()) {
            index[chord.name] = chord
        }
        index[chord.setNum.toString()] = chord
        index[chord.chroma] = chord
        chord.aliases.forEach { alias ->
            index[alias] = chord
        }
        return chord
    }

    private fun getQuality(intervals: List<String>): ChordQuality {
        return when {
            "5A" in intervals -> ChordQuality.Augmented
            "3M" in intervals -> ChordQuality.Major
            "5d" in intervals -> ChordQuality.Diminished
            "3m" in intervals -> ChordQuality.Minor
            else -> ChordQuality.Unknown
        }
    }
}

private object ChordsData {
    val CHORDS = listOf(
        // ==Major==
        listOf("1P 3M 5P", "major", "M ^  maj"),
        listOf("1P 3M 5P 7M", "major seventh", "maj7 Δ ma7 M7 Maj7 ^7"),
        listOf("1P 3M 5P 7M 9M", "major ninth", "maj9 Δ9 ^9"),
        listOf("1P 3M 5P 7M 9M 13M", "major thirteenth", "maj13 Maj13 ^13"),
        listOf("1P 3M 5P 6M", "sixth", "6 add6 add13 M6"),
        listOf("1P 3M 5P 6M 9M", "sixth added ninth", "6add9 6/9 69 M69"),
        listOf("1P 3M 6m 7M", "major seventh flat sixth", "M7b6 ^7b6"),
        listOf("1P 3M 5P 7M 11A", "major seventh sharp eleventh", "maj#4 Δ#4 Δ#11 M7#11 ^7#11 maj7#11"),

        // ==Minor==
        // '''Normal'''
        listOf("1P 3m 5P", "minor", "m min -"),
        listOf("1P 3m 5P 7m", "minor seventh", "m7 min7 mi7 -7"),
        listOf("1P 3m 5P 7M", "minor/major seventh", "m/ma7 m/maj7 mM7 mMaj7 m/M7 -Δ7 mΔ -^7 -maj7"),
        listOf("1P 3m 5P 6M", "minor sixth", "m6 -6"),
        listOf("1P 3m 5P 7m 9M", "minor ninth", "m9 -9"),
        listOf("1P 3m 5P 7M 9M", "minor/major ninth", "mM9 mMaj9 -^9"),
        listOf("1P 3m 5P 7m 9M 11P", "minor eleventh", "m11 -11"),
        listOf("1P 3m 5P 7m 9M 13M", "minor thirteenth", "m13 -13"),

        // '''Diminished'''
        listOf("1P 3m 5d", "diminished", "dim ° o"),
        listOf("1P 3m 5d 7d", "diminished seventh", "dim7 °7 o7"),
        listOf("1P 3m 5d 7m", "half-diminished", "m7b5 ø -7b5 h7 h"),

        // ==Dominant/Seventh==
        // '''Normal'''
        listOf("1P 3M 5P 7m", "dominant seventh", "7 dom"),
        listOf("1P 3M 5P 7m 9M", "dominant ninth", "9"),
        listOf("1P 3M 5P 7m 9M 13M", "dominant thirteenth", "13"),
        listOf("1P 3M 5P 7m 11A", "lydian dominant seventh", "7#11 7#4"),

        // '''Altered'''
        listOf("1P 3M 5P 7m 9m", "dominant flat ninth", "7b9"),
        listOf("1P 3M 5P 7m 9A", "dominant sharp ninth", "7#9"),
        listOf("1P 3M 7m 9m", "altered", "alt7"),

        // '''Suspended'''
        listOf("1P 4P 5P", "suspended fourth", "sus4 sus"),
        listOf("1P 2M 5P", "suspended second", "sus2"),
        listOf("1P 4P 5P 7m", "suspended fourth seventh", "7sus4 7sus"),
        listOf("1P 5P 7m 9M 11P", "eleventh", "11"),
        listOf("1P 4P 5P 7m 9m", "suspended fourth flat ninth", "b9sus phryg 7b9sus 7b9sus4"),

        // ==Other==
        listOf("1P 5P", "fifth", "5"),
        listOf("1P 3M 5A", "augmented", "aug + +5 ^#5"),
        listOf("1P 3m 5A", "minor augmented", "m#5 -#5 m+"),
        listOf("1P 3M 5A 7M", "augmented seventh", "maj7#5 maj7+5 +maj7 ^7#5"),
        listOf("1P 3M 5P 7M 9M 11A", "major sharp eleventh (lydian)", "maj9#11 Δ9#11 ^9#11"),

        // ==Legacy==
        listOf("1P 2M 4P 5P", "", "sus24 sus4add9"),
        listOf("1P 3M 5A 7M 9M", "", "maj9#5 Maj9#5"),
        listOf("1P 3M 5A 7m", "", "7#5 +7 7+ 7aug aug7"),
        listOf("1P 3M 5A 7m 9A", "", "7#5#9 7#9#5 7alt"),
        listOf("1P 3M 5A 7m 9M", "", "9#5 9+"),
        listOf("1P 3M 5A 7m 9M 11A", "", "9#5#11"),
        listOf("1P 3M 5A 7m 9m", "", "7#5b9 7b9#5"),
        listOf("1P 3M 5A 7m 9m 11A", "", "7#5b9#11"),
        listOf("1P 3M 5A 9A", "", "+add#9"),
        listOf("1P 3M 5A 9M", "", "M#5add9 +add9"),
        listOf("1P 3M 5P 6M 11A", "", "M6#11 M6b5 6#11 6b5"),
        listOf("1P 3M 5P 6M 7M 9M", "", "M7add13"),
        listOf("1P 3M 5P 6M 9M 11A", "", "69#11"),
        listOf("1P 3m 5P 6M 9M", "", "m69 -69"),
        listOf("1P 3M 5P 6m 7m", "", "7b6"),
        listOf("1P 3M 5P 7M 9A 11A", "", "maj7#9#11"),
        listOf("1P 3M 5P 7M 9M 11A 13M", "", "M13#11 maj13#11 M13+4 M13#4"),
        listOf("1P 3M 5P 7M 9m", "", "M7b9"),
        listOf("1P 3M 5P 7m 11A 13m", "", "7#11b13 7b5b13"),
        listOf("1P 3M 5P 7m 13M", "", "7add6 67 7add13"),
        listOf("1P 3M 5P 7m 9A 11A", "", "7#9#11 7b5#9 7#9b5"),
        listOf("1P 3M 5P 7m 9A 11A 13M", "", "13#9#11"),
        listOf("1P 3M 5P 7m 9A 11A 13m", "", "7#9#11b13"),
        listOf("1P 3M 5P 7m 9A 13M", "", "13#9"),
        listOf("1P 3M 5P 7m 9A 13m", "", "7#9b13"),
        listOf("1P 3M 5P 7m 9M 11A", "", "9#11 9+4 9#4"),
        listOf("1P 3M 5P 7m 9M 11A 13M", "", "13#11 13+4 13#4"),
        listOf("1P 3M 5P 7m 9M 11A 13m", "", "9#11b13 9b5b13"),
        listOf("1P 3M 5P 7m 9m 11A", "", "7b9#11 7b5b9 7b9b5"),
        listOf("1P 3M 5P 7m 9m 11A 13M", "", "13b9#11"),
        listOf("1P 3M 5P 7m 9m 11A 13m", "", "7b9b13#11 7b9#11b13 7b5b9b13"),
        listOf("1P 3M 5P 7m 9m 13M", "", "13b9"),
        listOf("1P 3M 5P 7m 9m 13m", "", "7b9b13"),
        listOf("1P 3M 5P 7m 9m 9A", "", "7b9#9"),
        listOf("1P 3M 5P 9M", "", "Madd9 2 add9 add2"),
        listOf("1P 3M 5P 9m", "", "Maddb9"),
        listOf("1P 3M 5d", "", "Mb5"),
        listOf("1P 3M 5d 6M 7m 9M", "", "13b5"),
        listOf("1P 3M 5d 7M", "", "M7b5"),
        listOf("1P 3M 5d 7M 9M", "", "M9b5"),
        listOf("1P 3M 5d 7m", "", "7b5"),
        listOf("1P 3M 5d 7m 9M", "", "9b5"),
        listOf("1P 3M 7m", "", "7no5"),
        listOf("1P 3M 7m 13m", "", "7b13"),
        listOf("1P 3M 7m 9M", "", "9no5"),
        listOf("1P 3M 7m 9M 13M", "", "13no5"),
        listOf("1P 3M 7m 9M 13m", "", "9b13"),
        listOf("1P 3m 4P 5P", "", "madd4"),
        listOf("1P 3m 5P 6m 7M", "", "mMaj7b6"),
        listOf("1P 3m 5P 6m 7M 9M", "", "mMaj9b6"),
        listOf("1P 3m 5P 7m 11P", "", "m7add11 m7add4"),
        listOf("1P 3m 5P 9M", "", "madd9"),
        listOf("1P 3m 5d 6M 7M", "", "o7M7"),
        listOf("1P 3m 5d 7M", "", "oM7"),
        listOf("1P 3m 6m 7M", "", "mb6M7"),
        listOf("1P 3m 6m 7m", "", "m7#5"),
        listOf("1P 3m 6m 7m 9M", "", "m9#5"),
        listOf("1P 3m 5A 7m 9M 11P", "", "m11A"),
        listOf("1P 3m 6m 9m", "", "mb6b9"),
        listOf("1P 2M 3m 5d 7m", "", "m9b5"),
        listOf("1P 4P 5A 7M", "", "M7#5sus4"),
        listOf("1P 4P 5A 7M 9M", "", "M9#5sus4"),
        listOf("1P 4P 5A 7m", "", "7#5sus4"),
        listOf("1P 4P 5P 7M", "", "M7sus4"),
        listOf("1P 4P 5P 7M 9M", "", "M9sus4"),
        listOf("1P 4P 5P 7m 9M", "", "9sus4 9sus"),
        listOf("1P 4P 5P 7m 9M 13M", "", "13sus4 13sus"),
        listOf("1P 4P 5P 7m 9m 13m", "", "7sus4b9b13 7b9b13sus4"),
        listOf("1P 4P 7m 10m", "", "4 quartal"),
        listOf("1P 5P 7m 9m 11P", "", "11b9")
    )
}

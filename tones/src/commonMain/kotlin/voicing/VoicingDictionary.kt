package io.peekandpoke.klang.tones.voicing

import io.peekandpoke.klang.tones.chord.Chord

typealias VoicingDictionary = Map<String, List<String>>

object VoicingDictionaries {
    /** Standard triad voicings (root, first and second inversions). */
    val triads: VoicingDictionary = mapOf(
        "M" to listOf("1P 3M 5P", "3M 5P 8P", "5P 8P 10M"),
        "m" to listOf("1P 3m 5P", "3m 5P 8P", "5P 8P 10m"),
        "o" to listOf("1P 3m 5d", "3m 5d 8P", "5d 8P 10m"),
        "aug" to listOf("1P 3M 5A", "3M 5A 8P", "5A 8P 10M")
    )

    /** Standard jazz left-hand (rootless) voicings. */
    val lefthand: VoicingDictionary = mapOf(
        "m7" to listOf("3m 5P 7m 9M", "7m 9M 10m 12P"),
        "7" to listOf("3M 6M 7m 9M", "7m 9M 10M 13M"),
        "^7" to listOf("3M 5P 7M 9M", "7M 9M 10M 12P"),
        "69" to listOf("3M 5P 6A 9M"),
        "m7b5" to listOf("3m 5d 7m 8P", "7m 8P 10m 12d"),
        "7b9" to listOf("3M 6m 7m 9m", "7m 9m 10M 13m"),
        "7b13" to listOf("3M 6m 7m 9m", "7m 9m 10M 13m"),
        "o7" to listOf("1P 3m 5d 6M", "5d 6M 8P 10m"),
        "7#11" to listOf("7m 9M 11A 13A"),
        "7#9" to listOf("3M 7m 9A"),
        "mM7" to listOf("3m 5P 7M 9M", "7M 9M 10m 12P"),
        "m6" to listOf("3m 5P 6M 9M", "6M 9M 10m 12P")
    )

    /** All available voicings. */
    val all: VoicingDictionary = triads + lefthand

    /** The default dictionary to use for lookups. */
    val defaultDictionary: VoicingDictionary = lefthand

    /**
     * Lookup a symbol in a dictionary.
     */
    fun lookup(symbol: String, dictionary: VoicingDictionary = defaultDictionary): List<String>? {
        if (dictionary.containsKey(symbol)) {
            return dictionary[symbol]
        }

        // Try to match using aliases from chord type
        val c = Chord.get("C$symbol")
        if (c.empty) return null

        val match = dictionary.keys.find { it in c.aliases }
        return if (match != null) dictionary[match] else null
    }
}

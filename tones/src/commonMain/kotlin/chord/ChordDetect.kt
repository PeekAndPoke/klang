package io.peekandpoke.klang.tones.chord

import io.peekandpoke.klang.tones.note.Note
import io.peekandpoke.klang.tones.pcset.PcSet

/**
 * Represents a chord found during detection.
 *
 * @property weight The weight/probability of the chord (1.0 for root position, 0.5 for inversions).
 * @property name The name/symbol of the chord.
 */
data class FoundChord(val weight: Double, val name: String)

/**
 * Options for chord detection.
 *
 * @property assumePerfectFifth If true, will find chords even if the perfect fifth is missing.
 */
data class DetectOptions(
    val assumePerfectFifth: Boolean = false,
)

/**
 * Chord detection utilities.
 */
object ChordDetect {
    /**
     * Detect a chord from a list of notes.
     *
     * @param source A list of note names.
     * @param options Detection options.
     * @return A list of possible chord names, sorted by probability.
     */
    fun detect(source: List<String>, options: DetectOptions = DetectOptions()): List<String> {
        // 1. Get pitch classes of all notes and filter out invalid ones
        val notes = source.map { Note.get(it).pc }.filter { it.isNotEmpty() }
        if (notes.isEmpty()) {
            return emptyList()
        }

        // 2. Find matches for the given notes
        val found = findMatches(notes, 1.0, options)

        // 3. Filter out zero-weight results, sort by weight descending, and return the names
        return found
            .filter { it.weight > 0 }
            .sortedByDescending { it.weight }
            .map { it.name }
    }

    /**
     * Bitmask values for chord detection.
     */
    private object Bitmask {
        // 3m 000100000000 -> index 3 (from left? No, Tonal uses binary string "1000..." where index 0 is C)
        // TonalJS chroma: "100010010000" for C major. C is index 0.
        // In parseInt(chroma, 2), "100010010000" -> 2192
        // C=2048, C#=1024, D=512, Eb=256, E=128, F=64, F#=32, G=16, Ab=8, A=4, Bb=2, B=1

        // anyThirds: 3m (Eb) = 256, 3M (E) = 128. 256 + 128 = 384.
        const val ANY_THIRDS = 384

        // perfectFifth: 5P (G) = 16.
        const val PERFECT_FIFTH = 16

        // nonPerfectFifths: 5d (Gb) = 32, 5A (G#) = 8. 32 + 8 = 40.
        const val NON_PERFECT_FIFTHS = 40

        // anySeventh: 7m (Bb) = 2, 7M (B) = 1. 2 + 1 = 3.
        const val ANY_SEVENTH = 3
    }

    /**
     * Creates a function that tests if a chroma number contains specific bits.
     */
    private fun testChromaNumber(bitmask: Int): (Int) -> Boolean = { chromaNumber ->
        (chromaNumber and bitmask) != 0
    }

    private val hasAnyThird = testChromaNumber(Bitmask.ANY_THIRDS)
    private val hasPerfectFifth = testChromaNumber(Bitmask.PERFECT_FIFTH)
    private val hasAnySeventh = testChromaNumber(Bitmask.ANY_SEVENTH)
    private val hasNonPerfectFifth = testChromaNumber(Bitmask.NON_PERFECT_FIFTHS)

    /**
     * Returns true if the chord type has a third, a perfect fifth, and a seventh.
     */
    private fun hasAnyThirdAndPerfectFifthAndAnySeventh(chordType: ChordType): Boolean {
        val chromaNumber = chordType.chroma.toInt(2)
        return hasAnyThird(chromaNumber) &&
                hasPerfectFifth(chromaNumber) &&
                hasAnySeventh(chromaNumber)
    }

    /**
     * Adds a perfect fifth to a chroma if it doesn't already have a non-perfect fifth.
     */
    private fun withPerfectFifth(chroma: String): String {
        val chromaNumber = chroma.toInt(2)
        return if (hasNonPerfectFifth(chromaNumber)) {
            chroma
        } else {
            (chromaNumber or 16).toString(2).padStart(12, '0')
        }
    }

    /**
     * Internal function to find matching chord types for a set of notes.
     *
     * @param notes The list of pitch classes to match.
     * @param weight The base weight for these matches.
     * @param options Detection options.
     */
    @Suppress("SameParameterValue")
    private fun findMatches(
        notes: List<String>,
        weight: Double,
        options: DetectOptions,
    ): List<FoundChord> {
        // The first note is considered the tonic for the purpose of identifying inversions
        val tonic = notes[0]
        val tonicChroma = Note.get(tonic).chroma

        // Map chroma to note names for later use in naming the found chords
        val pcToName = mutableMapOf<Int, String>()
        notes.forEach { n ->
            val chroma = Note.get(n).chroma
            if (chroma != -1) {
                if (!pcToName.containsKey(chroma)) {
                    pcToName[chroma] = Note.get(n).name
                }
            }
        }

        // Generate all modes (rotations) of the pitch class set to test each note as a potential root
        val allModes = PcSet.modes(notes, false)

        val found = mutableListOf<FoundChord>()
        allModes.forEachIndexed { index, mode ->
            // If optioned, assume a perfect fifth if it's missing (useful for power chords or incomplete voicings)
            val modeWithPerfectFifth = if (options.assumePerfectFifth) withPerfectFifth(mode) else mode

            // Find all chord types that match the current mode's chroma
            val chordTypes = ChordTypeDictionary.all().filter { chordType ->
                if (options.assumePerfectFifth && hasAnyThirdAndPerfectFifthAndAnySeventh(chordType)) {
                    // Match against the augmented chroma if it's a "standard" chord type
                    chordType.chroma == modeWithPerfectFifth
                } else {
                    // Standard exact match
                    chordType.chroma == mode
                }
            }

            chordTypes.forEach { chordType ->
                val chordAlias = chordType.aliases.firstOrNull() ?: ""
                val baseNote = pcToName[index]
                if (baseNote != null) {
                    // If the current root (baseNote) is not the original tonic, it's an inversion
                    val isInversion = index != tonicChroma
                    if (isInversion) {
                        found.add(
                            FoundChord(
                                weight = 0.5 * weight,
                                name = "$baseNote$chordAlias/$tonic"
                            )
                        )
                    } else {
                        found.add(
                            FoundChord(
                                weight = 1.0 * weight,
                                name = "$baseNote$chordAlias"
                            )
                        )
                    }
                }
            }
        }

        return found
    }
}

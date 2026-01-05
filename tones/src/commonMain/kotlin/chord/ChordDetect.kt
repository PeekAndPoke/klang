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

object ChordDetect {
    /**
     * Detect a chord from a list of notes.
     */
    fun detect(source: List<String>, options: DetectOptions = DetectOptions()): List<String> {
        val notes = source.map { Note.get(it).pc }.filter { it.isNotEmpty() }
        if (notes.isEmpty()) {
            return emptyList()
        }

        val found = findMatches(notes, 1.0, options)

        return found
            .filter { it.weight > 0 }
            .sortedByDescending { it.weight }
            .map { it.name }
    }

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

    private fun testChromaNumber(bitmask: Int): (Int) -> Boolean = { chromaNumber ->
        (chromaNumber and bitmask) != 0
    }

    private val hasAnyThird = testChromaNumber(Bitmask.ANY_THIRDS)
    private val hasPerfectFifth = testChromaNumber(Bitmask.PERFECT_FIFTH)
    private val hasAnySeventh = testChromaNumber(Bitmask.ANY_SEVENTH)
    private val hasNonPerfectFifth = testChromaNumber(Bitmask.NON_PERFECT_FIFTHS)

    private fun hasAnyThirdAndPerfectFifthAndAnySeventh(chordType: ChordType): Boolean {
        val chromaNumber = chordType.chroma.toInt(2)
        return hasAnyThird(chromaNumber) &&
                hasPerfectFifth(chromaNumber) &&
                hasAnySeventh(chromaNumber)
    }

    private fun withPerfectFifth(chroma: String): String {
        val chromaNumber = chroma.toInt(2)
        return if (hasNonPerfectFifth(chromaNumber)) {
            chroma
        } else {
            (chromaNumber or 16).toString(2).padStart(12, '0')
        }
    }

    private fun findMatches(
        notes: List<String>,
        weight: Double,
        options: DetectOptions,
    ): List<FoundChord> {
        val tonic = notes[0]
        val tonicChroma = Note.get(tonic).chroma

        val pcToName = mutableMapOf<Int, String>()
        notes.forEach { n ->
            val chroma = Note.get(n).chroma
            if (chroma != -1) {
                if (!pcToName.containsKey(chroma)) {
                    pcToName[chroma] = Note.get(n).name
                }
            }
        }

        // we need to test all chromas to get the correct baseNote
        val allModes = PcSet.modes(notes, false)

        val found = mutableListOf<FoundChord>()
        allModes.forEachIndexed { index, mode ->
            val modeWithPerfectFifth = if (options.assumePerfectFifth) withPerfectFifth(mode) else mode

            // some chords could have the same chroma but different interval spelling
            val chordTypes = ChordTypeDictionary.all().filter { chordType ->
                if (options.assumePerfectFifth && hasAnyThirdAndPerfectFifthAndAnySeventh(chordType)) {
                    chordType.chroma == modeWithPerfectFifth
                } else {
                    chordType.chroma == mode
                }
            }

            chordTypes.forEach { chordType ->
                val chordAlias = chordType.aliases.firstOrNull() ?: ""
                val baseNote = pcToName[index]
                if (baseNote != null) {
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

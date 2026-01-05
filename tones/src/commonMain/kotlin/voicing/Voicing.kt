package io.peekandpoke.klang.tones.voicing

import io.peekandpoke.klang.tones.chord.Chord
import io.peekandpoke.klang.tones.distance.Distance
import io.peekandpoke.klang.tones.interval.Interval
import io.peekandpoke.klang.tones.note.Note
import io.peekandpoke.klang.tones.utils.TonesRange

object Voicing {
    /** Default range for searching voicings. */
    private val defaultRange = listOf("C3", "C5")

    /** Default voicing dictionary (all available). */
    private val defaultDictionary = VoicingDictionaries.all

    /** Default voice leading strategy (smallest top note difference). */
    private val defaultVoiceLeading = VoiceLeading.topNoteDiff

    /**
     * Get a voicing for a chord.
     *
     * @param chord The chord symbol (e.g., "Cmaj7").
     * @param range The pitch range for the voicing (e.g., ["C3", "C5"]).
     * @param dictionary The voicing dictionary to use.
     * @param voiceLeading The voice leading strategy to use.
     * @param lastVoicing The previous voicing, used for voice leading.
     */
    fun get(
        chord: String,
        range: List<String> = defaultRange,
        dictionary: VoicingDictionary = defaultDictionary,
        voiceLeading: VoiceLeadingFunction = defaultVoiceLeading,
        lastVoicing: List<String> = emptyList(),
    ): List<String> {
        val voicings = search(chord, range, dictionary)
        return if (lastVoicing.isEmpty()) {
            // If no previous voicing, pick the first one found
            voicings.firstOrNull() ?: emptyList()
        } else {
            // Apply voice leading strategy to choose the best voicing
            voiceLeading(voicings, lastVoicing)
        }
    }

    /**
     * Search for all possible voicings for a chord in a given range.
     *
     * @param chord The chord symbol (e.g., "Cmaj7").
     * @param range The pitch range to search within.
     * @param dictionary The voicing dictionary to use.
     */
    fun search(
        chord: String,
        range: List<String> = defaultRange,
        dictionary: VoicingDictionary = defaultDictionary,
    ): List<List<String>> {
        val (tonic, symbol, _) = Chord.tokenize(chord)
        val sets = VoicingDictionaries.lookup(symbol, dictionary) ?: return emptyList()

        // Voicing intervals/notes from dictionary
        val voicings = sets.map { it.split(" ") }
        val notesInRange = TonesRange.chromatic(range)

        val result = voicings.flatMap { voicing ->
            if (voicing.isEmpty()) return@flatMap emptyList()

            // Calculate intervals relative to the bottom note of the voicing
            val relativeIntervals = voicing.map {
                Interval.subtract(it, voicing[0])
            }

            // Find all instances of the bottom note within the specified range
            val bottomPitchClass = Distance.transpose(tonic, voicing[0])
            val bottomChroma = Note.get(bottomPitchClass).chroma
            val topMidiInRange = Note.get(range.last()).midi ?: 0

            val starts = notesInRange
                .filter { Note.get(it).chroma == bottomChroma }
                .filter { start ->
                    // Check if the top note of the transposed voicing fits in range
                    val topNote = Distance.transpose(start, relativeIntervals.last())
                    (Note.get(topNote).midi ?: 0) <= topMidiInRange
                }
                .map { Note.enharmonic(it, bottomPitchClass) }

            // Transpose the voicing starting from each valid start note
            starts.map { start ->
                relativeIntervals.map { interval -> Distance.transpose(start, interval) }
            }
        }

        return result
    }

    /**
     * Get a sequence of voicings for a list of chords.
     */
    fun sequence(
        chords: List<String>,
        range: List<String> = defaultRange,
        dictionary: VoicingDictionary = defaultDictionary,
        voiceLeading: VoiceLeadingFunction = defaultVoiceLeading,
        lastVoicing: List<String> = emptyList(),
    ): List<List<String>> {
        val result = mutableListOf<List<String>>()
        var currentLast = lastVoicing

        for (chord in chords) {
            val voicing = get(chord, range, dictionary, voiceLeading, currentLast)
            result.add(voicing)
            currentLast = voicing
        }

        return result
    }
}

package io.peekandpoke.klang.tones.voicing

import io.peekandpoke.klang.tones.chord.Chord
import io.peekandpoke.klang.tones.distance.Distance
import io.peekandpoke.klang.tones.interval.Interval
import io.peekandpoke.klang.tones.note.Note
import io.peekandpoke.klang.tones.pitch.TonalPitch
import io.peekandpoke.klang.tones.range.TonalRange

object Voicing {
    private val defaultRange = listOf("C3", "C5")
    private val defaultDictionary = VoicingDictionaries.all
    private val defaultVoiceLeading = VoiceLeading.topNoteDiff

    /**
     * Get a voicing for a chord.
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
            voicings.firstOrNull() ?: emptyList()
        } else {
            voiceLeading(voicings, lastVoicing)
        }
    }

    /**
     * Search for all possible voicings for a chord in a given range.
     */
    fun search(
        chord: String,
        range: List<String> = defaultRange,
        dictionary: VoicingDictionary = defaultDictionary,
    ): List<List<String>> {
        val (tonic, symbol, _) = Chord.tokenize(chord)
        val sets = VoicingDictionaries.lookup(symbol, dictionary) ?: return emptyList()

        val voicings = sets.map { it.split(" ") }
        val notesInRange = TonalRange.chromatic(range.map { it as Any })

        val result = voicings.flatMap { voicing ->
            if (voicing.isEmpty()) return@flatMap emptyList<List<String>>()

            val relativeIntervals = voicing.map {
                Interval.subtract(it, voicing[0])
            }

            val bottomPitchClass = Distance.transpose(tonic, voicing[0])
            val bottomChroma = TonalPitch.chroma(Note.get(bottomPitchClass))
            val topMidiInRange = Note.get(range.last()).midi ?: 0

            val starts = notesInRange
                .filter { TonalPitch.chroma(Note.get(it)) == bottomChroma }
                .filter { start ->
                    val topNote = Distance.transpose(start, relativeIntervals.last())
                    (Note.get(topNote).midi ?: 0) <= topMidiInRange
                }
                .map { Note.enharmonic(it, bottomPitchClass) }

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

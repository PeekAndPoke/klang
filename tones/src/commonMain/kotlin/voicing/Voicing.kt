package io.peekandpoke.klang.tones.voicing

import io.peekandpoke.klang.tones.chord.tokenizeChord
import io.peekandpoke.klang.tones.distance.transpose
import io.peekandpoke.klang.tones.interval.subtract
import io.peekandpoke.klang.tones.note.enharmonic
import io.peekandpoke.klang.tones.note.note
import io.peekandpoke.klang.tones.pitch.chroma
import io.peekandpoke.klang.tones.pitch.midi
import io.peekandpoke.klang.tones.range.chromaticRange

private val defaultRange = listOf("C3", "C5")
private val defaultDictionary = VoicingDictionaries.all
private val defaultVoiceLeading = VoiceLeading.topNoteDiff

/**
 * Get a voicing for a chord.
 */
fun getVoicing(
    chord: String,
    range: List<String> = defaultRange,
    dictionary: VoicingDictionary = defaultDictionary,
    voiceLeading: VoiceLeadingFunction = defaultVoiceLeading,
    lastVoicing: List<String> = emptyList(),
): List<String> {
    val voicings = searchVoicings(chord, range, dictionary)
    return if (lastVoicing.isEmpty()) {
        voicings.firstOrNull() ?: emptyList()
    } else {
        voiceLeading(voicings, lastVoicing)
    }
}

/**
 * Search for all possible voicings for a chord in a given range.
 */
fun searchVoicings(
    chord: String,
    range: List<String> = defaultRange,
    dictionary: VoicingDictionary = VoicingDictionaries.triads,
): List<List<String>> {
    val (tonic, symbol, _) = tokenizeChord(chord)
    val sets = VoicingDictionaries.lookup(symbol, dictionary) ?: return emptyList()

    val voicings = sets.map { it.split(" ") }
    val notesInRange = chromaticRange(range.map { it as Any })

    val result = voicings.flatMap { voicing ->
        if (voicing.isEmpty()) return@flatMap emptyList<List<String>>()

        val relativeIntervals = voicing.map {
            subtract(it, voicing[0])
        }

        val bottomPitchClass = transpose(tonic, voicing[0])
        val bottomChroma = chroma(note(bottomPitchClass))
        val topMidiInRange = note(range.last()).midi ?: 0

        val starts = notesInRange
            .filter { chroma(note(it)) == bottomChroma }
            .filter { start ->
                val topNote = transpose(start, relativeIntervals.last())
                (midi(note(topNote)) ?: 0) <= topMidiInRange
            }
            .map { enharmonic(it, bottomPitchClass) }

        starts.map { start ->
            relativeIntervals.map { interval -> transpose(start, interval) }
        }
    }

    return result
}

/**
 * Get a sequence of voicings for a list of chords.
 */
fun sequenceVoicings(
    chords: List<String>,
    range: List<String> = defaultRange,
    dictionary: VoicingDictionary = defaultDictionary,
    voiceLeading: VoiceLeadingFunction = defaultVoiceLeading,
    lastVoicing: List<String> = emptyList(),
): List<List<String>> {
    val result = mutableListOf<List<String>>()
    var currentLast = lastVoicing

    for (chord in chords) {
        val voicing = getVoicing(chord, range, dictionary, voiceLeading, currentLast)
        result.add(voicing)
        currentLast = voicing
    }

    return result
}

package io.peekandpoke.klang.tones.voicing

import io.peekandpoke.klang.tones.note.Note
import io.peekandpoke.klang.tones.voicing.VoiceLeading.topNoteDiff
import kotlin.math.abs

/** A function that selects the best voicing from a list based on voice leading principles. */
typealias VoiceLeadingFunction = (voicings: List<List<String>>, lastVoicing: List<String>) -> List<String>

/** A function that ranks voicings best→worst based on voice leading principles. */
typealias VoiceLeadingRankFunction = (voicings: List<List<String>>, lastVoicing: List<String>) -> List<List<String>>

/**
 * Collection of voice leading algorithms for smooth voice transitions.
 */
object VoiceLeading {
    private fun topNoteMidi(voicing: List<String>): Int {
        if (voicing.isEmpty()) return 0
        return Note.get(voicing.last()).midi ?: 0
    }

    /**
     * Voice leading strategy that picks the voicing whose top note is closest
     * to the top note of the previous voicing.
     */
    val topNoteDiff: VoiceLeadingFunction = { voicings, lastVoicing ->
        if (lastVoicing.isEmpty()) {
            voicings.firstOrNull() ?: emptyList()
        } else {
            val lastTopMidi = topNoteMidi(lastVoicing)
            voicings.minByOrNull { voicing ->
                abs(lastTopMidi - topNoteMidi(voicing))
            } ?: emptyList()
        }
    }

    /**
     * Ranked counterpart to [topNoteDiff]: returns all voicings sorted ascending by
     * absolute top-note MIDI distance to the previous voicing. With no previous voicing,
     * returns the candidates in the input order (i.e. as supplied by the search).
     */
    val topNoteDiffRanked: VoiceLeadingRankFunction = { voicings, lastVoicing ->
        if (lastVoicing.isEmpty()) {
            voicings
        } else {
            val lastTopMidi = topNoteMidi(lastVoicing)
            voicings.sortedBy { voicing -> abs(lastTopMidi - topNoteMidi(voicing)) }
        }
    }
}

package io.peekandpoke.klang.tones.voicing

import io.peekandpoke.klang.tones.note.Note
import kotlin.math.abs

/** A function that selects the best voicing from a list based on voice leading principles. */
typealias VoiceLeadingFunction = (voicings: List<List<String>>, lastVoicing: List<String>) -> List<String>

/**
 * Collection of voice leading algorithms for smooth voice transitions.
 */
object VoiceLeading {
    /**
     * Voice leading strategy that picks the voicing whose top note is closest
     * to the top note of the previous voicing.
     */
    val topNoteDiff: VoiceLeadingFunction = { voicings, lastVoicing ->
        if (lastVoicing.isEmpty()) {
            voicings.firstOrNull() ?: emptyList()
        } else {
            // Helper to get MIDI number of the top note in a voicing
            fun topNoteMidi(voicing: List<String>): Int {
                if (voicing.isEmpty()) return 0
                return Note.get(voicing.last()).midi ?: 0
            }

            val lastTopMidi = topNoteMidi(lastVoicing)

            // Pick the voicing with the minimum absolute difference in top note MIDI
            voicings.minByOrNull { voicing ->
                abs(lastTopMidi - topNoteMidi(voicing))
            } ?: emptyList()
        }
    }
}

package io.peekandpoke.klang.tones.voicing

import io.peekandpoke.klang.tones.note.note
import kotlin.math.abs

typealias VoiceLeadingFunction = (voicings: List<List<String>>, lastVoicing: List<String>) -> List<String>

object VoiceLeading {
    val topNoteDiff: VoiceLeadingFunction = { voicings, lastVoicing ->
        if (lastVoicing.isEmpty()) {
            voicings.firstOrNull() ?: emptyList()
        } else {
            fun topNoteMidi(voicing: List<String>): Int {
                if (voicing.isEmpty()) return 0
                return note(voicing.last()).midi ?: 0
            }

            val lastTopMidi = topNoteMidi(lastVoicing)

            voicings.minByOrNull { voicing ->
                abs(lastTopMidi - topNoteMidi(voicing))
            } ?: emptyList()
        }
    }
}

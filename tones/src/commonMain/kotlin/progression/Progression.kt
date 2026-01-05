package io.peekandpoke.klang.tones.progression

import io.peekandpoke.klang.tones.chord.Chord
import io.peekandpoke.klang.tones.distance.Distance
import io.peekandpoke.klang.tones.interval.Interval
import io.peekandpoke.klang.tones.note.Note
import io.peekandpoke.klang.tones.roman.RomanNumeral

object Progression {
    /**
     * Given a tonic and a chord list expressed with roman numeral notation,
     * returns the progression expressed with leadsheet chords symbols notation.
     */
    fun fromRomanNumerals(tonic: String, chords: List<String>): List<String> {
        val t = Note.get(tonic)
        return fromRomanNumeralsInternal(t, chords)
    }

    fun fromRomanNumerals(tonic: Note, chords: List<String>): List<String> {
        return fromRomanNumeralsInternal(tonic, chords)
    }

    private fun fromRomanNumeralsInternal(tonic: Note, chords: List<String>): List<String> {
        if (tonic.empty) return chords.map { "" }
        return chords.map {
            val rn = RomanNumeral.get(it)
            if (rn.empty) ""
            else Distance.transpose(tonic, rn.interval) + rn.chordType
        }
    }

    /**
     * Given a tonic and a chord list with leadsheet symbols notation,
     * return the chord list with roman numeral notation.
     */
    fun toRomanNumerals(tonic: String, chords: List<String>): List<String> {
        val t = Note.get(tonic)
        return toRomanNumeralsInternal(t, chords)
    }

    fun toRomanNumerals(tonic: Note, chords: List<String>): List<String> {
        return toRomanNumeralsInternal(tonic, chords)
    }

    private fun toRomanNumeralsInternal(tonic: Note, chords: List<String>): List<String> {
        if (tonic.empty) return chords.map { "" }
        return chords.map { chordName ->
            val (root, chordType, _) = Chord.tokenize(chordName)
            val intervalName = Distance.distance(tonic, root)
            val roman = RomanNumeral.get(Interval.get(intervalName))
            roman.name + chordType
        }
    }
}

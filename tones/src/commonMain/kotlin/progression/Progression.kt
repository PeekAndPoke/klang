package io.peekandpoke.klang.tones.progression

import io.peekandpoke.klang.tones.chord.tokenizeChord
import io.peekandpoke.klang.tones.distance.distance
import io.peekandpoke.klang.tones.distance.transpose
import io.peekandpoke.klang.tones.interval.interval
import io.peekandpoke.klang.tones.roman.romanNumeral

/**
 * Given a tonic and a chord list expressed with roman numeral notation,
 * returns the progression expressed with leadsheet chords symbols notation.
 *
 * @param tonic The tonic note or note name.
 * @param chords A list of roman numeral chord symbols (e.g., ["I", "IIm7", "V7"]).
 * @return A list of chord symbols (e.g., ["C", "Dm7", "G7"]).
 */
fun fromRomanNumerals(tonic: Any?, chords: List<String>): List<String> {
    return chords.map {
        val rn = romanNumeral(it)
        transpose(tonic, rn.interval) + rn.chordType
    }
}

/**
 * Given a tonic and a chord list with leadsheet symbols notation,
 * return the chord list with roman numeral notation.
 *
 * @param tonic The tonic note or note name.
 * @param chords A list of chord symbols (e.g., ["CMaj7", "Dm7", "G7"]).
 * @return A list of roman numeral chord symbols (e.g., ["IMaj7", "IIm7", "V7"]).
 */
fun toRomanNumerals(tonic: Any?, chords: List<String>): List<String> {
    return chords.map { chordName ->
        val (root, chordType, _) = tokenizeChord(chordName)
        val intervalName = distance(tonic, root)
        val roman = romanNumeral(interval(intervalName))
        roman.name + chordType
    }
}

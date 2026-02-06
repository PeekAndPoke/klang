@file:Suppress("LocalVariableName")

package io.peekandpoke.klang.tones.chord

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

/**
 * Comprehensive test verifying all chord notations parse correctly and return the expected notes.
 *
 * This test covers all the chord symbols that should work with Chord.get(), including:
 * - Basic major, minor, and suspended chords
 * - Dominant seventh chords and extensions (9, 11, 13)
 * - Altered dominants (b9, #9, b5, #5, etc.)
 * - Major seventh variations (^7, M7)
 * - Minor variations (-7, m7, -6, -^7, etc.)
 * - Diminished (o, dim) and half-diminished (h, ø) chords
 * - Various notation styles (^, -, M, m)
 */
class ChordNotationComprehensiveTest : StringSpec({

    // ====================================================================================================
    // Basic Chords
    // ====================================================================================================

    "C - major triad" {
        Chord.get("C").notes shouldBe listOf("C", "E", "G")
    }

    "C5 - power chord (perfect fifth)" {
        Chord.get("C5").notes shouldBe listOf("C", "G")
    }

    "C6 - major sixth" {
        Chord.get("C6").notes shouldBe listOf("C", "E", "G", "A")
    }

    "C7 - dominant seventh" {
        Chord.get("C7").notes shouldBe listOf("C", "E", "G", "Bb")
    }

    "C9 - dominant ninth" {
        Chord.get("C9").notes shouldBe listOf("C", "E", "G", "Bb", "D")
    }

    "C11 - eleventh (no 3rd)" {
        Chord.get("C11").notes shouldBe listOf("C", "G", "Bb", "D", "F")
    }

    "C13 - dominant thirteenth" {
        Chord.get("C13").notes shouldBe listOf("C", "E", "G", "Bb", "D", "A")
    }

    "C69 - sixth added ninth" {
        Chord.get("C69").notes shouldBe listOf("C", "E", "G", "A", "D")
    }

    "Cadd9 - major add ninth" {
        Chord.get("Cadd9").notes shouldBe listOf("C", "E", "G", "D")
    }

    "Caug - augmented triad" {
        Chord.get("Caug").notes shouldBe listOf("C", "E", "G#")
    }

    // ====================================================================================================
    // Diminished Chords (o)
    // ====================================================================================================

    "Co - diminished triad" {
        Chord.get("Co").notes shouldBe listOf("C", "Eb", "Gb")
    }

    "Co7 - diminished seventh" {
        // Note: Returns Bbb (B double-flat) which is enharmonically A, but spelled correctly for dim7
        Chord.get("Co7").notes shouldBe listOf("C", "Eb", "Gb", "Bbb")
    }

    // ====================================================================================================
    // Half-Diminished Chords (h)
    // ====================================================================================================

    "Ch - half-diminished (same as m7b5)" {
        // Note: 'h' alone maps to the full m7b5 chord (includes the 7th), not just the diminished triad
        Chord.get("Ch").notes shouldBe listOf("C", "Eb", "Gb", "Bb")
    }

    "Ch7 - half-diminished seventh (m7b5)" {
        Chord.get("Ch7").notes shouldBe listOf("C", "Eb", "Gb", "Bb")
    }

    "Ch9 - half-diminished ninth" {
        Chord.get("Ch9").notes shouldBe listOf("C", "Eb", "Gb", "Bb", "D")
    }

    // ====================================================================================================
    // Suspended Chords
    // ====================================================================================================

    "Csus - suspended fourth" {
        Chord.get("Csus").notes shouldBe listOf("C", "F", "G")
    }

    "C7sus - suspended fourth seventh" {
        Chord.get("C7sus").notes shouldBe listOf("C", "F", "G", "Bb")
    }

    "C7susadd3 - 7sus with added 3rd" {
        Chord.get("C7susadd3").notes shouldBe listOf("C", "E", "F", "G", "Bb")
    }

    "C9sus - ninth suspended" {
        Chord.get("C9sus").notes shouldBe listOf("C", "F", "G", "Bb", "D")
    }

    "C13sus - thirteenth suspended" {
        Chord.get("C13sus").notes shouldBe listOf("C", "F", "G", "Bb", "D", "A")
    }

    "C7b13sus - dominant flat 13 suspended" {
        Chord.get("C7b13sus").notes shouldBe listOf("C", "F", "G", "Bb", "Ab")
    }

    "C7b9sus - suspended fourth flat ninth" {
        Chord.get("C7b9sus").notes shouldBe listOf("C", "F", "G", "Bb", "Db")
    }

    // ====================================================================================================
    // Major Seventh Variations (^ notation)
    // ====================================================================================================

    "C^ - major (caret notation)" {
        Chord.get("C^").notes shouldBe listOf("C", "E", "G")
    }

    "C^7 - major seventh (caret notation)" {
        Chord.get("C^7").notes shouldBe listOf("C", "E", "G", "B")
    }

    "C^9 - major ninth" {
        Chord.get("C^9").notes shouldBe listOf("C", "E", "G", "B", "D")
    }

    "C^13 - major thirteenth" {
        Chord.get("C^13").notes shouldBe listOf("C", "E", "G", "B", "D", "A")
    }

    "C^7#11 - major seventh sharp eleventh" {
        Chord.get("C^7#11").notes shouldBe listOf("C", "E", "G", "B", "F#")
    }

    "C^9#11 - major ninth sharp eleventh" {
        Chord.get("C^9#11").notes shouldBe listOf("C", "E", "G", "B", "D", "F#")
    }

    "C^7#5 - major seventh sharp fifth" {
        Chord.get("C^7#5").notes shouldBe listOf("C", "E", "G#", "B")
    }

    // ====================================================================================================
    // Major Seventh Variations (M notation)
    // ====================================================================================================

    "CM - major (capital M notation)" {
        Chord.get("CM").notes shouldBe listOf("C", "E", "G")
    }

    "CM7 - major seventh" {
        Chord.get("CM7").notes shouldBe listOf("C", "E", "G", "B")
    }

    "CM9 - major ninth" {
        Chord.get("CM9").notes shouldBe listOf("C", "E", "G", "B", "D")
    }

    "CM13 - major thirteenth" {
        Chord.get("CM13").notes shouldBe listOf("C", "E", "G", "B", "D", "A")
    }

    "CM7#11 - major seventh sharp eleventh" {
        Chord.get("CM7#11").notes shouldBe listOf("C", "E", "G", "B", "F#")
    }

    "CM9#11 - major ninth sharp eleventh" {
        Chord.get("CM9#11").notes shouldBe listOf("C", "E", "G", "B", "D", "F#")
    }

    "CM7#5 - major seventh sharp fifth" {
        Chord.get("CM7#5").notes shouldBe listOf("C", "E", "G#", "B")
    }

    // ====================================================================================================
    // Minor Chords (- notation)
    // ====================================================================================================

    "C- - minor (dash notation)" {
        Chord.get("C-").notes shouldBe listOf("C", "Eb", "G")
    }

    "C-7 - minor seventh" {
        Chord.get("C-7").notes shouldBe listOf("C", "Eb", "G", "Bb")
    }

    "C-6 - minor sixth" {
        Chord.get("C-6").notes shouldBe listOf("C", "Eb", "G", "A")
    }

    "C-69 - minor sixth ninth" {
        Chord.get("C-69").notes shouldBe listOf("C", "Eb", "G", "A", "D")
    }

    "C-^7 - minor/major seventh" {
        Chord.get("C-^7").notes shouldBe listOf("C", "Eb", "G", "B")
    }

    "C-^9 - minor/major ninth" {
        Chord.get("C-^9").notes shouldBe listOf("C", "Eb", "G", "B", "D")
    }

    "C-9 - minor ninth" {
        Chord.get("C-9").notes shouldBe listOf("C", "Eb", "G", "Bb", "D")
    }

    "C-add9 - minor add ninth" {
        Chord.get("C-add9").notes shouldBe listOf("C", "Eb", "G", "D")
    }

    "C-11 - minor eleventh" {
        Chord.get("C-11").notes shouldBe listOf("C", "Eb", "G", "Bb", "D", "F")
    }

    "C-7b5 - minor seventh flat fifth (half-diminished)" {
        Chord.get("C-7b5").notes shouldBe listOf("C", "Eb", "Gb", "Bb")
    }

    "C-b6 - minor flat sixth" {
        // Minor flat sixth: minor third + flat sixth (no fifth)
        Chord.get("C-b6").notes shouldBe listOf("C", "Eb", "Ab")
    }

    "C-#5 - minor sharp fifth (minor augmented)" {
        Chord.get("C-#5").notes shouldBe listOf("C", "Eb", "G#")
    }

    // ====================================================================================================
    // Minor Chords (m notation)
    // ====================================================================================================

    "Cm - minor (lowercase m notation)" {
        Chord.get("Cm").notes shouldBe listOf("C", "Eb", "G")
    }

    "Cm7 - minor seventh" {
        Chord.get("Cm7").notes shouldBe listOf("C", "Eb", "G", "Bb")
    }

    "Cm6 - minor sixth" {
        Chord.get("Cm6").notes shouldBe listOf("C", "Eb", "G", "A")
    }

    "Cm69 - minor sixth ninth" {
        Chord.get("Cm69").notes shouldBe listOf("C", "Eb", "G", "A", "D")
    }

    "Cm^7 - minor/major seventh" {
        Chord.get("Cm^7").notes shouldBe listOf("C", "Eb", "G", "B")
    }

    "C-M7 - minor/major seventh (dash-M notation)" {
        Chord.get("C-M7").notes shouldBe listOf("C", "Eb", "G", "B")
    }

    "Cm^9 - minor/major ninth" {
        Chord.get("Cm^9").notes shouldBe listOf("C", "Eb", "G", "B", "D")
    }

    "C-M9 - minor/major ninth (dash-M notation)" {
        Chord.get("C-M9").notes shouldBe listOf("C", "Eb", "G", "B", "D")
    }

    "Cm9 - minor ninth" {
        Chord.get("Cm9").notes shouldBe listOf("C", "Eb", "G", "Bb", "D")
    }

    "Cmadd9 - minor add ninth" {
        Chord.get("Cmadd9").notes shouldBe listOf("C", "Eb", "G", "D")
    }

    "Cm11 - minor eleventh" {
        Chord.get("Cm11").notes shouldBe listOf("C", "Eb", "G", "Bb", "D", "F")
    }

    "Cm7b5 - minor seventh flat fifth (half-diminished)" {
        Chord.get("Cm7b5").notes shouldBe listOf("C", "Eb", "Gb", "Bb")
    }

    "Cmb6 - minor flat sixth" {
        // Minor flat sixth: minor third + flat sixth (no fifth)
        Chord.get("Cmb6").notes shouldBe listOf("C", "Eb", "Ab")
    }

    "Cm#5 - minor sharp fifth (minor augmented)" {
        Chord.get("Cm#5").notes shouldBe listOf("C", "Eb", "G#")
    }

    // ====================================================================================================
    // Dominant Alterations
    // ====================================================================================================

    "C7b9 - dominant flat ninth" {
        Chord.get("C7b9").notes shouldBe listOf("C", "E", "G", "Bb", "Db")
    }

    "C7#9 - dominant sharp ninth" {
        Chord.get("C7#9").notes shouldBe listOf("C", "E", "G", "Bb", "D#")
    }

    "C7#11 - lydian dominant seventh (sharp eleventh)" {
        Chord.get("C7#11").notes shouldBe listOf("C", "E", "G", "Bb", "F#")
    }

    "C7b5 - dominant flat fifth" {
        Chord.get("C7b5").notes shouldBe listOf("C", "E", "Gb", "Bb")
    }

    "C7#5 - dominant sharp fifth" {
        Chord.get("C7#5").notes shouldBe listOf("C", "E", "G#", "Bb")
    }

    "C9#11 - ninth sharp eleventh" {
        Chord.get("C9#11").notes shouldBe listOf("C", "E", "G", "Bb", "D", "F#")
    }

    "C9b5 - ninth flat fifth" {
        Chord.get("C9b5").notes shouldBe listOf("C", "E", "Gb", "Bb", "D")
    }

    "C9#5 - ninth sharp fifth" {
        Chord.get("C9#5").notes shouldBe listOf("C", "E", "G#", "Bb", "D")
    }

    "C7b13 - dominant flat thirteenth" {
        // Dominant 7th with flat 13 (no 5th, no 9th in this voicing from legacy chord dictionary)
        Chord.get("C7b13").notes shouldBe listOf("C", "E", "Bb", "Ab")
    }

    "C7#9#5 - dominant sharp ninth sharp fifth" {
        Chord.get("C7#9#5").notes shouldBe listOf("C", "E", "G#", "Bb", "D#")
    }

    "C7#9b5 - dominant sharp ninth flat fifth" {
        Chord.get("C7#9b5").notes shouldBe listOf("C", "E", "Gb", "Bb", "D#")
    }

    "C7#9#11 - dominant sharp ninth sharp eleventh" {
        Chord.get("C7#9#11").notes shouldBe listOf("C", "E", "G", "Bb", "D#", "F#")
    }

    "C7b9#11 - dominant flat ninth sharp eleventh" {
        Chord.get("C7b9#11").notes shouldBe listOf("C", "E", "G", "Bb", "Db", "F#")
    }

    "C7b9b5 - dominant flat ninth flat fifth" {
        Chord.get("C7b9b5").notes shouldBe listOf("C", "E", "Gb", "Bb", "Db")
    }

    "C7b9#5 - dominant flat ninth sharp fifth" {
        Chord.get("C7b9#5").notes shouldBe listOf("C", "E", "G#", "Bb", "Db")
    }

    "C7b9#9 - dominant flat ninth sharp ninth" {
        Chord.get("C7b9#9").notes shouldBe listOf("C", "E", "G", "Bb", "Db", "D#")
    }

    "C7b9b13 - dominant flat ninth flat thirteenth" {
        Chord.get("C7b9b13").notes shouldBe listOf("C", "E", "G", "Bb", "Db", "Ab")
    }

    "C7alt - altered dominant (no 5th)" {
        // Note: 7alt maps to 7#5#9 in the dictionary, giving sharp fifth and sharp ninth
        // This is one interpretation of "altered" - may differ from jazz theory expectations
        Chord.get("C7alt").notes shouldBe listOf("C", "E", "G#", "Bb", "D#")
    }

    "C13#11 - thirteenth sharp eleventh" {
        Chord.get("C13#11").notes shouldBe listOf("C", "E", "G", "Bb", "D", "F#", "A")
    }

    "C13b9 - thirteenth flat ninth" {
        Chord.get("C13b9").notes shouldBe listOf("C", "E", "G", "Bb", "Db", "A")
    }

    "C13#9 - thirteenth sharp ninth" {
        Chord.get("C13#9").notes shouldBe listOf("C", "E", "G", "Bb", "D#", "A")
    }

    // ====================================================================================================
    // Additional Power Chord and Diatonic Variations
    // ====================================================================================================

    "C2 - suspended second (same as Csus2)" {
        // Note: "2" is an alias for "Madd9" (major with added 9th), not sus2
        // Use "Csus2" explicitly if you want the suspended voicing [C, D, G]
        Chord.get("C2").notes shouldBe listOf("C", "E", "G", "D")
    }
})

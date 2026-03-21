package io.peekandpoke.klang.strudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldMatch
import io.peekandpoke.klang.tones.note.Note

class LangChordParsingSpec : StringSpec({

    val range = listOf("C3", "C5")
    val lastVoicing = emptyList<String>()

    val chordsToCheck = """
        C2 C5 C6 C7 C9 C11 C13 C69
        Cadd9 Co Ch Csus C^ C- C^7 
        C-7 C7sus Ch7 Co7 C^9 C^13 
        C^7#11 C^9#11 C^7#5 C-6 C-69 
        C-^7 C-^9 C-9 C-add9 C-11 
        C-7b5 Ch9 C-b6 C-#5 C7b9 
        C7#9 C7#11 C7b5 C7#5 C9#11 
        C9b5 C9#5 C7b13 C7#9#5 C7#9b5 
        C7#9#11 C7b9#11 C7b9b5 C7b9#5 
        C7b9#9 C7b9b13 C7alt C13#11 
        C13b9 C13#9 C7b9sus C7susadd3 
        C9sus C13sus C7b13sus C Caug 
        CM Cm CM7 Cm7 CM9 CM13 CM7#11 
        CM9#11 CM7#5 Cm6 Cm69 Cm^7 
        C-M7 Cm^9 C-M9 Cm9 Cmadd9 
        Cm11 Cm7b5 Cmb6 Cm#5
    """.trimIndent().split(Regex("\\s+")).filter { it.isNotBlank() }

    chordsToCheck.forEach { chordName ->
        "Chord '$chordName' should produce valid notes in reasonable octave" {
            val notes = getVoicedNotes(chordName, range, lastVoicing)

            // Should not be empty
            notes.shouldNotBeEmpty()

            notes.forEach { note ->
                // Check if note matches typical note pattern with octave (e.g. C4, F#3)
                // Regex: [A-G] + optional accidental [#b]* + Octave [0-9]
                note shouldMatch Regex("^[A-G][#b]*\\d+$")

                // Check octave sanity (should be >= 3 given our fallback is 4 and range is C3-C5)
                // Note.tokenize returns [letter, acc, oct, rest]
                val tokens = Note.tokenize(note)
                val oct = tokens.getOrNull(2)?.toIntOrNull()

                // We expect octave to be present and reasonable
                // "horrible" low chords were in octave 0, 1, 2.
                // Let's assert octave >= 3.
                if (oct != null) {
                    (oct >= 3) shouldBe true
                }
            }
        }
    }
})

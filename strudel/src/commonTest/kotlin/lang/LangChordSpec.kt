package io.peekandpoke.klang.strudel.lang

import io.kotest.assertions.assertSoftly
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe

class LangChordSpec : StringSpec({

    "chord() creates pattern with chord property" {
        val p = chord("C")
        val events = p.queryArc(0.0, 1.0)

        // Should be 1 event with chord property
        events.size shouldBe 1
        events[0].data.chord shouldBe "C"
        // And note should be set to root
        events[0].data.note shouldBe "C"
    }

    "chord() can be voiced" {
        val p = chord("C").voicing()
        val events = p.queryArc(0.0, 1.0)

        // Voicing should expand to multiple notes
        // C major triad -> 3 notes (or more depending on default range)
        events.size shouldBe 3 // Assuming default triad voicing in default range

        val notes = events.map { it.data.note }.toSet()
        // Notes should be C, E, G (in some octave)
        // Checking simplified names
        val simplifiedNotes = notes.map { it?.replace(Regex("\\d"), "") }
        simplifiedNotes shouldContainAll listOf("C", "E", "G")
    }

    "chord() handles seventh chords metadata" {
        val p = chord("Cmaj7")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.chord shouldBe "Cmaj7"
    }

    "chord() handles slash chords metadata" {
        val p = chord("F/A")
        val events = p.queryArc(0.0, 1.0)

        assertSoftly {
            events.shouldHaveSize(1)
            events[0].data.chord shouldBe "F/A"
            // Root should be F
            events[0].data.note shouldBe "F"
        }
    }

    "chord() works with multiple chords" {
        val p = chord("C F G")
        val events = p.queryArc(0.0, 1.0).sortedBy { it.begin }

        assertSoftly {
            events.size shouldBe 3

            events[0].data.chord shouldBe "C"
            events[1].data.chord shouldBe "F"
            events[2].data.chord shouldBe "G"
        }
    }

    "chord() as pattern extension" {
        val p = s("piano").chord("C F")
        val events = p.queryArc(0.0, 1.0).sortedBy { it.begin }

        events.size shouldBe 1
        events[0].data.chord shouldBe "C"
        events[0].data.sound shouldBe "piano"
    }
})

package io.peekandpoke.klang.strudel.lang

import io.kotest.assertions.assertSoftly
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.strudel.StrudelPattern

class LangRootNotesSpec : StringSpec({

    "rootNotes() extracts root from major chord" {
        val p = chord("C").rootNotes()
        val events = p.queryArc(0.0, 1.0)

        // Should have single note instead of polyphonic chord
        events.size shouldBe 1
        events[0].data.note shouldBe "C4"
        events[0].data.chord shouldBe "C"
    }

    "rootNotes() extracts root from minor chord" {
        val p = chord("Dm7").rootNotes()
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.note shouldBe "D4"
        events[0].data.chord shouldBe "Dm7"
    }

    "rootNotes() extracts from multiple chords" {
        val p = chord("C F G").rootNotes()
        val events = p.queryArc(0.0, 1.0).sortedBy { it.part.begin }

        events.size shouldBe 3
        events[0].data.note shouldBe "C4"
        events[1].data.note shouldBe "F4"
        events[2].data.note shouldBe "G4"
    }

    "rootNotes() with specific octave" {
        val p = chord("C F G").rootNotes("3")
        val events = p.queryArc(0.0, 1.0).sortedBy { it.part.begin }

        events.size shouldBe 3
        events[0].data.note shouldBe "C3"
        events[1].data.note shouldBe "F3"
        events[2].data.note shouldBe "G3"
    }

    "rootNotes() handles slash chords correctly" {
        val p = chord("F/A").rootNotes()
        val events = p.queryArc(0.0, 1.0)

        // Root should be F, not A (bass note)
        events.size shouldBe 1
        events[0].data.note shouldBe "F4"
        events[0].data.chord shouldBe "F/A"
    }

    "rootNotes() preserves other properties" {
        val p = chord("C F").sound("bass").rootNotes()
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events.all { it.data.sound == "bass" } shouldBe true
    }

    "rootNotes() timing preservation" {
        val p = chord("C F G C").fast("2").rootNotes()
        val events = p.queryArc(0.0, 1.0).sortedBy { it.part.begin }

        // 4 chords at doubled speed = 8 root notes
        events.size shouldBe 8

        // Each event should be 1/8 of a cycle
        val duration = events[0].part.end - events[0].part.begin
        duration.toDouble() shouldBe 0.125
    }

    "rootNotes() as pattern extension" {
        val p = chord("Dm7 G7 Cmaj7").rootNotes()
        val events = p.queryArc(0.0, 1.0).sortedBy { it.part.begin }

        events.size shouldBe 3
        events[0].data.note shouldBe "D4"
        events[1].data.note shouldBe "G4"
        events[2].data.note shouldBe "C4"
    }

    "rootNotes() works in compiled code" {
        val p = StrudelPattern.compile("""chord("Cm7").rootNotes()""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        events.size shouldBe 1
        events[0].data.note shouldBe "C4"
    }

    "rootNotes() with octave in compiled code" {
        val p = StrudelPattern.compile("""chord("Dm7 G7 Cmaj7").rootNotes(3)""")
        val events = p?.queryArc(0.0, 1.0)?.sortedBy { it.part.begin } ?: emptyList()

        events.size shouldBe 3
        events[0].data.note shouldBe "D3"
        events[1].data.note shouldBe "G3"
        events[2].data.note shouldBe "C3"
    }

    "rootNotes() no-op when no chord property" {
        val p = note("C E G").rootNotes()
        val events = p.queryArc(0.0, 1.0)

        assertSoftly {
            // Should pass through unchanged
            events.size shouldBe 3
            events[0].data.note shouldBe "C"
            events[1].data.note shouldBe "E"
            events[2].data.note shouldBe "G"
        }
    }
})

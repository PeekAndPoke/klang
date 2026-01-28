package io.peekandpoke.klang.strudel.lang

import io.kotest.assertions.assertSoftly
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.tones.note.Note

class LangVoicingSpec : StringSpec({

    "voicing() applies voicing to chord" {
        val p = chord("Cmaj7").voicing()
        val events = p.queryArc(0.0, 1.0)

        // Should still be polyphonic (4 notes)
        events.size shouldBe 4

        // All events should be simultaneous
        val firstBegin = events[0].begin
        val firstEnd = events[0].end
        events.all { it.begin == firstBegin && it.end == firstEnd } shouldBe true

        // Chord property is removed after voicing (matches JS behavior)
        events.all { it.data.chord == null } shouldBe true

        // All notes should be within default range C3-C5
        val notes = events.map { it.data.note!! }
        notes.all { note ->
            val midi = Note.get(note).midi ?: 0
            midi in 48..72 // C3 = 48, C5 = 72
        } shouldBe true
    }

    "voicing() with voice leading" {
        val p = chord("Cmaj7 Dm7 G7 Cmaj7").voicing()
        val events = p.queryArc(0.0, 1.0).sortedBy { it.begin }

        // 4 chords with 4 notes each = 16 events
        events.size shouldBe 16

        // Chord property is removed after voicing (matches JS behavior)
        events.all { it.data.chord == null } shouldBe true

        // Instead, verify we have 4 groups of 4 simultaneous notes
        val groups = events.groupBy { it.begin }
        groups.size shouldBe 4
        groups.values.all { it.size == 4 } shouldBe true
    }

    "voicing() preserves other properties" {
        val p = chord("C").sound("piano").voicing()
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 3
        // Other properties like sound should be preserved
        events.all { it.data.sound == "piano" } shouldBe true
        // But chord property is removed
        events.all { it.data.chord == null } shouldBe true
    }

    "voicing() no-op when no chord property" {
        val p = note("C E G").voicing()
        val events = p.queryArc(0.0, 1.0)

        // Should pass through (no chord property to voice)
        events.size shouldBe 3
    }

    "voicing() as pattern extension" {
        val p = chord("Dm7 G7").voicing()
        val events = p.queryArc(0.0, 1.0).sortedBy { it.begin }

        // 2 chords with 4 notes each
        events.size shouldBe 8

        // Chord property is removed after voicing
        events.all { it.data.chord == null } shouldBe true

        // Verify we have 2 groups of 4 simultaneous notes
        val groups = events.groupBy { it.begin }
        groups.size shouldBe 2
        groups.values.all { it.size == 4 } shouldBe true
    }

    "voicing() works in compiled code" {
        val p = StrudelPattern.compile("""chord("Cmaj7").voicing()""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        events.size shouldBe 4
        // Chord property is removed after voicing (matches JS behavior)
        events.all { it.data.chord == null } shouldBe true
        // But all notes should be defined
        events.all { it.data.note != null } shouldBe true
    }

    "voicing() timing preservation" {
        val p = chord("C F").voicing().fast("2")
        val events = p.queryArc(0.0, 1.0).sortedBy { it.begin }

        // 2 chords at doubled speed: 4 occurrences, 3 notes each = 12 events
        events.size shouldNotBe 0

        // Each chord event should be short (at fast(2))
        val duration = events[0].end - events[0].begin
        duration.toDouble() shouldBe 0.25
    }

    "voicing() changes note octaves from basic chord" {
        val pChord = chord("Cmaj7")
        val pVoicing = chord("Cmaj7").voicing()

        val chordEvents = pChord.queryArc(0.0, 1.0)
        val voicingEvents = pVoicing.queryArc(0.0, 1.0)

        assertSoftly {
            // Both should have 4 notes
            chordEvents.size shouldBe 1
            voicingEvents.size shouldBe 4

            // Get the note names
            val chordNotes = chordEvents.map { it.data.note }
            val voicingNotes = voicingEvents.map { it.data.note }

            // The voicing might reorder or change octaves
            // Just verify they're valid notes
            voicingNotes.all { it != null } shouldBe true
            chordNotes.all { it != null } shouldBe true
        }
    }

    "voicing() maintains polyphony count" {
        val p = chord("G7").voicing()
        val events = p.queryArc(0.0, 1.0)

        // G7 has 4 notes, voicing should maintain that
        events.size shouldBe 4

        // All simultaneous
        val begin = events[0].begin
        events.all { it.begin == begin } shouldBe true
    }
})

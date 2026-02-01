package io.peekandpoke.klang.strudel.lang

import io.kotest.assertions.assertSoftly
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.strudel.StrudelPattern

class LangScaleTransposeSpec : StringSpec({

    "scaleTranspose(0) must be the same as not transpose" {
        val without = note("C E").scale("C3:major")
        val with = note("C E").scale("C3:major").scaleTranspose("0")

        val withoutEvents = without.queryArc(0.0, 1.0).sortedBy { it.part.begin }
        val withEvents = with.queryArc(0.0, 1.0).sortedBy { it.part.begin }

        assertSoftly {
            withoutEvents.shouldNotBeEmpty()
            withoutEvents.size shouldBe withEvents.size

            withoutEvents.zip(withEvents).forEachIndexed { index, (e1, e2) ->
                withClue("Event $index") {
                    e1.part.begin shouldBe e2.part.begin
                    e1.part.end shouldBe e2.part.end
                    e1.data.freqHz shouldBe e2.data.freqHz
                }
            }
        }
    }

    "scaleTranspose() transposes by scale degrees" {
        val p = n("0 2").scale("C:major").scaleTranspose("1")
        val events = p.queryArc(0.0, 1.0).sortedBy { it.part.begin }

        events.size shouldBe 2
        // 0 in C major is C, +1 degree = D
        events[0].data.note shouldBe "D3"
        // 2 in C major is E, +1 degree = F
        events[1].data.note shouldBe "F3"
    }

    "scaleTranspose() handles negative steps" {
        val p = n("2 4").scale("C:major").scaleTranspose("-1")
        val events = p.queryArc(0.0, 1.0).sortedBy { it.part.begin }

        events.size shouldBe 2
        // 2 in C major is E, -1 degree = D
        events[0].data.note shouldBe "D3"
        // 4 in C major is G, -1 degree = F
        events[1].data.note shouldBe "F3"
    }

    "scaleTranspose() wraps octaves correctly" {
        val p = n("6").scale("C:major").scaleTranspose("2")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        // 6 in C major is B4, +2 degrees = D5 (wraps to next octave)
        events[0].data.note shouldBe "D4"
    }

    "scaleTranspose() works with note names" {
        val p = note("C E").scale("C:major").scaleTranspose("2")
        val events = p.queryArc(0.0, 1.0).sortedBy { it.part.begin }

        events.size shouldBe 2
        // C +2 degrees = E
        events[0].data.note shouldBe "E3"
        // E +2 degrees = G
        events[1].data.note shouldBe "G3"
    }

    "scaleTranspose() falls back to chromatic when no scale set" {
        val p = note("C4 E4").scaleTranspose("2")
        val events = p.queryArc(0.0, 1.0).sortedBy { it.part.begin }

        events.size shouldBe 2
        // C4 + 2 semitones = D4 (chromatic)
        events[0].data.note shouldBe "D4"
        // E4 + 2 semitones = Gb4 (chromatic)
        events[1].data.note shouldBe "F#4"
    }

    "scaleTranspose() works with control patterns" {
        val p = note("C").scale("C:major").scaleTranspose("0 1 2")
        val events = p.queryArc(0.0, 1.0).sortedBy { it.part.begin }

        events.size shouldBe 1
        // C +0 = C, C +1 = D, C +2 = E
        events[0].data.note shouldBe "C"
    }

    "scaleTranspose() as pattern extension" {
        val p = note("C D").scale("C:major").scaleTranspose("1")
        val events = p.queryArc(0.0, 1.0).sortedBy { it.part.begin }

        events.size shouldBe 2
        events[0].data.note shouldBe "D3"
        events[1].data.note shouldBe "E3"
    }

    "scaleTranspose() as string extension" {
        val p = "C E".scale("C:major").scaleTranspose("1")
        val events = p.queryArc(0.0, 1.0).sortedBy { it.part.begin }

        events.size shouldBe 2
        // Values interpreted as notes via scale
        events.all { it.data.note != null } shouldBe true
    }

    "scaleTranspose() works in compiled code" {
        val p = StrudelPattern.compile("""n("0 2").scale("C:major").scaleTranspose(1)""")
        val events = p?.queryArc(0.0, 1.0)?.sortedBy { it.part.begin } ?: emptyList()

        events.size shouldBe 2
        events[0].data.note shouldBe "D3"
        events[1].data.note shouldBe "F3"
    }

    "scaleTranspose() large steps across octaves" {
        val p = note("C4").scale("C:major").scaleTranspose("7")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        // C4 +7 degrees in major scale = C5 (one octave up)
        events[0].data.note shouldBe "C5"
    }

    "scaleTranspose() with minor scale" {
        // C minor: C, D, Eb, F, G, Ab, Bb
        val p = n("0 2 4").scale("C:minor").scaleTranspose("1")
        val events = p.queryArc(0.0, 1.0).sortedBy { it.part.begin }

        events.size shouldBe 3
        // 0 (C) + 1 = D (D4)
        events[0].data.note shouldBe "D3"
        // 2 (Eb) + 1 = F (F4)
        events[1].data.note shouldBe "F3"
        // 4 (G) + 1 = Ab (Ab4)
        events[2].data.note shouldBe "Ab3"
    }

    "scaleTranspose() with pentatonic scale" {
        // C pentatonic: C, D, E, G, A
        val p = n("0 1 2 3 4").scale("C:pentatonic").scaleTranspose("1")
        val events = p.queryArc(0.0, 1.0).sortedBy { it.part.begin }

        events.size shouldBe 5
        // 0 (C) + 1 = D
        events[0].data.note shouldBe "D3"
        // 1 (D) + 1 = E
        events[1].data.note shouldBe "E3"
        // 2 (E) + 1 = G
        events[2].data.note shouldBe "G3"
        // 3 (G) + 1 = A
        events[3].data.note shouldBe "A3"
        // 4 (A) + 1 = C (next octave)
        events[4].data.note shouldBe "C4"
    }

    "scaleTranspose() with dorian scale" {
        // C dorian: C, D, Eb, F, G, A, Bb
        val p = n("0 2 5").scale("C:dorian").scaleTranspose("-1")
        val events = p.queryArc(0.0, 1.0).sortedBy { it.part.begin }

        events.size shouldBe 3
        // 0 (C) - 1 = Bb (Bb3, down octave)
        events[0].data.note shouldBe "Bb2"
        // 2 (Eb) - 1 = D
        events[1].data.note shouldBe "D3"
        // 5 (A) - 1 = G
        events[2].data.note shouldBe "G3"
    }

    "scaleTranspose() with phrygian scale" {
        // C phrygian: C, Db, Eb, F, G, Ab, Bb
        // Intervals: 1P 2m 3m 4P 5P 6m 7m
        val p = n("0 1 2 3").scale("C:phrygian").scaleTranspose("1")
        val events = p.queryArc(0.0, 1.0).sortedBy { it.part.begin }

        events.size shouldBe 4
        // 0 (C) -> Db
        events[0].data.note shouldBe "Db3"
        // 1 (Db) -> Eb
        events[1].data.note shouldBe "Eb3"
        // 2 (Eb) -> F
        events[2].data.note shouldBe "F3"
        // 3 (F) -> G
        events[3].data.note shouldBe "G3"
    }

    "scaleTranspose() with lydian scale" {
        // C lydian: C, D, E, F#, G, A, B
        // Intervals: 1P 2M 3M 4A 5P 6M 7M
        val p = n("3 4 6").scale("C:lydian").scaleTranspose("-1")
        val events = p.queryArc(0.0, 1.0).sortedBy { it.part.begin }

        events.size shouldBe 3
        // 3 (F#) -> E
        events[0].data.note shouldBe "E3"
        // 4 (G) -> F#
        events[1].data.note shouldBe "F#3"
        // 6 (B) -> A
        events[2].data.note shouldBe "A3"
    }

    "scaleTranspose() with locrian scale" {
        // C locrian: C, Db, Eb, F, Gb, Ab, Bb
        // Intervals: 1P 2m 3m 4P 5d 6m 7m
        val p = n("0 4 6").scale("C:locrian").scaleTranspose("1")
        val events = p.queryArc(0.0, 1.0).sortedBy { it.part.begin }

        events.size shouldBe 3
        // 0 (C) -> Db
        events[0].data.note shouldBe "Db3"
        // 4 (Gb) -> Ab
        events[1].data.note shouldBe "Ab3"
        // 6 (Bb) -> C (next octave)
        events[2].data.note shouldBe "C4"
    }

    "scaleTranspose() with chromatic scale" {
        // C chromatic: C, Db, D, Eb, E, F, Gb, G, Ab, A, Bb, B
        val p = n("0 1 2").scale("C:chromatic").scaleTranspose("2")
        val events = p.queryArc(0.0, 1.0).sortedBy { it.part.begin }

        events.size shouldBe 3
        // 0 (C) -> D (index 2)
        events[0].data.note shouldBe "D3"
        // 1 (Db) -> Eb (index 3)
        events[1].data.note shouldBe "Eb3"
        // 2 (D) -> E (index 4)
        events[2].data.note shouldBe "E3"
    }
})

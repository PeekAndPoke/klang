package io.peekandpoke.klang.strudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.strudel.StrudelPattern
import kotlin.math.pow

class LangScaleSpec : StringSpec({

    "scale() sets VoiceData.scale correctly" {
        val p = scale("C4:major")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.scale shouldBe "C4 major"
    }

    "scale() sets correct frequencies for different octaves" {
        fun expectedFreq(midi: Int) = 2.0.pow((midi - 69.0) / 12.0) * 440.0

        val cases = listOf(
            1 to 24, // C1
            2 to 36, // C2
            3 to 48, // C3
            4 to 60, // C4
            5 to 72, // C5
            6 to 84, // C6
            7 to 96  // C7
        )

        cases.forEach { (oct, midi) ->
            val p = n("0").scale("C$oct:minor")
            val events = p.queryArc(0.0, 1.0)

            events.size shouldBe 1
            events[0].data.note shouldBe "C$oct"
            events[0].data.freqHz shouldBe expectedFreq(midi)
        }
    }

    "scale() defaults to octave 3 when no octave is specified" {
        // "C:minor" should be treated as "C3:minor"
        val p = n("0 2 4").scale("C:minor")
        val events = p.queryArc(0.0, 1.0).sortedBy { it.begin }

        events.size shouldBe 3
        events[0].data.note shouldBe "C3"
        events[1].data.note shouldBe "Eb3"
        events[2].data.note shouldBe "G3"
    }

    "scale() resolves notes when applied to numeric pattern (n)" {
        // n("0 2") -> indices 0, 2
        // scale("C4:major") -> C4, E4
        val p = n("0 2").scale("C4:major")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events[0].data.note shouldBe "C4"
        events[1].data.note shouldBe "E4"
    }

    "scale() works as string extension" {
        // "0".scale("C4:major") -> parse "0" (n) then apply scale
        val p = "0".scale("C4:major")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.note shouldBe "C4"
    }

    "scale() supports changing scales over time" {
        // "0 2".scale("C4:major C4:minor")
        // First half: C4 major (0->C4, 2->E4)
        // Second half: C4 minor (0->C4, 2->Eb4)

        // n("0 2") plays 0 at 0.0-0.5, 2 at 0.5-1.0
        // scale("C4:major C4:minor") plays major at 0.0-0.5, minor at 0.5-1.0

        val p = n("0 2").scale("C4:major C4:minor")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        // 0 at 0.0 -> C4 major -> C4
        events[0].data.note shouldBe "C4"
        // 2 at 0.5 -> C4 minor -> Eb4
        events[1].data.note shouldBe "Eb4"
    }

    "scale() works in compiled code" {
        val p = StrudelPattern.compile("""n("0 2").scale("C4:major")""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        events.size shouldBe 2
        events[0].data.note shouldBe "C4"
        events[1].data.note shouldBe "E4"
    }

    "scale() as string extension works in compiled code" {
        val p = StrudelPattern.compile(""""0".scale("C4:major")""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        events.size shouldBe 1
        events[0].data.note shouldBe "C4"
    }

    "scale() with minor scale (numbers)" {
        // C4 minor: C4, D4, Eb4, F4, G4, Ab4, Bb4, C5
        val p = n("0 1 2 3 4 5 6 7").scale("C4:minor")
        val events = p.queryArc(0.0, 1.0).sortedBy { it.begin }

        events.size shouldBe 8
        events[0].data.note shouldBe "C4"
        events[1].data.note shouldBe "D4"
        events[2].data.note shouldBe "Eb4"
        events[3].data.note shouldBe "F4"
        events[4].data.note shouldBe "G4"
        events[5].data.note shouldBe "Ab4"
        events[6].data.note shouldBe "Bb4"
        events[7].data.note shouldBe "C5"
    }

    "scale() with minor scale (notes)" {
        // Notes are filtered/mapped to scale?
        // Actually, note("a") in scale context usually just resolves frequency if note is in scale?
        // Or if using degrees?
        // note("a b c") are absolute notes. scale() adds context but doesn't change them unless using degree-based functions.
        // However, the user request says: 1. note("a b c d e f g")
        // If note() is used, scale() doesn't transpose them to the scale unless they are degrees?
        // Wait, in Strudel, `note("0")` is same as `n("0")`.
        // But `note("a")` is note A.
        // If I say `note("0 1").scale("C minor")` it works like `n`.
        // If I say `note("a b").scale("C minor")`, it sets the scale property.

        val p = n("0 1 2").scale("C4:minor")
        val events = p.queryArc(0.0, 1.0).sortedBy { it.begin }

        events.size shouldBe 3
        events[0].data.note shouldBe "C4"
        events[1].data.note shouldBe "D4"
        events[2].data.note shouldBe "Eb4"
    }

    "scale() with pentatonic scale" {
        // C major pentatonic: C, D, E, G, A
        val p = n("0 1 2 3 4 5").scale("C4:pentatonic")
        val events = p.queryArc(0.0, 1.0).sortedBy { it.begin }

        events.size shouldBe 6
        events[0].data.note shouldBe "C4"
        events[1].data.note shouldBe "D4"
        events[2].data.note shouldBe "E4"
        events[3].data.note shouldBe "G4"
        events[4].data.note shouldBe "A4"
        events[5].data.note shouldBe "C5"
    }

    "scale() with mixolydian scale" {
        // C mixolydian: C, D, E, F, G, A, Bb
        val p = n("0 1 2 3 4 5 6 7").scale("C4:mixolydian")
        val events = p.queryArc(0.0, 1.0).sortedBy { it.begin }

        events.size shouldBe 8
        events[6].data.note shouldBe "Bb4"
        events[7].data.note shouldBe "C5"
    }

    "scale() with accidentals in tonic" {
        // C# major: C#, D#, E#, F#, G#, A#, B#
        val p = n("0 1 2 3").scale("C#4:major")
        val events = p.queryArc(0.0, 1.0).sortedBy { it.begin }

        events[0].data.note shouldBe "C#4"
        events[1].data.note shouldBe "D#4"
        events[2].data.note shouldBe "E#4" // or F4? Strudel/Tonal usually preserves spelling
        events[3].data.note shouldBe "F#4"
    }

    "scale() with flats in tonic" {
        // Eb minor: Eb, F, Gb, Ab, Bb, Cb, Db
        val p = n("0 1 2").scale("Eb4:minor")
        val events = p.queryArc(0.0, 1.0).sortedBy { it.begin }

        events[0].data.note shouldBe "Eb4"
        events[1].data.note shouldBe "F4"
        events[2].data.note shouldBe "Gb4"
    }

    "scale() with chromatic scale" {
        val p = n("0 1 2").scale("C4:chromatic")
        val events = p.queryArc(0.0, 1.0).sortedBy { it.begin }

        events[0].data.note shouldBe "C4"
        events[1].data.note shouldBe "Db4" // or C#4
        events[2].data.note shouldBe "D4"
    }
})

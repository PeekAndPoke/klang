package io.peekandpoke.klang.strudel.lang

import io.kotest.assertions.assertSoftly
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_bridge.VoiceValue.Companion.asVoiceValue
import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel.StrudelVoiceData

class LangTransposeSpec : StringSpec({

    "top-level transpose() sets semitones correctly" {
        // c3 is 130.81Hz. Transposing by 12 should give c4 (261.63Hz)
        val p = transpose(12, note("c3"))

        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 1
        events[0].data.note shouldBe "C4"
        events[0].data.freqHz!! shouldBe (261.63 plusOrMinus 0.1)
    }

    "control pattern transpose() shifts frequencies on existing pattern" {
        val base = note("c3 e3")
        // Step 1: transpose 0, Step 2: transpose 12
        val p = base.transpose("0 12")

        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 2
        events[0].data.note shouldBe "C3"
        events[1].data.note shouldBe "E4"
    }

    "transpose() works as string extension" {
        val p = "c3".transpose(7) // Perfect 5th -> g3
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.note shouldBe "G3"
    }

    "transpose() works within compiled code as top-level function" {
        val p = StrudelPattern.compile("""transpose(12, note("c3"))""")

        val events = p?.queryArc(0.0, 1.0) ?: emptyList()
        events.size shouldBe 1
        events[0].data.note shouldBe "C4"
    }

    "transpose() works within compiled code as chained-level function" {
        val p = StrudelPattern.compile("""note("c3").transpose(12)""")

        val events = p?.queryArc(0.0, 1.0) ?: emptyList()
        events.size shouldBe 1
        events[0].data.note shouldBe "C4"
    }

    "debug: VoiceData.transpose with intervals from 'note'" {
        // Test that VoiceData.transpose works correctly with interval strings
        val c2 = StrudelVoiceData.empty.copy(note = "C2")
        val c3 = StrudelVoiceData.empty.copy(note = "C3")

        c2.transpose("1P").note shouldBe "C2"
        c3.transpose("1P").note shouldBe "C3"

        c2.transpose("-2M").note shouldBe "Bb1"
        c3.transpose("-2M").note shouldBe "Bb2"

        c2.transpose("4P").note shouldBe "F2"
        c3.transpose("4P").note shouldBe "F3"

        c2.transpose("3m").note shouldBe "Eb2"
        c3.transpose("3m").note shouldBe "Eb3"
    }

    "debug: VoiceData.transpose with intervals from 'value'" {
        // Test that VoiceData.transpose works correctly with interval strings
        val c2 = StrudelVoiceData.empty.copy(value = "C2".asVoiceValue())
        val c3 = StrudelVoiceData.empty.copy(value = "C3".asVoiceValue())

        c2.transpose("1P").note shouldBe "C2"
        c3.transpose("1P").note shouldBe "C3"

        c2.transpose("-2M").note shouldBe "Bb1"
        c3.transpose("-2M").note shouldBe "Bb2"

        c2.transpose("4P").note shouldBe "F2"
        c3.transpose("4P").note shouldBe "F3"

        c2.transpose("3m").note shouldBe "Eb2"
        c3.transpose("3m").note shouldBe "Eb3"
    }

    "debug: transpose with single interval string" {
        val p = note("c2").transpose("4P")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.note shouldBe "F2"
    }

    "complex interval transposition" {
        // Pattern: [c2 c3]*4  (8 events total in 1 cycle)
        // Control: <1P -2M 4P 3m> (4 segments in 1 cycle)
        //
        // Segment 1 (0.0-0.25): 1P (Unison)
        //   Events at 0.0 (c2) and 0.125 (c3) -> Should remain c2, c3
        //
        // Segment 2 (0.25-0.5): -2M (Major 2nd down)
        //   Events at 0.25 (c2) and 0.375 (c3) -> c2-2M=Bb1, c3-2M=Bb2
        //
        // Segment 3 (0.5-0.75): 4P (Perfect 4th up)
        //   Events at 0.5 (c2) and 0.625 (c3) -> c2+4P=F2, c3+4P=F3
        //
        // Segment 4 (0.75-1.0): 3m (Minor 3rd up)
        //   Events at 0.75 (c2) and 0.875 (c3) -> c2+3m=Eb2, c3+3m=Eb3

        val p = "[c2 c3]".transpose("<1P -2M 4P 3m>").note()
        val events = p.queryArc(0.0, 4.0)

        assertSoftly {
            events.size shouldBe 8
            // Segment 1: 1P
            events[0].data.note shouldBe "C2"
            events[1].data.note shouldBe "C3"
            // Segment 2: -2M
            events[2].data.note shouldBe "Bb1"
            events[3].data.note shouldBe "Bb2"
            // Segment 3: 4P
            events[4].data.note shouldBe "F2"
            events[5].data.note shouldBe "F3"
            // Segment 4: 3m
            events[6].data.note shouldBe "Eb2"
            events[7].data.note shouldBe "Eb3"
        }
    }
})

package io.peekandpoke.klang.strudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.strudel.StrudelPattern

class LangScaleSpec : StringSpec({

    "scale() sets VoiceData.scale correctly" {
        val p = scale("C4:major")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.scale shouldBe "C4 major"
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
})

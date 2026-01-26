package io.peekandpoke.klang.strudel.lang

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.strudel.EPSILON
import io.peekandpoke.klang.strudel.StrudelPattern

class LangRatioSpec : StringSpec({

    "ratio() divides numbers via colon notation" {
        withClue("ratio with colon notation in kotlin") {
            // Using colon notation: "5:4" -> 5/4 = 1.25
            val p = seq("1 5:4 3:2").ratio()
            val events = p.queryArc(0.0, 1.0)

            events.size shouldBe 3
            events[0].data.value?.asDouble shouldBe (1.0 plusOrMinus EPSILON)      // 1
            events[1].data.value?.asDouble shouldBe (1.25 plusOrMinus EPSILON)     // 5/4
            events[2].data.value?.asDouble shouldBe (1.5 plusOrMinus EPSILON)      // 3/2
        }

        withClue("ratio with musical intervals") {
            // Common musical intervals as ratios
            val p = seq("2:1 3:2 4:3 5:4").ratio().mul(110.0)
            val events = p.queryArc(0.0, 1.0)

            events.size shouldBe 4
            events[0].data.value?.asDouble shouldBe (220.0 plusOrMinus EPSILON)    // octave (2/1)
            events[1].data.value?.asDouble shouldBe (165.0 plusOrMinus EPSILON)    // fifth (3/2)
            events[2].data.value?.asDouble shouldBe (146.67 plusOrMinus 0.01)      // fourth (4/3)
            events[3].data.value?.asDouble shouldBe (137.5 plusOrMinus EPSILON)    // major third (5/4)
        }

        withClue("ratio with plain numbers returns unchanged") {
            val p = seq("1 2 3").ratio()
            val events = p.queryArc(0.0, 1.0)

            events.size shouldBe 3
            events[0].data.value?.asDouble shouldBe (1.0 plusOrMinus EPSILON)
            events[1].data.value?.asDouble shouldBe (2.0 plusOrMinus EPSILON)
            events[2].data.value?.asDouble shouldBe (3.0 plusOrMinus EPSILON)
        }

        withClue("ratio with string extension") {
            val p = "5:4 3:2".ratio()
            val events = p.queryArc(0.0, 1.0)

            events.size shouldBe 2
            events[0].data.value?.asDouble shouldBe (1.25 plusOrMinus EPSILON)
            events[1].data.value?.asDouble shouldBe (1.5 plusOrMinus EPSILON)
        }

        withClue("ratio compiled") {
            val p = StrudelPattern.compile("""ratio("1 5:4 3:2")""")!!
            val events = p.queryArc(0.0, 1.0)

            events.size shouldBe 3
            events[0].data.value?.asDouble shouldBe (1.0 plusOrMinus EPSILON)
            events[1].data.value?.asDouble shouldBe (1.25 plusOrMinus EPSILON)
            events[2].data.value?.asDouble shouldBe (1.5 plusOrMinus EPSILON)
        }

        withClue("ratio with multiple divisions") {
            // 12:3:2 = 12 / 3 / 2 = 2
            val p = seq("12:3:2").ratio()
            val events = p.queryArc(0.0, 1.0)

            events.size shouldBe 1
            events[0].data.value?.asDouble shouldBe (2.0 plusOrMinus EPSILON)
        }
    }
})

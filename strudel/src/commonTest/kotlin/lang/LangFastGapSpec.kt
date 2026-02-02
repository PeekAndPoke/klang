package io.peekandpoke.klang.strudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.strudel.EPSILON
import io.peekandpoke.klang.strudel.StrudelPattern

class LangFastGapSpec : StringSpec({

    "top-level fastGap() creates compressed pattern with gap" {
        // fastGap requires at least 2 arguments: factor and pattern
        val p = fastGap(2.0, "c d")
        val events = p.queryArc(0.0, 1.0)

        // With factor 2, pattern is compressed to first half of cycle
        events.size shouldBe 2
        events[0].data.value?.asString shouldBe "c"
        events[0].part.begin.toDouble() shouldBe (0.0 plusOrMinus EPSILON)
        events[0].part.end.toDouble() shouldBe (0.25 plusOrMinus EPSILON)

        events[1].data.value?.asString shouldBe "d"
        events[1].part.begin.toDouble() shouldBe (0.25 plusOrMinus EPSILON)
        events[1].part.end.toDouble() shouldBe (0.5 plusOrMinus EPSILON)
    }

    "pattern extension fastGap() compresses pattern with gap" {
        val p = note("c d").fastGap(2.0)
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events[0].part.begin.toDouble() shouldBe (0.0 plusOrMinus EPSILON)
        events[0].part.end.toDouble() shouldBe (0.25 plusOrMinus EPSILON)

        events[1].part.begin.toDouble() shouldBe (0.25 plusOrMinus EPSILON)
        events[1].part.end.toDouble() shouldBe (0.5 plusOrMinus EPSILON)
    }

    "string extension fastGap() compresses pattern with gap" {
        val p = "c d".fastGap(2.0)
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events[0].data.value?.asString shouldBe "c"
        events[0].part.begin.toDouble() shouldBe (0.0 plusOrMinus EPSILON)
        events[0].part.end.toDouble() shouldBe (0.25 plusOrMinus EPSILON)
    }

    "fastGap() works within compiled code" {
        val p = StrudelPattern.compile("""note("a b").fastGap(3)""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        // With factor 3, pattern is compressed to first third of cycle
        events.size shouldBe 2
        events[0].part.end.toDouble() shouldBe (1.0 / 6.0 plusOrMinus EPSILON)
    }

    "densityGap() alias works as top-level function" {
        val p = densityGap(2.0, "c d")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events[0].data.value?.asString shouldBe "c"
        events[0].part.end.toDouble() shouldBe (0.25 plusOrMinus EPSILON)
    }

    "densityGap() alias works as pattern extension" {
        val p = note("c d").densityGap(2.0)
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events[0].part.end.toDouble() shouldBe (0.25 plusOrMinus EPSILON)
    }

    "densityGap() alias works as string extension" {
        val p = "c d".densityGap(2.0)
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events[0].data.value?.asString shouldBe "c"
        events[0].part.end.toDouble() shouldBe (0.25 plusOrMinus EPSILON)
    }

    "densityGap() alias works within compiled code" {
        val p = StrudelPattern.compile("""note("a b").densityGap(4)""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        events.size shouldBe 2
        events[0].part.end.toDouble() shouldBe (0.125 plusOrMinus EPSILON)
    }
})

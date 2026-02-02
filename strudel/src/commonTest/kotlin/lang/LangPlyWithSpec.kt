package io.peekandpoke.klang.strudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldBeEqualIgnoringCase
import io.peekandpoke.klang.strudel.EPSILON
import io.peekandpoke.klang.strudel.StrudelPattern

class LangPlyWithSpec : StringSpec({

    "plyWith() with soundIndex (n pattern)" {
        // n() sets soundIndex, which works with add()
        val p = seq("5").plyWith(3) { it.add("1") }
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 3

        // soundIndex: 5, 6, 7 (add applied 0, 1, 2 times)
        events[0].data.value?.asDouble shouldBe 5.0
        events[0].part.begin.toDouble() shouldBe (0.0 plusOrMinus EPSILON)
        events[0].part.end.toDouble() shouldBe (1.0 / 3.0 plusOrMinus EPSILON)

        events[1].data.value?.asDouble shouldBe 6.0
        events[1].part.begin.toDouble() shouldBe (1.0 / 3.0 plusOrMinus EPSILON)
        events[1].part.end.toDouble() shouldBe (2.0 / 3.0 plusOrMinus EPSILON)

        events[2].data.value?.asDouble shouldBe 7.0
        events[2].part.begin.toDouble() shouldBe (2.0 / 3.0 plusOrMinus EPSILON)
        events[2].part.end.toDouble() shouldBe (1.0 plusOrMinus EPSILON)
    }

    "plyWith() with multiple events" {
        val p = seq("0 2").plyWith(2) { it.add("5") }
        val events = p.queryArc(0.0, 1.0)

        // 2 base events, each repeated 2 times = 4 total
        events.size shouldBe 4

        // First event (0): 0, 5
        events[0].data.value?.asDouble shouldBe 0.0
        events[1].data.value?.asDouble shouldBe 5.0

        // Second event (2): 2, 7
        events[2].data.value?.asDouble shouldBe 2.0
        events[3].data.value?.asDouble shouldBe 7.0
    }

    "plyWith() with factor 1 returns original" {
        val p = seq("7").plyWith(1) { it.add("100") } // Function shouldn't be applied for factor=1
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.value?.asDouble shouldBe 7.0
    }

    "plywith() lowercase alias works" {
        val p = seq("0").plywith(2) { it.add("5") }
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events[0].data.value?.asDouble shouldBe 0.0
        events[1].data.value?.asDouble shouldBe 5.0
    }

    "plyWith() applies function cumulatively" {
        // Test that the function is applied 0, 1, 2, 3 times
        val p = "10".plyWith(4) { it.add("3") }
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 4

        // soundIndex: 10, 13, 16, 19 (add 3 applied 0, 1, 2, 3 times)
        events[0].data.value?.asDouble shouldBe 10
        events[1].data.value?.asDouble shouldBe 13
        events[2].data.value?.asDouble shouldBe 16
        events[3].data.value?.asDouble shouldBe 19
    }

    "plyWith() with note pattern" {
        // Test with note() which sets note field
        val p = note("c3 d3").plyWith(2) { it.transpose("12") }
        val events = p.queryArc(0.0, 1.0)

        // 2 events, each repeated 2 times = 4 total
        events.size shouldBe 4

        // First event (c): c, c+12
        events[0].data.note shouldBeEqualIgnoringCase "C3"
        events[1].data.note shouldBeEqualIgnoringCase "C4"  // c transposed by 12 semitones = c1

        // Second event (d): d, d+12
        events[2].data.note shouldBeEqualIgnoringCase "D3"
        events[3].data.note shouldBeEqualIgnoringCase "D4"  // d transposed by 12 semitones = d1
    }

    "plyWith() event timing" {
        // Verify events are evenly distributed
        val p = seq("0").plyWith(5) { it.add("1") }
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 5

        // Each event should be 1/5 = 0.2 of the cycle
        for (i in 0 until 5) {
            events[i].part.begin.toDouble() shouldBe (i * 0.2 plusOrMinus EPSILON)
            events[i].part.end.toDouble() shouldBe ((i + 1) * 0.2 plusOrMinus EPSILON)
        }
    }

    // Compile tests

    "plyWith() with compile - basic" {
        val p = StrudelPattern.compile("""seq("5").plyWith(3, p => p.add("1"))""")!!
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 3
        events[0].data.value?.asDouble shouldBe 5.0
        events[1].data.value?.asDouble shouldBe 6.0
        events[2].data.value?.asDouble shouldBe 7.0
    }

    "plyWith() with compile - multiple events" {
        val p = StrudelPattern.compile("""seq("0 2").plyWith(2, p => p.add("5"))""")!!
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 4
        events[0].data.value?.asDouble shouldBe 0.0
        events[1].data.value?.asDouble shouldBe 5.0
        events[2].data.value?.asDouble shouldBe 2.0
        events[3].data.value?.asDouble shouldBe 7.0
    }

    "plywith() with compile - lowercase alias" {
        val p = StrudelPattern.compile("""seq("0").plywith(2, p => p.add("5"))""")!!
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events[0].data.value?.asDouble shouldBe 0.0
        events[1].data.value?.asDouble shouldBe 5.0
    }

    "plyWith() with compile - note pattern" {
        val p = StrudelPattern.compile("""note("c3 d3").plyWith(2, p => p.transpose("12"))""")!!
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 4
        events[0].data.note shouldBeEqualIgnoringCase "C3"
        events[1].data.note shouldBeEqualIgnoringCase "C4"
        events[2].data.note shouldBeEqualIgnoringCase "D3"
        events[3].data.note shouldBeEqualIgnoringCase "D4"
    }
})

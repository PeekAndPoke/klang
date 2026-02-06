package io.peekandpoke.klang.strudel.lang

import io.kotest.assertions.assertSoftly
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldBeEqualIgnoringCase
import io.peekandpoke.klang.strudel.EPSILON
import io.peekandpoke.klang.strudel.StrudelPattern

class LangPlyForEachSpec : StringSpec({

    "plyForEach() with soundIndex (n pattern)" {
        // Test that plyForEach starts with the original value,
        // then applies the function with index 1, 2, 3...
        val p = seq("1").plyForEach(4) { pattern, index ->
            pattern.add(index * 2)
        }

        assertSoftly {

            val events = p.queryArc(0.0, 1.0)

            events.forEachIndexed { index, event ->
                println(
                    "${index + 1}: note: ${event.data.value?.asString} | " +
                            "part: ${event.part.begin} ${event.part.end} | " +
                            "whole: ${event.whole.begin} ${event.whole.end}"
                )
            }

            events.size shouldBe 4

            // Original soundIndex, then transformations with indices 1, 2, 3
            events[0].data.value?.asDouble shouldBe 1.0
            events[0].part.begin.toDouble() shouldBe (0.0 plusOrMinus EPSILON)
            events[0].part.end.toDouble() shouldBe (0.25 plusOrMinus EPSILON)

            events[1].data.value?.asDouble shouldBe 3.0 // 1 + 1 * 2
            events[1].part.begin.toDouble() shouldBe (0.25 plusOrMinus EPSILON)
            events[1].part.end.toDouble() shouldBe (0.5 plusOrMinus EPSILON)

            events[2].data.value?.asDouble shouldBe 5.0 // 1 + 2 * 2
            events[2].part.begin.toDouble() shouldBe (0.5 plusOrMinus EPSILON)
            events[2].part.end.toDouble() shouldBe (0.75 plusOrMinus EPSILON)

            events[3].data.value?.asDouble shouldBe 7.0 // 1 + 3 * 2
            events[3].part.begin.toDouble() shouldBe (0.75 plusOrMinus EPSILON)
            events[3].part.end.toDouble() shouldBe (1.0 plusOrMinus EPSILON)
        }
    }

    "plyForEach() with multiple events" {
        val p = seq("0 10").plyForEach(3) { pattern, index ->
            pattern.add((index * 10).toString())
        }
        val events = p.queryArc(0.0, 1.0)

        // 2 events, each repeated 3 times = 6 total
        events.size shouldBe 6

        // First event (0) with 3 repetitions: 0, 10, 20
        events[0].data.value?.asDouble shouldBe 0.0
        events[1].data.value?.asDouble shouldBe 10.0 // 0 + 1*10
        events[2].data.value?.asDouble shouldBe 20.0 // 0 + 2*10

        // Second event (10) with 3 repetitions: 10, 20, 30
        events[3].data.value?.asDouble shouldBe 10.0
        events[4].data.value?.asDouble shouldBe 20.0 // 10 + 1*10
        events[5].data.value?.asDouble shouldBe 30.0 // 10 + 2*10
    }

    "plyForEach() with factor 1 returns original" {
        val p = seq("5").plyForEach(1) { pattern, _ ->
            pattern.add("100") // This shouldn't be called since factor=1
        }
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.value?.asDouble shouldBe 5.0
    }

    "plyforeach() lowercase alias works" {
        val p = seq("0").plyforeach(2) { pattern, index ->
            pattern.add(index * 5)
        }
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events[0].data.value?.asDouble shouldBe 0.0
        events[1].data.value?.asDouble shouldBe 5.0 // 0 + 1*5
    }

    "plyForEach() passes correct indices" {
        // Verify that the indices passed are 1, 2, 3 (not 0, 1, 2)
        val p = seq("10").plyForEach(4) { pattern, index ->
            // index should be: 1, 2, 3 (skips 0)
            pattern.add(index * 100)
        }
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 4

        // 10 (original), 110 (10+100), 210 (10+200), 310 (10+300)
        events[0].data.value?.asDouble shouldBe 10.0
        events[1].data.value?.asDouble shouldBe 110.0 // 10 + 1*100
        events[2].data.value?.asDouble shouldBe 210.0 // 10 + 2*100
        events[3].data.value?.asDouble shouldBe 310.0 // 10 + 3*100
    }

    "plyForEach() with note patterns" {
        // Test with note() and transpose
        val p = note("c3").plyForEach(3) { pattern, index ->
            pattern.transpose(index * 12) // Transpose by octaves
        }
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 3

        // c, c1, c2 (transposed by 0, 12, 24 semitones)
        events[0].data.note shouldBeEqualIgnoringCase "C3"
        events[1].data.note shouldBeEqualIgnoringCase "C4" // +12 semitones = +1 octave
        events[2].data.note shouldBeEqualIgnoringCase "C5" // +24 semitones = +2 octaves
    }

    "plyForEach() timing is correct" {
        // Verify that events are evenly distributed in time
        val p = seq("1").plyForEach(5) { pattern, index ->
            pattern.add(index)
        }
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 5

        // Each event should be 1/5 = 0.2 of the cycle
        for (i in 0 until 5) {
            events[i].part.begin.toDouble() shouldBe (i * 0.2 plusOrMinus EPSILON)
            events[i].part.end.toDouble() shouldBe ((i + 1) * 0.2 plusOrMinus EPSILON)
        }
    }

    // Compile tests

    "plyForEach() with compile - basic" {
        val p = StrudelPattern.compile("""seq("1").plyForEach(4, (p, n) => p.add(n * 2))""")!!
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 4
        events[0].data.value?.asDouble shouldBe 1.0
        events[1].data.value?.asDouble shouldBe 3.0 // 1 + 1*2
        events[2].data.value?.asDouble shouldBe 5.0 // 1 + 2*2
        events[3].data.value?.asDouble shouldBe 7.0 // 1 + 3*2
    }

    "plyForEach() with compile - multiple events" {
        val p = StrudelPattern.compile("""seq("0 10").plyForEach(3, (p, n) => p.add(n * 10))""")!!
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 6
        events[0].data.value?.asDouble shouldBe 0.0
        events[1].data.value?.asDouble shouldBe 10.0
        events[2].data.value?.asDouble shouldBe 20.0
        events[3].data.value?.asDouble shouldBe 10.0
        events[4].data.value?.asDouble shouldBe 20.0
        events[5].data.value?.asDouble shouldBe 30.0
    }

    "plyforeach() with compile - lowercase alias" {
        val p = StrudelPattern.compile("""seq("0").plyforeach(2, (p, n) => p.add(n * 5))""")!!
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events[0].data.value?.asDouble shouldBe 0.0
        events[1].data.value?.asDouble shouldBe 5.0
    }

    "plyForEach() with compile - note pattern" {
        val p = StrudelPattern.compile("""note("c3").plyForEach(3, (p, n) => p.transpose(n * 12))""")!!
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 3
        events[0].data.note shouldBeEqualIgnoringCase "C3"
        events[1].data.note shouldBeEqualIgnoringCase "C4"
        events[2].data.note shouldBeEqualIgnoringCase "C5"
    }
})

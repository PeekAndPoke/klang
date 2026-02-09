package io.peekandpoke.klang.strudel.lang

import io.kotest.assertions.assertSoftly
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldBeEqualIgnoringCase
import io.peekandpoke.klang.strudel.StrudelPattern

class LangHushSpec : StringSpec({

    // -- Original behavior (no arguments) ---------------------------------------------------------------------

    "hush() returns silence with no arguments" {
        val p = hush()
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 0
    }

    "hush() works in compiled code" {
        val p = StrudelPattern.compile("""hush()""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        events.size shouldBe 0
    }

    "hush() works as method on StrudelPattern without arguments" {
        // note("c d").hush() -> silence
        val p = note("c d").hush()
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 0
    }

    "hush() works as extension on String without arguments" {
        // "a b c".hush()
        val p = "a b c".hush()
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 0
    }

    // -- New behavior: Control pattern support ----------------------------------------------------------------

    "hush() with 'true' silences the pattern" {
        val p = note("a").hush("true")
        p.queryArc(0.0, 1.0).size shouldBe 0
    }

    "hush() with 1 silences the pattern" {
        val p = note("a").hush(1)
        p.queryArc(0.0, 1.0).size shouldBe 0
    }

    "hush() with 'false' keeps the pattern" {
        val p = note("a").hush("false")
        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 1
        events[0].data.note shouldBeEqualIgnoringCase "a"
    }

    "hush() with 0 keeps the pattern" {
        val p = note("a").hush(0)
        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 1
        events[0].data.note shouldBeEqualIgnoringCase "a"
    }

    "hush() with boolean true silences" {
        val p = note("a").hush(true)
        p.queryArc(0.0, 1.0).size shouldBe 0
    }

    "hush() with boolean false keeps" {
        val p = note("a").hush(false)
        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 1
        events[0].data.note shouldBeEqualIgnoringCase "a"
    }

    "hush() with control pattern \"0 1\"" {
        // hush "0 1" -> 1st half: hush(0)=keep, 2nd half: hush(1)=silence
        val p = note("a b").hush("0 1")

        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 1
        events[0].data.note shouldBeEqualIgnoringCase "a"
    }

    "hush() with control pattern \"<1 0>\" alternates" {
        val p = s("bd sd").hush("<1 0>")

        assertSoftly {
            repeat(4) { cycle ->
                withClue("Cycle $cycle") {
                    val cycleDbl = cycle.toDouble()
                    val events = p.queryArc(cycleDbl, cycleDbl + 1)
                        .filter { it.isOnset }

                    // Pattern alternates: cycle 0 → hush(1)=silence, cycle 1 → hush(0)=keep
                    if (cycle % 2 == 0) {
                        events.size shouldBe 0
                    } else {
                        events.size shouldBe 2
                        events[0].data.sound shouldBeEqualIgnoringCase "bd"
                        events[1].data.sound shouldBeEqualIgnoringCase "sd"
                    }
                }
            }
        }
    }

    "hush() works as string extension with control pattern" {
        val p = "a".hush(1).note()
        p.queryArc(0.0, 1.0).size shouldBe 0
    }

    // -- mute() alias tests -----------------------------------------------------------------------------------

    "mute() returns silence with no arguments" {
        val p = mute()
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 0
    }

    "mute() works as method on StrudelPattern without arguments" {
        val p = note("c d").mute()
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 0
    }

    "mute() with 'true' silences the pattern" {
        val p = note("a").mute("true")
        p.queryArc(0.0, 1.0).size shouldBe 0
    }

    "mute() with 1 silences the pattern" {
        val p = note("a").mute(1)
        p.queryArc(0.0, 1.0).size shouldBe 0
    }

    "mute() with 'false' keeps the pattern" {
        val p = note("a").mute("false")
        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 1
        events[0].data.note shouldBeEqualIgnoringCase "a"
    }

    "mute() with 0 keeps the pattern" {
        val p = note("a").mute(0)
        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 1
        events[0].data.note shouldBeEqualIgnoringCase "a"
    }

    "mute() with control pattern \"0 1\"" {
        val p = note("a b").mute("0 1")

        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 1
        events[0].data.note shouldBeEqualIgnoringCase "a"
    }

    "mute() with control pattern \"<1 0>\" alternates" {
        val p = s("bd sd").mute("<1 0>")

        assertSoftly {
            repeat(4) { cycle ->
                withClue("Cycle $cycle") {
                    val cycleDbl = cycle.toDouble()
                    val events = p.queryArc(cycleDbl, cycleDbl + 1)
                        .filter { it.isOnset }

                    if (cycle % 2 == 0) {
                        events.size shouldBe 0
                    } else {
                        events.size shouldBe 2
                        events[0].data.sound shouldBeEqualIgnoringCase "bd"
                        events[1].data.sound shouldBeEqualIgnoringCase "sd"
                    }
                }
            }
        }
    }

    "mute() works as string extension" {
        val p = "a".mute(1).note()
        p.queryArc(0.0, 1.0).size shouldBe 0
    }

    "mute() behaves identically to hush()" {
        val pattern = s("bd sd")

        // Test without arguments
        val hushed1 = pattern.hush()
        val muted1 = pattern.mute()
        hushed1.queryArc(0.0, 1.0).size shouldBe muted1.queryArc(0.0, 1.0).size

        // Test with control pattern
        val hushed2 = pattern.hush("<1 0>")
        val muted2 = pattern.mute("<1 0>")

        repeat(4) { cycle ->
            val cycleDbl = cycle.toDouble()
            val hushedEvents = hushed2.queryArc(cycleDbl, cycleDbl + 1).filter { it.isOnset }
            val mutedEvents = muted2.queryArc(cycleDbl, cycleDbl + 1).filter { it.isOnset }
            hushedEvents.size shouldBe mutedEvents.size
        }
    }
})

package io.peekandpoke.klang.sprudel.lang.addons

import io.kotest.assertions.assertSoftly
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldBeEqualIgnoringCase
import io.peekandpoke.klang.sprudel.StrudelPattern
import io.peekandpoke.klang.sprudel.StrudelPatternEvent
import io.peekandpoke.klang.sprudel.dslInterfaceTests
import io.peekandpoke.klang.sprudel.lang.apply
import io.peekandpoke.klang.sprudel.lang.fast
import io.peekandpoke.klang.sprudel.lang.gain
import io.peekandpoke.klang.sprudel.lang.s

class LangSoloSpec : StringSpec({

    // SoloPattern fills silent gaps with filler events (gain=0.000001, sound="sine").
    // Source events have gain=null, so we filter by that to get the real events.
    fun List<StrudelPatternEvent>.sourceEvents() =
        filter { it.data.gain == null }

    // -- dsl interface tests -----------------------------------------------------------------------------------------

    "solo() dsl interface" {
        dslInterfaceTests(
            "pattern.solo()" to s("bd").solo(),
            "string.solo()" to "bd".solo(),
            "script pattern.solo()" to StrudelPattern.compile("""s("bd").solo()"""),
            "script string.solo()" to StrudelPattern.compile(""""bd".solo()"""),
            "apply(solo())" to s("bd").apply(solo()),
            "script apply(solo())" to StrudelPattern.compile("""s("bd").apply(solo())"""),
        ) { _, events ->
            val onsets = events.filter { it.isOnset }
            onsets shouldHaveSize 1
            onsets[0].data.solo shouldBe 0.97
        }
    }

    "solo(amount) dsl interface" {
        dslInterfaceTests(
            "pattern.solo(1)" to s("bd").solo(1),
            "string.solo(1)" to "bd".solo(1),
            "script pattern.solo(1)" to StrudelPattern.compile("""s("bd").solo(1)"""),
            "script string.solo(1)" to StrudelPattern.compile(""""bd".solo(1)"""),
        ) { _, events ->
            val onsets = events.filter { it.isOnset }
            onsets shouldHaveSize 1
            onsets[0].data.solo shouldBe 1.0
        }
    }

    // -- default amount (0.97) ---------------------------------------------------------------------------------------

    "solo() sets data.solo = 0.97" {
        val subject = s("bd").solo()

        assertSoftly {
            repeat(12) { cycle ->
                withClue("Cycle $cycle") {
                    val cycleDbl = cycle.toDouble()
                    val events = subject.queryArc(cycleDbl, cycleDbl + 1)
                        .filter { it.isOnset }

                    events.size shouldBe 1
                    events[0].data.sound shouldBeEqualIgnoringCase "bd"
                    events[0].data.solo shouldBe 0.97
                }
            }
        }
    }

    "solo(null) falls back to 0.97" {
        val subject = s("bd").solo(null)

        assertSoftly {
            repeat(12) { cycle ->
                withClue("Cycle $cycle") {
                    val events = subject.queryArc(cycle.toDouble(), cycle.toDouble() + 1)
                        .filter { it.isOnset }

                    events.size shouldBe 1
                    events[0].data.solo shouldBe 0.97
                }
            }
        }
    }

    "apply(solo(null)) falls back to 0.97 via PatternMapperFn" {
        val p = s("bd sd").apply(solo(null))
        val events = p.queryArc(0.0, 1.0).sourceEvents()

        assertSoftly {
            events.shouldNotBeEmpty()
            events.forEach { it.data.solo shouldBe 0.97 }
        }
    }

    // -- explicit amounts --------------------------------------------------------------------------------------------

    "solo(1) sets data.solo = 1.0" {
        val subject = s("bd").solo(1)

        assertSoftly {
            repeat(12) { cycle ->
                withClue("Cycle $cycle") {
                    val events = subject.queryArc(cycle.toDouble(), cycle.toDouble() + 1)
                        .filter { it.isOnset }

                    events.size shouldBe 1
                    events[0].data.solo shouldBe 1.0
                }
            }
        }
    }

    "solo(0) sets data.solo = 0.0" {
        val subject = s("bd").solo(0)

        assertSoftly {
            repeat(12) { cycle ->
                withClue("Cycle $cycle") {
                    val events = subject.queryArc(cycle.toDouble(), cycle.toDouble() + 1)
                        .filter { it.isOnset }

                    events.size shouldBe 1
                    events[0].data.solo shouldBe 0.0
                }
            }
        }
    }

    "solo(0.5) sets numeric solo amount" {
        val subject = s("bd").solo(0.5)

        assertSoftly {
            repeat(12) { cycle ->
                withClue("Cycle $cycle") {
                    val events = subject.queryArc(cycle.toDouble(), cycle.toDouble() + 1)
                        .filter { it.isOnset }

                    events.size shouldBe 1
                    events[0].data.solo shouldBe 0.5
                }
            }
        }
    }

    "solo(true) sets data.solo = 1.0" {
        val subject = s("bd").solo(true)

        assertSoftly {
            repeat(12) { cycle ->
                withClue("Cycle $cycle") {
                    val events = subject.queryArc(cycle.toDouble(), cycle.toDouble() + 1)
                        .filter { it.isOnset }

                    events.size shouldBe 1
                    events[0].data.solo shouldBe 1.0
                }
            }
        }
    }

    "solo(false) sets data.solo = 0.0" {
        val subject = s("bd").solo(false)

        assertSoftly {
            repeat(12) { cycle ->
                withClue("Cycle $cycle") {
                    val events = subject.queryArc(cycle.toDouble(), cycle.toDouble() + 1)
                        .filter { it.isOnset }

                    events.size shouldBe 1
                    events[0].data.solo shouldBe 0.0
                }
            }
        }
    }

    // -- control patterns --------------------------------------------------------------------------------------------

    "solo(\"<1 0>\") alternates solo amount per cycle" {
        val subject = s("bd sd").solo("<1 0>")

        assertSoftly {
            repeat(12) { cycle ->
                withClue("Cycle $cycle") {
                    val events = subject.queryArc(cycle.toDouble(), cycle.toDouble() + 1)
                        .filter { it.isOnset }

                    events.size shouldBe 2

                    val expectedSolo = if ((cycle % 2) == 0) 1.0 else 0.0
                    events[0].data.sound shouldBeEqualIgnoringCase "bd"
                    events[0].data.solo shouldBe expectedSolo
                    events[1].data.sound shouldBeEqualIgnoringCase "sd"
                    events[1].data.solo shouldBe expectedSolo
                }
            }
        }
    }

    "solo(\"1 0\") alternates solo amount per half cycle" {
        val subject = s("bd sd").solo("1 0")

        assertSoftly {
            repeat(12) { cycle ->
                withClue("Cycle $cycle") {
                    val events = subject.queryArc(cycle.toDouble(), cycle.toDouble() + 1)
                        .filter { it.isOnset }

                    events.size shouldBe 2
                    events[0].data.sound shouldBeEqualIgnoringCase "bd"
                    events[0].data.solo shouldBe 1.0
                    events[1].data.sound shouldBeEqualIgnoringCase "sd"
                    events[1].data.solo shouldBe 0.0
                }
            }
        }
    }

    // -- string extension --------------------------------------------------------------------------------------------

    "\"bd\".solo() works as string extension" {
        val subject = "bd".solo()

        assertSoftly {
            repeat(12) { cycle ->
                withClue("Cycle $cycle") {
                    val events = subject.queryArc(cycle.toDouble(), cycle.toDouble() + 1)
                        .filter { it.isOnset }

                    events.size shouldBe 1
                    events[0].data.value?.asString shouldBe "bd"
                    events[0].data.solo shouldBe 0.97
                }
            }
        }
    }

    // -- PatternMapperFn ---------------------------------------------------------------------------------------------

    "apply(solo()) works as PatternMapperFn" {
        val p = s("bd sd").apply(solo())
        val events = p.queryArc(0.0, 1.0).sourceEvents()

        assertSoftly {
            events.shouldNotBeEmpty()
            events.forEach { it.data.solo shouldBe 0.97 }
        }
    }

    "apply(solo(0)) disables solo via PatternMapperFn" {
        val p = s("bd sd").apply(solo(0))
        val events = p.queryArc(0.0, 1.0).sourceEvents()

        assertSoftly {
            events.shouldNotBeEmpty()
            events.forEach { it.data.solo shouldBe 0.0 }
        }
    }

    "apply(timeLoop(1).solo()) chains mappers" {
        val p = s("bd sd").apply(timeLoop(1.0).solo())
        val events = p.queryArc(0.0, 1.0).sourceEvents()

        assertSoftly {
            events.shouldNotBeEmpty()
            events.forEach { it.data.solo shouldBe 0.97 }
        }
    }

    // -- chaining ----------------------------------------------------------------------------------------------------

    "solo().fast(2) preserves solo value after transformation" {
        val subject = s("bd").solo().fast(2)

        assertSoftly {
            repeat(12) { cycle ->
                withClue("Cycle $cycle") {
                    val events = subject.queryArc(cycle.toDouble(), cycle.toDouble() + 1)
                        .filter { it.isOnset }

                    events.size shouldBe 2
                    events[0].data.solo shouldBe 0.97
                    events[1].data.solo shouldBe 0.97
                }
            }
        }
    }

    "gain(0.5).solo() chains with other modifiers" {
        val subject = s("bd").gain(0.5).solo()

        assertSoftly {
            repeat(12) { cycle ->
                withClue("Cycle $cycle") {
                    val events = subject.queryArc(cycle.toDouble(), cycle.toDouble() + 1)
                        .filter { it.isOnset }

                    events.size shouldBe 1
                    events[0].data.gain shouldBe 0.5
                    events[0].data.solo shouldBe 0.97
                }
            }
        }
    }

    // -- compiled scripts --------------------------------------------------------------------------------------------

    "script apply(solo()) works in compiled code" {
        val p = StrudelPattern.compile("""s("bd sd").apply(solo())""")!!
        val events = p.queryArc(0.0, 1.0).sourceEvents()

        assertSoftly {
            events.shouldNotBeEmpty()
            events.forEach { it.data.solo shouldBe 0.97 }
        }
    }

    "script apply(solo(0)) disables solo in compiled code" {
        val p = StrudelPattern.compile("""s("bd sd").apply(solo(0))""")!!
        val events = p.queryArc(0.0, 1.0).sourceEvents()

        assertSoftly {
            events.shouldNotBeEmpty()
            events.forEach { it.data.solo shouldBe 0.0 }
        }
    }
})

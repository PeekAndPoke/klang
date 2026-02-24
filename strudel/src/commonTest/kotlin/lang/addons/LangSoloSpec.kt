package io.peekandpoke.klang.strudel.lang.addons

import io.kotest.assertions.assertSoftly
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel.dslInterfaceTests
import io.peekandpoke.klang.strudel.lang.apply
import io.peekandpoke.klang.strudel.lang.s

class LangSoloSpec : StringSpec({

    // SoloPattern fills silent gaps with filler events (gain=0.000001, sound="sine").
    // Source events have gain=null, so we filter by that to get the real events.
    fun List<io.peekandpoke.klang.strudel.StrudelPatternEvent>.sourceEvents() =
        filter { it.data.gain == null }

    "solo dsl interface" {
        dslInterfaceTests(
            "pattern.solo()" to
                    s("sine").solo(),
            "script pattern.solo()" to
                    StrudelPattern.compile("""s("sine").solo()"""),
            "string.solo()" to
                    "sine".solo(),
            "script string.solo()" to
                    StrudelPattern.compile(""""sine".solo()"""),
            "solo()" to
                    s("sine").apply(solo()),
            "script solo()" to
                    StrudelPattern.compile("""s("sine").apply(solo())"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
            events.any { it.data.solo == true } shouldBe true
        }
    }

    "solo() sets data.solo = true on source events" {
        val p = s("bd sd").solo()
        val events = p.queryArc(0.0, 1.0).sourceEvents()

        assertSoftly {
            events.shouldNotBeEmpty()
            events.forEach { it.data.solo shouldBe true }
        }
    }

    "solo(0) sets data.solo = false on source events" {
        val p = s("bd sd").solo(0)
        val events = p.queryArc(0.0, 1.0).sourceEvents()

        assertSoftly {
            events.shouldNotBeEmpty()
            events.forEach { it.data.solo shouldBe false }
        }
    }

    "solo(1) sets data.solo = true on source events" {
        val p = s("bd sd").solo(1)
        val events = p.queryArc(0.0, 1.0).sourceEvents()

        assertSoftly {
            events.shouldNotBeEmpty()
            events.forEach { it.data.solo shouldBe true }
        }
    }

    "solo() works as string extension" {
        val p = "bd sd".solo()
        val events = p.queryArc(0.0, 1.0).sourceEvents()

        assertSoftly {
            events.shouldNotBeEmpty()
            events.forEach { it.data.solo shouldBe true }
        }
    }

    "apply(solo()) works as PatternMapperFn" {
        val p = s("bd sd").apply(solo())
        val events = p.queryArc(0.0, 1.0).sourceEvents()

        assertSoftly {
            events.shouldNotBeEmpty()
            events.forEach { it.data.solo shouldBe true }
        }
    }

    "apply(solo(0)) disables solo via PatternMapperFn" {
        val p = s("bd sd").apply(solo(0))
        val events = p.queryArc(0.0, 1.0).sourceEvents()

        assertSoftly {
            events.shouldNotBeEmpty()
            events.forEach { it.data.solo shouldBe false }
        }
    }

    "apply(timeLoop().solo()) chains mappers" {
        // timeLoop(1.0) is a no-op for a 1-cycle pattern; solo() then marks events
        val p = s("bd sd").apply(timeLoop(1.0).solo())
        val events = p.queryArc(0.0, 1.0).sourceEvents()

        assertSoftly {
            events.shouldNotBeEmpty()
            events.forEach { it.data.solo shouldBe true }
        }
    }

    "script apply(solo()) works in compiled code" {
        val p = StrudelPattern.compile("""s("bd sd").apply(solo())""")!!
        val events = p.queryArc(0.0, 1.0).sourceEvents()

        assertSoftly {
            events.shouldNotBeEmpty()
            events.forEach { it.data.solo shouldBe true }
        }
    }

    "script apply(solo(0)) disables solo in compiled code" {
        val p = StrudelPattern.compile("""s("bd sd").apply(solo(0))""")!!
        val events = p.queryArc(0.0, 1.0).sourceEvents()

        assertSoftly {
            events.shouldNotBeEmpty()
            events.forEach { it.data.solo shouldBe false }
        }
    }
})

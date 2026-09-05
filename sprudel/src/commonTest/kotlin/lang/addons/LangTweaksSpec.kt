/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.sprudel.lang.addons

import io.kotest.assertions.assertSoftly
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldNotBe
import io.peekandpoke.klang.common.math.CycleTime
import io.peekandpoke.klang.common.math.CycleTimeSpan
import io.peekandpoke.klang.sprudel.EPSILON
import io.peekandpoke.klang.sprudel.SprudelPattern
import io.peekandpoke.klang.sprudel.SprudelPatternEvent
import io.peekandpoke.klang.sprudel.createSprudelVoiceData
import io.peekandpoke.klang.sprudel.dslInterfaceTests
import io.peekandpoke.klang.sprudel.lang.apply
import io.peekandpoke.klang.sprudel.lang.fast
import io.peekandpoke.klang.sprudel.lang.add
import io.peekandpoke.klang.sprudel.lang.gain
import io.peekandpoke.klang.sprudel.lang.n
import io.peekandpoke.klang.sprudel.lang.note
import io.peekandpoke.klang.sprudel.lang.pan
import io.peekandpoke.klang.sprudel.lang.s
import io.peekandpoke.klang.sprudel.lang.scale
import io.peekandpoke.klang.sprudel.lang.seq
import io.peekandpoke.klang.sprudel.lang.transpose

/**
 * Tests for tweaks: named per-note modifiers attached in mini-notation (`e3{swell}`) and bound later
 * by `tweaks(...)`. See `docs/tasks-archive/2026-08/20260831-mini-notation-tweaks.md`.
 */
class LangTweaksSpec : StringSpec({

    // ── Marking ──────────────────────────────────────────────────────────────

    "tweak dsl interface" {
        val pat = "a b"

        dslInterfaceTests(
            "pattern.tweak(name)" to seq(pat).tweak("swell"),
            "script pattern.tweak(name)" to SprudelPattern.compile("""seq("$pat").tweak("swell")"""),
            "string.tweak(name)" to pat.tweak("swell"),
            "script string.tweak(name)" to SprudelPattern.compile(""""$pat".tweak("swell")"""),
            "tweak(name)" to seq(pat).apply(tweak("swell")),
            "script tweak(name)" to SprudelPattern.compile("""seq("$pat").apply(tweak("swell"))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
            events.forEach { it.data.tweaks shouldBe listOf("swell") }
        }
    }

    "mini-notation attaches tweak names in written order" {
        val events = note("c3 e3{swell} g3{swell bend}").queryArc(0.0, 1.0)

        assertSoftly {
            events[0].data.tweaks shouldBe null
            events[1].data.tweaks shouldBe listOf("swell")
            events[2].data.tweaks shouldBe listOf("swell", "bend")
        }
    }

    "tweak() appends, so a repeat is kept twice" {
        val events = seq("a").tweak("bend").tweak("bend").queryArc(0.0, 1.0)

        events[0].data.tweaks shouldBe listOf("bend", "bend")
    }

    // ── Binding ──────────────────────────────────────────────────────────────

    "a bound tweak is applied to the marked event only" {
        val events = note("c3 e3{swell}")
            .tweaks("swell" to { p: SprudelPattern -> p.gain(0.5) })
            .queryArc(0.0, 1.0)

        assertSoftly {
            events[0].data.gain shouldBe null
            events[1].data.gain shouldBe (0.5 plusOrMinus EPSILON)
        }
    }

    "the script door binds an object literal of lambdas" {
        val compiled = SprudelPattern
            .compile("""note("c3 e3{swell}").tweaks({ swell: x => x.gain(0.5) })""")
        compiled shouldNotBe null
        val events = compiled!!.queryArc(0.0, 1.0)

        assertSoftly {
            events[0].data.gain shouldBe null
            events[1].data.gain shouldBe (0.5 plusOrMinus EPSILON)
        }
    }

    "tweaks apply in the order written on the event, not the order the map declares" {
        // Both bind gain, so the LAST one applied wins and the order is observable.
        val defs = arrayOf(
            "quiet" to { p: SprudelPattern -> p.gain(0.2) },
            "loud" to { p: SprudelPattern -> p.gain(0.9) },
        )

        val quietLast = note("c3{loud quiet}").tweaks(*defs).queryArc(0.0, 1.0)
        val loudLast = note("c3{quiet loud}").tweaks(*defs).queryArc(0.0, 1.0)

        assertSoftly {
            quietLast[0].data.gain shouldBe (0.2 plusOrMinus EPSILON)
            loudLast[0].data.gain shouldBe (0.9 plusOrMinus EPSILON)
        }
    }

    "a repeated tweak applies twice" {
        // The transform stamps a marker each time it runs, so the number of runs is observable.
        val once = note("c3{stamp}").tweaks("stamp" to ranProbe).queryArc(0.0, 1.0)
        val twice = note("c3{stamp stamp}").tweaks("stamp" to ranProbe).queryArc(0.0, 1.0)

        assertSoftly {
            once[0].data.tweaks!!.count { it == "ran" } shouldBe 1
            twice[0].data.tweaks!!.count { it == "ran" } shouldBe 2
        }
    }

    "an unbound name passes through untouched and is not stripped" {
        val events = note("c3{swell bend}")
            .tweaks("swell" to { p: SprudelPattern -> p.gain(0.5) })
            .queryArc(0.0, 1.0)

        assertSoftly {
            events[0].data.gain shouldBe (0.5 plusOrMinus EPSILON)
            // "bend" survives so an OUTER tweaks() can still claim it — this is what makes layering work
            events[0].data.tweaks shouldBe listOf("swell", "bend")
        }
    }

    "an inner and an outer tweaks() compose without knowing about each other" {
        val events = note("c3{swell bend}")
            .tweaks("swell" to { p: SprudelPattern -> p.gain(0.5) })
            .tweaks("bend" to { p: SprudelPattern -> p.pan(0.25) })
            .queryArc(0.0, 1.0)

        assertSoftly {
            events[0].data.gain shouldBe (0.5 plusOrMinus EPSILON)
            events[0].data.pan shouldBe (0.25 plusOrMinus EPSILON)
        }
    }

    "applying does not consume the name: two calls binding it both apply" {
        val events = note("c3{stamp}")
            .tweaks("stamp" to ranProbe)
            .tweaks("stamp" to ranProbe)
            .queryArc(0.0, 1.0)

        events[0].data.tweaks!!.count { it == "ran" } shouldBe 2
    }

    "binding nothing leaves the pattern untouched" {
        val plain = note("c3{swell}").queryArc(0.0, 1.0)
        val bound = note("c3{swell}").tweaks("other" to { p: SprudelPattern -> p.gain(0.5) })
            .queryArc(0.0, 1.0)

        assertSoftly {
            bound[0].data.gain shouldBe null
            bound[0].part shouldBe plain[0].part
            bound[0].whole shouldBe plain[0].whole
        }
    }

    // ── Across the pattern kinds ─────────────────────────────────────────────

    "tweaks work on n() patterns" {
        val events = n("0 1{swell} 2")
            .tweaks("swell" to { p: SprudelPattern -> p.gain(0.5) })
            .queryArc(0.0, 1.0)

        assertSoftly {
            events[0].data.gain shouldBe null
            events[1].data.gain shouldBe (0.5 plusOrMinus EPSILON)
            events[2].data.gain shouldBe null
        }
    }

    "tweaks survive scale resolution" {
        val events = n("0 1{swell} 2")
            .scale("c3:major")
            .tweaks("swell" to { p: SprudelPattern -> p.gain(0.5) })
            .queryArc(0.0, 1.0)

        assertSoftly {
            events[1].data.note shouldBe "D3"
            events[1].data.gain shouldBe (0.5 plusOrMinus EPSILON)
            events[0].data.gain shouldBe null
        }
    }

    "tweaks work on s() patterns and leave the sample index alone" {
        val events = s("bd hh:2{swell}")
            .tweaks("swell" to { p: SprudelPattern -> p.gain(0.5) })
            .queryArc(0.0, 1.0)

        assertSoftly {
            events[0].data.gain shouldBe null
            events[1].data.gain shouldBe (0.5 plusOrMinus EPSILON)
            events[1].data.soundIndex shouldBe 2
        }
    }

    "a tweak on a chord applies to every note in it" {
        val events = note("[c3,e3]{swell}")
            .tweaks("swell" to { p: SprudelPattern -> p.gain(0.5) })
            .queryArc(0.0, 1.0)

        assertSoftly {
            events.size shouldBe 2
            events.forEach { it.data.gain shouldBe (0.5 plusOrMinus EPSILON) }
        }
    }

    "arithmetic through a tweak hits only the marked event" {
        val events = seq("0 1{up} 2")
            .tweaks("up" to { p: SprudelPattern -> p.add(2) })
            .queryArc(0.0, 1.0)

        assertSoftly {
            events[0].data.value?.asDouble shouldBe (0.0 plusOrMinus EPSILON)
            events[1].data.value?.asDouble shouldBe (3.0 plusOrMinus EPSILON)
            events[2].data.value?.asDouble shouldBe (2.0 plusOrMinus EPSILON)
        }
    }

    "a pitch tweak transposes only the marked note" {
        val events = note("c3 e3{up} g3")
            .tweaks("up" to { p: SprudelPattern -> p.transpose(2) })
            .queryArc(0.0, 1.0)

        assertSoftly {
            events[0].data.note shouldBe "c3"
            events[1].data.note shouldBe "F#3"
            events[2].data.note shouldBe "g3"
        }
    }

    // ── Placement in the chain ───────────────────────────────────────────────

    "a later chain call overrides the tweak" {
        val events = note("c3{swell}")
            .tweaks("swell" to { p: SprudelPattern -> p.gain(0.5) })
            .gain(0.9)
            .queryArc(0.0, 1.0)

        events[0].data.gain shouldBe (0.9 plusOrMinus EPSILON)
    }

    "a tweak placed last wins over an earlier chain call" {
        val events = note("c3{swell}")
            .gain(0.9)
            .tweaks("swell" to { p: SprudelPattern -> p.gain(0.5) })
            .queryArc(0.0, 1.0)

        events[0].data.gain shouldBe (0.5 plusOrMinus EPSILON)
    }

    // ── Timing ───────────────────────────────────────────────────────────────

    "a voice-data tweak leaves part and whole exactly as they were" {
        val plain = note("c3 e3 g3 a3").queryArc(0.0, 1.0)
        val tweaked = note("c3 e3{swell} g3 a3")
            .tweaks("swell" to { p: SprudelPattern -> p.gain(0.5) })
            .queryArc(0.0, 1.0)

        assertSoftly {
            tweaked.size shouldBe plain.size
            plain.indices.forEach { i ->
                tweaked[i].part shouldBe plain[i].part
                tweaked[i].whole shouldBe plain[i].whole
            }
        }
    }

    "an already-clipped event keeps its clip after a tweak" {
        // `part` is the visible slice of `whole`. Fed straight in, because the DSL constructs used
        // above all hand out part == whole, which would leave the clipping path untested.
        val clipped = ClippedEventPattern(
            whole = CycleTimeSpan(CycleTime.ofCycles(0.0), CycleTime.ofCycles(1.0)),
            part = CycleTimeSpan(CycleTime.ofCycles(0.25), CycleTime.ofCycles(0.75)),
            tweaks = listOf("swell"),
        )

        val events = clipped.tweaks("swell" to { p: SprudelPattern -> p.gain(0.5) }).queryArc(0.0, 1.0)

        assertSoftly {
            events.size shouldBe 1
            events[0].data.gain shouldBe (0.5 plusOrMinus EPSILON)
            events[0].whole.begin.toCycles() shouldBe (0.0 plusOrMinus EPSILON)
            events[0].whole.end.toCycles() shouldBe (1.0 plusOrMinus EPSILON)
            events[0].part.begin.toCycles() shouldBe (0.25 plusOrMinus EPSILON)
            events[0].part.end.toCycles() shouldBe (0.75 plusOrMinus EPSILON)
        }
    }

    "a structural tweak plays inside the note's own slot" {
        val events = note("c3 e3{roll}")
            .tweaks("roll" to { p: SprudelPattern -> p.fast(2.0) })
            .queryArc(0.0, 1.0)

        assertSoftly {
            // c3 untouched, e3 became two events filling the second half
            events.size shouldBe 3
            events[1].whole.begin.toCycles() shouldBe (0.5 plusOrMinus EPSILON)
            events[1].whole.end.toCycles() shouldBe (0.75 plusOrMinus EPSILON)
            events[2].whole.begin.toCycles() shouldBe (0.75 plusOrMinus EPSILON)
            events[2].whole.end.toCycles() shouldBe (1.0 plusOrMinus EPSILON)
        }
    }

    // ── Cost ─────────────────────────────────────────────────────────────────

    "chained tweaks() query the base pattern once each, not 2^n times" {
        // Guards against the filter+stack shape, which queries its inner TWICE per level.
        val counter = intArrayOf(0)
        val base = CountingPattern(note("c3{a} e3{b} g3{c}"), counter)

        base.tweaks("a" to { p: SprudelPattern -> p.gain(0.5) })
            .tweaks("b" to { p: SprudelPattern -> p.gain(0.5) })
            .tweaks("c" to { p: SprudelPattern -> p.gain(0.5) })
            .queryArc(0.0, 1.0)

        counter[0] shouldBe 1
    }
})

/** Emits exactly one event whose [part] is a strict slice of its [whole]. */
private class ClippedEventPattern(
    private val whole: CycleTimeSpan,
    private val part: CycleTimeSpan,
    private val tweaks: List<String>,
) : SprudelPattern {
    override val weight: Double = 1.0
    override val numSteps: Double = 1.0
    override fun estimateCycleDuration(): Double = 1.0

    override fun queryArcContextual(
        from: CycleTime,
        to: CycleTime,
        ctx: SprudelPattern.QueryContext,
    ): List<SprudelPatternEvent> = listOf(
        SprudelPatternEvent(
            part = part,
            whole = whole,
            data = createSprudelVoiceData { this.tweaks = this@ClippedEventPattern.tweaks },
        ),
    )
}

/** A transform that stamps a marker every time it runs, so re-application is observable. */
private val ranProbe: (SprudelPattern) -> SprudelPattern = { p -> p.tweak("ran") }

/** Counts how often its inner pattern is queried. */
private class CountingPattern(
    private val inner: SprudelPattern,
    private val counter: IntArray,
) : SprudelPattern {
    override val weight: Double get() = inner.weight
    override val numSteps: Double? get() = inner.numSteps
    override fun estimateCycleDuration(): Double = inner.estimateCycleDuration()

    override fun queryArcContextual(
        from: CycleTime,
        to: CycleTime,
        ctx: SprudelPattern.QueryContext,
    ): List<SprudelPatternEvent> {
        counter[0]++
        return inner.queryArcContextual(from, to, ctx)
    }
}

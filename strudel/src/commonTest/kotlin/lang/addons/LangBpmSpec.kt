package io.peekandpoke.klang.strudel.lang.addons

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel.StrudelPattern.QueryContext
import io.peekandpoke.klang.strudel.dslInterfaceTests
import io.peekandpoke.klang.strudel.math.Rational

class LangBpmSpec : StringSpec({

    "bpm dsl interface" {
        dslInterfaceTests(
            "bpm" to bpm,
            "script bpm" to StrudelPattern.compile("bpm"),
        ) { _, events ->
            events.size shouldBe 1
            events[0].data.value?.asDouble shouldBe 0.5 * 240.0
        }
    }

    "bpm should return default value (120.0) from empty context" {
        val p = bpm
        val events = p.queryArc(0.0, 1.0)

        // bpm is a continuous pattern, queryArc returns events covering the arc
        // default implementation of queryArc uses empty context
        // default CPS is 0.5, so BPM = 0.5 * 240 = 120.0
        events.size shouldBe 1
        events[0].data.value?.asDouble shouldBe 120.0
    }

    "bpm should return custom value from context" {
        val p = bpm
        val customCps = 1.0

        val ctx = QueryContext {
            set(QueryContext.cpsKey, customCps)
        }

        val events = p.queryArcContextual(Rational.ZERO, Rational.ONE, ctx)

        // BPM = CPS * 240, so 1.0 * 240 = 240.0
        events.size shouldBe 1
        events[0].data.value?.asDouble shouldBe 240.0
    }

    "bpm should scale correctly with different CPS values" {
        val p = bpm

        val ctx = QueryContext {
            set(QueryContext.cpsKey, 0.25)
        }

        val events = p.queryArcContextual(Rational.ZERO, Rational.ONE, ctx)

        // BPM = CPS * 240, so 0.25 * 240 = 60.0
        events.size shouldBe 1
        events[0].data.value?.asDouble shouldBe 60.0
    }
})

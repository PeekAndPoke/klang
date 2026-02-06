package io.peekandpoke.klang.strudel.lang.addons

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.strudel.StrudelPattern.QueryContext
import io.peekandpoke.klang.strudel.math.Rational

class LangCpsSpec : StringSpec({

    "cps should return default value (0.5) from empty context" {
        val p = cps
        val events = p.queryArc(0.0, 1.0)

        // cps is a continuous pattern, queryArc returns events covering the arc
        // default implementation of queryArc uses empty context
        events.size shouldBe 1
        events[0].data.value?.asDouble shouldBe 0.5
    }

    "cps should return custom value from context" {
        val p = cps
        val customCps = 120.0 / 60.0 // 2.0

        val ctx = QueryContext {
            set(QueryContext.cpsKey, customCps)
        }

        val events = p.queryArcContextual(Rational.ZERO, Rational.ONE, ctx)

        events.size shouldBe 1
        events[0].data.value?.asDouble shouldBe 2.0
    }
})

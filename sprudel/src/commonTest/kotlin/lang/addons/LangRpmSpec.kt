package io.peekandpoke.klang.sprudel.lang.addons

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.common.math.Rational
import io.peekandpoke.klang.sprudel.SprudelPattern
import io.peekandpoke.klang.sprudel.SprudelPattern.QueryContext
import io.peekandpoke.klang.sprudel.dslInterfaceTests

class LangRpmSpec : StringSpec({

    "rpm dsl interface" {
        dslInterfaceTests(
            "rpm" to rpm,
            "script rpm" to SprudelPattern.compile("rpm"),
        ) { _, events ->
            events.size shouldBe 1
            // default CPS is 0.5, so RPM = 0.5 * 60 = 30.0
            events[0].data.value?.asDouble shouldBe 0.5 * 60.0
        }
    }

    "rpm should return default value (30.0) from empty context" {
        val p = rpm
        val events = p.queryArc(0.0, 1.0)

        // default CPS is 0.5, so RPM = 0.5 * 60 = 30.0
        events.size shouldBe 1
        events[0].data.value?.asDouble shouldBe 30.0
    }

    "rpm should return custom value from context" {
        val p = rpm
        val customCps = 1.0

        val ctx = QueryContext {
            set(QueryContext.cpsKey, customCps)
        }

        val events = p.queryArcContextual(Rational.ZERO, Rational.ONE, ctx)

        // RPM = CPS * 60, so 1.0 * 60 = 60.0
        events.size shouldBe 1
        events[0].data.value?.asDouble shouldBe 60.0
    }

    "rpm should scale correctly with different CPS values" {
        val p = rpm

        val ctx = QueryContext {
            set(QueryContext.cpsKey, 0.25)
        }

        val events = p.queryArcContextual(Rational.ZERO, Rational.ONE, ctx)

        // RPM = CPS * 60, so 0.25 * 60 = 15.0
        events.size shouldBe 1
        events[0].data.value?.asDouble shouldBe 15.0
    }
})
